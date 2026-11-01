package app.nzyme.core.ethernet.spoofing;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

public class DhcpOfferedParametersTest {

    private static final DhcpOfferedParameters BASELINE = DhcpOfferedParameters.create(
            "255.255.255.0", List.of("192.168.0.1"), List.of("1.1.1.1", "1.0.0.1"), "localdomain", 86400L
    );

    @Test
    public void testSameConfigurationDoesNotDeviate() {
        DhcpOfferedParameters p = DhcpOfferedParameters.create(
                "255.255.255.0", List.of("192.168.0.1"), List.of("1.0.0.1", "1.1.1.1"), "LocalDomain ", 3600L
        );

        assertFalse(p.deviatesFrom(BASELINE));
    }

    @Test
    public void testAbsentOptionsDoNotDeviate() {
        // A client that did not ask for the domain name, subnet mask or DNS servers.
        DhcpOfferedParameters p = DhcpOfferedParameters.create(null, List.of("192.168.0.1"), List.of(), null, null);

        assertFalse(p.deviatesFrom(BASELINE));
        assertFalse(DhcpOfferedParameters.create(null, List.of(), List.of(), "", null).deviatesFrom(BASELINE));
    }

    @Test
    public void testDifferentValuesDeviate() {
        assertTrue(DhcpOfferedParameters.create(null, List.of("192.168.0.254"), List.of(), null, null)
                .deviatesFrom(BASELINE));
        assertTrue(DhcpOfferedParameters.create(null, List.of(), List.of("9.9.9.9"), null, null)
                .deviatesFrom(BASELINE));
        assertTrue(DhcpOfferedParameters.create(null, List.of(), List.of(), "evil.example", null)
                .deviatesFrom(BASELINE));
        assertTrue(DhcpOfferedParameters.create("255.255.0.0", List.of(), List.of(), null, null)
                .deviatesFrom(BASELINE));
        assertTrue(DhcpOfferedParameters.create(null, List.of("192.168.0.1", "192.168.0.2"), List.of(), null, null)
                .deviatesFrom(BASELINE));
    }

    @Test
    public void testValuePresentButMissingFromBaselineDeviates() {
        DhcpOfferedParameters baseline = DhcpOfferedParameters.create(
                "255.255.255.0", List.of("192.168.0.1"), List.of("1.1.1.1"), null, null
        );

        assertTrue(DhcpOfferedParameters.create(null, List.of(), List.of(), "localdomain", null)
                .deviatesFrom(baseline));
    }

    @Test
    public void testMergedWithFillsAbsentOptions() {
        DhcpOfferedParameters partial = DhcpOfferedParameters.create(null, List.of("192.168.0.1"), List.of(), "", null);
        DhcpOfferedParameters merged = partial.mergedWith(BASELINE);

        assertEquals(merged.subnetMask(), "255.255.255.0");
        assertEquals(merged.routers(), List.of("192.168.0.1"));
        assertEquals(merged.dnsServers(), List.of("1.1.1.1", "1.0.0.1"));
        assertEquals(merged.domainName(), "localdomain");
        assertEquals(merged.leaseTimeSeconds(), Long.valueOf(86400L));
    }

    @Test
    public void testMergedWithKeepsOwnValues() {
        DhcpOfferedParameters p = DhcpOfferedParameters.create(
                "255.255.0.0", List.of("10.0.0.1"), List.of("9.9.9.9"), "other.example", 60L
        );

        assertEquals(p.mergedWith(BASELINE), p);
        assertEquals(p.mergedWith(null), p);
    }

}
