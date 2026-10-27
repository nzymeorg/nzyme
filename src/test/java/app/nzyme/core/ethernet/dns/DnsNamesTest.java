package app.nzyme.core.ethernet.dns;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class DnsNamesTest {

    @Test
    public void testSubdomain() {
        assertEquals(DnsNames.subdomain("a.b.example.com", "example.com"), "a.b");
        assertEquals(DnsNames.subdomain("WWW.Example.com.", "example.com"), "www");
        assertNull(DnsNames.subdomain("example.com", "example.com"));
        assertNull(DnsNames.subdomain("example.com.", "example.com"));
        assertNull(DnsNames.subdomain("notexample.com", "example.com"));
        assertNull(DnsNames.subdomain("host.corp", null));
        assertNull(DnsNames.subdomain(null, "example.com"));
    }

    @Test
    public void testShannonEntropy() {
        assertEquals(DnsNames.shannonEntropy(""), 0.0);
        assertEquals(DnsNames.shannonEntropy("aaaa"), 0.0);
        assertEquals(DnsNames.shannonEntropy("ab"), 1.0, 0.0001);
        assertEquals(DnsNames.shannonEntropy("0123456789abcdef"), 4.0, 0.0001);
    }

    @Test
    public void testSubdomainEntropy() {
        assertNull(DnsNames.subdomainEntropy("example.com", "example.com"));
        assertEquals(DnsNames.subdomainEntropy("aaaa.example.com", "example.com"), 0.0f, 0.0001f);
        assertEquals(DnsNames.subdomainEntropy("0123456789abcdef.example.com", "example.com"), 4.0f, 0.0001f);
    }

}
