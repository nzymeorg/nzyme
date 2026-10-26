package app.nzyme.core.ethernet.recon;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.recon.db.ReconScanEntry;
import app.nzyme.core.ethernet.recon.db.ReconScanTargetEntry;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.RegistryKey;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.util.*;

public class Recon {

    public enum OrderColumn {

        TYPE("type"),
        SOURCE_ADDRESS("source_address"),
        SOURCE_MAC("source_mac"),
        TARGET_ADDRESS("target_address"),
        HOST_COUNT("host_count"),
        PORT_COUNT("port_count"),
        PROBE_COUNT("probe_count"),
        OPEN_COUNT("open_count"),
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

    public enum TargetOrderColumn {

        TARGET_ADDRESS("target_address"),
        TARGET_PORT("target_port"),
        OUTCOME("outcome"),
        PROBE_COUNT("probe_count"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        TargetOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public enum ProbedHostOrderColumn {

        ADDRESS("address"),
        SOURCE_COUNT("source_count"),
        PORT_COUNT("port_count"),
        FAILED_PORT_COUNT("failed_port_count"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        ProbedHostOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public record TargetRecord(String address,
                               @Nullable Integer port,
                               ReconTargetOutcome outcome,
                               long probeCount,
                               DateTime firstSeen,
                               DateTime lastSeen) { }

    public record TargetCounts(long total, long open, long closed, long unanswered, long hosts, long ports) { }

    public record TargetUpsertResult(int inserted, @Nullable DateTime latestNewTargetFirstSeen) { }

    private final NzymeNode nzyme;
    private final ReconExclusions exclusions;

    public Recon(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.exclusions = new ReconExclusions(nzyme, this);
    }

    public NzymeNode getNzyme() {
        return nzyme;
    }

    public ReconExclusions exclusions() {
        return exclusions;
    }

    public ReconConfiguration getConfiguration(UUID organizationId, UUID tenantId) {
        return ReconConfiguration.create(
                booleanValue(ReconRegistryKeys.DETECTION_ENABLED, organizationId, tenantId),
                booleanValue(ReconRegistryKeys.SOURCE_TCP_ENABLED, organizationId, tenantId),
                booleanValue(ReconRegistryKeys.SOURCE_UDP_ENABLED, organizationId, tenantId),
                booleanValue(ReconRegistryKeys.SOURCE_ARP_ENABLED, organizationId, tenantId),
                booleanValue(ReconRegistryKeys.SOURCE_ICMP_ENABLED, organizationId, tenantId),
                booleanValue(ReconRegistryKeys.SOURCE_DNS_ENABLED, organizationId, tenantId),
                booleanValue(ReconRegistryKeys.FINGERPRINTING_ENABLED, organizationId, tenantId),
                booleanValue(ReconRegistryKeys.INCLUDE_EXTERNAL_SOURCES, organizationId, tenantId),
                booleanValue(ReconRegistryKeys.INCLUDE_EXTERNAL_TARGETS, organizationId, tenantId),
                intValue(ReconRegistryKeys.DETECTION_WINDOW_MINUTES, organizationId, tenantId),
                intValue(ReconRegistryKeys.PORT_SCAN_MINIMUM_PORTS, organizationId, tenantId),
                intValue(ReconRegistryKeys.HOST_SWEEP_MINIMUM_HOSTS, organizationId, tenantId),
                intValue(ReconRegistryKeys.ARP_SWEEP_MINIMUM_HOSTS, organizationId, tenantId),
                intValue(ReconRegistryKeys.MINIMUM_FAILURE_PERCENT, organizationId, tenantId),
                intValue(ReconRegistryKeys.SCAN_RETENTION_TIME_DAYS, organizationId, tenantId),
                booleanValue(ReconRegistryKeys.SLOW_SCAN_ENABLED, organizationId, tenantId),
                intValue(ReconRegistryKeys.SLOW_SCAN_MEMORY_DAYS, organizationId, tenantId),
                intValue(ReconRegistryKeys.SLOW_SCAN_MINIMUM_TARGETS, organizationId, tenantId),
                booleanValue(ReconRegistryKeys.ALERT_ON_NEW_SCAN, organizationId, tenantId),
                booleanValue(ReconRegistryKeys.ALERT_ON_NEW_FINGERPRINTING, organizationId, tenantId)
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

    private static String tapsCondition(@Nullable List<UUID> taps, String tableAlias) {
        if (taps == null) {
            return "";
        }

        if (taps.isEmpty()) {
            return "AND false ";
        }

        // A scan without taps was built from the probe history of the whole tenant and belongs to every selection.
        String column = tableAlias == null || tableAlias.isEmpty() ? "tap_uuids" : tableAlias + ".tap_uuids";
        return "AND (jsonb_array_length(COALESCE(" + column + ", '[]'::jsonb)) = 0 " +
                "OR EXISTS (SELECT 1 FROM jsonb_array_elements_text(" + column + ") AS tap WHERE tap IN (<taps>))) ";
    }

    private static <T extends org.jdbi.v3.core.statement.SqlStatement<T>> T bindTaps(T query, @Nullable List<UUID> taps) {
        if (taps != null && !taps.isEmpty()) {
            query.bindList("taps", taps.stream().map(UUID::toString).toList());
        }

        return query;
    }

    private static final String SCAN_TIME_RANGE_CONDITION = "AND first_seen <= :tr_to AND last_seen >= :tr_from ";

    public long countScans(Filters filters,
                           ReconScanCategory category,
                           @Nullable List<UUID> taps,
                           TimeRange timeRange,
                           UUID organizationId,
                           UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ReconScanFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT COUNT(*) FROM recon_scans WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND type IN (<types>) " + SCAN_TIME_RANGE_CONDITION +
                                tapsCondition(taps, null) + filterFragment.whereSql()), taps)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("types", category.types())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<ReconScanEntry> findAllScans(Filters filters,
                                             ReconScanCategory category,
                                             @Nullable List<UUID> taps,
                                             TimeRange timeRange,
                                             OrderColumn orderColumn,
                                             OrderDirection orderDirection,
                                             int limit,
                                             int offset,
                                             UUID organizationId,
                                             UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ReconScanFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT * FROM recon_scans WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND type IN (<types>) " + SCAN_TIME_RANGE_CONDITION +
                                tapsCondition(taps, null) + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, last_seen DESC " +
                                "LIMIT :limit OFFSET :offset"), taps)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("types", category.types())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(ReconScanEntry.class)
                        .list()
        );
    }

    public Map<String, Long> countScansByType(Filters filters,
                                              ReconScanCategory category,
                                              @Nullable List<UUID> taps,
                                              TimeRange timeRange,
                                              UUID organizationId,
                                              UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ReconScanFilters());

        Map<String, Long> result = new HashMap<>();
        for (String t : category.types()) {
            result.put(t, 0L);
        }

        nzyme.getDatabase().useHandle(handle ->
                bindTaps(handle.createQuery("SELECT type, COUNT(*) AS count FROM recon_scans " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND type IN (<types>) " + SCAN_TIME_RANGE_CONDITION +
                                tapsCondition(taps, null) + filterFragment.whereSql() +
                                "GROUP BY type"), taps)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("types", category.types())
                        .bindMap(filterFragment.bindings())
                        .mapToMap()
                        .forEach(row -> result.put((String) row.get("type"), (Long) row.get("count")))
        );

        return result;
    }

