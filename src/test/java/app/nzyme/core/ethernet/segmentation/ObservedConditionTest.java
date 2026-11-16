package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.ethernet.CIDR;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static app.nzyme.core.ethernet.segmentation.PolicyRule.SelectorType.ADDRESS;
import static app.nzyme.core.ethernet.segmentation.PolicyRule.SelectorType.BROADCAST;
import static app.nzyme.core.ethernet.segmentation.PolicyRule.SelectorType.EXTERNAL;
import static app.nzyme.core.ethernet.segmentation.PolicyRule.SelectorType.INTERNAL;
import static app.nzyme.core.ethernet.segmentation.SegmentationTestData.any;
import static app.nzyme.core.ethernet.segmentation.SegmentationTestData.internal;
import static app.nzyme.core.ethernet.segmentation.SegmentationTestData.segment;
import static app.nzyme.core.ethernet.segmentation.SegmentationTestData.segmentSelector;
import static app.nzyme.core.ethernet.segmentation.SegmentationTestData.selector;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ObservedConditionTest {

    private static final Segment SERVERS = internal(1, "Servers", "10.1.0.0/16");

    private static final MembershipResolver RESOLVER =
            new MembershipResolver(Lists.newArrayList(SERVERS), Collections.emptyMap());

    private static String sources(List<PolicyRule.Selector> selectors, Map<String, Object> bindings, boolean stale) {
        return SegmentationViews.observedCondition(selectors, "source", "destination", RESOLVER, bindings, stale);
    }

    private static String destinations(List<PolicyRule.Selector> selectors, Map<String, Object> bindings) {
        return SegmentationViews.observedCondition(selectors, "destination", "source", RESOLVER, bindings, false);
    }

    @Test
    public void sourcesThatStartNothingAreSeenWhereTheyAnswered() {
        assertEquals(sources(List.of(selector(EXTERNAL, null)), Maps.newHashMap(), false),
                "(((NOT source_internal AND source_key NOT LIKE 'bc:%')) OR "
                        + "(((NOT destination_internal AND destination_key NOT LIKE 'bc:%')) "
                        + "AND outcome = 'CONNECTED'))");
    }

    @Test
    public void destinationsThatNothingConnectsToAreSeenWhereTheyWereAnswered() {
        Map<String, Object> bindings = Maps.newHashMap();

        assertEquals(destinations(List.of(segmentSelector(SERVERS)), bindings),
                "((destination_segment_id = :destination_selector_0) OR "
                        + "((source_segment_id = :source_selector_0) AND outcome = 'CONNECTED'))");
        assertEquals(bindings.get("destination_selector_0"), 1L);
        assertEquals(bindings.get("source_selector_0"), 1L);
    }

    @Test
    public void aPrivateNetworkIsOnlySeenByItsAddresses() {
        assertEquals(sources(List.of(selector(ADDRESS, "10.99.0.0/16")), Maps.newHashMap(), false),
                "((source_address <<= :source_selector_0::inet) OR "
                        + "((destination_address <<= :destination_selector_0::inet) AND outcome = 'CONNECTED'))");
        assertEquals(destinations(List.of(selector(ADDRESS, "10.99.0.0/16")), Maps.newHashMap()),
                "((destination_address <<= :destination_selector_0::inet) OR "
                        + "((source_address <<= :source_selector_0::inet) AND outcome = 'CONNECTED'))");
    }

    @Test
    public void anyExternalSideStandsForANetworkThatCanBeExternal() {
        assertEquals(destinations(List.of(selector(ADDRESS, "203.0.113.0/24")), Maps.newHashMap()),
                "(((destination_address <<= :destination_selector_0::inet OR (destination_address IS NULL "
                        + "AND NOT destination_internal AND destination_key NOT LIKE 'bc:%'))) OR "
                        + "(((source_address <<= :source_selector_0::inet OR (source_address IS NULL "
                        + "AND NOT source_internal AND source_key NOT LIKE 'bc:%'))) AND outcome = 'CONNECTED'))");
    }

    @Test
    public void whichNetworksCanBeExternal() {
        Segment branch = segment(2, SegmentKind.EXTERNAL, "Branch", null, "10.50.0.0/16");
        Segment dmz = internal(3, "DMZ", "198.51.100.0/24");
        Segment cloud = segment(4, SegmentKind.EXTERNAL, "Cloud", null, 64500L);
        List<Segment> segments = List.of(SERVERS, branch, dmz, cloud);

        assertFalse(SegmentationViews.canBeExternal(CIDR.parse("10.99.0.0/16"), segments));
        assertFalse(SegmentationViews.canBeExternal(CIDR.parse("192.168.1.7/32"), segments));
        assertFalse(SegmentationViews.canBeExternal(CIDR.parse("172.16.0.0/12"), segments));
        assertFalse(SegmentationViews.canBeExternal(CIDR.parse("fec0::/64"), segments));
        assertTrue(SegmentationViews.canBeExternal(CIDR.parse("10.50.3.0/24"), segments));
        assertTrue(SegmentationViews.canBeExternal(CIDR.parse("10.0.0.0/8"), segments));

        assertTrue(SegmentationViews.canBeExternal(CIDR.parse("203.0.113.0/24"), segments));
        assertTrue(SegmentationViews.canBeExternal(CIDR.parse("2001:db8::/32"), segments));
        assertFalse(SegmentationViews.canBeExternal(CIDR.parse("198.51.100.16/28"), segments));
        assertTrue(SegmentationViews.canBeExternal(CIDR.parse("198.51.0.0/16"), segments));

        assertTrue(SegmentationViews.canBeExternal(CIDR.parse("172.0.0.0/8"), segments));
        assertTrue(SegmentationViews.canBeExternal(CIDR.parse("0.0.0.0/0"), segments));

        assertFalse(SegmentationViews.canBeExternal(CIDR.parse("10.50.3.0/24"), List.of()));
        assertTrue(SegmentationViews.canBeExternal(CIDR.parse("198.51.100.16/28"), List.of()));
    }

    @Test
    public void whatWasSentToBroadcastAddressesIsOnlySeenAsDestination() {
        assertEquals(destinations(List.of(selector(BROADCAST, null)), Maps.newHashMap()),
                "((destination_key LIKE 'bc:%') OR ((source_key LIKE 'bc:%') AND outcome = 'CONNECTED'))");
    }

    @Test
    public void anySideIsSeenInAnyFlow() {
        assertNull(sources(List.of(), Maps.newHashMap(), false));
        assertNull(sources(List.of(any()), Maps.newHashMap(), false));
        assertNull(destinations(List.of(selector(INTERNAL, null), any()), Maps.newHashMap()));
    }

    @Test
    public void segmentsAreNotAskedForWhileTheRollupWaitsForChangedSegments() {
        assertNull(sources(List.of(segmentSelector(SERVERS)), Maps.newHashMap(), true));
    }

}
