package app.nzyme.core.connect;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Timer;
import com.google.common.hash.Hashing;
import com.sun.net.httpserver.HttpServer;
import okhttp3.HttpUrl;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.testng.Assert.*;

public class ConnectDatasetTest {

    private static final int MIB = 1024 * 1024;

    private static class Connect {
        volatile byte[] data;
        volatile boolean supportRanges = true;
        volatile boolean sendEtag = true;
        volatile boolean sendChecksum = true;
        volatile boolean wrongChecksum = false;
        volatile int statusOverride = 0;
        volatile byte[] replaceDataAfterFirstChunk = null;
        volatile int maxRangeLength = Integer.MAX_VALUE;
        volatile long announcedTotal = -1;
        volatile int extraChunkBytes = 0;
        volatile String etagOverride = null;

        volatile long failOffset = -1;
        final AtomicInteger failOffsetTimes = new AtomicInteger(0);

        final AtomicInteger failFirstN = new AtomicInteger(0);
        final AtomicInteger requests = new AtomicInteger(0);
        final List<String> conditions = new CopyOnWriteArrayList<>();

        HttpServer server;

        static String etag(byte[] data) {
            return "\"" + Hashing.sha256().hashBytes(data) + "\"";
        }

        void start() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/data/test", exchange -> {
                requests.incrementAndGet();

                if (!"Bearer test-key".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    exchange.sendResponseHeaders(401, -1);
                    exchange.close();
                    return;
                }

                if (statusOverride > 0) {
                    exchange.sendResponseHeaders(statusOverride, -1);
                    exchange.close();
                    return;
                }

                if (failFirstN.get() > 0) {
                    failFirstN.decrementAndGet();
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                    return;
                }

                byte[] data = this.data;

                String condition = exchange.getRequestHeaders().getFirst("If-None-Match");
                if (condition != null) {
                    conditions.add(condition);

                    if (condition.equals(etag(data))) {
                        exchange.getResponseHeaders().add("ETag", etag(data));
                        exchange.sendResponseHeaders(304, -1);
                        exchange.close();
                        return;
                    }
                }

                if (sendEtag) {
                    exchange.getResponseHeaders().add("ETag", etagOverride == null ? etag(data) : etagOverride);
                }

                if (sendChecksum) {
                    exchange.getResponseHeaders().add("X-Nzyme-Data-Sha256", wrongChecksum
                            ? Hashing.sha256().hashBytes(new byte[]{1}).toString()
                            : Hashing.sha256().hashBytes(data).toString());
                }

                String range = exchange.getRequestHeaders().getFirst("Range");
                if (!supportRanges || range == null) {
                    exchange.sendResponseHeaders(200, data.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(data);
                    }
                    return;
                }

                String[] parts = range.substring("bytes=".length()).split("-");
                int start = Integer.parseInt(parts[0]);

                if (start == failOffset && failOffsetTimes.getAndDecrement() > 0) {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                    return;
                }
                int end = Math.min(Integer.parseInt(parts[1]), data.length - 1);
                end = (int) Math.min(end, (long) start + maxRangeLength - 1);
                int length = end - start + 1;
                long total = announcedTotal < 0 ? data.length : announcedTotal;

                exchange.getResponseHeaders().add("Content-Range", "bytes " + start + "-" + end + "/" + total);
                exchange.getResponseHeaders().add("Accept-Ranges", "bytes");
                exchange.sendResponseHeaders(206, extraChunkBytes > 0 ? 0 : length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data, start, length);
                    os.write(new byte[extraChunkBytes]);
                }

