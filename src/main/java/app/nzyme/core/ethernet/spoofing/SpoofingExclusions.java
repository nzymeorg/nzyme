package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.spoofing.db.ArpSpoofingFindingEntry;
import app.nzyme.core.ethernet.spoofing.db.DhcpSpoofingFindingEntry;
import app.nzyme.core.ethernet.spoofing.db.NameResolutionSpoofingFindingEntry;
import app.nzyme.core.ethernet.spoofing.db.SpoofingExclusionEntry;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;

import java.util.*;

public class SpoofingExclusions {

    private static final Logger LOG = LogManager.getLogger(SpoofingExclusions.class);

    private static final int IMPACT_SAMPLE_SIZE = 25;

    private final NzymeNode nzyme;
    private final Spoofing spoofing;

    public SpoofingExclusions(NzymeNode nzyme, Spoofing spoofing) {
        this.nzyme = nzyme;
        this.spoofing = spoofing;
    }

    public record Criteria(@Nullable List<String> types,
                           @Nullable String addressCidr,
                           @Nullable String mac) {
        public Criteria normalized() {
            SpoofingExclusionMatcher.create(types, addressCidr, mac);

            List<String> normalizedTypes = null;
            Set<SpoofingType> parsed = SpoofingExclusionMatcher.parseTypes(types);
            if (parsed != null) {
                normalizedTypes = Lists.newArrayList();
                for (SpoofingType t : SpoofingType.values()) {
                    if (parsed.contains(t)) {
                        normalizedTypes.add(t.name());
                    }
                }
            }

            return new Criteria(
                    normalizedTypes,
                    SpoofingExclusionMatcher.normalizeCidr(addressCidr),
                    SpoofingExclusionMatcher.normalizeMac(mac)
            );
        }

        public SpoofingExclusionMatcher matcher() {
            return SpoofingExclusionMatcher.create(types, addressCidr, mac);
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
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SpoofingExclusionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM spoofing_exclusions WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id " + filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<SpoofingExclusionEntry> findAll(Filters filters,
                                                OrderColumn orderColumn,
                                                OrderDirection orderDirection,
                                                int limit,
                                                int offset,
                                                UUID organizationId,
                                                UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new SpoofingExclusionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM spoofing_exclusions WHERE organization_id = :organization_id " +
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
                        .mapTo(SpoofingExclusionEntry.class)
                        .list()
        );
    }

    public List<SpoofingExclusionEntry> findAllEnabled(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT * FROM spoofing_exclusions WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND enabled = true ORDER BY name ASC")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(SpoofingExclusionEntry.class)
                .list();
    }

