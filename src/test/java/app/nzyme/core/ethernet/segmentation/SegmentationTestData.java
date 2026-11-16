package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.ethernet.CIDR;
import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

class SegmentationTestData {

    static final UUID TAP_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID TAP_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    static Segment segment(long id, SegmentKind kind, String name, @Nullable List<UUID> taps, Object... members) {
        List<Segment.Member> parsed = Lists.newArrayList();
        for (Object member : members) {
            if (member instanceof Long asn) {
                parsed.add(Segment.Member.ofAsn(asn));
            } else {
                parsed.add(Segment.Member.ofNetwork(CIDR.parse((String) member)));
            }
        }

        return new Segment(id, new UUID(0, id), ORGANIZATION, TENANT, kind, name, null, null, taps, parsed,
                DateTime.now(), DateTime.now());
    }

    static Segment internal(long id, String name, String... networks) {
        return segment(id, SegmentKind.INTERNAL, name, null, (Object[]) networks);
    }

    static InetAddress address(String address) {
        return InetAddresses.forString(address);
    }

    static PolicyRule.Selector selector(PolicyRule.SelectorType type, @Nullable String value) {
        return new PolicyRule.Selector(type, value);
    }

    static PolicyRule.Selector any() {
        return selector(PolicyRule.SelectorType.ANY, null);
    }

    static PolicyRule.Selector segmentSelector(Segment segment) {
        return selector(PolicyRule.SelectorType.SEGMENT, segment.uuid().toString());
    }

    static PolicyRule.Service tcp(int port) {
        return new PolicyRule.Service("TCP", port, null, null);
    }

    static PolicyRule.Service udp(int port) {
        return new PolicyRule.Service("UDP", port, null, null);
    }

    static PolicyRule rule(List<PolicyRule.Selector> sources,
                           List<PolicyRule.Selector> destinations,
                           List<PolicyRule.Service> services) {
        return new PolicyRule(sources, destinations, services);
    }

    static PolicyMatcher matcher(PolicyRule scope, PolicyRule... allowed) {
        return new PolicyMatcher(null, scope, Lists.newArrayList(allowed));
    }

    static Flow flow(Endpoint source, Endpoint destination, int port, Outcome outcome, long sessions) {
        DateTime now = DateTime.now();
        return new Flow(TAP_A, Flows.bucketOf(now), source, destination, Flows.key(source), Flows.key(destination),
                "TCP", port, "", outcome, sessions, 0, now, now);
    }

    static Endpoint host(MembershipResolver resolver, String address) {
        InetAddress parsed = address(address);
        return resolver.resolve(TAP_A, parsed, parsed.isSiteLocalAddress(), null, null);
    }

    static <T> List<T> none() {
        return Collections.emptyList();
    }

}
