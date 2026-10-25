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
                booleanValue(ReconRegistryKeys.FINGERPRINTING_ENABLED, organizationId, tenantId),
                booleanValue(ReconRegistryKeys.INCLUDE_EXTERNAL_SOURCES, organizationId, tenantId),
                booleanValue(ReconRegistryKeys.INCLUDE_EXTERNAL_TARGETS, organizationId, tenantId),
                intValue(ReconRegistryKeys.DETECTION_WINDOW_MINUTES, organizationId, tenantId),
                intValue(ReconRegistryKeys.PORT_SCAN_MINIMUM_PORTS, organizationId, tenantId),
                intValue(ReconRegistryKeys.HOST_SWEEP_MINIMUM_HOSTS, organizationId, tenantId),
                intValue(ReconRegistryKeys.ARP_SWEEP_MINIMUM_HOSTS, organizationId, tenantId),
                intValue(ReconRegistryKeys.MINIMUM_FAILURE_PERCENT, organizationId, tenantId),
                intValue(ReconRegistryKeys.SCAN_RETENTION_TIME_DAYS, organizationId, tenantId),
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

        String column = tableAlias == null || tableAlias.isEmpty() ? "tap_uuids" : tableAlias + ".tap_uuids";
        return "AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(" + column + ") AS tap " +
                "WHERE tap IN (<taps>)) ";
    }

    private static <T extends org.jdbi.v3.core.statement.SqlStatement<T>> T bindTaps(T query, @Nullable List<UUID> taps) {
        if (taps != null && !taps.isEmpty()) {
            query.bindList("taps", taps.stream().map(UUID::toString).toList());
        }

        return query;
    }

    public long countScans(Filters filters, ReconScanCategory category, @Nullable List<UUID> taps, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ReconScanFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT COUNT(*) FROM recon_scans WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND type IN (<types>) " + tapsCondition(taps, null) + filterFragment.whereSql()), taps)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindList("types", category.types())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<ReconScanEntry> findAllScans(Filters filters,
                                             ReconScanCategory category,
                                             @Nullable List<UUID> taps,
                                             OrderColumn orderColumn,
                                             OrderDirection orderDirection,
                                             int limit,
                                             int offset,
                                             UUID organizationId,
                                             UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ReconScanFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindTaps(handle.createQuery("SELECT * FROM recon_scans WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND type IN (<types>) " + tapsCondition(taps, null) + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, last_seen DESC " +
                                "LIMIT :limit OFFSET :offset"), taps)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
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

    public Map<String, Long> countScansByType(ReconScanCategory category, @Nullable List<UUID> taps, UUID organizationId, UUID tenantId) {
        Map<String, Long> result = new HashMap<>();
        for (String t : category.types()) {
            result.put(t, 0L);
        }

        nzyme.getDatabase().useHandle(handle ->
                bindTaps(handle.createQuery("SELECT type, COUNT(*) AS count FROM recon_scans " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND type IN (<types>) " + tapsCondition(taps, null) + "GROUP BY type"), taps)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindList("types", category.types())
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

}
