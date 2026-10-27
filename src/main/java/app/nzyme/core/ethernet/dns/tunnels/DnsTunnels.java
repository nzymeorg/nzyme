package app.nzyme.core.ethernet.dns.tunnels;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.dns.tunnels.db.DnsTunnelFindingEntry;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.RegistryKey;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.util.*;

public class DnsTunnels {

    private final NzymeNode nzyme;
    private final DnsTunnelExclusions exclusions;

    public DnsTunnels(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.exclusions = new DnsTunnelExclusions(nzyme, this);
    }

    public DnsTunnelExclusions exclusions() {
        return exclusions;
    }

    public DnsTunnelConfiguration getConfiguration(UUID organizationId, UUID tenantId) {
        return DnsTunnelConfiguration.create(
                booleanValue(DnsTunnelRegistryKeys.DETECTION_ENABLED, organizationId, tenantId),
                intValue(DnsTunnelRegistryKeys.DETECTION_WINDOW_MINUTES, organizationId, tenantId),
                intValue(DnsTunnelRegistryKeys.MINIMUM_DISTINCT_NAMES, organizationId, tenantId),
                intValue(DnsTunnelRegistryKeys.ENTROPY_THRESHOLD_TENTHS, organizationId, tenantId),
                intValue(DnsTunnelRegistryKeys.NAME_LENGTH_THRESHOLD, organizationId, tenantId),
                intValue(DnsTunnelRegistryKeys.RECORD_TYPE_SHARE_THRESHOLD, organizationId, tenantId),
                intValue(DnsTunnelRegistryKeys.RETENTION_TIME_DAYS, organizationId, tenantId),
                booleanValue(DnsTunnelRegistryKeys.ALERT_ON_NEW, organizationId, tenantId)
        );
    }

    public String stringValue(RegistryKey key, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabaseCoreRegistry().getValue(key.key(), organizationId, tenantId)
                .orElse(key.defaultValue().orElseThrow());
    }

    public boolean booleanValue(RegistryKey key, UUID organizationId, UUID tenantId) {
        return Boolean.parseBoolean(stringValue(key, organizationId, tenantId));
    }

    public int intValue(RegistryKey key, UUID organizationId, UUID tenantId) {
        return Integer.parseInt(stringValue(key, organizationId, tenantId));
    }

    public enum OrderColumn {

        CLIENT_ADDRESS("client_address"),
        CLIENT_MAC("client_mac"),
        DOMAIN("domain"),
        QUERY_COUNT("query_count"),
        DISTINCT_NAME_COUNT("distinct_name_count"),
        NEGATIVE_COUNT("negative_count"),
        AVERAGE_ENTROPY("average_entropy"),
        AVERAGE_NAME_LENGTH("average_name_length"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    private static final String TIME_RANGE_CONDITION = "AND first_seen <= :tr_to AND last_seen >= :tr_from ";

    public long countFindings(Filters filters, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DnsTunnelFindingFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM dns_tunnel_findings " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                TIME_RANGE_CONDITION + filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<DnsTunnelFindingEntry> findAllFindings(Filters filters,
                                                       TimeRange timeRange,
                                                       OrderColumn orderColumn,
                                                       OrderDirection orderDirection,
                                                       int limit,
                                                       int offset,
                                                       UUID organizationId,
                                                       UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DnsTunnelFindingFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dns_tunnel_findings " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                TIME_RANGE_CONDITION + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, last_seen DESC " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(DnsTunnelFindingEntry.class)
                        .list()
        );
    }

    public List<GenericIntegerHistogramEntry> activeFindingsHistogram(Filters filters,
                                                                      UUID organizationId,
                                                                      UUID tenantId,
                                                                      TimeRange timeRange,
                                                                      Bucketing.BucketingConfiguration bucketing) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DnsTunnelFindingFilters("f"));

