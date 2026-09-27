package app.nzyme.core.ethernet.arp;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.AssetPairNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnHistogramOrderColumn;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.arp.db.ARPStatisticsBucket;
import app.nzyme.core.ethernet.arp.db.ArpPacketEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ARP {

    public enum OrderColumn {

        TIMESTAMP("timestamp"),
        ETHERNET_SOURCE_MAC("ethernet_source_mac"),
        ETHERNET_DESTINATION_MAC("ethernet_destination_mac"),
        HARDWARE_TYPE("hardware_type"),
        PROTOCOL_TYPE("protocol_type"),
        OPERATION("operation"),
        ARP_SENDER_MAC("arp_sender_mac"),
        ARP_SENDER_ADDRESS("arp_sender_address"),
        ARP_TARGET_MAC("arp_target_mac"),
        ARP_TARGET_ADDRESS("arp_target_address");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    private final NzymeNode nzyme;

    public ARP(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
    }

    public long countAllPackets(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ARPFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM arp_packets " +
                                "WHERE timestamp >= :tr_from AND timestamp <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }


    public List<ArpPacketEntry> findAllPackets(TimeRange timeRange,
                                               Filters filters,
                                               int limit,
                                               int offset,
                                               OrderColumn orderColumn,
                                               OrderDirection orderDirection,
                                               List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ARPFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM arp_packets " +
                                "WHERE timestamp >= :tr_from AND timestamp <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
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
                        .mapTo(ArpPacketEntry.class)
                        .list()
        );
    }

    public List<ARPStatisticsBucket> getStatistics(TimeRange timeRange,
                                                   Bucketing.BucketingConfiguration bucketing,
                                                   Filters filters,
                                                   List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ARPFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT date_trunc(:date_trunc, timestamp) AS bucket, " +
                                "COUNT(*) AS total_count, COUNT(*) FILTER (WHERE operation = 'Request') " +
                                "AS request_count, " +
                                "COUNT(*) FILTER (WHERE operation = 'Reply') AS reply_count, " +
                                "CASE WHEN COUNT(*) FILTER (WHERE operation = 'Reply') = 0 THEN NULL ELSE " +
                                "ROUND(COUNT(*) FILTER (WHERE operation = 'Request')::numeric / COUNT(*) " +
                                "FILTER (WHERE operation = 'Reply'), 2) END AS request_to_reply_ratio, " +
                                "COUNT(*) FILTER (WHERE operation = 'Request' " +
                                "AND arp_sender_address = arp_target_address) AS gratuitous_request_count, " +
                                "COUNT(*) FILTER ( WHERE operation = 'Reply' AND " +
                                "arp_sender_address = arp_target_address) AS gratuitous_reply_count " +
                                "FROM arp_packets WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>)" + filterFragment.whereSql() + " " +
                                "GROUP BY bucket ORDER BY bucket DESC")
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(ARPStatisticsBucket.class)
                        .list()
        );
    }

    public long countPairs(String operation, TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ARPFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT(arp_sender_mac, arp_target_mac)) " +
                                "FROM arp_packets WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND operation = :operation AND tap_uuid IN (<taps>)" + filterFragment.whereSql())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("operation", operation)
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<AssetPairNumberAggregationResult> getPairs(String operation,
                                                           TimeRange timeRange,
                                                           Filters filters,
                                                           int limit,
                                                           int offset,
                                                           ThreeColumnHistogramOrderColumn orderColumn,
                                                           OrderDirection orderDirection,
                                                           List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ARPFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT arp_sender_mac AS value1, arp_target_mac AS value2, COUNT(*) AS value3 " +
                                "FROM arp_packets WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND operation = :operation AND tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                                "GROUP BY value1, value2 " +
                                "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("operation", operation)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(AssetPairNumberAggregationResult.class)
                        .list()
        );
    }

}
