package app.nzyme.core.database;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class DatabaseImplSessionSettingsTest {

    @Test
    public void testServerVersionCheck() {
        try {
            DatabaseImpl.assertSupportedServerVersion(130014, "13.14");
            fail("Expected exception.");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("needs PostgreSQL 14 or newer"));
            assertTrue(e.getMessage().contains("recommends 15 or newer"));
        }

        DatabaseImpl.assertSupportedServerVersion(140013, "14.13");
        DatabaseImpl.assertSupportedServerVersion(150008, "15.8");
        DatabaseImpl.assertSupportedServerVersion(180004, "18.4");
    }

    @Test
    public void testConnectionInitSql() {
        assertEquals(DatabaseImpl.buildConnectionInitSql(64), "SET work_mem = '64MB'; SET jit = off");
        // Clamped to the allowed range.
        assertEquals(DatabaseImpl.buildConnectionInitSql(1), "SET work_mem = '4MB'; SET jit = off");
        assertEquals(DatabaseImpl.buildConnectionInitSql(100000), "SET work_mem = '4096MB'; SET jit = off");
    }

}
