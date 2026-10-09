/*
 * This file is part of nzyme.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */

package app.nzyme.core;

import app.nzyme.core.assets.AssetManager;
import app.nzyme.core.assets.AssetMonitor;
import app.nzyme.core.assets.AssetStatisticsCleaner;
import app.nzyme.core.bluetooth.Bluetooth;
import app.nzyme.core.bluetooth.sig.BluetoothSigService;
import app.nzyme.core.cache.CacheManager;
import app.nzyme.core.connect.ConnectService;
import app.nzyme.core.context.ContextService;
import app.nzyme.core.database.tasks.handlers.GlobalPurgeCategoryTaskHandler;
import app.nzyme.core.database.tasks.handlers.OrganizationPurgeCategoryTaskHandler;
import app.nzyme.core.database.tasks.handlers.TenantPurgeCategoryTaskHandler;
import app.nzyme.core.detection.alerts.DetectionAlertService;
import app.nzyme.core.distributed.ClusterManager;
import app.nzyme.core.distributed.NodeManager;
import app.nzyme.core.distributed.messaging.postgres.PostgresMessageBusImpl;
import app.nzyme.core.distributed.tasksqueue.postgres.PostgresTasksQueueImpl;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.monitoring.Dot11SignalTrackMonitor;
import app.nzyme.core.dot11.monitoring.clients.KnownClientMonitor;
import app.nzyme.core.dot11.monitoring.disco.Dot11DiscoMonitor;
import app.nzyme.core.dot11.monitoring.ssids.KnownSSIDMonitor;
import app.nzyme.core.environment.EnvironmentService;
import app.nzyme.core.ethernet.L4ConnectionCleaner;
import app.nzyme.core.events.EventEngine;
import app.nzyme.core.events.EventEngineImpl;
import app.nzyme.core.integrations.ScheduledIntegrationsManager;
import app.nzyme.core.integrations.geoip.GeoIpService;
import app.nzyme.core.monitoring.health.HealthMonitor;
import app.nzyme.core.monitors.MonitorExecutionTaskHandler;
import app.nzyme.core.monitors.Monitors;
import app.nzyme.core.monitors.MonitorsThread;
import app.nzyme.core.ouis.OuiService;
import app.nzyme.core.periodicals.connect.ConnectStatusReporter;
import app.nzyme.core.context.ContextCleaner;
import app.nzyme.core.periodicals.distributed.NodeUpdater;
import app.nzyme.core.periodicals.housekeeping.DatabaseRetentionCleaner;
import app.nzyme.core.quota.QuotaService;
import app.nzyme.core.registry.RegistryChangeMonitorImpl;
import app.nzyme.core.rest.server.NzymeHttpServer;
import app.nzyme.core.security.authentication.AuthenticationService;
import app.nzyme.core.subsystems.Subsystems;
import app.nzyme.core.timelines.TimelinesThread;
import app.nzyme.core.timelines.tasks.Dot11BSSIDTimelineCalculationTaskHandler;
import app.nzyme.core.timelines.tasks.Dot11SSIDTimelineCalculationTaskHandler;
import app.nzyme.core.uav.Uav;
import app.nzyme.plugin.*;
import app.nzyme.plugin.distributed.messaging.MessageBus;
import app.nzyme.plugin.distributed.tasksqueue.*;
import app.nzyme.plugin.retro.RetroService;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jmx.JmxReporter;
import com.codahale.metrics.jvm.*;
import tools.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import app.nzyme.core.configuration.base.BaseConfiguration;
import app.nzyme.core.configuration.node.NodeConfiguration;
import app.nzyme.core.crypto.Crypto;
import app.nzyme.core.database.DatabaseImpl;
import app.nzyme.core.security.NodeFilePermissions;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.periodicals.PeriodicalManager;
import app.nzyme.core.periodicals.versioncheck.VersioncheckThread;
import app.nzyme.core.plugin.loading.PluginLoader;
import app.nzyme.core.registry.RegistryImpl;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.taps.TapManager;
import app.nzyme.core.util.MetricNames;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static app.nzyme.core.timelines.Timelines.EVENT_HORIZON_MINUTES;

public class NzymeNodeImpl implements NzymeNode {

    private static final Logger LOG = LogManager.getLogger(NzymeNodeImpl.class);

    private final Version version;

    private final NodeIdentification nodeIdentification;

    private final NodeConfiguration configuration;
    private final BaseConfiguration baseConfiguration;

    private final Path dataDirectory;

    private final DatabaseImpl database;
    private final AuthenticationService authenticationService;

    private final RegistryImpl registry;
    private final RegistryChangeMonitor registryChangeMonitor;

    private final Subsystems subsystems;
    private final QuotaService quotaService;

    private final NodeManager nodeManager;
    private final ClusterManager clusterManager;
    private final NzymeHttpServer httpServer;

