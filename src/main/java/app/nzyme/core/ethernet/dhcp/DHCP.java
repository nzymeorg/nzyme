package app.nzyme.core.ethernet.dhcp;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.AssetRegistryKeys;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.dhcp.db.DHCPFlakyFingerprintAssetEntry;
import app.nzyme.core.ethernet.dhcp.db.DHCPStatisticsBucket;
import app.nzyme.core.ethernet.dhcp.db.DHCPTransactionEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DHCP {

    public enum OrderColumn {

        INITIATED_AT("first_packet"),
        TRANSACTION_TYPE("transaction_type"),
        CLIENT_MAC("client_mac"),
        SERVER_MAC("server_mac"),
        REQUESTED_IP_ADDRESS("requested_ip_address"),
        FINGERPRINT("fingerprint");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    private final NzymeNode nzyme;
    private final DHCPFingerprintExclusions fingerprintExclusions;

    public DHCP(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
        this.fingerprintExclusions = new DHCPFingerprintExclusions(this.nzyme);
    }

    public DHCPFingerprintExclusions fingerprintExclusions() {
        return fingerprintExclusions;
    }

    public boolean fingerprintMonitoringEnabled(UUID organizationId, UUID tenantId) {
        return Boolean.parseBoolean(nzyme.getDatabaseCoreRegistry()
                .getValue(AssetRegistryKeys.DHCP_FINGERPRINT_MONITORING_ENABLED.key(), organizationId, tenantId)
                .orElse(AssetRegistryKeys.DHCP_FINGERPRINT_MONITORING_ENABLED.defaultValue().orElseThrow()));
    }

    public long countAllTransactions(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DHCPFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT transaction_id) FROM dhcp_transactions " +
                                "WHERE latest_packet >= :tr_from AND latest_packet <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<DHCPTransactionEntry> findAllTransactions(TimeRange timeRange,
                                                          int limit,
                                                          int offset,
                                                          OrderColumn orderColumn,
                                                          OrderDirection orderDirection,
                                                          Filters filters,
                                                          List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DHCPFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT transaction_id, MIN(transaction_type) AS transaction_type, " +
                                "FLOOR(EXTRACT(EPOCH FROM first_packet)::numeric / 10) AS time_bucket, " +
                                "MIN(client_mac) AS client_mac, " +
                                "MIN(additional_client_macs::text)::jsonb AS additional_client_macs, " +
                                "MIN(server_mac) AS server_mac, " +
                                "MIN(additional_server_macs::text)::jsonb AS additional_server_macs, " +
                                "MIN(offered_ip_addresses::text)::jsonb AS offered_ip_addresses, " +
                                "MIN(requested_ip_address) AS requested_ip_address, " +
                                "MIN(options::text)::jsonb AS options, " +
                                "MIN(additional_options::text)::jsonb AS additional_options, " +
                                "MIN(fingerprint) AS fingerprint, " +
                                "MIN(additional_fingerprints::text)::jsonb AS additional_fingerprints, " +
                                "MIN(vendor_class) AS vendor_class, " +
                                "MIN(additional_vendor_classes::text)::jsonb AS additional_vendor_classes, " +
                                "MIN(timestamps::text)::jsonb AS timestamps, MAX(first_packet) AS first_packet, " +
                                "MAX(latest_packet) AS latest_packet, MIN(notes::text)::jsonb AS notes, " +
                                "jsonb_agg(server_responses) AS server_responses, " +
                                "MIN(selected_server_address) AS selected_server_address, " +
                                "BOOL_OR(is_successful) AS is_successful, BOOL_OR(is_complete) AS is_complete " +
                                "FROM dhcp_transactions " +
                                "WHERE latest_packet >= :tr_from AND latest_packet <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY transaction_id, time_bucket " +
                                "ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(DHCPTransactionEntry.class)
                        .list()
        );
    }

    public Optional<DHCPTransactionEntry> findTransaction(long transactionId, DateTime transactionTime, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT transaction_id, MIN(transaction_type) AS transaction_type, " +
                                "MIN(client_mac) AS client_mac, " +
                                "MIN(additional_client_macs::text)::jsonb AS additional_client_macs, " +
                                "MIN(server_mac) AS server_mac, " +
                                "MIN(additional_server_macs::text)::jsonb AS additional_server_macs, " +
                                "MIN(offered_ip_addresses::text)::jsonb AS offered_ip_addresses, " +
                                "MIN(requested_ip_address) AS requested_ip_address, " +
                                "MIN(options::text)::jsonb AS options, " +
                                "MIN(additional_options::text)::jsonb AS additional_options, " +
                                "MIN(fingerprint) AS fingerprint, " +
                                "MIN(additional_fingerprints::text)::jsonb AS additional_fingerprints, " +
                                "MIN(vendor_class) AS vendor_class, " +
                                "MIN(additional_vendor_classes::text)::jsonb AS additional_vendor_classes, " +
                                "MIN(timestamps::text)::jsonb AS timestamps, MAX(first_packet) AS first_packet, " +
                                "MAX(latest_packet) AS latest_packet, MIN(notes::text)::jsonb AS notes, " +
                                "jsonb_agg(server_responses) AS server_responses, " +
                                "MIN(selected_server_address) AS selected_server_address, " +
                                "BOOL_OR(is_successful) AS is_successful, BOOL_OR(is_complete) AS is_complete " +
                                "FROM dhcp_transactions " +
                                "WHERE transaction_id = :transaction_id " +
                                "AND (first_packet BETWEEN (:transaction_time::timestamptz - INTERVAL '10 seconds') " +
                                "AND (:transaction_time::timestamptz + INTERVAL '10 seconds'))" +
                                "AND tap_uuid IN (<taps>) " +
                                "GROUP BY transaction_id " +
                                "ORDER BY latest_packet DESC LIMIT 1")
                        .bind("transaction_id", transactionId)
                        .bind("transaction_time", transactionTime)
                        .bindList("taps", taps)
                        .mapTo(DHCPTransactionEntry.class)
                        .findOne()
        );
    }

    public List<DHCPStatisticsBucket> getStatistics(TimeRange timeRange,
                                                    Bucketing.BucketingConfiguration bucketing,
                                                    Filters filters,
                                                    List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DHCPFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT date_trunc(:date_trunc, first_packet) AS bucket, " +
                                "COUNT(*) AS total_transaction_count, " +
                                "COUNT(*) FILTER (WHERE is_complete = true AND is_successful = true) AS successful_transaction_count, " +
                                "COUNT(*) FILTER (WHERE is_complete = false OR is_successful = false) AS failed_transaction_count " +
                                "FROM dhcp_transactions WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY bucket ORDER BY bucket DESC")
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(DHCPStatisticsBucket.class)
                        .list()
        );
    }

    // Transactions are de-duplicated across taps the same way as in findAllTransactions().
    private static final String FLAKY_FINGERPRINTS_CTE =
            "WITH txs AS (" +
                "SELECT MIN(client_mac) AS client_mac, MIN(transaction_type) AS transaction_type, " +
                "MIN(fingerprint) AS fingerprint, MIN(first_packet) AS first_packet, " +
                "MAX(latest_packet) AS latest_packet " +
                "FROM dhcp_transactions " +
                "WHERE fingerprint IS NOT NULL " +
                "AND latest_packet >= :tr_from AND latest_packet <= :tr_to " +
                "AND tap_uuid IN (<taps>) " +
                "GROUP BY transaction_id, FLOOR(EXTRACT(EPOCH FROM first_packet)::numeric / 10)" +
            "), fps AS (" +
                "SELECT *, LAG(fingerprint) OVER (PARTITION BY client_mac, transaction_type " +
                "ORDER BY first_packet) AS previous_fingerprint FROM txs" +
            "), flaky AS (" +
                "SELECT client_mac, transaction_type FROM fps " +
                "GROUP BY client_mac, transaction_type HAVING COUNT(DISTINCT fingerprint) > 1" +
            ") ";

    public long countFlakyFingerprintAssets(TimeRange timeRange, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(FLAKY_FINGERPRINTS_CTE + "SELECT COUNT(DISTINCT client_mac) FROM flaky")
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<DHCPFlakyFingerprintAssetEntry> findFlakyFingerprintAssets(TimeRange timeRange,
                                                                           int limit,
                                                                           int offset,
                                                                           List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(FLAKY_FINGERPRINTS_CTE +
                                "SELECT f.client_mac, " +
                                "jsonb_agg(DISTINCT f.transaction_type) AS transaction_types, " +
                                "jsonb_agg(DISTINCT f.fingerprint) AS fingerprints, " +
                                "COUNT(DISTINCT f.fingerprint) AS fingerprint_count, " +
                                "COUNT(*) FILTER (WHERE f.previous_fingerprint IS NOT NULL " +
                                "AND f.previous_fingerprint <> f.fingerprint) AS change_count, " +
                                "COUNT(*) AS transaction_count, " +
                                "MIN(f.first_packet) AS first_seen, MAX(f.latest_packet) AS last_seen " +
                                "FROM fps f " +
                                "JOIN flaky fl ON fl.client_mac = f.client_mac " +
                                "AND fl.transaction_type = f.transaction_type " +
                                "GROUP BY f.client_mac " +
                                "ORDER BY change_count DESC, fingerprint_count DESC, last_seen DESC " +
                                "LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(DHCPFlakyFingerprintAssetEntry.class)
                        .list()
        );
    }

}
