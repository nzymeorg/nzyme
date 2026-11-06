package app.nzyme.core.integrations.geoip;

import com.sun.net.httpserver.HttpServer;
import okhttp3.HttpUrl;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

public class GeoIpServiceTest {

    private static byte[] randomData(int size) {
        byte[] data = new byte[size];
        new Random(42).nextBytes(data);
        return data;
    }

    private static HttpServer startServer(byte[] data, boolean supportRanges, int statusOverride,
                                          AtomicInteger requests, AtomicInteger failFirstN) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/data/geoip/ip", exchange -> {
            int n = requests.incrementAndGet();

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

            exchange.getResponseHeaders().add("X-Nzyme-GeoIp-Type", "IPinfoStandard");

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
            int end = Math.min(Integer.parseInt(parts[1]), data.length - 1);
            int length = end - start + 1;

            exchange.getResponseHeaders().add("Content-Range", "bytes " + start + "-" + end + "/" + data.length);
            exchange.getResponseHeaders().add("Accept-Ranges", "bytes");
            exchange.sendResponseHeaders(206, length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data, start, length);
            }
        });
        server.start();
        return server;
    }

    private static HttpUrl url(HttpServer server) {
        return HttpUrl.get("http://127.0.0.1:" + server.getAddress().getPort())
                .newBuilder()
                .addPathSegment("data")
                .addPathSegment("geoip")
                .addPathSegment("ip")
                .build();
    }

    @Test
    public void testRangedDownloadMultipleChunks() throws IOException {
        // 20 MiB + 1 byte: three chunks of 8 MiB, the last one partial, with an odd trailing byte.
        byte[] data = randomData(20 * 1024 * 1024 + 1);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startServer(data, true, 0, requests, new AtomicInteger(0));

        try {
            Optional<byte[]> result = GeoIpService.downloadMmdb(url(server), "test-key");
            assertTrue(result.isPresent());
            assertEquals(result.get(), data);
            assertEquals(requests.get(), 3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testRangedDownloadExactChunkMultiple() throws IOException {
        byte[] data = randomData(16 * 1024 * 1024);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startServer(data, true, 0, requests, new AtomicInteger(0));

        try {
            Optional<byte[]> result = GeoIpService.downloadMmdb(url(server), "test-key");
            assertTrue(result.isPresent());
            assertEquals(result.get(), data);
            assertEquals(requests.get(), 2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testRangedDownloadSmallFile() throws IOException {
        byte[] data = randomData(1000);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startServer(data, true, 0, requests, new AtomicInteger(0));

        try {
            Optional<byte[]> result = GeoIpService.downloadMmdb(url(server), "test-key");
            assertTrue(result.isPresent());
            assertEquals(result.get(), data);
            assertEquals(requests.get(), 1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testFallbackToFullResponseWhenRangesUnsupported() throws IOException {
        byte[] data = randomData(10 * 1024 * 1024);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startServer(data, false, 0, requests, new AtomicInteger(0));

        try {
            Optional<byte[]> result = GeoIpService.downloadMmdb(url(server), "test-key");
            assertTrue(result.isPresent());
            assertEquals(result.get(), data);
            assertEquals(requests.get(), 1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testForbiddenReturnsEmpty() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startServer(randomData(100), true, 403, requests, new AtomicInteger(0));

        try {
            Optional<byte[]> result = GeoIpService.downloadMmdb(url(server), "test-key");
            assertTrue(result.isEmpty());
            assertEquals(requests.get(), 1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testUnexpectedStatusReturnsEmpty() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startServer(randomData(100), true, 404, requests, new AtomicInteger(0));

        try {
            Optional<byte[]> result = GeoIpService.downloadMmdb(url(server), "test-key");
            assertTrue(result.isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testChunkRetryOnServerError() throws IOException {
        byte[] data = randomData(9 * 1024 * 1024);
        AtomicInteger requests = new AtomicInteger();
        // First request fails with 503 once, then succeeds.
        HttpServer server = startServer(data, true, 0, requests, new AtomicInteger(1));

        try {
            Optional<byte[]> result = GeoIpService.downloadMmdb(url(server), "test-key");
            assertTrue(result.isPresent());
            assertEquals(result.get(), data);
            assertEquals(requests.get(), 3); // 1 failed + 2 chunks.
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testParseContentRange() {
        GeoIpService.ContentRange r = GeoIpService.parseContentRange("bytes 0-8388607/104857600");
        assertEquals(r.start(), 0);
        assertEquals(r.end(), 8388607);
        assertEquals(r.total(), 104857600);

        r = GeoIpService.parseContentRange("bytes 100-100/101");
        assertEquals(r.start(), 100);
        assertEquals(r.end(), 100);
        assertEquals(r.total(), 101);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testParseContentRangeMissing() {
        GeoIpService.parseContentRange(null);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testParseContentRangeUnknownTotal() {
        GeoIpService.parseContentRange("bytes 0-100/*");
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testParseContentRangeEndBeyondTotal() {
        GeoIpService.parseContentRange("bytes 0-100/100");
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testParseContentRangeGarbage() {
        GeoIpService.parseContentRange("items 0-100/200");
    }

}
