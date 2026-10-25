package app.nzyme.core.ethernet.icmp;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

public class IcmpExclusionMatcherTest {

    @Test
    public void testSourceCidr() {
        IcmpExclusionMatcher m = IcmpExclusionMatcher.create(null, "10.0.0.0/8", null, null);
        assertTrue(m.matches(IcmpDetectionType.TRACEROUTE, "10.1.2.3", null, null));
        assertFalse(m.matches(IcmpDetectionType.TRACEROUTE, "11.1.2.3", null, null));
    }

    @Test
    public void testSourceMac() {
        IcmpExclusionMatcher m = IcmpExclusionMatcher.create(null, null, "aa-bb-cc-dd-ee-ff", null);
        assertTrue(m.matches(IcmpDetectionType.REDIRECT, "10.0.0.5", "AA:BB:CC:DD:EE:FF", "10.0.0.254"));
        assertFalse(m.matches(IcmpDetectionType.REDIRECT, "10.0.0.5", "AA:BB:CC:DD:EE:00", "10.0.0.254"));
        assertFalse(m.matches(IcmpDetectionType.REDIRECT, "10.0.0.5", null, "10.0.0.254"));
    }

    @Test
    public void testDetectionTypes() {
        IcmpExclusionMatcher m = IcmpExclusionMatcher.create(List.of("traceroute"), "10.0.0.0/8", null, null);
        assertTrue(m.matches(IcmpDetectionType.TRACEROUTE, "10.0.0.5", null, null));
        assertFalse(m.matches(IcmpDetectionType.REDIRECT, "10.0.0.5", null, null));
    }

    @Test
    public void testGatewayOnlyMatchesRedirects() {
        IcmpExclusionMatcher m = IcmpExclusionMatcher.create(null, null, null, "10.0.0.254");
        // Redirect advertising that gateway: match.
        assertTrue(m.matches(IcmpDetectionType.REDIRECT, "10.0.0.5", null, "10.0.0.254"));
        // Redirect advertising a different gateway: no match.
        assertFalse(m.matches(IcmpDetectionType.REDIRECT, "10.0.0.5", null, "10.0.0.253"));
        // Traceroute has no gateway, so a gateway criterion never matches it.
        assertFalse(m.matches(IcmpDetectionType.TRACEROUTE, "10.0.0.5", null, null));
    }

    @Test
    public void testAllCriteriaMustMatch() {
        IcmpExclusionMatcher m = IcmpExclusionMatcher.create(List.of("REDIRECT"), "10.0.0.0/8", "AA:BB:CC:DD:EE:FF", "10.0.0.254");
        assertTrue(m.matches(IcmpDetectionType.REDIRECT, "10.1.1.1", "AA:BB:CC:DD:EE:FF", "10.0.0.254"));
        assertFalse(m.matches(IcmpDetectionType.REDIRECT, "11.1.1.1", "AA:BB:CC:DD:EE:FF", "10.0.0.254"));
        assertFalse(m.matches(IcmpDetectionType.TRACEROUTE, "10.1.1.1", "AA:BB:CC:DD:EE:FF", null));
    }

    @Test
    public void testNormalization() {
        assertEquals(IcmpExclusionMatcher.normalizeMac(" aa-bb-cc-dd-ee-ff "), "AA:BB:CC:DD:EE:FF");
        assertEquals(IcmpExclusionMatcher.normalizeCidr("10.0.0.5/32"), "10.0.0.5");
        assertNull(IcmpExclusionMatcher.normalizeCidr("  "));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsEmptyCriteria() {
        IcmpExclusionMatcher.create(null, null, null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsInvalidMac() {
        IcmpExclusionMatcher.create(null, null, "not-a-mac", null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsUnknownType() {
        IcmpExclusionMatcher.create(List.of("PING"), null, null, null);
    }

}
