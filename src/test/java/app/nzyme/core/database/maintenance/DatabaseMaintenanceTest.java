package app.nzyme.core.database.maintenance;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.*;

public class DatabaseMaintenanceTest {

    @Test
    public void testDesiredStorageParametersPerClass() {
        Map<String, String> upsert = DatabaseMaintenance.desiredStorageParameters(TableClass.UPSERT, new DatabaseMaintenance.ClassParameters(1, 1000, 2, 85));
        assertEquals(upsert.get("autovacuum_vacuum_scale_factor"), "0.01");
        assertEquals(upsert.get("autovacuum_vacuum_threshold"), "1000");
        assertEquals(upsert.get("autovacuum_analyze_scale_factor"), "0.02");
        assertEquals(upsert.get("fillfactor"), "85");
        assertEquals(upsert.get("autovacuum_vacuum_cost_delay"), "0");
        assertFalse(upsert.containsKey("autovacuum_vacuum_insert_scale_factor"));

        Map<String, String> append = DatabaseMaintenance.desiredStorageParameters(TableClass.APPEND, new DatabaseMaintenance.ClassParameters(5, 1000, 2, 100));
        assertEquals(append.get("autovacuum_vacuum_insert_scale_factor"), "0.05");
        assertFalse(append.containsKey("autovacuum_vacuum_cost_delay"));

        Map<String, String> hot = DatabaseMaintenance.desiredStorageParameters(TableClass.HOT, new DatabaseMaintenance.ClassParameters(5, 50, 10, 50));
        assertEquals(hot.get("fillfactor"), "50");
        assertEquals(hot.get("autovacuum_analyze_scale_factor"), "0.10");
    }

    @Test
    public void testMaintenanceWindow() {
        DateTime three = new DateTime(2026, 11, 8, 3, 30, DateTimeZone.UTC);
        DateTime noon = new DateTime(2026, 11, 8, 12, 0, DateTimeZone.UTC);
        DateTime oneAm = new DateTime(2026, 11, 8, 1, 0, DateTimeZone.UTC);

        assertTrue(DatabaseMaintenance.isInMaintenanceWindow(three, 2, 4));
        assertFalse(DatabaseMaintenance.isInMaintenanceWindow(noon, 2, 4));
        assertFalse(DatabaseMaintenance.isInMaintenanceWindow(new DateTime(2026, 11, 8, 6, 0, DateTimeZone.UTC), 2, 4));

        assertTrue(DatabaseMaintenance.isInMaintenanceWindow(oneAm, 22, 4));
        assertFalse(DatabaseMaintenance.isInMaintenanceWindow(three, 22, 4));

        assertTrue(DatabaseMaintenance.isInMaintenanceWindow(three.withZone(DateTimeZone.forID("America/Chicago")), 2, 4));

        assertTrue(DatabaseMaintenance.isInMaintenanceWindow(noon, 0, 24));
    }

    @Test
    public void testEveryTableBelongsToAtMostOneClass() {
        for (TableClass a : TableClass.values()) {
            for (String table : a.getTables()) {
                assertEquals(TableClass.ofTable(table).orElseThrow(), a, table);
                for (TableClass b : TableClass.values()) {
                    if (a != b) {
                        assertFalse(b.getTables().contains(table), table + " is in " + a + " and " + b);
                    }
                }
            }
        }

        assertTrue(TableClass.ofTable("auth_sessions").isEmpty());
    }

}
