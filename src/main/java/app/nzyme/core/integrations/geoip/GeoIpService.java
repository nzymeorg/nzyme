package app.nzyme.core.integrations.geoip;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.connect.ConnectRegistryKeys;
import app.nzyme.core.integrations.geoip.ipinfo.IpInfoCountryAsnLookupResult;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.maxmind.db.Reader;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeoIpService {

    private static final Logger LOG = LogManager.getLogger(GeoIpService.class);

    private static final long DOWNLOAD_CHUNK_SIZE = 8L * 1024 * 1024; // 8 MiB
    private static final int DOWNLOAD_CHUNK_RETRIES = 3;
    private static final long DOWNLOAD_CHUNK_RETRY_DELAY_MS = 2000;

    private static final Pattern CONTENT_RANGE_PATTERN = Pattern.compile("^bytes (\\d+)-(\\d+)/(\\d+)$");

    private final NzymeNode nzyme;
    private final Timer lookupTimerUncached;

    private final LoadingCache<InetAddress, Optional<GeoIpLookupResult>> cache;

    private final ReentrantLock lock = new ReentrantLock();
    private Reader mmdb = null;

    private final ScheduledExecutorService refresher;

    // Can be disabled if Connect is not set up or GeoIp data source is not enabled in Connect.
    private boolean isEnabled = false;

    public GeoIpService(NzymeNode nzyme) {
        this.nzyme = nzyme;

        this.cache = CacheBuilder.newBuilder() // TODO Configurable
                .maximumSize(10000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build(new CacheLoader<>() {
                    @Override
                    public Optional<GeoIpLookupResult> load(@NotNull InetAddress address) {
                        return mmdbLookup(address);
                    }
                });

        this.lookupTimerUncached = nzyme.getMetrics().timer(
                MetricRegistry.name(MetricNames.GEOIP_LOOKUP_TIMING_UNCACHED)
        );

        nzyme.getMetrics().register(MetricNames.GEOIP_CACHE_SIZE, new Gauge<Long>() {
            @Override
            public Long getValue() {
                return cache.size();
            }
        });

        // Reload on configuration change.
        nzyme.getRegistryChangeMonitor()
                .onChange("core", ConnectRegistryKeys.CONNECT_API_KEY.key(), this::reload);
        nzyme.getRegistryChangeMonitor()
                .onChange("core", ConnectRegistryKeys.CONNECT_ENABLED.key(), this::reload);

        // Reload if provided services by Connect change.
        nzyme.getRegistryChangeMonitor()
                .onChange("core", ConnectRegistryKeys.PROVIDED_SERVICES.key(), this::reload);

        refresher = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat("geoip-refresher-%d")
                        .build()
        );

        refresher.scheduleAtFixedRate(this::reload, 1, 1, TimeUnit.HOURS);
    }

    private void reload() {
        // Reload with new registry settings.
        initialize();

        // Clear cache.
        cache.invalidateAll();
    }

    public void initialize() {
        // IMPORTANT: This method will also be called on configuration changes.

        // Update connect status.
        this.isEnabled = nzyme.getConnect().isEnabled();
        if (!this.isEnabled) {
            return;
        }

        lock.lock();

        try {
            // Load MMDB from connect.
            Optional<byte[]> bytes = fetchMmdbFromConnect();

            // Check if GeoIP data was disabled in Connect for this cluster.
            if (bytes.isEmpty()) {
                this.isEnabled = false;
                return;
            }

            // We have (new) data. Close current reader in preparation for switch.
            if (this.mmdb != null) {
                this.mmdb.close();
            }

            // Create new reader with (new) data.
            this.mmdb = new Reader(new ByteArrayInputStream(bytes.get()));
            this.isEnabled = true;

            LOG.info("GeoIP database loaded. Type: [{}]", mmdb.getMetadata().getDatabaseType());
        } catch (Exception e) {
            LOG.error("Could not create MMDB reader.", e);
            this.isEnabled = false;
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public Optional<GeoIpLookupResult> lookup(InetAddress address) {
        if (!isEnabled) {
            return Optional.empty();
        }

        return cache.getUnchecked(address);
    }

    private Optional<GeoIpLookupResult> mmdbLookup(InetAddress address) {
        try (Timer.Context ignored = lookupTimerUncached.time()) {
            lock.lock();

            try {
                IpInfoCountryAsnLookupResult lookup = mmdb.get(address, IpInfoCountryAsnLookupResult.class);

                if (lookup == null) {
                    return Optional.empty();
                }

                GeoIpGeoInformation geo = GeoIpGeoInformation.create(
                        lookup.getCity(),
                        lookup.getCountryCode(),
                        countryName(lookup),
                        lookup.getLatitude(),
                        lookup.getLongitude()
                );

                Integer asNumber;
                if (lookup.getAsNumber() != null) {
                    String[] parts = lookup.getAsNumber().split("^AS");
                    if (parts.length > 1) {
                        asNumber = Integer.parseInt(parts[1]);
                    } else {
                        asNumber = null;
                    }
                } else {
                    asNumber = null;
                }

                GeoIpAsnInformation asn = GeoIpAsnInformation.create(
                        asNumber,
                        lookup.getAsName(),
                        lookup.getAsDomain()
                );

                return Optional.of(GeoIpLookupResult.create(asn, geo));
            } catch (Exception e) {
                LOG.info("Could not look up IP address [{}].", address, e);
                return Optional.empty();
            } finally {
                lock.unlock();
            }
        }
    }

    @Nullable
    private static String countryName(IpInfoCountryAsnLookupResult lookup) {
        if (lookup.getCountryName() != null) {
            return lookup.getCountryName();
        }

        if (lookup.getCountryCode() == null) {
            return null;
        }

        String name = Locale.of("", lookup.getCountryCode()).getDisplayCountry(Locale.ENGLISH);

        // Locale returns the code itself if it does not know the country.
        if (name.isEmpty() || name.equalsIgnoreCase(lookup.getCountryCode()) || name.equals("Unknown Region")) {
            return null;
        }

        return name;
    }

    private Optional<byte[]> fetchMmdbFromConnect() {
        LOG.debug("Loading new GeoIP data from Connect.");

        HttpUrl url = HttpUrl.get(nzyme.getConnect().getApiUri())
                .newBuilder()
                .addPathSegment("data")
                .addPathSegment("geoip")
                .addPathSegment("ip")
                .build();

        return downloadMmdb(url, nzyme.getConnect().getApiKey());
    }

    static Optional<byte[]> downloadMmdb(HttpUrl url, String apiKey) {
        long started = System.currentTimeMillis();

        try {
            OkHttpClient c = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.MINUTES)
                    .followRedirects(true)
                    .build();

            ByteArrayOutputStream data = new ByteArrayOutputStream();
            long offset = 0;
            long total = -1;
            int chunks = 0;
            String type = "unknown";

            while (total < 0 || offset < total) {
                long rangeEnd = offset + DOWNLOAD_CHUNK_SIZE - 1;
                if (total >= 0 && rangeEnd > total - 1) {
                    rangeEnd = total - 1;
                }

                Request request = new Request.Builder()
                        .get()
                        .url(url)
                        .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .addHeader(HttpHeaders.ACCEPT, "application/octet-stream")
                        .addHeader(HttpHeaders.RANGE, "bytes=" + offset + "-" + rangeEnd)
                        .addHeader(HttpHeaders.USER_AGENT, "nzyme-node")
                        .build();

                try (Response response = executeWithRetries(c, request)) {
                    if (response.code() == 403) {
                        // GeoIP data disabled in Connect for this cluster.
                        return Optional.empty();
                    }

                    if (response.body() == null) {
                        throw new RuntimeException("Empty response.");
                    }

                    if (chunks == 0) {
                        type = response.header("X-Nzyme-GeoIp-Type", "unknown");
                    }

                    if (response.code() == 200) {
                        // Connect does not support range requests. Read the full body.
                        if (offset != 0) {
                            throw new RuntimeException("Received full response (HTTP 200) during ranged download.");
                        }

                        byte[] bytes = response.body().bytes();
                        LOG.info("GeoIP data download from Connect complete. Data type: [{}], Size: [{}] bytes, " +
                                        "Single response, Duration: [{}] ms.",
                                type, bytes.length, System.currentTimeMillis() - started);

                        return Optional.of(bytes);
                    }

                    if (response.code() != 206) {
                        throw new RuntimeException("Expected HTTP 200, 206 or 403 but got HTTP " + response.code());
                    }

                    ContentRange contentRange = parseContentRange(response.header("Content-Range"));
                    if (contentRange.start() != offset) {
                        throw new RuntimeException("Received unexpected range start [" + contentRange.start()
                                + "], expected [" + offset + "].");
                    }

                    byte[] chunk = response.body().bytes();
                    long expectedLength = contentRange.end() - contentRange.start() + 1;
                    if (chunk.length != expectedLength) {
                        throw new RuntimeException("Received [" + chunk.length + "] bytes but Content-Range " +
                                "announced [" + expectedLength + "] bytes.");
                    }

                    data.write(chunk);
                    total = contentRange.total();
                    offset = contentRange.end() + 1;
                    chunks++;

                    LOG.debug("Downloaded GeoIP data chunk [{}]: [{}/{}] bytes.", chunks, offset, total);
                }
            }

            byte[] bytes = data.toByteArray();

            LOG.info("GeoIP data download from Connect complete. Data type: [{}], Size: [{}] bytes, " +
                            "Chunks: [{}], Duration: [{}] ms.",
                    type, bytes.length, chunks, System.currentTimeMillis() - started);

            return Optional.of(bytes);
        } catch (Exception e) {
            LOG.error("Could not download GeoIP data from Connect.", e);
            return Optional.empty();
        }
    }

    private static Response executeWithRetries(OkHttpClient client, Request request) throws IOException, InterruptedException {
        IOException lastException = null;

        for (int attempt = 1; attempt <= DOWNLOAD_CHUNK_RETRIES; attempt++) {
            try {
                Response response = client.newCall(request).execute();

                if (response.code() >= 500 && attempt < DOWNLOAD_CHUNK_RETRIES) {
                    LOG.warn("GeoIP data chunk request to Connect failed with HTTP [{}]. Retrying. (Attempt {}/{})",
                            response.code(), attempt, DOWNLOAD_CHUNK_RETRIES);
                    response.close();
                    Thread.sleep(DOWNLOAD_CHUNK_RETRY_DELAY_MS * attempt);
                    continue;
                }

                return response;
            } catch (IOException e) {
                lastException = e;

                if (attempt < DOWNLOAD_CHUNK_RETRIES) {
                    LOG.warn("GeoIP data chunk request to Connect failed. Retrying. (Attempt {}/{})",
                            attempt, DOWNLOAD_CHUNK_RETRIES, e);
                    Thread.sleep(DOWNLOAD_CHUNK_RETRY_DELAY_MS * attempt);
                }
            }
        }

        throw lastException;
    }

    static ContentRange parseContentRange(@Nullable String header) {
        if (header == null) {
            throw new RuntimeException("Missing Content-Range header in partial response.");
        }

        Matcher m = CONTENT_RANGE_PATTERN.matcher(header);
        if (!m.matches()) {
            throw new RuntimeException("Invalid Content-Range header: [" + header + "]");
        }

        long start = Long.parseLong(m.group(1));
        long end = Long.parseLong(m.group(2));
        long total = Long.parseLong(m.group(3));

        if (end < start || end >= total) {
            throw new RuntimeException("Invalid Content-Range header: [" + header + "]");
        }

        return new ContentRange(start, end, total);
    }

    record ContentRange(long start, long end, long total) { }

}
