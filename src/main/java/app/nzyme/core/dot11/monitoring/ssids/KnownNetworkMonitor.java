package app.nzyme.core.dot11.monitoring.ssids;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.AlertOrigin;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class KnownNetworkMonitor extends DwellTimeMonitor {

    private static final String OF_TENANT = "organization_id = :organization_id AND tenant_id = :tenant_id";
    private static final String PENDING = "NOT is_approved AND NOT is_ignored";

    // A list of thousands of names is joined as a set. Compared with ANY, every row would be held against each name.
    private static final String IN_SSIDS = "IN (SELECT unnest(CAST(:ssids AS text[])))";

    public KnownNetworkMonitor(NzymeNode nzyme) {
        super(nzyme);
    }

    @Override
    protected String subjects() {
        return "SSIDs";
    }

    @Override
    protected String name() {
        return "dot11_known_networks";
    }

    @Override
    protected List<String> findSeen(Handle handle, Tenant tenant, DateTime from, DateTime to, int limit) {
        return handle.createQuery("SELECT s.ssid FROM dot11_ssids s WHERE s.created_at >= :from " +
                        "AND s.created_at <= :to AND s.tap_uuid IN (<taps>) GROUP BY s.ssid " +
                        "ORDER BY SUM(s.beacon_advertisements + s.proberesp_advertisements) DESC, s.ssid ASC " +
                        "LIMIT :limit")
                .bindList("taps", tenant.taps())
                .bind("from", from)
                .bind("to", to)
                .bind("limit", limit)
                .mapTo(String.class)
                .list();
    }

    @Override
    protected Set<String> touchKnown(Handle handle, Tenant tenant, List<String> seen) {
        if (seen.isEmpty()) {
            return Set.of();
        }

        Set<String> known = Sets.newHashSet(handle.createQuery("SELECT DISTINCT ssid FROM dot11_known_networks " +
                        "WHERE " + OF_TENANT + " AND ssid " + IN_SSIDS)
                .bind("organization_id", tenant.organizationId())
                .bind("tenant_id", tenant.tenantId())
                .bind("ssids", seen.toArray(new String[0]))
                .mapTo(String.class)
                .list());

        // Rows are locked in the order of their ID, like the actions of users do it, so that the two cannot deadlock.
        handle.createUpdate("UPDATE dot11_known_networks SET last_seen = NOW() WHERE id IN (SELECT id " +
                        "FROM dot11_known_networks WHERE " + OF_TENANT + " AND ssid " + IN_SSIDS + " " +
                        "ORDER BY id FOR UPDATE)")
                .bind("organization_id", tenant.organizationId())
                .bind("tenant_id", tenant.tenantId())
                .bind("ssids", known.toArray(new String[0]))
                .execute();

        return known;
    }

    @Override
    protected Optional<Sightings> findSightings(Handle handle, Tenant tenant, String ssid, DateTime from, DateTime to) {
        List<Sightings> result = Lists.newArrayList();
        List<Sightings.Location> perLocation = Lists.newArrayList();

        /*
         * One row for each location the SSID was seen at, followed by one row for the SSID as a whole. Only the
         * index on SSID and time finds the reports of one SSID. The row comparison cannot be served by another index,
         * and the tap is compared as text so that the index on tap, BSSID and SSID is no option either. It would
         * read all reports of the taps. A widespread SSID has millions of reports a day, so they are cut down to
         * the minutes of each tap before anything else is done with them.
         */
        handle.createQuery("SELECT GROUPING(t.location_uuid) = 1 AS is_total, " +
                        "COUNT(DISTINCT m.minute) AS active_minutes, " +
                        "string_agg(DISTINCT m.tap_uuid::text, ',') AS taps " +
                        "FROM (SELECT DISTINCT date_trunc('minute', s.created_at) AS minute, s.tap_uuid " +
                        "FROM dot11_ssids s WHERE (s.ssid, s.created_at) >= (:ssid, :from) " +
                        "AND (s.ssid, s.created_at) <= (:ssid, :to) AND s.tap_uuid::text = ANY(:tap_ids)) m " +
                        "JOIN taps t ON t.uuid = m.tap_uuid " +
                        "GROUP BY GROUPING SETS ((), (t.location_uuid)) HAVING COUNT(*) > 0 " +
                        "ORDER BY GROUPING(t.location_uuid)")
                .bind("ssid", ssid)
                .bind("tap_ids", tenant.taps().stream().map(UUID::toString).toArray(String[]::new))
                .bind("from", from)
                .bind("to", to)
                .map((rs, ctx) -> {
                    List<UUID> seenBy = Arrays.stream(rs.getString("taps").split(",")).map(UUID::fromString).toList();
                    int activeMinutes = rs.getInt("active_minutes");

                    if (rs.getBoolean("is_total")) {
                        result.add(new Sightings(ssid, activeMinutes, seenBy, Lists.newArrayList(perLocation)));
                    } else {
                        perLocation.add(new Sightings.Location(activeMinutes, seenBy));
                    }

                    return true;
                })
                .list();

        return result.stream().findFirst();
    }

    // No other node creates known networks of the tenant while this one holds its lock.
    @Override
    protected boolean create(Handle handle, Tenant tenant, String ssid, List<UUID> alertTaps) {
        return handle.createUpdate("INSERT INTO dot11_known_networks(uuid, ssid, is_approved, is_ignored, " +
                        "organization_id, tenant_id, first_seen, last_seen, alert_taps) SELECT :uuid, :ssid, false, " +
                        "false, :organization_id, :tenant_id, NOW(), NOW(), :alert_taps WHERE NOT EXISTS (SELECT 1 " +
                        "FROM dot11_known_networks WHERE " + OF_TENANT + " AND ssid = :ssid)")
                .bind("uuid", UUID.randomUUID())
                .bind("ssid", ssid)
                .bind("organization_id", tenant.organizationId())
                .bind("tenant_id", tenant.tenantId())
                .bind("alert_taps", alertTaps.toArray(new UUID[0]))
                .execute() > 0;
    }

    @Override
    protected List<Pending> findPendingToEvaluate(Tenant tenant, DateTime evaluatedBefore, int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT id, ssid FROM dot11_known_networks WHERE " + OF_TENANT + " " +
                                "AND " + PENDING + " AND last_seen >= :seen_since " +
                                "AND (alert_taps_evaluated_at IS NULL OR alert_taps_evaluated_at < :evaluated_before " +
                                "OR (cardinality(alert_taps) = 0 AND alert_taps_evaluated_at < :run_started_at)) " +
                                "ORDER BY alert_taps_evaluated_at ASC NULLS FIRST, id ASC LIMIT :limit")
                        .bind("organization_id", tenant.organizationId())
                        .bind("tenant_id", tenant.tenantId())
                        .bind("seen_since", tenant.dwellSince())
                        .bind("evaluated_before", evaluatedBefore)
                        .bind("run_started_at", tenant.now())
                        .bind("limit", limit)
                        .map((rs, ctx) -> new Pending(rs.getLong("id"), rs.getString("ssid")))
                        .list()
        );
    }

    @Override
    protected void setEvaluated(Handle handle, long id, DateTime at, @Nullable Collection<UUID> alertTaps) {
        handle.createUpdate("UPDATE dot11_known_networks SET alert_taps_evaluated_at = :at, " +
                        "alert_taps = COALESCE(:alert_taps, alert_taps) WHERE id = :id")
                .bind("id", id)
                .bind("at", at)
                .bindBySqlType("alert_taps", alertTaps == null ? null : alertTaps.toArray(new UUID[0]), Types.ARRAY)
                .execute();
    }

    @Override
    protected List<Alerting> findAlerting(Tenant tenant) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT ssid, alert_taps FROM dot11_known_networks WHERE " + OF_TENANT + " " +
                                "AND " + PENDING + " AND last_seen >= :seen_since AND cardinality(alert_taps) > 0 " +
                                "ORDER BY id ASC")
                        .bind("organization_id", tenant.organizationId())
                        .bind("tenant_id", tenant.tenantId())
                        .bind("seen_since", tenant.dwellSince())
                        .map((rs, ctx) -> new Alerting(rs.getString("ssid"),
                                Arrays.asList((UUID[]) rs.getArray("alert_taps").getArray()),
                                Map.of("ssid", rs.getString("ssid"))))
                        .list()
        );
    }

    @Override
    protected void raiseAlert(Tenant tenant, Alerting alerting, List<UUID> taps) {
        nzyme.getDetectionAlertService().raiseAlert(
                tenant.organizationId(),
                tenant.tenantId(),
                null,
                AlertOrigin.taps(taps),
                DetectionType.DOT11_UNAPPROVED_SSID,
                Subsystem.DOT11,
                "Unapproved SSID \"" + alerting.name() + "\" detected.",
                alerting.attributes(),
                Set.of("ssid")
        );
    }

}
