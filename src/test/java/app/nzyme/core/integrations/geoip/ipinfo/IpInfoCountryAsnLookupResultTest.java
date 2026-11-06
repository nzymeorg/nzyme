package app.nzyme.core.integrations.geoip.ipinfo;

import com.maxmind.db.Reader;
import org.testng.annotations.Test;

import java.io.File;
import java.net.InetAddress;
import java.util.Map;

import static org.testng.Assert.*;

public class IpInfoCountryAsnLookupResultTest {

    private static IpInfoCountryAsnLookupResult build(String country, String countryCode, String countryName,
                                                      String asName, String asn, String asDomain,
                                                      String city, String region, String postalCode, String timezone,
                                                      Object latitude, Object longitude) {
        return new IpInfoCountryAsnLookupResult(country, countryCode, countryName, asName, asn, asDomain,
                city, region, postalCode, timezone, latitude, longitude, null, null,
                null, null, null, null, null, null, null, null);
    }

    @Test
    public void testProductionStandardLocationSchemaWithLatLngKeys() {
        // Production standard_location.mmdb files use "lat" and "lng" keys.
        IpInfoCountryAsnLookupResult r = new IpInfoCountryAsnLookupResult(
                "US", null, null, null, null, null,
                "Mountain View", "California", "94043", "America/Los_Angeles", null, null, "37.4056", -122.0775d,
                null, null, null, null, null, null, null, null
        );

        assertEquals(r.getCountryCode(), "US");
        assertEquals(r.getCity(), "Mountain View");
        assertEquals(r.getLatitude(), 37.4056f, 0.0001f);
        assertEquals(r.getLongitude(), -122.0775f, 0.0001f);
        assertNull(r.getAsNumber());
    }

    private static File sample(String name) {
        return new File("src/test/resources/geoip/ipinfo/" + name + ".mmdb");
    }

    private static IpInfoCountryAsnLookupResult lookup(String file, String address) throws Exception {
        try (Reader reader = new Reader(sample(file))) {
            return reader.get(InetAddress.getByName(address), IpInfoCountryAsnLookupResult.class);
        }
    }

    @Test
    public void testLegacyFreeCountryAsnSchema() {
        IpInfoCountryAsnLookupResult r = build(
                "US", null, "United States", "Google LLC", "AS15169", "google.com",
                null, null, null, null, null, null
        );

        assertEquals(r.getCountryCode(), "US");
        assertEquals(r.getCountryName(), "United States");
        assertEquals(r.getAsName(), "Google LLC");
        assertEquals(r.getAsNumber(), "AS15169");
        assertEquals(r.getAsDomain(), "google.com");
        assertNull(r.getCity());
        assertNull(r.getRegion());
        assertNull(r.getPostalCode());
        assertNull(r.getTimezone());
        assertNull(r.getLatitude());
        assertNull(r.getLongitude());
    }

    @Test
    public void testLegacyPaidStandardLocationSchemaWithStringCoordinates() {
        IpInfoCountryAsnLookupResult r = build(
                "US", null, null, null, null, null,
                "Charlottesville", "Virginia", "22902", "America/New_York", "38.02931", "-78.47668"
        );

        assertEquals(r.getCountryCode(), "US");
        assertNull(r.getCountryName());
        assertNull(r.getAsName());
        assertNull(r.getAsNumber());
        assertNull(r.getAsDomain());
        assertEquals(r.getCity(), "Charlottesville");
        assertEquals(r.getRegion(), "Virginia");
        assertEquals(r.getPostalCode(), "22902");
        assertEquals(r.getTimezone(), "America/New_York");
        assertEquals(r.getLatitude(), 38.02931f, 0.00001f);
        assertEquals(r.getLongitude(), -78.47668f, 0.00001f);
    }

    @Test
    public void testNewSchemaCountryNameAndCode() {
        IpInfoCountryAsnLookupResult r = build(
                "Australia", "AU", null, null, null, null,
                "Sydney", "New South Wales", "1001", "Australia/Sydney", -33.86785d, 151.20732d
        );

        assertEquals(r.getCountryCode(), "AU");
        assertEquals(r.getCountryName(), "Australia");
        assertEquals(r.getCity(), "Sydney");
        assertEquals(r.getLatitude(), -33.86785f, 0.0001f);
        assertEquals(r.getLongitude(), 151.20732f, 0.0001f);
    }

    @Test
    public void testCountryNameWithoutCodeKey() {
        IpInfoCountryAsnLookupResult r = build(
                "Australia", null, null, null, null, null,
                null, null, null, null, null, null
        );

        assertNull(r.getCountryCode());
        assertEquals(r.getCountryName(), "Australia");
    }

