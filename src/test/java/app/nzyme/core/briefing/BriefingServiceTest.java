package app.nzyme.core.briefing;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.ResourcesAccessingTest;
import app.nzyme.core.assets.AssetManager;
import app.nzyme.core.bluetooth.Bluetooth;
import app.nzyme.core.bluetooth.sig.BluetoothSigService;
import app.nzyme.core.configuration.IncompleteConfigurationException;
import app.nzyme.core.configuration.InvalidConfigurationException;
import app.nzyme.core.configuration.base.BaseConfiguration;
import app.nzyme.core.configuration.node.NodeConfiguration;
import app.nzyme.core.configuration.node.NodeConfigurationLoader;
import app.nzyme.core.connect.ConnectService;
import app.nzyme.core.context.ContextService;
import app.nzyme.core.crypto.Crypto;
import app.nzyme.core.database.DatabaseImpl;
import app.nzyme.core.detection.alerts.DetectionAlertService;
import app.nzyme.core.distributed.ClusterManager;
import app.nzyme.core.distributed.NodeManager;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.environment.EnvironmentService;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.events.EventEngine;
import app.nzyme.core.integrations.geoip.GeoIpService;
import app.nzyme.core.monitoring.health.HealthMonitor;
import app.nzyme.core.monitors.Monitors;
import app.nzyme.core.ouis.OuiService;
import app.nzyme.core.quota.QuotaService;
import app.nzyme.core.registry.RegistryImpl;
import app.nzyme.core.rest.ObjectMapperProvider;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.responses.briefing.BriefingResponse;
import app.nzyme.core.rest.server.NzymeHttpServer;
import app.nzyme.core.security.authentication.AuthenticationService;
import app.nzyme.core.subsystems.Subsystems;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.taps.TapManager;
import app.nzyme.core.uav.Uav;
import app.nzyme.core.util.TimeRangeFactory;
import app.nzyme.plugin.Database;
import app.nzyme.plugin.NodeIdentification;
import app.nzyme.plugin.Registry;
import app.nzyme.plugin.RegistryChangeMonitor;
import app.nzyme.core.Version;
import app.nzyme.plugin.distributed.messaging.MessageBus;
import app.nzyme.plugin.distributed.tasksqueue.TasksQueue;
import app.nzyme.plugin.retro.RetroService;
import com.codahale.metrics.MetricRegistry;
import liquibase.exception.LiquibaseException;
import org.joda.time.DateTime;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.testng.Assert.*;

public class BriefingServiceTest extends ResourcesAccessingTest {

    private BriefingTestNode nzyme;
    private AuthenticatedUser user;

    @BeforeMethod
    public void setUp() {
        nzyme = new BriefingTestNode();
        user = new AuthenticatedUser(UUID.randomUUID(), "briefing-test-session", "briefing@test.nzyme.org",
                DateTime.now(), null, null, false, true, true);
    }