                if (start == 0 && replaceDataAfterFirstChunk != null) {
                    this.data = replaceDataAfterFirstChunk;
                    replaceDataAfterFirstChunk = null;
                }
            });
            server.start();
        }

        HttpUrl url() {
            return HttpUrl.get("http://127.0.0.1:" + server.getAddress().getPort());
        }
    }

    private static class Loaded implements ConnectDataset.Consumer {
        volatile byte[] data = null;
        volatile boolean reject = false;
        final AtomicInteger loads = new AtomicInteger(0);
        final AtomicInteger unloads = new AtomicInteger(0);

        @Override
        public void load(Path file) throws Exception {
            if (reject) {
                throw new IOException("Not loadable.");
            }

            data = Files.readAllBytes(file);
            loads.incrementAndGet();
        }

        @Override
        public void unload() {
            data = null;
            unloads.incrementAndGet();
        }
    }

    private static class TestClock extends Clock {
        volatile Instant now = Instant.parse("2026-11-23T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private Path directory;
    private Connect connect;
    private Loaded loaded;
    private TestClock clock;
    private Timer refreshTimer;
    private Counter failing;

    private static byte[] randomData(int size, long seed) {
        byte[] data = new byte[size];
        new Random(seed).nextBytes(data);
        return data;
    }

    private ConnectDataset dataset(boolean ranged) {
        return new ConnectDataset(
                "test",
                directory,
                List.of("data", "test"),
                ranged,
                () -> new ConnectDataset.Source(connect.url(), "test-key"),
                loaded,
                clock,
                10,
                refreshTimer,
                failing
        );
    }

    private Path dataFile() {
        return directory.resolve("test.data");
    }

    private Path metaFile() {
        return directory.resolve("test.meta");
    }

    private void assertNothingOnDisk() throws IOException {
        if (Files.isDirectory(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                assertEquals(files.count(), 0);
            }
        }
    }

    @BeforeMethod
    public void setUp() throws IOException {
        directory = Files.createTempDirectory("connect-dataset-test").resolve("connect");
        connect = new Connect();
        connect.data = randomData(1000, 42);
        connect.start();
        loaded = new Loaded();
        clock = new TestClock();
        refreshTimer = new Timer();
        failing = new Counter();
    }

    @AfterMethod
    public void tearDown() throws IOException {
        connect.server.stop(0);
        directory.toFile().setWritable(true);

        try (Stream<Path> files = Files.walk(directory.getParent())) {
            files.sorted(Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
        }
    }

    @Test
    public void testRangedDownloadMultipleChunks() throws IOException {
        // 20 MiB + 1 byte: three chunks of 8 MiB, the last one partial, with an odd trailing byte.
        connect.data = randomData(20 * MIB + 1, 42);

        assertTrue(dataset(true).refresh());
        assertEquals(loaded.data, connect.data);
        assertEquals(Files.readAllBytes(dataFile()), connect.data);
        assertEquals(connect.requests.get(), 3);
        assertFalse(Files.exists(directory.resolve("test.tmp")));
    }

    @Test
    public void testRangedDownloadManyChunksInParallel() {
        connect.data = randomData(70 * MIB + 12345, 42);

        assertTrue(dataset(true).refresh());
        assertEquals(loaded.data, connect.data);
        assertEquals(connect.requests.get(), 9);
    }

    @Test
    public void testRangedDownloadExactChunkMultiple() {
        connect.data = randomData(16 * MIB, 42);

        assertTrue(dataset(true).refresh());
        assertEquals(loaded.data, connect.data);
        assertEquals(connect.requests.get(), 2);
    }

    @Test
    public void testRangedDownloadSmallFile() {
        assertTrue(dataset(true).refresh());
        assertEquals(loaded.data, connect.data);
        assertEquals(connect.requests.get(), 1);
    }

    @Test
    public void testFallbackToFullResponseWhenRangesUnsupported() {
        connect.data = randomData(10 * MIB, 42);
        connect.supportRanges = false;

        assertTrue(dataset(true).refresh());
        assertEquals(loaded.data, connect.data);
        assertEquals(connect.requests.get(), 1);
    }

    @Test
    public void testUnrangedDownload() {
        assertTrue(dataset(false).refresh());
        assertEquals(loaded.data, connect.data);
        assertEquals(connect.requests.get(), 1);
    }

    @Test
    public void testForbiddenWithoutLocalCopy() throws IOException {
        connect.statusOverride = 403;

        assertFalse(dataset(true).refresh());
        assertNull(loaded.data);
        assertEquals(connect.requests.get(), 1);
        assertNothingOnDisk();
    }

    @Test
    public void testUnexpectedStatusWithoutLocalCopy() throws IOException {
        connect.statusOverride = 404;

        assertFalse(dataset(true).refresh());
        assertFalse(dataset(false).refresh());
        assertNull(loaded.data);
        assertNothingOnDisk();
    }

    @Test
    public void testRetryOnServerError() {
        connect.data = randomData(9 * MIB, 42);
        connect.failFirstN.set(1);

        assertTrue(dataset(true).refresh());
        assertEquals(loaded.data, connect.data);
        assertEquals(connect.requests.get(), 3); // 1 failed + 2 chunks.
    }

    @Test
    public void testRetryOfLaterChunkOnServerError() {
        connect.data = randomData(20 * MIB, 42);
        connect.failOffset = 16 * MIB;
        connect.failOffsetTimes.set(2);

        assertTrue(dataset(true).refresh());
        assertEquals(loaded.data, connect.data);
        assertEquals(connect.requests.get(), 5); // 3 chunks + 2 failed.
    }

    @Test
    public void testLaterChunkFailingForGood() throws IOException {
        connect.data = randomData(20 * MIB, 42);
        connect.failOffset = 16 * MIB;
        connect.failOffsetTimes.set(100);

        assertFalse(dataset(true).refresh());
        assertNull(loaded.data);
        assertNothingOnDisk();
    }

    @Test
    public void testUnchangedDataIsNotDownloadedAgain() throws IOException {
        connect.data = randomData(9 * MIB, 42);
        ConnectDataset dataset = dataset(true);

        assertTrue(dataset.refresh());
        assertEquals(connect.requests.get(), 2);
        assertTrue(Files.readString(metaFile()).contains(String.valueOf(clock.millis())));

        clock.now = clock.now.plus(Duration.ofHours(1));

        assertTrue(dataset.refresh());
        assertEquals(connect.requests.get(), 3);
        assertEquals(connect.conditions, List.of(Connect.etag(connect.data)));
        assertEquals(loaded.loads.get(), 1);
        assertTrue(Files.readString(metaFile()).contains(String.valueOf(clock.millis())));
    }

    @Test
    public void testChangedDataIsDownloaded() throws IOException {
        ConnectDataset dataset = dataset(false);
        assertTrue(dataset.refresh());

        byte[] previous = connect.data;
        connect.data = randomData(2000, 43);

        assertTrue(dataset.refresh());
        assertEquals(connect.conditions, List.of(Connect.etag(previous)));
        assertEquals(loaded.data, connect.data);
        assertEquals(Files.readAllBytes(dataFile()), connect.data);
        assertEquals(loaded.loads.get(), 2);

        assertTrue(dataset.refresh());
        assertEquals(connect.conditions, List.of(Connect.etag(previous), Connect.etag(connect.data)));
        assertEquals(loaded.loads.get(), 2);
    }

    @Test
    public void testLocalCopyIsUsedAfterRestartWithoutRequest() {
        assertTrue(dataset(true).refresh());
        assertEquals(connect.requests.get(), 1);

        loaded = new Loaded();
        ConnectDataset restarted = dataset(true);

        assertTrue(restarted.loadFromDisk());
        assertEquals(loaded.data, connect.data);
        assertEquals(connect.requests.get(), 1);

        assertTrue(restarted.refresh());
        assertEquals(connect.requests.get(), 2);
        assertEquals(connect.conditions, List.of(Connect.etag(connect.data)));
        assertEquals(loaded.loads.get(), 1);
    }

    @Test
    public void testNoLocalCopy() {
        assertFalse(dataset(true).loadFromDisk());
        assertNull(loaded.data);
        assertEquals(connect.requests.get(), 0);
    }

    @Test
    public void testRefreshLoadsLocalCopyFirst() {
        assertTrue(dataset(true).refresh());

        loaded = new Loaded();

        assertTrue(dataset(true).refresh());
        assertEquals(loaded.loads.get(), 1);
        assertEquals(connect.requests.get(), 2);
        assertEquals(connect.conditions.size(), 1);
    }

    @Test
    public void testForbiddenDeletesLocalCopy() throws IOException {
        ConnectDataset dataset = dataset(true);
        assertTrue(dataset.refresh());

        connect.statusOverride = 403;

        assertFalse(dataset.refresh());
        assertNull(loaded.data);
        assertEquals(loaded.unloads.get(), 1);
        assertNothingOnDisk();

        connect.statusOverride = 0;

        assertTrue(dataset.refresh());
        assertEquals(loaded.data, connect.data);
        assertEquals(connect.conditions.size(), 0);
    }

    @Test
    public void testLocalCopyStaysInUseWhenConnectFails() throws IOException {
        ConnectDataset dataset = dataset(true);
        assertTrue(dataset.refresh());

        connect.statusOverride = 500;
        clock.now = clock.now.plus(Duration.ofDays(29));

        assertTrue(dataset.refresh());
        assertEquals(loaded.data, connect.data);
        assertEquals(loaded.unloads.get(), 0);
        assertTrue(Files.exists(dataFile()));
    }

    @Test
    public void testLocalCopyStaysInUseWhenConnectIsUnreachable() {
        ConnectDataset dataset = dataset(true);
        assertTrue(dataset.refresh());

        connect.server.stop(0);

        assertTrue(dataset.refresh());
        assertEquals(loaded.data, connect.data);
    }

    @Test
    public void testUnconfirmedLocalCopyExpires() throws IOException {
        ConnectDataset dataset = dataset(true);
        assertTrue(dataset.refresh());

        connect.statusOverride = 500;
        clock.now = clock.now.plus(ConnectDataset.MAX_UNCONFIRMED_AGE).plusSeconds(1);

        assertFalse(dataset.refresh());
        assertNull(loaded.data);
        assertEquals(loaded.unloads.get(), 1);
        assertNothingOnDisk();
    }

    @Test
    public void testConfirmationCountsWhenItCannotBeWritten() {
        ConnectDataset dataset = dataset(true);
        assertTrue(dataset.refresh());

        directory.toFile().setWritable(false);
        clock.now = clock.now.plus(ConnectDataset.MAX_UNCONFIRMED_AGE).plusSeconds(1);

        // Connect confirms the copy, the new confirmation time cannot be stored.
        assertTrue(dataset.refresh());
        assertEquals(connect.conditions.size(), 1);

        connect.statusOverride = 500;

        assertTrue(dataset.refresh());
        assertEquals(loaded.data, connect.data);
        assertEquals(loaded.unloads.get(), 0);
    }

    @Test
    public void testConfirmationExtendsLifetime() {
        ConnectDataset dataset = dataset(true);
        assertTrue(dataset.refresh());

        clock.now = clock.now.plus(Duration.ofDays(20));
        assertTrue(dataset.refresh());

        connect.statusOverride = 500;
        clock.now = clock.now.plus(Duration.ofDays(20));

        assertTrue(dataset.refresh());
        assertEquals(loaded.data, connect.data);
    }

    @Test
    public void testExpiredLocalCopyIsNotLoadedAfterRestart() throws IOException {
        assertTrue(dataset(true).refresh());

        clock.now = clock.now.plus(ConnectDataset.MAX_UNCONFIRMED_AGE).plusSeconds(1);
        loaded = new Loaded();

        assertFalse(dataset(true).loadFromDisk());
        assertNull(loaded.data);
        assertEquals(loaded.loads.get(), 0);
        assertNothingOnDisk();
    }

    @Test
    public void testUnloadableDownloadKeepsPreviousCopy() throws IOException {
        ConnectDataset dataset = dataset(true);
        assertTrue(dataset.refresh());

        byte[] previous = connect.data;
        connect.data = randomData(2000, 43);
        loaded.reject = true;

        assertTrue(dataset.refresh());
        assertEquals(loaded.data, previous);
        assertEquals(Files.readAllBytes(dataFile()), previous);
        assertFalse(Files.exists(directory.resolve("test.tmp")));

        loaded.reject = false;

        assertTrue(dataset.refresh());
        assertEquals(loaded.data, connect.data);
        assertEquals(connect.conditions, List.of(Connect.etag(previous), Connect.etag(previous)));
    }

    @Test
    public void testUnloadableDownloadWithoutPreviousCopy() throws IOException {
        loaded.reject = true;

        assertFalse(dataset(true).refresh());
        assertNothingOnDisk();
    }

    @Test
    public void testUnloadableLocalCopyIsDeleted() throws IOException {
        assertTrue(dataset(true).refresh());

        loaded = new Loaded();
        loaded.reject = true;

        assertFalse(dataset(true).loadFromDisk());
        assertNothingOnDisk();
    }

    @Test
    public void testChecksumMismatchKeepsPreviousCopy() throws IOException {
        connect.data = randomData(9 * MIB, 42);
        ConnectDataset dataset = dataset(true);
        assertTrue(dataset.refresh());

        byte[] previous = connect.data;
        connect.data = randomData(9 * MIB, 43);
        connect.wrongChecksum = true;

        assertTrue(dataset.refresh());
        assertEquals(loaded.data, previous);
        assertEquals(Files.readAllBytes(dataFile()), previous);
        assertEquals(loaded.loads.get(), 1);
    }

    @Test
    public void testChecksumMismatchWithoutPreviousCopy() throws IOException {
        connect.wrongChecksum = true;

        assertFalse(dataset(true).refresh());
        assertNull(loaded.data);
        assertNothingOnDisk();
    }

    @Test
    public void testDataChangingDuringDownloadStartsOver() {
        connect.data = randomData(30 * MIB, 42);
        byte[] replacement = randomData(30 * MIB, 43);
        connect.replaceDataAfterFirstChunk = replacement;

        assertTrue(dataset(true).refresh());
        assertEquals(loaded.data, replacement);
        assertEquals(loaded.loads.get(), 1);
    }

    @Test
    public void testDataChangingDuringDownloadWithoutVersions() {
        // Without ETag the change shows in the checksum of the assembled file.
        connect.sendEtag = false;
        connect.data = randomData(30 * MIB, 42);
        connect.replaceDataAfterFirstChunk = randomData(30 * MIB, 43);

        assertFalse(dataset(true).refresh());
        assertNull(loaded.data);
    }

    @Test
    public void testConnectWithoutVersionsDownloadsEveryTime() {
        connect.sendEtag = false;
        connect.sendChecksum = false;
        ConnectDataset dataset = dataset(true);

        assertTrue(dataset.refresh());
        assertTrue(dataset.refresh());
        assertEquals(connect.requests.get(), 2);
        assertEquals(connect.conditions.size(), 0);
        assertEquals(loaded.loads.get(), 2);

        loaded = new Loaded();
        assertTrue(dataset(true).loadFromDisk());
        assertEquals(loaded.data, connect.data);
    }

    @Test
    public void testTruncatedLocalCopyIsNotLoaded() throws IOException {
        assertTrue(dataset(true).refresh());

        Files.write(dataFile(), new byte[]{1, 2, 3});
        loaded = new Loaded();

        assertFalse(dataset(true).loadFromDisk());
        assertEquals(loaded.loads.get(), 0);
        assertNothingOnDisk();
    }

    @Test
    public void testLocalCopyWithoutMetadataIsNotLoaded() throws IOException {
        assertTrue(dataset(true).refresh());

        Files.delete(metaFile());
        loaded = new Loaded();

        assertFalse(dataset(true).loadFromDisk());
        assertNothingOnDisk();
    }

    @Test
    public void testLeftoverTemporaryFileIsReplaced() throws IOException {
        Files.createDirectories(directory);
        Files.write(directory.resolve("test.tmp"), new byte[]{1, 2, 3});

        assertTrue(dataset(true).refresh());
        assertEquals(loaded.data, connect.data);
        assertFalse(Files.exists(directory.resolve("test.tmp")));
    }

    @Test
    public void testLeftoverTemporaryFileIsDeletedAtStartup() throws IOException {
        assertTrue(dataset(true).refresh());
        Files.write(directory.resolve("test.tmp"), new byte[]{1, 2, 3});

        assertTrue(dataset(true).loadFromDisk());
        assertFalse(Files.exists(directory.resolve("test.tmp")));
        assertEquals(loaded.data, connect.data);
    }

    @Test
    public void testShortRangesAreCompleted() {
        // Without the checksum, a hole left by a short range would go unnoticed.
        connect.sendChecksum = false;
        connect.maxRangeLength = 3 * MIB;
        connect.data = randomData(20 * MIB + 1, 42);

        assertTrue(dataset(true).refresh());
        assertEquals(loaded.data, connect.data);
        assertEquals(connect.requests.get(), 8);
    }

    @Test
    public void testOversizedAnnouncementIsRejected() throws IOException {
        connect.announcedTotal = ConnectDataset.MAX_SIZE + 1;

        assertFalse(dataset(true).refresh());
        assertNull(loaded.data);
        assertEquals(connect.requests.get(), 1);
        assertNothingOnDisk();
    }

    @Test
    public void testChunkLongerThanAnnouncedIsRejected() throws IOException {
        connect.extraChunkBytes = 5;

        assertFalse(dataset(true).refresh());
        assertNull(loaded.data);
        assertNothingOnDisk();
    }

    @Test
    public void testMalformedVersionIsNotSentBack() {
        connect.etagOverride = "not-quoted";

        ConnectDataset dataset = dataset(false);
        assertTrue(dataset.refresh());
        assertTrue(dataset.refresh());

        assertEquals(connect.conditions.size(), 0);
        assertEquals(loaded.loads.get(), 2);
        assertEquals(loaded.data, connect.data);
    }

    @Test
    public void testDeleteRemovesLocalCopy() throws IOException {
        ConnectDataset dataset = dataset(true);
        assertTrue(dataset.refresh());

        dataset.delete();

        assertNull(loaded.data);
        assertEquals(loaded.unloads.get(), 1);
        assertNothingOnDisk();

        assertTrue(dataset.refresh());
        assertEquals(loaded.data, connect.data);
    }

    @Test
    public void testDeleteRemovesCopyThatWasNeverLoaded() throws IOException {
        assertTrue(dataset(true).refresh());

        dataset(true).delete();

        assertEquals(loaded.unloads.get(), 0);
        assertNothingOnDisk();
    }

    @Test
    public void testFailingIsCountedAfterTwoHoursOfFailedRefreshes() {
        ConnectDataset dataset = dataset(true);
        assertTrue(dataset.refresh());

        connect.statusOverride = 500;
        assertTrue(dataset.refresh());
        assertEquals(failing.getCount(), 0);

        clock.now = clock.now.plus(Duration.ofHours(1));
        assertTrue(dataset.refresh());
        assertEquals(failing.getCount(), 0);

        clock.now = clock.now.plus(Duration.ofHours(1));
        assertTrue(dataset.refresh());
        assertEquals(failing.getCount(), 1);

        clock.now = clock.now.plus(Duration.ofHours(1));
        assertTrue(dataset.refresh());
        assertEquals(failing.getCount(), 1);

        connect.statusOverride = 0;
        assertTrue(dataset.refresh());
        assertEquals(failing.getCount(), 0);
    }

    @Test
    public void testSuccessBetweenFailuresRestartsTheFailingPeriod() {
        ConnectDataset dataset = dataset(true);

        connect.statusOverride = 500;
        assertFalse(dataset.refresh());

        clock.now = clock.now.plus(Duration.ofHours(1));
        connect.statusOverride = 0;
        assertTrue(dataset.refresh());

        clock.now = clock.now.plus(Duration.ofHours(1));
        connect.statusOverride = 500;
        assertTrue(dataset.refresh());
        assertEquals(failing.getCount(), 0);
    }

    @Test
    public void testForbiddenAndDeleteAreNotFailing() {
        ConnectDataset dataset = dataset(true);

        connect.statusOverride = 500;
        assertFalse(dataset.refresh());
        clock.now = clock.now.plus(Duration.ofHours(3));
        assertFalse(dataset.refresh());
        assertEquals(failing.getCount(), 1);

        connect.statusOverride = 403;
        assertFalse(dataset.refresh());
        assertEquals(failing.getCount(), 0);

        connect.statusOverride = 500;
        assertFalse(dataset.refresh());
        clock.now = clock.now.plus(Duration.ofHours(3));
        assertFalse(dataset.refresh());
        assertEquals(failing.getCount(), 1);

        dataset.delete();
        assertEquals(failing.getCount(), 0);
    }

    @Test
    public void testUnwritableDirectoryCountsAsFailing() {
        ConnectDataset dataset = dataset(true);
        assertTrue(dataset.refresh());

        directory.toFile().setWritable(false);

        assertTrue(dataset.refresh());
        clock.now = clock.now.plus(Duration.ofHours(2));
        assertTrue(dataset.refresh());
        assertEquals(failing.getCount(), 1);

        directory.toFile().setWritable(true);
        assertTrue(dataset.refresh());
        assertEquals(failing.getCount(), 0);
    }

    @Test
    public void testRefreshesAreTimed() {
        ConnectDataset dataset = dataset(true);
        dataset.refresh();
        dataset.refresh();

        assertEquals(refreshTimer.getCount(), 2);
    }

    @Test
    public void testParseContentRange() throws IOException {
        ConnectDataset.ContentRange r = ConnectDataset.parseContentRange("bytes 0-8388607/104857600");
        assertEquals(r.start(), 0);
        assertEquals(r.end(), 8388607);
        assertEquals(r.total(), 104857600);

        r = ConnectDataset.parseContentRange("bytes 100-100/101");
        assertEquals(r.start(), 100);
        assertEquals(r.end(), 100);
        assertEquals(r.total(), 101);
    }

    @Test(expectedExceptions = IOException.class)
    public void testParseContentRangeMissing() throws IOException {
        ConnectDataset.parseContentRange(null);
    }

    @Test(expectedExceptions = IOException.class)
    public void testParseContentRangeUnknownTotal() throws IOException {
        ConnectDataset.parseContentRange("bytes 0-100/*");
    }

    @Test(expectedExceptions = IOException.class)
    public void testParseContentRangeEndBeyondTotal() throws IOException {
        ConnectDataset.parseContentRange("bytes 0-100/100");
    }

    @Test(expectedExceptions = IOException.class)
    public void testParseContentRangeGarbage() throws IOException {
        ConnectDataset.parseContentRange("items 0-100/200");
    }

}
