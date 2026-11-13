package app.nzyme.core.dashboards;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.*;

public class DisplaySettingsTest {

    private static DisplaySettings withNetworks(List<String> networks) {
        return DisplaySettings.validated("Wall", null, null, null, null, null, null, null, null, null, networks, null, null, null);
    }

    @Test
    public void testAddressesAndNamesAreShownUnlessHidden() {
        assertFalse(DisplaySettings.validated("Wall", null, null, null, null, null, null, null, null, null, null, null, null, null).redacted());
        assertFalse(DisplaySettings.validated("Wall", null, null, null, null, null, null, null, null, null, null, false, null, null).redacted());
        assertTrue(DisplaySettings.validated("Wall", null, null, null, null, null, null, null, null, null, null, true, null, null).redacted());
    }

    @Test
    public void testCursorIsShownUnlessHidden() {
        assertFalse(DisplaySettings.validated("Wall", null, null, null, null, null, null, null, null, null, null, null, null, null).hideCursor());
        assertFalse(DisplaySettings.validated("Wall", null, null, null, null, null, null, null, null, null, null, null, false, null).hideCursor());
        assertTrue(DisplaySettings.validated("Wall", null, null, null, null, null, null, null, null, null, null, null, true, null).hideCursor());
    }

    @Test
    public void testDefaults() {
        DisplaySettings s = DisplaySettings.validated(" Wall ", null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertEquals(s.name(), "Wall");
        assertNull(s.locationId());
        assertEquals(s.theme(), DisplaySettings.Theme.DARK);
        assertEquals(s.timeZone(), "UTC");
        assertEquals(s.clockFormat(), DisplaySettings.ClockFormat.H24);
        assertEquals(s.scalePercent(), 100);
        assertEquals(s.rotationSeconds(), 30);
        assertEquals(s.refreshSeconds(), 60);
        assertTrue(s.allowedNetworks().isEmpty());
        assertNull(s.expiresAt());
    }

    @Test
    public void testKeepsAllValues() {
        DateTime expiresAt = DateTime.now().plusDays(1);
        UUID locationId = UUID.randomUUID();
        DisplaySettings s = DisplaySettings.validated("Wall", locationId, "scheduled", "07:00", "19:30",
                "Europe/Berlin", "h12", 150, 20, 10, List.of("10.0.0.0/8"), null, null, expiresAt);

        assertEquals(s.locationId(), locationId);
        assertEquals(s.theme(), DisplaySettings.Theme.SCHEDULED);
        assertEquals(s.lightFrom(), "07:00");
        assertEquals(s.lightUntil(), "19:30");
        assertEquals(s.timeZone(), "Europe/Berlin");
        assertEquals(s.clockFormat(), DisplaySettings.ClockFormat.H12);
        assertEquals(s.scalePercent(), 150);
        assertEquals(s.rotationSeconds(), 20);
        assertEquals(s.refreshSeconds(), 10);
        assertEquals(s.expiresAt(), expiresAt);
    }

    @Test
    public void testDropsScheduleOfFixedTheme() {
        DisplaySettings s = DisplaySettings.validated("Wall", null, "LIGHT", "07:00", "19:30", null, null, null,
                null, null, null, null, null, null);

        assertNull(s.lightFrom());
        assertNull(s.lightUntil());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsEmptyName() {
        DisplaySettings.validated("  ", null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsLongName() {
        DisplaySettings.validated("x".repeat(129), null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsUnknownTheme() {
        DisplaySettings.validated("Wall", null, "BLUE", null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsScheduledThemeWithoutTimes() {
        DisplaySettings.validated("Wall", null, "SCHEDULED", "07:00", null, null, null, null, null, null, null, null, null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsMalformedScheduleTime() {
        DisplaySettings.validated("Wall", null, "SCHEDULED", "7:00", "24:00", null, null, null, null, null, null, null, null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsScheduleWithoutDuration() {
        DisplaySettings.validated("Wall", null, "SCHEDULED", "07:00", "07:00", null, null, null, null, null, null, null, null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsUnknownTimeZone() {
        DisplaySettings.validated("Wall", null, null, null, null, "Mars/Olympus", null, null, null, null, null, null, null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsRefreshBelowMinimum() {
        DisplaySettings.validated("Wall", null, null, null, null, null, null, null, null, 9, null, null, null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsScaleAboveMaximum() {
        DisplaySettings.validated("Wall", null, null, null, null, null, null, 301, null, null, null, null, null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsRotationBelowMinimum() {
        DisplaySettings.validated("Wall", null, null, null, null, null, null, null, 4, null, null, null, null, null);
    }

    @Test
    public void testNormalizesAllowedNetworks() {
        DisplaySettings s = withNetworks(List.of(" 10.0.0.0/8 ", "192.168.1.5", "", "10.0.0.0/8", "fd00::/8"));

        assertEquals(s.allowedNetworks(), List.of("10.0.0.0/8", "192.168.1.5/32", "fd00::/8"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsInvalidNetwork() {
        withNetworks(List.of("10.0.0.0/33"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsNetworkWithHostBits() {
        withNetworks(List.of("10.0.0.1/8"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsTooManyNetworks() {
        List<String> networks = new java.util.ArrayList<>();
        for (int i = 0; i <= DisplaySettings.MAXIMUM_ALLOWED_NETWORKS; i++) {
            networks.add("10.0." + i + ".0/24");
        }

        withNetworks(networks);
    }

    @Test
    public void testAllowsEveryAddressWithoutNetworks() {
        DisplaySettings s = withNetworks(Collections.emptyList());

        assertTrue(s.allowsAddress("203.0.113.9"));
        assertTrue(s.allowsAddress(null));
    }

    @Test
    public void testAllowsOnlyAddressesInNetworks() {
        DisplaySettings s = withNetworks(List.of("10.0.0.0/8", "192.168.1.5", "fd00::/8"));

        assertTrue(s.allowsAddress("10.255.0.1"));
        assertTrue(s.allowsAddress("192.168.1.5"));
        assertTrue(s.allowsAddress("fd12::1"));
        assertFalse(s.allowsAddress("192.168.1.6"));
        assertFalse(s.allowsAddress("11.0.0.1"));
        assertFalse(s.allowsAddress("2001:db8::1"));
        assertFalse(s.allowsAddress(null));
        assertFalse(s.allowsAddress("not an address"));
    }

}
