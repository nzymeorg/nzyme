package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.ethernet.CIDR;
import com.google.common.collect.Lists;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

import static app.nzyme.core.ethernet.segmentation.SegmentationTestData.*;
import static org.testng.Assert.*;

public class SegmentationEngineTest {

    private static final Segment OFFICE = internal(1, "Office", "192.168.10.0/24");
    private static final Segment SERVERS = internal(2, "Servers", "10.1.0.0/16");
    private static final MembershipResolver RESOLVER =
            new MembershipResolver(Lists.newArrayList(OFFICE, SERVERS), Collections.emptyMap());

    private static long sessions(List<Flow> flows) {
        return flows.stream().mapToLong(Flow::sessions).sum();
    }

    @Test
    public void alertsNameAnAddressOfAnExternalSourceAndNeverItsKey() {
        assertEquals(SegmentationEngine.exampleSource(violation("10.20.4.17", null, Collections.emptySet())), "10.20.4.17");
        assertEquals(SegmentationEngine.exampleSource(violation(null, 64999L, new LinkedHashSet<>(List.of("198.51.100.7")))),
                "198.51.100.7");
        assertEquals(SegmentationEngine.exampleSource(violation(null, 64999L, Collections.emptySet())), "AS64999");
        assertEquals(SegmentationEngine.exampleSource(violation(null, null, Collections.emptySet())), "Internet");
    }

    private static Findings.Violation violation(String address, Long asn, Set<String> addresses) {
        return new Findings.Violation(null, FindingClass.CROSSED, "asn:64999/NL", address, address != null, asn, null,
                addresses, Collections.emptyList(), Collections.emptySet(), DateTime.now(), DateTime.now());
    }

    @Test
    public void aTapThatSawTheAnswerOverrulesATapThatDidNot() {
        Map<UUID, Outcome> strongest = Map.of(TAP_A, Outcome.CONNECTED, TAP_B, Outcome.NO_REPLY);

        assertTrue(SegmentationEngine.overruled(Outcome.NO_REPLY, TAP_B, strongest));
        assertFalse(SegmentationEngine.overruled(Outcome.CONNECTED, TAP_A, strongest));
    }

    @Test
    public void aTapKeepsItsOwnUnansweredConnectionsNextToAnsweredOnes() {
        Map<UUID, Outcome> strongest = Map.of(TAP_A, Outcome.CONNECTED, TAP_B, Outcome.NO_REPLY);

        assertFalse(SegmentationEngine.overruled(Outcome.NO_REPLY, TAP_A, strongest));
        assertFalse(SegmentationEngine.overruled(Outcome.REJECTED, TAP_A, strongest));
    }

    @Test
    public void tapsThatAgreeOverruleNothingAndBroadcastIsNeverOverruled() {
        assertFalse(SegmentationEngine.overruled(Outcome.NO_REPLY, TAP_B,
                Map.of(TAP_A, Outcome.NO_REPLY, TAP_B, Outcome.NO_REPLY)));
        assertTrue(SegmentationEngine.overruled(Outcome.NO_REPLY, TAP_B,
                Map.of(TAP_A, Outcome.REJECTED, TAP_B, Outcome.NO_REPLY)));
        assertFalse(SegmentationEngine.overruled(Outcome.SENT, TAP_B, Map.of(TAP_A, Outcome.CONNECTED)));
    }

    @Test
    public void answeredConnectionsAreNeverFolded() {
        Endpoint source = host(RESOLVER, "192.168.10.5");
        Endpoint destination = host(RESOLVER, "10.1.0.1");

        List<Flow> flows = Lists.newArrayList();
        for (int port = 1; port <= 500; port++) {
            flows.add(flow(source, destination, port, Outcome.CONNECTED, 1));
        }

        assertEquals(SegmentationEngine.fold(flows).size(), 500);
    }

    @Test
    public void portSweepOfOneHostIsFoldedIntoOneFlowPerOutcome() {
        Endpoint source = host(RESOLVER, "192.168.10.5");
        Endpoint destination = host(RESOLVER, "10.1.0.1");

        List<Flow> flows = Lists.newArrayList();
        for (int port = 1; port <= 1000; port++) {
            flows.add(flow(source, destination, port, port % 10 == 0 ? Outcome.REJECTED : Outcome.NO_REPLY, 2));
        }
        flows.add(flow(source, destination, 443, Outcome.CONNECTED, 7));

        List<Flow> folded = SegmentationEngine.fold(flows);

        assertEquals(folded.size(), 3);
        assertEquals(sessions(folded), 2007);
        for (Flow flow : folded) {
            if (flow.outcome() == Outcome.CONNECTED) {
                assertEquals(flow.port(), 443);
            } else {
                assertEquals(flow.port(), PolicyMatcher.FOLDED_PORT);
                assertEquals(flow.destinationKey(), "ip:10.1.0.1");
            }
        }
    }