        String step = "1 " + bucketing.type().getDateTruncName();

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.bucket AS bucket, COUNT(f.id)::integer AS value " +
                                "FROM generate_series(date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                ":tr_to::timestamptz, :step::interval) AS b(bucket) " +
                                "LEFT JOIN dns_tunnel_findings f ON f.organization_id = :organization_id " +
                                "AND f.tenant_id = :tenant_id AND f.first_seen < b.bucket + :step::interval " +
                                "AND f.last_seen >= b.bucket " + filterFragment.whereSql() +
                                "GROUP BY b.bucket ORDER BY b.bucket DESC")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("step", step)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    public Optional<DnsTunnelFindingEntry> findFinding(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dns_tunnel_findings WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(DnsTunnelFindingEntry.class)
                        .findOne()
        );
    }

    public Optional<DnsTunnelFindingEntry> findFindingById(Handle handle, long id) {
        return handle.createQuery("SELECT * FROM dns_tunnel_findings WHERE id = :id")
                .bind("id", id)
                .mapTo(DnsTunnelFindingEntry.class)
                .findOne();
    }

    public List<DnsTunnelFindingEntry> findAllFindingsOfTenant(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT * FROM dns_tunnel_findings WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(DnsTunnelFindingEntry.class)
                .list();
    }

    public List<DnsTunnelFindingEntry> findActiveFindingsOfTenant(Handle handle,
                                                                  UUID organizationId,
                                                                  UUID tenantId,
                                                                  DateTime activeSince) {
        return handle.createQuery("SELECT * FROM dns_tunnel_findings WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND last_seen >= :active_since")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("active_since", activeSince)
                .mapTo(DnsTunnelFindingEntry.class)
                .list();
    }

    public DnsTunnelFindingEntry createFinding(Handle handle,
                                               UUID organizationId,
                                               UUID tenantId,
                                               String clientAddress,
                                               @Nullable String clientMac,
                                               String domain,
                                               DateTime firstSeen,
                                               DateTime lastSeen) {
        return handle.createQuery("INSERT INTO dns_tunnel_findings(uuid, organization_id, tenant_id, " +
                        "client_address, client_mac, domain, server_addresses, indicators, query_count, " +
                        "distinct_name_count, negative_count, average_entropy, maximum_entropy, " +
                        "average_name_length, maximum_name_length, record_types, names, name_count, tap_uuids, " +
                        "first_seen, last_seen, created_at, updated_at) " +
                        "VALUES(:uuid, :organization_id, :tenant_id, :client_address::inet, :client_mac, :domain, " +
                        "'[]'::jsonb, '[]'::jsonb, 0, 0, 0, 0, 0, 0, 0, '{}'::jsonb, '[]'::jsonb, 0, '[]'::jsonb, " +
                        ":first_seen, :last_seen, NOW(), NOW()) RETURNING *")
                .bind("uuid", UUID.randomUUID())
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("client_address", clientAddress)
                .bind("client_mac", clientMac)
                .bind("domain", domain)
                .bind("first_seen", firstSeen)
                .bind("last_seen", lastSeen)
                .mapTo(DnsTunnelFindingEntry.class)
                .one();
    }

    public record Activity(long queryCount,
                           long distinctNameCount,
                           long negativeCount,
                           double averageEntropy,
                           double maximumEntropy,
                           double averageNameLength,
                           int maximumNameLength,
                           Map<String, Long> recordTypes,
                           List<String> names,
                           List<String> indicators,
                           List<String> serverAddresses,
                           @Nullable String clientMac,
                           Collection<UUID> taps,
                           DateTime lastSeen) { }

    /*
     * Counters are added, averages are weighted by query count, maxima are kept, and the lists are the merged
     * result the detector computed.
     */
    public void addFindingActivity(Handle handle, long id, Activity a) {
        handle.createUpdate("UPDATE dns_tunnel_findings SET " +
                        "average_entropy = CASE WHEN query_count + :query_count = 0 THEN 0 ELSE " +
                        "(average_entropy * query_count + :average_entropy * :query_count) / (query_count + :query_count) END, " +
                        "average_name_length = CASE WHEN query_count + :query_count = 0 THEN 0 ELSE " +
                        "(average_name_length * query_count + :average_name_length * :query_count) / (query_count + :query_count) END, " +
                        "query_count = query_count + :query_count, " +
                        "distinct_name_count = distinct_name_count + :distinct_name_count, " +
                        "negative_count = negative_count + :negative_count, " +
                        "maximum_entropy = GREATEST(maximum_entropy, :maximum_entropy), " +
                        "maximum_name_length = GREATEST(maximum_name_length, :maximum_name_length), " +
                        "record_types = :record_types::jsonb, names = :names::jsonb, name_count = :name_count, " +
                        "indicators = :indicators::jsonb, server_addresses = :server_addresses::jsonb, " +
                        "client_mac = COALESCE(:client_mac, client_mac), tap_uuids = :tap_uuids::jsonb, " +
                        "last_seen = GREATEST(last_seen, :last_seen), updated_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("query_count", a.queryCount())
                .bind("distinct_name_count", a.distinctNameCount())
                .bind("negative_count", a.negativeCount())
                .bind("average_entropy", a.averageEntropy())
                .bind("maximum_entropy", a.maximumEntropy())
                .bind("average_name_length", a.averageNameLength())
                .bind("maximum_name_length", a.maximumNameLength())
                .bind("record_types", DnsTunnelJson.toJson(a.recordTypes()))
                .bind("names", DnsTunnelJson.toJson(a.names()))
                .bind("name_count", a.names().size())
                .bind("indicators", DnsTunnelJson.toJson(a.indicators()))
                .bind("server_addresses", DnsTunnelJson.toJson(a.serverAddresses()))
                .bind("client_mac", a.clientMac())
                .bind("tap_uuids", DnsTunnelJson.toJson(a.taps().stream().map(UUID::toString).toList()))
                .bind("last_seen", a.lastSeen())
                .execute();
    }

    public void deleteFinding(UUID uuid, UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dns_tunnel_findings WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void deleteFindingById(Handle handle, long id) {
        handle.createUpdate("DELETE FROM dns_tunnel_findings WHERE id = :id")
                .bind("id", id)
                .execute();
    }

    public int deleteAllFindings(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate("DELETE FROM dns_tunnel_findings WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void retentionCleanFindings(UUID organizationId, UUID tenantId, DateTime cutoff) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM dns_tunnel_findings WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND last_seen < :cutoff")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("cutoff", cutoff)
                        .execute()
        );
    }

}
