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
import app.nzyme.core.bluetooth.Bluetooth;
import app.nzyme.core.bluetooth.sig.BluetoothSigService;
import app.nzyme.core.configuration.base.BaseConfiguration;
import app.nzyme.core.connect.ConnectService;
import app.nzyme.core.context.ContextService;
import app.nzyme.core.detection.alerts.DetectionAlertService;
import app.nzyme.core.distributed.ClusterManager;
import app.nzyme.core.distributed.NodeManager;
import app.nzyme.core.distributed.messaging.postgres.PostgresMessageBusImpl;
import app.nzyme.core.distributed.tasksqueue.postgres.PostgresTasksQueueImpl;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.environment.EnvironmentService;
import app.nzyme.core.events.EventEngine;
import app.nzyme.core.events.EventEngineImpl;
import app.nzyme.core.integrations.geoip.GeoIpService;
import app.nzyme.core.monitoring.health.HealthMonitor;
import app.nzyme.core.database.maintenance.DatabaseMaintenance;
import app.nzyme.core.monitors.Monitors;
import app.nzyme.core.ouis.OuiService;
import app.nzyme.core.quota.QuotaService;
import app.nzyme.core.registry.RegistryChangeMonitorImpl;
import app.nzyme.core.registry.RegistryImpl;
import app.nzyme.core.rest.server.NzymeHttpServer;
import app.nzyme.core.security.authentication.AuthenticationService;
import app.nzyme.core.subsystems.Subsystems;
import app.nzyme.core.uav.Uav;
import app.nzyme.plugin.Database;
import app.nzyme.plugin.NodeIdentification;
import app.nzyme.plugin.Registry;
import app.nzyme.plugin.RegistryChangeMonitor;
import app.nzyme.plugin.distributed.messaging.MessageBus;
import app.nzyme.plugin.distributed.tasksqueue.TasksQueue;
import app.nzyme.plugin.retro.RetroService;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jvm.*;
import tools.jackson.databind.ObjectMapper;
import app.nzyme.core.configuration.IncompleteConfigurationException;
import app.nzyme.core.configuration.InvalidConfigurationException;
import app.nzyme.core.configuration.node.NodeConfiguration;
import app.nzyme.core.configuration.node.NodeConfigurationLoader;
import app.nzyme.core.crypto.Crypto;
import app.nzyme.core.database.DatabaseImpl;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.taps.TapManager;
import liquibase.exception.LiquibaseException;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class MockNzyme implements NzymeNode {

    private File loadFromResourceFile(String name) {
        URL resource = getClass().getClassLoader().getResource(name);
        if (resource == null) {
            throw new RuntimeException("test config file does not exist in resources");
        }

        return new File(resource.getFile());
    }

    private final NodeManager nodeManager;

    private final NodeIdentification nodeIdentification;

    private final NodeConfiguration configuration;
    private final OuiService ouiService;
    private final MetricRegistry metricRegistry;
    private final ObjectMapper objectMapper;
    private final Version version;
    private final Database database;
    private final Path dataDirectory;
    private final MessageBus messageBus;
    private final TasksQueue tasksQueue;
    private final BaseConfiguration baseConfiguration;
    private final Crypto crypto;
    private final ClusterManager clusterManager;
    private final AuthenticationService authenticationService;
    private final GeoIpService geoIp;
    private final Registry registry;
    private final RegistryChangeMonitor registryChangeMonitor;
    private final EventEngine eventEngine;

    public MockNzyme() {
        this(Integer.MAX_VALUE, TimeUnit.DAYS);
    }

    public MockNzyme(int taskAndMessagePollInterval, TimeUnit taskAndMessagePollIntervalUnit) {
        this.version = new Version();

        this.baseConfiguration = BaseConfiguration.create(
                "mocky-mock-" + new Random().nextInt(Integer.MAX_VALUE),
                "foo"
        );

        try {
            String configFile = "nzyme-test-complete-valid.conf.test";
            if (System.getProperty("os.name").startsWith("Windows")) {
                configFile = "nzyme-test-complete-valid-windows.conf.test";
                System.out.println("loading Windows nzyme configuration file");
            }

            this.configuration = new NodeConfigurationLoader(loadFromResourceFile(configFile), false).get();
            this.dataDirectory = Path.of("test_data_dir");
        } catch (InvalidConfigurationException | IncompleteConfigurationException | FileNotFoundException e) {
            throw new RuntimeException("Could not load test config file from resources.", e);
        }
        this.database = new DatabaseImpl(configuration);

        try {
            this.database.migrate();
            this.database.initialize();
        } catch (LiquibaseException e) {
            throw new RuntimeException(e);
        }

        this.registry = new RegistryImpl(this, "core");
        this.registryChangeMonitor = new RegistryChangeMonitorImpl(this);

        // Metrics.
        this.metricRegistry = new MetricRegistry();
        this.metricRegistry.register("gc", new GarbageCollectorMetricSet());
        this.metricRegistry.register("classes", new ClassLoadingGaugeSet());
        this.metricRegistry.register("fds", new FileDescriptorRatioGauge());
        this.metricRegistry.register("jvm", new JvmAttributeGaugeSet());
        this.metricRegistry.register("mem", new MemoryUsageGaugeSet());
        this.metricRegistry.register("threadstates", new ThreadStatesGaugeSet());

        this.geoIp = new GeoIpService(this);
        this.geoIp.initialize();

        this.eventEngine = new EventEngineImpl(this);

        this.nodeManager = new NodeManager(this);
        try {
            this.nodeManager.initialize();
        } catch (NodeManager.NodeInitializationException e) {
            throw new RuntimeException(e);
        }

        this.nodeIdentification = NodeIdentification.create(nodeManager.getLocalNodeId(), baseConfiguration.name());

        this.clusterManager = new ClusterManager(this);

        this.messageBus = new PostgresMessageBusImpl(this);
        ((PostgresMessageBusImpl) this.messageBus).initialize(taskAndMessagePollInterval, taskAndMessagePollIntervalUnit);

        this.tasksQueue = new PostgresTasksQueueImpl(this);
        ((PostgresTasksQueueImpl) this.tasksQueue).initialize(taskAndMessagePollInterval, taskAndMessagePollIntervalUnit);

        this.authenticationService = new AuthenticationService(this);


        this.database.useHandle(handle -> handle.execute("TRUNCATE sentry_ssids"));

        this.crypto = new Crypto(this);

        this.ouiService = new OuiService(this);
        this.objectMapper = new ObjectMapper();

    }

    @Override
    public void initialize() {
        this.clusterManager.initialize();

        try {
            this.crypto.initialize(false);
        } catch (Crypto.CryptoInitializationException e) {
            throw new RuntimeException(e);
        }

        this.authenticationService.initialize();
    }

    @Override
    public void shutdown() {
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
        return null;
    }

    @Override
    public HealthMonitor getHealthMonitor() {
        return null;
    }

    @Override
    public DatabaseMaintenance getDatabaseMaintenance() {
        return null;
    }

    @Override
    public Ethernet getEthernet() {
        return null;
    }

    @Override
    public Dot11 getDot11() {
        return null;
    }

    @Override
    public Bluetooth getBluetooth() {
        return null;
    }

    @Override
    public Uav getUav() {
        return null;
    }

    @Override
    public AssetManager getAssetsManager() {
        return null;
    }

    @Override
    public GeoIpService getGeoIpService() {
        return geoIp;
    }

    @Override
    public ContextService getContextService() {
        return null;
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
        return metricRegistry;
    }

    @Override
    public Database getDatabase() {
        return database;
    }

    @Override
    public QuotaService getQuotaService() {
        return null;
    }

    @Override
    public TapManager getTapManager() {
        return null;
    }

    @Override
    public TablesService getTablesService() {
        return null;
    }

    @Override
    public OuiService getOuiService() {
        return ouiService;
    }

    @Override
    public BluetoothSigService getBluetoothSigService() {
        return null;
    }

    @Override
    public EnvironmentService getEnvironmentService() {
        return null;
    }

    @Override
    public List<String> getInitializedPlugins() {
        return null;
    }

    @Override
    public Optional<RetroService> retroService() {
        return Optional.empty();
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
    public Registry getDatabaseCoreRegistry() {
        return new RegistryImpl(this, "core");
    }

    @Override
    public RegistryChangeMonitor getRegistryChangeMonitor() {
        return registryChangeMonitor;
    }

    @Override
    public DetectionAlertService getDetectionAlertService() {
        return null;
    }

    @Override
    public ConnectService getConnect() {
        return null;
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public NzymeHttpServer getHttpServer() {
        return null;
    }

    @Override
    public void registerRetroService(RetroService retroService) {
    }

    @Override
    public void registerRestResource(Object o) {

    }

    public Registry getRegistry(String s) {
        return registry;
    }

    @Override
    public EventEngine getEventEngine() {
        return eventEngine;
    }

    @Override
    public Monitors getMonitors() {
        return null;
    }

    @Override
    public NodeIdentification getNodeInformation() {
        return nodeIdentification;
    }
}