    private final MetricRegistry metrics;
    private final TapManager tapManager;
    private final MessageBus messageBus;
    private final TasksQueue tasksQueue;

    private final GeoIpService geoIpService;
    private final OuiService ouiService;
    private final BluetoothSigService bluetoothSigService;
    private final EnvironmentService environmentService;

    private final ContextService contextService;

    private final Ethernet ethernet;
    private final Dot11 dot11;
    private final Bluetooth bluetooth;
    private final Uav uav;

    private final AssetManager assets;

    private final TablesService tablesService;

    private final ObjectMapper objectMapper;

    private final DetectionAlertService detectionAlertService;
    private final EventEngine eventEngine;
    private final Monitors monitors;

    private final ConnectService connect;
    private final HealthMonitor healthMonitor;

    private final ScheduledIntegrationsManager scheduledIntegrationsManager;

    private List<String> plugins;

    private Optional<RetroService> retroService = Optional.empty();

    private final Crypto crypto;

    private final List<Object> pluginRestResources;

    public NzymeNodeImpl(BaseConfiguration baseConfiguration, NodeConfiguration configuration, DatabaseImpl database) {
        this.baseConfiguration = baseConfiguration;
        this.version = new Version();
        this.dataDirectory = Path.of(baseConfiguration.dataDirectory());
        this.metrics = new MetricRegistry();
        this.database = database;
        this.configuration = configuration;

        this.registry = new RegistryImpl(this, "core");
        this.registryChangeMonitor = new RegistryChangeMonitorImpl(this);

        this.authenticationService = new AuthenticationService(this);

        this.subsystems = new Subsystems(this);
        this.quotaService = new QuotaService(this);

        this.nodeManager = new NodeManager(this);
        try {
            this.nodeManager.initialize();
            this.nodeIdentification = NodeIdentification.create(nodeManager.getLocalNodeId(), baseConfiguration.name());
            this.nodeManager.registerSelf();
        } catch (NodeManager.NodeInitializationException e) {
            throw new RuntimeException("Could not initialize distributed subsystem.", e);
        }

        this.clusterManager = new ClusterManager(this);
        this.messageBus = new PostgresMessageBusImpl(this);
        this.tasksQueue = new PostgresTasksQueueImpl(this);

        this.geoIpService = new GeoIpService(this);
        this.ouiService = new OuiService(this);
        this.bluetoothSigService = new BluetoothSigService(this);
        this.environmentService = new EnvironmentService(this);

        this.contextService = new ContextService(this);

        this.pluginRestResources = Lists.newArrayList();
        this.plugins = Lists.newArrayList();

        this.httpServer = new NzymeHttpServer(this, this.pluginRestResources);

        this.ethernet = new Ethernet(this);
        this.dot11 = new Dot11(this);
        this.bluetooth = new Bluetooth(this);
        this.uav = new Uav(this);

        this.assets = new AssetManager(this);

        this.tapManager = new TapManager(this);

        this.crypto = new Crypto(this);
        this.objectMapper = new ObjectMapper();

        this.connect = new ConnectService(this);
        this.healthMonitor = new HealthMonitor(this);

        // Register JVM metrics.
        this.metrics.register("gc", new GarbageCollectorMetricSet());
        this.metrics.register("classes", new ClassLoadingGaugeSet());
        this.metrics.register("fds", new FileDescriptorRatioGauge());
        this.metrics.register("jvm", new JvmAttributeGaugeSet());
        this.metrics.register("mem", new MemoryUsageGaugeSet());
        this.metrics.register("threadstates", new ThreadStatesGaugeSet());

        this.detectionAlertService = new DetectionAlertService(this);
        this.eventEngine = new EventEngineImpl(this);
        this.monitors = new Monitors(this);

        this.tablesService = new TablesService(this);

        this.scheduledIntegrationsManager = new ScheduledIntegrationsManager(this);
    }

