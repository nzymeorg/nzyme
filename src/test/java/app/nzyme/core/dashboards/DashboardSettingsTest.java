package app.nzyme.core.dashboards;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.testng.Assert.*;

public class DashboardSettingsTest {

    private static DashboardWidget widget(String id, String type, int x, int y, int w, int h, Map<String, Object> options) {
        return new DashboardWidget(id, type, x, y, w, h, options);
    }

    private static DashboardSettings withWidgets(List<DashboardWidget> widgets) {
        return DashboardSettings.validated("Wall", null, null, null, null, widgets);
    }

    @Test
    public void testDefaults() {
        DashboardSettings s = DashboardSettings.validated(" Wall ", "  ", null, null, null, null);

        assertEquals(s.name(), "Wall");
        assertNull(s.description());
        assertEquals(s.scope(), DashboardSettings.Scope.ALL);
        assertTrue(s.scopeIds().isEmpty());
        assertEquals(s.windowMinutes(), 1440);
        assertTrue(s.widgets().isEmpty());
        assertFalse(s.showsAlerts());
    }

    @Test
    public void testScopeAllDropsIds() {
        DashboardSettings s = DashboardSettings.validated("Wall", null, "all", List.of(UUID.randomUUID()), null, null);

        assertTrue(s.scopeIds().isEmpty());
    }

    @Test
    public void testScopeIdsAreDeduplicated() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        DashboardSettings s = DashboardSettings.validated("Wall", null, "locations", List.of(a, b, a), 60, null);

