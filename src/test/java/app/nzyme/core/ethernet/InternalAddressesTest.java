package app.nzyme.core.ethernet;

import com.google.common.net.InetAddresses;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

public class InternalAddressesTest {

    private static final InternalAddresses.Rules CGNAT_IS_INTERNAL = new InternalAddresses.Rules(true, List.of());

    private static boolean siteLocal(InternalAddresses.Rules rules, String address) {
        return rules.isSiteLocal(InetAddresses.forString(address));
    }

    @Test
    public void testCgnatIsExternalByDefault() {
        assertFalse(siteLocal(InternalAddresses.Rules.DEFAULT, "100.64.0.1"));
        assertFalse(siteLocal(InternalAddresses.Rules.DEFAULT, "100.100.100.100"));
        assertTrue(siteLocal(InternalAddresses.Rules.DEFAULT, "10.0.0.1"));
        assertTrue(siteLocal(InternalAddresses.Rules.DEFAULT, "172.16.5.5"));
        assertTrue(siteLocal(InternalAddresses.Rules.DEFAULT, "192.168.1.1"));
        assertFalse(siteLocal(InternalAddresses.Rules.DEFAULT, "8.8.8.8"));
    }

    @Test
    public void testCgnatIsInternalWhereConfigured() {
        assertTrue(siteLocal(CGNAT_IS_INTERNAL, "100.64.0.0"));
        assertTrue(siteLocal(CGNAT_IS_INTERNAL, "100.100.100.100"));
        assertTrue(siteLocal(CGNAT_IS_INTERNAL, "100.127.255.255"));
        assertTrue(siteLocal(CGNAT_IS_INTERNAL, "10.0.0.1"));
    }

    @Test
    public void testTapSettingsAreReadFromTheReportedConfiguration() {
        assertTrue(InternalAddresses.tapSettings("{\"misc\":{\"cgnat_is_internal\":true}}").cgnatIsInternal());
        assertFalse(InternalAddresses.tapSettings("{\"misc\":{\"cgnat_is_internal\":false}}").cgnatIsInternal());

        assertEquals(
                InternalAddresses.tapSettings("{\"misc\":{\"internal_networks\":[\"2001:db8:1200::/48\",\"198.51.100.0/24\"]}}")
                        .internalNetworks(),
                List.of(CIDR.parse("2001:db8:1200::/48"), CIDR.parse("198.51.100.0/24"))
        );
    }

    @Test
    public void testTapWithoutReportedSettingsHasTheDefaults() {
        for (String configuration : new String[]{null, "", "{}", "{\"misc\":{\"panic_log_file_path\":null}}",
                "{\"misc\":{\"cgnat_is_internal\":\"yes\"}}", "{\"misc\":[1,2]}", "not json",
                "{\"misc\":{\"internal_networks\":\"2001:db8::/32\"}}",
                "{\"misc\":{\"internal_networks\":[\"nonsense\", 7, \"2001:db8::1/32\"]}}"}) {
            assertEquals(InternalAddresses.tapSettings(configuration), InternalAddresses.Rules.DEFAULT, configuration);
        }
    }

    @Test
    public void testLinkLocalAndUniqueLocalAddressesAreInternal() {
        assertTrue(siteLocal(InternalAddresses.Rules.DEFAULT, "169.254.10.20"));
        assertTrue(siteLocal(InternalAddresses.Rules.DEFAULT, "fe80::1c2d:3eff:fe4f:5a6b"));
        assertTrue(siteLocal(InternalAddresses.Rules.DEFAULT, "febf::1"));
        assertTrue(siteLocal(InternalAddresses.Rules.DEFAULT, "fec0::1"));
        assertTrue(siteLocal(InternalAddresses.Rules.DEFAULT, "fc00::1"));
        assertTrue(siteLocal(InternalAddresses.Rules.DEFAULT, "fd7a:115c:a1e0::1"));
        assertFalse(siteLocal(InternalAddresses.Rules.DEFAULT, "fe00::1"));
        assertFalse(siteLocal(InternalAddresses.Rules.DEFAULT, "2001:db8::1"));
        assertFalse(siteLocal(InternalAddresses.Rules.DEFAULT, "ff02::1"));
        assertFalse(siteLocal(InternalAddresses.Rules.DEFAULT, "::1"));
        assertFalse(siteLocal(InternalAddresses.Rules.DEFAULT, "64:ff9b::a00:1"));
    }

    @Test
    public void testConfiguredNetworksAreInternal() {
        InternalAddresses.Rules rules = new InternalAddresses.Rules(false,
                List.of(CIDR.parse("2001:db8:aa00::/40"), CIDR.parse("198.51.100.0/24"), CIDR.parse("2001:db8:ff::7/128")));

        assertTrue(siteLocal(rules, "2001:db8:aa12::5"));
        assertFalse(siteLocal(rules, "2001:db8:ab00::5"));
        assertTrue(siteLocal(rules, "198.51.100.9"));
        assertFalse(siteLocal(rules, "198.51.101.9"));
        assertTrue(siteLocal(rules, "2001:db8:ff::7"));
        assertFalse(siteLocal(rules, "2001:db8:ff::8"));
        assertTrue(siteLocal(rules, "10.0.0.1"));
        assertFalse(siteLocal(rules, "100.64.0.1"));
    }

