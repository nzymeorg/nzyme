package app.nzyme.core.ethernet.segmentation;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static app.nzyme.core.ethernet.segmentation.PolicyMatcher.Verdict.*;
import static app.nzyme.core.ethernet.segmentation.PolicyRule.SelectorType.*;
import static app.nzyme.core.ethernet.segmentation.SegmentationTestData.*;
import static org.testng.Assert.assertEquals;

public class PolicyMatcherTest {

    private static final Segment GUEST = internal(1, "Guest", "192.168.50.0/24");
    private static final Segment OFFICE = internal(2, "Office", "192.168.10.0/24");
    private static final Segment SERVERS = internal(3, "Servers", "10.1.0.0/16");
    private static final Segment PARTNER = segment(4, SegmentKind.EXTERNAL, "Partner", null, "203.0.113.0/24");

    private static final UUID DB_SERVERS = UUID.randomUUID();
    private static final UUID APP_SERVERS = UUID.randomUUID();
    private static final UUID DB_ASSET = UUID.randomUUID();

    private static final MembershipResolver RESOLVER;

    static {
        Map<InetAddress, MembershipResolver.TaggedAsset> assets = Maps.newHashMap();
        assets.put(address("10.1.5.20"), new MembershipResolver.TaggedAsset(DB_ASSET, Set.of(DB_SERVERS)));
        assets.put(address("10.1.5.21"), new MembershipResolver.TaggedAsset(UUID.randomUUID(), Set.of(DB_SERVERS)));
        assets.put(address("10.1.7.5"), new MembershipResolver.TaggedAsset(UUID.randomUUID(), Set.of(APP_SERVERS)));

        RESOLVER = new MembershipResolver(Lists.newArrayList(GUEST, OFFICE, SERVERS, PARTNER), assets);
    }

    private static Endpoint host(String address) {
        return SegmentationTestData.host(RESOLVER, address);
    }

    private static Endpoint external(String address, Long asn, String countryCode) {
        return RESOLVER.resolve(TAP_A, address(address), false, asn, countryCode);
    }

    @Test
    public void onlyListedSourcesAndServicesReachTaggedServers() {
        PolicyMatcher matcher = matcher(
                rule(none(), List.of(selector(TAG, DB_SERVERS.toString())), none()),
                rule(List.of(selector(TAG, APP_SERVERS.toString())), none(), List.of(tcp(5432))),
                rule(List.of(selector(ADDRESS, "192.168.10.7")), none(), List.of(tcp(22)))
        );

        assertEquals(matcher.evaluate(host("10.1.7.5"), host("10.1.5.20"), "TCP", 5432, ""), ALLOWED);
        assertEquals(matcher.evaluate(host("192.168.10.7"), host("10.1.5.21"), "TCP", 22, "SSH"), ALLOWED);

        assertEquals(matcher.evaluate(host("10.1.7.5"), host("10.1.5.20"), "TCP", 22, "SSH"), VIOLATION);
        assertEquals(matcher.evaluate(host("192.168.10.8"), host("10.1.5.20"), "TCP", 5432, ""), VIOLATION);
        assertEquals(matcher.evaluate(host("192.168.50.9"), host("10.1.5.20"), "TCP", 5432, ""), VIOLATION);

        assertEquals(matcher.evaluate(host("192.168.50.9"), host("10.1.5.99"), "TCP", 5432, ""), OUT_OF_SCOPE);
    }

    @Test
    public void aCrossingThatSaysEverythingAllowsAllOfTheScopeAndNothingElse() {
        PolicyMatcher matcher = matcher(
                rule(List.of(segmentSelector(GUEST)), List.of(selector(EXTERNAL, null)), none()),
                rule(List.of(any()), List.of(any()), none())
        );

        assertEquals(matcher.evaluate(host("192.168.50.9"), external("198.51.100.7", 64500L, "DE"), "TCP", 443, "TLS"), ALLOWED);
        assertEquals(matcher.evaluate(host("192.168.50.9"), external("203.0.113.5", null, null), "UDP", 53, "DNS"), ALLOWED);

        assertEquals(matcher.evaluate(host("192.168.50.9"), host("10.1.5.20"), "TCP", 5432, ""), OUT_OF_SCOPE);
        assertEquals(matcher.evaluate(host("192.168.10.7"), external("198.51.100.7", 64500L, "DE"), "TCP", 443, "TLS"), OUT_OF_SCOPE);
    }

