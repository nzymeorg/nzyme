package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.ethernet.CIDR;
import com.google.common.collect.Lists;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static app.nzyme.core.ethernet.segmentation.SegmentationTestData.*;
import static org.testng.Assert.*;

public class ExclusionsTest {

    private static final Segment CLIENTS = internal(1, "Clients", "10.1.0.0/16");
    private static final Segment SERVERS = internal(2, "Servers", "10.2.0.0/16", "203.0.113.0/24");
    private static final Segment LIMITED = segment(3, SegmentKind.INTERNAL, "Lab", Lists.newArrayList(TAP_B), "10.3.0.0/16");
    private static final Segment PARTNER = segment(4, SegmentKind.EXTERNAL, "Partner", null, "198.51.100.0/24");

    private static final List<Segment> SEGMENTS = Lists.newArrayList(CLIENTS, SERVERS, LIMITED, PARTNER);
    private static final MembershipResolver RESOLVER = new MembershipResolver(SEGMENTS, Collections.emptyMap());

    private static final Map<Long, List<Exclusion>> EXCLUDED = Map.of(
            CLIENTS.id(), List.of(exclusion("172.16.0.157/32"), exclusion("10.2.9.0/24")),
            LIMITED.id(), List.of(exclusion("172.16.0.157/32"))
    );

    private static Exclusion exclusion(String destination) {
        return new Exclusion(1, UUID.randomUUID(), CLIENTS.id(), CIDR.parse(destination), "Note.", 0, null,
                DateTime.now());
    }

    private static boolean excludes(Map<Long, List<Exclusion>> perSegment, Endpoint source, Endpoint destination) {
        return Exclusions.excluding(perSegment, source, destination) != null;
    }

    private static void assertRejected(Segment segment, String destination, String note, List<Exclusion> existing) {
        assertThrows(IllegalArgumentException.class, () -> Exclusions.validate(segment,
                destination == null ? null : CIDR.parse(destination), note, existing, SEGMENTS));
    }

    @Test
    public void testOnlyTheSegmentOfTheExclusionIsExcluded() {
        Endpoint dead = host(RESOLVER, "172.16.0.157");

        assertTrue(excludes(EXCLUDED, host(RESOLVER, "10.1.5.20"), dead));
        assertFalse(excludes(EXCLUDED, host(RESOLVER, "10.2.5.20"), dead));
        assertFalse(excludes(EXCLUDED, host(RESOLVER, "192.168.77.1"), dead));
    }

    @Test
    public void testADestinationIsExcludedByAddressAndByNetwork() {
        Endpoint client = host(RESOLVER, "10.1.5.20");

        assertTrue(excludes(EXCLUDED, client, host(RESOLVER, "10.2.9.1")));
        assertEquals(Exclusions.excluding(EXCLUDED, client, host(RESOLVER, "10.2.9.1")).destination(),
                CIDR.parse("10.2.9.0/24"));
        assertTrue(excludes(EXCLUDED, client, host(RESOLVER, "10.2.9.254")));
        assertFalse(excludes(EXCLUDED, client, host(RESOLVER, "10.2.10.1")));
        assertFalse(excludes(EXCLUDED, client, host(RESOLVER, "172.16.0.158")));
    }

    @Test
    public void testASegmentThatIsLimitedToTapsOnlyExcludesWhatTheyRecord() {
        Endpoint dead = host(RESOLVER, "172.16.0.157");

        Endpoint seenByOther = RESOLVER.resolve(TAP_A, address("10.3.0.5"), true, null, null);
        Endpoint seenByItsTap = RESOLVER.resolve(TAP_B, address("10.3.0.5"), true, null, null);

        assertFalse(excludes(EXCLUDED, seenByOther, dead));
        assertTrue(excludes(EXCLUDED, seenByItsTap, dead));
    }

    @Test
    public void testExternalBroadcastAndFoldedDestinationsAreNeverExcluded() {
        Endpoint client = host(RESOLVER, "10.1.5.20");
        Map<Long, List<Exclusion>> wide = Map.of(CLIENTS.id(), List.of(exclusion("198.51.100.0/24"),
                exclusion("10.2.9.0/24")));

        assertFalse(excludes(wide, client, RESOLVER.resolve(TAP_A, address("198.51.100.7"), false, 64500L, "US")));
        assertFalse(excludes(wide, client, Endpoint.broadcast(address("10.2.9.255"))));
        assertFalse(excludes(wide, client, RESOLVER.stored(true, SERVERS.id(), null, null)));
    }

    @Test
    public void testAnAddressAndASmallNetworkCanBeExcluded() {
        Exclusions.validate(CLIENTS, CIDR.parse("172.16.0.157"), "Does not exist.", none(), SEGMENTS);
        Exclusions.validate(CLIENTS, CIDR.parse("172.16.0.0/24"), "Does not exist.", none(), SEGMENTS);
        Exclusions.validate(CLIENTS, CIDR.parse("10.1.0.9/32"), "Inside the segment itself.", none(), SEGMENTS);
    }

    @Test
    public void testAnAddressThatIsOnlyInternalByItsSegmentCanBeExcluded() {
        Exclusions.validate(CLIENTS, CIDR.parse("203.0.113.9"), "Decommissioned.", none(), SEGMENTS);

        assertRejected(CLIENTS, "203.0.114.9", "Not ours.", none());
        assertRejected(CLIENTS, "198.51.100.7", "In an external network.", none());
    }

    @Test
    public void testLargeNetworksAreRejected() {
        assertRejected(CLIENTS, "172.16.0.0/23", "Too large.", none());
        assertRejected(CLIENTS, "10.0.0.0/8", "Too large.", none());
        assertRejected(CLIENTS, "fec0::/48", "Too large.", none());

        Exclusions.validate(CLIENTS, CIDR.parse("fec0:0:0:1::/64"), "Small enough.", none(), SEGMENTS);
    }

    @Test
    public void testANoteIsRequired() {
        assertRejected(CLIENTS, "172.16.0.157", null, none());
        assertRejected(CLIENTS, "172.16.0.157", "   ", none());
        assertRejected(CLIENTS, "172.16.0.157", "x".repeat(Exclusions.NOTE_MAX_LENGTH + 1), none());
        assertRejected(CLIENTS, "172.16.0.157", "Zero\u0000byte", none());
        assertRejected(CLIENTS, "172.16.0.157", "Two\nlines", none());
    }

    @Test
    public void testExternalNetworksHaveNoExclusions() {
        assertRejected(PARTNER, "172.16.0.157", "Does not exist.", none());
    }

    @Test
    public void testADestinationIsExcludedOncePerSegment() {
        assertRejected(CLIENTS, "172.16.0.157/32", "Again.", List.of(exclusion("172.16.0.157")));

        Exclusions.validate(CLIENTS, CIDR.parse("172.16.0.158"), "Another one.", List.of(exclusion("172.16.0.157")),
                SEGMENTS);
    }

    @Test
    public void testTheNumberOfExclusionsPerSegmentIsLimited() {
        List<Exclusion> full = Lists.newArrayList();
        for (int i = 0; i < Exclusions.MAX_PER_SEGMENT; i++) {
            full.add(exclusion("172.16." + (i / 250) + "." + (i % 250 + 1)));
        }

        assertRejected(CLIENTS, "172.17.0.1", "One too many.", full);
    }

}