    public List<ReconScanEntry> findAllScansOfTenant(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT * FROM recon_scans WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(ReconScanEntry.class)
                .list();
    }

    public List<ReconScanEntry> findActiveScansOfTenant(Handle handle, UUID organizationId, UUID tenantId, DateTime since) {
        return handle.createQuery("SELECT * FROM recon_scans WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND last_seen >= :since")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("since", since)
                .mapTo(ReconScanEntry.class)
                .list();
    }

    public Optional<ReconScanEntry> findScan(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM recon_scans WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(ReconScanEntry.class)
                        .findOne()
        );
    }

    public Optional<ReconScanEntry> findScanById(Handle handle, long id) {
        return handle.createQuery("SELECT * FROM recon_scans WHERE id = :id")
                .bind("id", id)
                .mapTo(ReconScanEntry.class)
                .findOne();
    }

    public ReconScanEntry createScan(Handle handle,
                                     UUID organizationId,
                                     UUID tenantId,
                                     ReconScanType type,
                                     @Nullable String sourceAddress,
                                     @Nullable String sourceMac,
                                     @Nullable String targetAddress,
                                     DateTime firstSeen,
                                     DateTime lastSeen) {
        return handle.createQuery("INSERT INTO recon_scans(uuid, organization_id, tenant_id, type, " +
                        "source_address, source_mac, target_address, host_count, port_count, probe_count, " +
                        "open_count, closed_count, unanswered_count, targets_truncated, enrichment, tap_uuids, " +
                        "first_seen, last_seen, created_at, updated_at) VALUES(:uuid, :organization_id, " +
                        ":tenant_id, :type, :source_address::inet, :source_mac, :target_address::inet, 0, 0, 0, " +
                        "0, 0, 0, false, NULL, '[]'::jsonb, :first_seen, :last_seen, NOW(), NOW()) RETURNING *")
                .bind("uuid", UUID.randomUUID())
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("type", type.name())
                .bind("source_address", sourceAddress)
                .bind("source_mac", sourceMac)
                .bind("target_address", targetAddress)
                .bind("first_seen", firstSeen)
                .bind("last_seen", lastSeen)
                .mapTo(ReconScanEntry.class)
                .one();
    }