    @AfterMethod
    public void tearDown() {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM briefing_visits WHERE user_id = :user_id")
                        .bind("user_id", user.getUserId())
                        .execute()
        );
    }

    @Test
    public void testBuildsEveryBriefingType() throws Exception {
        BriefingService service = new BriefingService(nzyme);
        ObjectMapper om = new ObjectMapperProvider().getContext(null);

        UUID organizationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        List<UUID> taps = List.of(UUID.randomUUID());

        for (BriefingType type : BriefingType.values()) {
            BriefingWindow window = service.resolveWindow(type, user, BriefingSinceMode.LAST_VISIT, null);
            BriefingContext ctx = service.buildContext(user, organizationId, tenantId, taps, window, true, true, true, true);

            BriefingResponse briefing = service.build(type, ctx);

            assertEquals(briefing.briefingType(), type.name());
            assertNotNull(briefing.window());
            assertNotNull(briefing.attention());
            assertNotNull(briefing.changes());
            assertNotNull(briefing.activity());
            assertEquals(briefing.monitoring().tapsTotal(), 0);
            assertTrue(briefing.window().isFirstVisit());

            String json = om.writeValueAsString(briefing);
            assertTrue(json.contains("\"briefing_type\":\"" + type.name() + "\""));
            assertTrue(json.contains("\"window\""));
        }
    }

    @Test
    public void testBuildsWithExistingTaps() throws Exception {
        BriefingService service = new BriefingService(nzyme);
        ObjectMapper om = new ObjectMapperProvider().getContext(null);

        List<Tap> taps = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM taps WHERE deleted = false AND organization_id IS NOT NULL " +
                                "AND tenant_id IS NOT NULL LIMIT 3")
                        .mapTo(Tap.class)
                        .list()
        );

        for (Tap tap : taps) {
            for (BriefingType type : BriefingType.values()) {
                BriefingWindow window = service.resolveWindow(type, user, BriefingSinceMode.TIME_RANGE,
                        TimeRangeFactory.relative(60 * 24 * 7));
                BriefingContext ctx = service.buildContext(user, tap.organizationId(), tap.tenantId(),
                        List.of(tap.uuid()), window, true, true, true, true);

                BriefingResponse briefing = service.build(type, ctx);

                assertEquals(briefing.monitoring().tapsTotal(), 1);
                assertEquals(briefing.monitoring().taps().get(0).uuid(), tap.uuid());
                assertNotNull(om.writeValueAsString(briefing));
            }
        }
    }

    @Test
    public void testWindowResolution() {
        BriefingService service = new BriefingService(nzyme);

        BriefingWindow first = service.resolveWindow(BriefingType.MAIN, user, BriefingSinceMode.LAST_VISIT, null);
        assertTrue(first.firstVisit());
        assertEquals(first.duration().getStandardHours(), 24);
        assertEquals(first.previousTo(), first.from());
        assertEquals(first.previous().from(), first.from().minus(first.duration()));

        BriefingWindow reload = service.resolveWindow(BriefingType.MAIN, user, BriefingSinceMode.LAST_VISIT, null);
        assertTrue(reload.firstVisit());

        service.visits().markSeen(user.getUserId(), BriefingType.MAIN);
        BriefingWindow seen = service.resolveWindow(BriefingType.MAIN, user, BriefingSinceMode.LAST_VISIT, null);
        assertFalse(seen.firstVisit());
        assertEquals(seen.duration().getStandardMinutes(), BriefingService.MIN_WINDOW_MINUTES);

        BriefingWindow dot11 = service.resolveWindow(BriefingType.DOT11, user, BriefingSinceMode.LAST_VISIT, null);
        assertTrue(dot11.firstVisit());

        BriefingWindow session = service.resolveWindow(BriefingType.MAIN, user, BriefingSinceMode.LAST_SESSION, null);
        assertTrue(session.firstVisit());

        BriefingWindow range = service.resolveWindow(BriefingType.MAIN, user, BriefingSinceMode.TIME_RANGE,
                TimeRangeFactory.relative(60));
        assertFalse(range.firstVisit());
        assertFalse(range.capped());
        assertEquals(range.duration().getStandardMinutes(), 60);
    }

    private class BriefingTestNode implements NzymeNode {

        private final NodeConfiguration configuration;
        private final Database database;
        private final AuthenticationService authenticationService;
        private final Dot11 dot11;
        private final Ethernet ethernet;
        private final Bluetooth bluetooth;
        private final TapManager tapManager;
        private final DetectionAlertService detectionAlertService;
        private final Subsystems subsystems;
        private final HealthMonitor healthMonitor;

        BriefingTestNode() {
            try {
                String configFile = System.getProperty("os.name").startsWith("Windows")
                        ? "nzyme-test-complete-valid-windows.conf.test" : "nzyme-test-complete-valid.conf.test";
                this.configuration = new NodeConfigurationLoader(loadFromResourceFile(configFile), false).get();
            } catch (InvalidConfigurationException | IncompleteConfigurationException | FileNotFoundException e) {
                throw new RuntimeException("Could not load test config file from resources.", e);
            }

            DatabaseImpl db = new DatabaseImpl(configuration);
            try {
                db.migrate();
                db.initialize();
            } catch (LiquibaseException e) {
                throw new RuntimeException(e);
            }
            this.database = db;

            this.authenticationService = new AuthenticationService(this);
            this.dot11 = new Dot11(this);
            this.ethernet = new Ethernet(this);
            this.bluetooth = new Bluetooth(this);
            this.tapManager = new TapManager(this);
            this.detectionAlertService = new DetectionAlertService(this);
            this.subsystems = new Subsystems(this);
            this.healthMonitor = new HealthMonitor(this);
        }

        @Override public Database getDatabase() { return database; }
        @Override public AuthenticationService getAuthenticationService() { return authenticationService; }
        @Override public Dot11 getDot11() { return dot11; }
        @Override public Ethernet getEthernet() { return ethernet; }
        @Override public Bluetooth getBluetooth() { return bluetooth; }
        @Override public TapManager getTapManager() { return tapManager; }
        @Override public DetectionAlertService getDetectionAlertService() { return detectionAlertService; }
        @Override public Subsystems getSubsystems() { return subsystems; }
        @Override public HealthMonitor getHealthMonitor() { return healthMonitor; }
        @Override public Registry getDatabaseCoreRegistry() { return new RegistryImpl(this, "core"); }
        @Override public NodeConfiguration getConfiguration() { return configuration; }
        @Override public Optional<RetroService> retroService() { return Optional.empty(); }
        @Override public void registerRetroService(RetroService retroService) { }
        @Override public void registerRestResource(Object o) { }
        @Override public void initialize() { }
        @Override public void shutdown() { }

        private UnsupportedOperationException unavailable() {
            return new UnsupportedOperationException("Not available in the briefing test node.");
        }

        @Override public NodeManager getNodeManager() { throw unavailable(); }
        @Override public ClusterManager getClusterManager() { throw unavailable(); }
        @Override public MessageBus getMessageBus() { throw unavailable(); }
        @Override public TasksQueue getTasksQueue() { throw unavailable(); }
        @Override public Uav getUav() { throw unavailable(); }
        @Override public AssetManager getAssetsManager() { throw unavailable(); }
        @Override public ContextService getContextService() { throw unavailable(); }
        @Override public BaseConfiguration getBaseConfiguration() { throw unavailable(); }
        @Override public Path getDataDirectory() { throw unavailable(); }
        @Override public MetricRegistry getMetrics() { throw unavailable(); }
        @Override public QuotaService getQuotaService() { throw unavailable(); }
        @Override public EventEngine getEventEngine() { throw unavailable(); }
        @Override public Monitors getMonitors() { throw unavailable(); }
        @Override public TablesService getTablesService() { throw unavailable(); }
        @Override public GeoIpService getGeoIpService() { throw unavailable(); }
        @Override public OuiService getOuiService() { throw unavailable(); }
        @Override public BluetoothSigService getBluetoothSigService() { throw unavailable(); }
        // Never initialized, so it reports no environment data.
        private EnvironmentService environmentService;
        @Override public EnvironmentService getEnvironmentService() {
            if (environmentService == null) {
                environmentService = new EnvironmentService(this);
            }
            return environmentService;
        }
        @Override public List<String> getInitializedPlugins() { throw unavailable(); }
        @Override public Crypto getCrypto() { throw unavailable(); }
        @Override public ObjectMapper getObjectMapper() { throw unavailable(); }
        @Override public RegistryChangeMonitor getRegistryChangeMonitor() { return (namespace, key, runnable) -> {}; }
        // The real isEnabled() needs crypto for the API key.
        @Override public ConnectService getConnect() {
            return new ConnectService(this) {
                @Override public boolean isEnabled() { return true; }
            };
        }
        @Override public Version getVersion() { throw unavailable(); }
        @Override public NzymeHttpServer getHttpServer() { throw unavailable(); }
        @Override public NodeIdentification getNodeInformation() { throw unavailable(); }

    }

}
