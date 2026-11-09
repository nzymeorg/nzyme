package app.nzyme.core.ethernet.l4;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.NumberNumberNumberAggregationResult;
import app.nzyme.core.database.generic.StringNumberNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.l4.db.L4Numbers;
import app.nzyme.core.ethernet.l4.db.L4Session;
import app.nzyme.core.ethernet.l4.db.L4SessionsTimelineBucket;
import app.nzyme.core.ethernet.l4.db.L4StatisticsBucket;
import app.nzyme.core.ethernet.l4.filters.L4Filters;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import org.jdbi.v3.core.mapper.RowMapper;
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

public class L4 {

    // Key columns of histograms grouped by source address, mapped to L4AddressData.
    private static final String SOURCE_ADDRESS_KEY_COLUMNS = "source_address AS key_address, " +
                    "MIN(source_mac) AS key_mac, NULL as key_port, " +
                    "MIN(source_address_geo_asn_number) AS key_address_geo_asn_number, " +
                    "MIN(source_address_geo_asn_name) AS key_address_geo_asn_name, " +
                    "MIN(source_address_geo_asn_domain) AS key_address_geo_asn_domain, " +
                    "MIN(source_address_geo_city) AS key_address_geo_city, " +
                    "MIN(source_address_geo_country_code) AS key_address_geo_country_code, " +
                    "MIN(source_address_geo_latitude) AS key_address_geo_latitude, " +
                    "MIN(source_address_geo_longitude) AS key_address_geo_longitude, " +
                    "BOOL_AND(source_address_is_site_local) AS key_address_is_site_local, " +
                    "BOOL_AND(source_address_is_loopback) AS key_address_is_loopback, " +
                    "BOOL_AND(source_address_is_multicast) AS key_address_is_multicast, ";

    private static final String SESSION_AGGREGATES = "MIN(source_mac) AS source_mac, " +
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
                    "MIN(created_at) AS created_at ";

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

    public record AggregationPage<T>(List<T> results, long total) {

        public static <T> AggregationPage<T> empty() {
            return new AggregationPage<>(Collections.emptyList(), 0);
        }

    }

    private record AggregationRow<T>(T value, long total) {
    }

    public L4(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
    }

    private static boolean hasHavingFilters(FilterSqlFragment filterFragment) {
        return filterFragment.havingSql() != null && !filterFragment.havingSql().isBlank();
    }

    private static <T> RowMapper<AggregationRow<T>> withTotal(Class<T> type) {
        return (rs, ctx) -> new AggregationRow<>(
                ctx.findRowMapperFor(type)
                        .orElseThrow(() -> new IllegalStateException("No row mapper registered for " + type.getName()))
                        .map(rs, ctx),
                rs.getLong("total_groups")
        );
    }

    private static <T> AggregationPage<T> toPage(List<AggregationRow<T>> rows, int offset, LongSupplier fallbackCount) {
        if (rows.isEmpty()) {
            return new AggregationPage<>(Collections.emptyList(), offset > 0 ? fallbackCount.getAsLong() : 0);
        }

        return new AggregationPage<>(rows.stream().map(AggregationRow::value).toList(), rows.get(0).total());
    }

