package app.nzyme.core.database.maintenance;

import app.nzyme.core.database.maintenance.DatabaseStatistics.ServerSetting;
import app.nzyme.core.database.maintenance.DatabaseStatistics.SettingSeverity;
import app.nzyme.core.database.maintenance.ServerSettingRecommendations.Input;
import app.nzyme.core.database.maintenance.ServerSettingRecommendations.Setting;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.testng.Assert.*;

public class ServerSettingRecommendationsTest {

    private static Map<String, Setting> defaults() {
        Map<String, Setting> s = new HashMap<>();
        s.put("autovacuum", new Setting("autovacuum", "on", null, "default"));
        s.put("shared_buffers", new Setting("shared_buffers", "16384", "8kB", "configuration file"));
        s.put("effective_cache_size", new Setting("effective_cache_size", "524288", "8kB", "default"));
        s.put("maintenance_work_mem", new Setting("maintenance_work_mem", "65536", "kB", "default"));
        s.put("work_mem", new Setting("work_mem", "4096", "kB", "default"));
        s.put("autovacuum_vacuum_cost_delay", new Setting("autovacuum_vacuum_cost_delay", "2", "ms", "default"));
        s.put("autovacuum_vacuum_cost_limit", new Setting("autovacuum_vacuum_cost_limit", "-1", null, "default"));
        s.put("vacuum_cost_limit", new Setting("vacuum_cost_limit", "200", null, "default"));
        s.put("autovacuum_max_workers", new Setting("autovacuum_max_workers", "3", null, "default"));
        s.put("max_wal_size", new Setting("max_wal_size", "1024", "MB", "configuration file"));
        s.put("checkpoint_timeout", new Setting("checkpoint_timeout", "300", "s", "default"));
        s.put("random_page_cost", new Setting("random_page_cost", "4", null, "default"));
        s.put("track_io_timing", new Setting("track_io_timing", "off", null, "default"));
        s.put("jit", new Setting("jit", "on", null, "default"));
        s.put("max_connections", new Setting("max_connections", "100", null, "configuration file"));
        return s;
    }

    private static Map<String, ServerSetting> byName(List<ServerSetting> settings) {
        Map<String, ServerSetting> m = new HashMap<>();
        for (ServerSetting s : settings) {
            m.put(s.name(), s);
        }
        return m;
    }

    @Test
    public void testPostgresDefaultsFailEveryRequiredSettingExceptAutovacuumAndVersion() {
        Map<String, ServerSetting> r = byName(ServerSettingRecommendations.evaluate(new Input(
                defaults(), 180004, "18.4", OptionalLong.of(32L * 1024 * 1024 * 1024), true, 64, true
        )));

        assertTrue(r.get("server_version").ok());
        assertTrue(r.get("autovacuum").ok());
        assertFalse(r.get("shared_buffers").ok());
        assertFalse(r.get("maintenance_work_mem").ok());
        assertFalse(r.get("autovacuum_vacuum_cost_delay").ok());
        assertFalse(r.get("autovacuum_max_workers").ok());
        assertFalse(r.get("max_wal_size").ok());

        assertEquals(r.get("shared_buffers").configLines(), "shared_buffers = 8GB");
        assertEquals(r.get("shared_buffers").currentValue(), "128MB");
        assertEquals(r.get("maintenance_work_mem").currentValue(), "64MB");
        assertEquals(r.get("max_wal_size").currentValue(), "1GB");
        assertTrue(r.get("autovacuum_vacuum_cost_delay").configLines().contains("autovacuum_vacuum_cost_limit = 2000"));

        for (ServerSetting s : r.values()) {
            if (s.ok()) {
                assertNull(s.configLines(), s.name());
            }
        }
    }

    @Test
    public void testTunedServerPasses() {
        Map<String, Setting> s = defaults();
        s.put("shared_buffers", new Setting("shared_buffers", "1048576", "8kB", "configuration file")); // 8 GB
        s.put("effective_cache_size", new Setting("effective_cache_size", "3145728", "8kB", "configuration file"));
        s.put("maintenance_work_mem", new Setting("maintenance_work_mem", "1048576", "kB", "configuration file"));
        s.put("autovacuum_vacuum_cost_delay", new Setting("autovacuum_vacuum_cost_delay", "1", "ms", "configuration file"));
        s.put("autovacuum_max_workers", new Setting("autovacuum_max_workers", "6", null, "configuration file"));
        s.put("max_wal_size", new Setting("max_wal_size", "8192", "MB", "configuration file"));
        s.put("checkpoint_timeout", new Setting("checkpoint_timeout", "900", "s", "configuration file"));
        s.put("random_page_cost", new Setting("random_page_cost", "1.1", null, "configuration file"));
        s.put("track_io_timing", new Setting("track_io_timing", "on", null, "configuration file"));

        List<ServerSetting> result = ServerSettingRecommendations.evaluate(new Input(
                s, 160002, "16.2", OptionalLong.empty(), true, 64, true
        ));

        for (ServerSetting setting : result) {
            assertTrue(setting.ok(), setting.name() + " should be ok");
            assertNull(setting.configLines(), setting.name());
        }
    }

