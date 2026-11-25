package app.nzyme.core.assets.identity;

import app.nzyme.core.assets.identity.db.IdentityChange;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

public class IdentityChangeDetectorTest {

    private static final DateTime NOW = new DateTime(2026, 11, 24, 12, 0);
    private static final DateTime SINCE = NOW.minusHours(24);

    private static final String LINUX_V4 = "64:1:0:mss,sok,ts,nop,ws:-";
    private static final String LINUX_V6 = "64:-:0:mss,sok,ts,nop,ws:-";
    private static final String WINDOWS_V4 = "128:1:0:mss,nop,ws,nop,nop,sok:-";
    private static final String WINDOWS_V6 = "128:-:0:mss,nop,ws,nop,nop,sok:-";

    private static IdentityChangeDetector.Value old(String value) {
        return new IdentityChangeDetector.Value(value, NOW.minusDays(30), NOW.minusHours(30));
    }

    private static IdentityChangeDetector.Value stillSeen(String value) {
        return new IdentityChangeDetector.Value(value, NOW.minusDays(30), NOW.minusMinutes(5));
    }

    private static IdentityChangeDetector.Value fresh(String value) {
        return new IdentityChangeDetector.Value(value, NOW.minusHours(2), NOW.minusMinutes(5));
    }

    private static List<String> signals(Map<String, List<IdentityChangeDetector.Value>> attributes) {
        return IdentityChangeDetector.score(attributes, List.of(), SINCE).stream().map(IdentityChange::signal).toList();
    }

    @Test
    public void testFirstStackOverIpv6ReplacesNothing() {
        assertEquals(signals(Map.of("TCP_STACK", List.of(stillSeen(LINUX_V4), fresh(LINUX_V6)))), List.of());
        assertEquals(signals(Map.of("TCP_STACK", List.of(old(LINUX_V4), fresh(LINUX_V6)))), List.of());
        assertEquals(signals(Map.of("TCP_SERVER_FINGERPRINT",
                List.of(old(LINUX_V4 + ":1460:7"), fresh(LINUX_V6 + ":1440:7")))), List.of());
    }

    @Test
    public void testStackReplacedWithinAVersion() {
        assertEquals(signals(Map.of("TCP_STACK", List.of(old(LINUX_V4), fresh(WINDOWS_V4)))),
                List.of("TCP_STACK_REPLACED"));
        assertEquals(signals(Map.of("TCP_STACK",
                List.of(stillSeen(LINUX_V4), old(LINUX_V6), fresh(WINDOWS_V6)))), List.of("TCP_STACK_REPLACED"));

        assertEquals(signals(Map.of("TCP_STACK",
                List.of(old(LINUX_V4), fresh(WINDOWS_V4), old(LINUX_V6), fresh(WINDOWS_V6)))),
                List.of("TCP_STACK_REPLACED"));
    }

    @Test
    public void testDhcpv6IdentityIsItsOwnDimension() {
        assertEquals(signals(Map.of(
                "DHCP_FINGERPRINT", List.of(stillSeen("aaaa")),
                "DHCP6_FINGERPRINT", List.of(fresh("bbbb")),
                "DHCP6_CLIENT_DUID", List.of(fresh("00010001deadbeef")))), List.of());

        assertEquals(signals(Map.of("DHCP6_CLIENT_DUID", List.of(old("00010001aaaa"), fresh("00010001bbbb")))),
                List.of("DHCP6_CLIENT_DUID_REPLACED"));
    }

}