    public long countAllSessions(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        if (taps.size() == 1 && !hasHavingFilters(filterFragment)) {
            return nzyme.getDatabase().withHandle(handle ->
                    handle.createQuery("SELECT COUNT(*) FROM l4_sessions " +
                                    "WHERE most_recent_segment_time >= :tr_from AND most_recent_segment_time <= :tr_to " +
                                    "AND tap_uuid = :tap_uuid " + filterFragment.whereSql())
                            .bindMap(filterFragment.bindings())
                            .bind("tap_uuid", taps.get(0))
                            .bind("tr_from", timeRange.from())
                            .bind("tr_to", timeRange.to())
                            .mapTo(Long.class)
                            .one()
            );
        }

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

        if (orderColumn == OrderColumn.MOST_RECENT_SEGMENT_TIME
                && orderDirection == OrderDirection.DESC
                && !hasHavingFilters(filterFragment)) {
            return findAllSessionsNewestFirst(timeRange, filterFragment, limit, offset, taps);
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT session_key, l4_type, state, " + SESSION_AGGREGATES +
                                "FROM l4_sessions " +
                                "WHERE most_recent_segment_time >= :tr_from AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY session_key, l4_type, state " +
                                "HAVING 1=1 " + filterFragment.havingSql() + " " +
                                "ORDER BY <order_column> <order_direction>, session_key, l4_type, state " +
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

    private List<L4Session> findAllSessionsNewestFirst(TimeRange timeRange,
                                                       FilterSqlFragment filterFragment,
                                                       int limit,
                                                       int offset,
                                                       List<UUID> taps) {
        long scanLimit = ((long) offset + limit) * taps.size();

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH recent AS (" +
                                "SELECT r.p_session_key, r.p_l4_type, r.p_state, r.p_mrst " +
                                "FROM unnest(ARRAY[<taps>]::uuid[]) AS t(tap) " +
                                "CROSS JOIN LATERAL (" +
                                "SELECT session_key AS p_session_key, l4_type AS p_l4_type, " +
                                "state AS p_state, most_recent_segment_time AS p_mrst " +
                                "FROM l4_sessions " +
                                "WHERE tap_uuid = t.tap " +
                                "AND most_recent_segment_time >= :tr_from AND most_recent_segment_time <= :tr_to " +
                                filterFragment.whereSql() + " " +
                                "ORDER BY most_recent_segment_time DESC, session_key, l4_type, state " +
                                "LIMIT <scan_limit>" +
                                ") AS r " +
                                "ORDER BY r.p_mrst DESC, r.p_session_key, r.p_l4_type, r.p_state " +
                                "LIMIT <scan_limit>" +
                                "), page AS (" +
                                "SELECT p_session_key, p_l4_type, p_state, MAX(p_mrst) AS page_mrst " +
                                "FROM recent " +
                                "GROUP BY p_session_key, p_l4_type, p_state " +
                                "ORDER BY page_mrst DESC, p_session_key, p_l4_type, p_state " +
                                "LIMIT :limit OFFSET :offset" +
                                ") " +
                                "SELECT s.session_key, s.l4_type, s.state, " + SESSION_AGGREGATES +
                                "FROM page JOIN l4_sessions AS s " +
                                "ON s.session_key = page.p_session_key " +
                                "AND s.l4_type = page.p_l4_type " +
                                "AND s.state IS NOT DISTINCT FROM page.p_state " +
                                "WHERE s.most_recent_segment_time >= :tr_from " +
                                "AND s.most_recent_segment_time <= :tr_to " +
                                "AND s.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY s.session_key, s.l4_type, s.state " +
                                "ORDER BY most_recent_segment_time DESC, s.session_key, s.l4_type, s.state")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("scan_limit", scanLimit)
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

    public AggregationPage<StringNumberNumberAggregationResult> getTopTrafficSourceMacs(TimeRange timeRange,
                                                                                        Filters filters,
                                                                                        int limit,
                                                                                        int offset,
                                                                                        List<UUID> taps) {
        if (taps.isEmpty()) {
            return AggregationPage.empty();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        List<AggregationRow<StringNumberNumberAggregationResult>> rows = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT source_mac AS key, SUM(bytes_rx_count) AS value1, " +
                                "SUM(bytes_tx_count) AS value2, COUNT(*) OVER () AS total_groups " +
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
                        .map(withTotal(StringNumberNumberAggregationResult.class))
                        .list()
        );

        return toPage(rows, offset, () -> countTopTrafficSourceMacs(timeRange, filters, taps));
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

    public AggregationPage<StringNumberNumberAggregationResult> getTopTrafficDestinationMacs(TimeRange timeRange,
                                                                                             Filters filters,
                                                                                             int limit,
                                                                                             int offset,
                                                                                             List<UUID> taps) {
        if (taps.isEmpty()) {
            return AggregationPage.empty();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        List<AggregationRow<StringNumberNumberAggregationResult>> rows = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT destination_mac AS key, SUM(bytes_rx_count) AS value1, " +
                                "SUM(bytes_tx_count) AS value2, COUNT(*) OVER () AS total_groups " +
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
                        .map(withTotal(StringNumberNumberAggregationResult.class))
                        .list()
        );

        return toPage(rows, offset, () -> countTopTrafficDestinationMacs(timeRange, filters, taps));
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

    public AggregationPage<L4AddressDataAddressNumberNumberAggregationResult> getTopTrafficSourceAddresses(TimeRange timeRange,
                                                                                                           Filters filters,
                                                                                                           int limit,
                                                                                                           int offset,
                                                                                                           List<UUID> taps) {
        if (taps.isEmpty()) {
            return AggregationPage.empty();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        List<AggregationRow<L4AddressDataAddressNumberNumberAggregationResult>> rows = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT " + SOURCE_ADDRESS_KEY_COLUMNS +
                                "SUM(bytes_rx_count) AS value1, SUM(bytes_tx_count) AS value2, " +
                                "COUNT(*) OVER () AS total_groups " +
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
                        .map(withTotal(L4AddressDataAddressNumberNumberAggregationResult.class))
                        .list()
        );

        return toPage(rows, offset, () -> countTopTrafficSourceAddresses(timeRange, filters, taps));
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

    public AggregationPage<L4AddressDataAddressNumberNumberAggregationResult> getTopTrafficDestinationAddresses(TimeRange timeRange,
                                                                                                                Filters filters,
                                                                                                                int limit,
                                                                                                                int offset,
                                                                                                                List<UUID> taps) {
        if (taps.isEmpty()) {
            return AggregationPage.empty();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        List<AggregationRow<L4AddressDataAddressNumberNumberAggregationResult>> rows = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT destination_address AS key_address, MAX(destination_mac) AS key_mac, " +
                                "NULL as key_port, " +
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
                                "SUM(bytes_rx_count) AS value1, SUM(bytes_tx_count) AS value2, " +
                                "COUNT(*) OVER () AS total_groups " +
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
                        .map(withTotal(L4AddressDataAddressNumberNumberAggregationResult.class))
                        .list()
        );

        return toPage(rows, offset, () -> countTopTrafficDestinationAddresses(timeRange, filters, taps));
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

    public AggregationPage<NumberNumberNumberAggregationResult> getTopDestinationPorts(TimeRange timeRange,
                                                                                       Filters filters,
                                                                                       int limit,
                                                                                       int offset,
                                                                                       List<UUID> taps) {
        if (taps.isEmpty()) {
            return AggregationPage.empty();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        List<AggregationRow<NumberNumberNumberAggregationResult>> rows = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT destination_port AS key, COUNT(*) AS value1, " +
                                "SUM(bytes_rx_count+bytes_tx_count) AS value2, COUNT(*) OVER () AS total_groups " +
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
                        .map(withTotal(NumberNumberNumberAggregationResult.class))
                        .list()
        );

        return toPage(rows, offset, () -> countTopDestinationPorts(timeRange, filters, taps));
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

    public AggregationPage<NumberNumberNumberAggregationResult> getLeastCommonNonEphemeralDestinationPorts(TimeRange timeRange,
                                                                                                           Filters filters,
                                                                                                           int limit,
                                                                                                           int offset,
                                                                                                           List<UUID> taps) {
        if (taps.isEmpty()) {
            return AggregationPage.empty();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        List<AggregationRow<NumberNumberNumberAggregationResult>> rows = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT destination_port AS key, COUNT(*) AS value1, " +
                                "SUM(bytes_rx_count+bytes_tx_count) AS value2, COUNT(*) OVER () AS total_groups " +
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
                        .map(withTotal(NumberNumberNumberAggregationResult.class))
                        .list()
        );

        return toPage(rows, offset, () -> countLeastCommonNonEphemeralDestinationPorts(timeRange, filters, taps));
    }

    /*
     * Fan-out per source address: how many distinct destination addresses and destination ports a source talked
     * to. Groups like the top traffic source addresses, so that count query is reused for paging.
     */
    public AggregationPage<L4AddressDataAddressNumberNumberAggregationResult> getSourceFanOut(TimeRange timeRange,
                                                                                               Filters filters,
                                                                                               int limit,
                                                                                               int offset,
                                                                                               ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                                               OrderDirection orderDirection,
                                                                                               List<UUID> taps) {
        if (taps.isEmpty()) {
            return AggregationPage.empty();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        String orderSql = orderColumn == ThreeColumnWithKeyHistogramOrderColumn.KEY
                ? "key_address" : orderColumn.getColumnName();

        List<AggregationRow<L4AddressDataAddressNumberNumberAggregationResult>> rows = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT " + SOURCE_ADDRESS_KEY_COLUMNS +
                                "COUNT(DISTINCT destination_address) AS value1, " +
                                "COUNT(DISTINCT destination_port) AS value2, " +
                                "COUNT(*) OVER () AS total_groups " +
                                "FROM l4_sessions WHERE most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY source_address HAVING 1=1 " + filterFragment.havingSql() + " " +
                                "ORDER BY <order_column> <order_direction>, key_address ASC " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .define("order_column", orderSql)
                        .define("order_direction", orderDirection)
                        .map(withTotal(L4AddressDataAddressNumberNumberAggregationResult.class))
                        .list()
        );

        return toPage(rows, offset, () -> countTopTrafficSourceAddresses(timeRange, filters, taps));
    }

    /*
     * Sessions are grouped by their key first so that a session seen by more than one tap is counted once and the
     * duration filter (a HAVING filter) applies per session. A session lands in the bucket of its most recent
     * activity, which is also the column the time range is applied to.
     */
    public List<L4SessionsTimelineBucket> getSessionsTimeline(TimeRange timeRange,
                                                               Bucketing.BucketingConfiguration bucketing,
                                                               Filters filters,
                                                               List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH sess AS (" +
                                "SELECT MAX(most_recent_segment_time) AS most_recent_segment_time, " +
                                "MAX(bytes_rx_count) + MAX(bytes_tx_count) AS bytes " +
                                "FROM l4_sessions WHERE most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY session_key, l4_type, state HAVING 1=1 " + filterFragment.havingSql() +
                                "), " +
                                // An "all time" range spans from 1970 far into the future. Bound the series by
                                // the data in that case instead of generating hundreds of thousands of buckets.
                                "bounds AS (" +
                                "SELECT CASE WHEN :all_time THEN COALESCE(MIN(most_recent_segment_time), NOW()) " +
                                "ELSE :tr_from::timestamptz END AS series_from, " +
                                "CASE WHEN :all_time THEN COALESCE(MAX(most_recent_segment_time), NOW()) " +
                                "ELSE :tr_to::timestamptz END AS series_to FROM sess" +
                                "), " +
                                "buckets AS (" +
                                "SELECT generate_series(" +
                                "date_trunc(:date_trunc, series_from), " +
                                "date_trunc(:date_trunc, series_to), " +
                                "make_interval(secs => :bucket_size_s)" +
                                ") AS bucket FROM bounds" +
                                "), " +
                                "per_bucket AS (" +
                                "SELECT date_trunc(:date_trunc, most_recent_segment_time) AS bucket, " +
                                "COUNT(*) AS sessions, SUM(bytes) AS bytes FROM sess GROUP BY bucket" +
                                ") " +
                                "SELECT b.bucket AS bucket, COALESCE(p.sessions, 0) AS sessions, " +
                                "COALESCE(p.bytes, 0) AS bytes " +
                                "FROM buckets AS b LEFT JOIN per_bucket AS p ON p.bucket = b.bucket " +
                                "ORDER BY b.bucket DESC")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("all_time", timeRange.isAllTime())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("bucket_size_s", bucketing.bucketSizeMs() / 1000.0)
                        .bindMap(filterFragment.bindings())
                        .bindList("taps", taps)
                        .mapTo(L4SessionsTimelineBucket.class)
                        .list()
        );
    }

    /*
     * Returns the number of sessions and their traffic per duration bucket. The key is the index into
     * DURATION_BUCKET_UPPER_BOUNDS_MS; the last index collects everything above the last bound. Buckets without
     * sessions are not returned.
     */
    public static final long[] DURATION_BUCKET_UPPER_BOUNDS_MS = {1_000, 10_000, 60_000, 600_000, 3_600_000};

    public List<NumberNumberNumberAggregationResult> getDurationHistogram(TimeRange timeRange,
                                                                          Filters filters,
                                                                          List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new L4Filters());

        StringBuilder bucketCase = new StringBuilder("CASE ");
        for (int i = 0; i < DURATION_BUCKET_UPPER_BOUNDS_MS.length; i++) {
            bucketCase.append("WHEN duration_ms < ").append(DURATION_BUCKET_UPPER_BOUNDS_MS[i])
                    .append(" THEN ").append(i).append(" ");
        }
        bucketCase.append("ELSE ").append(DURATION_BUCKET_UPPER_BOUNDS_MS.length).append(" END");

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH sess AS (" +
                                "SELECT GREATEST(EXTRACT(EPOCH FROM (MAX(most_recent_segment_time) - " +
                                "MIN(start_time))) * 1000, 0) AS duration_ms, " +
                                "MAX(bytes_rx_count) + MAX(bytes_tx_count) AS bytes " +
                                "FROM l4_sessions WHERE most_recent_segment_time >= :tr_from " +
                                "AND most_recent_segment_time <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY session_key, l4_type, state HAVING 1=1 " + filterFragment.havingSql() +
                                ") " +
                                "SELECT " + bucketCase + " AS key, COUNT(*) AS value1, SUM(bytes) AS value2 " +
                                "FROM sess GROUP BY key ORDER BY key ASC")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
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

}