    @Test
    public void fewPortsOfOneHostAreKept() {
        Endpoint source = host(RESOLVER, "192.168.10.5");
        Endpoint destination = host(RESOLVER, "10.1.0.1");

        List<Flow> flows = Lists.newArrayList();
        for (int port = 1; port <= SegmentationEngine.MAX_PORTS_PER_PAIR; port++) {
            flows.add(flow(source, destination, port, Outcome.NO_REPLY, 1));
        }

        List<Flow> folded = SegmentationEngine.fold(flows);
        assertEquals(folded.size(), SegmentationEngine.MAX_PORTS_PER_PAIR);
        assertTrue(folded.stream().noneMatch(f -> f.port() == PolicyMatcher.FOLDED_PORT));
    }

    @Test
    public void hostSweepIsFoldedPerSegmentBeyondTheLimit() {
        Endpoint source = host(RESOLVER, "192.168.10.5");

        List<Flow> flows = Lists.newArrayList();
        int hosts = 0;
        for (int third = 0; third < 8; third++) {
            for (int fourth = 1; fourth <= 250; fourth++) {
                flows.add(flow(source, host(RESOLVER, "10.1." + third + "." + fourth), 22, Outcome.NO_REPLY, 1));
                hosts++;
            }
        }

        List<Flow> folded = SegmentationEngine.fold(flows);

        assertEquals(folded.size(), SegmentationEngine.MAX_ATTEMPT_ROWS_PER_SOURCE + 1);
        assertEquals(sessions(folded), hosts);

        Flow rest = folded.stream().filter(f -> !f.destination().hasAddress()).findFirst().orElseThrow();
        assertEquals(rest.destinationKey(), Flows.foldedKey(SERVERS));
        assertEquals(rest.destination().segment(), SERVERS);
        assertEquals(rest.sessions(), hosts - SegmentationEngine.MAX_ATTEMPT_ROWS_PER_SOURCE);
        assertEquals(rest.port(), PolicyMatcher.FOLDED_PORT);
    }

    @Test
    public void sweepsOfDifferentSourcesAreFoldedSeparately() {
        List<Flow> flows = Lists.newArrayList();
        for (String source : List.of("192.168.10.5", "192.168.10.6")) {
            for (int port = 1; port <= 100; port++) {
                flows.add(flow(host(RESOLVER, source), host(RESOLVER, "10.1.0.1"), port, Outcome.NO_REPLY, 1));
            }
        }

        List<Flow> folded = SegmentationEngine.fold(flows);
        assertEquals(folded.size(), 2);
        assertEquals(sessions(folded), 200);
    }

    @Test
    public void broadcastAndMulticastDestinationsAreFoldedLikeASweep() {
        Endpoint source = host(RESOLVER, "192.168.10.5");

        List<Flow> ports = Lists.newArrayList();
        for (int port = 1; port <= 100; port++) {
            ports.add(flow(source, Endpoint.broadcast(address("255.255.255.255")), port, Outcome.SENT, 3));
        }

        List<Flow> folded = SegmentationEngine.fold(ports);
        assertEquals(folded.size(), 1);
        assertEquals(folded.get(0).destinationKey(), "bc:255.255.255.255");
        assertEquals(folded.get(0).port(), PolicyMatcher.FOLDED_PORT);
        assertEquals(folded.get(0).outcome(), Outcome.SENT);
        assertEquals(sessions(folded), 300);

        int addresses = SegmentationEngine.MAX_ATTEMPT_ROWS_PER_SOURCE + 50;
        List<Flow> flows = Lists.newArrayList();
        for (int i = 0; i < addresses; i++) {
            flows.add(flow(source, Endpoint.broadcast(address("239.1." + (i / 250) + "." + (i % 250 + 1))), 5000, Outcome.SENT, 1));
        }

        folded = SegmentationEngine.fold(flows);
        assertEquals(folded.size(), SegmentationEngine.MAX_ATTEMPT_ROWS_PER_SOURCE + 1);
        assertEquals(sessions(folded), addresses);

        Flow rest = folded.stream().filter(f -> !f.destination().hasAddress()).findFirst().orElseThrow();
        assertEquals(rest.destinationKey(), "bc:fold");
        assertTrue(rest.destination().broadcast());
        assertEquals(rest.destinationGroup(), Flow.GROUP_BROADCAST);
        assertEquals(rest.sessions(), 50);
    }

