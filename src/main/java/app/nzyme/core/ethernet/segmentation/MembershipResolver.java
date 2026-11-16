package app.nzyme.core.ethernet.segmentation;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

// The most specific network wins. Among equally specific networks, a segment that is limited to the recording tap
// wins over one that is not.
public class MembershipResolver {

    public record TaggedAsset(UUID asset, Set<UUID> tags) { }

    private record Entry(Segment segment, boolean tapLimited) { }

    // segment is only set if every tap agrees. If segments are limited to taps and the taps do not agree, candidates
    // has the segments the address is in for some tap.
    public record AnyTap(@Nullable Segment segment, List<Segment> candidates, boolean unassignedPossible) {

        public static final AnyTap NONE = new AnyTap(null, Collections.emptyList(), false);

        public boolean dependsOnTap() {
            return !candidates.isEmpty();
        }

    }

    private static final UUID NO_TAP = new UUID(0, 0);

    private final NavigableMap<Integer, Map<ByteBuffer, List<Entry>>> v4 = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Integer, Map<ByteBuffer, List<Entry>>> v6 = new TreeMap<>(Comparator.reverseOrder());

    private final Map<Long, List<Segment>> byAsn = Maps.newHashMap();
    private final Map<Long, Segment> byId = Maps.newHashMap();
    private final Map<UUID, Segment> byUuid = Maps.newHashMap();
    private final Map<InetAddress, TaggedAsset> assets;

    private boolean tapLimited = false;

    public MembershipResolver(Collection<Segment> segments, Map<InetAddress, TaggedAsset> assets) {
        this.assets = assets;

        List<Segment> ordered = Lists.newArrayList(segments);
        ordered.sort(Comparator.comparingLong(Segment::id));

        for (Segment segment : ordered) {
            byId.put(segment.id(), segment);
            byUuid.put(segment.uuid(), segment);
            if (segment.tapUuids() != null) {
                tapLimited = true;
            }

            for (Segment.Member member : segment.members()) {
                if (member.type() == Segment.MemberType.ASN && member.asn() != null) {
                    byAsn.computeIfAbsent(member.asn(), k -> Lists.newArrayList()).add(segment);
                    continue;
                }

                if (member.network() == null) {
                    continue;
                }

                byte[] network = member.network().address().getAddress();
                NavigableMap<Integer, Map<ByteBuffer, List<Entry>>> table = network.length == 4 ? v4 : v6;
                List<Entry> entries = table.computeIfAbsent(member.network().prefix(), k -> Maps.newHashMap())
                        .computeIfAbsent(ByteBuffer.wrap(network), k -> Lists.newArrayList());

                entries.add(new Entry(segment, segment.tapUuids() != null));
                // Tap limited segments first, then by ID. The list is in ID order already.
                entries.sort(Comparator.comparing((Entry e) -> !e.tapLimited()));
            }
        }
    }

    public Collection<Segment> segments() {
        return byId.values();
    }

    @Nullable
    public Segment segment(@Nullable Long id) {
        return id == null ? null : byId.get(id);
    }

    @Nullable
    public Segment segment(UUID uuid) {
        return byUuid.get(uuid);
    }

