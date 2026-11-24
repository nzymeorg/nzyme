package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.ethernet.InternalAddresses;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.ethernet.CIDR;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

public class Exclusions {

    public static final int MAX_PER_SEGMENT = 100;
    public static final int NOTE_MAX_LENGTH = 500;

    // An exclusion is for single destinations that do not exist, not for parts of the network.
    public static final int WIDEST_PREFIX_IPV4 = 24;
    public static final int WIDEST_PREFIX_IPV6 = 64;

    private static final String SELECT = "SELECT e.id, e.uuid, e.segment_id, e.destination::text AS destination, " +
            "e.note, e.match_count, e.last_matched_at, e.created_at FROM segmentation_exclusions AS e ";

    private final NzymeNode nzyme;
    private final Segmentation segmentation;

    public Exclusions(NzymeNode nzyme, Segmentation segmentation) {
        this.nzyme = nzyme;
        this.segmentation = segmentation;
    }

    public Map<Long, List<Exclusion>> findAllPerSegment(Handle handle, UUID organizationId, UUID tenantId) {
        Map<Long, List<Exclusion>> exclusions = Maps.newHashMap();
        handle.createQuery(SELECT + "JOIN segmentation_segments AS s ON s.id = e.segment_id " +
                        "WHERE s.organization_id = :organization_id AND s.tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .map((rs, ctx) -> map(rs))
                .forEach(exclusion -> exclusions.computeIfAbsent(exclusion.segmentId(), k -> Lists.newArrayList())
                        .add(exclusion));

        return exclusions;
    }

    // Exclusions are created and deleted and never changed, so their number and the newest one tell every change.
    public static String version(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT COUNT(*) || ':' || COALESCE(MAX(e.id)::text, '') " +
                        "FROM segmentation_exclusions AS e JOIN segmentation_segments AS s ON s.id = e.segment_id " +
                        "WHERE s.organization_id = :organization_id AND s.tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(String.class)
                .one();
    }

    public List<Exclusion> findAllOfSegment(Segment segment) {
        return nzyme.getDatabase().withHandle(handle -> findAllOfSegment(handle, segment));
    }

    private static List<Exclusion> findAllOfSegment(Handle handle, Segment segment) {
        return handle.createQuery(SELECT + "WHERE e.segment_id = :segment_id ORDER BY e.destination ASC")
                .bind("segment_id", segment.id())
                .map((rs, ctx) -> map(rs))
                .list();
    }

    public Optional<Exclusion> find(Segment segment, UUID uuid) {
        return findAllOfSegment(segment).stream().filter(exclusion -> exclusion.uuid().equals(uuid)).findFirst();
    }

    @Nullable
    public static Exclusion excluding(Map<Long, List<Exclusion>> perSegment, Endpoint source, Endpoint destination) {
        if (source.segment() == null || !destination.internal() || !destination.hasAddress()) {
            return null;
        }

        for (Exclusion exclusion : perSegment.getOrDefault(source.segment().id(), Collections.emptyList())) {
            if (exclusion.destination().contains(destination.address())) {
                return exclusion;
            }
        }

        return null;
    }

    // Counts the sessions that were not recorded. An exclusion that was deleted in the meantime is skipped.
    public void recordMatches(Handle handle, Map<Long, Long> sessionsPerExclusion) {
        if (sessionsPerExclusion.isEmpty()) {
            return;
        }

        PreparedBatch batch = handle.prepareBatch("UPDATE segmentation_exclusions SET match_count = match_count + " +
                ":count, last_matched_at = NOW() WHERE id = :id");
        for (Map.Entry<Long, Long> matched : new TreeMap<>(sessionsPerExclusion).entrySet()) {
            batch.bind("id", matched.getKey()).bind("count", matched.getValue()).add();
        }
        batch.execute();
    }

    // Also removes what is stored of the attempts that will no longer be recorded.
    public Exclusion create(Segment segment, @Nullable CIDR destination, @Nullable String note) {
        UUID uuid = UUID.randomUUID();
        InternalAddresses.Rules internalAddresses = nzyme.getEthernet().internalAddresses()
                .of(segment.organizationId(), segment.tenantId());

        nzyme.getDatabase().useTransaction(handle -> {
            // Waits for an evaluation that is running, so that it cannot store again what is removed here.
            segmentation.state(handle, segment.organizationId(), segment.tenantId(), true);

            List<Exclusion> existing = findAllOfSegment(handle, segment);
            validate(segment, destination, note, existing,
                    segmentation.segments().findAll(handle, segment.organizationId(), segment.tenantId()),
                    internalAddresses);

            handle.createUpdate("INSERT INTO segmentation_exclusions(uuid, segment_id, destination, note, created_at) " +
                            "VALUES(:uuid, :segment_id, :destination::cidr, :note, NOW())")
                    .bind("uuid", uuid)
                    .bind("segment_id", segment.id())
                    .bind("destination", destination.toString())
                    .bind("note", note.trim())
                    .execute();

            segmentation.flows().removeUnanswered(handle, segment.organizationId(), segment.tenantId(), segment.id(),
                    destination);
        });

        return find(segment, uuid).orElseThrow();
    }

    public void delete(Segment segment, Exclusion exclusion) {
        nzyme.getDatabase().useTransaction(handle -> {
            segmentation.state(handle, segment.organizationId(), segment.tenantId(), true);

            handle.createUpdate("DELETE FROM segmentation_exclusions WHERE id = :id AND segment_id = :segment_id")
                    .bind("id", exclusion.id())
                    .bind("segment_id", segment.id())
                    .execute();
        });
    }

    static void validate(Segment segment,
                         @Nullable CIDR destination,
                         @Nullable String note,
                         List<Exclusion> existing,
                         List<Segment> segments,
                         InternalAddresses.Rules internalAddresses) {
        if (segment.kind() != SegmentKind.INTERNAL) {
            throw new IllegalArgumentException("Only a segment can have excluded destinations.");
        }

        if (destination == null) {
            throw new IllegalArgumentException("A destination is required.");
        }

        if (note == null || note.trim().isEmpty()) {
            throw new IllegalArgumentException("A note is required.");
        }

        if (note.trim().length() > NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException("The note must not be longer than " + NOTE_MAX_LENGTH + " characters.");
        }

        if (note.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("The note must not contain control characters.");
        }

        boolean ipv4 = destination.address().getAddress().length == 4;
        int widest = ipv4 ? WIDEST_PREFIX_IPV4 : WIDEST_PREFIX_IPV6;
        if (destination.prefix() < widest) {
            throw new IllegalArgumentException("The destination must not be larger than a /" + widest + " network.");
        }

        if (!internal(destination, segments, internalAddresses)) {
            throw new IllegalArgumentException("Only internal destinations can be excluded.");
        }

        if (existing.size() >= MAX_PER_SEGMENT) {
            throw new IllegalArgumentException("No more than " + MAX_PER_SEGMENT + " excluded destinations per segment.");
        }

        for (Exclusion exclusion : existing) {
            if (exclusion.destination().equals(destination)) {
                throw new IllegalArgumentException(destination + " is excluded already.");
            }
        }
    }

    private static boolean internal(CIDR destination, List<Segment> segments, InternalAddresses.Rules internalAddresses) {
        if (internalAddresses.isSiteLocal(destination.address())) {
            return true;
        }

        for (Segment segment : segments) {
            if (segment.kind() != SegmentKind.INTERNAL) {
                continue;
            }

            for (Segment.Member member : segment.members()) {
                if (member.network() != null && member.network().prefix() <= destination.prefix()
                        && member.network().contains(destination.address())) {
                    return true;
                }
            }
        }

        return false;
    }

    private static Exclusion map(ResultSet rs) throws SQLException {
        return new Exclusion(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                rs.getLong("segment_id"),
                CIDR.parse(rs.getString("destination")),
                rs.getString("note"),
                rs.getLong("match_count"),
                rs.getTimestamp("last_matched_at") == null ? null : new DateTime(rs.getTimestamp("last_matched_at")),
                new DateTime(rs.getTimestamp("created_at"))
        );
    }

}