    public Optional<SpoofingExclusionEntry> find(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM spoofing_exclusions WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(SpoofingExclusionEntry.class)
                        .findOne()
        );
    }

    public SpoofingExclusionEntry create(UUID organizationId,
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
                handle.createQuery("INSERT INTO spoofing_exclusions(uuid, organization_id, tenant_id, name, " +
                                "description, enabled, types, address_cidr, mac, match_count, last_matched_at, " +
                                "created_by_user_id, created_by_name, created_by_email, created_at, updated_at) " +
                                "VALUES(:uuid, :organization_id, :tenant_id, :name, :description, :enabled, :types, " +
                                ":address_cidr, :mac, 0, NULL, :created_by_user_id, :created_by_name, " +
                                ":created_by_email, NOW(), NOW()) RETURNING *")
                        .bind("uuid", UUID.randomUUID())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("enabled", enabled)
                        .bind("types", c.types() == null ? null : String.join(",", c.types()))
                        .bind("address_cidr", c.addressCidr())
                        .bind("mac", c.mac())
                        .bind("created_by_user_id", createdByUserId)
                        .bind("created_by_name", createdByName)
                        .bind("created_by_email", createdByEmail)
                        .mapTo(SpoofingExclusionEntry.class)
                        .one()
        );
    }

    public void update(long id, String name, @Nullable String description, boolean enabled, Criteria criteria) {
        Criteria c = criteria.normalized();

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE spoofing_exclusions SET name = :name, description = :description, " +
                                "enabled = :enabled, types = :types, address_cidr = :address_cidr, mac = :mac, " +
                                "updated_at = NOW() WHERE id = :id")
                        .bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("enabled", enabled)
                        .bind("types", c.types() == null ? null : String.join(",", c.types()))
                        .bind("address_cidr", c.addressCidr())
                        .bind("mac", c.mac())
                        .execute()
        );
    }

    public void setEnabled(long id, boolean enabled) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE spoofing_exclusions SET enabled = :enabled, updated_at = NOW() " +
                                "WHERE id = :id")
                        .bind("id", id)
                        .bind("enabled", enabled)
                        .execute()
        );
    }

    public void delete(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM spoofing_exclusions WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    public void recordMatches(Handle handle, long id, long count) {
        handle.createUpdate("UPDATE spoofing_exclusions SET match_count = match_count + :count, " +
                        "last_matched_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("count", count)
                .execute();
    }

    public Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> compileEnabled(Handle handle,
                                                                                UUID organizationId,
                                                                                UUID tenantId) {
        Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> result = new LinkedHashMap<>();
        for (SpoofingExclusionEntry entry : findAllEnabled(handle, organizationId, tenantId)) {
            try {
                result.put(entry, SpoofingExclusionMatcher.create(entry));
            } catch (IllegalArgumentException e) {
                LOG.error("Spoofing exclusion [{}] of tenant [{}/{}] has invalid criteria and is ignored: {}",
                        entry.uuid(), organizationId, tenantId, e.getMessage());
            }
        }

        return result;
    }

    public Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> compileEnabled(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle -> compileEnabled(handle, organizationId, tenantId));
    }

    public record MatchedFinding(UUID uuid, String type, String address, String mac) { }

    public record Impact(int matchingFindings, List<MatchedFinding> findings) {
        public static Impact empty() {
            return new Impact(0, Collections.emptyList());
        }
    }

    public Impact calculateImpact(SpoofingExclusionMatcher matcher, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle -> matchExisting(handle, matcher, organizationId, tenantId));
    }

    public Impact applyToExistingFindings(SpoofingExclusionMatcher matcher, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().inTransaction(handle -> {
            Impact impact = matchExisting(handle, matcher, organizationId, tenantId);

            for (ArpSpoofingFindingEntry finding : spoofing.findAllArpFindingsOfTenant(handle, organizationId, tenantId)) {
                if (matchesArpFinding(matcher, finding)) {
                    spoofing.deleteArpFindingById(handle, finding.id());
                }
            }

            for (DhcpSpoofingFindingEntry finding : spoofing.findAllDhcpFindingsOfTenant(handle, organizationId, tenantId)) {
                if (matchesDhcpFinding(matcher, finding)) {
                    spoofing.deleteDhcpFindingById(handle, finding.id());
                }
            }

            for (NameResolutionSpoofingFindingEntry finding : spoofing.findAllNameResolutionFindingsOfTenant(handle, organizationId, tenantId)) {
                if (matchesNameResolutionFinding(matcher, finding)) {
                    spoofing.deleteNameResolutionFindingById(handle, finding.id());
                }
            }

            return impact;
        });
    }

    public static boolean matchesArpFinding(SpoofingExclusionMatcher matcher, ArpSpoofingFindingEntry finding) {
        return matcher.matches(SpoofingType.ARP, finding.address(), finding.spooferMac());
    }

    // For DHCP, the address is the server's address and the MAC is the server's MAC.
    public static boolean matchesDhcpFinding(SpoofingExclusionMatcher matcher, DhcpSpoofingFindingEntry finding) {
        return matcher.matches(SpoofingType.DHCP, finding.serverAddress(), finding.serverMac());
    }

    // For name resolution, the MAC is the responder's MAC and the address is any address the responder answered from.
    public static boolean matchesNameResolutionFinding(SpoofingExclusionMatcher matcher, NameResolutionSpoofingFindingEntry finding) {
        return matchesNameResolutionResponder(matcher, finding.responderAddresses(), finding.responderMac());
    }

    public static boolean matchesNameResolutionResponder(SpoofingExclusionMatcher matcher, Collection<String> addresses, String mac) {
        if (addresses.isEmpty()) {
            return matcher.matches(SpoofingType.NAME_RESOLUTION, null, mac);
        }

        for (String address : addresses) {
            if (matcher.matches(SpoofingType.NAME_RESOLUTION, address, mac)) {
                return true;
            }
        }

        return false;
    }

    private Impact matchExisting(Handle handle, SpoofingExclusionMatcher matcher, UUID organizationId, UUID tenantId) {
        List<MatchedFinding> matched = Lists.newArrayList();
        for (ArpSpoofingFindingEntry finding : spoofing.findAllArpFindingsOfTenant(handle, organizationId, tenantId)) {
            if (!matchesArpFinding(matcher, finding)) {
                continue;
            }

            matched.add(new MatchedFinding(
                    finding.uuid(), SpoofingType.ARP.name(), finding.address(), finding.spooferMac()
            ));
        }

        for (DhcpSpoofingFindingEntry finding : spoofing.findAllDhcpFindingsOfTenant(handle, organizationId, tenantId)) {
            if (!matchesDhcpFinding(matcher, finding)) {
                continue;
            }

            matched.add(new MatchedFinding(
                    finding.uuid(), SpoofingType.DHCP.name(), finding.serverAddress(), finding.serverMac()
            ));
        }

        for (NameResolutionSpoofingFindingEntry finding : spoofing.findAllNameResolutionFindingsOfTenant(handle, organizationId, tenantId)) {
            if (!matchesNameResolutionFinding(matcher, finding)) {
                continue;
            }

            matched.add(new MatchedFinding(
                    finding.uuid(),
                    SpoofingType.NAME_RESOLUTION.name(),
                    finding.responderAddresses().isEmpty() ? "" : finding.responderAddresses().get(0),
                    finding.responderMac()
            ));
        }

        if (matched.isEmpty()) {
            return Impact.empty();
        }

        matched.sort(Comparator.comparing(MatchedFinding::address).thenComparing(MatchedFinding::mac));
        List<MatchedFinding> sample = matched.size() > IMPACT_SAMPLE_SIZE
                ? matched.subList(0, IMPACT_SAMPLE_SIZE) : matched;

        return new Impact(matched.size(), Lists.newArrayList(sample));
    }

    public List<SpoofingExclusionEntry> findMatchingExclusions(ArpSpoofingFindingEntry finding,
                                                               Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> compiled) {
        List<SpoofingExclusionEntry> result = Lists.newArrayList();
        for (Map.Entry<SpoofingExclusionEntry, SpoofingExclusionMatcher> e : compiled.entrySet()) {
            if (matchesArpFinding(e.getValue(), finding)) {
                result.add(e.getKey());
            }
        }

        return result;
    }

    public List<SpoofingExclusionEntry> findMatchingExclusions(DhcpSpoofingFindingEntry finding,
                                                               Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> compiled) {
        List<SpoofingExclusionEntry> result = Lists.newArrayList();
        for (Map.Entry<SpoofingExclusionEntry, SpoofingExclusionMatcher> e : compiled.entrySet()) {
            if (matchesDhcpFinding(e.getValue(), finding)) {
                result.add(e.getKey());
            }
        }

        return result;
    }

    public List<SpoofingExclusionEntry> findMatchingExclusions(NameResolutionSpoofingFindingEntry finding,
                                                               Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> compiled) {
        List<SpoofingExclusionEntry> result = Lists.newArrayList();
        for (Map.Entry<SpoofingExclusionEntry, SpoofingExclusionMatcher> e : compiled.entrySet()) {
            if (matchesNameResolutionFinding(e.getValue(), finding)) {
                result.add(e.getKey());
            }
        }

        return result;
    }

}
