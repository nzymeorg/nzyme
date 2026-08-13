package app.nzyme.core.ethernet.l4;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.NumberNumberNumberAggregationResult;
import app.nzyme.core.database.generic.StringNumberNumberAggregationResult;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.l4.db.L4Numbers;
import app.nzyme.core.ethernet.l4.db.L4Session;
import app.nzyme.core.ethernet.l4.db.L4StatisticsBucket;
import app.nzyme.core.ethernet.l4.filters.L4Filters;
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

public class L4 {

    private final NzymeNode nzyme;

    public enum OrderColumn {

        SESSION_KEY("session_key"),
        STATE("state"),
        L4_TYPE("l4_type"),
        SOURCE_MAC("source_mac"),
        SOURCE_ADDRESS("source_address"),
        SOURCE_PORT("source_port"),
        DESTINATION_MAC("destination_mac"),
        DESTINATION_ADDRESS("destination_address"),
        DESTINATION_PORT("destination_port"),
        BYTES_RX_COUNT("bytes_rx_count"),
        BYTES_TX_COUNT("bytes_tx_count"),
        FINGERPRINT("fingerprint"),
        START_TIME("start_time"),
        END_TIME("end_time"),
        MOST_RECENT_SEGMENT_TIME("most_recent_segment_time"),
        DURATION("duration_ms");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    public L4(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
    }

    public long countAllSessions(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT session_key, l4_type, state FROM l4_sessions " +
                                "WHERE most_recent_segment_time >= :tr_from AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY session_key, l4_type, state " +
                                "HAVING 1=1 " + filterFragment.havingSql() + ") AS ignored")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<L4Session> findAllSessions(TimeRange timeRange,
                                           Filters filters,
                                           int limit,
                                           int offset,
                                           OrderColumn orderColumn,
                                           OrderDirection orderDirection,
                                           List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT session_key, l4_type, state, MIN(source_mac) AS source_mac, " +
                                "MIN(source_address) AS source_address, MIN(source_port) AS source_port, " +
                                "MIN(source_address_geo_asn_number) AS source_address_geo_asn_number, " +
                                "MIN(source_address_geo_asn_name) AS source_address_geo_asn_name, " +
                                "MIN(source_address_geo_asn_domain) AS source_address_geo_asn_domain, " +
                                "MIN(source_address_geo_city) AS source_address_geo_city, " +
                                "MIN(source_address_geo_country_code) AS source_address_geo_country_code, " +
                                "MIN(source_address_geo_latitude) AS source_address_geo_latitude, " +
                                "MIN(source_address_geo_longitude) AS source_address_geo_longitude, " +
                                "BOOL_AND(source_address_is_site_local) AS source_address_is_site_local, " +
                                "BOOL_AND(source_address_is_loopback) AS source_address_is_loopback, " +
                                "BOOL_AND(source_address_is_multicast) AS source_address_is_multicast, " +
                                "MIN(destination_mac) AS destination_mac, " +
                                "MIN(destination_address) AS destination_address, " +
                                "MIN(destination_port) AS destination_port, " +
                                "MIN(destination_address_geo_asn_number) AS destination_address_geo_asn_number, " +
                                "MIN(destination_address_geo_asn_name) AS destination_address_geo_asn_name, " +
                                "MIN(destination_address_geo_asn_domain) AS destination_address_geo_asn_domain, " +
                                "MIN(destination_address_geo_city) AS destination_address_geo_city, " +
                                "MIN(destination_address_geo_country_code) AS destination_address_geo_country_code, " +
                                "MIN(destination_address_geo_latitude) AS destination_address_geo_latitude, " +
                                "MIN(destination_address_geo_longitude) AS destination_address_geo_longitude, " +
                                "BOOL_AND(destination_address_is_site_local) AS destination_address_is_site_local, " +
                                "BOOL_AND(destination_address_is_loopback) AS destination_address_is_loopback, " +
                                "BOOL_AND(destination_address_is_multicast) AS destination_address_is_multicast, " +
                                "MIN(fingerprint) AS fingerprint, MIN(tags::text)::jsonb AS tags, " +
                                "MAX(bytes_rx_count) AS bytes_rx_count, MAX(bytes_tx_count) AS bytes_tx_count, " +
                                "MAX(segments_count) AS segments_count, " +
                                "MIN(start_time) AS start_time, MAX(end_time) AS end_time, " +
                                "MAX(most_recent_segment_time) AS most_recent_segment_time, " +
                                "(EXTRACT(EPOCH FROM (MAX(most_recent_segment_time) - MIN(start_time))) * 1000) AS duration_ms, " +
                                "MIN(created_at) AS created_at " +
                                "FROM l4_sessions " +
                                "WHERE most_recent_segment_time >= :tr_from AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY session_key, l4_type, state " +
                                "HAVING 1=1 " + filterFragment.havingSql() + " " +
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
                        .mapTo(L4Session.class)
                        .list()
        );
    }