    @Test
    public void clientIsolationWithGatewayAsOnlyAllowedDestination() {
        PolicyMatcher matcher = matcher(
                rule(List.of(segmentSelector(GUEST)), List.of(segmentSelector(GUEST)), none()),
                rule(none(), List.of(selector(ADDRESS, "192.168.50.1")), none())
        );

        assertEquals(matcher.evaluate(host("192.168.50.20"), host("192.168.50.1"), "UDP", 53, "DNS"), ALLOWED);
        assertEquals(matcher.evaluate(host("192.168.50.20"), host("192.168.50.21"), "TCP", 445, ""), VIOLATION);
        assertEquals(matcher.evaluate(host("192.168.50.20"), host("192.168.10.5"), "TCP", 445, ""), OUT_OF_SCOPE);
    }

    @Test
    public void neverAcceptsProtocolOnAnyPort() {
        PolicyMatcher matcher = matcher(
                rule(none(), List.of(segmentSelector(SERVERS)), List.of(new PolicyRule.Service(null, null, null, "SSH")))
        );

        assertEquals(matcher.evaluate(host("192.168.10.5"), host("10.1.9.9"), "TCP", 22, "SSH"), VIOLATION);
        assertEquals(matcher.evaluate(host("192.168.10.5"), host("10.1.9.9"), "TCP", 2222, "SSH"), VIOLATION);
        assertEquals(matcher.evaluate(host("192.168.10.5"), host("10.1.9.9"), "TCP", 443, "HTTP/TLS"), OUT_OF_SCOPE);
        assertEquals(matcher.evaluate(host("192.168.10.5"), host("10.1.9.9"), "TCP", 22, ""), OUT_OF_SCOPE);
    }

    @Test
    public void onlyAcceptsOneServiceOnOneAddress() {
        PolicyMatcher matcher = matcher(
                rule(none(), List.of(segmentSelector(SERVERS)), none()),
                rule(none(), List.of(selector(ADDRESS, "10.1.9.9")), List.of(new PolicyRule.Service("TCP", 22, null, "SSH")))
        );

        assertEquals(matcher.evaluate(host("192.168.10.5"), host("10.1.9.9"), "TCP", 22, "SSH"), ALLOWED);
        assertEquals(matcher.evaluate(host("192.168.10.5"), host("10.1.9.9"), "TCP", 22, "TLS"), VIOLATION);
        assertEquals(matcher.evaluate(host("192.168.10.5"), host("10.1.9.10"), "TCP", 22, "SSH"), VIOLATION);
        assertEquals(matcher.evaluate(host("192.168.10.5"), host("10.1.9.9"), "UDP", 22, ""), VIOLATION);
    }

    @Test
    public void portRangesAndLayerFourType() {
        PolicyMatcher matcher = matcher(
                rule(List.of(segmentSelector(OFFICE)), List.of(segmentSelector(SERVERS)), none()),
                rule(none(), none(), List.of(new PolicyRule.Service("TCP", 8000, 8099, null), new PolicyRule.Service("UDP", null, null, null)))
        );

        assertEquals(matcher.evaluate(host("192.168.10.5"), host("10.1.9.9"), "TCP", 8000, ""), ALLOWED);
        assertEquals(matcher.evaluate(host("192.168.10.5"), host("10.1.9.9"), "TCP", 8099, ""), ALLOWED);
        assertEquals(matcher.evaluate(host("192.168.10.5"), host("10.1.9.9"), "TCP", 8100, ""), VIOLATION);
        assertEquals(matcher.evaluate(host("192.168.10.5"), host("10.1.9.9"), "UDP", 9999, ""), ALLOWED);
    }

