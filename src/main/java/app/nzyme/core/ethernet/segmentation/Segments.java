package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.ethernet.spoofing.db.DhcpSpoofingServerEntry;
import app.nzyme.core.context.db.NetworkContextEntry;
import app.nzyme.core.ethernet.dns.tunnels.DnsTunnelJson;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.CIDR;
import app.nzyme.core.ethernet.segmentation.filters.SegmentFilters;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.net.InetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class Segments {

    // The same limits as network context, so that names fit wherever addresses are annotated.
    public static final int NAME_MAX_LENGTH = 12;
    public static final int DESCRIPTION_MAX_LENGTH = 32;
    public static final int MAX_MEMBERS = 256;

    private static final int SUGGESTION_LOOKBACK_DAYS = 7;
    public static final int SUGGESTION_LIMIT = 50;

    private static final int BULK_DELETE_CHUNK = 5000;

    public enum SuggestionSource {
        NETWORK_CONTEXT,
        DHCP,
        OBSERVED
    }

    public record Suggestion(SuggestionSource source,
                             SegmentKind kind,
                             String network,
                             @Nullable String name,
                             @Nullable String description,
                             @Nullable Long hosts,
                             @Nullable String dhcpServer) { }

    public record Suggestions(List<Suggestion> suggestions, boolean limited) { }

    public enum OrderColumn {

        NAME("lower(s.name)"),
        KIND("s.kind"),
        DESCRIPTION("lower(s.description)"),
        CREATED_AT("s.created_at"),
        UPDATED_AT("s.updated_at");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    private static final String SELECT_MEMBERS = "SELECT m.segment_id, m.member_type, m.network::text AS network, " +
            "m.asn FROM segmentation_segment_members AS m ";

    private final NzymeNode nzyme;
    private final Segmentation segmentation;

    public Segments(NzymeNode nzyme, Segmentation segmentation) {
        this.nzyme = nzyme;
        this.segmentation = segmentation;
    }

    public List<Segment> findAll(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle -> findAll(handle, organizationId, tenantId));
    }

    public List<Segment> findAll(Handle handle, UUID organizationId, UUID tenantId) {
        Map<Long, List<Segment.Member>> members = Maps.newHashMap();
        handle.createQuery(SELECT_MEMBERS + "JOIN segmentation_segments AS s ON s.id = m.segment_id " +
                        "WHERE s.organization_id = :organization_id AND s.tenant_id = :tenant_id ORDER BY m.id ASC")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .map((rs, ctx) -> addMember(rs, members))
                .list();

        return handle.createQuery("SELECT * FROM segmentation_segments WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id ORDER BY kind DESC, lower(name) ASC")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .map((rs, ctx) -> map(rs, members))
                .list();
    }

    public long count(Filters filters, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SegmentFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM segmentation_segments AS s " +
                                "WHERE s.organization_id = :organization_id AND s.tenant_id = :tenant_id " +
                                filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<Segment> findAll(Filters filters,
                                 OrderColumn orderColumn,
                                 OrderDirection orderDirection,
                                 int limit,
                                 int offset,
                                 UUID organizationId,
                                 UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SegmentFilters());

        return nzyme.getDatabase().withHandle(handle -> {
            List<Long> ids = handle.createQuery("SELECT s.id FROM segmentation_segments AS s " +
                            "WHERE s.organization_id = :organization_id AND s.tenant_id = :tenant_id " +
                            filterFragment.whereSql() +
                            "ORDER BY <order_column> <order_direction> NULLS LAST, lower(s.name) ASC, s.id ASC " +
                            "LIMIT :limit OFFSET :offset")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bindMap(filterFragment.bindings())
                    .bind("limit", limit)
                    .bind("offset", offset)
                    .define("order_column", orderColumn.getColumnName())
                    .define("order_direction", orderDirection)
                    .mapTo(Long.class)
                    .list();

            if (ids.isEmpty()) {
                return Collections.<Segment>emptyList();
            }

            Map<Long, List<Segment.Member>> members = Maps.newHashMap();
            handle.createQuery(SELECT_MEMBERS + "WHERE m.segment_id IN (<ids>) ORDER BY m.id ASC")
                    .bindList("ids", ids)
                    .map((rs, ctx) -> addMember(rs, members))
                    .list();

            Map<Long, Segment> segments = Maps.newHashMap();
            handle.createQuery("SELECT * FROM segmentation_segments WHERE id IN (<ids>)")
                    .bindList("ids", ids)
                    .map((rs, ctx) -> map(rs, members))
                    .forEach(segment -> segments.put(segment.id(), segment));

            List<Segment> page = Lists.newArrayList();
            for (long id : ids) {
                Segment segment = segments.get(id);
                if (segment != null) {
                    page.add(segment);
                }
            }

            return page;
        });
    }

    private static boolean addMember(ResultSet rs, Map<Long, List<Segment.Member>> members) throws SQLException {
        Segment.MemberType type = Segment.MemberType.valueOf(rs.getString("member_type"));
        Segment.Member member = type == Segment.MemberType.CIDR
                ? Segment.Member.ofNetwork(CIDR.parse(rs.getString("network")))
                : Segment.Member.ofAsn(rs.getLong("asn"));
        members.computeIfAbsent(rs.getLong("segment_id"), k -> Lists.newArrayList()).add(member);

        return true;
    }

    private static Segment map(ResultSet rs, Map<Long, List<Segment.Member>> members) throws SQLException {
        return new Segment(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                SegmentKind.valueOf(rs.getString("kind")),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("notes"),
                tapUuids(rs.getString("tap_uuids")),
                members.getOrDefault(rs.getLong("id"), Collections.emptyList()),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

    public Optional<Segment> find(UUID uuid, UUID organizationId, UUID tenantId) {
        return findAll(organizationId, tenantId).stream().filter(s -> s.uuid().equals(uuid)).findFirst();
    }

    public Segment create(UUID organizationId,
                          UUID tenantId,
                          SegmentKind kind,
                          String name,
                          @Nullable String description,
                          @Nullable String notes,
                          @Nullable List<UUID> tapUuids,
                          List<Segment.Member> members) {
        UUID uuid = UUID.randomUUID();

        nzyme.getDatabase().useTransaction(handle -> {
            validate(handle, null, organizationId, tenantId, kind, name, description, tapUuids, members);

            long id = handle.createQuery("INSERT INTO segmentation_segments(uuid, organization_id, tenant_id, kind, " +
                            "name, description, notes, tap_uuids, created_at, updated_at) VALUES(:uuid, " +
                            ":organization_id, :tenant_id, :kind, :name, :description, :notes, :tap_uuids::jsonb, " +
                            "NOW(), NOW()) RETURNING id")
                    .bind("uuid", uuid)
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("kind", kind.name())
                    .bind("name", name.trim())
                    .bind("description", Strings.emptyToNull(Strings.nullToEmpty(description).trim()))
                    .bind("notes", Strings.emptyToNull(Strings.nullToEmpty(notes).trim()))
                    .bind("tap_uuids", tapsJson(tapUuids))
                    .mapTo(Long.class)
                    .one();

            writeMembers(handle, id, members);
            segmentation.requestRestamp(handle, organizationId, tenantId);
        });

        segmentation.invalidateResolver(organizationId, tenantId);

        return find(uuid, organizationId, tenantId).orElseThrow();
    }

    public void update(Segment segment,
                       String name,
                       @Nullable String description,
                       @Nullable String notes,
                       @Nullable List<UUID> tapUuids,
                       List<Segment.Member> members) {
        nzyme.getDatabase().useTransaction(handle -> {
            validate(handle, segment.id(), segment.organizationId(), segment.tenantId(), segment.kind(), name,
                    description, tapUuids, members);

            handle.createUpdate("UPDATE segmentation_segments SET name = :name, description = :description, " +
                            "notes = :notes, tap_uuids = :tap_uuids::jsonb, updated_at = NOW() WHERE id = :id")
                    .bind("id", segment.id())
                    .bind("name", name.trim())
                    .bind("description", Strings.emptyToNull(Strings.nullToEmpty(description).trim()))
                    .bind("notes", Strings.emptyToNull(Strings.nullToEmpty(notes).trim()))
                    .bind("tap_uuids", tapsJson(tapUuids))
                    .execute();

            boolean membershipChanged = !Sets.newHashSet(segment.members()).equals(Sets.newHashSet(members))
                    || !java.util.Objects.equals(asSet(segment.tapUuids()), asSet(tapUuids));

            if (membershipChanged) {
                handle.createUpdate("DELETE FROM segmentation_segment_members WHERE segment_id = :id")
                        .bind("id", segment.id())
                        .execute();
                writeMembers(handle, segment.id(), members);
                segmentation.requestRestamp(handle, segment.organizationId(), segment.tenantId());
            }
        });

        segmentation.invalidateResolver(segment.organizationId(), segment.tenantId());
    }

    public void delete(Segment segment) {
        nzyme.getDatabase().useTransaction(handle -> {
            handle.createUpdate("DELETE FROM segmentation_segments WHERE id = :id")
                    .bind("id", segment.id())
                    .execute();
            segmentation.requestRestamp(handle, segment.organizationId(), segment.tenantId());
        });

        segmentation.invalidateResolver(segment.organizationId(), segment.tenantId());
    }

    private record Target(long id, UUID uuid, String name) { }

    public BulkResult delete(BulkSelection selection, UUID organizationId, UUID tenantId) {
        if (selection.isEmpty()) {
            return BulkResult.of(0);
        }

        Map<UUID, List<String>> references = Policies.segmentReferences(
                segmentation.policies().findAll(organizationId, tenantId));

        BulkResult result = nzyme.getDatabase().inTransaction(handle -> {
            List<Target> targets;
            if (selection.isChecked()) {
                targets = handle.createQuery("SELECT s.id, s.uuid, s.name FROM segmentation_segments AS s " +
                                "WHERE s.organization_id = :organization_id AND s.tenant_id = :tenant_id " +
                                "AND s.uuid IN (<uuids>) ORDER BY lower(s.name) ASC")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindList("uuids", selection.uuids())
                        .map((rs, ctx) -> new Target(rs.getLong("id"), UUID.fromString(rs.getString("uuid")), rs.getString("name")))
                        .list();
            } else {
                FilterSqlFragment filterFragment = FilterSql.generate(selection.filters(), new SegmentFilters());
                targets = handle.createQuery("SELECT s.id, s.uuid, s.name FROM segmentation_segments AS s " +
                                "WHERE s.organization_id = :organization_id AND s.tenant_id = :tenant_id " +
                                filterFragment.whereSql() + "ORDER BY lower(s.name) ASC")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .map((rs, ctx) -> new Target(rs.getLong("id"), UUID.fromString(rs.getString("uuid")), rs.getString("name")))
                        .list();
            }

            List<Long> free = Lists.newArrayList();
            List<BulkResult.Skipped> skipped = Lists.newArrayList();
            for (Target target : targets) {
                List<String> policies = references.get(target.uuid());
                if (policies == null) {
                    free.add(target.id());
                } else {
                    skipped.add(new BulkResult.Skipped(target.uuid(), target.name(), "In use by "
                            + (policies.size() == 1 ? "policy " : "policies ")
                            + "\"" + String.join("\", \"", policies) + "\"."));
                }
            }

            long deleted = 0;
            for (List<Long> ids : Lists.partition(free, BULK_DELETE_CHUNK)) {
                deleted += handle.createUpdate("DELETE FROM segmentation_segments WHERE id IN (<ids>)")
                        .bindList("ids", ids)
                        .execute();
            }

            if (deleted > 0) {
                segmentation.requestRestamp(handle, organizationId, tenantId);
            }

            return new BulkResult(deleted, skipped);
        });

        segmentation.invalidateResolver(organizationId, tenantId);
        return result;
    }

    // taps are the taps whose records the caller may read: only they suggest networks that were seen talking.
    public Suggestions suggestions(UUID organizationId, UUID tenantId, List<UUID> taps) {
        List<Segment> segments = findAll(organizationId, tenantId);
        Set<String> taken = Sets.newHashSet();
        for (Segment segment : segments) {
            for (Segment.Member member : segment.members()) {
                if (member.network() != null) {
                    taken.add(member.network().toString());
                }
            }
        }

        List<Suggestion> suggestions = Lists.newArrayList();
        boolean[] limited = new boolean[]{false};

        nzyme.getDatabase().useHandle(handle -> {
            List<NetworkContextEntry> contexts = nzyme.getContextService()
                    .findAllNetworkContext(organizationId, tenantId, "%", SUGGESTION_LIMIT + 1, 0);
            if (contexts.size() > SUGGESTION_LIMIT) {
                limited[0] = true;
                contexts = contexts.subList(0, SUGGESTION_LIMIT);
            }

            for (NetworkContextEntry context : contexts) {
                CIDR network = context.network();
                if (taken.add(network.toString())) {
                    suggestions.add(new Suggestion(
                            SuggestionSource.NETWORK_CONTEXT,
                            network.address().isSiteLocalAddress() ? SegmentKind.INTERNAL : SegmentKind.EXTERNAL,
                            network.toString(),
                            context.name(),
                            context.description(),
                            null,
                            null
                    ));
                }
            }

            // Only servers that the DHCP spoofing detection trusts. The subnet mask a server hands out decides
            // which network is suggested, and that must not be up to a rogue server.
            List<DhcpSpoofingServerEntry> servers = nzyme.getEthernet().spoofing()
                    .findAllDhcpServersOfTenant(handle, organizationId, tenantId).stream()
                    .filter(server -> server.trusted() && server.address() != null && server.latest() != null
                            && server.latest().subnetMask() != null)
                    .sorted(Comparator.comparing(DhcpSpoofingServerEntry::lastSeen).reversed())
                    .collect(Collectors.toList());
            if (servers.size() > SUGGESTION_LIMIT) {
                limited[0] = true;
            }

            servers.stream().limit(SUGGESTION_LIMIT).forEach(server -> {
                CIDR network = networkOf(server.address(), server.latest().subnetMask());
                if (network != null && taken.add(network.toString())) {
                    suggestions.add(new Suggestion(SuggestionSource.DHCP, SegmentKind.INTERNAL,
                            network.toString(), null, null, null, server.address()));
                }
            });

            if (taps.isEmpty()) {
                return;
            }

            // Destinations only count if they answered, so that a scan of unused addresses suggests nothing.
            handle.createQuery("SELECT network(set_masklen(a, CASE WHEN family(a) = 4 THEN 24 ELSE 64 END))::text " +
                            "AS network, COUNT(DISTINCT a) AS hosts FROM (" +
                            "SELECT source_address AS a FROM segmentation_flows " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                            "AND tap_uuid IN (<taps>) " +
                            "AND bucket >= :since AND source_internal AND source_segment_id IS NULL " +
                            "AND source_address IS NOT NULL " +
                            "UNION SELECT destination_address AS a FROM segmentation_flows " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                            "AND tap_uuid IN (<taps>) " +
                            "AND bucket >= :since AND destination_internal AND destination_segment_id IS NULL " +
                            "AND destination_address IS NOT NULL AND outcome = 'CONNECTED') AS addresses " +
                            "GROUP BY 1 ORDER BY hosts DESC LIMIT :limit")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("since", DateTime.now().minusDays(SUGGESTION_LOOKBACK_DAYS))
                    .bind("limit", SUGGESTION_LIMIT + 1)
                    .bindList("taps", taps)
                    .map((rs, ctx) -> {
                        if (rs.getRow() > SUGGESTION_LIMIT) {
                            limited[0] = true;
                            return true;
                        }

                        CIDR network = CIDR.parse(rs.getString("network"));
                        if (taken.add(network.toString())) {
                            suggestions.add(new Suggestion(SuggestionSource.OBSERVED, SegmentKind.INTERNAL,
                                    network.toString(), null, null, rs.getLong("hosts"), null));
                        }
                        return true;
                    })
                    .list();
        });

        return new Suggestions(suggestions, limited[0]);
    }

    public List<String> importNetworkContext(UUID organizationId, UUID tenantId) {
        List<NetworkContextEntry> networks = nzyme.getContextService()
                .findAllNetworkContext(organizationId, tenantId, "%", Integer.MAX_VALUE, 0);

        Set<String> names = Sets.newHashSet();
        Set<CIDR> taken = Sets.newHashSet();
        for (Segment segment : findAll(organizationId, tenantId)) {
            names.add(segment.name().toLowerCase());
            for (Segment.Member member : segment.members()) {
                if (member.network() != null) {
                    taken.add(member.network());
                }
            }
        }

        List<String> created = Lists.newArrayList();
        for (NetworkContextEntry network : networks) {
            if (network.network().prefix() == 0 || !taken.add(network.network())) {
                continue;
            }

            String name = uniqueName(Strings.isNullOrEmpty(network.name())
                    ? defaultName(network.network()) : network.name().trim(), names);

            create(
                    organizationId,
                    tenantId,
                    network.network().address().isSiteLocalAddress() ? SegmentKind.INTERNAL : SegmentKind.EXTERNAL,
                    name,
                    network.description(),
                    network.notes(),
                    null,
                    Lists.newArrayList(Segment.Member.ofNetwork(network.network()))
            );

            created.add(name);
        }

        return created;
    }

    static String defaultName(CIDR network) {
        String name = InetAddresses.toAddrString(network.address());
        while (name.endsWith(".0")) {
            name = name.substring(0, name.length() - 2);
        }

        return name;
    }

    static String uniqueName(String wanted, Set<String> taken) {
        String name = wanted.length() > NAME_MAX_LENGTH ? wanted.substring(0, NAME_MAX_LENGTH) : wanted;

        int n = 2;
        while (!taken.add(name.toLowerCase())) {
            String suffix = "-" + n++;
            String base = wanted.length() > NAME_MAX_LENGTH - suffix.length()
                    ? wanted.substring(0, NAME_MAX_LENGTH - suffix.length()) : wanted;
            name = base + suffix;
        }

        return name;
    }

    @Nullable
    static CIDR networkOf(@Nullable String address, @Nullable String subnetMask) {
        if (address == null || subnetMask == null || !InetAddresses.isInetAddress(address)
                || !InetAddresses.isInetAddress(subnetMask)) {
            return null;
        }

        byte[] a = InetAddresses.forString(address).getAddress();
        byte[] m = InetAddresses.forString(subnetMask).getAddress();
        if (a.length != m.length) {
            return null;
        }

        int prefix = 0;
        boolean ended = false;
        for (byte b : m) {
            for (int bit = 7; bit >= 0; bit--) {
                boolean set = (b & (1 << bit)) != 0;
                if (set && ended) {
                    return null;
                }

                if (set) {
                    prefix++;
                } else {
                    ended = true;
                }
            }
        }

        if (prefix == 0) {
            return null;
        }

        try {
            InetAddress network = InetAddress.getByAddress(MembershipResolver.mask(a, prefix));
            return new CIDR(network, prefix);
        } catch (Exception e) {
            return null;
        }
    }

    private void validate(Handle handle,
                          @Nullable Long id,
                          UUID organizationId,
                          UUID tenantId,
                          SegmentKind kind,
                          @Nullable String name,
                          @Nullable String description,
                          @Nullable List<UUID> tapUuids,
                          @Nullable List<Segment.Member> members) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("A name is required.");
        }

        if (name.trim().length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("The name must not be longer than " + NAME_MAX_LENGTH + " characters.");
        }

        if (description != null && description.trim().length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("The description must not be longer than " + DESCRIPTION_MAX_LENGTH
                    + " characters.");
        }

        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("At least one network is required.");
        }

        if (members.size() > MAX_MEMBERS) {
            throw new IllegalArgumentException("No more than " + MAX_MEMBERS + " networks per segment.");
        }

        if (tapUuids != null && tapUuids.isEmpty()) {
            throw new IllegalArgumentException("Select at least one tap or do not limit the segment to taps.");
        }

        Set<Segment.Member> seen = Sets.newHashSet();
        for (Segment.Member member : members) {
            if (!seen.add(member)) {
                throw new IllegalArgumentException("A network is listed more than once.");
            }

            if (member.type() == Segment.MemberType.ASN) {
                if (kind != SegmentKind.EXTERNAL) {
                    throw new IllegalArgumentException("Only external networks can be defined by autonomous system.");
                }

                if (member.asn() == null || member.asn() <= 0 || member.asn() > 4294967295L) {
                    throw new IllegalArgumentException("Invalid autonomous system number.");
                }
            } else if (member.network() == null) {
                throw new IllegalArgumentException("Invalid network.");
            } else if (member.network().prefix() == 0) {
                throw new IllegalArgumentException("A network that covers every address cannot be a segment.");
            }
        }

        for (Segment other : findAll(handle, organizationId, tenantId)) {
            if (id != null && other.id() == id) {
                continue;
            }

            if (other.name().equalsIgnoreCase(name.trim())) {
                throw new IllegalArgumentException("The name is in use already.");
            }

            if (!tapsOverlap(other.tapUuids(), tapUuids)) {
                continue;
            }

            for (Segment.Member member : members) {
                if (other.members().contains(member)) {
                    String what = member.network() != null ? member.network().toString() : "AS" + member.asn();
                    throw new IllegalArgumentException(what + " is part of \"" + other.name() + "\" already.");
                }
            }
        }
    }

    private static boolean tapsOverlap(@Nullable List<UUID> a, @Nullable List<UUID> b) {
        if (a == null || b == null) {
            // Both apply to every tap, or one applies to every tap and the other to some: Only the first is a clash.
            return a == null && b == null;
        }

        return !Collections.disjoint(a, b);
    }

    @Nullable
    private static Set<UUID> asSet(@Nullable List<UUID> uuids) {
        return uuids == null ? null : Sets.newHashSet(uuids);
    }

    @Nullable
    private static List<UUID> tapUuids(@Nullable String json) {
        List<UUID> taps = DnsTunnelJson.parseUuids(json);
        return taps.isEmpty() ? null : taps;
    }

    @Nullable
    private static String tapsJson(@Nullable List<UUID> tapUuids) {
        if (tapUuids == null) {
            return null;
        }

        List<String> taps = Lists.newArrayList();
        for (UUID tap : tapUuids) {
            taps.add(tap.toString());
        }

        return DnsTunnelJson.toJson(taps);
    }

    private static void writeMembers(Handle handle, long segmentId, List<Segment.Member> members) {
        PreparedBatch batch = handle.prepareBatch("INSERT INTO segmentation_segment_members(segment_id, " +
                "member_type, network, asn, created_at) VALUES(:segment_id, :member_type, :network::cidr, :asn, NOW())");

        for (Segment.Member member : members) {
            batch.bind("segment_id", segmentId)
                    .bind("member_type", member.type().name())
                    .bind("network", member.network() == null ? null : member.network().toString())
                    .bind("asn", member.asn())
                    .add();
        }

        batch.execute();
    }

}
