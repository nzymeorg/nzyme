package app.nzyme.core.assets.tags;

import app.nzyme.core.assets.profile.AssetAttributeDimension;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.segmentation.BulkResult;
import app.nzyme.core.ethernet.segmentation.BulkSelection;
import app.nzyme.core.ethernet.segmentation.MembershipResolver;
import app.nzyme.core.ethernet.segmentation.Policies;
import app.nzyme.core.ethernet.segmentation.Policy;
import app.nzyme.core.ethernet.segmentation.PolicyRule;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import com.google.common.base.Strings;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class AssetTags {

    public static final int NAME_MAX_LENGTH = 24;
    public static final int DESCRIPTION_MAX_LENGTH = 64;

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");

    private static final int SUGGESTION_LOOKBACK_DAYS = 30;
    private static final int SUGGESTION_MINIMUM_SESSIONS = 5;
    private static final int SUGGESTION_SERVED_PORTS_LIMIT = 500;

    private static final int BULK_DELETE_CHUNK = 5000;

    private static final Map<String, String> SUGGESTED_TAGS = Maps.newLinkedHashMap();

    static {
        for (String port : List.of("TCP/5432", "TCP/3306", "TCP/1433", "TCP/1521", "TCP/27017", "TCP/6379", "TCP/9042")) {
            SUGGESTED_TAGS.put(port, "db-servers");
        }
        for (String port : List.of("TCP/80", "TCP/443", "TCP/8080", "TCP/8443")) {
            SUGGESTED_TAGS.put(port, "web-servers");
        }
        for (String port : List.of("TCP/445", "TCP/139", "TCP/2049")) {
            SUGGESTED_TAGS.put(port, "file-servers");
        }
        for (String port : List.of("TCP/25", "TCP/587", "TCP/143", "TCP/993")) {
            SUGGESTED_TAGS.put(port, "mail-servers");
        }
        for (String port : List.of("TCP/389", "TCP/636", "TCP/88")) {
            SUGGESTED_TAGS.put(port, "directory-servers");
        }
        for (String port : List.of("TCP/9100", "TCP/631", "TCP/515")) {
            SUGGESTED_TAGS.put(port, "printers");
        }
        for (String port : List.of("TCP/3389", "TCP/5900")) {
            SUGGESTED_TAGS.put(port, "remote-desktop");
        }
        SUGGESTED_TAGS.put("TCP/22", "ssh-servers");
        SUGGESTED_TAGS.put("UDP/53", "dns-servers");
        SUGGESTED_TAGS.put("TCP/53", "dns-servers");
    }

    public enum OrderColumn {

        NAME("lower(t.name)"),
        DESCRIPTION("lower(t.description)"),
        ASSET_COUNT("asset_count"),
        CREATED_AT("t.created_at"),
        UPDATED_AT("t.updated_at");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public record TaggedAssetReference(UUID uuid, String mac, List<String> hostnames, List<String> addresses) { }

    public record TagReference(UUID uuid, String name, @Nullable String description) { }

    public record Suggestion(String tag, UUID assetUuid, String mac, List<String> ports, long sessions) { }

    public record Suggestions(List<Suggestion> suggestions, int limit, boolean truncated) { }

    private record Target(long id, UUID uuid, String name) { }

    private record ServedPort(long assetId, UUID assetUuid, String mac, String port, long sessions) { }

    private final NzymeNode nzyme;

    public AssetTags(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    private static final String SELECT_TAGS = "SELECT t.*, " + AssetTagFilters.ASSET_COUNT + " AS asset_count " +
            "FROM assets_tags AS t ";

    public long count(Filters filters, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new AssetTagFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM assets_tags AS t " +
                                "WHERE t.organization_id = :organization_id AND t.tenant_id = :tenant_id " +
                                filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<AssetTag> findAll(Filters filters,
                                  OrderColumn orderColumn,
                                  OrderDirection orderDirection,
                                  int limit,
                                  int offset,
                                  UUID organizationId,
                                  UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new AssetTagFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SELECT_TAGS + "WHERE t.organization_id = :organization_id " +
                                "AND t.tenant_id = :tenant_id " + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, lower(t.name) ASC, t.id ASC " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .map((rs, ctx) -> map(rs))
                        .list()
        );
    }

    public List<AssetTag> findAll(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SELECT_TAGS + "WHERE t.organization_id = :organization_id " +
                                "AND t.tenant_id = :tenant_id ORDER BY lower(t.name) ASC")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .map((rs, ctx) -> map(rs))
                        .list()
        );
    }

    public Optional<AssetTag> find(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SELECT_TAGS + "WHERE t.uuid = :uuid AND t.organization_id = :organization_id " +
                                "AND t.tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .map((rs, ctx) -> map(rs))
                        .findOne()
        );
    }

    public Optional<AssetTag> findByName(String name, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SELECT_TAGS + "WHERE lower(t.name) = lower(:name) " +
                                "AND t.organization_id = :organization_id AND t.tenant_id = :tenant_id")
                        .bind("name", name)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .map((rs, ctx) -> map(rs))
                        .findOne()
        );
    }

    public AssetTag create(UUID organizationId, UUID tenantId, String name, @Nullable String description) {
        validate(null, organizationId, tenantId, name, description);

        UUID uuid = UUID.randomUUID();
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO assets_tags(uuid, organization_id, tenant_id, name, description, " +
                                "created_at, updated_at) VALUES(:uuid, :organization_id, :tenant_id, :name, " +
                                ":description, NOW(), NOW())")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("name", name.trim())
                        .bind("description", Strings.emptyToNull(Strings.nullToEmpty(description).trim()))
                        .execute()
        );

        return find(uuid, organizationId, tenantId).orElseThrow();
    }

    public void update(AssetTag tag, String name, @Nullable String description) {
        validate(tag.id(), tag.organizationId(), tag.tenantId(), name, description);

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE assets_tags SET name = :name, description = :description, " +
                                "updated_at = NOW() WHERE id = :id")
                        .bind("id", tag.id())
                        .bind("name", name.trim())
                        .bind("description", Strings.emptyToNull(Strings.nullToEmpty(description).trim()))
                        .execute()
        );
    }

    public void delete(AssetTag tag) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM assets_tags WHERE id = :id")
                        .bind("id", tag.id())
                        .execute()
        );
    }

    public static List<String> referencingPolicies(UUID tag, Collection<Policy> policies) {
        List<String> names = Lists.newArrayList();
        for (Policy policy : policies) {
            if (Policies.references(policy, PolicyRule.SelectorType.TAG, tag.toString())) {
                names.add(policy.name());
            }
        }

        return names;
    }

    public static String inUseReason(List<String> policies) {
        return "In use by segmentation " + (policies.size() == 1 ? "policy " : "policies ")
                + "\"" + String.join("\", \"", policies) + "\".";
    }

    public BulkResult delete(BulkSelection selection, Collection<Policy> policies, UUID organizationId, UUID tenantId) {
        if (selection.isEmpty()) {
            return BulkResult.of(0);
        }

        return nzyme.getDatabase().inTransaction(handle -> {
            List<Target> targets;
            if (selection.isChecked()) {
                targets = handle.createQuery("SELECT t.id, t.uuid, t.name FROM assets_tags AS t " +
                                "WHERE t.organization_id = :organization_id AND t.tenant_id = :tenant_id " +
                                "AND t.uuid IN (<uuids>) ORDER BY lower(t.name) ASC")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindList("uuids", selection.uuids())
                        .map((rs, ctx) -> new Target(rs.getLong("id"), UUID.fromString(rs.getString("uuid")),
                                rs.getString("name")))
                        .list();
            } else {
                FilterSqlFragment filterFragment = FilterSql.generate(selection.filters(), new AssetTagFilters());
                targets = handle.createQuery("SELECT t.id, t.uuid, t.name FROM assets_tags AS t " +
                                "WHERE t.organization_id = :organization_id AND t.tenant_id = :tenant_id " +
                                filterFragment.whereSql() + "ORDER BY lower(t.name) ASC")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .map((rs, ctx) -> new Target(rs.getLong("id"), UUID.fromString(rs.getString("uuid")),
                                rs.getString("name")))
                        .list();
            }

            List<Long> free = Lists.newArrayList();
            List<BulkResult.Skipped> skipped = Lists.newArrayList();
            for (Target target : targets) {
                List<String> referencing = referencingPolicies(target.uuid(), policies);
                if (referencing.isEmpty()) {
                    free.add(target.id());
                } else {
                    skipped.add(new BulkResult.Skipped(target.uuid(), target.name(), inUseReason(referencing)));
                }
            }

            long deleted = 0;
            for (List<Long> ids : Lists.partition(free, BULK_DELETE_CHUNK)) {
                deleted += handle.createUpdate("DELETE FROM assets_tags WHERE id IN (<ids>)")
                        .bindList("ids", ids)
                        .execute();
            }

            return new BulkResult(deleted, skipped);
        });
    }

    public void assign(AssetTag tag, long assetId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO assets_tag_assignments(tag_id, asset_id, created_at) " +
                                "VALUES(:tag_id, :asset_id, NOW()) ON CONFLICT (tag_id, asset_id) DO NOTHING")
                        .bind("tag_id", tag.id())
                        .bind("asset_id", assetId)
                        .execute()
        );
    }

    public void unassign(AssetTag tag, long assetId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM assets_tag_assignments WHERE tag_id = :tag_id AND asset_id = :asset_id")
                        .bind("tag_id", tag.id())
                        .bind("asset_id", assetId)
                        .execute()
        );
    }

    public int unassign(AssetTag tag, Collection<UUID> assets) {
        if (assets.isEmpty()) {
            return 0;
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate("DELETE FROM assets_tag_assignments WHERE tag_id = :tag_id " +
                                "AND asset_id IN (SELECT a.id FROM assets AS a WHERE a.uuid IN (<assets>) " +
                                "AND a.organization_id = :organization_id AND a.tenant_id = :tenant_id)")
                        .bind("tag_id", tag.id())
                        .bind("organization_id", tag.organizationId())
                        .bind("tenant_id", tag.tenantId())
                        .bindList("assets", assets)
                        .execute()
        );
    }

    public int unassignAll(AssetTag tag) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate("DELETE FROM assets_tag_assignments WHERE tag_id = :tag_id")
                        .bind("tag_id", tag.id())
                        .execute()
        );
    }

    public List<AssetTag> findTagsOfAsset(long assetId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SELECT_TAGS + "JOIN assets_tag_assignments AS asg ON asg.tag_id = t.id " +
                                "WHERE asg.asset_id = :asset_id ORDER BY lower(t.name) ASC")
                        .bind("asset_id", assetId)
                        .map((rs, ctx) -> map(rs))
                        .list()
        );
    }

    public Map<Long, List<TagReference>> findTagsOfAssets(Collection<Long> assetIds) {
        Map<Long, List<TagReference>> result = Maps.newHashMap();
        if (assetIds.isEmpty()) {
            return result;
        }

        nzyme.getDatabase().useHandle(handle ->
                handle.createQuery("SELECT asg.asset_id, t.uuid, t.name, t.description " +
                                "FROM assets_tag_assignments AS asg " +
                                "JOIN assets_tags AS t ON t.id = asg.tag_id WHERE asg.asset_id IN (<assets>) " +
                                "ORDER BY lower(t.name) ASC")
                        .bindList("assets", assetIds)
                        .map((rs, ctx) -> result.computeIfAbsent(rs.getLong("asset_id"), k -> Lists.newArrayList())
                                .add(new TagReference(UUID.fromString(rs.getString("uuid")), rs.getString("name"),
                                        rs.getString("description"))))
                        .list()
        );

        return result;
    }

    public List<TaggedAssetReference> findAssetsOfTag(AssetTag tag, int limit, int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT p.uuid, p.mac, COALESCE(array_agg(DISTINCT h.hostname) " +
                                "FILTER (WHERE h.hostname IS NOT NULL), '{}') AS hostnames, " +
                                "COALESCE(array_agg(DISTINCT host(i.address)) " +
                                "FILTER (WHERE i.address IS NOT NULL), '{}') AS ip_addresses " +
                                "FROM (SELECT a.id, a.uuid, a.mac, a.last_seen FROM assets AS a " +
                                "JOIN assets_tag_assignments AS asg ON asg.asset_id = a.id " +
                                "WHERE asg.tag_id = :tag_id ORDER BY a.last_seen DESC, a.id ASC " +
                                "LIMIT :limit OFFSET :offset) AS p " +
                                "LEFT JOIN assets_hostnames AS h ON h.asset_id = p.id " +
                                "LEFT JOIN assets_ip_addresses AS i ON i.asset_id = p.id " +
                                "GROUP BY p.id, p.uuid, p.mac, p.last_seen ORDER BY p.last_seen DESC, p.id ASC")
                        .bind("tag_id", tag.id())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> new TaggedAssetReference(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("mac"),
                                Arrays.asList((String[]) rs.getArray("hostnames").getArray()),
                                Arrays.asList((String[]) rs.getArray("ip_addresses").getArray())
                        ))
                        .list()
        );
    }

    // An address that several assets used belongs to the one that used it last. If that is none of the assets asked
    // for, the address is left out.
    public Map<InetAddress, MembershipResolver.TaggedAsset> resolveAddresses(Handle handle,
                                                                             UUID organizationId,
                                                                             UUID tenantId,
                                                                             Set<UUID> additionalAssets) {
        Map<Long, Set<UUID>> tagsOfAssets = Maps.newHashMap();
        handle.createQuery("SELECT asg.asset_id, t.uuid FROM assets_tag_assignments AS asg " +
                        "JOIN assets_tags AS t ON t.id = asg.tag_id " +
                        "WHERE t.organization_id = :organization_id AND t.tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .map((rs, ctx) -> tagsOfAssets.computeIfAbsent(rs.getLong("asset_id"), k -> Sets.newHashSet())
                        .add(UUID.fromString(rs.getString("uuid"))))
                .list();

        Map<InetAddress, MembershipResolver.TaggedAsset> result = Maps.newHashMap();
        if (tagsOfAssets.isEmpty() && additionalAssets.isEmpty()) {
            return result;
        }

        // One array instead of a list of binds: policies can name more assets than a statement takes parameters.
        String additional = additionalAssets.isEmpty() ? "" : "OR a.uuid = ANY(:additional::uuid[]) ";
        var query = handle.createQuery("WITH relevant AS (" +
                        "SELECT a.id, a.uuid FROM assets AS a WHERE a.organization_id = :organization_id " +
                        "AND a.tenant_id = :tenant_id AND (a.id IN (SELECT asg.asset_id " +
                        "FROM assets_tag_assignments AS asg JOIN assets_tags AS t ON t.id = asg.tag_id " +
                        "WHERE t.organization_id = :organization_id AND t.tenant_id = :tenant_id) " + additional + ")), " +
                        "owners AS (SELECT DISTINCT ON (ip.address) ip.address, ip.asset_id " +
                        "FROM assets_ip_addresses AS ip JOIN assets AS a ON a.id = ip.asset_id " +
                        "WHERE a.organization_id = :organization_id AND a.tenant_id = :tenant_id " +
                        "AND ip.address IN (SELECT r.address FROM assets_ip_addresses AS r " +
                        "WHERE r.asset_id IN (SELECT id FROM relevant)) " +
                        "ORDER BY ip.address, ip.last_seen DESC) " +
                        "SELECT host(o.address) AS address, r.id AS asset_id, r.uuid AS asset_uuid " +
                        "FROM owners AS o JOIN relevant AS r ON r.id = o.asset_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId);

        if (!additionalAssets.isEmpty()) {
            query = query.bind("additional", "{" + Joiner.on(",").join(additionalAssets) + "}");
        }

        query.map((rs, ctx) -> {
            Set<UUID> tags = tagsOfAssets.getOrDefault(rs.getLong("asset_id"), Collections.emptySet());
            result.put(
                    InetAddresses.forString(rs.getString("address")),
                    new MembershipResolver.TaggedAsset(UUID.fromString(rs.getString("asset_uuid")), tags)
            );
            return true;
        }).list();

        return result;
    }

    public Suggestions suggestions(UUID organizationId, UUID tenantId) {
        List<ServedPort> served = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT a.id, a.uuid, a.mac, at.value, at.count FROM assets_attributes AS at " +
                                "JOIN assets AS a ON a.organization_id = at.organization_id " +
                                "AND a.tenant_id = at.tenant_id AND a.mac = at.mac " +
                                "WHERE at.organization_id = :organization_id AND at.tenant_id = :tenant_id " +
                                "AND at.dimension = :dimension AND at.value IN (<ports>) " +
                                "AND at.count >= :minimum_sessions AND at.last_seen >= :since " +
                                "ORDER BY at.count DESC LIMIT :limit")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("dimension", AssetAttributeDimension.INBOUND_PORT.name())
                        .bindList("ports", SUGGESTED_TAGS.keySet())
                        .bind("minimum_sessions", SUGGESTION_MINIMUM_SESSIONS)
                        .bind("since", DateTime.now().minusDays(SUGGESTION_LOOKBACK_DAYS))
                        .bind("limit", SUGGESTION_SERVED_PORTS_LIMIT + 1)
                        .map((rs, ctx) -> new ServedPort(rs.getLong("id"), UUID.fromString(rs.getString("uuid")),
                                rs.getString("mac"), rs.getString("value"), rs.getLong("count")))
                        .list()
        );

        boolean truncated = served.size() > SUGGESTION_SERVED_PORTS_LIMIT;

        Map<String, Suggestion> suggestions = Maps.newLinkedHashMap();
        for (ServedPort row : truncated ? served.subList(0, SUGGESTION_SERVED_PORTS_LIMIT) : served) {
            String tag = SUGGESTED_TAGS.get(row.port());
            String key = tag + "|" + row.assetId();
            Suggestion existing = suggestions.get(key);

            List<String> ports = existing == null ? Lists.newArrayList() : existing.ports();
            ports.add(row.port());

            suggestions.put(key, new Suggestion(
                    tag,
                    row.assetUuid(),
                    row.mac(),
                    ports,
                    (existing == null ? 0 : existing.sessions()) + row.sessions()
            ));
        }

        if (suggestions.isEmpty()) {
            return new Suggestions(Lists.newArrayList(), SUGGESTION_SERVED_PORTS_LIMIT, truncated);
        }

        Set<String> assigned = Sets.newHashSet();
        nzyme.getDatabase().useHandle(handle ->
                handle.createQuery("SELECT lower(t.name) AS name, a.uuid FROM assets_tag_assignments AS asg " +
                                "JOIN assets_tags AS t ON t.id = asg.tag_id JOIN assets AS a ON a.id = asg.asset_id " +
                                "WHERE t.organization_id = :organization_id AND t.tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .map((rs, ctx) -> assigned.add(rs.getString("name") + "|" + rs.getString("uuid")))
                        .list()
        );

        List<Suggestion> result = Lists.newArrayList();
        for (Suggestion suggestion : suggestions.values()) {
            if (!assigned.contains(suggestion.tag() + "|" + suggestion.assetUuid())) {
                result.add(suggestion);
            }
        }

        return new Suggestions(result, SUGGESTION_SERVED_PORTS_LIMIT, truncated);
    }

    private void validate(@Nullable Long id, UUID organizationId, UUID tenantId, @Nullable String name, @Nullable String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("A name is required.");
        }

        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("The name must not be longer than " + NAME_MAX_LENGTH + " characters.");
        }

        if (!NAME.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("The name can contain letters, digits, dashes, underscores and dots.");
        }

        if (description != null && description.trim().length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("The description must not be longer than " + DESCRIPTION_MAX_LENGTH
                    + " characters.");
        }

        Optional<AssetTag> existing = findByName(trimmed, organizationId, tenantId);
        if (existing.isPresent() && (id == null || existing.get().id() != id)) {
            throw new IllegalArgumentException("The name is in use already.");
        }
    }

    private static AssetTag map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AssetTag(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("name"),
                rs.getString("description"),
                rs.getLong("asset_count"),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
