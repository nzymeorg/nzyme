package app.nzyme.core.integrations.geoip;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.connect.ConnectDataset;
import app.nzyme.core.connect.ConnectRegistryKeys;
import app.nzyme.core.integrations.geoip.ipinfo.IpInfoCountryAsnLookupResult;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.maxmind.db.Reader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private final ConnectDataset dataset;

    private final ReentrantLock lock = new ReentrantLock();
    private Reader mmdb = null;

    private final ScheduledExecutorService refresher;

    // Can be disabled if Connect is not set up or GeoIp data source is not enabled in Connect.
    private volatile boolean isEnabled = false;

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

        this.dataset = ConnectDataset.create(nzyme, "geoip", List.of("data", "geoip", "ip"), true,
                new ConnectDataset.Consumer() {
                    @Override
                    public void load(Path file) throws IOException {
                        // Memory-mapped. The file is not read into the heap.
                        Reader reader = new Reader(file.toFile());
                        setMmdb(reader);

                        LOG.info("GeoIP database loaded. Type: [{}]", reader.getMetadata().getDatabaseType());
                    }

                    @Override
                    public void unload() {
                        setMmdb(null);
                    }
                }
        );

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

    private void setMmdb(@Nullable Reader reader) {
        lock.lock();

        try {
            Reader previous = this.mmdb;
            this.mmdb = reader;

            if (previous != null) {
                previous.close();
            }
        } catch (IOException e) {
            LOG.warn("Could not close previous GeoIP database.", e);
        } finally {
            lock.unlock();
        }

        cache.invalidateAll();
    }

    private synchronized void reload() {
        try {
            if (!nzyme.getConnect().isEnabled()) {
                this.isEnabled = false;
                dataset.delete();
                return;
            }

            this.isEnabled = dataset.refresh();
        } catch (Exception e) {
            LOG.error("Could not reload GeoIP data.", e);
        }
    }

    public synchronized void initialize() {
        if (!nzyme.getConnect().isEnabled()) {
            dataset.delete();
            return;
        }

        if (dataset.loadFromDisk()) {
            this.isEnabled = true;
            refresher.execute(this::reload);
        } else {
            reload();
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
                if (mmdb == null) {
                    return Optional.empty();
                }

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

}
