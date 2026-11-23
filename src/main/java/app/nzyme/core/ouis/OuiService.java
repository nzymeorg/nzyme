package app.nzyme.core.ouis;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.connect.ConnectDataset;
import app.nzyme.core.connect.ConnectRegistryKeys;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class OuiService {

    private static final Logger LOG = LogManager.getLogger(OuiService.class);

    private final NzymeNode nzyme;
    private final Timer lookupTimer;

    private final ScheduledExecutorService refresher;

    private final ConnectDataset dataset;

    private final ReentrantLock lock = new ReentrantLock();
    private Map<String, String> ouis = Map.of();

    // Can be disabled if Connect is not set up or OUI data source is not enabled in Connect.
    private volatile boolean isEnabled = false;

    public OuiService(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.lookupTimer = nzyme.getMetrics().timer(MetricRegistry.name(MetricNames.OUI_LOOKUP_TIMING));

        ObjectMapper om = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        this.dataset = ConnectDataset.create(nzyme, "ouis", List.of("data", "oui", "all"), false,
                new ConnectDataset.Consumer() {
                    @Override
                    public void load(Path file) {
                        setOuis(om.readValue(file.toFile(), ConnectOuiResponse.class).ouis());
                    }

                    @Override
                    public void unload() {
                        setOuis(Map.of());
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
                        .setNameFormat("oui-refresher-%d")
                        .build()
        );

        refresher.scheduleAtFixedRate(this::reload, 1, 1, TimeUnit.HOURS);
    }

    private void setOuis(Map<String, String> ouis) {
        lock.lock();

        try {
            this.ouis = ouis;
        } finally {
            lock.unlock();
        }
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
            LOG.error("Could not reload OUI data.", e);
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

    public Optional<String> lookup(String mac) {
        if (!isEnabled || mac == null || mac.trim().isEmpty() || mac.equals("00:00:00:00:00:00")) {
            return Optional.empty();
        }

        if (mac.length() != 17) {
            LOG.warn("Passed invalid MAC address [{}]", mac);
            return Optional.empty();
        }

        try(Timer.Context ignored = lookupTimer.time()) {
            lock.lock();

            try {
                String oui = ouis.get(mac.toUpperCase().substring(0, 8).replace(":", ""));
                return oui == null ? Optional.empty() : Optional.of(oui);
            } finally {
                lock.unlock();
            }
        }
    }

}