    @Test
    public void assetAndUnassignedSelectors() {
        PolicyMatcher matcher = matcher(
                rule(List.of(selector(UNASSIGNED, null)), List.of(selector(ASSET, DB_ASSET.toString())), none())
        );

        assertEquals(matcher.evaluate(host("172.16.4.4"), host("10.1.5.20"), "TCP", 5432, ""), VIOLATION);
        assertEquals(matcher.evaluate(host("192.168.10.5"), host("10.1.5.20"), "TCP", 5432, ""), OUT_OF_SCOPE);
        assertEquals(matcher.evaluate(host("172.16.4.4"), host("10.1.5.21"), "TCP", 5432, ""), OUT_OF_SCOPE);
    }

    @Test
    public void externalSelectors() {
        PolicyMatcher matcher = matcher(
                rule(List.of(segmentSelector(SERVERS)), List.of(selector(EXTERNAL, null)), none()),
                rule(none(), List.of(selector(ASN, "64500"), segmentSelector(PARTNER)), List.of(tcp(443)))
        );

        assertEquals(matcher.evaluate(host("10.1.9.9"), external("198.51.100.7", 64500L, "US"), "TCP", 443, "TLS"), ALLOWED);
        assertEquals(matcher.evaluate(host("10.1.9.9"), external("203.0.113.9", 64999L, "DE"), "TCP", 443, "TLS"), ALLOWED);
        assertEquals(matcher.evaluate(host("10.1.9.9"), external("198.51.100.7", 64501L, "US"), "TCP", 443, "TLS"), VIOLATION);
        assertEquals(matcher.evaluate(host("10.1.9.9"), external("198.51.100.7", 64500L, "US"), "TCP", 22, "SSH"), VIOLATION);
        assertEquals(matcher.evaluate(host("10.1.9.9"), host("192.168.10.5"), "TCP", 443, "TLS"), OUT_OF_SCOPE);
    }

    @Test
    public void countryAndExternalAddressSelectors() {
        PolicyMatcher matcher = matcher(
                rule(List.of(selector(COUNTRY, "kp"), selector(ADDRESS, "198.51.100.0/24")), List.of(selector(INTERNAL, null)), none())
        );

        assertEquals(matcher.evaluate(external("192.0.2.1", 1L, "KP"), host("10.1.9.9"), "TCP", 22, ""), VIOLATION);
        assertEquals(matcher.evaluate(external("198.51.100.200", 2L, "US"), host("10.1.9.9"), "TCP", 22, ""), VIOLATION);
        assertEquals(matcher.evaluate(external("192.0.2.1", 1L, "US"), host("10.1.9.9"), "TCP", 22, ""), OUT_OF_SCOPE);
    }

    @Test
    public void whatAStoredFlowDoesNotCarryIsNotGuessed() {
        Endpoint stored = RESOLVER.stored(false, null, 64500L, "US");

        PolicyMatcher scopeByAddress = matcher(
                rule(List.of(selector(ADDRESS, "198.51.100.0/24")), List.of(segmentSelector(SERVERS)), none())
        );
        assertEquals(scopeByAddress.evaluate(stored, host("10.1.9.9"), "TCP", 22, ""), INDETERMINATE);

        PolicyMatcher allowedByAddress = matcher(
                rule(List.of(selector(EXTERNAL, null)), List.of(segmentSelector(SERVERS)), none()),
                rule(List.of(selector(ADDRESS, "198.51.100.0/24")), none(), none())
        );
        assertEquals(allowedByAddress.evaluate(stored, host("10.1.9.9"), "TCP", 22, ""), INDETERMINATE);

        PolicyMatcher allowedBySystem = matcher(
                rule(List.of(selector(EXTERNAL, null)), List.of(segmentSelector(SERVERS)), none()),
                rule(List.of(selector(ASN, "64500")), none(), none())
        );
        assertEquals(allowedBySystem.evaluate(stored, host("10.1.9.9"), "TCP", 22, ""), ALLOWED);
    }

    @Test
    public void foldedPortsAreOnlyDecidedWhereThePortDoesNotMatter() {
        PolicyMatcher byPort = matcher(
                rule(List.of(segmentSelector(GUEST)), List.of(segmentSelector(SERVERS)), none()),
                rule(none(), none(), List.of(tcp(443)))
        );
        assertEquals(byPort.evaluate(host("192.168.50.9"), host("10.1.9.9"), "TCP", PolicyMatcher.FOLDED_PORT, ""), INDETERMINATE);

        PolicyMatcher nothingAllowed = matcher(rule(List.of(segmentSelector(GUEST)), List.of(segmentSelector(SERVERS)), none()));
        assertEquals(nothingAllowed.evaluate(host("192.168.50.9"), host("10.1.9.9"), "TCP", PolicyMatcher.FOLDED_PORT, ""), VIOLATION);
    }