    @Override
    public void initialize() {
        LOG.info("Initializing nzyme version: {}.", version.getVersionString());

        LOG.info("Initializing cluster manager...");
        this.clusterManager.initialize();
        LOG.info("Done.");

        LOG.info("Initializing message bus [{}] ...", this.messageBus.getClass().getCanonicalName());
        this.messageBus.initialize();
        LOG.info("Done.");

        LOG.info("Initializing tasks queue [{}] ...", this.tasksQueue.getClass().getCanonicalName());
        this.tasksQueue.initialize();
        LOG.info("Done.");

        // Register task handlers. (There are others registered in other parts of the system.)
        this.tasksQueue.onMessageReceived(
                TaskType.PURGE_DATA_CATEGORY_GLOBAL,
                new GlobalPurgeCategoryTaskHandler(this)
        );
        this.tasksQueue.onMessageReceived(
                TaskType.PURGE_DATA_CATEGORY_ORGANIZATION,
                new OrganizationPurgeCategoryTaskHandler(this)
        );
        this.tasksQueue.onMessageReceived(
                TaskType.PURGE_DATA_CATEGORY_TENANT,
                new TenantPurgeCategoryTaskHandler(this)
        );
        this.tasksQueue.onMessageReceived(
                TaskType.MONITOR_EXECUTION,
                new MonitorExecutionTaskHandler(this)
        );
        this.tasksQueue.onMessageReceived(
                TaskType.TIMELINES_CALCULATION_DOT11_BSSID,
                new Dot11BSSIDTimelineCalculationTaskHandler(this)
        );
        this.tasksQueue.onMessageReceived(
                TaskType.TIMELINES_CALCULATION_DOT11_SSID,
                new Dot11SSIDTimelineCalculationTaskHandler(this)
        );

        try {
            this.crypto.initialize();
        } catch (Crypto.CryptoInitializationException e) {
            throw new RuntimeException("Could not load cryptographic subsystem.", e);
        }

        if (configuration.connectSkip()) {
            // Connect is disabled in config file. None of the Connect tasks will run. (checked in ConnectService)
            LOG.warn("Connect has been disabled in configuration file. Not connecting.");
        }

        LOG.info("Initializing Geo IP service...");
        this.geoIpService.initialize();
        LOG.info("Done.");

        LOG.info("Initializing OUI service...");
        this.ouiService.initialize();
        LOG.info("Done.");

        LOG.info("Initializing Bluetooth SIG service...");
        this.bluetoothSigService.initialize();
        LOG.info("Done.");

        LOG.info("Initializing UAV service...");
        this.uav.initializeConnectModels();
        LOG.info("Done.");

        LOG.info("Initializing environment service...");
        this.environmentService.initialize();
        LOG.info("Done.");

        LOG.info("Initializing authentication service...");
        this.authenticationService.initialize();
        LOG.info("Done.");

        // Metrics JMX reporter.
        final JmxReporter reporter = JmxReporter.forRegistry(metrics).build();
        reporter.start();

        // Database metrics.
        metrics.register(MetricNames.DATABASE_SIZE, (Gauge<Long>) database::getTotalSize);
        database.registerMetrics(metrics);

        // Files holding secrets with insecure permissions. See NodeFilePermissions.
        NodeFilePermissions filePermissions = new NodeFilePermissions(configuration);
        metrics.register(MetricNames.FILE_PERMISSION_ISSUES, (Gauge<Integer>) () -> filePermissions.check().size());

        // Periodicals. (TODO: Replace with scheduler service)
        PeriodicalManager periodicalManager = new PeriodicalManager();
        periodicalManager.scheduleAtFixedRate(new NodeUpdater(this), 0, 5, TimeUnit.SECONDS);
        periodicalManager.scheduleAtFixedRate(new ConnectStatusReporter(this), 0, 1, TimeUnit.MINUTES);
        periodicalManager.scheduleAtFixedRate(new L4ConnectionCleaner(this), 0, 1, TimeUnit.MINUTES);
        periodicalManager.scheduleAtFixedRate(new Dot11SignalTrackMonitor(this), 1, 1, TimeUnit.MINUTES);
        periodicalManager.scheduleAtFixedRate(new Dot11DiscoMonitor(this), 1, 1, TimeUnit.MINUTES);
        periodicalManager.scheduleAtFixedRate(new ContextCleaner(getContextService()), 0, 1, TimeUnit.MINUTES);
        periodicalManager.scheduleAtFixedRate(new KnownSSIDMonitor(this), 1, 1, TimeUnit.MINUTES);
        periodicalManager.scheduleAtFixedRate(new KnownClientMonitor(this), 1, 1, TimeUnit.MINUTES);
        periodicalManager.scheduleAtFixedRate(new DatabaseRetentionCleaner(this), 1, 60, TimeUnit.MINUTES);
        periodicalManager.scheduleAtFixedRate(new AssetMonitor(this), 1, 1, TimeUnit.MINUTES);
        periodicalManager.scheduleAtFixedRate(new AssetStatisticsCleaner(this), 1, 10, TimeUnit.MINUTES);
        periodicalManager.scheduleAtFixedRate(new MonitorsThread(this), 1, 1, TimeUnit.MINUTES);
        periodicalManager.scheduleAtFixedRate(new TimelinesThread(this), 0, EVENT_HORIZON_MINUTES, TimeUnit.MINUTES);
        if (configuration.versionchecksEnabled()) {
            periodicalManager.scheduleAtFixedRate(new VersioncheckThread(version, this), 0, 60, TimeUnit.MINUTES);
        } else {
            LOG.info("Versionchecks are disabled.");
        }

        healthMonitor.initialize();

        // Load plugins.
        PluginLoader pl = new PluginLoader(new File(configuration.pluginDirectory())); // TODO make path configurable
        for (Plugin plugin : pl.loadPlugins()) {
            // Initialize plugin
            LOG.info("Initializing plugin of type [{}]: [{}] ...", plugin.getClass().getCanonicalName(), plugin.getName());

            try {
                plugin.initialize(this, getDatabaseRegistry(plugin.getId()), this, this);
            } catch(Exception e) {
                LOG.error("Could not load plugin. Skipping.", e);
                continue;
            }

            this.plugins.add(plugin.getId());

            LOG.info("Done.");
        }

        CacheManager cacheManager = new CacheManager(this);
        cacheManager.initialize();

        this.scheduledIntegrationsManager.initialize();

        // Spin up REST API and web interface.
        java.util.logging.Logger.getLogger("org.glassfish.grizzly").setLevel(Level.SEVERE);
        java.util.logging.Logger.getLogger("org.glassfish.jersey.internal.inject.Providers").setLevel(Level.SEVERE);
        this.httpServer.initialize();
    }

