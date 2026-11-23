package app.nzyme.core.connect;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.util.FilePermissions;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.MoreFiles;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import jakarta.annotation.Nullable;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConnectDataset {

    private static final Logger LOG = LogManager.getLogger(ConnectDataset.class);

    static final Duration MAX_UNCONFIRMED_AGE = Duration.ofDays(30);

    // Refreshes run every hour. A single failed one is not reported.
    static final Duration FAILING_AFTER = Duration.ofHours(2);

    static final String DIRECTORY_NAME = "connect";

    private static final String CHECKSUM_HEADER = "X-Nzyme-Data-Sha256";

    // Connect is behind a proxy that cuts responses off after about two minutes. Large files come in chunks.
    private static final long CHUNK_SIZE = 8L * 1024 * 1024;
    private static final int CHUNK_PARALLELISM = 4;
    private static final int ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2000;

    static final long MAX_SIZE = 8L * 1024 * 1024 * 1024;

    // Anything else is not sent back to Connect, which answers a malformed If-None-Match with HTTP 400.
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(W/)?\"[\\x21\\x23-\\x7E]{1,256}\"$");

    private static final Pattern CONTENT_RANGE_PATTERN = Pattern.compile("^bytes (\\d+)-(\\d+)/(\\d+)$");

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .followRedirects(true)
            .build();

    private static final ObjectMapper OM = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public interface Consumer {
        void load(Path file) throws Exception;
        void unload();
    }

    public record Source(HttpUrl apiUri, String apiKey) { }

    record Meta(@JsonProperty("version") @Nullable String version,
                @JsonProperty("confirmed_at") long confirmedAt,
                @JsonProperty("size") long size) { }

    record ContentRange(long start, long end, long total) { }

    private enum Outcome { NOT_MODIFIED, DOWNLOADED, FORBIDDEN }

    private record Fetch(Outcome outcome, @Nullable String version) { }

    private static class VersionChangedException extends IOException {
        VersionChangedException(String message) {
            super(message);
        }
    }

    private final String name;
    private final List<String> pathSegments;
    private final boolean ranged;
    private final Supplier<Source> source;
    private final Consumer consumer;
    private final Clock clock;
    private final long retryDelayMs;
    private final Timer refreshTimer;
    private final Counter failing;

    private final Path directory;
    private final Path dataFile;
    private final Path metaFile;
    private final Path tempFile;

    private boolean loaded = false;

    // Also kept in memory: a confirmation counts even if it could not be written to disk.
    private long confirmedAt = 0;

    private long failingSince = 0;
    private boolean countedAsFailing = false;

    public static ConnectDataset create(NzymeNode nzyme,
                                        String name,
                                        List<String> pathSegments,
                                        boolean ranged,
                                        Consumer consumer) {
        return new ConnectDataset(
                name,
                nzyme.getDataDirectory().resolve(DIRECTORY_NAME),
                pathSegments,
                ranged,
                () -> new Source(HttpUrl.get(nzyme.getConnect().getApiUri()), nzyme.getConnect().getApiKey()),
                consumer,
                Clock.systemUTC(),
                RETRY_DELAY_MS,
                nzyme.getMetrics().timer(MetricNames.CONNECT_DATA_REFRESH_TIMER),
                nzyme.getMetrics().counter(MetricNames.CONNECT_DATA_FAILING)
        );
    }

    ConnectDataset(String name,
                   Path directory,
                   List<String> pathSegments,
                   boolean ranged,
                   Supplier<Source> source,
                   Consumer consumer,
                   Clock clock,
                   long retryDelayMs,
                   Timer refreshTimer,
                   Counter failing) {
        this.name = name;
        this.pathSegments = pathSegments;
        this.ranged = ranged;
        this.source = source;
        this.consumer = consumer;
        this.clock = clock;
        this.retryDelayMs = retryDelayMs;
        this.refreshTimer = refreshTimer;
        this.failing = failing;

        this.directory = directory;
        this.dataFile = directory.resolve(name + ".data");
        this.metaFile = directory.resolve(name + ".meta");
        this.tempFile = directory.resolve(name + ".tmp");
    }

    public synchronized boolean loadFromDisk() {
        if (loaded) {
            return true;
        }

        Optional<Meta> meta = readMeta();
        if (meta.isEmpty()) {
            deleteFiles();
            return false;
        }

        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            LOG.warn("Could not delete [{}].", tempFile, e);
        }

        if (isExpired(meta.get().confirmedAt())) {
            LOG.warn("Deleting local copy of Connect data [{}] because Connect has not confirmed it for more " +
                    "than [{}] days.", name, MAX_UNCONFIRMED_AGE.toDays());
            deleteFiles();
            return false;
        }

        try {
            consumer.load(dataFile);
            loaded = true;
            confirmedAt = meta.get().confirmedAt();
            return true;
        } catch (Exception e) {
            LOG.error("Could not load local copy of Connect data [{}]. Deleting it.", name, e);
            discard();
            return false;
        }
    }

    /*
     * Returns if data is loaded afterwards. A copy that could not be checked, for example because Connect is
     * not reachable, stays in use until it expires.
     */
    public synchronized boolean refresh() {
        try (Timer.Context ignored = refreshTimer.time()) {
            return refreshTimed();
        }
    }

    public synchronized void delete() {
        if (loaded || Files.exists(dataFile)) {
            LOG.info("Connect is disabled. Deleting local copy of Connect data [{}].", name);
        }

        discard();
        markHealthy();
    }

    private boolean refreshTimed() {
        loadFromDisk();

        Optional<Meta> meta = loaded ? readMeta() : Optional.empty();
        String knownVersion = meta.map(Meta::version)
                .filter(version -> VERSION_PATTERN.matcher(version).matches())
                .orElse(null);

        try {
            Fetch fetch = fetch(knownVersion);

            switch (fetch.outcome()) {
                case NOT_MODIFIED:
                    if (knownVersion == null) {
                        throw new IOException("Connect answered HTTP 304 to an unconditional request.");
                    }

                    confirmedAt = clock.millis();
                    writeMeta(new Meta(meta.get().version(), confirmedAt, meta.get().size()));
                    markHealthy();
                    return true;
                case FORBIDDEN:
                    if (loaded || Files.exists(dataFile)) {
                        LOG.info("Connect data [{}] is no longer provided to this cluster. Deleting local copy.", name);
                    }

                    discard();
                    markHealthy();
                    return false;
                case DOWNLOADED:
                    // Chunks are written out of order. Without this, a power loss could leave a file of the
                    // right size with holes in it, which Connect would then confirm as current.
                    try (FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.WRITE)) {
                        channel.force(true);
                    }

                    try {
                        // Loaded before it replaces the current copy, so that a broken download costs nothing.
                        consumer.load(tempFile);
                    } catch (Exception e) {
                        Files.deleteIfExists(tempFile);
                        throw new IOException("Downloaded data could not be loaded.", e);
                    }

                    loaded = true;
                    confirmedAt = clock.millis();

                    long size = Files.size(tempFile);
                    Files.deleteIfExists(metaFile);
                    Files.move(tempFile, dataFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    writeMeta(new Meta(fetch.version(), confirmedAt, size));

                    LOG.info("Connect data [{}] updated. Size: [{}] bytes.", name, size);
                    markHealthy();
                    return true;
                default:
                    throw new IllegalStateException("Unexpected outcome: " + fetch.outcome());
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            LOG.error("Could not refresh Connect data [{}].", name, e);
            markFailed();

            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) { }

            if (loaded && isExpired(confirmedAt)) {
                LOG.warn("Deleting Connect data [{}] because Connect has not confirmed it for more than [{}] days.",
                        name, MAX_UNCONFIRMED_AGE.toDays());
                discard();
            }

            return loaded;
        }
    }

    private void markHealthy() {
        failingSince = 0;

        if (countedAsFailing) {
            countedAsFailing = false;
            failing.dec();
        }
    }

    private void markFailed() {
        if (failingSince == 0) {
            failingSince = clock.millis();
        }

        if (!countedAsFailing && clock.millis() - failingSince >= FAILING_AFTER.toMillis()) {
            countedAsFailing = true;
            failing.inc();
        }
    }

    private boolean isExpired(long confirmedAt) {
        return clock.millis() - confirmedAt > MAX_UNCONFIRMED_AGE.toMillis();
    }

    private Optional<Meta> readMeta() {
        try {
            if (!Files.isRegularFile(metaFile) || !Files.isRegularFile(dataFile)) {
                return Optional.empty();
            }

            Meta meta = OM.readValue(metaFile.toFile(), Meta.class);
            if (meta.size() != Files.size(dataFile)) {
                return Optional.empty();
            }

            return Optional.of(meta);
        } catch (Exception e) {
            LOG.warn("Could not read metadata of local copy of Connect data [{}].", name, e);
            return Optional.empty();
        }
    }

    private void writeMeta(Meta meta) throws IOException {
        Path temp = directory.resolve(name + ".meta.tmp");
        Files.write(temp, OM.writeValueAsBytes(meta));
        Files.move(temp, metaFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private void discard() {
        deleteFiles();

        if (loaded) {
            loaded = false;
            consumer.unload();
        }
    }

    private void deleteFiles() {
        for (Path file : List.of(metaFile, dataFile, tempFile)) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                LOG.error("Could not delete [{}].", file, e);
            }
        }
    }

    private void prepareDirectory() throws IOException {
        if (!Files.isDirectory(directory)) {
            if (FilePermissions.isSupported()) {
                Files.createDirectories(directory,
                        PosixFilePermissions.asFileAttribute(FilePermissions.OWNER_READ_WRITE_EXECUTE));
            } else {
                Files.createDirectories(directory);
            }
        }

        Files.deleteIfExists(tempFile);
    }

    private Request.Builder request(@Nullable String knownVersion) {
        Source source = this.source.get();

        HttpUrl.Builder url = source.apiUri().newBuilder();
        pathSegments.forEach(url::addPathSegment);

        Request.Builder request = new Request.Builder()
                .get()
                .url(url.build())
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + source.apiKey())
                .addHeader(HttpHeaders.USER_AGENT, "nzyme-node");

        if (knownVersion != null) {
            request.addHeader(HttpHeaders.IF_NONE_MATCH, knownVersion);
        }

        return request;
    }

    private Fetch fetch(@Nullable String knownVersion) throws IOException, InterruptedException {
        if (!ranged) {
            try (Response response = execute(request(knownVersion).build())) {
                switch (response.code()) {
                    case 304:
                        return new Fetch(Outcome.NOT_MODIFIED, knownVersion);
                    case 403:
                        return new Fetch(Outcome.FORBIDDEN, null);
                    case 200:
                        writeBody(response);
                        return new Fetch(Outcome.DOWNLOADED, response.header(HttpHeaders.ETAG));
                    default:
                        throw new IOException("Expected HTTP 200, 304 or 403 but got HTTP " + response.code());
                }
            }
        }

        try {
            return fetchRanged(knownVersion);
        } catch (VersionChangedException e) {
            LOG.info("Connect data [{}] changed during download. Starting over.", name);
            return fetchRanged(knownVersion);
        }
    }

    private Fetch fetchRanged(@Nullable String knownVersion) throws IOException, InterruptedException {
        long started = clock.millis();

        ExecutorService pool = null;

        try (Response first = execute(chunkRequest(knownVersion, 0, CHUNK_SIZE - 1))) {
            switch (first.code()) {
                case 304:
                    return new Fetch(Outcome.NOT_MODIFIED, knownVersion);
                case 403:
                    return new Fetch(Outcome.FORBIDDEN, null);
                case 200:
                    // No support for range requests.
                    writeBody(first);
                    verifyChecksum(first.header(CHECKSUM_HEADER));
                    return new Fetch(Outcome.DOWNLOADED, first.header(HttpHeaders.ETAG));
                case 206:
                    break;
                default:
                    throw new IOException("Expected HTTP 200, 206, 304 or 403 but got HTTP " + first.code());
            }

            String version = first.header(HttpHeaders.ETAG);
            ContentRange firstRange = parseContentRange(first.header(HttpHeaders.CONTENT_RANGE));
            if (firstRange.start() != 0) {
                throw new IOException("Received unexpected range start [" + firstRange.start() + "], expected [0].");
            }

            long total = firstRange.total();
            if (total > MAX_SIZE) {
                throw new IOException("Connect announced [" + total + "] bytes, more than the allowed ["
                        + MAX_SIZE + "] bytes.");
            }

            prepareDirectory();
            try (FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                writeChunk(channel, first, firstRange);

                pool = Executors.newFixedThreadPool(CHUNK_PARALLELISM, new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat("connect-data-" + name + "-%d")
                        .build());

                List<Future<?>> chunks = new ArrayList<>();
                for (long offset = firstRange.end() + 1; offset < total; offset += CHUNK_SIZE) {
                    final long chunkOffset = offset;
                    chunks.add(pool.submit(() -> {
                        downloadChunk(channel, chunkOffset, total, version);
                        return null;
                    }));
                }

                for (Future<?> chunk : chunks) {
                    try {
                        chunk.get();
                    } catch (ExecutionException e) {
                        if (e.getCause() instanceof IOException io) {
                            throw io;
                        }

                        throw new IOException("Chunk download failed.", e.getCause());
                    }
                }

                if (channel.size() != total) {
                    throw new IOException("Downloaded [" + channel.size() + "] bytes, expected [" + total + "].");
                }
            }

            verifyChecksum(first.header(CHECKSUM_HEADER));

            LOG.info("Download of Connect data [{}] complete. Size: [{}] bytes, Duration: [{}] ms.",
                    name, total, clock.millis() - started);

            return new Fetch(Outcome.DOWNLOADED, version);
        } finally {
            if (pool != null) {
                pool.shutdownNow();
            }
        }
    }

    private void downloadChunk(FileChannel channel, long offset, long total, @Nullable String version)
            throws IOException, InterruptedException {
        long end = Math.min(offset + CHUNK_SIZE, total) - 1;

        // Connect may answer with less than was asked for. The rest is requested until the chunk is complete.
        long next = offset;
        while (next <= end) {
            next = downloadRange(channel, next, end, total, version) + 1;
        }
    }

    private long downloadRange(FileChannel channel, long start, long end, long total, @Nullable String version)
            throws IOException, InterruptedException {
        IOException lastException = null;

        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            if (attempt > 1) {
                Thread.sleep(retryDelayMs * (attempt - 1));
            }

            try (Response response = HTTP.newCall(chunkRequest(null, start, end)).execute()) {
                if (response.code() != 206) {
                    throw new IOException("Expected HTTP 206 but got HTTP " + response.code());
                }

                ContentRange range = parseContentRange(response.header(HttpHeaders.CONTENT_RANGE));
                if (range.start() != start || range.end() > end) {
                    throw new IOException("Received range [" + range.start() + "-" + range.end()
                            + "], asked for [" + start + "-" + end + "].");
                }

                if (range.total() != total || !Objects.equals(version, response.header(HttpHeaders.ETAG))) {
                    throw new VersionChangedException("Data changed during download.");
                }

                writeChunk(channel, response, range);
                return range.end();
            } catch (VersionChangedException e) {
                throw e;
            } catch (IOException e) {
                lastException = e;
                LOG.warn("Chunk request for Connect data [{}] failed. (Attempt {}/{})",
                        name, attempt, ATTEMPTS, e);
            }
        }

        throw lastException;
    }

    private Request chunkRequest(@Nullable String knownVersion, long start, long end) {
        return request(knownVersion)
                .addHeader(HttpHeaders.RANGE, "bytes=" + start + "-" + end)
                .build();
    }

    private Response execute(Request request) throws IOException, InterruptedException {
        IOException lastException = null;

        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            if (attempt > 1) {
                Thread.sleep(retryDelayMs * (attempt - 1));
            }

            try {
                Response response = HTTP.newCall(request).execute();
                if (response.code() < 500 || attempt == ATTEMPTS) {
                    return response;
                }

                LOG.warn("Request for Connect data [{}] failed with HTTP [{}]. (Attempt {}/{})",
                        name, response.code(), attempt, ATTEMPTS);
                response.close();
            } catch (IOException e) {
                lastException = e;
                LOG.warn("Request for Connect data [{}] failed. (Attempt {}/{})", name, attempt, ATTEMPTS, e);
            }
        }

        throw lastException;
    }

    private static void writeChunk(FileChannel channel, Response response, ContentRange range) throws IOException {
        long expectedLength = range.end() - range.start() + 1;
        if (expectedLength > CHUNK_SIZE) {
            throw new IOException("Content-Range announced [" + expectedLength + "] bytes, more than the ["
                    + CHUNK_SIZE + "] bytes of a chunk.");
        }

        byte[] chunk = ByteStreams.limit(response.body().byteStream(), expectedLength + 1).readAllBytes();
        if (chunk.length != expectedLength) {
            throw new IOException("Received [" + chunk.length + "] bytes but Content-Range announced ["
                    + expectedLength + "] bytes.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(chunk);
        long position = range.start();
        while (buffer.hasRemaining()) {
            position += channel.write(buffer, position);
        }
    }

    private void writeBody(Response response) throws IOException {
        prepareDirectory();

        try (InputStream body = ByteStreams.limit(response.body().byteStream(), MAX_SIZE + 1)) {
            if (Files.copy(body, tempFile) > MAX_SIZE) {
                throw new IOException("Connect sent more than the allowed [" + MAX_SIZE + "] bytes.");
            }
        }
    }

    private void verifyChecksum(@Nullable String expected) throws IOException {
        if (expected == null) {
            return;
        }

        String actual = MoreFiles.asByteSource(tempFile).hash(Hashing.sha256()).toString();
        if (!actual.equalsIgnoreCase(expected.trim())) {
            Files.deleteIfExists(tempFile);
            throw new IOException("Checksum of downloaded data is [" + actual + "], expected [" + expected + "].");
        }
    }

    static ContentRange parseContentRange(@Nullable String header) throws IOException {
        if (header == null) {
            throw new IOException("Missing Content-Range header in partial response.");
        }

        Matcher m = CONTENT_RANGE_PATTERN.matcher(header);
        if (!m.matches()) {
            throw new IOException("Invalid Content-Range header: [" + header + "]");
        }

        long start = Long.parseLong(m.group(1));
        long end = Long.parseLong(m.group(2));
        long total = Long.parseLong(m.group(3));

        if (end < start || end >= total) {
            throw new IOException("Invalid Content-Range header: [" + header + "]");
        }

        return new ContentRange(start, end, total);
    }

}