    @Test
    public void broadcastAndMulticastDestinationsAreAGroupOfTheirOwn() {
        Endpoint mdns = Endpoint.broadcast(address("224.0.0.251"));

        assertEquals(Flows.key(mdns), "bc:224.0.0.251");
        assertEquals(Flow.group(mdns), Flow.GROUP_BROADCAST);

        Endpoint subnet = Endpoint.broadcast(address("192.168.10.255"));
        assertEquals(Flows.key(subnet), "bc:192.168.10.255");
        assertEquals(Flow.group(subnet), Flow.GROUP_BROADCAST);
        assertEquals(Flow.group(host(RESOLVER, "192.168.10.255")), OFFICE.id());

        assertEquals(Outcome.SENT.findingClass(), FindingClass.CROSSED);
    }

    @Test
    public void rollupKeysOfInternalAndExternalSides() {
        assertEquals(Flows.key(host(RESOLVER, "192.168.10.5")), "ip:192.168.10.5");

        Segment partner = segment(9, SegmentKind.EXTERNAL, "Partner", null, "203.0.113.0/24");
        MembershipResolver resolver = new MembershipResolver(Lists.newArrayList(partner), Collections.emptyMap());

        assertEquals(Flows.key(resolver.resolve(TAP_A, address("203.0.113.7"), false, 64500L, "us")), "seg:9/asn:64500/US");
        assertEquals(Flows.key(resolver.resolve(TAP_A, address("198.51.100.7"), false, 64500L, "US")), "asn:64500/US");
        assertEquals(Flows.key(resolver.resolve(TAP_A, address("198.51.100.7"), false, null, "US")), "noasn/US");
        assertEquals(Flows.key(resolver.resolve(TAP_A, address("198.51.100.7"), false, null, null)), "noasn/-");
    }

    @Test
    public void groupsOfTheCrossingRollup() {
        assertEquals(Flow.group(host(RESOLVER, "192.168.10.5")), OFFICE.id());
        assertEquals(Flow.group(host(RESOLVER, "172.16.0.5")), Flow.GROUP_UNASSIGNED);
        assertEquals(Flow.group(RESOLVER.resolve(TAP_A, address("198.51.100.7"), false, null, null)), Flow.GROUP_INTERNET);
    }

    @Test
    public void importedNamesAreCutToTheLimitAndKeptUnique() {
        java.util.Set<String> taken = com.google.common.collect.Sets.newHashSet("servers");

        assertEquals(Segments.uniqueName("Guest", taken), "Guest");
        assertEquals(Segments.uniqueName("Servers", taken), "Servers-2");
        assertEquals(Segments.uniqueName("servers", taken), "servers-3");
        assertEquals(Segments.uniqueName("192.168.100.25", taken), "192.168.100.");
        assertEquals(Segments.uniqueName("192.168.100.25", taken), "192.168.10-2");
        assertTrue(Segments.uniqueName("A name that is far too long", taken).length() <= Segments.NAME_MAX_LENGTH);
    }

    @Test
    public void networksWithoutNameAreNamedByTheirPrefix() {
        assertEquals(Segments.defaultName(CIDR.parse("10.1.0.0/16")), "10.1");
        assertEquals(Segments.defaultName(CIDR.parse("192.168.100.0/24")), "192.168.100");
        assertEquals(Segments.defaultName(CIDR.parse("192.168.0.128/25")), "192.168.0.128");
    }

    @Test
    public void networkOfDhcpServerAndSubnetMask() {
        assertEquals(Segments.networkOf("192.168.8.1", "255.255.255.0"), CIDR.parse("192.168.8.0/24"));
        assertEquals(Segments.networkOf("10.20.30.40", "255.255.252.0"), CIDR.parse("10.20.28.0/22"));
        assertNull(Segments.networkOf("192.168.8.1", "255.0.255.0"));
        assertNull(Segments.networkOf("192.168.8.1", "0.0.0.0"));
        assertNull(Segments.networkOf("192.168.8.1", "not a mask"));
        assertNull(Segments.networkOf(null, "255.255.255.0"));
    }

}
