package app.nzyme.core.ethernet.beacons;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.beacons.db.BeaconCheckinEntry;
import app.nzyme.core.ethernet.beacons.db.BeaconNoteEntry;
import app.nzyme.core.ethernet.beacons.db.BeaconEntry;
import app.nzyme.core.ethernet.beacons.db.BeaconSourceEntry;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.RegistryKey;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.util.*;

public class Beacons {

    public enum OrderColumn {

        NAME("name"),
        TYPE("type"),
        CLASSIFICATION("classification"),
        DESTINATION("COALESCE(destination_host, host(destination_address))"),
        DESTINATION_PORT("destination_port"),
        SOURCE_COUNT("source_count"),
        CHECKIN_COUNT("checkin_count"),
        INTERVAL_SECONDS("interval_seconds"),
        INTERVAL_JITTER("interval_jitter"),
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

    public enum SourceOrderColumn {

        SOURCE_ADDRESS("source_address"),
        SOURCE_MAC("source_mac"),
        CHECKIN_COUNT("checkin_count"),
        INTERVAL_SECONDS("interval_seconds"),
        INTERVAL_JITTER("interval_jitter"),
        REGULARITY("regularity"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        SourceOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    private final NzymeNode nzyme;
    private final BeaconNameGenerator nameGenerator;
    private final BeaconExclusions exclusions;

    public Beacons(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.nameGenerator = new BeaconNameGenerator();
        this.exclusions = new BeaconExclusions(nzyme, this);
    }

    public NzymeNode getNzyme() {
        return nzyme;
    }

    public BeaconExclusions exclusions() {
        return exclusions;
    }

    public BeaconsConfiguration getConfiguration(UUID organizationId, UUID tenantId) {
        Set<BeaconType> enabledSources = Sets.newHashSet();
        if (booleanValue(BeaconRegistryKeys.SOURCE_TLS_ENABLED, organizationId, tenantId)) enabledSources.add(BeaconType.TLS);
        if (booleanValue(BeaconRegistryKeys.SOURCE_HTTP_ENABLED, organizationId, tenantId)) enabledSources.add(BeaconType.HTTP);
        if (booleanValue(BeaconRegistryKeys.SOURCE_SSH_ENABLED, organizationId, tenantId)) enabledSources.add(BeaconType.SSH);
        if (booleanValue(BeaconRegistryKeys.SOURCE_SOCKS_ENABLED, organizationId, tenantId)) enabledSources.add(BeaconType.SOCKS);
        if (booleanValue(BeaconRegistryKeys.SOURCE_DNS_ENABLED, organizationId, tenantId)) enabledSources.add(BeaconType.DNS);
        if (booleanValue(BeaconRegistryKeys.SOURCE_TCP_ENABLED, organizationId, tenantId)) enabledSources.add(BeaconType.TCP);
        if (booleanValue(BeaconRegistryKeys.SOURCE_UDP_ENABLED, organizationId, tenantId)) enabledSources.add(BeaconType.UDP);

        return BeaconsConfiguration.create(
                booleanValue(BeaconRegistryKeys.DETECTION_ENABLED, organizationId, tenantId),
                enabledSources,
                booleanValue(BeaconRegistryKeys.INCLUDE_SITE_LOCAL_DESTINATIONS, organizationId, tenantId),
                intValue(BeaconRegistryKeys.DETECTION_WINDOW_MINUTES, organizationId, tenantId),
                intValue(BeaconRegistryKeys.MINIMUM_CHECKINS, organizationId, tenantId),
                intValue(BeaconRegistryKeys.MINIMUM_INTERVAL_SECONDS, organizationId, tenantId),
                intValue(BeaconRegistryKeys.DNS_MINIMUM_INTERVAL_SECONDS, organizationId, tenantId),
                intValue(BeaconRegistryKeys.MAXIMUM_JITTER_PERCENT, organizationId, tenantId),
                intValue(BeaconRegistryKeys.MINIMUM_REGULARITY_PERCENT, organizationId, tenantId),
                intValue(BeaconRegistryKeys.CHECKIN_RETENTION_TIME_DAYS, organizationId, tenantId),
                booleanValue(BeaconRegistryKeys.ALERT_ON_NEW_BEACON, organizationId, tenantId),
                booleanValue(BeaconRegistryKeys.ALERT_ON_NEW_SOURCE, organizationId, tenantId),
                booleanValue(BeaconRegistryKeys.ALERT_ON_SUSPICIOUS_ACTIVITY, organizationId, tenantId),
                booleanValue(BeaconRegistryKeys.ALERT_ON_MALICIOUS_ACTIVITY, organizationId, tenantId)
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

    private static final String TIME_RANGE_CONDITION = "AND first_seen <= :tr_to AND last_seen >= :tr_from ";

    public long countBeacons(Filters filters, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new BeaconFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM beacons WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id " + TIME_RANGE_CONDITION + filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<BeaconEntry> findAllBeacons(Filters filters,
                                            TimeRange timeRange,
                                            OrderColumn orderColumn,
                                            OrderDirection orderDirection,
                                            int limit,
                                            int offset,
                                            UUID organizationId,
                                            UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new BeaconFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM beacons WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id " + TIME_RANGE_CONDITION + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction>, name ASC LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(BeaconEntry.class)
                        .list()
        );
    }

    public Map<String, Long> countBeaconsByClassification(TimeRange timeRange, UUID organizationId, UUID tenantId) {
        Map<String, Long> result = new HashMap<>();
        for (BeaconClassification c : BeaconClassification.values()) {
            result.put(c.name(), 0L);
        }

        nzyme.getDatabase().useHandle(handle ->
                handle.createQuery("SELECT classification, COUNT(*) AS count FROM beacons " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                TIME_RANGE_CONDITION + "GROUP BY classification")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapToMap()
                        .forEach(row -> result.put((String) row.get("classification"), (Long) row.get("count")))
        );

        return result;
    }

    public List<BeaconEntry> findAllBeaconsOfTenant(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT * FROM beacons WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(BeaconEntry.class)
                .list();
    }

    public Optional<BeaconEntry> findBeacon(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM beacons WHERE uuid = :uuid AND organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(BeaconEntry.class)
                        .findOne()
        );
    }

    public Optional<BeaconEntry> findBeaconById(Handle handle, long id) {
        return handle.createQuery("SELECT * FROM beacons WHERE id = :id")
                .bind("id", id)
                .mapTo(BeaconEntry.class)
                .findOne();
    }

    public boolean beaconNameExists(Handle handle, String name, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT COUNT(*) FROM beacons WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND name = :name")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("name", name)
                .mapTo(Long.class)
                .one() > 0;
    }

    public String generateUniqueBeaconName(Handle handle, UUID organizationId, UUID tenantId) {
        for (int i = 0; i < 100; i++) {
            String candidate = nameGenerator.generate();
            if (!beaconNameExists(handle, candidate, organizationId, tenantId)) {
                return candidate;
            }
        }

        // Extremely unlikely. Fall back to a random suffix that is guaranteed to be unique.
        return nameGenerator.generate() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public BeaconEntry createBeacon(Handle handle,
                                    UUID organizationId,
                                    UUID tenantId,
                                    BeaconType type,
                                    String destinationKey,
                                    @Nullable String destinationHost,
                                    @Nullable String destinationAddress,
                                    @Nullable Integer destinationPort,
                                    boolean destinationIsSiteLocal,
                                    @Nullable String enrichment,
                                    DateTime firstSeen,
                                    DateTime lastSeen) {
        String name = generateUniqueBeaconName(handle, organizationId, tenantId);

        return handle.createQuery("INSERT INTO beacons(uuid, organization_id, tenant_id, type, destination_key, " +
                        "destination_host, destination_address, destination_port, destination_is_site_local, name, " +
                        "description, classification, classification_changed_at, source_count, checkin_count, " +
                        "interval_seconds, interval_jitter, enrichment, first_seen, last_seen, created_at, " +
                        "updated_at) VALUES(:uuid, :organization_id, :tenant_id, :type, :destination_key, " +
                        ":destination_host, :destination_address::inet, :destination_port, " +
                        ":destination_is_site_local, :name, NULL, :classification, NULL, 0, 0, 0, 0, " +
                        ":enrichment::jsonb, :first_seen, :last_seen, NOW(), NOW()) RETURNING *")
                .bind("uuid", UUID.randomUUID())
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("type", type.name())
                .bind("destination_key", destinationKey)
                .bind("destination_host", destinationHost)
                .bind("destination_address", destinationAddress)
                .bind("destination_port", destinationPort)
                .bind("destination_is_site_local", destinationIsSiteLocal)
                .bind("name", name)
                .bind("classification", BeaconClassification.UNKNOWN.name())
                .bind("enrichment", enrichment)
                .bind("first_seen", firstSeen)
                .bind("last_seen", lastSeen)
                .mapTo(BeaconEntry.class)
                .one();
    }

    public void refreshBeaconAggregates(Handle handle,
                                        long beaconId,
                                        @Nullable String latestDestinationAddress,
                                        @Nullable String enrichment) {
        handle.createUpdate("UPDATE beacons SET " +
                        "source_count = (SELECT COUNT(*) FROM beacon_sources WHERE beacon_id = :beacon_id), " +
                        "checkin_count = (SELECT COALESCE(SUM(checkin_count), 0) FROM beacon_sources WHERE beacon_id = :beacon_id), " +
                        "interval_seconds = (SELECT COALESCE(percentile_cont(0.5) WITHIN GROUP (ORDER BY interval_seconds), 0) FROM beacon_sources WHERE beacon_id = :beacon_id), " +
                        "interval_jitter = (SELECT COALESCE(AVG(interval_jitter), 0) FROM beacon_sources WHERE beacon_id = :beacon_id), " +
                        "first_seen = (SELECT COALESCE(MIN(first_seen), beacons.first_seen) FROM beacon_sources WHERE beacon_id = :beacon_id), " +
                        "last_seen = (SELECT COALESCE(MAX(last_seen), beacons.last_seen) FROM beacon_sources WHERE beacon_id = :beacon_id), " +
                        "destination_address = COALESCE(:destination_address::inet, destination_address), " +
                        "enrichment = COALESCE(:enrichment::jsonb, enrichment), " +
                        "updated_at = NOW() " +
                        "WHERE id = :beacon_id")
                .bind("beacon_id", beaconId)
                .bind("destination_address", latestDestinationAddress)
                .bind("enrichment", enrichment)
                .execute();
    }

    public void updateBeacon(UUID uuid, String name, @Nullable String description, UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE beacons SET name = :name, description = :description, " +
                                "updated_at = NOW() WHERE uuid = :uuid AND organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void updateBeaconType(Handle handle, long beaconId, BeaconType type) {
        handle.createUpdate("UPDATE beacons SET type = :type, updated_at = NOW() WHERE id = :id")
                .bind("type", type.name())
                .bind("id", beaconId)
                .execute();
    }

    public void classifyBeacon(UUID uuid, BeaconClassification classification, UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE beacons SET classification = :classification, " +
                                "classification_changed_at = NOW(), updated_at = NOW() WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("classification", classification.name())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public int classifyBeacons(Filters filters,
                               TimeRange timeRange,
                               BeaconClassification classification,
                               UUID organizationId,
                               UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new BeaconFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate("UPDATE beacons SET classification = :classification, " +
                                "classification_changed_at = NOW(), updated_at = NOW() " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND classification <> :classification " + TIME_RANGE_CONDITION + filterFragment.whereSql())
                        .bind("classification", classification.name())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .execute()
        );
    }

    public void deleteBeacon(UUID uuid, UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM beacons WHERE uuid = :uuid AND organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public int deleteAllBeacons(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate("DELETE FROM beacons WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void deleteBeaconById(Handle handle, long id) {
        handle.createUpdate("DELETE FROM beacons WHERE id = :id")
                .bind("id", id)
                .execute();
    }

    public void deleteSources(Handle handle, Collection<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return;
        }

        handle.createUpdate("DELETE FROM beacon_sources WHERE id IN (<ids>)")
                .bindList("ids", sourceIds)
                .execute();
    }

    public List<BeaconSourceEntry> findAllSourcesOfBeacon(long beaconId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM beacon_sources WHERE beacon_id = :beacon_id")
                        .bind("beacon_id", beaconId)
                        .mapTo(BeaconSourceEntry.class)
                        .list()
        );
    }

    public List<BeaconSourceEntry> findAllSourcesOfBeacons(Handle handle, Collection<Long> beaconIds) {
        if (beaconIds.isEmpty()) {
            return Collections.emptyList();
        }

        return handle.createQuery("SELECT * FROM beacon_sources WHERE beacon_id IN (<beacon_ids>)")
                .bindList("beacon_ids", beaconIds)
                .mapTo(BeaconSourceEntry.class)
                .list();
    }

    public long countSourcesOfBeacon(long beaconId, TimeRange timeRange) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM beacon_sources WHERE beacon_id = :beacon_id " +
                                TIME_RANGE_CONDITION)
                        .bind("beacon_id", beaconId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<BeaconSourceEntry> findSourcesOfBeacon(long beaconId,
                                                       TimeRange timeRange,
                                                       SourceOrderColumn orderColumn,
                                                       OrderDirection orderDirection,
                                                       int limit,
                                                       int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM beacon_sources WHERE beacon_id = :beacon_id " +
                                TIME_RANGE_CONDITION +
                                "ORDER BY <order_column> <order_direction>, source_address ASC " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("beacon_id", beaconId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(BeaconSourceEntry.class)
                        .list()
        );
    }

    public Optional<BeaconSourceEntry> findSourceOfBeacon(UUID sourceUuid, long beaconId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM beacon_sources WHERE uuid = :uuid AND beacon_id = :beacon_id")
                        .bind("uuid", sourceUuid)
                        .bind("beacon_id", beaconId)
                        .mapTo(BeaconSourceEntry.class)
                        .findOne()
        );
    }

    public BeaconSourceEntry createSource(Handle handle,
                                          long beaconId,
                                          String sourceAddress,
                                          @Nullable String sourceMac,
                                          IntervalAnalysis.Result analysis) {
        return handle.createQuery("INSERT INTO beacon_sources(uuid, beacon_id, source_address, source_mac, " +
                        "checkin_count, interval_seconds, interval_jitter, regularity, first_seen, last_seen, " +
                        "created_at, updated_at) VALUES(:uuid, :beacon_id, :source_address::inet, :source_mac, 0, " +
                        ":interval_seconds, :interval_jitter, :regularity, :first_seen, :last_seen, NOW(), NOW()) " +
                        "RETURNING *")
                .bind("uuid", UUID.randomUUID())
                .bind("beacon_id", beaconId)
                .bind("source_address", sourceAddress)
                .bind("source_mac", sourceMac)
                .bind("interval_seconds", analysis.intervalMedianSeconds())
                .bind("interval_jitter", analysis.jitter())
                .bind("regularity", analysis.regularity())
                .bind("first_seen", analysis.firstSeen())
                .bind("last_seen", analysis.lastSeen())
                .mapTo(BeaconSourceEntry.class)
                .one();
    }

    public void updateSourceStatistics(Handle handle,
                                       long sourceId,
                                       IntervalAnalysis.Result analysis,
                                       int newCheckins) {
        handle.createUpdate("UPDATE beacon_sources SET checkin_count = checkin_count + :new_checkins, " +
                        "interval_seconds = :interval_seconds, interval_jitter = :interval_jitter, " +
                        "regularity = :regularity, first_seen = LEAST(first_seen, :first_seen), " +
                        "last_seen = GREATEST(last_seen, :last_seen), updated_at = NOW() WHERE id = :id")
                .bind("id", sourceId)
                .bind("new_checkins", newCheckins)
                .bind("interval_seconds", analysis.intervalMedianSeconds())
                .bind("interval_jitter", analysis.jitter())
                .bind("regularity", analysis.regularity())
                .bind("first_seen", analysis.firstSeen())
                .bind("last_seen", analysis.lastSeen())
                .execute();
    }

    public int insertCheckins(Handle handle, long beaconId, long sourceId, List<BeaconCheckinRecord> checkins) {
        if (checkins.isEmpty()) {
            return 0;
        }

        PreparedBatch batch = handle.prepareBatch("INSERT INTO beacon_checkins(beacon_id, beacon_source_id, " +
                "timestamp, destination_address, destination_mac, tap_uuid, session_key, l4_type, session_count, " +
                "bytes_rx, bytes_tx, bytes_total) VALUES(:beacon_id, :beacon_source_id, :timestamp, " +
                ":destination_address::inet, :destination_mac, :tap_uuid::uuid, :session_key, :l4_type, " +
                ":session_count, :bytes_rx, :bytes_tx, :bytes_total) " +
                "ON CONFLICT (beacon_source_id, timestamp) DO UPDATE SET session_key = EXCLUDED.session_key, " +
                "destination_mac = EXCLUDED.destination_mac, l4_type = EXCLUDED.l4_type, " +
                "session_count = EXCLUDED.session_count, bytes_rx = EXCLUDED.bytes_rx, " +
                "bytes_tx = EXCLUDED.bytes_tx, bytes_total = EXCLUDED.bytes_total " +
                "WHERE (beacon_checkins.session_key IS NULL AND beacon_checkins.bytes_total IS NULL) " +
                "OR (beacon_checkins.destination_mac IS NULL AND EXCLUDED.destination_mac IS NOT NULL) " +
                "RETURNING (xmax = 0) AS inserted");

        for (BeaconCheckinRecord checkin : checkins) {
            batch.bind("beacon_id", beaconId)
                    .bind("beacon_source_id", sourceId)
                    .bind("timestamp", checkin.timestamp())
                    .bind("destination_address", checkin.destinationAddress())
                    .bind("destination_mac", checkin.destinationMac())
                    .bind("tap_uuid", checkin.tapUuid())
                    .bind("session_key", checkin.sessionKey())
                    .bind("l4_type", checkin.l4Type())
                    .bind("session_count", checkin.sessionCount())
                    .bind("bytes_rx", checkin.bytesRx())
                    .bind("bytes_tx", checkin.bytesTx())
                    .bind("bytes_total", checkin.bytesTotal())
                    .add();
        }

        int inserted = 0;
        for (Boolean isInsert : batch.executePreparedBatch("inserted").mapTo(Boolean.class)) {
            if (Boolean.TRUE.equals(isInsert)) {
                inserted++;
            }
        }

        return inserted;
    }

    public List<GenericIntegerHistogramEntry> checkinBytesHistogram(long beaconId,
                                                                    @Nullable Long sourceId,
                                                                    TimeRange timeRange,
                                                                    Bucketing.BucketingConfiguration bucketing) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT date_trunc(:date_trunc, timestamp) AS bucket, " +
                                "ROUND(AVG(bytes_total))::integer AS value " +
                                "FROM beacon_checkins WHERE beacon_id = :beacon_id " +
                                "AND (:source_id::bigint IS NULL OR beacon_source_id = :source_id::bigint) " +
                                "AND bytes_total IS NOT NULL " +
                                "AND timestamp >= :tr_from AND timestamp <= :tr_to " +
                                "GROUP BY bucket ORDER BY bucket DESC")
                        .bind("beacon_id", beaconId)
                        .bind("source_id", sourceId)
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    public List<DestinationConnectionSummary> findDestinationConnections(String destinationAddress,
                                                                         List<UUID> taps,
                                                                         TimeRange timeRange) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT l4_type, destination_port, COUNT(*) AS session_count, " +
                                "COUNT(DISTINCT source_address) AS source_count, " +
                                "SUM(bytes_rx_count) AS bytes_rx, SUM(bytes_tx_count) AS bytes_tx, " +
                                "MIN(start_time) AS first_seen, MAX(most_recent_segment_time) AS last_seen " +
                                "FROM l4_sessions WHERE destination_address = :destination_address::inet " +
                                "AND tap_uuid IN (<taps>) AND most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to " +
                                "GROUP BY l4_type, destination_port ORDER BY session_count DESC, destination_port ASC")
                        .bind("destination_address", destinationAddress)
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .map((rs, ctx) -> new DestinationConnectionSummary(
                                rs.getString("l4_type"),
                                rs.getInt("destination_port"),
                                rs.getLong("session_count"),
                                rs.getLong("source_count"),
                                rs.getLong("bytes_rx"),
                                rs.getLong("bytes_tx"),
                                new DateTime(rs.getTimestamp("first_seen")),
                                new DateTime(rs.getTimestamp("last_seen"))
                        ))
                        .list()
        );
    }

    public List<CheckinPoint> findCheckinPoints(long beaconId,
                                                @Nullable Long sourceId,
                                                Filters filters,
                                                TimeRange timeRange,
                                                int limit) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new BeaconCheckinFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT c.beacon_source_id, c.timestamp, c.bytes_total, c.interval_seconds " +
                                "FROM (SELECT beacon_source_id, timestamp, destination_address, destination_mac, " +
                                "bytes_total, session_count, " +
                                "EXTRACT(EPOCH FROM timestamp - lag(timestamp) OVER " +
                                "(PARTITION BY beacon_source_id ORDER BY timestamp)) AS interval_seconds " +
                                "FROM beacon_checkins WHERE beacon_id = :beacon_id " +
                                "AND (:source_id::bigint IS NULL OR beacon_source_id = :source_id::bigint)) c " +
                                "JOIN beacon_sources s ON s.id = c.beacon_source_id " +
                                "WHERE c.timestamp >= :tr_from AND c.timestamp <= :tr_to " +
                                filterFragment.whereSql() +
                                "ORDER BY c.timestamp DESC LIMIT :limit")
                        .bind("beacon_id", beaconId)
                        .bind("source_id", sourceId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .map((rs, ctx) -> {
                            long bytesTotal = rs.getLong("bytes_total");
                            boolean bytesNull = rs.wasNull();
                            double interval = rs.getDouble("interval_seconds");
                            boolean intervalNull = rs.wasNull();

                            return new CheckinPoint(
                                    rs.getLong("beacon_source_id"),
                                    new DateTime(rs.getTimestamp("timestamp")),
                                    bytesNull ? null : bytesTotal,
                                    intervalNull ? null : interval
                            );
                        })
                        .list()
        );
    }

    public record CheckinPoint(long sourceId,
                               DateTime timestamp,
                               @Nullable Long bytesTotal,
                               @Nullable Double intervalSeconds) { }

    public List<BeaconNoteEntry> findNotesOfBeacon(long beaconId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM beacon_notes WHERE beacon_id = :beacon_id ORDER BY created_at DESC")
                        .bind("beacon_id", beaconId)
                        .mapTo(BeaconNoteEntry.class)
                        .list()
        );
    }

    public Optional<BeaconNoteEntry> findNoteOfBeacon(UUID noteUuid, long beaconId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM beacon_notes WHERE uuid = :uuid AND beacon_id = :beacon_id")
                        .bind("uuid", noteUuid)
                        .bind("beacon_id", beaconId)
                        .mapTo(BeaconNoteEntry.class)
                        .findOne()
        );
    }

    public BeaconNoteEntry createNote(long beaconId,
                                      @Nullable UUID authorUserId,
                                      @Nullable String authorName,
                                      @Nullable String authorEmail,
                                      String note) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("INSERT INTO beacon_notes(uuid, beacon_id, author_user_id, author_name, " +
                                "author_email, note, created_at) VALUES(:uuid, :beacon_id, :author_user_id, " +
                                ":author_name, :author_email, :note, NOW()) RETURNING *")
                        .bind("uuid", UUID.randomUUID())
                        .bind("beacon_id", beaconId)
                        .bind("author_user_id", authorUserId)
                        .bind("author_name", authorName)
                        .bind("author_email", authorEmail)
                        .bind("note", note)
                        .mapTo(BeaconNoteEntry.class)
                        .one()
        );
    }

    public void deleteNote(long noteId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM beacon_notes WHERE id = :id")
                        .bind("id", noteId)
                        .execute()
        );
    }

    public record DestinationConnectionSummary(String l4Type,
                                               int destinationPort,
                                               long sessionCount,
                                               long sourceCount,
                                               long bytesRx,
                                               long bytesTx,
                                               DateTime firstSeen,
                                               DateTime lastSeen) { }

    public List<GenericIntegerHistogramEntry> checkinHistogram(long beaconId,
                                                               @Nullable Long sourceId,
                                                               TimeRange timeRange,
                                                               Bucketing.BucketingConfiguration bucketing) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT date_trunc(:date_trunc, timestamp) AS bucket, COUNT(*) AS value " +
                                "FROM beacon_checkins WHERE beacon_id = :beacon_id " +
                                "AND (:source_id::bigint IS NULL OR beacon_source_id = :source_id::bigint) " +
                                "AND timestamp >= :tr_from AND timestamp <= :tr_to " +
                                "GROUP BY bucket ORDER BY bucket DESC")
                        .bind("beacon_id", beaconId)
                        .bind("source_id", sourceId)
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    public List<GenericIntegerHistogramEntry> activeBeaconsHistogram(Filters filters,
                                                                     UUID organizationId,
                                                                     UUID tenantId,
                                                                     TimeRange timeRange,
                                                                     Bucketing.BucketingConfiguration bucketing) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new BeaconFilters("b"));

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT date_trunc(:date_trunc, c.timestamp) AS bucket, " +
                                "COUNT(DISTINCT c.beacon_id) AS value " +
                                "FROM beacon_checkins c JOIN beacons b ON b.id = c.beacon_id " +
                                "WHERE b.organization_id = :organization_id AND b.tenant_id = :tenant_id " +
                                "AND c.timestamp >= :tr_from AND c.timestamp <= :tr_to " + filterFragment.whereSql() +
                                "GROUP BY bucket ORDER BY bucket DESC")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    public long countCheckins(long beaconId, @Nullable Long sourceId, Filters filters, TimeRange timeRange) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new BeaconCheckinFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM beacon_checkins c " +
                                "JOIN beacon_sources s ON s.id = c.beacon_source_id " +
                                "WHERE c.beacon_id = :beacon_id " +
                                "AND (:source_id::bigint IS NULL OR c.beacon_source_id = :source_id::bigint) " +
                                "AND c.timestamp >= :tr_from AND c.timestamp <= :tr_to " +
                                filterFragment.whereSql())
                        .bind("beacon_id", beaconId)
                        .bind("source_id", sourceId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<BeaconCheckinEntry> findCheckins(long beaconId,
                                                 @Nullable Long sourceId,
                                                 Filters filters,
                                                 TimeRange timeRange,
                                                 int limit,
                                                 int offset) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new BeaconCheckinFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT c.* FROM beacon_checkins c " +
                                "JOIN beacon_sources s ON s.id = c.beacon_source_id " +
                                "WHERE c.beacon_id = :beacon_id " +
                                "AND (:source_id::bigint IS NULL OR c.beacon_source_id = :source_id::bigint) " +
                                "AND c.timestamp >= :tr_from AND c.timestamp <= :tr_to " +
                                filterFragment.whereSql() +
                                "ORDER BY c.timestamp DESC LIMIT :limit OFFSET :offset")
                        .bind("beacon_id", beaconId)
                        .bind("source_id", sourceId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(BeaconCheckinEntry.class)
                        .list()
        );
    }

    public void retentionCleanCheckins(UUID organizationId, UUID tenantId, DateTime cutoff) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM beacon_checkins WHERE timestamp < :cutoff AND beacon_id IN " +
                                "(SELECT id FROM beacons WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id)")
                        .bind("cutoff", cutoff)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

}
