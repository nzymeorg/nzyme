package app.nzyme.core.ethernet.dns.tunnels;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.dns.tunnels.db.DnsTunnelExclusionEntry;
import app.nzyme.core.ethernet.dns.tunnels.db.DnsTunnelFindingEntry;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;

import java.util.*;

public class DnsTunnelExclusions {

    private static final Logger LOG = LogManager.getLogger(DnsTunnelExclusions.class);

    private static final int IMPACT_SAMPLE_SIZE = 25;

    private final NzymeNode nzyme;
    private final DnsTunnels tunnels;

    public DnsTunnelExclusions(NzymeNode nzyme, DnsTunnels tunnels) {
        this.nzyme = nzyme;
        this.tunnels = tunnels;
    }

    public record Criteria(@Nullable String domain, @Nullable String clientAddressCidr) {
        public Criteria normalized() {
            DnsTunnelExclusionMatcher.create(domain, clientAddressCidr);

            return new Criteria(
                    DnsTunnelExclusionMatcher.normalizeDomain(domain),
                    DnsTunnelExclusionMatcher.normalizeCidr(clientAddressCidr)
            );
        }

        public DnsTunnelExclusionMatcher matcher() {
            return DnsTunnelExclusionMatcher.create(domain, clientAddressCidr);
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
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DnsTunnelExclusionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM dns_tunnel_exclusions " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<DnsTunnelExclusionEntry> findAll(Filters filters,
                                                 OrderColumn orderColumn,
                                                 OrderDirection orderDirection,
                                                 int limit,
                                                 int offset,
                                                 UUID organizationId,
                                                 UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DnsTunnelExclusionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dns_tunnel_exclusions " +
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
                        .mapTo(DnsTunnelExclusionEntry.class)
                        .list()
        );
    }

    public List<DnsTunnelExclusionEntry> findAllEnabled(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT * FROM dns_tunnel_exclusions WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND enabled = true ORDER BY name ASC")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(DnsTunnelExclusionEntry.class)
                .list();
    }

