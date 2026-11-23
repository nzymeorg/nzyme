package app.nzyme.core.bluetooth.sig;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.connect.ConnectDataset;
import app.nzyme.core.connect.ConnectRegistryKeys;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class BluetoothSigService {

    private static final Logger LOG = LogManager.getLogger(BluetoothSigService.class);

    private final NzymeNode nzyme;
    private final Timer companyIdLookupTimer;
    private final Timer serviceUuidLookupTimer;

    private final ScheduledExecutorService refresher;

    private final ConnectDataset companyIdsDataset;
    private final ConnectDataset serviceUuidsDataset;

    private final ReentrantLock lock = new ReentrantLock();
    private Map<Integer, String> companyIds = Map.of();
    private Map<String, String> serviceUuids = Map.of();

    // Can be disabled if Connect is not set up or BT SIG data source is not enabled in Connect.
    private volatile boolean isEnabled = false;

    public BluetoothSigService(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.companyIdLookupTimer = nzyme.getMetrics()
                .timer(MetricRegistry.name(MetricNames.BTSIG_CID_LOOKUP_TIMING));
        this.serviceUuidLookupTimer = nzyme.getMetrics()
                .timer(MetricRegistry.name(MetricNames.BTSIG_SUUID_LOOKUP_TIMING));

        ObjectMapper om = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        this.companyIdsDataset = ConnectDataset.create(nzyme, "bluetooth-company-ids",
                List.of("data", "bluetooth", "companyids"), false,
                new ConnectDataset.Consumer() {
                    @Override
                    public void load(Path file) {
                        Map<Integer, String> table = Maps.newHashMap();
                        for (ConnectCompanyIdResponse id : om.readValue(file.toFile(), ConnectCompanyIdListResponse.class).companyIds()) {
                            table.put(id.companyId(), id.name());
                        }

                        setCompanyIds(table);
                    }

                    @Override
                    public void unload() {
                        setCompanyIds(Map.of());
                    }
                }
        );

        this.serviceUuidsDataset = ConnectDataset.create(nzyme, "bluetooth-service-uuids",
                List.of("data", "bluetooth", "serviceuuids"), false,
                new ConnectDataset.Consumer() {
                    @Override
                    public void load(Path file) {
                        Map<String, String> table = Maps.newHashMap();
                        for (ConnectServiceUuidResponse id : om.readValue(file.toFile(), ConnectServiceUuidListResponse.class).serviceUuids()) {
                            table.put(id.uuid(), id.name());
                        }

                        setServiceUuids(table);
                    }

                    @Override
                    public void unload() {
                        setServiceUuids(Map.of());
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
                        .setNameFormat("btsig-refresher-%d")
                        .build()
        );

        refresher.scheduleAtFixedRate(this::reload, 1, 1, TimeUnit.HOURS);
    }

    private void setCompanyIds(Map<Integer, String> companyIds) {
        lock.lock();

        try {
            this.companyIds = companyIds;
        } finally {
            lock.unlock();
        }
    }

    private void setServiceUuids(Map<String, String> serviceUuids) {
        lock.lock();

        try {
            this.serviceUuids = serviceUuids;
        } finally {
            lock.unlock();
        }
    }

    private synchronized void reload() {
        try {
            if (!nzyme.getConnect().isEnabled()) {
                this.isEnabled = false;
                companyIdsDataset.delete();
                serviceUuidsDataset.delete();
                return;
            }

            boolean companyIdsLoaded = companyIdsDataset.refresh();
            boolean serviceUuidsLoaded = serviceUuidsDataset.refresh();
            this.isEnabled = companyIdsLoaded && serviceUuidsLoaded;
        } catch (Exception e) {
            LOG.error("Could not reload Bluetooth SIG data.", e);
        }
    }

    public synchronized void initialize() {
        if (!nzyme.getConnect().isEnabled()) {
            companyIdsDataset.delete();
            serviceUuidsDataset.delete();
            return;
        }

        boolean companyIdsLoaded = companyIdsDataset.loadFromDisk();
        boolean serviceUuidsLoaded = serviceUuidsDataset.loadFromDisk();

        if (companyIdsLoaded && serviceUuidsLoaded) {
            this.isEnabled = true;
            refresher.execute(this::reload);
        } else {
            reload();
        }
    }

    public Optional<String> lookupCompanyId(int companyId) {
        if (!isEnabled) {
            return Optional.empty();
        }

        try(Timer.Context ignored = companyIdLookupTimer.time()) {
            lock.lock();

            try {
                String cid = companyIds.get(companyId);
                return cid == null ? Optional.empty() : Optional.of(cid);
            } finally {
                lock.unlock();
            }
        }
    }

    public Optional<String> lookupServiceUuid(String uuid) {
        if (!isEnabled) {
            return Optional.empty();
        }

        try(Timer.Context ignored = serviceUuidLookupTimer.time()) {
            lock.lock();

            try {
                String name = serviceUuids.get(uuid);
                return name == null ? Optional.empty() : Optional.of(name);
            } finally {
                lock.unlock();
            }
        }
    }

}
