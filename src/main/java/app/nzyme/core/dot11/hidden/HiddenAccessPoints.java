package app.nzyme.core.dot11.hidden;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.monitoring.ssids.Sightings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class HiddenAccessPoints {

    public static final int MAX_ENTRIES_PER_TENANT = 10000;
    public static final int MAX_HINTS = 5;

    private static final String TABLE = "dot11_known_hidden_access_points";

    // A hidden access point never sends a name in a beacon. A single frame without one among named beacons is noise.
    private static final String HIDDEN_REPORT = "b.hidden_ssid_frames > 0 AND NOT EXISTS (SELECT 1 FROM dot11_ssids s " +
            "WHERE s.bssid_id = b.id AND s.beacon_advertisements > 0)";

    private final NzymeNode nzyme;

    public HiddenAccessPoints(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public enum OrderColumn {

        BSSID("bssid"),
        STATUS("(is_approved, is_ignored)"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

    }

    public record Entry(long id,
                        UUID uuid,
                        UUID organizationId,
                        UUID tenantId,
                        String bssid,
                        boolean isApproved,
                        boolean isIgnored,
                        List<String> siblingSsids,
                        List<String> revealedSsids,
                        List<UUID> alertTaps,
                        DateTime firstSeen,
                        DateTime lastSeen) {

        public boolean isPending() {
            return !isApproved && !isIgnored;
        }

    }

    public long count(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle -> count(handle, organizationId, tenantId));
    }

    public long count(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT COUNT(*) FROM " + TABLE + " " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(Long.class)
                .one();
    }

    public long countPending(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM " + TABLE + " " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND NOT is_approved AND NOT is_ignored")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public long countPendingFirstSeenBetween(UUID organizationId, UUID tenantId, DateTime from, DateTime to) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM " + TABLE + " " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND NOT is_approved AND NOT is_ignored " +
                                "AND first_seen >= :from AND first_seen <= :to")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("from", from)
                        .bind("to", to)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<Entry> findAll(UUID organizationId,
                               UUID tenantId,
                               OrderColumn orderColumn,
                               OrderDirection orderDirection,
                               int limit,
                               int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM " + TABLE + " " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "ORDER BY <order_column> <order_direction>, bssid ASC LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.columnName)
                        .define("order_direction", orderDirection)
                        .map((rs, ctx) -> entry(rs))
                        .list()
        );
    }

    public Optional<Entry> find(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM " + TABLE + " WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .map((rs, ctx) -> entry(rs))
                        .findOne()
        );
    }

    public Optional<Entry> findByBssid(String bssid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                findByBssids(handle, List.of(bssid), organizationId, tenantId).stream().findFirst());
    }

    public List<Entry> findByBssids(Handle handle, Collection<String> bssids, UUID organizationId, UUID tenantId) {
        if (bssids.isEmpty()) {
            return List.of();
        }

        return handle.createQuery("SELECT * FROM " + TABLE + " WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND bssid IN (SELECT unnest(CAST(:bssids AS text[])))")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("bssids", bssids.toArray(new String[0]))
                .map((rs, ctx) -> entry(rs))
                .list();
    }

    public int apply(List<UUID> uuids, Dot11.KnownNetworkBulkAction action, UUID organizationId, UUID tenantId) {
        if (uuids.isEmpty()) {
            return 0;
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate(action.sql(TABLE))
                        .bindList("uuids", uuids)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public int approveAllOfTenant(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate("UPDATE " + TABLE + " SET is_approved = true WHERE id IN (" +
                                "SELECT id FROM " + TABLE + " WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND NOT is_approved AND NOT is_ignored " +
                                "ORDER BY id FOR UPDATE)")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public int deleteAllOfTenant(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate("DELETE FROM " + TABLE + " WHERE id IN (SELECT id FROM " + TABLE + " " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "ORDER BY id FOR UPDATE)")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void retentionClean(DateTime since) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM " + TABLE + " WHERE id IN (SELECT id FROM " + TABLE + " " +
                                "WHERE last_seen < :since ORDER BY id FOR UPDATE)")
                        .bind("since", since)
                        .execute()
        );
    }

    /*
     * The access points with the most frames come first, so that a flood of made-up addresses is considered last.
     * Reports are read up to a moment ago: the names of a report are written after its BSSID.
     */
    public List<String> findSeenBetween(Handle handle, List<UUID> taps, DateTime from, DateTime to, int limit) {
        return handle.createQuery("SELECT b.bssid FROM dot11_bssids b WHERE b.tap_uuid IN (<taps>) " +
                        "AND b.created_at >= :from AND b.created_at <= :to AND " + HIDDEN_REPORT + " " +
                        "GROUP BY b.bssid ORDER BY SUM(b.hidden_ssid_frames) DESC, b.bssid ASC LIMIT :limit")
                .bindList("taps", taps)
                .bind("from", from)
                .bind("to", to)
                .bind("limit", limit)
                .mapTo(String.class)
                .list();
    }

    // Empty if no tap recorded the access point as hidden in the time range.
    public Optional<Sightings> findSightings(Handle handle, List<UUID> taps, String bssid, DateTime from, DateTime to) {
        List<Sightings> result = Lists.newArrayList();
        List<Sightings.Location> perLocation = Lists.newArrayList();

        // One row for each location the access point was seen at, followed by one row for the access point as a whole.
        handle.createQuery("SELECT GROUPING(t.location_uuid) = 1 AS is_total, " +
                        "COUNT(DISTINCT date_trunc('minute', b.created_at)) AS active_minutes, " +
                        "string_agg(DISTINCT b.tap_uuid::text, ',') AS taps " +
                        "FROM dot11_bssids b JOIN taps t ON t.uuid = b.tap_uuid " +
                        "WHERE b.bssid = :bssid AND b.tap_uuid IN (<taps>) " +
                        "AND b.created_at >= :from AND b.created_at <= :to AND " + HIDDEN_REPORT + " " +
                        "GROUP BY GROUPING SETS ((b.bssid), (b.bssid, t.location_uuid)) " +
                        "ORDER BY GROUPING(t.location_uuid)")
                .bind("bssid", bssid)
                .bindList("taps", taps)
                .bind("from", from)
                .bind("to", to)
                .map((rs, ctx) -> {
                    List<UUID> seenBy = Arrays.stream(rs.getString("taps").split(",")).map(UUID::fromString).toList();
                    int activeMinutes = rs.getInt("active_minutes");

                    if (rs.getBoolean("is_total")) {
                        result.add(new Sightings(bssid, activeMinutes, seenBy, Lists.newArrayList(perLocation)));
                    } else {
                        perLocation.add(new Sightings.Location(activeMinutes, seenBy));
                    }

                    return true;
                })
                .list();

        return result.stream().findFirst();
    }

    public void touch(Handle handle, UUID organizationId, UUID tenantId, Collection<String> bssids) {
        if (bssids.isEmpty()) {
            return;
        }

        // Rows are locked in the order of their ID, like the actions of users do it, so that the two cannot deadlock.
        handle.createUpdate("UPDATE " + TABLE + " SET last_seen = NOW() WHERE id IN (SELECT id FROM " + TABLE + " " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                        "AND bssid IN (SELECT unnest(CAST(:bssids AS text[]))) ORDER BY id FOR UPDATE)")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("bssids", bssids.toArray(new String[0]))
                .execute();
    }

    // False if the access point is known already or the list of the tenant is full.
    public boolean create(Handle handle, UUID organizationId, UUID tenantId, String bssid, Collection<UUID> alertTaps) {
        return handle.createUpdate("INSERT INTO " + TABLE + "(uuid, organization_id, tenant_id, bssid, " +
                        "is_approved, is_ignored, sibling_ssids, revealed_ssids, alert_taps, first_seen, last_seen) " +
                        "SELECT :uuid, :organization_id, :tenant_id, :bssid, false, false, '{}', '{}', :alert_taps, " +
                        "NOW(), NOW() WHERE (SELECT COUNT(*) FROM " + TABLE + " WHERE organization_id = " +
                        ":organization_id AND tenant_id = :tenant_id) < :maximum " +
                        "ON CONFLICT (organization_id, tenant_id, bssid) DO NOTHING")
                .bind("uuid", UUID.randomUUID())
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("bssid", bssid)
                .bind("alert_taps", alertTaps.toArray(new UUID[0]))
                .bind("maximum", MAX_ENTRIES_PER_TENANT)
                .execute() > 0;
    }

    /*
     * Those never evaluated come first, then those evaluated longest ago. An entry without taps to alert at is due in
     * every run, so that it follows a change of the dwell time at once. Such an entry has few reports to read.
     */
    public List<Entry> findPendingToEvaluate(UUID organizationId,
                                             UUID tenantId,
                                             DateTime seenSince,
                                             DateTime evaluatedBefore,
                                             DateTime runStartedAt,
                                             int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM " + TABLE + " WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND NOT is_approved AND NOT is_ignored " +
                                "AND last_seen >= :seen_since AND (alert_taps_evaluated_at IS NULL " +
                                "OR alert_taps_evaluated_at < :evaluated_before " +
                                "OR (cardinality(alert_taps) = 0 AND alert_taps_evaluated_at < :run_started_at)) " +
                                "ORDER BY alert_taps_evaluated_at ASC NULLS FIRST, id ASC LIMIT :limit")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("seen_since", seenSince)
                        .bind("evaluated_before", evaluatedBefore)
                        .bind("run_started_at", runStartedAt)
                        .bind("limit", limit)
                        .map((rs, ctx) -> entry(rs))
                        .list()
        );
    }

    // Null keeps the taps of the last evaluation.
    public void setEvaluated(Handle handle, long id, DateTime at, @Nullable Collection<UUID> alertTaps) {
        handle.createUpdate("UPDATE " + TABLE + " SET alert_taps_evaluated_at = :at, " +
                        "alert_taps = COALESCE(:alert_taps, alert_taps) WHERE id = :id")
                .bind("id", id)
                .bind("at", at)
                .bindBySqlType("alert_taps", alertTaps == null ? null : alertTaps.toArray(new UUID[0]), Types.ARRAY)
                .execute();
    }

    public List<Entry> findPendingWithAlertTaps(UUID organizationId, UUID tenantId, DateTime seenSince) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM " + TABLE + " WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND NOT is_approved AND NOT is_ignored " +
                                "AND last_seen >= :seen_since AND cardinality(alert_taps) > 0 ORDER BY id ASC")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("seen_since", seenSince)
                        .map((rs, ctx) -> entry(rs))
                        .list()
        );
    }

    // Those without hints come first, then those refreshed longest ago.
    public List<Entry> findWithHintsOlderThan(UUID organizationId, UUID tenantId, DateTime time, int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM " + TABLE + " WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND (hints_refreshed_at IS NULL " +
                                "OR hints_refreshed_at < :time) " +
                                "ORDER BY hints_refreshed_at ASC NULLS FIRST, id ASC LIMIT :limit")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("time", time)
                        .bind("limit", limit)
                        .map((rs, ctx) -> entry(rs))
                        .list()
        );
    }

    // The most advertised names of each address.
    public Map<String, List<String>> findRevealedSsids(Handle handle, List<UUID> taps, Collection<String> bssids,
                                                       DateTime since) {
        Map<String, List<String>> result = Maps.newHashMap();
        if (bssids.isEmpty() || taps.isEmpty()) {
            return result;
        }

        handle.createQuery("SELECT bssid, ssid FROM (SELECT n.*, row_number() OVER (PARTITION BY n.bssid " +
                        "ORDER BY n.frames DESC, n.ssid) AS position FROM (SELECT s.bssid, s.ssid, " +
                        "SUM(s.beacon_advertisements + s.proberesp_advertisements) AS frames FROM dot11_ssids s " +
                        "WHERE s.tap_uuid IN (<taps>) AND s.bssid = ANY(:bssids) AND s.created_at >= :since " +
                        "GROUP BY s.bssid, s.ssid) n) ranked WHERE position <= :limit ORDER BY bssid, ssid")
                .bindList("taps", taps)
                .bind("bssids", bssids.toArray(new String[0]))
                .bind("since", since)
                .bind("limit", MAX_HINTS)
                .map((rs, ctx) -> result.computeIfAbsent(rs.getString("bssid"), bssid -> Lists.newArrayList())
                        .add(rs.getString("ssid")))
                .list();

        return result;
    }

    // Null keeps the hints an entry has. The IDs have to be in ascending order, see touch().
    public void setHints(Handle handle, List<Long> ids, @Nullable Map<Long, List<String>> siblingSsids,
                         @Nullable Map<Long, List<String>> revealedSsids) {
        if (ids.isEmpty()) {
            return;
        }

        PreparedBatch batch = handle.prepareBatch("UPDATE " + TABLE + " SET hints_refreshed_at = NOW(), " +
                "sibling_ssids = COALESCE(:sibling_ssids, sibling_ssids), " +
                "revealed_ssids = COALESCE(:revealed_ssids, revealed_ssids) WHERE id = :id");

        for (long id : ids) {
            batch.bind("id", id)
                    .bindBySqlType("sibling_ssids", siblingSsids == null ? null
                            : siblingSsids.getOrDefault(id, List.of()).toArray(new String[0]), Types.ARRAY)
                    .bindBySqlType("revealed_ssids", revealedSsids == null ? null
                            : revealedSsids.getOrDefault(id, List.of()).toArray(new String[0]), Types.ARRAY)
                    .add();
        }

        batch.execute();
    }

    private static Entry entry(ResultSet rs) throws SQLException {
        return new Entry(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("bssid"),
                rs.getBoolean("is_approved"),
                rs.getBoolean("is_ignored"),
                Arrays.asList((String[]) rs.getArray("sibling_ssids").getArray()),
                Arrays.asList((String[]) rs.getArray("revealed_ssids").getArray()),
                rs.getArray("alert_taps") == null
                        ? List.of() : Arrays.asList((UUID[]) rs.getArray("alert_taps").getArray()),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen"))
        );
    }

}