    @Test
    public void emptySidesAndAnyMatchEverything() {
        PolicyMatcher matcher = matcher(rule(List.of(any()), List.of(segmentSelector(SERVERS)), none()), rule(List.of(segmentSelector(OFFICE)), none(), none()));

        assertEquals(matcher.evaluate(host("192.168.10.5"), host("10.1.9.9"), "TCP", 1, ""), ALLOWED);
        assertEquals(matcher.evaluate(external("192.0.2.1", null, null), host("10.1.9.9"), "TCP", 1, ""), VIOLATION);
    }

    @Test
    public void broadcastAndMulticastAreOnlyInAScopeThatNamesThem() {
        Endpoint guest = host("192.168.50.9");
        Endpoint mdns = Endpoint.broadcast(address("224.0.0.251"));
        Endpoint subnet = Endpoint.broadcast(address("192.168.50.255"));
        Endpoint limited = Endpoint.broadcast(address("255.255.255.255"));

        List<List<PolicyRule.Selector>> hosts = List.of(
                none(),
                List.of(any()),
                List.of(selector(INTERNAL, null)),
                List.of(selector(EXTERNAL, null)),
                List.of(selector(UNASSIGNED, null)),
                List.of(segmentSelector(GUEST)),
                List.of(selector(ADDRESS, "192.168.50.0/24")),
                List.of(selector(ADDRESS, "0.0.0.0/0"))
        );
        for (List<PolicyRule.Selector> destinations : hosts) {
            PolicyMatcher matcher = matcher(rule(List.of(segmentSelector(GUEST)), destinations, none()));
            for (Endpoint destination : List.of(mdns, subnet, limited)) {
                assertEquals(matcher.evaluate(guest, destination, "UDP", 5353, ""), OUT_OF_SCOPE);
            }
        }

        PolicyMatcher named = matcher(rule(List.of(segmentSelector(GUEST)), List.of(selector(BROADCAST, null)), none()));
        for (Endpoint destination : List.of(mdns, subnet, limited)) {
            assertEquals(named.evaluate(guest, destination, "UDP", 5353, ""), VIOLATION);
        }
        assertEquals(named.evaluate(guest, host("192.168.50.10"), "UDP", 5353, ""), OUT_OF_SCOPE);
        assertEquals(named.evaluate(guest, external("192.0.2.1", null, null), "UDP", 5353, ""), OUT_OF_SCOPE);

        PolicyMatcher multicast = matcher(rule(none(), List.of(selector(ADDRESS, "224.0.0.0/4")), none()));
        assertEquals(multicast.evaluate(guest, mdns, "UDP", 5353, ""), VIOLATION);
        assertEquals(multicast.evaluate(guest, subnet, "UDP", 5353, ""), OUT_OF_SCOPE);
        assertEquals(multicast.evaluate(guest, limited, "UDP", 5353, ""), OUT_OF_SCOPE);

        PolicyMatcher broadcast = matcher(rule(none(), List.of(selector(ADDRESS, "255.255.255.255")), none()));
        assertEquals(broadcast.evaluate(guest, limited, "UDP", 67, ""), VIOLATION);
        assertEquals(broadcast.evaluate(guest, mdns, "UDP", 67, ""), OUT_OF_SCOPE);

        assertEquals(multicast.evaluate(guest, host("192.168.50.10"), "UDP", 5353, ""), OUT_OF_SCOPE);
        assertEquals(multicast.evaluate(guest, RESOLVER.stored(false, null, 64500L, "US"), "UDP", 5353, ""), OUT_OF_SCOPE);

        PolicyMatcher wide = matcher(rule(none(), List.of(selector(ADDRESS, "192.0.0.0/2")), none()));
        assertEquals(wide.evaluate(guest, mdns, "UDP", 5353, ""), OUT_OF_SCOPE);
        assertEquals(wide.evaluate(guest, external("203.0.113.9", null, null), "UDP", 5353, ""), VIOLATION);
    }