    @Test
    public void testSqlConditionListsTheNetworksOfTheRules() {
        assertEquals(InternalAddresses.Rules.DEFAULT.sqlCondition("a.address"),
                "a.address <<= ANY('{10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,169.254.0.0/16,fc00::/7,fe80::/10,"
                        + "fec0::/10}'::cidr[])");

        String sql = new InternalAddresses.Rules(true, List.of(CIDR.parse("2001:db8:aa00::/40"))).sqlCondition("x");
        assertTrue(sql.contains(",100.64.0.0/10,2001:db8:aa00::/40}"), sql);
    }

    @Test
    public void testNetworksOfUsersAreValidated() {
        assertEquals(
                InternalAddresses.validateNetworks(List.of(" 2001:db8:1200::/48 ", "198.51.100.0/24", "2001:DB8:1200::/48")),
                List.of(CIDR.parse("2001:db8:1200::/48"), CIDR.parse("198.51.100.0/24"))
        );
        assertEquals(InternalAddresses.validateNetworks(List.of()), List.of());

        for (List<String> invalid : List.of(
                List.of("2001:db8::1/32"), List.of("2001:db8::1"), List.of("2000::/3"), List.of("0.0.0.0/0"),
                List.of("11.0.0.0/7"), List.of("nonsense/8"), List.of("10.0.0.0/33"), List.of("::ffff:10.0.0.0/104"),
                List.of("fe80::1%eth0/64"), List.of("10.0.0.0/8'); DROP TABLE taps; --"))) {
            assertThrows(IllegalArgumentException.class, () -> InternalAddresses.validateNetworks(invalid));
        }

        assertThrows(IllegalArgumentException.class, () -> InternalAddresses.validateNetworks(null));
        assertThrows(IllegalArgumentException.class, () -> InternalAddresses.validateNetworks(
                java.util.stream.IntStream.rangeClosed(0, InternalAddresses.MAX_INTERNAL_NETWORKS)
                        .mapToObj(i -> "10." + i + ".0.0/16").toList()));
    }

    @Test
    public void testStoredNetworksRoundTrip() {
        List<CIDR> networks = List.of(CIDR.parse("2001:db8:1200::/48"), CIDR.parse("198.51.100.0/24"));
        String stored = InternalAddresses.serializeNetworks(networks);

        assertEquals(stored, "[\"2001:db8:1200::/48\",\"198.51.100.0/24\"]");
        assertEquals(InternalAddresses.tapSettings("{\"misc\":{\"internal_networks\":" + stored + "}}").internalNetworks(),
                networks);
    }

    @Test
    public void testLearnedNetworksAreInternal() {
        InternalAddresses.Rules rules = new InternalAddresses.Rules(false, List.of(CIDR.parse("198.51.100.0/24")),
                List.of(CIDR.parse("2001:db8:1200:34::/64")));

        assertTrue(siteLocal(rules, "2001:db8:1200:34::77"));
        assertFalse(siteLocal(rules, "2001:db8:1200:35::77"));
        assertTrue(siteLocal(rules, "198.51.100.9"));
        assertFalse(siteLocal(rules, "198.51.101.9"));
        assertTrue(rules.sqlCondition("x").endsWith(",198.51.100.0/24,2001:db8:1200:34::/64}'::cidr[])"));
        assertNotEquals(rules, new InternalAddresses.Rules(false, List.of(CIDR.parse("198.51.100.0/24"))));
    }

    @Test
    public void testOnlyNarrowGlobalPrefixesOfRoutersAreLearned() {
        assertEquals(
                InternalAddresses.learnedNetworks(List.of("2001:db8:1200:34::/64, 2001:db8:1200:35::/64",
                        "2001:db8:1200:34::/64", "2001:db8:aa00::/48")),
                List.of(CIDR.parse("2001:db8:1200:34::/64"), CIDR.parse("2001:db8:1200:35::/64"),
                        CIDR.parse("2001:db8:aa00::/48"))
        );

        assertEquals(InternalAddresses.learnedNetworks(List.of("2000::/3", "2001:db8::/47", "fd00:1::/64",
                "fe80::/64", "10.0.0.0/24", "nonsense", "", "2001:db8::1")), List.of());

        assertEquals(InternalAddresses.learnedNetworks(java.util.stream.IntStream.range(0, 700)
                .mapToObj(i -> "2001:db8:" + Integer.toHexString(i) + "::/64").toList()).size(),
                InternalAddresses.MAX_LEARNED_NETWORKS);
    }

    @Test
    public void testOnlyTheSharedAddressSpaceIsAdded() {
        assertFalse(siteLocal(CGNAT_IS_INTERNAL, "100.63.255.255"));
        assertFalse(siteLocal(CGNAT_IS_INTERNAL, "100.128.0.0"));
        assertFalse(siteLocal(CGNAT_IS_INTERNAL, "101.64.0.1"));
        assertFalse(siteLocal(CGNAT_IS_INTERNAL, "8.8.8.8"));
        assertFalse(siteLocal(CGNAT_IS_INTERNAL, "2606:4700:4700::1111"));
    }

}
