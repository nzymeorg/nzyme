package app.nzyme.core.ethernet.portalintegrity;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.portalintegrity.db.PortalIntegrityControlUrlEntry;
import app.nzyme.core.ethernet.portalintegrity.db.PortalIntegrityReportEntry;
import app.nzyme.core.ethernet.portalintegrity.db.PortalIntegrityReportHopEntry;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;

import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import org.jdbi.v3.core.statement.UnableToExecuteStatementException;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public class PortalIntegrity {

    public enum ReportOrderColumn {

        UUID("uuid"),
        PROBE_NAME("probe_name"),
        CONTROL_URL("control_url"),
        LAST_HOP_URL("last_hop_url"),
        HOP_COUNT("hop_count"),
        PROBED_AT("probed_at");

        private final String columnName;

        ReportOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    public static final int MAX_CONTROL_URLS_PER_TAP = 200;
    public static final int MAX_EXPECTED_VALUES = 25;
    public static final int MAX_PROBE_NAME_LENGTH = 255;

    public static final int MAX_OBSERVED_REPORTS = 1000;
    public static final int MAX_OBSERVED_OUTCOMES = 50;

    // A probe follows as many redirects as its configuration says. More hops than this of a report are not read.
    private static final int MAX_OBSERVED_HOPS = 64;
    private static final int OBSERVED_STATE_TIMEOUT_MS = 10_000;
    private static final String QUERY_CANCELED = "57014";

    public static class ObservedStateTimeoutException extends RuntimeException {}

    private static final int MAX_REPORT_INTERVAL_SECONDS = 30 * 24 * 60 * 60;

    // Longer first responses are never the answer of a connectivity check and are not loaded to find that out.
    private static final int MAX_JUDGED_RESPONSE_BYTES = 16 * 1024;

    public enum ObservedKind {
        PORTAL,
        NO_PORTAL,
        FAILED
    }

    // One thing the reports of a control URL showed. All failed requests are one outcome, with the latest error.
    public record ObservedOutcome(ObservedKind kind,
                                  PortalIntegrityJudge.Observation observation,
                                  long reportCount,
                                  DateTime firstSeen,
                                  DateTime lastSeen) {}

    public record ObservedState(int reportCount, boolean reportsLimited, List<ObservedOutcome> outcomes,
                                boolean outcomesLimited) {}

    private record ObservedHop(UUID reportUuid, DateTime probedAt, @Nullable String error,
                               @Nullable PortalIntegrityJudge.Hop hop) {}

    private final NzymeNode nzyme;

    public PortalIntegrity(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
    }

    public long countAllIntegrityReports(TimeRange timeRange,
                                         Filters filters,
                                         List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new PortalIntegrityReportFilters());
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT r.uuid, r.control_url, r.probe_interface, r.probe_mac, r.probe_name, " +
                                "r.probed_at, r.error, host(d.assigned_address)::inet AS assigned_address, " +
                                "d.gateway_address, d.dhcp_server_address, d.dns_servers::text[] AS dns_servers, " +
                                "(SELECT COUNT(*) FROM portal_integrity_hops h WHERE h.report_uuid = r.uuid) " +
                                "AS hop_count, " +
                                "(SELECT h.url FROM portal_integrity_hops h WHERE h.report_uuid = r.uuid " +
                                "ORDER BY h.hop_index DESC LIMIT 1) AS last_hop_url " +
                                "FROM portal_integrity_reports AS r " +
                                "LEFT JOIN portal_integrity_dhcp_leases AS d ON r.uuid = d.report_uuid " +
                                "WHERE r.probed_at >= :tr_from AND r.probed_at <= :tr_to " +
                                "AND r.tap_uuid IN (<taps>) " +
                                ") AS r WHERE 1=1 " + filterFragment.whereSql())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<PortalIntegrityReportEntry> findAllIntegrityReports(TimeRange timeRange,
                                                                    Filters filters,
                                                                    ReportOrderColumn orderColumn,
                                                                    OrderDirection orderDirection,
                                                                    int limit,
                                                                    int offset,
                                                                    List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new PortalIntegrityReportFilters());
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM (" +
                                "SELECT r.uuid, r.control_url, r.probe_interface, r.probe_mac, r.probe_name, " +
                                "r.probed_at, r.error, host(d.assigned_address)::inet AS assigned_address, " +
                                "d.gateway_address, d.dhcp_server_address, " +
                                "CASE WHEN d.dns_servers IS NULL THEN NULL ELSE " +
                                "ARRAY(SELECT host(s.ip) FROM unnest(d.dns_servers) WITH ORDINALITY AS s(ip, n) ORDER BY s.n) " +
                                "END AS dns_servers, " +
                                "(SELECT COUNT(*) FROM portal_integrity_hops h WHERE h.report_uuid = r.uuid) " +
                                "AS hop_count, " +
                                "(SELECT h.url FROM portal_integrity_hops h WHERE h.report_uuid = r.uuid " +
                                "ORDER BY h.hop_index DESC LIMIT 1) AS last_hop_url " +
                                "FROM portal_integrity_reports AS r " +
                                "LEFT JOIN portal_integrity_dhcp_leases AS d ON r.uuid = d.report_uuid " +
                                "WHERE r.probed_at >= :tr_from AND r.probed_at <= :tr_to " +
                                "AND r.tap_uuid IN (<taps>) " +
                                ") AS r WHERE 1=1 " + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(PortalIntegrityReportEntry.class)
                        .list()
        );
    }

    public Optional<PortalIntegrityReportEntry> findOneIntegrityReport(UUID uuid, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT r.uuid, r.control_url, r.probe_interface, r.probe_mac, r.probe_name, " +
                                "r.probed_at, r.error, host(d.assigned_address)::inet AS assigned_address, " +
                                "d.gateway_address, d.dhcp_server_address, " +
                                "CASE WHEN d.dns_servers IS NULL THEN NULL ELSE " +
                                "ARRAY(SELECT host(s.ip) FROM unnest(d.dns_servers) WITH ORDINALITY AS s(ip, n) ORDER BY s.n) " +
                                "END AS dns_servers, " +
                                "(SELECT COUNT(*) FROM portal_integrity_hops h WHERE h.report_uuid = r.uuid) " +
                                "AS hop_count, " +
                                "(SELECT h.url FROM portal_integrity_hops h WHERE h.report_uuid = r.uuid " +
                                "ORDER BY h.hop_index DESC LIMIT 1) AS last_hop_url " +
                                "FROM portal_integrity_reports AS r " +
                                "LEFT JOIN portal_integrity_dhcp_leases AS d ON r.uuid = d.report_uuid " +
                                "WHERE r.uuid = :uuid AND r.tap_uuid IN (<taps>)")
                        .bind("uuid", uuid)
                        .bindList("taps", taps)
                        .mapTo(PortalIntegrityReportEntry.class)
                        .findOne()
        );
    }

    public List<PortalIntegrityReportHopEntry> findAllHopsOfIntegrityReport(UUID reportUuid) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM portal_integrity_hops " +
                                "WHERE report_uuid = :report_uuid ORDER BY hop_index ASC")
                        .bind("report_uuid", reportUuid)
                        .mapTo(PortalIntegrityReportHopEntry.class)
                        .list()
        );
    }

    /*
     * Keeps what the last report of a control URL showed. A new control URL is not recorded while its tap is at
     * the limit. The time is the one of the node: the clock of a tap can be wrong, and a time from the future
     * would keep every later report from counting.
     */
    public void recordObservation(UUID organizationId,
                                  UUID tenantId,
                                  UUID tapUuid,
                                  String probeName,
                                  String controlUrl,
                                  int ipVersion,
                                  UUID reportUuid,
                                  PortalIntegrityJudge.Observation observation) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO portal_integrity_control_urls AS c (uuid, organization_id, " +
                                "tenant_id, tap_uuid, probe_name, control_url, ip_version, last_report_uuid, " +
                                "last_report_at, last_error, last_hop_count, last_portal_presented, " +
                                "last_final_url, last_final_address, last_final_certificate, created_at, " +
                                "updated_at) " +
                                "SELECT :uuid, :organization_id, :tenant_id, :tap_uuid, :probe_name, :control_url, " +
                                ":ip_version, :last_report_uuid, NOW(), :last_error, :last_hop_count, " +
                                ":last_portal_presented, :last_final_url, :last_final_address, " +
                                ":last_final_certificate, NOW(), NOW() " +
                                "WHERE (SELECT COUNT(*) FROM portal_integrity_control_urls " +
                                "WHERE tap_uuid = :tap_uuid) < :maximum " +
                                "OR EXISTS (SELECT 1 FROM portal_integrity_control_urls WHERE tap_uuid = :tap_uuid " +
                                "AND probe_name = :probe_name AND control_url = :control_url " +
                                "AND ip_version = :ip_version) " +
                                "ON CONFLICT (tap_uuid, probe_name, control_url, ip_version) DO UPDATE SET " +
                                "last_report_uuid = EXCLUDED.last_report_uuid, " +
                                "last_report_at = EXCLUDED.last_report_at, " +
                                "report_interval_seconds = LEAST(:maximum_interval, GREATEST(0, EXTRACT(EPOCH FROM " +
                                "(EXCLUDED.last_report_at - c.last_report_at))::bigint))::integer, " +
                                "last_error = EXCLUDED.last_error, last_hop_count = EXCLUDED.last_hop_count, " +
                                "last_portal_presented = EXCLUDED.last_portal_presented, " +
                                "last_final_url = EXCLUDED.last_final_url, " +
                                "last_final_address = EXCLUDED.last_final_address, " +
                                "last_final_certificate = EXCLUDED.last_final_certificate")
                        .bind("uuid", UUID.randomUUID())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tap_uuid", tapUuid)
                        .bind("probe_name", probeName)
                        .bind("control_url", controlUrl)
                        .bind("ip_version", ipVersion)
                        .bind("last_report_uuid", reportUuid)
                        .bind("last_error", observation.error())
                        .bind("last_hop_count", observation.hopCount())
                        .bind("last_portal_presented", observation.portalPresented())
                        .bind("last_final_url", observation.finalUrl())
                        .bind("last_final_address", observation.finalAddress())
                        .bind("last_final_certificate", observation.finalCertificate())
                        .bind("maximum", MAX_CONTROL_URLS_PER_TAP)
                        .bind("maximum_interval", MAX_REPORT_INTERVAL_SECONDS)
                        .execute()
        );
    }

    /*
     * What the latest reports of a control URL in the time range showed, most recently seen first. Reports are
     * looked at with the rules that judge a report when it comes in.
     *
     * The innermost query walks the reports of the tap and control URL backwards in time and stops at its limit.
     * The IP version is only known from the lease and is decided after that: as a condition next to the others,
     * the planner takes it for rare, gives up on stopping early and reads every report of the time range.
     */
    public ObservedState findObservedState(PortalIntegrityControlUrlEntry entry, TimeRange timeRange) {
        Map<PortalIntegrityJudge.Observation, ObservedOutcome> outcomes = new LinkedHashMap<>();
        ObservedOutcome[] failed = new ObservedOutcome[1];
        int[] reports = new int[1];
        boolean[] limited = new boolean[2];

        try {
            nzyme.getDatabase().useTransaction(handle -> {
                handle.execute("SET LOCAL statement_timeout = " + OBSERVED_STATE_TIMEOUT_MS);

                int scanned = handle.createQuery("SELECT COUNT(*) FROM (SELECT 1 FROM portal_integrity_reports " +
                                "WHERE tap_uuid = :tap_uuid AND control_url = :control_url " +
                                "AND probe_name = :probe_name AND probed_at >= :tr_from AND probed_at <= :tr_to " +
                                "LIMIT :scan_limit) AS scanned")
                        .bind("tap_uuid", entry.tapUuid())
                        .bind("control_url", entry.controlUrl())
                        .bind("probe_name", entry.probeName())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("scan_limit", MAX_OBSERVED_REPORTS)
                        .mapTo(Integer.class)
                        .one();
                limited[0] = scanned >= MAX_OBSERVED_REPORTS;

                try (Stream<ObservedHop> rows = handle.createQuery("SELECT r.uuid, r.probed_at, r.error, " +
                                "left(h.url, :maximum_url) AS url, h.status, " +
                                "host(h.resolved_address) AS resolved_address, h.tls_leaf_sha256, " +
                                "CASE WHEN h.hop_index = 0 AND octet_length(h.raw) <= :maximum_response " +
                                "THEN h.raw END AS raw " +
                                "FROM (SELECT r.uuid, r.probed_at, r.error FROM (" +
                                "SELECT uuid, probed_at, error FROM portal_integrity_reports " +
                                "WHERE tap_uuid = :tap_uuid AND control_url = :control_url " +
                                "AND probe_name = :probe_name AND probed_at >= :tr_from AND probed_at <= :tr_to " +
                                "ORDER BY probed_at DESC LIMIT :scan_limit) AS r " +
                                "JOIN portal_integrity_dhcp_leases AS d ON d.report_uuid = r.uuid " +
                                "WHERE family(d.assigned_address) = :ip_version) AS r " +
                                "LEFT JOIN portal_integrity_hops AS h ON h.report_uuid = r.uuid " +
                                "AND h.hop_index < :maximum_hops " +
                                "ORDER BY r.probed_at DESC, r.uuid, h.hop_index")
                        .bind("tap_uuid", entry.tapUuid())
                        .bind("control_url", entry.controlUrl())
                        .bind("probe_name", entry.probeName())
                        .bind("ip_version", entry.ipVersion())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("maximum_url", PortalUrls.MAX_URL_LENGTH)
                        .bind("maximum_response", MAX_JUDGED_RESPONSE_BYTES)
                        .bind("maximum_hops", MAX_OBSERVED_HOPS)
                        .bind("scan_limit", MAX_OBSERVED_REPORTS)
                        .setFetchSize(500)
                        .map((rs, ctx) -> {
                            byte[] raw = rs.getBytes("raw");

                            return new ObservedHop(
                                    UUID.fromString(rs.getString("uuid")),
                                    new DateTime(rs.getTimestamp("probed_at")),
                                    rs.getString("error"),
                                    rs.getString("url") == null ? null : new PortalIntegrityJudge.Hop(
                                            rs.getString("url"),
                                            rs.getInt("status"),
                                            rs.getString("resolved_address"),
                                            rs.getString("tls_leaf_sha256"),
                                            raw == null ? new byte[0] : raw
                                    )
                            );
                        })
                        .stream()) {
                    // Rows come one report after the other. Only the hops of the current report are held.
                    List<ObservedHop> current = new ArrayList<>();
                    Iterator<ObservedHop> iterator = rows.iterator();
                    while (iterator.hasNext()) {
                        ObservedHop row = iterator.next();
                        if (!current.isEmpty() && !current.get(0).reportUuid().equals(row.reportUuid())) {
                            limited[1] |= !observe(entry, current, outcomes, failed);
                            reports[0]++;
                            current.clear();
                        }
                        current.add(row);
                    }

                    if (!current.isEmpty()) {
                        limited[1] |= !observe(entry, current, outcomes, failed);
                        reports[0]++;
                    }
                }
            });
        } catch (UnableToExecuteStatementException e) {
            if (e.getCause() instanceof SQLException sql && QUERY_CANCELED.equals(sql.getSQLState())) {
                throw new ObservedStateTimeoutException();
            }

            throw e;
        }

        List<ObservedOutcome> result = new ArrayList<>(outcomes.values());
        if (failed[0] != null) {
            result.add(failed[0]);
        }
        result.sort(Comparator.comparing(ObservedOutcome::lastSeen).reversed());

        return new ObservedState(reports[0], limited[0], result, limited[1]);
    }

    // The rows of one report, newest report first across calls. False if the outcome did not fit any more.
    private static boolean observe(PortalIntegrityControlUrlEntry entry,
                                   List<ObservedHop> rows,
                                   Map<PortalIntegrityJudge.Observation, ObservedOutcome> outcomes,
                                   ObservedOutcome[] failed) {
        ObservedHop first = rows.get(0);
        List<PortalIntegrityJudge.Hop> hops = new ArrayList<>();
        for (ObservedHop row : rows) {
            if (row.hop() != null) {
                hops.add(row.hop());
            }
        }

        PortalIntegrityJudge.Observation seen = PortalIntegrityJudge.observe(entry.controlUrl(), first.error(), hops);

        if (seen.error() != null || seen.portalPresented() == null) {
            failed[0] = failed[0] == null
                    ? new ObservedOutcome(ObservedKind.FAILED, seen, 1, first.probedAt(), first.probedAt())
                    : new ObservedOutcome(ObservedKind.FAILED, failed[0].observation(), failed[0].reportCount() + 1,
                    first.probedAt(), failed[0].lastSeen());
            return true;
        }

        // Hop counts differ between reports of the same outcome, for example after a portal adds a redirect.
        PortalIntegrityJudge.Observation key = new PortalIntegrityJudge.Observation(
                null, 0, seen.portalPresented(), seen.finalUrl(), seen.finalAddress(), seen.finalCertificate()
        );

        ObservedOutcome known = outcomes.get(key);
        if (known != null) {
            outcomes.put(key, new ObservedOutcome(known.kind(), key, known.reportCount() + 1, first.probedAt(),
                    known.lastSeen()));
            return true;
        }

        if (outcomes.size() >= MAX_OBSERVED_OUTCOMES) {
            return false;
        }

        outcomes.put(key, new ObservedOutcome(
                seen.portalPresented() ? ObservedKind.PORTAL : ObservedKind.NO_PORTAL, key, 1,
                first.probedAt(), first.probedAt()
        ));

        return true;
    }

    public long countAllControlUrls(UUID organizationId, UUID tenantId, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM portal_integrity_control_urls " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND tap_uuid IN (<taps>)")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindList("taps", taps)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<PortalIntegrityControlUrlEntry> findAllControlUrls(UUID organizationId,
                                                                   UUID tenantId,
                                                                   List<UUID> taps,
                                                                   int limit,
                                                                   int offset) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM portal_integrity_control_urls " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND tap_uuid IN (<taps>) " +
                                "ORDER BY probe_name, control_url, ip_version, id LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindList("taps", taps)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(PortalIntegrityControlUrlEntry.class)
                        .list()
        );
    }

    public Optional<PortalIntegrityControlUrlEntry> findControlUrl(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM portal_integrity_control_urls WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(PortalIntegrityControlUrlEntry.class)
                        .findOne()
        );
    }

    public List<UUID> findTapsAtControlUrlLimit(List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT tap_uuid FROM portal_integrity_control_urls " +
                                "WHERE tap_uuid IN (<taps>) GROUP BY tap_uuid HAVING COUNT(*) >= :maximum")
                        .bindList("taps", taps)
                        .bind("maximum", MAX_CONTROL_URLS_PER_TAP)
                        .mapTo(UUID.class)
                        .list()
        );
    }

    // Monitored control URLs of taps that still exist, for the monitor.
    public List<PortalIntegrityControlUrlEntry> findAllMonitoredControlUrls(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT c.* FROM portal_integrity_control_urls AS c " +
                                "JOIN taps AS t ON t.uuid = c.tap_uuid AND t.deleted = false " +
                                "WHERE c.organization_id = :organization_id AND c.tenant_id = :tenant_id " +
                                "AND c.expectation <> :not_monitored ORDER BY c.id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("not_monitored", PortalExpectation.NOT_MONITORED.name())
                        .mapTo(PortalIntegrityControlUrlEntry.class)
                        .list()
        );
    }

    // What the taps of a tenant last said about the probes they run.
    public PortalProbeConfigurations findProbeConfigurations(UUID organizationId, UUID tenantId) {
        PortalProbeConfigurations probes = new PortalProbeConfigurations();

        nzyme.getDatabase().useHandle(handle ->
                handle.createQuery("SELECT uuid, portal_integrity_probes::text AS probes FROM taps " +
                                "WHERE deleted = false AND organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .map((rs, ctx) -> Map.entry(UUID.fromString(rs.getString("uuid")),
                                Optional.ofNullable(rs.getString("probes"))))
                        .forEach(tap -> probes.put(tap.getKey(), tap.getValue().orElse(null)))
        );

        return probes;
    }

    public void setExpectation(long id,
                               PortalExpectation expectation,
                               List<String> expectedFinalUrls,
                               List<String> expectedAddresses,
                               List<String> expectedCertificates) {
        nzyme.getDatabase().useHandle(handle ->
                // A control URL that was not monitored starts over with the time its probe has to report.
                handle.createUpdate("UPDATE portal_integrity_control_urls SET in_tap_configuration_since = " +
                                "CASE WHEN expectation = :not_monitored THEN NULL " +
                                "ELSE in_tap_configuration_since END, expectation = :expectation, " +
                                "expected_final_urls = :expected_final_urls::text[], " +
                                "expected_addresses = :expected_addresses::text[], " +
                                "expected_certificates = :expected_certificates::text[], updated_at = NOW() " +
                                "WHERE id = :id")
                        .bind("not_monitored", PortalExpectation.NOT_MONITORED.name())
                        .bind("expectation", expectation.name())
                        .bindBySqlType("expected_final_urls", expectedFinalUrls.toArray(new String[0]), Types.ARRAY)
                        .bindBySqlType("expected_addresses", expectedAddresses.toArray(new String[0]), Types.ARRAY)
                        .bindBySqlType("expected_certificates", expectedCertificates.toArray(new String[0]), Types.ARRAY)
                        .bind("id", id)
                        .execute()
        );
    }

    /*
     * Adds values to the expected lists of a control URL that expects a portal. False if a list would grow
     * past its limit, in which case nothing is changed.
     */
    public boolean addExpectedValues(long id,
                                     @Nullable String finalUrl,
                                     @Nullable String address,
                                     @Nullable String certificate) {
        return nzyme.getDatabase().inTransaction(handle -> {
            Optional<PortalIntegrityControlUrlEntry> entry = handle.createQuery(
                            "SELECT * FROM portal_integrity_control_urls WHERE id = :id FOR UPDATE")
                    .bind("id", id)
                    .mapTo(PortalIntegrityControlUrlEntry.class)
                    .findOne();

            if (entry.isEmpty()) {
                return false;
            }

            Set<String> urls = new LinkedHashSet<>(entry.get().expectedFinalUrls());
            Set<String> addresses = new LinkedHashSet<>(entry.get().expectedAddresses());
            Set<String> certificates = new LinkedHashSet<>(entry.get().expectedCertificates());
            if (finalUrl != null) {
                urls.add(finalUrl);
            }
            if (address != null) {
                addresses.add(address);
            }
            if (certificate != null) {
                certificates.add(certificate);
            }

            if (urls.size() > MAX_EXPECTED_VALUES || addresses.size() > MAX_EXPECTED_VALUES
                    || certificates.size() > MAX_EXPECTED_VALUES) {
                return false;
            }

            handle.createUpdate("UPDATE portal_integrity_control_urls SET " +
                            "expected_final_urls = :expected_final_urls::text[], " +
                            "expected_addresses = :expected_addresses::text[], " +
                            "expected_certificates = :expected_certificates::text[], updated_at = NOW() " +
                            "WHERE id = :id")
                    .bindBySqlType("expected_final_urls", urls.toArray(new String[0]), Types.ARRAY)
                    .bindBySqlType("expected_addresses", addresses.toArray(new String[0]), Types.ARRAY)
                    .bindBySqlType("expected_certificates", certificates.toArray(new String[0]), Types.ARRAY)
                    .bind("id", id)
                    .execute();

            return true;
        });
    }

    // When the monitor first found the tap running the probe of a control URL, or null once it no longer does.
    public void setInTapConfigurationSince(long id, @Nullable DateTime since) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE portal_integrity_control_urls SET in_tap_configuration_since = :since " +
                                "WHERE id = :id")
                        .bind("since", since)
                        .bind("id", id)
                        .execute()
        );
    }

    public void deleteControlUrl(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM portal_integrity_control_urls WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

}