    @Test
    public void allowedCrossingsCoverBroadcastInsideOfAScopeThatNamesIt() {
        Endpoint server = host("10.1.5.20");
        Endpoint mdns = Endpoint.broadcast(address("224.0.0.251"));
        Endpoint llmnr = Endpoint.broadcast(address("224.0.0.252"));
        Endpoint ssdp = Endpoint.broadcast(address("239.255.255.250"));
        Endpoint subnet = Endpoint.broadcast(address("10.1.255.255"));

        PolicyMatcher matcher = matcher(
                rule(List.of(segmentSelector(SERVERS)), List.of(selector(BROADCAST, null)), none()),
                rule(none(), none(), List.of(udp(5353))),
                rule(none(), List.of(selector(ADDRESS, "239.255.255.250")), none()),
                rule(none(), List.of(selector(ADDRESS, "10.1.0.0/16")), List.of(udp(137)))
        );

        assertEquals(matcher.evaluate(server, mdns, "UDP", 5353, ""), ALLOWED);
        assertEquals(matcher.evaluate(server, ssdp, "UDP", 1900, ""), ALLOWED);
        assertEquals(matcher.evaluate(server, subnet, "UDP", 137, ""), ALLOWED);

        assertEquals(matcher.evaluate(server, subnet, "UDP", 138, ""), VIOLATION);
        assertEquals(matcher.evaluate(server, llmnr, "UDP", 5355, ""), VIOLATION);

        PolicyMatcher toHosts = matcher(
                rule(List.of(segmentSelector(SERVERS)), List.of(selector(BROADCAST, null)), none()),
                rule(none(), List.of(segmentSelector(SERVERS)), none()),
                rule(none(), List.of(selector(INTERNAL, null)), none()),
                rule(none(), List.of(selector(EXTERNAL, null)), none()),
                rule(none(), List.of(selector(TAG, DB_SERVERS.toString())), none())
        );
        assertEquals(toHosts.evaluate(server, subnet, "UDP", 137, ""), VIOLATION);
        assertEquals(toHosts.evaluate(server, mdns, "UDP", 5353, ""), VIOLATION);

        Endpoint folded = Endpoint.broadcast(null);
        assertEquals(matcher.evaluate(server, folded, "UDP", 5353, ""), ALLOWED);
        assertEquals(matcher.evaluate(server, folded, "UDP", 1900, ""), INDETERMINATE);
        assertEquals(toHosts.evaluate(server, folded, "UDP", 1900, ""), VIOLATION);
    }

    @Test
    public void onlyProtocolsThatTapsDetectCanBeNamed() {
        for (String protocol : List.of("DNS", "DHCP4", "NTP", "TLS", "SSH", "HTTP", "RTSP", "SOCKS", "STUN", "TURN",
                "RTP", "DTLS", "WebRTC", "WireGuard", "OpenVPN", "DERP")) {
            assertEquals(PolicyRule.isKnownProtocol(protocol), true, protocol);
        }
        assertEquals(PolicyRule.PROTOCOLS.size(), 16);

        for (String protocol : List.of("SMTP", "IMAP", "POP3", "FTP", "Telnet")) {
            assertEquals(PolicyRule.isKnownProtocol(protocol), false, protocol);
        }

        assertEquals(PolicyRule.isKnownProtocol("Unencrypted"), false);
        assertEquals(PolicyRule.isKnownProtocol(""), false);
        assertEquals(PolicyRule.isKnownProtocol(null), false);

        assertEquals(PolicyRule.isKnownProtocol("ssh"), true);
        assertEquals(PolicyRule.isKnownProtocol(" wireguard "), true);
    }

    @Test
    public void protocolsOfAFlowAreMatchedOneByOne() {
        assertEquals(PolicyMatcher.speaks("HTTP/TLS", "tls"), true);
        assertEquals(PolicyMatcher.speaks("HTTP/TLS", "HTTP"), true);
        assertEquals(PolicyMatcher.speaks("HTTP/TLS", "TL"), false);
        assertEquals(PolicyMatcher.speaks("", "TLS"), false);
        assertEquals(PolicyMatcher.speaks(null, "TLS"), false);
    }

}