    @Test
    public void testCostLimitAloneSatisfiesThrottleRule() {
        Map<String, Setting> s = defaults();
        s.put("autovacuum_vacuum_cost_limit", new Setting("autovacuum_vacuum_cost_limit", "2000", null, "configuration file"));

        Map<String, ServerSetting> r = byName(ServerSettingRecommendations.evaluate(new Input(
                s, 180004, "18.4", OptionalLong.empty(), true, 64, true
        )));

        assertTrue(r.get("autovacuum_vacuum_cost_delay").ok());
        assertEquals(r.get("autovacuum_vacuum_cost_delay").currentValue(), "2ms delay, cost limit 2000");
    }

    @Test
    public void testOldVersionAndDisabledAutovacuumAreRequiredFailures() {
        Map<String, Setting> s = defaults();
        s.put("autovacuum", new Setting("autovacuum", "off", null, "configuration file"));

        Map<String, ServerSetting> r = byName(ServerSettingRecommendations.evaluate(new Input(
                s, 140013, "14.13", OptionalLong.empty(), true, 64, true
        )));

        assertFalse(r.get("server_version").ok());
        assertEquals(r.get("server_version").severity(), SettingSeverity.REQUIRED);
        assertEquals(r.get("server_version").recommendedValue(), "PostgreSQL 15 or newer");

        Map<String, ServerSetting> recommended = byName(ServerSettingRecommendations.evaluate(new Input(
                s, 150008, "15.8", OptionalLong.empty(), true, 64, true
        )));
        assertTrue(recommended.get("server_version").ok());
        assertFalse(r.get("autovacuum").ok());
        assertEquals(r.get("autovacuum").configLines(), "autovacuum = on");
    }

    @Test
    public void testSessionAndTableScopesReflectNzymeSettings() {
        Map<String, ServerSetting> tuned = byName(ServerSettingRecommendations.evaluate(new Input(
                defaults(), 180004, "18.4", OptionalLong.empty(), true, 128, true
        )));
        assertTrue(tuned.get("work_mem").ok());
        assertTrue(tuned.get("work_mem").currentValue().contains("128MB on Nzyme connections"));
        assertTrue(tuned.get("jit").ok());
        assertTrue(tuned.get("table storage parameters").ok());

        Map<String, ServerSetting> untuned = byName(ServerSettingRecommendations.evaluate(new Input(
                defaults(), 180004, "18.4", OptionalLong.empty(), false, 128, false
        )));
        assertFalse(untuned.get("work_mem").ok());
        assertFalse(untuned.get("jit").ok());
        assertFalse(untuned.get("table storage parameters").ok());
    }

    @Test
    public void testUnitConversions() {
        Map<String, Setting> s = new HashMap<>();
        s.put("a", new Setting("a", "16384", "8kB", "default"));
        s.put("b", new Setting("b", "2", "GB", "default"));
        s.put("c", new Setting("c", "500", "ms", "default"));
        s.put("d", new Setting("d", "15", "min", "default"));
        Input in = new Input(s, 0, "", OptionalLong.empty(), true, 64, true);

        assertEquals(ServerSettingRecommendations.bytes(in, "a", 0), 128L * 1024 * 1024);
        assertEquals(ServerSettingRecommendations.bytes(in, "b", 0), 2L * 1024 * 1024 * 1024);
        assertEquals(ServerSettingRecommendations.bytes(in, "missing", 42), 42);
        assertEquals(ServerSettingRecommendations.millis(in, "c", 0), 500.0);
        assertEquals(ServerSettingRecommendations.seconds(in, "d", 0), 900.0);
        assertEquals(ServerSettingRecommendations.human(1536L * 1024 * 1024), "1.5GB");
        assertEquals(ServerSettingRecommendations.human(64L * 1024 * 1024), "64MB");
    }

}
