package app.nzyme.core.assets.identity;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.identity.db.AssetIdentityExclusionEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

public class AssetIdentityExclusions {

    private static final Logger LOG = LogManager.getLogger(AssetIdentityExclusions.class);

    private static final int IMPACT_SAMPLE_SIZE = 25;

    private final NzymeNode nzyme;
    private final ObjectMapper om = JsonMapper.builder().build();

    public AssetIdentityExclusions(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public record Criteria(@Nullable String mac, @Nullable List<String> types) {
        public Criteria normalized() {
            AssetIdentityExclusionMatcher.create(mac, types);

            return new Criteria(
                    AssetIdentityExclusionMatcher.normalizeMac(mac),
                    AssetIdentityExclusionMatcher.normalizeTypes(types)
            );
        }

        public AssetIdentityExclusionMatcher matcher() {
            return AssetIdentityExclusionMatcher.create(mac, types);
        }
    }

    public enum OrderColumn {

        NAME("name"),
        ENABLED("enabled"),
        MAC("mac"),
        LAST_MATCHED_AT("last_matched_at"),
        MATCH_COUNT("match_count"),
        CREATED_AT("created_at"),
        UPDATED_AT("updated_at");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public long count(Filters filters, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new AssetIdentityExclusionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM assets_identity_exclusions " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<AssetIdentityExclusionEntry> findAll(Filters filters,
                                                     OrderColumn orderColumn,
                                                     OrderDirection orderDirection,
                                                     int limit,
                                                     int offset,
                                                     UUID organizationId,
                                                     UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new AssetIdentityExclusionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM assets_identity_exclusions " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, name ASC " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(AssetIdentityExclusionEntry.class)
                        .list()
        );
    }

    public List<AssetIdentityExclusionEntry> findAllEnabled(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT * FROM assets_identity_exclusions " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND enabled = true " +
                        "ORDER BY name ASC")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(AssetIdentityExclusionEntry.class)
                .list();
    }

    public Optional<AssetIdentityExclusionEntry> find(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM assets_identity_exclusions WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(AssetIdentityExclusionEntry.class)
                        .findOne()
        );
    }

    public AssetIdentityExclusionEntry create(UUID organizationId,
                                              UUID tenantId,
                                              String name,
                                              @Nullable String description,
                                              boolean enabled,
                                              Criteria criteria,
                                              @Nullable UUID createdByUserId,
                                              @Nullable String createdByName,
                                              @Nullable String createdByEmail) {
        Criteria c = criteria.normalized();

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("INSERT INTO assets_identity_exclusions(uuid, organization_id, tenant_id, " +
                                "name, description, enabled, mac, types, match_count, last_matched_at, " +
                                "created_by_user_id, created_by_name, created_by_email, created_at, updated_at) " +
                                "VALUES(:uuid, :organization_id, :tenant_id, :name, :description, :enabled, :mac, " +
                                ":types::jsonb, 0, NULL, :created_by_user_id, :created_by_name, " +
                                ":created_by_email, NOW(), NOW()) RETURNING *")
                        .bind("uuid", UUID.randomUUID())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("enabled", enabled)
                        .bind("mac", c.mac())
                        .bind("types", om.writeValueAsString(c.types()))
                        .bind("created_by_user_id", createdByUserId)
                        .bind("created_by_name", createdByName)
                        .bind("created_by_email", createdByEmail)
                        .mapTo(AssetIdentityExclusionEntry.class)
                        .one()
        );
    }

    public void update(long id, String name, @Nullable String description, boolean enabled, Criteria criteria) {
        Criteria c = criteria.normalized();

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE assets_identity_exclusions SET name = :name, " +
                                "description = :description, enabled = :enabled, mac = :mac, " +
                                "types = :types::jsonb, updated_at = NOW() WHERE id = :id")
                        .bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("enabled", enabled)
                        .bind("mac", c.mac())
                        .bind("types", om.writeValueAsString(c.types()))
                        .execute()
        );
    }

    public void setEnabled(long id, boolean enabled) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE assets_identity_exclusions SET enabled = :enabled, " +
                                "updated_at = NOW() WHERE id = :id")
                        .bind("id", id)
                        .bind("enabled", enabled)
                        .execute()
        );
    }

    public void delete(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM assets_identity_exclusions WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    public void recordMatches(Handle handle, long id, long count) {
        handle.createUpdate("UPDATE assets_identity_exclusions SET match_count = match_count + :count, " +
                        "last_matched_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("count", count)
                .execute();
    }

    public Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> compileEnabled(Handle handle,
                                                                                          UUID organizationId,
                                                                                          UUID tenantId) {
        Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> result = new LinkedHashMap<>();
        for (AssetIdentityExclusionEntry entry : findAllEnabled(handle, organizationId, tenantId)) {
            try {
                result.put(entry, AssetIdentityExclusionMatcher.create(entry));
            } catch (IllegalArgumentException e) {
                LOG.error("Asset identity exclusion [{}] of tenant [{}/{}] has invalid criteria and is " +
                        "ignored: {}", entry.uuid(), organizationId, tenantId, e.getMessage());
            }
        }

        return result;
    }

    public Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> compileEnabled(UUID organizationId,
                                                                                          UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle -> compileEnabled(handle, organizationId, tenantId));
    }

    @Nullable
    public static AssetIdentityExclusionEntry findMatching(Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> compiled,
                                                           String mac,
                                                           AssetIdentityType type) {
        for (Map.Entry<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> e : compiled.entrySet()) {
            if (e.getValue().matches(mac, type)) {
                return e.getKey();
            }
        }

        return null;
    }

    public static List<AssetIdentityExclusionEntry> findAllMatchingMac(Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> compiled,
                                                                       String mac) {
        List<AssetIdentityExclusionEntry> result = Lists.newArrayList();
        for (Map.Entry<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> e : compiled.entrySet()) {
            if (e.getValue().matchesMac(mac)) {
                result.add(e.getKey());
            }
        }

        return result;
    }

    public record MatchedAsset(UUID uuid, String mac) { }

    public record Impact(int matchingAssets, List<MatchedAsset> assets) {
        public static Impact empty() {
            return new Impact(0, Collections.emptyList());
        }
    }

    public Impact calculateImpact(AssetIdentityExclusionMatcher matcher, UUID organizationId, UUID tenantId) {
        String macCondition = matcher.isPrefix() ? "mac LIKE :mac_prefix" : "mac = :mac";

        return nzyme.getDatabase().withHandle(handle -> {
            long total = handle.createQuery("SELECT COUNT(*) FROM assets " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND " + macCondition)
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("mac", matcher.mac())
                    .bind("mac_prefix", matcher.mac() + ":%")
                    .mapTo(Long.class)
                    .one();

            if (total == 0) {
                return Impact.empty();
            }

            List<MatchedAsset> sample = handle.createQuery("SELECT uuid, mac FROM assets " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                            "AND " + macCondition + " ORDER BY mac ASC LIMIT :limit")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("mac", matcher.mac())
                    .bind("mac_prefix", matcher.mac() + ":%")
                    .bind("limit", IMPACT_SAMPLE_SIZE)
                    .map((rs, ctx) -> new MatchedAsset(UUID.fromString(rs.getString("uuid")), rs.getString("mac")))
                    .list();

            return new Impact((int) Math.min(total, Integer.MAX_VALUE), sample);
        });
    }

}
