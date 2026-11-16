package app.nzyme.core.ethernet.segmentation;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static app.nzyme.core.ethernet.segmentation.SegmentationTestData.*;
import static org.testng.Assert.*;

public class MembershipResolverTest {

    private static MembershipResolver resolver(Segment... segments) {
        return new MembershipResolver(Lists.newArrayList(segments), Collections.emptyMap());
    }

    @Test
    public void mostSpecificNetworkWins() {
        Segment servers = internal(1, "Servers", "10.1.0.0/16");
        Segment databases = internal(2, "Databases", "10.1.5.0/24");
        Segment vault = internal(3, "Vault", "10.1.5.9/32");
        MembershipResolver resolver = resolver(servers, databases, vault);

        assertEquals(resolver.segmentOf(TAP_A, address("10.1.9.1")), servers);
        assertEquals(resolver.segmentOf(TAP_A, address("10.1.5.20")), databases);
        assertEquals(resolver.segmentOf(TAP_A, address("10.1.5.9")), vault);
        assertNull(resolver.segmentOf(TAP_A, address("10.2.0.1")));
    }

    @Test
    public void networksThatAreNotOnByteBoundariesMatch() {
        Segment lower = internal(1, "Lower", "192.168.0.0/25");
        Segment upper = internal(2, "Upper", "192.168.0.128/25");
        Segment wide = internal(3, "Wide", "172.16.0.0/12");
        MembershipResolver resolver = resolver(lower, upper, wide);

        assertEquals(resolver.segmentOf(TAP_A, address("192.168.0.127")), lower);
        assertEquals(resolver.segmentOf(TAP_A, address("192.168.0.128")), upper);
        assertEquals(resolver.segmentOf(TAP_A, address("172.31.255.254")), wide);
        assertNull(resolver.segmentOf(TAP_A, address("172.32.0.1")));
    }

    @Test
    public void sameNetworkAtTwoSitesIsToldApartByTap() {
        Segment berlin = segment(1, SegmentKind.INTERNAL, "Berlin", List.of(TAP_A), "192.168.1.0/24");
        Segment austin = segment(2, SegmentKind.INTERNAL, "Austin", List.of(TAP_B), "192.168.1.0/24");
        MembershipResolver resolver = resolver(berlin, austin);

        assertEquals(resolver.segmentOf(TAP_A, address("192.168.1.10")), berlin);
        assertEquals(resolver.segmentOf(TAP_B, address("192.168.1.10")), austin);
        assertNull(resolver.segmentOf(UUID.randomUUID(), address("192.168.1.10")));
    }

    @Test
    public void tapLimitedSegmentWinsOverUnlimitedOneOfTheSameNetwork() {
        Segment everywhere = internal(1, "Office", "192.168.1.0/24");
        Segment lab = segment(2, SegmentKind.INTERNAL, "Lab", List.of(TAP_B), "192.168.1.0/24");
        MembershipResolver resolver = resolver(everywhere, lab);

        assertEquals(resolver.segmentOf(TAP_A, address("192.168.1.10")), everywhere);
        assertEquals(resolver.segmentOf(TAP_B, address("192.168.1.10")), lab);
    }

    @Test
    public void lessSpecificSegmentAppliesWhereTheMoreSpecificOneIsLimitedToAnotherTap() {
        Segment wide = internal(1, "Wide", "10.0.0.0/8");
        Segment narrow = segment(2, SegmentKind.INTERNAL, "Narrow", List.of(TAP_B), "10.1.0.0/16");
        MembershipResolver resolver = resolver(wide, narrow);

        assertEquals(resolver.segmentOf(TAP_A, address("10.1.2.3")), wide);
        assertEquals(resolver.segmentOf(TAP_B, address("10.1.2.3")), narrow);
    }

    @Test
    public void addressWithoutSegmentIsInternalIfRecordedAsSiteLocal() {
        MembershipResolver resolver = resolver();

        Endpoint internal = resolver.resolve(TAP_A, address("192.168.8.5"), true, null, null);
        assertTrue(internal.internal());
        assertNull(internal.segment());

        Endpoint external = resolver.resolve(TAP_A, address("203.0.113.5"), false, 64500L, "DE");
        assertFalse(external.internal());
        assertNull(external.segment());
        assertEquals(external.asn(), Long.valueOf(64500));
        assertEquals(external.countryCode(), "DE");
    }

