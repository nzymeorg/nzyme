package app.nzyme.core.ethernet.dns.tunnels;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class DnsTunnelExclusionMatcherTest {

    @Test
    public void testDomainExactAndParent() {
        DnsTunnelExclusionMatcher m = DnsTunnelExclusionMatcher.create("Example.com.", null);

        assertTrue(m.matches("example.com", "10.0.0.1"));
        assertTrue(m.matches("sub.example.com", "10.0.0.1"));
        assertTrue(m.matches("EXAMPLE.COM", null));
        assertFalse(m.matches("notexample.com", "10.0.0.1"));
        assertFalse(m.matches("example.org", "10.0.0.1"));
        assertFalse(m.matches(null, "10.0.0.1"));
    }

    @Test
    public void testClientCidr() {
        DnsTunnelExclusionMatcher m = DnsTunnelExclusionMatcher.create(null, "10.0.0.0/8");

        assertTrue(m.matches("example.com", "10.1.2.3"));
        assertFalse(m.matches("example.com", "11.1.2.3"));
        assertFalse(m.matches("example.com", null));
    }

    @Test
    public void testBothCriteria() {
        DnsTunnelExclusionMatcher m = DnsTunnelExclusionMatcher.create("example.com", "192.168.0.10");

        assertTrue(m.matches("example.com", "192.168.0.10"));
        assertFalse(m.matches("example.com", "192.168.0.11"));
        assertFalse(m.matches("example.org", "192.168.0.10"));
    }

    @Test
    public void testNormalization() {
        assertEquals(DnsTunnelExclusionMatcher.normalizeDomain("  Foo.Example.COM.  "), "foo.example.com");
        assertNull(DnsTunnelExclusionMatcher.normalizeDomain("   "));
        assertEquals(DnsTunnelExclusionMatcher.normalizeCidr("10.0.0.1/32"), "10.0.0.1");
        assertEquals(DnsTunnelExclusionMatcher.normalizeCidr("10.0.0.0/8"), "10.0.0.0/8");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRequiresCriterion() {
        DnsTunnelExclusionMatcher.create(" ", null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsInvalidDomain() {
        DnsTunnelExclusionMatcher.create("exa mple.com", null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsHostBits() {
        DnsTunnelExclusionMatcher.create(null, "10.0.0.1/8");
    }

}