    public Optional<DnsTunnelExclusionEntry> find(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dns_tunnel_exclusions WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(DnsTunnelExclusionEntry.class)
                        .findOne()
        );
    }

    public DnsTunnelExclusionEntry create(UUID organizationId,
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
                handle.createQuery("INSERT INTO dns_tunnel_exclusions(uuid, organization_id, tenant_id, name, " +
                                "description, enabled, domain, client_address_cidr, match_count, last_matched_at, " +
                                "created_by_user_id, created_by_name, created_by_email, created_at, updated_at) " +
                                "VALUES(:uuid, :organization_id, :tenant_id, :name, :description, :enabled, :domain, " +
                                ":client_address_cidr, 0, NULL, :created_by_user_id, :created_by_name, " +
                                ":created_by_email, NOW(), NOW()) RETURNING *")
                        .bind("uuid", UUID.randomUUID())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("enabled", enabled)
                        .bind("domain", c.domain())
                        .bind("client_address_cidr", c.clientAddressCidr())
                        .bind("created_by_user_id", createdByUserId)
                        .bind("created_by_name", createdByName)
                        .bind("created_by_email", createdByEmail)
                        .mapTo(DnsTunnelExclusionEntry.class)
                        .one()
        );
    }

    public void update(long id, String name, @Nullable String description, boolean enabled, Criteria criteria) {
        Criteria c = criteria.normalized();

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dns_tunnel_exclusions SET name = :name, description = :description, " +
                                "enabled = :enabled, domain = :domain, client_address_cidr = :client_address_cidr, " +
                                "updated_at = NOW() WHERE id = :id")
                        .bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("enabled", enabled)
                        .bind("domain", c.domain())
                        .bind("client_address_cidr", c.clientAddressCidr())
                        .execute()
        );
    }

    public void setEnabled(long id, boolean enabled) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE dns_tunnel_exclusions SET enabled = :enabled, updated_at = NOW() " +
                                "WHERE id = :id")
                        .bind("id", id)
                        .bind("enabled", enabled)
                        .execute()
        );
    }

    public void delete(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dns_tunnel_exclusions WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    public void recordMatches(Handle handle, long id, long count) {
        handle.createUpdate("UPDATE dns_tunnel_exclusions SET match_count = match_count + :count, " +
                        "last_matched_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("count", count)
                .execute();
    }

    public Map<DnsTunnelExclusionEntry, DnsTunnelExclusionMatcher> compileEnabled(Handle handle,
                                                                                  UUID organizationId,
                                                                                  UUID tenantId) {
        Map<DnsTunnelExclusionEntry, DnsTunnelExclusionMatcher> result = new LinkedHashMap<>();
        for (DnsTunnelExclusionEntry entry : findAllEnabled(handle, organizationId, tenantId)) {
            try {
                result.put(entry, DnsTunnelExclusionMatcher.create(entry));
            } catch (IllegalArgumentException e) {
                LOG.error("DNS tunnel exclusion [{}] of tenant [{}/{}] has invalid criteria and is ignored: {}",
                        entry.uuid(), organizationId, tenantId, e.getMessage());
            }
        }

        return result;
    }

    public Map<DnsTunnelExclusionEntry, DnsTunnelExclusionMatcher> compileEnabled(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle -> compileEnabled(handle, organizationId, tenantId));
    }

    public record MatchedFinding(UUID uuid, String clientAddress, String domain) { }

    public record Impact(int matchingFindings, List<MatchedFinding> findings) {
        public static Impact empty() {
            return new Impact(0, Collections.emptyList());
        }
    }

    public Impact calculateImpact(DnsTunnelExclusionMatcher matcher, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle -> matchExisting(handle, matcher, organizationId, tenantId));
    }

    public Impact applyToExistingFindings(DnsTunnelExclusionMatcher matcher, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().inTransaction(handle -> {
            Impact impact = matchExisting(handle, matcher, organizationId, tenantId);

            for (DnsTunnelFindingEntry finding : tunnels.findAllFindingsOfTenant(handle, organizationId, tenantId)) {
                if (matchesFinding(matcher, finding)) {
                    tunnels.deleteFindingById(handle, finding.id());
                }
            }

            return impact;
        });
    }

    public static boolean matchesFinding(DnsTunnelExclusionMatcher matcher, DnsTunnelFindingEntry finding) {
        return matcher.matches(finding.domain(), finding.clientAddress());
    }

    private Impact matchExisting(Handle handle, DnsTunnelExclusionMatcher matcher, UUID organizationId, UUID tenantId) {
        List<MatchedFinding> matched = Lists.newArrayList();
        for (DnsTunnelFindingEntry finding : tunnels.findAllFindingsOfTenant(handle, organizationId, tenantId)) {
            if (!matchesFinding(matcher, finding)) {
                continue;
            }

            matched.add(new MatchedFinding(finding.uuid(), finding.clientAddress(), finding.domain()));
        }

        if (matched.isEmpty()) {
            return Impact.empty();
        }

        matched.sort(Comparator.comparing(MatchedFinding::domain).thenComparing(MatchedFinding::clientAddress));
        List<MatchedFinding> sample = matched.size() > IMPACT_SAMPLE_SIZE
                ? matched.subList(0, IMPACT_SAMPLE_SIZE) : matched;

        return new Impact(matched.size(), Lists.newArrayList(sample));
    }

    public List<DnsTunnelExclusionEntry> findMatchingExclusions(DnsTunnelFindingEntry finding,
                                                                Map<DnsTunnelExclusionEntry, DnsTunnelExclusionMatcher> compiled) {
        List<DnsTunnelExclusionEntry> result = Lists.newArrayList();
        for (Map.Entry<DnsTunnelExclusionEntry, DnsTunnelExclusionMatcher> e : compiled.entrySet()) {
            if (matchesFinding(e.getValue(), finding)) {
                result.add(e.getKey());
            }
        }

        return result;
    }

}