    @Test
    public void internalSegmentMakesPublicRangePartOfTheNetwork() {
        Segment overlay = internal(1, "Overlay", "100.64.0.0/10");
        MembershipResolver resolver = resolver(overlay);

        Endpoint endpoint = resolver.resolve(TAP_A, address("100.100.1.2"), false, 64500L, "US");
        assertTrue(endpoint.internal());
        assertEquals(endpoint.segment(), overlay);
        assertNull(endpoint.asn());
        assertNull(endpoint.countryCode());
    }

    @Test
    public void externalNetworkByAddressRangeAndByAutonomousSystem() {
        Segment partner = segment(1, SegmentKind.EXTERNAL, "Partner", null, "203.0.113.0/24");
        Segment cloud = segment(2, SegmentKind.EXTERNAL, "Cloud", null, 64500L);
        MembershipResolver resolver = resolver(partner, cloud);

        Endpoint inRange = resolver.resolve(TAP_A, address("203.0.113.7"), false, 64500L, "US");
        assertFalse(inRange.internal());
        assertEquals(inRange.segment(), partner);

        Endpoint inSystem = resolver.resolve(TAP_A, address("198.51.100.7"), false, 64500L, "US");
        assertEquals(inSystem.segment(), cloud);

        Endpoint neither = resolver.resolve(TAP_A, address("198.51.100.7"), false, 64501L, "US");
        assertNull(neither.segment());

        Endpoint internal = resolver.resolve(TAP_A, address("10.0.0.7"), true, 64500L, null);
        assertTrue(internal.internal());
        assertNull(internal.segment());
    }

    @Test
    public void externalSegmentOverPrivateRangeIsExternal() {
        Segment partner = segment(1, SegmentKind.EXTERNAL, "PartnerVPN", null, "10.99.0.0/16");
        MembershipResolver resolver = resolver(partner);

        Endpoint endpoint = resolver.resolve(TAP_A, address("10.99.1.1"), true, null, null);
        assertFalse(endpoint.internal());
        assertEquals(endpoint.segment(), partner);
    }

    @Test
    public void tagsAndAssetComeFromTheAddress() {
        UUID asset = UUID.randomUUID();
        UUID tag = UUID.randomUUID();
        Map<InetAddress, MembershipResolver.TaggedAsset> assets = Maps.newHashMap();
        assets.put(address("10.1.5.20"), new MembershipResolver.TaggedAsset(asset, Set.of(tag)));

        MembershipResolver resolver = new MembershipResolver(Lists.newArrayList(internal(1, "Servers", "10.1.0.0/16")), assets);

        Endpoint tagged = resolver.resolve(TAP_A, address("10.1.5.20"), true, null, null);
        assertEquals(tagged.asset(), asset);
        assertTrue(tagged.tags().contains(tag));

        Endpoint untagged = resolver.resolve(TAP_A, address("10.1.5.21"), true, null, null);
        assertNull(untagged.asset());
        assertTrue(untagged.tags().isEmpty());

        assertEquals(resolver.addressesOfTag(tag), List.of(address("10.1.5.20")));
        assertEquals(resolver.addressesOfAsset(asset), List.of(address("10.1.5.20")));
    }

    @Test
    public void ipv6SegmentsMatch() {
        Segment v6 = internal(1, "V6", "fd00:1::/64");
        MembershipResolver resolver = resolver(v6, internal(2, "V4", "10.0.0.0/8"));

        assertEquals(resolver.segmentOf(TAP_A, address("fd00:1::25")), v6);
        assertNull(resolver.segmentOf(TAP_A, address("fd00:2::25")));
    }

    @Test
    public void maskKeepsOnlyThePrefix() {
        assertEquals(MembershipResolver.mask(address("192.168.37.201").getAddress(), 24), address("192.168.37.0").getAddress());
        assertEquals(MembershipResolver.mask(address("192.168.37.201").getAddress(), 25), address("192.168.37.128").getAddress());
        assertEquals(MembershipResolver.mask(address("192.168.37.201").getAddress(), 32), address("192.168.37.201").getAddress());
        assertEquals(MembershipResolver.mask(address("192.168.37.201").getAddress(), 0), address("0.0.0.0").getAddress());
    }

