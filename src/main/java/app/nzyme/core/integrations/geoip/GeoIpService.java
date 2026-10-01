package app.nzyme.core.integrations.geoip;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.connect.ConnectRegistryKeys;
import app.nzyme.core.integrations.geoip.ipinfo.IpInfoFreeCountryAsnLookupResult;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class GeoIpService {

    private static final Logger LOG = LogManager.getLogger(GeoIpService.class);

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
                IpInfoFreeCountryAsnLookupResult lookup = mmdb.get(address, IpInfoFreeCountryAsnLookupResult.class);

                if (lookup == null) {
                    return Optional.empty();
                }

                GeoIpGeoInformation geo = GeoIpGeoInformation.create(
                        null,
                        lookup.getCountryCode(),
                        lookup.getCountryName(),
                        null,
                        null
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

    private Optional<byte[]> fetchMmdbFromConnect() {
        LOG.debug("Loading new GeoIP data from Connect.");

        try {
            OkHttpClient c = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.MINUTES)
                    .followRedirects(true)
                    .build();

            HttpUrl url = HttpUrl.get(nzyme.getConnect().getApiUri())
                    .newBuilder()
                    .addPathSegment("data")
                    .addPathSegment("geoip")
                    .addPathSegment("ip")
                    .build();

            Response response = c.newCall(new Request.Builder()
                            .addHeader("User-Agent", "nzyme")
                            .get()
                            .url(url)
                            .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + nzyme.getConnect().getApiKey())
                            .addHeader("Content-Type", "application/octet-stream")
                            .addHeader(HttpHeaders.USER_AGENT, "nzyme-node")
                            .build()
                    ).execute();

            try (response) {
                if (!response.isSuccessful()) {
                    if (response.code() == 403) {
                        // GeoIP data disabled in Connect for this cluster.
                        return Optional.empty();
                    }

                    throw new RuntimeException("Expected HTTP 200 or 403 but got HTTP " + response.code());
                }


                if (response.body() == null) {
                    throw new RuntimeException("Empty response.");
                }

                LOG.info("GeoIP data download from Connect complete.");

                return Optional.of(response.body().bytes());
            }
        }catch (Exception e) {
            LOG.error("Could not download GeoIP data from Connect.", e);
            return Optional.empty();
        }
    }

}
