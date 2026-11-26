package app.nzyme.core.dot11.disco;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.dot11.db.filters.Dot11DiscoAnomalyFilters;
import app.nzyme.core.dot11.db.monitoring.MonitoredBSSID;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.RegistryKey;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.statement.Query;
import org.joda.time.DateTime;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class DiscoAnomalies {

    private final NzymeNode nzyme;

    public DiscoAnomalies(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public enum OrderColumn {

        ANOMALY_TYPE("anomaly_type"),
        BSSID("bssid"),
        CLIENT("client"),
        FRAME_COUNT("frame_count"),
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

    public record Anomaly(UUID uuid,
                          UUID tapUuid,
                          DiscoAnomalyType type,
                          String bssid,
                          @Nullable String client,
                          DateTime firstSeen,
                          DateTime lastSeen,
                          long frameCount,
                          long framesFromBssid,
                          @Nullable Integer peak,
                          @Nullable Integer threshold,
                          @Nullable Double signalStrength,
                          @Nullable Double referenceSignalStrength,
                          List<Integer> reasonCodes,
                          List<String> frameTypes,
                          List<String> sampleClients) { }

    public record TypeCount(DiscoAnomalyType type, long anomalies) { }

    public record HistogramBucket(DateTime bucket, DiscoAnomalyType type, long anomalies) { }

    public record ConnectionFrame(long id,
                                  UUID tapUuid,
                                  String frameType,
                                  String transmitter,
                                  String receiver,
                                  String bssid,
                                  @Nullable Integer algorithm,
                                  @Nullable Integer transaction,
                                  @Nullable Integer statusCode,
                                  @Nullable Integer frequency,
                                  @Nullable Float signalStrengthAverage,
                                  long frameCount,
                                  long retryFrames,
                                  DateTime firstSeen,
                                  DateTime lastSeen) { }

    public DiscoDetectionConfiguration getConfiguration(UUID organizationId, UUID tenantId) {
        return new DiscoDetectionConfiguration(
                booleanValue(DiscoDetectionRegistryKeys.MONITORED_NETWORKS_ONLY, organizationId, tenantId),
                booleanValue(DiscoDetectionRegistryKeys.UNPROTECTED_FRAME_ENABLED, organizationId, tenantId),
                booleanValue(DiscoDetectionRegistryKeys.UNPROTECTED_FRAME_IGNORE_MISSING_STATE, organizationId, tenantId),
                booleanValue(DiscoDetectionRegistryKeys.MIRRORED_FRAMES_ENABLED, organizationId, tenantId),
                booleanValue(DiscoDetectionRegistryKeys.REPEATED_FRAMES_ENABLED, organizationId, tenantId),
                intValue(DiscoDetectionRegistryKeys.REPEATED_FRAMES_MINIMUM_SEQUENCE_NUMBERS, organizationId, tenantId),
                intValue(DiscoDetectionRegistryKeys.REPEATED_FRAMES_MINIMUM_FRAMES, organizationId, tenantId),
                booleanValue(DiscoDetectionRegistryKeys.SIGNAL_MISMATCH_ENABLED, organizationId, tenantId),
                intValue(DiscoDetectionRegistryKeys.SIGNAL_MISMATCH_MINIMUM_DIFFERENCE, organizationId, tenantId),
                booleanValue(DiscoDetectionRegistryKeys.AUTHENTICATION_FLOOD_ENABLED, organizationId, tenantId),
                intValue(DiscoDetectionRegistryKeys.AUTHENTICATION_FLOOD_MINIMUM_TRANSMITTERS, organizationId, tenantId),
                booleanValue(DiscoDetectionRegistryKeys.EAPOL_FLOOD_ENABLED, organizationId, tenantId),
                intValue(DiscoDetectionRegistryKeys.EAPOL_FLOOD_MINIMUM_FRAMES, organizationId, tenantId),
                booleanValue(DiscoDetectionRegistryKeys.EAP_FAILURE_ENABLED, organizationId, tenantId),
                intValue(DiscoDetectionRegistryKeys.EAP_FAILURE_MINIMUM_FAILURES, organizationId, tenantId),
                booleanValue(DiscoDetectionRegistryKeys.FRAME_OVERFLOW_ENABLED, organizationId, tenantId)
        );
    }

    public Map<String, List<MonitoredSSID>> monitoredNetworksByBssid(UUID organizationId, UUID tenantId,
                                                                     boolean enabledOnly) {
        Map<String, List<MonitoredSSID>> monitored = Maps.newHashMap();
        for (MonitoredSSID network : nzyme.getDot11().findAllMonitoredSSIDs(organizationId, tenantId)) {
            if (enabledOnly && !network.isEnabled()) {
                continue;
            }

            for (MonitoredBSSID bssid : nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(network.id())) {
                monitored.computeIfAbsent(bssid.bssid().toUpperCase(), b -> Lists.newArrayList()).add(network);
            }
        }

        return monitored;
    }

    public String stringValue(RegistryKey key, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabaseCoreRegistry().getValue(key.key(), organizationId, tenantId)
                .orElse(key.defaultValue().orElseThrow());
    }

    public boolean booleanValue(RegistryKey key, UUID organizationId, UUID tenantId) {
        return Boolean.parseBoolean(stringValue(key, organizationId, tenantId));
    }

    public int intValue(RegistryKey key, UUID organizationId, UUID tenantId) {
        try {
            return Integer.parseInt(stringValue(key, organizationId, tenantId));
        } catch (NumberFormatException e) {
            return Integer.parseInt(key.defaultValue().orElseThrow());
        }
    }

    private static final List<String> KNOWN_TYPES = Arrays.stream(DiscoAnomalyType.values()).map(Enum::name).toList();

    private static final String SCOPE_SQL = "WHERE last_seen >= :tr_from AND first_seen <= :tr_to " +
            "AND tap_uuid IN (<taps>) AND anomaly_type IN (<known_types>) ";

    public List<Anomaly> findAll(TimeRange timeRange,
                                 Filters filters,
                                 @Nullable List<String> bssids,
                                 List<UUID> taps,
                                 OrderColumn orderColumn,
                                 OrderDirection orderDirection,
                                 int limit,
                                 int offset) {
        if (isEmpty(taps, bssids)) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DiscoAnomalyFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindScope(handle.createQuery("SELECT * FROM dot11_disco_anomalies " + SCOPE_SQL +
                                filterFragment.whereSql() + " " + bssidRestriction(bssids) +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, id DESC " +
                                "LIMIT :limit OFFSET :offset"), timeRange, taps, filterFragment, bssids)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> anomaly(rs))
                        .list()
        );
    }

    public long count(TimeRange timeRange, Filters filters, @Nullable List<String> bssids, List<UUID> taps) {
        if (isEmpty(taps, bssids)) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DiscoAnomalyFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindScope(handle.createQuery("SELECT COUNT(*) FROM dot11_disco_anomalies " + SCOPE_SQL +
                                filterFragment.whereSql() + " " + bssidRestriction(bssids)),
                        timeRange, taps, filterFragment, bssids)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<TypeCount> countByType(TimeRange timeRange,
                                       Filters filters,
                                       @Nullable List<String> bssids,
                                       List<UUID> taps) {
        if (isEmpty(taps, bssids)) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DiscoAnomalyFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindScope(handle.createQuery("SELECT anomaly_type, COUNT(*) AS anomalies " +
                                "FROM dot11_disco_anomalies " + SCOPE_SQL + filterFragment.whereSql() + " " +
                                bssidRestriction(bssids) + "GROUP BY anomaly_type ORDER BY anomalies DESC"),
                        timeRange, taps, filterFragment, bssids)
                        .map((rs, ctx) -> new TypeCount(
                                DiscoAnomalyType.valueOf(rs.getString("anomaly_type")), rs.getLong("anomalies")
                        ))
                        .list()
        );
    }

    public List<HistogramBucket> histogram(TimeRange timeRange,
                                           Bucketing.BucketingConfiguration bucketing,
                                           Filters filters,
                                           @Nullable List<String> bssids,
                                           List<UUID> taps) {
        if (isEmpty(taps, bssids)) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new Dot11DiscoAnomalyFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindScope(handle.createQuery("SELECT DATE_TRUNC(:date_trunc, first_seen) AS bucket, " +
                                "anomaly_type, COUNT(*) AS anomalies FROM dot11_disco_anomalies " +
                                "WHERE first_seen >= :tr_from AND first_seen <= :tr_to AND last_seen >= :tr_from " +
                                "AND tap_uuid IN (<taps>) " +
                                "AND anomaly_type IN (<known_types>) " +
                                filterFragment.whereSql() + " " + bssidRestriction(bssids) +
                                "GROUP BY bucket, anomaly_type ORDER BY bucket DESC"),
                        timeRange, taps, filterFragment, bssids)
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .map((rs, ctx) -> new HistogramBucket(
                                new DateTime(rs.getTimestamp("bucket")),
                                DiscoAnomalyType.valueOf(rs.getString("anomaly_type")),
                                rs.getLong("anomalies")
                        ))
                        .list()
        );
    }

    public Optional<Anomaly> findOne(UUID uuid, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dot11_disco_anomalies WHERE uuid = :uuid " +
                                "AND tap_uuid IN (<taps>) AND anomaly_type IN (<known_types>)")
                        .bind("uuid", uuid)
                        .bindList("taps", taps)
                        .bindList("known_types", KNOWN_TYPES)
                        .map((rs, ctx) -> anomaly(rs))
                        .findOne()
        );
    }

    public List<ConnectionFrame> findConnectionFrames(UUID tapUuid,
                                                      String bssid,
                                                      @Nullable String client,
                                                      List<String> frameTypes,
                                                      DateTime from,
                                                      DateTime to,
                                                      int limit,
                                                      int offset) {
        if (frameTypes.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                bindConnectionFrames(handle.createQuery("SELECT * FROM dot11_connection_frames " +
                                CONNECTION_FRAMES_SQL + clientRestriction(client) +
                                "ORDER BY first_seen ASC, id ASC LIMIT :limit OFFSET :offset"),
                        tapUuid, bssid, client, frameTypes, from, to)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> {
                            float signal = rs.getFloat("signal_strength_average");
                            boolean signalIsNull = rs.wasNull();

                            return new ConnectionFrame(
                                    rs.getLong("id"),
                                    UUID.fromString(rs.getString("tap_uuid")),
                                    rs.getString("frame_type"),
                                    rs.getString("transmitter"),
                                    rs.getString("receiver"),
                                    rs.getString("bssid"),
                                    rs.getObject("algorithm", Integer.class),
                                    rs.getObject("transaction", Integer.class),
                                    rs.getObject("status_code", Integer.class),
                                    rs.getObject("frequency", Integer.class),
                                    signalIsNull ? null : signal,
                                    rs.getLong("frame_count"),
                                    rs.getLong("retry_frames"),
                                    new DateTime(rs.getTimestamp("first_seen")),
                                    new DateTime(rs.getTimestamp("last_seen"))
                            );
                        })
                        .list()
        );
    }

    public long countConnectionFrames(UUID tapUuid,
                                      String bssid,
                                      @Nullable String client,
                                      List<String> frameTypes,
                                      DateTime from,
                                      DateTime to) {
        if (frameTypes.isEmpty()) {
            return 0;
        }

        return nzyme.getDatabase().withHandle(handle ->
                bindConnectionFrames(handle.createQuery("SELECT COUNT(*) FROM dot11_connection_frames " +
                        CONNECTION_FRAMES_SQL + clientRestriction(client)), tapUuid, bssid, client, frameTypes, from, to)
                        .mapTo(Long.class)
                        .one()
        );
    }

    // The created_at range lets the index on (bssid, created_at) narrow the rows; a report is written at most
    // two minutes after its oldest frame.
    private static final String CONNECTION_FRAMES_SQL = "WHERE bssid = :bssid AND tap_uuid = :tap_uuid " +
            "AND created_at >= :from AND created_at <= :to::timestamptz + interval '3 minutes' " +
            "AND last_seen >= :from AND first_seen <= :to AND frame_type IN (<frame_types>) ";

    private static String clientRestriction(@Nullable String client) {
        return client == null ? "" : "AND (transmitter = :client OR receiver = :client) ";
    }

    private static Query bindConnectionFrames(Query query, UUID tapUuid, String bssid, @Nullable String client,
                                              List<String> frameTypes, DateTime from, DateTime to) {
        query.bind("tap_uuid", tapUuid)
                .bind("bssid", bssid)
                .bind("from", from)
                .bind("to", to)
                .bindList("frame_types", frameTypes);

        if (client != null) {
            query.bind("client", client);
        }

        return query;
    }

    private static boolean isEmpty(List<UUID> taps, @Nullable List<String> bssids) {
        return taps.isEmpty() || (bssids != null && bssids.isEmpty());
    }

    private static String bssidRestriction(@Nullable List<String> bssids) {
        return bssids == null ? "" : "AND bssid IN (<bssids>) ";
    }

    private static Query bindScope(Query query,
                                   TimeRange timeRange,
                                   List<UUID> taps,
                                   FilterSqlFragment filterFragment,
                                   @Nullable List<String> bssids) {
        query.bind("tr_from", timeRange.from())
                .bind("tr_to", timeRange.to())
                .bindList("taps", taps)
                .bindList("known_types", KNOWN_TYPES)
                .bindMap(filterFragment.bindings());

        if (bssids != null) {
            query.bindList("bssids", bssids);
        }

        return query;
    }

    private static Anomaly anomaly(ResultSet rs) throws SQLException {
        return new Anomaly(
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("tap_uuid")),
                DiscoAnomalyType.valueOf(rs.getString("anomaly_type")),
                rs.getString("bssid"),
                rs.getString("client"),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen")),
                rs.getLong("frame_count"),
                rs.getLong("frames_from_bssid"),
                rs.getObject("peak", Integer.class),
                rs.getObject("threshold", Integer.class),
                rs.getObject("signal_strength", Double.class),
                rs.getObject("reference_signal_strength", Double.class),
                Arrays.asList((Integer[]) rs.getArray("reason_codes").getArray()),
                Arrays.asList((String[]) rs.getArray("frame_types").getArray()),
                Arrays.asList((String[]) rs.getArray("sample_clients").getArray())
        );
    }

}