    public void updateScanAggregates(Handle handle,
                                     long scanId,
                                     long windowProbeCount,
                                     long windowOpenCount,
                                     long windowClosedCount,
                                     long windowUnansweredCount,
                                     int windowHostCount,
                                     int windowPortCount,
                                     boolean targetsTruncated,
                                     @Nullable String enrichment,
                                     Collection<UUID> tapUuids,
                                     DateTime firstSeen,
                                     @Nullable DateTime progressedAt) {
        TargetCounts stored = targetCounts(handle, scanId);

        handle.createUpdate("UPDATE recon_scans SET " +
                        "probe_count = :probe_count, open_count = :open_count, closed_count = :closed_count, " +
                        "unanswered_count = :unanswered_count, host_count = :host_count, port_count = :port_count, " +
                        "targets_truncated = targets_truncated OR :targets_truncated, " +
                        "enrichment = COALESCE(:enrichment::jsonb, enrichment), " +
                        "tap_uuids = (SELECT COALESCE(jsonb_agg(DISTINCT t), '[]'::jsonb) FROM " +
                        "jsonb_array_elements_text(tap_uuids || :tap_uuids::jsonb) AS t), " +
                        "first_seen = LEAST(first_seen, :first_seen), " +
                        "last_seen = GREATEST(last_seen, COALESCE(:progressed_at, last_seen)), " +
                        "updated_at = NOW() WHERE id = :id")
                .bind("id", scanId)
                .bind("probe_count", Math.max(stored.total(), windowProbeCount))
                .bind("open_count", Math.max(stored.open(), windowOpenCount))
                .bind("closed_count", Math.max(stored.closed(), windowClosedCount))
                .bind("unanswered_count", Math.max(stored.unanswered(), windowUnansweredCount))
                .bind("host_count", (int) Math.max(stored.hosts(), windowHostCount))
                .bind("port_count", (int) Math.max(stored.ports(), windowPortCount))
                .bind("targets_truncated", targetsTruncated)
                .bind("enrichment", enrichment)
                .bind("tap_uuids", tapUuidsJson(tapUuids))
                .bind("first_seen", firstSeen)
                .bind("progressed_at", progressedAt)
                .execute();
    }

    private static String tapUuidsJson(Collection<UUID> tapUuids) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (UUID uuid : tapUuids) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(uuid).append("\"");
            first = false;
        }