    @Test
    public void testMaxMindCompatibleSchema() {
        IpInfoCountryAsnLookupResult r = new IpInfoCountryAsnLookupResult(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                "Tempe", "US", "United States", "Arizona", "85285", "America/Phoenix", 33.41477d, "-111.90931"
        );

        assertEquals(r.getCountryCode(), "US");
        assertEquals(r.getCountryName(), "United States");
        assertEquals(r.getCity(), "Tempe");
        assertEquals(r.getRegion(), "Arizona");
        assertEquals(r.getPostalCode(), "85285");
        assertEquals(r.getTimezone(), "America/Phoenix");
        assertEquals(r.getLatitude(), 33.41477f, 0.0001f);
        assertEquals(r.getLongitude(), -111.90931f, 0.0001f);
    }

    @Test
    public void testEmptyAndInvalidValuesBecomeNull() {
        IpInfoCountryAsnLookupResult r = build(
                "EG", null, "", "", "", "",
                "Cairo", "", "", "Africa/Cairo", "", "not-a-number"
        );

        assertEquals(r.getCountryCode(), "EG");
        assertNull(r.getCountryName());
        assertNull(r.getAsName());
        assertNull(r.getAsNumber());
        assertNull(r.getAsDomain());
        assertEquals(r.getCity(), "Cairo");
        assertNull(r.getRegion());
        assertNull(r.getPostalCode());
        assertNull(r.getLatitude());
        assertNull(r.getLongitude());
    }

    // Decoding of real IPinfo sample files.

    @Test
    public void testSampleLegacyCountryAsn() throws Exception {
        IpInfoCountryAsnLookupResult r = lookup("ip_country_asn_sample", "1.0.0.1");

        assertEquals(r.getCountryCode(), "AU");
        assertEquals(r.getCountryName(), "Australia");
        assertEquals(r.getAsNumber(), "AS13335");
        assertEquals(r.getAsName(), "Cloudflare, Inc.");
        assertEquals(r.getAsDomain(), "cloudflare.com");
        assertNull(r.getCity());
        assertNull(r.getLatitude());
        assertNull(r.getLongitude());
    }

    @Test
    public void testSampleLegacyCountryAsnEmptyAsn() throws Exception {
        IpInfoCountryAsnLookupResult r = lookup("ip_country_asn_sample", "1.0.8.1");

        assertEquals(r.getCountryCode(), "CN");
        assertEquals(r.getCountryName(), "China");
        assertNull(r.getAsNumber());
        assertNull(r.getAsName());
        assertNull(r.getAsDomain());
    }

    @Test
    public void testSampleLite() throws Exception {
        IpInfoCountryAsnLookupResult r = lookup("ipinfo_lite_sample", "1.0.0.1");

        assertEquals(r.getCountryCode(), "AU");
        assertEquals(r.getCountryName(), "Australia");
        assertEquals(r.getAsNumber(), "AS13335");
        assertEquals(r.getAsName(), "Cloudflare, Inc.");
        assertEquals(r.getAsDomain(), "cloudflare.com");
        assertNull(r.getCity());
        assertNull(r.getLatitude());
    }

    @Test
    public void testSampleLocation() throws Exception {
        IpInfoCountryAsnLookupResult r = lookup("ipinfo_location_sample", "1.0.1.1");

        assertEquals(r.getCountryCode(), "CN");
        assertEquals(r.getCountryName(), "China");
        assertEquals(r.getCity(), "Fuzhou");
        assertEquals(r.getRegion(), "Fujian");
        assertEquals(r.getPostalCode(), "350000");
        assertEquals(r.getTimezone(), "Asia/Shanghai");
        assertEquals(r.getLatitude(), 26.06139f, 0.0001f);
        assertEquals(r.getLongitude(), 119.30611f, 0.0001f);
        assertNull(r.getAsNumber());
    }

    @Test
    public void testSampleCore() throws Exception {
        IpInfoCountryAsnLookupResult r = lookup("ipinfo_core_sample", "1.0.0.1");

        assertEquals(r.getCountryCode(), "AU");
        assertEquals(r.getCountryName(), "Australia");
        assertEquals(r.getCity(), "Sydney");
        assertEquals(r.getLatitude(), -33.86785f, 0.0001f);
        assertEquals(r.getLongitude(), 151.20732f, 0.0001f);
        assertEquals(r.getAsNumber(), "AS13335");
        assertEquals(r.getAsName(), "Cloudflare, Inc.");
        assertEquals(r.getAsDomain(), "cloudflare.com");
    }

    @Test
    public void testSampleFilesDecodeAsMap() throws Exception {
        // Sanity check that the sample files are readable at all.
        try (Reader reader = new Reader(sample("ipinfo_core_sample"))) {
            Map<?, ?> m = reader.get(InetAddress.getByName("1.0.0.1"), Map.class);
            assertNotNull(m);
            assertTrue(m.containsKey("latitude"));
            assertTrue(m.containsKey("asn"));
        }
    }

}