    public Optional<L4Session> findSession(L4Type type, DateTime startTime, String sessionKey, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT session_key, MIN(l4_type) AS l4_type, " +
                                "MIN(state) AS state, MIN(source_mac) AS source_mac, " +
                                "MIN(source_address) AS source_address, MIN(source_port) AS source_port, " +
                                "MIN(source_address_geo_asn_number) AS source_address_geo_asn_number, " +
                                "MIN(source_address_geo_asn_name) AS source_address_geo_asn_name, " +
                                "MIN(source_address_geo_asn_domain) AS source_address_geo_asn_domain, " +
                                "MIN(source_address_geo_city) AS source_address_geo_city, " +
                                "MIN(source_address_geo_country_code) AS source_address_geo_country_code, " +
                                "MIN(source_address_geo_latitude) AS source_address_geo_latitude, " +
                                "MIN(source_address_geo_longitude) AS source_address_geo_longitude, " +
                                "BOOL_AND(source_address_is_site_local) AS source_address_is_site_local, " +
                                "BOOL_AND(source_address_is_loopback) AS source_address_is_loopback, " +
                                "BOOL_AND(source_address_is_multicast) AS source_address_is_multicast, " +
                                "MIN(destination_mac) AS destination_mac, " +
                                "MIN(destination_address) AS destination_address, " +
                                "MIN(destination_port) AS destination_port, " +
                                "MIN(destination_address_geo_asn_number) AS destination_address_geo_asn_number, " +
                                "MIN(destination_address_geo_asn_name) AS destination_address_geo_asn_name, " +
                                "MIN(destination_address_geo_asn_domain) AS destination_address_geo_asn_domain, " +
                                "MIN(destination_address_geo_city) AS destination_address_geo_city, " +
                                "MIN(destination_address_geo_country_code) AS destination_address_geo_country_code, " +
                                "MIN(destination_address_geo_latitude) AS destination_address_geo_latitude, " +
                                "MIN(destination_address_geo_longitude) AS destination_address_geo_longitude, " +
                                "BOOL_AND(destination_address_is_site_local) AS destination_address_is_site_local, " +
                                "BOOL_AND(destination_address_is_loopback) AS destination_address_is_loopback, " +
                                "BOOL_AND(destination_address_is_multicast) AS destination_address_is_multicast, " +
                                "MIN(fingerprint) AS fingerprint, MIN(tags::text)::jsonb AS tags, " +
                                "MAX(bytes_rx_count) AS bytes_rx_count, MAX(bytes_tx_count) AS bytes_tx_count, " +
                                "MAX(segments_count) AS segments_count, " +
                                "MIN(start_time) AS start_time, MAX(end_time) AS end_time, " +
                                "MAX(most_recent_segment_time) AS most_recent_segment_time, " +
                                "(EXTRACT(EPOCH FROM (MAX(most_recent_segment_time) - MIN(start_time))) * 1000) AS duration_ms, " +
                                "MIN(created_at) AS created_at " +
                                "FROM l4_sessions WHERE session_key = :session_key AND l4_type = :l4_type " +
                                "AND start_time >= :start_time_from AND start_time <= :start_time_to " +
                                "AND tap_uuid IN (<taps>) GROUP BY session_key LIMIT 1 ")
                        .bindList("taps", taps)
                        .bind("l4_type", type)
                        .bind("session_key", sessionKey)
                        .bind("start_time_from", startTime.minusMinutes(1))
                        .bind("start_time_to", startTime.plusMinutes(1))
                        .mapTo(L4Session.class)
                        .findOne()
        );
    }

    public List<L4StatisticsBucket> getStatistics(TimeRange timeRange,
                                                  Bucketing.BucketingConfiguration bucketing,
                                                  List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT date_trunc(:date_trunc, timestamp) AS bucket, " +
                                "SUM(bytes_rx_tcp) AS bytes_rx_tcp, SUM(bytes_tx_tcp) AS bytes_tx_tcp, " +
                                "SUM(bytes_rx_internal_tcp) AS bytes_rx_internal_tcp, " +
                                "SUM(bytes_tx_internal_tcp) AS bytes_tx_internal_tcp, " +
                                "SUM(bytes_rx_udp) AS bytes_rx_udp, SUM(bytes_tx_udp) AS bytes_tx_udp, " +
                                "SUM(bytes_rx_internal_udp) AS bytes_rx_internal_udp, " +
                                "SUM(bytes_tx_internal_udp) AS bytes_tx_internal_udp, " +
                                "SUM(segments_tcp) AS segments_tcp, SUM(datagrams_udp) AS datagrams_udp, " +
                                "MAX(sessions_tcp) AS sessions_tcp, MAX(sessions_udp) AS sessions_udp, " +
                                "MAX(sessions_internal_tcp) AS sessions_internal_tcp, " +
                                "MAX(sessions_internal_udp) AS sessions_internal_udp " +
                                "FROM l4_statistics WHERE timestamp >= :tr_from AND timestamp <= :tr_to " +
                                "AND tap_uuid IN (<taps>) GROUP BY bucket " +
                                "ORDER BY bucket DESC")
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .mapTo(L4StatisticsBucket.class)
                        .list()
        );
    }

    public L4Numbers getTotals(TimeRange timeRange, List<UUID> taps) {
        if (taps.isEmpty()) {
            return L4Numbers.create(0, 0, 0, 0, 0, 0);
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT NOW() AS bucket, " +
                                "SUM(bytes_rx_tcp+bytes_tx_tcp) AS bytes_tcp, " +
                                "SUM(bytes_rx_udp+bytes_tx_udp) AS bytes_udp, " +
                                "SUM(bytes_rx_internal_tcp+bytes_tx_internal_tcp) AS bytes_internal_tcp, " +
                                "SUM(bytes_rx_internal_udp+bytes_tx_internal_udp) AS bytes_internal_udp, " +
                                "SUM(segments_tcp) AS segments_tcp, SUM(datagrams_udp) AS datagrams_udp " +
                                "FROM l4_statistics WHERE timestamp >= :tr_from AND timestamp <= :tr_to " +
                                "AND tap_uuid IN (<taps>)")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .mapTo(L4Numbers.class)
                        .one()
        );
    }

    public long countTopTrafficSourceMacs(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT source_mac AS key, " +
                                "SUM(bytes_rx_count+bytes_tx_count) AS value " +
                                "FROM l4_sessions WHERE most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to AND tap_uuid IN (<taps>) " +
                                "AND source_mac IS NOT NULL " + filterFragment.whereSql() + " " +
                                "GROUP BY source_mac HAVING 1=1 " + filterFragment.havingSql() + ") AS ignored")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<StringNumberNumberAggregationResult> getTopTrafficSourceMacs(TimeRange timeRange,
                                                                             Filters filters,
                                                                             int limit,
                                                                             int offset,
                                                                             List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT source_mac AS key, SUM(bytes_rx_count) AS value1, " +
                                "SUM(bytes_tx_count) AS value2 " +
                                "FROM l4_sessions WHERE most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " +
                                "AND source_mac IS NOT NULL " + filterFragment.whereSql() + " " +
                                "GROUP BY source_mac HAVING 1=1 " + filterFragment.havingSql() + " " +
                                "ORDER BY SUM(bytes_rx_count+bytes_tx_count) DESC LIMIT :limit OFFSET :offset")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(StringNumberNumberAggregationResult.class)
                        .list()
        );
    }

    public long countTopTrafficDestinationMacs(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT destination_mac AS key, " +
                                "SUM(bytes_rx_count+bytes_tx_count) AS value " +
                                "FROM l4_sessions WHERE most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to AND tap_uuid IN (<taps>) " +
                                "AND destination_mac IS NOT NULL " + filterFragment.whereSql() + " " +
                                "GROUP BY destination_mac HAVING 1=1 " + filterFragment.havingSql() + ") AS ignored")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<StringNumberNumberAggregationResult> getTopTrafficDestinationMacs(TimeRange timeRange,
                                                                                  Filters filters,
                                                                                  int limit,
                                                                                  int offset,
                                                                                  List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT destination_mac AS key, SUM(bytes_rx_count) AS value1, " +
                                "SUM(bytes_tx_count) AS value2 " +
                                "FROM l4_sessions WHERE most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " +
                                "AND destination_mac IS NOT NULL " + filterFragment.whereSql() + " " +
                                "GROUP BY destination_mac HAVING 1=1 " + filterFragment.havingSql() + " " +
                                "ORDER BY SUM(bytes_rx_count+bytes_tx_count) DESC LIMIT :limit OFFSET :offset")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(StringNumberNumberAggregationResult.class)
                        .list()
        );
    }


    public long countTopTrafficSourceAddresses(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT source_address AS ignored FROM l4_sessions " +
                                "WHERE most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY source_address HAVING 1=1 " + filterFragment.havingSql() + ") AS ignored")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<L4AddressDataAddressNumberNumberAggregationResult> getTopTrafficSourceAddresses(TimeRange timeRange,
                                                                                                Filters filters,
                                                                                                int limit,
                                                                                                int offset,
                                                                                                List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT source_address AS key_address, NULL as key_port, " +
                                "MIN(source_address_geo_asn_number) AS key_address_geo_asn_number, " +
                                "MIN(source_address_geo_asn_name) AS key_address_geo_asn_name, " +
                                "MIN(source_address_geo_asn_domain) AS key_address_geo_asn_domain, " +
                                "MIN(source_address_geo_city) AS key_address_geo_city, " +
                                "MIN(source_address_geo_country_code) AS key_address_geo_country_code, " +
                                "MIN(source_address_geo_latitude) AS key_address_geo_latitude, " +
                                "MIN(source_address_geo_longitude) AS key_address_geo_longitude, " +
                                "BOOL_AND(source_address_is_site_local) AS key_address_is_site_local, " +
                                "BOOL_AND(source_address_is_loopback) AS key_address_is_loopback, " +
                                "BOOL_AND(source_address_is_multicast) AS key_address_is_multicast, " +
                                "SUM(bytes_rx_count) AS value1, SUM(bytes_tx_count) AS value2 " +
                                "FROM l4_sessions WHERE most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY source_address HAVING 1=1 " + filterFragment.havingSql() + " " +
                                "ORDER BY SUM(bytes_rx_count+bytes_tx_count) DESC LIMIT :limit OFFSET :offset")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(L4AddressDataAddressNumberNumberAggregationResult.class)
                        .list()
        );
    }

    public long countTopTrafficDestinationAddresses(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT destination_address AS ignored, " +
                                "SUM(bytes_rx_count+bytes_tx_count) AS value " +
                                "FROM l4_sessions WHERE most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY destination_address HAVING 1=1 " + filterFragment.havingSql() + ") AS ignored")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<L4AddressDataAddressNumberNumberAggregationResult> getTopTrafficDestinationAddresses(TimeRange timeRange,
                                                                                                     Filters filters,
                                                                                                     int limit,
                                                                                                     int offset,
                                                                                                     List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT destination_address AS key_address, NULL as key_port, " +
                                "MIN(destination_address_geo_asn_number) AS key_address_geo_asn_number, " +
                                "MIN(destination_address_geo_asn_name) AS key_address_geo_asn_name, " +
                                "MIN(destination_address_geo_asn_domain) AS key_address_geo_asn_domain, " +
                                "MIN(destination_address_geo_city) AS key_address_geo_city, " +
                                "MIN(destination_address_geo_country_code) AS key_address_geo_country_code, " +
                                "MIN(destination_address_geo_latitude) AS key_address_geo_latitude, " +
                                "MIN(destination_address_geo_longitude) AS key_address_geo_longitude, " +
                                "BOOL_AND(destination_address_is_site_local) AS key_address_is_site_local, " +
                                "BOOL_AND(destination_address_is_loopback) AS key_address_is_loopback, " +
                                "BOOL_AND(destination_address_is_multicast) AS key_address_is_multicast, " +
                                "SUM(bytes_rx_count) AS value1, SUM(bytes_tx_count) AS value2 " +
                                "FROM l4_sessions WHERE most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " +  filterFragment.whereSql() + " " +
                                "GROUP BY destination_address HAVING 1=1 " + filterFragment.havingSql() + " " +
                                "ORDER BY SUM(bytes_rx_count+bytes_tx_count) DESC LIMIT :limit OFFSET :offset")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(L4AddressDataAddressNumberNumberAggregationResult.class)
                        .list()
        );
    }

    public long countTopDestinationPorts(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT destination_port AS key, COUNT(*) AS value " +
                                "FROM l4_sessions WHERE most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY destination_port HAVING 1=1 " + filterFragment.havingSql() + ") AS ignored")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<NumberNumberNumberAggregationResult> getTopDestinationPorts(TimeRange timeRange,
                                                                            Filters filters,
                                                                            int limit,
                                                                            int offset,
                                                                            List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT destination_port AS key, COUNT(*) AS value1, " +
                                "SUM(bytes_rx_count+bytes_tx_count) AS value2 " +
                                "FROM l4_sessions WHERE  most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY destination_port HAVING 1=1 " + filterFragment.havingSql() + " " +
                                "ORDER BY value2 DESC LIMIT :limit OFFSET :offset")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(NumberNumberNumberAggregationResult.class)
                        .list()
        );
    }


    public long countLeastCommonNonEphemeralDestinationPorts(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT destination_port AS key, COUNT(*) AS value " +
                                "FROM l4_sessions WHERE destination_port < 32768 " +
                                "AND most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY destination_port HAVING 1=1 " + filterFragment.havingSql() + ") AS ignored")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<NumberNumberNumberAggregationResult> getLeastCommonNonEphemeralDestinationPorts(TimeRange timeRange,
                                                                                                Filters filters,
                                                                                                int limit,
                                                                                                int offset,
                                                                                                List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT destination_port AS key, COUNT(*) AS value1, " +
                                "SUM(bytes_rx_count+bytes_tx_count) AS value2 " +
                                "FROM l4_sessions WHERE destination_port < 32768 " +
                                "AND most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY destination_port HAVING 1=1 " + filterFragment.havingSql() + " " +
                                "ORDER BY value1 ASC LIMIT :limit OFFSET :offset")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(NumberNumberNumberAggregationResult.class)
                        .list()
        );
    }

    public Optional<L4AddressData> findMostRecentDestinationAddressData(List<UUID> taps, String address) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT destination_mac AS mac, destination_address AS address, " +
                                "destination_port AS port, destination_address_geo_asn_number AS geo_asn_number, " +
                                "destination_address_geo_asn_name AS geo_asn_name, " +
                                "destination_address_geo_asn_domain AS geo_asn_domain, " +
                                "destination_address_geo_city AS geo_city, " +
                                "destination_address_geo_country_code AS geo_country_code, " +
                                "destination_address_geo_latitude AS geo_latitude, " +
                                "destination_address_geo_longitude AS geo_longitude, " +
                                "destination_address_is_site_local AS is_site_local, " +
                                "destination_address_is_loopback AS is_loopback, " +
                                "destination_address_is_multicast AS is_multicast " +
                                "FROM l4_sessions WHERE destination_address = :address::inet AND " +
                                "tap_uuid IN (<taps>) " +
                                "ORDER BY most_recent_segment_time " +
                                "DESC LIMIT 1")
                        .bindList("taps", taps)
                        .bind("address", address)
                        .mapTo(L4AddressData.class)
                        .findOne()
        );
    }

    public Optional<L4AddressData> findMostRecentSourceAddressData(List<UUID> taps, String address) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT source_mac AS mac, source_address AS address, " +
                                "source_port AS port, source_address_geo_asn_number AS geo_asn_number, " +
                                "source_address_geo_asn_name AS geo_asn_name, " +
                                "source_address_geo_asn_domain AS geo_asn_domain, " +
                                "source_address_geo_city AS geo_city, " +
                                "source_address_geo_country_code AS geo_country_code, " +
                                "source_address_geo_latitude AS geo_latitude, " +
                                "source_address_geo_longitude AS geo_longitude, " +
                                "source_address_is_site_local AS is_site_local, " +
                                "source_address_is_loopback AS is_loopback, " +
                                "source_address_is_multicast AS is_multicast " +
                                "FROM l4_sessions WHERE source_address = :address::inet AND " +
                                "tap_uuid IN (<taps>) " +
                                "ORDER BY most_recent_segment_time " +
                                "DESC LIMIT 1")
                        .bindList("taps", taps)
                        .bind("address", address)
                        .mapTo(L4AddressData.class)
                        .findOne()
        );
    }

}
