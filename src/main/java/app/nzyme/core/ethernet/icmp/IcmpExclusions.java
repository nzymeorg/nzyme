package app.nzyme.core.ethernet.icmp;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.icmp.db.IcmpExclusionEntry;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;

import java.util.*;

public class IcmpExclusions {

    private static final Logger LOG = LogManager.getLogger(IcmpExclusions.class);

    private final NzymeNode nzyme;

    public IcmpExclusions(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public record Criteria(@Nullable List<String> detectionTypes,
                           @Nullable String sourceCidr,
                           @Nullable String sourceMac,
                           @Nullable String gatewayCidr) {
        public Criteria normalized() {
            IcmpExclusionMatcher.create(detectionTypes, sourceCidr, sourceMac, gatewayCidr);

            List<String> types = null;
            Set<IcmpDetectionType> parsed = IcmpExclusionMatcher.parseTypes(detectionTypes);
            if (parsed != null) {
                types = Lists.newArrayList();
                for (IcmpDetectionType t : IcmpDetectionType.values()) {
                    if (parsed.contains(t)) {
                        types.add(t.name());
                    }
                }
            }

            return new Criteria(
                    types,
                    IcmpExclusionMatcher.normalizeCidr(sourceCidr),
                    IcmpExclusionMatcher.normalizeMac(sourceMac),
                    IcmpExclusionMatcher.normalizeCidr(gatewayCidr)
            );
        }
    }

    public enum OrderColumn {

        NAME("name"),
        ENABLED("enabled"),
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
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpExclusionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM icmp_exclusions WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id " + filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<IcmpExclusionEntry> findAll(Filters filters,
                                            OrderColumn orderColumn,
                                            OrderDirection orderDirection,
                                            int limit,
                                            int offset,
                                            UUID organizationId,
                                            UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IcmpExclusionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM icmp_exclusions WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id " + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, name ASC " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(IcmpExclusionEntry.class)
                        .list()
        );
    }

    public List<IcmpExclusionEntry> findAllEnabled(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT * FROM icmp_exclusions WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND enabled = true ORDER BY name ASC")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(IcmpExclusionEntry.class)
                .list();
    }

    public Optional<IcmpExclusionEntry> find(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM icmp_exclusions WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(IcmpExclusionEntry.class)
                        .findOne()
        );
    }

    public IcmpExclusionEntry create(UUID organizationId,
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
                handle.createQuery("INSERT INTO icmp_exclusions(uuid, organization_id, tenant_id, name, " +
                                "description, enabled, detection_types, source_cidr, source_mac, gateway_cidr, " +
                                "match_count, last_matched_at, created_by_user_id, created_by_name, " +
                                "created_by_email, created_at, updated_at) VALUES(:uuid, :organization_id, " +
                                ":tenant_id, :name, :description, :enabled, :detection_types, :source_cidr, " +
                                ":source_mac, :gateway_cidr, 0, NULL, :created_by_user_id, :created_by_name, " +
                                ":created_by_email, NOW(), NOW()) RETURNING *")
                        .bind("uuid", UUID.randomUUID())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("enabled", enabled)
                        .bind("detection_types", c.detectionTypes() == null ? null : String.join(",", c.detectionTypes()))
                        .bind("source_cidr", c.sourceCidr())
                        .bind("source_mac", c.sourceMac())
                        .bind("gateway_cidr", c.gatewayCidr())
                        .bind("created_by_user_id", createdByUserId)
                        .bind("created_by_name", createdByName)
                        .bind("created_by_email", createdByEmail)
                        .mapTo(IcmpExclusionEntry.class)
                        .one()
        );
    }

    public void update(long id, String name, @Nullable String description, boolean enabled, Criteria criteria) {
        Criteria c = criteria.normalized();

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE icmp_exclusions SET name = :name, description = :description, " +
                                "enabled = :enabled, detection_types = :detection_types, source_cidr = :source_cidr, " +
                                "source_mac = :source_mac, gateway_cidr = :gateway_cidr, updated_at = NOW() " +
                                "WHERE id = :id")
                        .bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("enabled", enabled)
                        .bind("detection_types", c.detectionTypes() == null ? null : String.join(",", c.detectionTypes()))
                        .bind("source_cidr", c.sourceCidr())
                        .bind("source_mac", c.sourceMac())
                        .bind("gateway_cidr", c.gatewayCidr())
                        .execute()
        );
    }

    public void setEnabled(long id, boolean enabled) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE icmp_exclusions SET enabled = :enabled, updated_at = NOW() WHERE id = :id")
                        .bind("id", id)
                        .bind("enabled", enabled)
                        .execute()
        );
    }

    public void delete(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM icmp_exclusions WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    public void recordMatches(Handle handle, long id, long count) {
        handle.createUpdate("UPDATE icmp_exclusions SET match_count = match_count + :count, " +
                        "last_matched_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("count", count)
                .execute();
    }

    public Map<IcmpExclusionEntry, IcmpExclusionMatcher> compileEnabled(Handle handle,
                                                                        UUID organizationId,
                                                                        UUID tenantId) {
        Map<IcmpExclusionEntry, IcmpExclusionMatcher> result = new LinkedHashMap<>();
        for (IcmpExclusionEntry entry : findAllEnabled(handle, organizationId, tenantId)) {
            try {
                result.put(entry, IcmpExclusionMatcher.create(entry));
            } catch (IllegalArgumentException e) {
                LOG.error("ICMP exclusion [{}] of tenant [{}/{}] has invalid criteria and is ignored: {}",
                        entry.uuid(), organizationId, tenantId, e.getMessage());
            }
        }

        return result;
    }

    @Nullable
    public IcmpExclusionEntry findMatching(Map<IcmpExclusionEntry, IcmpExclusionMatcher> exclusions,
                                           IcmpDetectionType type,
                                           @Nullable String sourceAddress,
                                           @Nullable String sourceMac,
                                           @Nullable String gatewayAddress) {
        for (Map.Entry<IcmpExclusionEntry, IcmpExclusionMatcher> e : exclusions.entrySet()) {
            if (e.getValue().matches(type, sourceAddress, sourceMac, gatewayAddress)) {
                return e.getKey();
            }
        }

        return null;
    }

}
