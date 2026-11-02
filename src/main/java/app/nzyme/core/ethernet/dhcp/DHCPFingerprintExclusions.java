package app.nzyme.core.ethernet.dhcp;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.dhcp.db.DHCPFingerprintExclusionEntry;
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

public class DHCPFingerprintExclusions {

    private static final Logger LOG = LogManager.getLogger(DHCPFingerprintExclusions.class);

    private static final int IMPACT_SAMPLE_SIZE = 25;

    private final NzymeNode nzyme;
    private final ObjectMapper om = JsonMapper.builder().build();

    public DHCPFingerprintExclusions(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public record Criteria(@Nullable String mac, @Nullable List<String> transactionTypes) {
        public Criteria normalized() {
            DHCPFingerprintExclusionMatcher.create(mac, transactionTypes);

            return new Criteria(
                    DHCPFingerprintExclusionMatcher.normalizeMac(mac),
                    DHCPFingerprintExclusionMatcher.normalizeTransactionTypes(transactionTypes)
            );
        }

        public DHCPFingerprintExclusionMatcher matcher() {
            return DHCPFingerprintExclusionMatcher.create(mac, transactionTypes);
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
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DHCPFingerprintExclusionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM dhcp_fingerprint_exclusions " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<DHCPFingerprintExclusionEntry> findAll(Filters filters,
                                                       OrderColumn orderColumn,
                                                       OrderDirection orderDirection,
                                                       int limit,
                                                       int offset,
                                                       UUID organizationId,
                                                       UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DHCPFingerprintExclusionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dhcp_fingerprint_exclusions " +
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
                        .mapTo(DHCPFingerprintExclusionEntry.class)
                        .list()
        );
    }

    public List<DHCPFingerprintExclusionEntry> findAllEnabled(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT * FROM dhcp_fingerprint_exclusions " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND enabled = true " +
                        "ORDER BY name ASC")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(DHCPFingerprintExclusionEntry.class)
                .list();
    }

    public Optional<DHCPFingerprintExclusionEntry> find(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dhcp_fingerprint_exclusions WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(DHCPFingerprintExclusionEntry.class)
                        .findOne()
        );
    }

    public DHCPFingerprintExclusionEntry create(UUID organizationId,
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
                handle.createQuery("INSERT INTO dhcp_fingerprint_exclusions(uuid, organization_id, tenant_id, " +
                                "name, description, enabled, mac, transaction_types, match_count, last_matched_at, " +
                                "created_by_user_id, created_by_name, created_by_email, created_at, updated_at) " +
                                "VALUES(:uuid, :organization_id, :tenant_id, :name, :description, :enabled, :mac, " +
                                ":transaction_types::jsonb, 0, NULL, :created_by_user_id, :created_by_name, " +
                                ":created_by_email, NOW(), NOW()) RETURNING *")
                        .bind("uuid", UUID.randomUUID())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("enabled", enabled)
                        .bind("mac", c.mac())
                        .bind("transaction_types", om.writeValueAsString(c.transactionTypes()))
                        .bind("created_by_user_id", createdByUserId)
                        .bind("created_by_name", createdByName)
                        .bind("created_by_email", createdByEmail)
                        .mapTo(DHCPFingerprintExclusionEntry.class)
                        .one()
        );
    }

    public void update(long id, String name, @Nullable String description, boolean enabled, Criteria criteria) {
        Criteria c = criteria.normalized();

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dhcp_fingerprint_exclusions SET name = :name, " +
                                "description = :description, enabled = :enabled, mac = :mac, " +
                                "transaction_types = :transaction_types::jsonb, updated_at = NOW() WHERE id = :id")
                        .bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("enabled", enabled)
                        .bind("mac", c.mac())
                        .bind("transaction_types", om.writeValueAsString(c.transactionTypes()))
                        .execute()
        );
    }

    public void setEnabled(long id, boolean enabled) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dhcp_fingerprint_exclusions SET enabled = :enabled, " +
                                "updated_at = NOW() WHERE id = :id")
                        .bind("id", id)
                        .bind("enabled", enabled)
                        .execute()
        );
    }

    public void delete(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dhcp_fingerprint_exclusions WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    public void recordMatch(Handle handle, long id) {
        handle.createUpdate("UPDATE dhcp_fingerprint_exclusions SET match_count = match_count + 1, " +
                        "last_matched_at = NOW() WHERE id = :id")
                .bind("id", id)
                .execute();
    }

    public Map<DHCPFingerprintExclusionEntry, DHCPFingerprintExclusionMatcher> compileEnabled(Handle handle,
                                                                                              UUID organizationId,
                                                                                              UUID tenantId) {
        Map<DHCPFingerprintExclusionEntry, DHCPFingerprintExclusionMatcher> result = new LinkedHashMap<>();
        for (DHCPFingerprintExclusionEntry entry : findAllEnabled(handle, organizationId, tenantId)) {
            try {
                result.put(entry, DHCPFingerprintExclusionMatcher.create(entry));
            } catch (IllegalArgumentException e) {
                LOG.error("DHCP fingerprint exclusion [{}] of tenant [{}/{}] has invalid criteria and is " +
                        "ignored: {}", entry.uuid(), organizationId, tenantId, e.getMessage());
            }
        }

        return result;
    }

    public Map<DHCPFingerprintExclusionEntry, DHCPFingerprintExclusionMatcher> compileEnabled(UUID organizationId,
                                                                                              UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle -> compileEnabled(handle, organizationId, tenantId));
    }

    @Nullable
    public static DHCPFingerprintExclusionEntry findMatching(Map<DHCPFingerprintExclusionEntry, DHCPFingerprintExclusionMatcher> compiled,
                                                             String clientMac,
                                                             String transactionType) {
        for (Map.Entry<DHCPFingerprintExclusionEntry, DHCPFingerprintExclusionMatcher> e : compiled.entrySet()) {
            if (e.getValue().matches(clientMac, transactionType)) {
                return e.getKey();
            }
        }

        return null;
    }

    public List<DHCPFingerprintExclusionEntry> findAllMatchingMac(Map<DHCPFingerprintExclusionEntry, DHCPFingerprintExclusionMatcher> compiled,
                                                                  String clientMac) {
        List<DHCPFingerprintExclusionEntry> result = Lists.newArrayList();
        for (Map.Entry<DHCPFingerprintExclusionEntry, DHCPFingerprintExclusionMatcher> e : compiled.entrySet()) {
            if (e.getValue().matchesMac(clientMac)) {
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

    public Impact calculateImpact(DHCPFingerprintExclusionMatcher matcher, UUID organizationId, UUID tenantId) {
        String macCondition = matcher.isPrefix() ? "mac LIKE :mac_prefix" : "mac = :mac";

        return nzyme.getDatabase().withHandle(handle -> {
            List<MatchedAsset> matched = handle.createQuery("SELECT uuid, mac FROM assets " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                            "AND " + macCondition + " ORDER BY mac ASC")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("mac", matcher.mac())
                    .bind("mac_prefix", matcher.mac() + ":%")
                    .map((rs, ctx) -> new MatchedAsset(UUID.fromString(rs.getString("uuid")), rs.getString("mac")))
                    .list();

            if (matched.isEmpty()) {
                return Impact.empty();
            }

            List<MatchedAsset> sample = matched.size() > IMPACT_SAMPLE_SIZE
                    ? matched.subList(0, IMPACT_SAMPLE_SIZE) : matched;

            return new Impact(matched.size(), Lists.newArrayList(sample));
        });
    }

}