    public void shutdown() {
        LOG.info("Shutting down.");

        // Shutdown REST API.
        if (httpServer != null) {
            LOG.info("Stopping REST API.");
            httpServer.shutdownNow();
        }

        // Close database connection pool.
        database.shutdown();

        LOG.info("Shutdown complete.");
    }

    @Override
    public NodeManager getNodeManager() {
        return nodeManager;
    }

    @Override
    public ClusterManager getClusterManager() {
        return clusterManager;
    }

    @Override
    public MessageBus getMessageBus() {
        return messageBus;
    }

    @Override
    public TasksQueue getTasksQueue() {
        return tasksQueue;
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    @Override
    public Subsystems getSubsystems() {
        return subsystems;
    }

    @Override
    public HealthMonitor getHealthMonitor() {
        return healthMonitor;
    }

    @Override
    public Ethernet getEthernet() {
        return ethernet;
    }

    @Override
    public Dot11 getDot11() {
        return dot11;
    }

    @Override
    public Bluetooth getBluetooth() {
        return bluetooth;
    }

    @Override
    public Uav getUav() {
        return uav;
    }

    @Override
    public AssetManager getAssetsManager() {
        return assets;
    }

    @Override
    public GeoIpService getGeoIpService() {
        return geoIpService;
    }

    @Override
    public OuiService getOuiService() {
        return ouiService;
    }

    @Override
    public BluetoothSigService getBluetoothSigService() {
        return bluetoothSigService;
    }

    @Override
    public EnvironmentService getEnvironmentService() {
        return environmentService;
    }

    @Override
    public ContextService getContextService() {
        return contextService;
    }

    @Override
    public EventEngine getEventEngine() {
        return eventEngine;
    }

    @Override
    public Monitors getMonitors() {
        return monitors;
    }

    @Override
    public TapManager getTapManager() {
        return tapManager;
    }

    @Override
    public TablesService getTablesService() {
        return tablesService;
    }

    @Override
    public List<String> getInitializedPlugins() {
        return plugins;
    }

    @Nullable
    @Override
    public Optional<RetroService> retroService() {
        return retroService;
    }

    @Override
    public Crypto getCrypto() {
        return crypto;
    }

    @Override
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public NodeConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public BaseConfiguration getBaseConfiguration() {
        return baseConfiguration;
    }

    @Override
    public Path getDataDirectory() {
        return dataDirectory;
    }

    @Override
    public MetricRegistry getMetrics() {
        return metrics;
    }

    @Override
    public Database getDatabase() {
        return database;
    }

    @Override
    public QuotaService getQuotaService() {
        return quotaService;
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public NzymeHttpServer getHttpServer() {
        return httpServer;
    }

    @Override
    public void registerRetroService(RetroService service) {
        if (this.retroService.isPresent()) {
            LOG.error("Attempt to register a new RetroService but one already exists. Aborting.");
            return;
        }

        this.retroService = Optional.of(service);
    }

    @Override
    public void registerRestResource(Object resource) {
        this.pluginRestResources.add(resource);
    }

    public Registry getDatabaseRegistry(String namespace) {
        return new RegistryImpl(this, namespace);
    }

    @Override
    public Registry getDatabaseCoreRegistry() {
        return registry;
    }

    @Override
    public RegistryChangeMonitor getRegistryChangeMonitor() {
        return registryChangeMonitor;
    }

    @Override
    public DetectionAlertService getDetectionAlertService() {
        return detectionAlertService;
    }

    @Override
    public ConnectService getConnect() {
        return connect;
    }

    @Override
    public NodeIdentification getNodeInformation() {
        return nodeIdentification;
    }
}
