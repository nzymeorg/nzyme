package app.nzyme.core.ethernet.portalintegrity;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.portalintegrity.db.PortalIntegrityReportEntry;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class PortalIntegrity {

    public enum ReportOrderColumn {

        PROBED_AT("probed_at");

        private final String columnName;

        ReportOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

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
                handle.createQuery("SELECT COUNT(*) FROM portal_integrity_reports " +
                                "WHERE probed_at >= :tr_from AND probed_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>)" + filterFragment.whereSql())
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
                handle.createQuery("SELECT r.uuid, r.control_url, r.probe_interface, r.probe_mac, r.probe_name, " +
                                "r.probed_at, r.error, d.assigned_address, d.gateway_address, d.dhcp_server_address, " +
                                "d.dns_servers::text[], " +
                                "(SELECT COUNT(*) FROM portal_integrity_hops h WHERE h.report_uuid = r.uuid) " +
                                "AS hop_count " +
                                "FROM portal_integrity_reports AS r " +
                                "LEFT JOIN portal_integrity_dhcp_leases AS d ON r.uuid = d.report_uuid " +
                                "WHERE r.probed_at >= :tr_from AND r.probed_at <= :tr_to " +
                                "AND r.tap_uuid IN (<taps>) " + filterFragment.whereSql() +
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

}