    public List<InetAddress> addressesOfTag(UUID tag) {
        List<InetAddress> result = Lists.newArrayList();
        for (Map.Entry<InetAddress, TaggedAsset> entry : assets.entrySet()) {
            if (entry.getValue().tags().contains(tag)) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

    public List<InetAddress> addressesOfAsset(UUID asset) {
        List<InetAddress> result = Lists.newArrayList();
        for (Map.Entry<InetAddress, TaggedAsset> entry : assets.entrySet()) {
            if (entry.getValue().asset().equals(asset)) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

    @Nullable
    public Segment segmentOfAnyTap(InetAddress address) {
        byte[] bytes = address.getAddress();
        NavigableMap<Integer, Map<ByteBuffer, List<Entry>>> table = bytes.length == 4 ? v4 : v6;

        for (Map.Entry<Integer, Map<ByteBuffer, List<Entry>>> level : table.entrySet()) {
            List<Entry> entries = level.getValue().get(ByteBuffer.wrap(mask(bytes, level.getKey())));
            if (entries != null && !entries.isEmpty()) {
                return entries.get(0).segment();
            }
        }

        return null;
    }

    // The segment an address is in by its networks. Autonomous systems are not considered.
    @Nullable
    public Segment segmentOf(UUID tap, InetAddress address) {
        byte[] bytes = address.getAddress();
        NavigableMap<Integer, Map<ByteBuffer, List<Entry>>> table = bytes.length == 4 ? v4 : v6;

        for (Map.Entry<Integer, Map<ByteBuffer, List<Entry>>> level : table.entrySet()) {
            List<Entry> entries = level.getValue().get(ByteBuffer.wrap(mask(bytes, level.getKey())));
            if (entries == null) {
                continue;
            }

            for (Entry entry : entries) {
                if (entry.segment().coversTap(tap)) {
                    return entry.segment();
                }
            }
        }

        return null;
    }

    public Endpoint resolve(UUID tap,
                            InetAddress address,
                            boolean siteLocal,
                            @Nullable Long asn,
                            @Nullable String countryCode) {
        Segment segment = segmentOf(tap, address);

        if (segment == null && !siteLocal && asn != null) {
            List<Segment> candidates = byAsn.get(asn);
            if (candidates != null) {
                for (Segment candidate : candidates) {
                    if (candidate.coversTap(tap)) {
                        segment = candidate;
                        break;
                    }
                }
            }
        }

        boolean internal = segment == null ? siteLocal : segment.kind() == SegmentKind.INTERNAL;
        TaggedAsset asset = internal ? assets.get(address) : null;

        return new Endpoint(
                address,
                internal,
                segment,
                asset == null ? Collections.emptySet() : asset.tags(),
                asset == null ? null : asset.asset(),
                internal ? null : asn,
                internal ? null : countryCode
        );
    }

    public AnyTap resolveForAnyTap(Collection<UUID> taps, InetAddress address, @Nullable Long asn) {
        boolean siteLocal = address.isSiteLocalAddress();

        if (!tapLimited || taps.isEmpty()) {
            Segment segment = resolve(NO_TAP, address, siteLocal, asn, null).segment();
            return segment == null ? AnyTap.NONE : new AnyTap(segment, Collections.emptyList(), false);
        }

        Map<Long, Segment> found = Maps.newLinkedHashMap();
        boolean unassigned = false;
        for (UUID tap : taps) {
            Segment segment = resolve(tap, address, siteLocal, asn, null).segment();
            if (segment == null) {
                unassigned = true;
            } else {
                found.put(segment.id(), segment);
            }
        }

        if (found.isEmpty()) {
            return AnyTap.NONE;
        }

        if (found.size() == 1 && !unassigned) {
            return new AnyTap(found.values().iterator().next(), Collections.emptyList(), false);
        }

        List<Segment> candidates = Lists.newArrayList(found.values());
        candidates.sort(Comparator.comparing((Segment s) -> s.name().toLowerCase()).thenComparingLong(Segment::id));

        return new AnyTap(null, candidates, unassigned);
    }

    public Endpoint stored(boolean internal, @Nullable Long segmentId, @Nullable Long asn, @Nullable String countryCode) {
        return new Endpoint(null, internal, segment(segmentId), Collections.emptySet(), null, asn, countryCode);
    }

    static byte[] mask(byte[] address, int prefix) {
        byte[] masked = new byte[address.length];
        int fullBytes = prefix / 8;
        System.arraycopy(address, 0, masked, 0, Math.min(fullBytes, address.length));

        int remainder = prefix % 8;
        if (remainder != 0 && fullBytes < address.length) {
            masked[fullBytes] = (byte) (address[fullBytes] & (0xFF << (8 - remainder)));
        }

        return masked;
    }

}