        return sb.append("]").toString();
    }

    public void deleteScan(UUID uuid, UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM recon_scans WHERE uuid = :uuid AND organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public int deleteAllScans(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate("DELETE FROM recon_scans WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void deleteScanById(Handle handle, long id) {
        handle.createUpdate("DELETE FROM recon_scans WHERE id = :id")
                .bind("id", id)
                .execute();
    }

    public void retentionCleanScans(UUID organizationId, UUID tenantId, DateTime cutoff) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM recon_scans WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND last_seen < :cutoff")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("cutoff", cutoff)
                        .execute()
        );
    }

    public List<GenericIntegerHistogramEntry> activeScansHistogram(Filters filters,
                                                                   ReconScanCategory category,
                                                                   @Nullable List<UUID> taps,
                                                                   UUID organizationId,
                                                                   UUID tenantId,
                                                                   TimeRange timeRange,
                                                                   Bucketing.BucketingConfiguration bucketing) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ReconScanFilters("s"));

        String step = "1 " + bucketing.type().getDateTruncName();

        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT b.bucket AS bucket, COUNT(s.id)::integer AS value " +
                                "FROM generate_series(date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                ":tr_to::timestamptz, :step::interval) AS b(bucket) " +
                                "LEFT JOIN recon_scans s ON s.organization_id = :organization_id " +
                                "AND s.tenant_id = :tenant_id AND s.first_seen < b.bucket + :step::interval " +
                                "AND s.last_seen >= b.bucket AND s.type IN (<types>) " + tapsCondition(taps, "s") + filterFragment.whereSql() +
                                "GROUP BY b.bucket ORDER BY b.bucket DESC"), taps)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindList("types", category.types())
                        .bindMap(filterFragment.bindings())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("step", step)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    public TargetUpsertResult upsertTargets(Handle handle, long scanId, List<TargetRecord> targets) {
        if (targets.isEmpty()) {
            return new TargetUpsertResult(0, null);
        }

        PreparedBatch batch = handle.prepareBatch("INSERT INTO recon_scan_targets(scan_id, target_address, " +
                "target_port, outcome, outcome_rank, probe_count, first_seen, last_seen) VALUES(:scan_id, " +
                ":target_address::inet, :target_port, :outcome, :outcome_rank, :probe_count, :first_seen, " +
                ":last_seen) ON CONFLICT (scan_id, target_address, COALESCE(target_port, -1)) DO UPDATE SET " +
                "outcome = CASE WHEN EXCLUDED.outcome_rank > recon_scan_targets.outcome_rank " +
                "THEN EXCLUDED.outcome ELSE recon_scan_targets.outcome END, " +
                "outcome_rank = GREATEST(recon_scan_targets.outcome_rank, EXCLUDED.outcome_rank), " +
                "probe_count = GREATEST(recon_scan_targets.probe_count, EXCLUDED.probe_count), " +
                "first_seen = LEAST(recon_scan_targets.first_seen, EXCLUDED.first_seen), " +
                "last_seen = GREATEST(recon_scan_targets.last_seen, EXCLUDED.last_seen) " +
                "RETURNING (xmax = 0) AS inserted, first_seen");

        for (TargetRecord target : targets) {
            batch.bind("scan_id", scanId)
                    .bind("target_address", target.address())
                    .bind("target_port", target.port())
                    .bind("outcome", target.outcome().name())
                    .bind("outcome_rank", target.outcome().getRank())
                    .bind("probe_count", target.probeCount())
                    .bind("first_seen", target.firstSeen())
                    .bind("last_seen", target.lastSeen())
                    .add();
        }

        int inserted = 0;
        DateTime latest = null;
        for (Map<String, Object> row : batch.executePreparedBatch("inserted", "first_seen").mapToMap()) {
            if (Boolean.TRUE.equals(row.get("inserted"))) {
                inserted++;
                DateTime firstSeen = new DateTime(row.get("first_seen"));
                if (latest == null || firstSeen.isAfter(latest)) {
                    latest = firstSeen;
                }
            }
        }

        return new TargetUpsertResult(inserted, latest);
    }

    public long countStoredTargets(Handle handle, long scanId) {
        return handle.createQuery("SELECT COUNT(*) FROM recon_scan_targets WHERE scan_id = :scan_id")
                .bind("scan_id", scanId)
                .mapTo(Long.class)
                .one();
    }

    public TargetCounts targetCounts(Handle handle, long scanId) {
        return handle.createQuery("SELECT COUNT(*) AS total, " +
                        "COUNT(*) FILTER (WHERE outcome = 'OPEN') AS open, " +
                        "COUNT(*) FILTER (WHERE outcome = 'CLOSED') AS closed, " +
                        "COUNT(*) FILTER (WHERE outcome = 'UNANSWERED') AS unanswered, " +
                        "COUNT(DISTINCT target_address) AS hosts, " +
                        "COUNT(DISTINCT target_port) AS ports " +
                        "FROM recon_scan_targets WHERE scan_id = :scan_id")
                .bind("scan_id", scanId)
                .map((rs, ctx) -> new TargetCounts(
                        rs.getLong("total"),
                        rs.getLong("open"),
                        rs.getLong("closed"),
                        rs.getLong("unanswered"),
                        rs.getLong("hosts"),
                        rs.getLong("ports")
                ))
                .one();
    }

    public long countTargets(long scanId, Filters filters) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ReconScanTargetFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM recon_scan_targets WHERE scan_id = :scan_id " +
                                filterFragment.whereSql())
                        .bind("scan_id", scanId)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<ReconScanTargetEntry> findTargets(long scanId,
                                                  Filters filters,
                                                  TargetOrderColumn orderColumn,
                                                  OrderDirection orderDirection,
                                                  int limit,
                                                  int offset) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ReconScanTargetFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM recon_scan_targets WHERE scan_id = :scan_id " +
                                filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, target_address ASC, " +
                                "target_port ASC LIMIT :limit OFFSET :offset")
                        .bind("scan_id", scanId)
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(ReconScanTargetEntry.class)
                        .list()
        );
    }

    public record AnsweredHost(String address, List<Integer> ports) { }

    public record AnsweredPort(int port, long probeCount, DateTime firstSeen, DateTime lastSeen) { }

    public record TimelineBucket(DateTime bucket, long open, long closed, long unanswered) { }

    public record TargetNetwork(String network, long hosts, long answeredHosts, long ports) { }

    public record PortRange(String range, long probed, long answered) { }

    public List<AnsweredPort> findAnsweredPorts(long scanId, int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT target_port, SUM(probe_count) AS probe_count, MIN(first_seen) AS first_seen, " +
                                "MAX(last_seen) AS last_seen FROM recon_scan_targets WHERE scan_id = :scan_id " +
                                "AND outcome = 'OPEN' AND target_port IS NOT NULL GROUP BY target_port " +
                                "ORDER BY target_port ASC LIMIT :limit")
                        .bind("scan_id", scanId)
                        .bind("limit", limit)
                        .map((rs, ctx) -> new AnsweredPort(
                                rs.getInt("target_port"),
                                rs.getLong("probe_count"),
                                new DateTime(rs.getTimestamp("first_seen")),
                                new DateTime(rs.getTimestamp("last_seen"))
                        ))
                        .list()
        );
    }

    public List<AnsweredPort> findUnansweredPorts(long scanId, int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT target_port, SUM(probe_count) AS probe_count, MIN(first_seen) AS first_seen, " +
                                "MAX(last_seen) AS last_seen FROM recon_scan_targets WHERE scan_id = :scan_id " +
                                "AND outcome = 'UNANSWERED' AND target_port IS NOT NULL GROUP BY target_port " +
                                "ORDER BY target_port ASC LIMIT :limit")
                        .bind("scan_id", scanId)
                        .bind("limit", limit)
                        .map((rs, ctx) -> new AnsweredPort(
                                rs.getInt("target_port"),
                                rs.getLong("probe_count"),
                                new DateTime(rs.getTimestamp("first_seen")),
                                new DateTime(rs.getTimestamp("last_seen"))
                        ))
                        .list()
        );
    }

    public long countUnansweredPorts(long scanId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT target_port) FROM recon_scan_targets WHERE scan_id = :scan_id " +
                                "AND outcome = 'UNANSWERED' AND target_port IS NOT NULL")
                        .bind("scan_id", scanId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public long countAnsweredPorts(long scanId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT target_port) FROM recon_scan_targets WHERE scan_id = :scan_id " +
                                "AND outcome = 'OPEN' AND target_port IS NOT NULL")
                        .bind("scan_id", scanId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<AnsweredHost> findAnsweredHosts(long scanId, int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT host(target_address) AS address, " +
                                "array_remove(array_agg(target_port ORDER BY target_port), NULL) AS ports " +
                                "FROM recon_scan_targets WHERE scan_id = :scan_id AND outcome = 'OPEN' " +
                                "GROUP BY target_address ORDER BY target_address ASC LIMIT :limit")
                        .bind("scan_id", scanId)
                        .bind("limit", limit)
                        .map((rs, ctx) -> {
                            List<Integer> ports = new ArrayList<>();
                            java.sql.Array array = rs.getArray("ports");
                            if (array != null) {
                                for (Object o : (Object[]) array.getArray()) {
                                    if (o != null) {
                                        ports.add(((Number) o).intValue());
                                    }
                                }
                            }
                            return new AnsweredHost(rs.getString("address"), ports);
                        })
                        .list()
        );
    }

    public long countAnsweredHosts(long scanId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT target_address) FROM recon_scan_targets " +
                                "WHERE scan_id = :scan_id AND outcome = 'OPEN'")
                        .bind("scan_id", scanId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<TimelineBucket> targetTimeline(long scanId, DateTime firstSeen, DateTime lastSeen) {
        String truncate = new org.joda.time.Duration(firstSeen, lastSeen).getStandardHours() <= 24 ? "minute" : "hour";

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT date_trunc(:truncate, first_seen) AS bucket, " +
                                "COUNT(*) FILTER (WHERE outcome = 'OPEN') AS open, " +
                                "COUNT(*) FILTER (WHERE outcome = 'CLOSED') AS closed, " +
                                "COUNT(*) FILTER (WHERE outcome = 'UNANSWERED') AS unanswered " +
                                "FROM recon_scan_targets WHERE scan_id = :scan_id GROUP BY bucket ORDER BY bucket ASC")
                        .bind("scan_id", scanId)
                        .bind("truncate", truncate)
                        .map((rs, ctx) -> new TimelineBucket(
                                new DateTime(rs.getTimestamp("bucket")),
                                rs.getLong("open"),
                                rs.getLong("closed"),
                                rs.getLong("unanswered")
                        ))
                        .list()
        );
    }

    public List<TargetNetwork> targetNetworks(long scanId, int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT (CASE WHEN family(target_address) = 4 " +
                                "THEN network(set_masklen(target_address, 24)) " +
                                "ELSE network(set_masklen(target_address, 64)) END)::text AS network, " +
                                "COUNT(DISTINCT target_address) AS hosts, " +
                                "COUNT(DISTINCT target_address) FILTER (WHERE outcome = 'OPEN') AS answered_hosts, " +
                                "COUNT(DISTINCT target_port) AS ports " +
                                "FROM recon_scan_targets WHERE scan_id = :scan_id " +
                                "GROUP BY 1 ORDER BY hosts DESC, network ASC LIMIT :limit")
                        .bind("scan_id", scanId)
                        .bind("limit", limit)
                        .map((rs, ctx) -> new TargetNetwork(
                                rs.getString("network"),
                                rs.getLong("hosts"),
                                rs.getLong("answered_hosts"),
                                rs.getLong("ports")
                        ))
                        .list()
        );
    }

    public List<PortRange> portRanges(long scanId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT CASE WHEN target_port < 1024 THEN 'WELL_KNOWN' " +
                                "WHEN target_port < 49152 THEN 'REGISTERED' ELSE 'DYNAMIC' END AS range, " +
                                "COUNT(*) AS probed, COUNT(*) FILTER (WHERE outcome = 'OPEN') AS answered " +
                                "FROM recon_scan_targets WHERE scan_id = :scan_id AND target_port IS NOT NULL " +
                                "GROUP BY 1 ORDER BY MIN(target_port) ASC")
                        .bind("scan_id", scanId)
                        .map((rs, ctx) -> new PortRange(
                                rs.getString("range"),
                                rs.getLong("probed"),
                                rs.getLong("answered")
                        ))
                        .list()
        );
    }

    public List<String> findTargetAddresses(Handle handle, long scanId, int limit) {
        return handle.createQuery("SELECT DISTINCT host(target_address) AS address FROM recon_scan_targets " +
                        "WHERE scan_id = :scan_id LIMIT :limit")
                .bind("scan_id", scanId)
                .bind("limit", limit)
                .mapTo(String.class)
                .list();
    }

    public void recordProbeHistory(Handle handle, UUID organizationId, UUID tenantId, DateTime since) {
        handle.createUpdate("INSERT INTO recon_probe_history(organization_id, tenant_id, source_address, " +
                        "source_mac, l4_type, target_address, target_port, outcome, first_seen, last_seen) " +
                        "SELECT :organization_id, :tenant_id, source_address::inet, source_mac, l4_type, " +
                        "destination_address::inet, destination_port, outcome, first_seen, last_seen " +
                        "FROM recon_probes WHERE last_seen >= :since " +
                        "ON CONFLICT (organization_id, tenant_id, source_address, (COALESCE(source_mac, '')), " +
                        "l4_type, target_address, target_port) DO UPDATE SET " +
                        "outcome = GREATEST(recon_probe_history.outcome, EXCLUDED.outcome), " +
                        "first_seen = LEAST(recon_probe_history.first_seen, EXCLUDED.first_seen), " +
                        "last_seen = GREATEST(recon_probe_history.last_seen, EXCLUDED.last_seen)")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("since", since)
                .execute();
    }

    public void attributeProbeHistory(Handle handle,
                                      UUID organizationId,
                                      UUID tenantId,
                                      Collection<String> sourceKeys,
                                      DateTime since) {
        if (sourceKeys.isEmpty()) {
            return;
        }

        handle.createUpdate("UPDATE recon_probe_history SET attributed = true " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                        "AND attributed = false AND last_seen >= :since " +
                        "AND host(source_address) || '/' || COALESCE(source_mac, '') IN (<keys>)")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("since", since)
                .bindList("keys", List.copyOf(sourceKeys))
                .execute();
    }

    public static String probeHistorySourceKey(String sourceAddress, @Nullable String sourceMac) {
        return sourceAddress + "/" + (sourceMac == null ? "" : sourceMac);
    }

    public void decayProbeHistory(UUID organizationId, UUID tenantId, DateTime cutoff) {
        nzyme.getDatabase().useHandle(handle -> decayProbeHistory(handle, organizationId, tenantId, cutoff));
    }

    public void decayProbeHistory(Handle handle, UUID organizationId, UUID tenantId, DateTime cutoff) {
        handle.createUpdate("DELETE FROM recon_probe_history WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND last_seen < :cutoff")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("cutoff", cutoff)
                .execute();
    }

    public record ProbedHost(String address, long sourceCount, long portCount, long failedPortCount,
                             long pairCount, DateTime firstSeen, DateTime lastSeen) { }

    public record ProbedHostSource(String address, @Nullable String mac, boolean scanner, List<String> l4Types,
                                   long portCount, long failedPortCount, DateTime firstSeen, DateTime lastSeen) { }

    public record ProbedHostPort(String l4Type, int port, long sourceCount, boolean answered,
                                 DateTime firstSeen, DateTime lastSeen) { }

    private static final String SCANNERS_CTE =
            "scanners AS (SELECT DISTINCT source_address, COALESCE(source_mac, '') AS source_mac " +
            "FROM recon_probe_history WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
            "AND last_seen >= :from AND last_seen <= :to AND attributed = true), ";

    private static final String PROBES_CTE =
            "probes AS (SELECT h.target_address, h.l4_type, h.target_port, h.source_address, h.source_mac, " +
            "h.outcome, h.first_seen, h.last_seen FROM recon_probe_history h " +
            "WHERE h.organization_id = :organization_id AND h.tenant_id = :tenant_id " +
            "AND h.last_seen >= :from AND h.last_seen <= :to " +
            "AND (:include_scanners OR NOT EXISTS (SELECT 1 FROM scanners s " +
            "WHERE s.source_address = h.source_address AND s.source_mac = COALESCE(h.source_mac, ''))))";

    private static final String PROBED_HOSTS_CTE =
            "WITH " + SCANNERS_CTE + PROBES_CTE + ", " +
            "ports AS (SELECT target_address, l4_type, target_port, MAX(outcome) AS outcome, " +
            "COUNT(*) AS pairs, MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen " +
            "FROM probes GROUP BY target_address, l4_type, target_port), " +
            "sources AS (SELECT target_address, COUNT(DISTINCT (source_address, COALESCE(source_mac, ''))) AS source_count " +
            "FROM probes GROUP BY target_address), " +
            "hosts AS (SELECT host(p.target_address) AS address, p.target_address, s.source_count, " +
            "COUNT(*) AS port_count, COUNT(*) FILTER (WHERE p.outcome < 2) AS failed_port_count, " +
            "SUM(p.pairs) AS pair_count, MIN(p.first_seen) AS first_seen, MAX(p.last_seen) AS last_seen " +
            "FROM ports p JOIN sources s ON s.target_address = p.target_address " +
            "GROUP BY p.target_address, s.source_count) ";

    public long countProbedHosts(UUID organizationId,
                                 UUID tenantId,
                                 TimeRange timeRange,
                                 boolean includeScanners,
                                 Filters filters) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ReconProbedHostFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(PROBED_HOSTS_CTE + "SELECT COUNT(*) FROM hosts WHERE true " +
                                filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("from", timeRange.from())
                        .bind("to", timeRange.to())
                        .bind("include_scanners", includeScanners)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<ProbedHost> findProbedHosts(UUID organizationId,
                                            UUID tenantId,
                                            TimeRange timeRange,
                                            boolean includeScanners,
                                            Filters filters,
                                            ProbedHostOrderColumn orderColumn,
                                            OrderDirection orderDirection,
                                            int limit,
                                            int offset) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ReconProbedHostFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(PROBED_HOSTS_CTE + "SELECT * FROM hosts WHERE true " +
                                filterFragment.whereSql() + "ORDER BY " +
                                orderColumn.getColumnName() + " " + orderDirection + ", address ASC " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("from", timeRange.from())
                        .bind("to", timeRange.to())
                        .bind("include_scanners", includeScanners)
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> probedHost(rs))
                        .list()
        );
    }

    public Optional<ProbedHost> findProbedHost(UUID organizationId,
                                               UUID tenantId,
                                               String address,
                                               TimeRange timeRange,
                                               boolean includeScanners) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(PROBED_HOSTS_CTE + "SELECT * FROM hosts WHERE address = :address")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("from", timeRange.from())
                        .bind("to", timeRange.to())
                        .bind("include_scanners", includeScanners)
                        .bind("address", address)
                        .map((rs, ctx) -> probedHost(rs))
                        .findOne()
        );
    }

    private static ProbedHost probedHost(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProbedHost(
                rs.getString("address"),
                rs.getLong("source_count"),
                rs.getLong("port_count"),
                rs.getLong("failed_port_count"),
                rs.getLong("pair_count"),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen"))
        );
    }

    public List<ProbedHostSource> findProbedHostSources(UUID organizationId,
                                                        UUID tenantId,
                                                        String address,
                                                        TimeRange timeRange,
                                                        boolean includeScanners,
                                                        int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH " + SCANNERS_CTE + PROBES_CTE + " " +
                                "SELECT host(p.source_address) AS address, p.source_mac, " +
                                "EXISTS (SELECT 1 FROM scanners s WHERE s.source_address = p.source_address " +
                                "AND s.source_mac = COALESCE(p.source_mac, '')) AS scanner, " +
                                "array_agg(DISTINCT p.l4_type) AS l4_types, COUNT(*) AS port_count, " +
                                "COUNT(*) FILTER (WHERE p.outcome < 2) AS failed_port_count, " +
                                "MIN(p.first_seen) AS first_seen, MAX(p.last_seen) AS last_seen " +
                                "FROM probes p WHERE p.target_address = :address::inet " +
                                "GROUP BY p.source_address, p.source_mac " +
                                "ORDER BY failed_port_count DESC, last_seen DESC LIMIT :limit")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("address", address)
                        .bind("from", timeRange.from())
                        .bind("to", timeRange.to())
                        .bind("include_scanners", includeScanners)
                        .bind("limit", limit)
                        .map((rs, ctx) -> new ProbedHostSource(
                                rs.getString("address"),
                                rs.getString("source_mac"),
                                rs.getBoolean("scanner"),
                                Arrays.asList((String[]) rs.getArray("l4_types").getArray()),
                                rs.getLong("port_count"),
                                rs.getLong("failed_port_count"),
                                new DateTime(rs.getTimestamp("first_seen")),
                                new DateTime(rs.getTimestamp("last_seen"))
                        ))
                        .list()
        );
    }

    public List<ProbedHostPort> findProbedHostPorts(UUID organizationId,
                                                    UUID tenantId,
                                                    String address,
                                                    TimeRange timeRange,
                                                    boolean includeScanners,
                                                    int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH " + SCANNERS_CTE + PROBES_CTE + " " +
                                "SELECT l4_type, target_port, " +
                                "COUNT(DISTINCT (source_address, COALESCE(source_mac, ''))) AS source_count, " +
                                "MAX(outcome) = 2 AS answered, MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen " +
                                "FROM probes WHERE target_address = :address::inet " +
                                "GROUP BY l4_type, target_port " +
                                "ORDER BY answered ASC, source_count DESC, target_port ASC LIMIT :limit")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("address", address)
                        .bind("from", timeRange.from())
                        .bind("to", timeRange.to())
                        .bind("include_scanners", includeScanners)
                        .bind("limit", limit)
                        .map((rs, ctx) -> new ProbedHostPort(
                                rs.getString("l4_type"),
                                rs.getInt("target_port"),
                                rs.getLong("source_count"),
                                rs.getBoolean("answered"),
                                new DateTime(rs.getTimestamp("first_seen")),
                                new DateTime(rs.getTimestamp("last_seen"))
                        ))
                        .list()
        );
    }

}