    @Test
    public void withoutTapLimitedSegmentsEveryAddressHasOneAnswer() {
        Segment office = internal(1, "Office", "192.168.10.0/24");
        Segment printers = internal(2, "Printers", "192.168.10.128/25");
        Segment partner = segment(3, SegmentKind.EXTERNAL, "Partner", null, 64500L);
        MembershipResolver resolver = new MembershipResolver(Lists.newArrayList(office, printers, partner), Collections.emptyMap());
        List<UUID> taps = List.of(TAP_A, TAP_B);

        MembershipResolver.AnyTap inOffice = resolver.resolveForAnyTap(taps, address("192.168.10.5"), null);
        assertEquals(inOffice.segment(), office);
        assertFalse(inOffice.dependsOnTap());

        assertEquals(resolver.resolveForAnyTap(taps, address("192.168.10.200"), null).segment(), printers);

        assertEquals(resolver.resolveForAnyTap(taps, address("198.51.100.7"), 64500L).segment(), partner);
        assertNull(resolver.resolveForAnyTap(taps, address("198.51.100.7"), 64501L).segment());

        MembershipResolver.AnyTap nowhere = resolver.resolveForAnyTap(taps, address("10.9.9.9"), null);
        assertNull(nowhere.segment());
        assertFalse(nowhere.dependsOnTap());

        assertEquals(resolver.resolveForAnyTap(Collections.emptyList(), address("192.168.10.5"), null).segment(), office);
    }

    @Test
    public void twoSitesWithTheSameRangeCannotBeToldApartWithoutTheTap() {
        Segment berlin = segment(1, SegmentKind.INTERNAL, "Berlin LAN", List.of(TAP_A), "10.0.0.0/24");
        Segment hamburg = segment(2, SegmentKind.INTERNAL, "Hamburg LAN", List.of(TAP_B), "10.0.0.0/24");
        MembershipResolver resolver = new MembershipResolver(Lists.newArrayList(hamburg, berlin), Collections.emptyMap());

        MembershipResolver.AnyTap resolved = resolver.resolveForAnyTap(List.of(TAP_A, TAP_B), address("10.0.0.7"), null);
        assertNull(resolved.segment());
        assertTrue(resolved.dependsOnTap());
        assertEquals(resolved.candidates(), List.of(berlin, hamburg));
        assertFalse(resolved.unassignedPossible());

        MembershipResolver.AnyTap oneSite = resolver.resolveForAnyTap(List.of(TAP_A), address("10.0.0.7"), null);
        assertEquals(oneSite.segment(), berlin);
        assertFalse(oneSite.dependsOnTap());

        assertFalse(resolver.resolveForAnyTap(List.of(TAP_A, TAP_B), address("10.0.1.7"), null).dependsOnTap());
        assertNull(resolver.resolveForAnyTap(List.of(TAP_A, TAP_B), address("10.0.1.7"), null).segment());
    }

    @Test
    public void aSegmentOfSomeTapsLeavesTheAddressUnassignedForTheOthers() {
        Segment lab = segment(1, SegmentKind.INTERNAL, "Lab", List.of(TAP_A), "10.5.0.0/16");
        MembershipResolver resolver = new MembershipResolver(Lists.newArrayList(lab), Collections.emptyMap());

        MembershipResolver.AnyTap resolved = resolver.resolveForAnyTap(List.of(TAP_A, TAP_B), address("10.5.1.1"), null);
        assertNull(resolved.segment());
        assertTrue(resolved.dependsOnTap());
        assertEquals(resolved.candidates(), List.of(lab));
        assertTrue(resolved.unassignedPossible());

        Segment everything = internal(2, "Everything", "10.0.0.0/8");
        MembershipResolver layered = new MembershipResolver(Lists.newArrayList(lab, everything), Collections.emptyMap());
        MembershipResolver.AnyTap both = layered.resolveForAnyTap(List.of(TAP_A, TAP_B), address("10.5.1.1"), null);
        assertTrue(both.dependsOnTap());
        assertEquals(both.candidates(), List.of(everything, lab));
        assertFalse(both.unassignedPossible());

        Segment allTaps = segment(3, SegmentKind.INTERNAL, "All taps", List.of(TAP_A, TAP_B), "10.7.0.0/16");
        MembershipResolver all = new MembershipResolver(Lists.newArrayList(allTaps), Collections.emptyMap());
        assertEquals(all.resolveForAnyTap(List.of(TAP_A, TAP_B), address("10.7.0.1"), null).segment(), allTaps);
    }

}