        assertEquals(s.scope(), DashboardSettings.Scope.LOCATIONS);
        assertEquals(s.scopeIds(), List.of(a, b));
        assertEquals(s.windowMinutes(), 60);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testScopeWithoutIdsIsRejected() {
        DashboardSettings.validated("Wall", null, "TAPS", List.of(), null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUnknownScopeIsRejected() {
        DashboardSettings.validated("Wall", null, "EVERYTHING", null, null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUnknownWindowIsRejected() {
        DashboardSettings.validated("Wall", null, null, null, 61, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testEmptyNameIsRejected() {
        DashboardSettings.validated(" ", null, null, null, null, null);
    }

    @Test
    public void testWidgetsAreNormalized() {
        DashboardSettings s = withWidgets(List.of(
                widget("a", "status", 0, 0, 4, 2, null),
                widget("b", "METRIC", 4, 0, 4, 2, Map.of("metric", "alerts_new", "title", "  New  ", "color", "red")),
                widget("c", "TAPS", 0, 2, 12, 4, Map.of("title", " "))
        ));

        assertEquals(s.widgets().get(0).type(), "STATUS");
        assertEquals(s.widgets().get(1).options(), Map.of("metric", "ALERTS_NEW", "title", "New"));
        assertTrue(s.widgets().get(2).options().isEmpty());
        assertTrue(s.showsAlerts());
    }

    @Test
    public void testDashboardWithoutAlertContent() {
        DashboardSettings s = withWidgets(List.of(
                widget("a", "TAPS", 0, 0, 6, 6, null),
                widget("b", "METRIC", 6, 0, 6, 6, Map.of("metric", "THROUGHPUT"))
        ));

        assertFalse(s.showsAlerts());
    }

    @Test
    public void testWidgetsMayTouch() {
        withWidgets(List.of(
                widget("a", "STATUS", 0, 0, 6, 3, null),
                widget("b", "ALERTS", 6, 0, 6, 3, null),
                widget("c", "TAPS", 0, 3, 12, 3, null)
        ));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testOverlappingWidgetsAreRejected() {
        withWidgets(List.of(widget("a", "STATUS", 0, 0, 6, 3, null), widget("b", "ALERTS", 5, 2, 6, 3, null)));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testWidgetOutsideOfGridIsRejected() {
        withWidgets(List.of(widget("a", "STATUS", 8, 0, 5, 2, null)));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testWidgetBelowGridIsRejected() {
        withWidgets(List.of(widget("a", "STATUS", 0, 5, 2, 2, null)));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testWidgetBelowMinimumSizeIsRejected() {
        withWidgets(List.of(widget("a", "ALERTS", 0, 0, 2, 2, null)));
    }

    @Test
    public void testWidgetAtMinimumSize() {
        withWidgets(List.of(widget("a", "ALERTS", 0, 0, 3, 1, null), widget("b", "METRIC", 3, 0, 2, 1, Map.of("metric", "THROUGHPUT"))));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testDuplicateWidgetIdIsRejected() {
        withWidgets(List.of(widget("a", "STATUS", 0, 0, 2, 2, null), widget("a", "TAPS", 4, 0, 2, 2, null)));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUnknownWidgetTypeIsRejected() {
        withWidgets(List.of(widget("a", "PIE", 0, 0, 2, 2, null)));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testMetricWidgetWithoutMetricIsRejected() {
        withWidgets(List.of(widget("a", "METRIC", 0, 0, 2, 2, null)));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUnknownMetricIsRejected() {
        withWidgets(List.of(widget("a", "METRIC", 0, 0, 2, 2, Map.of("metric", "BITCOIN"))));
    }

    @Test
    public void testLocationsWidgetShowsAlerts() {
        DashboardSettings s = withWidgets(List.of(widget("a", "LOCATIONS", 0, 0, 4, 1, null)));

        assertTrue(s.showsAlerts());
    }

    @Test
    public void testTextWidget() {
        DashboardSettings s = withWidgets(List.of(
                widget("a", "text", 0, 0, 2, 1, Map.of("text", "  Maintenance on Saturday.\nCall 555-0100.  ")),
                widget("b", "TEXT", 2, 0, 2, 1, Map.of("text", "All hands 14:00", "size", "large"))
        ));

        assertEquals(s.widgets().get(0).options(), Map.of("text", "Maintenance on Saturday.\nCall 555-0100.", "size", "MEDIUM"));
        assertEquals(s.widgets().get(1).options(), Map.of("text", "All hands 14:00", "size", "LARGE"));
        assertFalse(s.showsAlerts());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testTextWidgetWithoutTextIsRejected() {
        withWidgets(List.of(widget("a", "TEXT", 0, 0, 2, 1, Map.of("text", "  "))));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testTextWidgetWithTooLongTextIsRejected() {
        withWidgets(List.of(widget("a", "TEXT", 0, 0, 2, 1, Map.of("text", "x".repeat(2001)))));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testTextWidgetWithUnknownSizeIsRejected() {
        withWidgets(List.of(widget("a", "TEXT", 0, 0, 2, 1, Map.of("text", "x", "size", "HUGE"))));
    }

    @Test
    public void testWidgetsNotInOtherSettings() {
        DashboardSettings saved = withWidgets(List.of(
                widget("status", "STATUS", 0, 0, 2, 1, Map.of()),
                widget("alerts", "ALERTS", 2, 0, 3, 1, Map.of("subsystem", "DOT11", "title", "WiFi")),
                widget("metric", "METRIC", 5, 0, 2, 1, Map.of("metric", "THROUGHPUT")),
                widget("topology", "TOPOLOGY", 0, 1, 4, 2, Map.of("connections", 100))
        ));

        // Moved, resized, renamed, one removed: nothing new.
        DashboardSettings rearranged = withWidgets(List.of(
                widget("alerts", "ALERTS", 0, 3, 6, 2, Map.of("subsystem", "DOT11", "title", "Wireless")),
                widget("metric", "METRIC", 6, 3, 3, 2, Map.of("metric", "THROUGHPUT")),
                widget("topology", "TOPOLOGY", 0, 0, 8, 3, Map.of("connections", "100", "internal_level", "HOST"))
        ));
        assertTrue(rearranged.widgetsNotIn(saved).isEmpty());

        DashboardSettings changed = withWidgets(List.of(
                widget("status", "ALERTS", 0, 0, 3, 1, Map.of()),
                widget("alerts", "ALERTS", 3, 0, 3, 1, Map.of()),
                widget("metric", "METRIC", 6, 0, 2, 1, Map.of("metric", "ALERTS_NEW")),
                widget("topology", "TOPOLOGY", 0, 1, 4, 2, Map.of("connections", 100, "internal_level", "SUBNET")),
                widget("added", "CLOCK", 8, 0, 2, 1, Map.of("time_zone", "UTC"))
        ));
        assertEquals(changed.widgetsNotIn(saved).stream().map(DashboardWidget::id).toList(),
                List.of("status", "alerts", "metric", "topology", "added"));
        assertEquals(changed.widgetsNotIn(null).size(), 5);
        assertEquals(changed.widgetsNotIn(saved).stream().filter(DashboardSettings::showsAlerts)
                .map(DashboardWidget::id).toList(), List.of("status", "alerts", "metric"));
    }

    private static final UUID OWN_TAP = UUID.randomUUID();
    private static final UUID OTHER_TAP = UUID.randomUUID();
    private static final UUID OWN_LOCATION = UUID.randomUUID();

    private static DashboardSettings.Access restricted(boolean alerts) {
        return new DashboardSettings.Access(false, java.util.Set.of(OWN_LOCATION), java.util.Set.of(OWN_TAP), alerts);
    }

    private static DashboardSettings scoped(String scope, List<UUID> ids, Integer window, List<DashboardWidget> widgets) {
        return DashboardSettings.validated("Wall", null, scope, ids, window, widgets);
    }

    @Test
    public void testNewDashboardNeedsAccessToItsScope() {
        List<DashboardWidget> clock = List.of(widget("c", "CLOCK", 0, 0, 2, 1, Map.of("time_zone", "UTC")));

        scoped("TAPS", List.of(OWN_TAP), null, clock).requireChangeAllowed(null, () -> restricted(false));
        scoped("LOCATIONS", List.of(OWN_LOCATION), null, clock).requireChangeAllowed(null, () -> restricted(false));
        assertThrows(IllegalArgumentException.class, () -> scoped("ALL", null, null, clock)
                .requireChangeAllowed(null, () -> restricted(true)));
        assertThrows(IllegalArgumentException.class, () -> scoped("TAPS", List.of(OWN_TAP, OTHER_TAP), null, clock)
                .requireChangeAllowed(null, () -> restricted(true)));
        assertThrows(IllegalArgumentException.class, () -> scoped("LOCATIONS", List.of(UUID.randomUUID()), null, clock)
                .requireChangeAllowed(null, () -> restricted(true)));
        scoped("ALL", null, null, clock).requireChangeAllowed(null,
                () -> new DashboardSettings.Access(true, java.util.Set.of(), java.util.Set.of(), false));
    }

    @Test
    public void testWiderDashboardCanOnlyBeRenamedAndRearranged() {
        DashboardSettings saved = scoped("ALL", null, 1440, List.of(
                widget("c", "CLOCK", 0, 0, 2, 1, Map.of("time_zone", "UTC")),
                widget("s", "STATUS", 2, 0, 2, 1, Map.of())));

        // Name, layout and a removed widget: the access of the user is not even looked up.
        DashboardSettings.validated("Renamed", "x", "ALL", null, 1440, List.of(
                widget("s", "STATUS", 0, 0, 4, 2, Map.of("title", "State")))
        ).requireChangeAllowed(saved, () -> { throw new AssertionError("looked up"); });

        // Narrowing the scope to what the user can access is fine, also with new widgets.
        scoped("TAPS", List.of(OWN_TAP), 1440, List.of(
                widget("t", "TOPOLOGY", 0, 0, 4, 2, Map.of()))).requireChangeAllowed(saved, () -> restricted(false));

        assertThrows(IllegalArgumentException.class, () -> scoped("ALL", null, 1440, List.of(
                widget("c", "CLOCK", 0, 0, 2, 1, Map.of("time_zone", "UTC")),
                widget("t", "TOPOLOGY", 0, 1, 4, 2, Map.of()))).requireChangeAllowed(saved, () -> restricted(true)));
        assertThrows(IllegalArgumentException.class, () -> scoped("ALL", null, 10080, saved.widgets())
                .requireChangeAllowed(saved, () -> restricted(true)));
    }

    @Test
    public void testAlertWidgetsNeedThePermissionPerWidget() {
        DashboardSettings saved = scoped("TAPS", List.of(OWN_TAP), 1440, List.of(
                widget("s", "STATUS", 0, 0, 2, 1, Map.of()),
                widget("a", "ALERTS", 2, 0, 3, 1, Map.of("subsystem", "DOT11"))));

        // Kept as they are, next to a new widget without alerts.
        scoped("TAPS", List.of(OWN_TAP), 1440, List.of(
                widget("s", "STATUS", 0, 0, 2, 1, Map.of()),
                widget("a", "ALERTS", 2, 0, 3, 1, Map.of("subsystem", "DOT11")),
                widget("t", "TAPS", 5, 0, 2, 1, Map.of()))).requireChangeAllowed(saved, () -> restricted(false));

        // A second alert widget, a wider subsystem and an alert metric all need the permission.
        for (DashboardWidget added : List.of(
                widget("b", "ALERTS", 5, 0, 3, 1, Map.of()),
                widget("a", "ALERTS", 2, 0, 3, 1, Map.of()),
                widget("m", "METRIC", 5, 0, 2, 1, Map.of("metric", "ALERTS_NEW")),
                widget("l", "LOCATIONS", 5, 0, 4, 1, Map.of()))) {
            List<DashboardWidget> widgets = List.of(widget("s", "STATUS", 0, 0, 2, 1, Map.of()), added);

            assertThrows(IllegalArgumentException.class, () -> scoped("TAPS", List.of(OWN_TAP), 1440, widgets)
                    .requireChangeAllowed(saved, () -> restricted(false)));
            scoped("TAPS", List.of(OWN_TAP), 1440, widgets).requireChangeAllowed(saved, () -> restricted(true));
        }
    }

    @Test
    public void testScopeIdsAreDeduplicatedAndLimited() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertEquals(DashboardSettings.validated("Wall", null, "TAPS", java.util.Arrays.asList(a, null, b, a), null, null)
                .scopeIds(), List.of(a, b));

        List<UUID> many = java.util.stream.Stream.generate(UUID::randomUUID).limit(101).toList();
        assertThrows(IllegalArgumentException.class,
                () -> DashboardSettings.validated("Wall", null, "TAPS", many, null, null));
    }

    @Test
    public void testNulCharactersAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DashboardSettings.validated("Wa\u0000ll", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> withWidgets(List.of(widget("a", "TEXT", 0, 0, 2, 1, Map.of("text", "a\u0000b")))));
        assertThrows(IllegalArgumentException.class, () -> DisplaySettings.validated("Wa\u0000ll", null, null, null,
                null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    public void testAlertSubsystem() {
        DashboardSettings s = withWidgets(List.of(
                widget("a", "ALERTS", 0, 0, 3, 1, Map.of("subsystem", " dot11 ")),
                widget("b", "METRIC", 3, 0, 2, 1, Map.of("metric", "ALERTS_NEW", "subsystem", "ETHERNET")),
                widget("c", "METRIC", 5, 0, 2, 1, Map.of("metric", "THROUGHPUT", "subsystem", "ETHERNET")),
                widget("d", "ALERTS", 7, 0, 3, 1, Map.of("subsystem", " "))
        ));

        assertEquals(s.widgets().get(0).options(), Map.of("subsystem", "DOT11"));
        assertEquals(s.widgets().get(1).options(), Map.of("metric", "ALERTS_NEW", "subsystem", "ETHERNET"));
        assertEquals(s.widgets().get(2).options(), Map.of("metric", "THROUGHPUT"));
        assertEquals(s.widgets().get(3).options(), Map.of());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUnknownAlertSubsystemIsRejected() {
        withWidgets(List.of(widget("a", "ALERTS", 0, 0, 3, 1, Map.of("subsystem", "ZIGBEE"))));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUavAlertSubsystemIsRejected() {
        withWidgets(List.of(widget("a", "ALERTS", 0, 0, 3, 1, Map.of("subsystem", "UAV"))));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testGenericAlertSubsystemIsRejected() {
        withWidgets(List.of(widget("a", "ALERTS", 0, 0, 3, 1, Map.of("subsystem", "GENERIC"))));
    }

    @Test
    public void testTopologyWidget() {
        String filters = "{\"destination_port\":[{\"field\":\"destination_port\",\"operator\":\"equals_numeric\",\"value\":443}]}";
        DashboardSettings s = withWidgets(List.of(
                widget("a", "topology", 0, 0, 4, 2, Map.of()),
                widget("b", "TOPOLOGY", 4, 0, 6, 4, Map.of("filters", " " + filters + " ", "internal_level", "subnet",
                        "external_level", "country", "connections", 250))
        ));

        assertEquals(s.widgets().get(0).options(),
                Map.of("internal_level", "HOST", "external_level", "HOST", "connections", 100));
        assertEquals(s.widgets().get(1).options(), Map.of("filters", filters, "internal_level", "SUBNET",
                "external_level", "COUNTRY", "connections", 250));
        assertFalse(s.showsAlerts());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testTopologyWidgetTooSmallIsRejected() {
        withWidgets(List.of(widget("a", "TOPOLOGY", 0, 0, 4, 1, Map.of())));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testTopologyWidgetWithUnknownLevelIsRejected() {
        withWidgets(List.of(widget("a", "TOPOLOGY", 0, 0, 4, 2, Map.of("internal_level", "NETWORK"))));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testTopologyWidgetWithUnknownConnectionsIsRejected() {
        withWidgets(List.of(widget("a", "TOPOLOGY", 0, 0, 4, 2, Map.of("connections", 10000))));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testTopologyWidgetWithFilterOnUnknownFieldIsRejected() {
        withWidgets(List.of(widget("a", "TOPOLOGY", 0, 0, 4, 2, Map.of("filters",
                "{\"nope\":[{\"field\":\"nope\",\"operator\":\"equals\",\"value\":\"x\"}]}"))));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testTopologyWidgetWithBrokenFiltersIsRejected() {
        withWidgets(List.of(widget("a", "TOPOLOGY", 0, 0, 4, 2, Map.of("filters", "{not json"))));
    }

    @Test
    public void testClockWidget() {
        DashboardSettings s = withWidgets(List.of(widget("a", "clock", 0, 0, 2, 1, Map.of("time_zone", " Europe/Berlin "))));

        assertEquals(s.widgets().get(0).type(), "CLOCK");
        assertEquals(s.widgets().get(0).options(), Map.of("time_zone", "Europe/Berlin"));
        assertFalse(s.showsAlerts());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testClockWidgetWithoutTimeZoneIsRejected() {
        withWidgets(List.of(widget("a", "CLOCK", 0, 0, 2, 2, null)));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testClockWidgetWithUnknownTimeZoneIsRejected() {
        withWidgets(List.of(widget("a", "CLOCK", 0, 0, 2, 2, Map.of("time_zone", "Mars/Olympus"))));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testTooManyWidgetsAreRejected() {
        List<DashboardWidget> widgets = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            widgets.add(widget("w" + i, "STATUS", (i % 6) * 2, i / 6, 2, 1, null));
        }

        withWidgets(widgets);
    }

}
