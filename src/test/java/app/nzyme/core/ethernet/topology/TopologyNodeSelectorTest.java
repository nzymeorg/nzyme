package app.nzyme.core.ethernet.topology;

import app.nzyme.core.ethernet.topology.TopologyNodeSelector.Kind;
import app.nzyme.core.ethernet.topology.TopologyNodeSelector.Side;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.*;

public class TopologyNodeSelectorTest {

    @Test
    public void testParsesAllKinds() {
        assertEquals(TopologyNodeSelector.parse("host:192.168.0.5").kind(), Kind.HOST);
        assertEquals(TopologyNodeSelector.parse("subnet:192.168.0.0/24").kind(), Kind.SUBNET);
        assertEquals(TopologyNodeSelector.parse("tap:3b158c02-6d70-4600-8ac5-51365a23ce03").kind(), Kind.TAP);
        assertEquals(TopologyNodeSelector.parse("ext:203.0.113.7").kind(), Kind.EXTERNAL_HOST);
        assertEquals(TopologyNodeSelector.parse("asn:15169").kind(), Kind.ASN);
        assertEquals(TopologyNodeSelector.parse("noasn:US").kind(), Kind.NO_ASN);
        assertEquals(TopologyNodeSelector.parse("noasn:-").kind(), Kind.NO_ASN);
        assertEquals(TopologyNodeSelector.parse("cc:DE").kind(), Kind.COUNTRY);
        assertEquals(TopologyNodeSelector.parse("cc:-").kind(), Kind.COUNTRY);
        assertEquals(TopologyNodeSelector.parse("loc:52.52,13.4").kind(), Kind.LOCATION);
    }

    @Test
    public void testIdRoundTrips() {
        for (String id : new String[]{"host:192.168.0.5", "subnet:192.168.0.0/24", "subnet:fd00::/64",
                "tap:3b158c02-6d70-4600-8ac5-51365a23ce03", "ext:203.0.113.7", "ext:2001:db8::1", "asn:15169",
                "noasn:US", "noasn:-", "cc:DE", "cc:-", "loc:52.52,13.4", "loc:-33.87,151.21"}) {
            assertEquals(TopologyNodeSelector.parse(id).id(), id);
        }
    }

    @Test
    public void testNormalizesValues() {
        assertEquals(TopologyNodeSelector.parse("asn:015169").value(), "15169");
        // Coordinates are kept as hundredths of a degree.
        assertEquals(TopologyNodeSelector.parse("loc:52.524,13.405").value(), "5252,1341");
    }

    @Test
    public void testKeepsStoredValuesAsTheyAre() {
        // IDs carry what the database prints. A changed value would no longer match the stored one.
        assertEquals(TopologyNodeSelector.parse("cc:de").value(), "de");
        assertEquals(TopologyNodeSelector.parse("ext:::ffff:192.0.2.1").value(), "::ffff:192.0.2.1");
        assertEquals(TopologyNodeSelector.parse("subnet:fe80::/64").value(), "fe80::/64");
        assertEquals(TopologyNodeSelector.parse("subnet:10.0.0.0/32").value(), "10.0.0.0/32");
    }

    @Test
    public void testRejectsAddressesTheDatabaseWouldNotTake() {
        for (String id : new String[]{"subnet:10.0.0.0/33", "subnet:10.0.0.0/64", "subnet:fe80::/129",
                "host:fe80::1%eth0", "ext:fe80::1%1", "subnet:fe80::%1/64", "subnet:10.0.0.0/-1"}) {
            assertThrows(IllegalArgumentException.class, () -> TopologyNodeSelector.parse(id));
        }
    }

    @Test
    public void testRejectsInvalidIds() {
        for (String id : new String[]{null, "", "host", "host:", ":192.168.0.5", "host:not-an-address",
                "host:192.168.0.5' OR '1'='1", "subnet:192.168.0.0", "subnet:192.168.0.0/x", "subnet:192.168.0.0/200",
                "tap:not-a-uuid", "asn:google", "cc:USA", "cc:U1", "noasn:", "loc:52.52", "loc:a,b", "loc:500,13",
                "loc:NaN,13", "nothing:1"}) {
            assertThrows(IllegalArgumentException.class, () -> TopologyNodeSelector.parse(id));
        }
    }

    @Test
    public void testSqlNeverContainsTheValue() {
        for (String id : new String[]{"host:192.168.0.5", "subnet:192.168.0.0/24",
                "tap:3b158c02-6d70-4600-8ac5-51365a23ce03", "ext:203.0.113.7", "asn:15169", "noasn:US", "cc:DE",
                "loc:52.52,13.4"}) {
            TopologyNodeSelector selector = TopologyNodeSelector.parse(id);
            for (Side side : Side.values()) {
                String sql = selector.sql(side, "node");
                assertTrue(sql.contains(":node"), sql);
                assertFalse(sql.contains(selector.value()), sql);
            }
        }
    }

    @Test
    public void testSqlMatchesTheRightSide() {
        TopologyNodeSelector host = TopologyNodeSelector.parse("host:192.168.0.5");
        assertEquals(host.sql(Side.SOURCE, "n"), "source_address_is_site_local AND source_address = :n::inet");
        assertEquals(host.sql(Side.DESTINATION, "n"),
                "destination_address_is_site_local AND destination_address = :n::inet");

        TopologyNodeSelector asn = TopologyNodeSelector.parse("asn:15169");
        assertEquals(asn.sql(Side.DESTINATION, "n"),
                "NOT destination_address_is_site_local AND destination_address_geo_asn_number = :n");
    }

    @Test
    public void testUnknownValuesMatchNullsAndBindNothing() {
        TopologyNodeSelector country = TopologyNodeSelector.parse("cc:-");
        assertEquals(country.sql(Side.SOURCE, "n"),
                "NOT source_address_is_site_local AND source_address_geo_country_code IS NULL");
        assertTrue(country.bindings("n").isEmpty());

        TopologyNodeSelector network = TopologyNodeSelector.parse("noasn:-");
        assertEquals(network.sql(Side.SOURCE, "n"), "NOT source_address_is_site_local "
                + "AND source_address_geo_asn_number IS NULL AND source_address_geo_country_code IS NULL");
        assertTrue(network.bindings("n").isEmpty());
    }

    @Test
    public void testBindings() {
        assertEquals(TopologyNodeSelector.parse("asn:15169").bindings("n"), Map.of("n", 15169));
        assertEquals(TopologyNodeSelector.parse("cc:DE").bindings("n"), Map.of("n", "DE"));
        assertEquals(TopologyNodeSelector.parse("host:192.168.0.5").bindings("n"), Map.of("n", "192.168.0.5"));
        assertEquals(TopologyNodeSelector.parse("loc:52.52,-13.4").bindings("n"),
                Map.of("n_lat", 5252L, "n_lon", -1340L));
    }

}
