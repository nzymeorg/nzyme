package app.nzyme.core.ethernet.rtsp;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.StringNumberNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.rtsp.db.RTSPStreamEntry;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
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

public class RTSP {

    public enum OrderColumn {

        SETUP_ESTABLISHED_AT("setup_established_at"),
        SETUP_MOST_RECENT_SEGMENT_TIME("setup_most_recent_segment_time"),
        TYPE("MAX(stream.l4_type)"),
        SETUP_SOURCE_ADDRESS("MAX(setup.source_address)"),
        SETUP_SOURCE_MAC("MAX(setup.source_mac)"),
        SETUP_DESTINATION_ADDRESS("MAX(setup.destination_address)"),
        SETUP_DESTINATION_MAC("MAX(setup.destination_mac)"),
        STREAM_BYTES_RX("MAX(stream.bytes_rx_count)"),
        STREAM_BYTES_TX("MAX(stream.bytes_tx_count)"),
        DURATION("duration_ms");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    private final NzymeNode nzyme;

    public RTSP(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
    }

    public long countAllStreams(TimeRange timeRange,
                                Filters filters,
                                List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new RTSPFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT rtsp.setup_tcp_session_key " +
                                "FROM rtsp_streams AS rtsp " +
                                "LEFT JOIN l4_sessions AS setup " +
                                "ON setup.session_key = rtsp.setup_tcp_session_key " +
                                "AND setup.start_time >= rtsp.setup_established_at - INTERVAL '10 seconds' " +
                                "AND setup.start_time <= rtsp.setup_established_at + INTERVAL '10 seconds' " +
                                "AND setup.tap_uuid = rtsp.tap_uuid " +
                                "LEFT JOIN l4_sessions AS stream " +
                                "ON stream.untimed_session_key = rtsp.stream_l4_untimed_session_key " +
                                "AND stream.start_time >= rtsp.setup_established_at - INTERVAL '10 seconds' " +
                                "AND stream.start_time <= rtsp.setup_established_at + INTERVAL '10 seconds' " +
                                "AND stream.tap_uuid = rtsp.tap_uuid " +
                                "WHERE ((rtsp.setup_most_recent_segment_time >= :tr_from " +
                                "AND rtsp.setup_most_recent_segment_time <= :tr_to) " +
                                "OR stream.most_recent_segment_time >= :tr_from " +
                                "AND stream.most_recent_segment_time <= :tr_to) " +
                                "AND rtsp.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                                "GROUP BY rtsp.setup_tcp_session_key HAVING 1=1 " + filterFragment.havingSql() +
                                ") AS sessions")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<RTSPStreamEntry> findAllStreams(TimeRange timeRange,
                                                Filters filters,
                                                RTSP.OrderColumn orderColumn,
                                                OrderDirection orderDirection,
                                                int limit,
                                                int offset,
                                                List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new RTSPFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT rtsp.setup_tcp_session_key AS setup_tcp_session_key, " +
                                "MAX(rtsp.state) AS state, MAX(rtsp.media_locator::text)::jsonb AS media_locator, " +
                                "MAX(rtsp.request_uri) AS request_uri, MAX(rtsp.client_agent) AS client_agent, " +
                                "MAX(rtsp.server_info) AS server_info, MAX(rtsp.authentication) AS authentication, " +
                                "MAX(rtsp.flags) as flags, " +
                                "MAX(rtsp.setup_connection_status) AS setup_connection_status, " +
                                "MIN(rtsp.setup_established_at) AS setup_established_at, " +
                                "MAX(rtsp.setup_terminated_at) AS setup_terminated_at, " +
                                "MAX(setup_most_recent_segment_time) AS setup_most_recent_segment_time, " +
                                "MAX(setup.source_mac) AS setup_source_mac, " +
                                "MAX(setup.source_address) AS setup_source_address, " +
                                "MAX(setup.source_port) AS setup_source_port, " +
                                "MAX(setup.source_address_geo_asn_number) AS setup_source_address_geo_asn_number, " +
                                "MAX(setup.source_address_geo_asn_name) AS setup_source_address_geo_asn_name, " +
                                "MAX(setup.source_address_geo_asn_domain) AS setup_source_address_geo_asn_domain, " +
                                "MAX(setup.source_address_geo_city) AS setup_source_address_geo_city, " +
                                "MAX(setup.source_address_geo_country_code) AS setup_source_address_geo_country_code, " +
                                "MAX(setup.source_address_geo_latitude) AS setup_source_address_geo_latitude, " +
                                "MAX(setup.source_address_geo_longitude) AS setup_source_address_geo_longitude, " +
                                "MAX(setup.source_address_geo_latitude) AS setup_source_address_geo_latitude, " +
                                "BOOL_OR(setup.source_address_is_site_local) AS setup_source_address_is_site_local, " +
                                "BOOL_OR(setup.source_address_is_loopback) AS setup_source_address_is_loopback, " +
                                "BOOL_OR(setup.source_address_is_multicast) AS setup_source_address_is_multicast, " +
                                "MAX(setup.destination_mac) AS setup_destination_mac, " +
                                "MAX(setup.destination_address) AS setup_destination_address, " +
                                "MAX(setup.destination_port) AS setup_destination_port, " +
                                "MAX(setup.destination_address_geo_asn_number) AS setup_destination_address_geo_asn_number, " +
                                "MAX(setup.destination_address_geo_asn_name) AS setup_destination_address_geo_asn_name, " +
                                "MAX(setup.destination_address_geo_asn_domain) AS setup_destination_address_geo_asn_domain, " +
                                "MAX(setup.destination_address_geo_city) AS setup_destination_address_geo_city, " +
                                "MAX(setup.destination_address_geo_country_code) AS setup_destination_address_geo_country_code, " +
                                "MAX(setup.destination_address_geo_latitude) AS setup_destination_address_geo_latitude, " +
                                "MAX(setup.destination_address_geo_longitude) AS setup_destination_address_geo_longitude, " +
                                "MAX(setup.destination_address_geo_latitude) AS setup_destination_address_geo_latitude, " +
                                "BOOL_OR(setup.destination_address_is_site_local) AS setup_destination_address_is_site_local, " +
                                "BOOL_OR(setup.destination_address_is_loopback) AS setup_destination_address_is_loopback, " +
                                "BOOL_OR(setup.destination_address_is_multicast) AS setup_destination_address_is_multicast, " +
                                "MAX(setup.bytes_rx_count)+MAX(setup.bytes_tx_count) AS setup_bytes_exchanged, " +
                                "MAX(stream.l4_type) AS stream_l4_type, " +
                                "MAX(stream.source_mac) AS stream_source_mac, " +
                                "MAX(stream.source_address) AS stream_source_address, " +
                                "MAX(stream.source_port) AS stream_source_port, " +
                                "MAX(stream.source_address_geo_asn_number) AS stream_source_address_geo_asn_number, " +
                                "MAX(stream.source_address_geo_asn_name) AS stream_source_address_geo_asn_name, " +
                                "MAX(stream.source_address_geo_asn_domain) AS stream_source_address_geo_asn_domain, " +
                                "MAX(stream.source_address_geo_city) AS stream_source_address_geo_city, " +
                                "MAX(stream.source_address_geo_country_code) AS stream_source_address_geo_country_code, " +
                                "MAX(stream.source_address_geo_latitude) AS stream_source_address_geo_latitude, " +
                                "MAX(stream.source_address_geo_longitude) AS stream_source_address_geo_longitude, " +
                                "MAX(stream.source_address_geo_latitude) AS stream_source_address_geo_latitude, " +
                                "BOOL_OR(stream.source_address_is_site_local) AS stream_source_address_is_site_local, " +
                                "BOOL_OR(stream.source_address_is_loopback) AS stream_source_address_is_loopback, " +
                                "BOOL_OR(stream.source_address_is_multicast) AS stream_source_address_is_multicast, " +
                                "MAX(stream.destination_mac) AS stream_destination_mac, " +
                                "MAX(stream.destination_address) AS stream_destination_address, " +
                                "MAX(stream.destination_port) AS stream_destination_port, " +
                                "MAX(stream.most_recent_segment_time) AS stream_most_recent_segment_time, " +
                                "MAX(stream.destination_address_geo_asn_number) AS stream_destination_address_geo_asn_number, " +
                                "MAX(stream.destination_address_geo_asn_name) AS stream_destination_address_geo_asn_name, " +
                                "MAX(stream.destination_address_geo_asn_domain) AS stream_destination_address_geo_asn_domain, " +
                                "MAX(stream.destination_address_geo_city) AS stream_destination_address_geo_city, " +
                                "MAX(stream.destination_address_geo_country_code) AS stream_destination_address_geo_country_code, " +
                                "MAX(stream.destination_address_geo_latitude) AS stream_destination_address_geo_latitude, " +
                                "MAX(stream.destination_address_geo_longitude) AS stream_destination_address_geo_longitude, " +
                                "MAX(stream.destination_address_geo_latitude) AS stream_destination_address_geo_latitude, " +
                                "BOOL_OR(stream.destination_address_is_site_local) AS stream_destination_address_is_site_local, " +
                                "BOOL_OR(stream.destination_address_is_loopback) AS stream_destination_address_is_loopback, " +
                                "BOOL_OR(stream.destination_address_is_multicast) AS stream_destination_address_is_multicast, " +
                                "MAX(stream.bytes_rx_count) AS stream_bytes_rx, " +
                                "MAX(stream.bytes_tx_count) AS stream_bytes_tx, " +
                                "CASE WHEN MAX(rtsp.setup_most_recent_segment_time) IS NOT NULL " +
                                "AND MAX(stream.most_recent_segment_time) IS NOT NULL " +
                                "THEN GREATEST(MAX(rtsp.setup_most_recent_segment_time), " +
                                "MAX(stream.most_recent_segment_time)) " +
                                "ELSE NULL END AS last_activity, " +
                                "CASE WHEN MAX(rtsp.setup_most_recent_segment_time) IS NOT NULL " +
                                "AND MAX(stream.most_recent_segment_time) IS NOT NULL " +
                                "THEN GREATEST(MAX(rtsp.setup_most_recent_segment_time), " +
                                "MAX(stream.most_recent_segment_time)) > :active_cutoff " +
                                "ELSE NULL END AS is_active, " +
                                "CASE WHEN MIN(rtsp.setup_established_at) IS NOT NULL " +
                                "AND MAX(rtsp.setup_most_recent_segment_time) IS NOT NULL " +
                                "AND MAX(stream.most_recent_segment_time) IS NOT NULL " +
                                "THEN (EXTRACT(EPOCH FROM (GREATEST(MAX(rtsp.setup_most_recent_segment_time), " +
                                "MAX(stream.most_recent_segment_time)) - MIN(rtsp.setup_established_at))) * 1000)::bigint " +
                                "ELSE NULL END AS duration_ms " +
                                "FROM rtsp_streams AS rtsp " +
                                "LEFT JOIN l4_sessions AS setup " +
                                "ON setup.session_key = rtsp.setup_tcp_session_key " +
                                "AND setup.start_time >= rtsp.setup_established_at - INTERVAL '10 seconds' " +
                                "AND setup.start_time <= rtsp.setup_established_at + INTERVAL '10 seconds' " +
                                "AND setup.tap_uuid = rtsp.tap_uuid " +
                                "LEFT JOIN l4_sessions AS stream " +
                                "ON stream.untimed_session_key = rtsp.stream_l4_untimed_session_key " +
                                "AND stream.start_time >= rtsp.setup_established_at - INTERVAL '10 seconds' " +
                                "AND stream.start_time <= rtsp.setup_established_at + INTERVAL '10 seconds' " +
                                "AND stream.tap_uuid = rtsp.tap_uuid " +
                                "WHERE ((rtsp.setup_most_recent_segment_time >= :tr_from " +
                                "AND rtsp.setup_most_recent_segment_time <= :tr_to) " +
                                "OR stream.most_recent_segment_time >= :tr_from " +
                                "AND stream.most_recent_segment_time <= :tr_to) " +
                                "AND rtsp.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                                "GROUP BY rtsp.setup_tcp_session_key HAVING 1=1 " + filterFragment.havingSql() +
                                "ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("active_cutoff", DateTime.now().minusMinutes(1))
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(RTSPStreamEntry.class)
                        .list()
        );
    }

    public Optional<RTSPStreamEntry> findOneStream(String sessionKey, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT rtsp.setup_tcp_session_key AS setup_tcp_session_key, " +
                                "MAX(rtsp.state) AS state, MAX(rtsp.media_locator::text)::jsonb AS media_locator, " +
                                "MAX(rtsp.request_uri) AS request_uri, MAX(rtsp.client_agent) AS client_agent, " +
                                "MAX(rtsp.server_info) AS server_info, MAX(rtsp.authentication) AS authentication, " +
                                "MAX(rtsp.flags) as flags, " +
                                "MAX(rtsp.setup_connection_status) AS setup_connection_status, " +
                                "MIN(rtsp.setup_established_at) AS setup_established_at, " +
                                "MAX(rtsp.setup_terminated_at) AS setup_terminated_at, " +
                                "MAX(setup_most_recent_segment_time) AS setup_most_recent_segment_time, " +
                                "MAX(setup.source_mac) AS setup_source_mac, " +
                                "MAX(setup.source_address) AS setup_source_address, " +
                                "MAX(setup.source_port) AS setup_source_port, " +
                                "MAX(setup.source_address_geo_asn_number) AS setup_source_address_geo_asn_number, " +
                                "MAX(setup.source_address_geo_asn_name) AS setup_source_address_geo_asn_name, " +
                                "MAX(setup.source_address_geo_asn_domain) AS setup_source_address_geo_asn_domain, " +
                                "MAX(setup.source_address_geo_city) AS setup_source_address_geo_city, " +
                                "MAX(setup.source_address_geo_country_code) AS setup_source_address_geo_country_code, " +
                                "MAX(setup.source_address_geo_latitude) AS setup_source_address_geo_latitude, " +
                                "MAX(setup.source_address_geo_longitude) AS setup_source_address_geo_longitude, " +
                                "MAX(setup.source_address_geo_latitude) AS setup_source_address_geo_latitude, " +
                                "BOOL_OR(setup.source_address_is_site_local) AS setup_source_address_is_site_local, " +
                                "BOOL_OR(setup.source_address_is_loopback) AS setup_source_address_is_loopback, " +
                                "BOOL_OR(setup.source_address_is_multicast) AS setup_source_address_is_multicast, " +
                                "MAX(setup.destination_mac) AS setup_destination_mac, " +
                                "MAX(setup.destination_address) AS setup_destination_address, " +
                                "MAX(setup.destination_port) AS setup_destination_port, " +
                                "MAX(setup.destination_address_geo_asn_number) AS setup_destination_address_geo_asn_number, " +
                                "MAX(setup.destination_address_geo_asn_name) AS setup_destination_address_geo_asn_name, " +
                                "MAX(setup.destination_address_geo_asn_domain) AS setup_destination_address_geo_asn_domain, " +
                                "MAX(setup.destination_address_geo_city) AS setup_destination_address_geo_city, " +
                                "MAX(setup.destination_address_geo_country_code) AS setup_destination_address_geo_country_code, " +
                                "MAX(setup.destination_address_geo_latitude) AS setup_destination_address_geo_latitude, " +
                                "MAX(setup.destination_address_geo_longitude) AS setup_destination_address_geo_longitude, " +
                                "MAX(setup.destination_address_geo_latitude) AS setup_destination_address_geo_latitude, " +
                                "BOOL_OR(setup.destination_address_is_site_local) AS setup_destination_address_is_site_local, " +
                                "BOOL_OR(setup.destination_address_is_loopback) AS setup_destination_address_is_loopback, " +
                                "BOOL_OR(setup.destination_address_is_multicast) AS setup_destination_address_is_multicast, " +
                                "MAX(setup.bytes_rx_count)+MAX(setup.bytes_tx_count) AS setup_bytes_exchanged, " +
                                "MAX(stream.l4_type) AS stream_l4_type, " +
                                "MAX(stream.source_mac) AS stream_source_mac, " +
                                "MAX(stream.source_address) AS stream_source_address, " +
                                "MAX(stream.source_port) AS stream_source_port, " +
                                "MAX(stream.source_address_geo_asn_number) AS stream_source_address_geo_asn_number, " +
                                "MAX(stream.source_address_geo_asn_name) AS stream_source_address_geo_asn_name, " +
                                "MAX(stream.source_address_geo_asn_domain) AS stream_source_address_geo_asn_domain, " +
                                "MAX(stream.source_address_geo_city) AS stream_source_address_geo_city, " +
                                "MAX(stream.source_address_geo_country_code) AS stream_source_address_geo_country_code, " +
                                "MAX(stream.source_address_geo_latitude) AS stream_source_address_geo_latitude, " +
                                "MAX(stream.source_address_geo_longitude) AS stream_source_address_geo_longitude, " +
                                "MAX(stream.source_address_geo_latitude) AS stream_source_address_geo_latitude, " +
                                "BOOL_OR(stream.source_address_is_site_local) AS stream_source_address_is_site_local, " +
                                "BOOL_OR(stream.source_address_is_loopback) AS stream_source_address_is_loopback, " +
                                "BOOL_OR(stream.source_address_is_multicast) AS stream_source_address_is_multicast, " +
                                "MAX(stream.destination_mac) AS stream_destination_mac, " +
                                "MAX(stream.destination_address) AS stream_destination_address, " +
                                "MAX(stream.destination_port) AS stream_destination_port, " +
                                "MAX(stream.most_recent_segment_time) AS stream_most_recent_segment_time, " +
                                "MAX(stream.destination_address_geo_asn_number) AS stream_destination_address_geo_asn_number, " +
                                "MAX(stream.destination_address_geo_asn_name) AS stream_destination_address_geo_asn_name, " +
                                "MAX(stream.destination_address_geo_asn_domain) AS stream_destination_address_geo_asn_domain, " +
                                "MAX(stream.destination_address_geo_city) AS stream_destination_address_geo_city, " +
                                "MAX(stream.destination_address_geo_country_code) AS stream_destination_address_geo_country_code, " +
                                "MAX(stream.destination_address_geo_latitude) AS stream_destination_address_geo_latitude, " +
                                "MAX(stream.destination_address_geo_longitude) AS stream_destination_address_geo_longitude, " +
                                "MAX(stream.destination_address_geo_latitude) AS stream_destination_address_geo_latitude, " +
                                "BOOL_OR(stream.destination_address_is_site_local) AS stream_destination_address_is_site_local, " +
                                "BOOL_OR(stream.destination_address_is_loopback) AS stream_destination_address_is_loopback, " +
                                "BOOL_OR(stream.destination_address_is_multicast) AS stream_destination_address_is_multicast, " +
                                "MAX(stream.bytes_rx_count) AS stream_bytes_rx, " +
                                "MAX(stream.bytes_tx_count) AS stream_bytes_tx, " +
                                "CASE WHEN MAX(rtsp.setup_most_recent_segment_time) IS NOT NULL " +
                                "AND MAX(stream.most_recent_segment_time) IS NOT NULL " +
                                "THEN GREATEST(MAX(rtsp.setup_most_recent_segment_time), " +
                                "MAX(stream.most_recent_segment_time)) " +
                                "ELSE NULL END AS last_activity, " +
                                "CASE WHEN MAX(rtsp.setup_most_recent_segment_time) IS NOT NULL " +
                                "AND MAX(stream.most_recent_segment_time) IS NOT NULL " +
                                "THEN GREATEST(MAX(rtsp.setup_most_recent_segment_time), " +
                                "MAX(stream.most_recent_segment_time)) > :active_cutoff " +
                                "ELSE NULL END AS is_active, " +
                                "CASE WHEN MIN(rtsp.setup_established_at) IS NOT NULL " +
                                "AND MAX(rtsp.setup_most_recent_segment_time) IS NOT NULL " +
                                "AND MAX(stream.most_recent_segment_time) IS NOT NULL " +
                                "THEN (EXTRACT(EPOCH FROM (GREATEST(MAX(rtsp.setup_most_recent_segment_time), " +
                                "MAX(stream.most_recent_segment_time)) - MIN(rtsp.setup_established_at))) * 1000)::bigint " +
                                "ELSE NULL END AS duration_ms " +
                                "FROM rtsp_streams AS rtsp " +
                                "LEFT JOIN l4_sessions AS setup " +
                                "ON setup.session_key = rtsp.setup_tcp_session_key " +
                                "AND setup.start_time >= rtsp.setup_established_at - INTERVAL '10 seconds' " +
                                "AND setup.start_time <= rtsp.setup_established_at + INTERVAL '10 seconds' " +
                                "AND setup.tap_uuid = rtsp.tap_uuid " +
                                "LEFT JOIN l4_sessions AS stream " +
                                "ON stream.untimed_session_key = rtsp.stream_l4_untimed_session_key " +
                                "AND stream.start_time >= rtsp.setup_established_at - INTERVAL '10 seconds' " +
                                "AND stream.start_time <= rtsp.setup_established_at + INTERVAL '10 seconds' " +
                                "AND stream.tap_uuid = rtsp.tap_uuid " +
                                "WHERE rtsp.setup_tcp_session_key = :session_key " +
                                "AND rtsp.tap_uuid IN (<taps>) " +
                                "GROUP BY rtsp.setup_tcp_session_key")
                        .bindList("taps", taps)
                        .bind("session_key", sessionKey)
                        .bind("active_cutoff", DateTime.now().minusMinutes(1))
                        .mapTo(RTSPStreamEntry.class)
                        .findOne()
        );
    }

    public List<GenericIntegerHistogramEntry> getActiveStreamsHistogram(TimeRange timeRange,
                                                                        Bucketing.BucketingConfiguration bucketing,
                                                                        Filters filters,
                                                                        List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new RTSPFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(
                                "WITH buckets AS (" +
                                        "SELECT generate_series(" +
                                        "date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                        "date_trunc(:date_trunc, :tr_to::timestamptz), " +
                                        "make_interval(secs => :bucket_size_s)" +
                                        ") AS bucket" +
                                        "), " +
                                        "sessions AS (" +
                                        "SELECT rtsp.setup_tcp_session_key, " +
                                        "MIN(rtsp.setup_established_at) AS session_start, " +
                                        "GREATEST(MAX(rtsp.setup_most_recent_segment_time), " +
                                        "MAX(stream.most_recent_segment_time)) AS session_end " +
                                        "FROM rtsp_streams AS rtsp " +
                                        "LEFT JOIN l4_sessions AS setup " +
                                        "ON setup.session_key = rtsp.setup_tcp_session_key " +
                                        "AND setup.start_time >= rtsp.setup_established_at - INTERVAL '10 seconds' " +
                                        "AND setup.start_time <= rtsp.setup_established_at + INTERVAL '10 seconds' " +
                                        "AND setup.tap_uuid = rtsp.tap_uuid " +
                                        "LEFT JOIN l4_sessions AS stream " +
                                        "ON stream.untimed_session_key = rtsp.stream_l4_untimed_session_key " +
                                        "AND stream.start_time >= rtsp.setup_established_at - INTERVAL '10 seconds' " +
                                        "AND stream.start_time <= rtsp.setup_established_at + INTERVAL '10 seconds' " +
                                        "AND stream.tap_uuid = rtsp.tap_uuid " +
                                        "WHERE ((rtsp.setup_most_recent_segment_time >= :tr_from " +
                                        "AND rtsp.setup_most_recent_segment_time <= :tr_to) " +
                                        "OR (stream.most_recent_segment_time >= :tr_from " +
                                        "AND stream.most_recent_segment_time <= :tr_to)) " +
                                        "AND rtsp.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                                        " GROUP BY rtsp.setup_tcp_session_key HAVING 1=1 " + filterFragment.havingSql() +
                                        ") " +
                                        "SELECT b.bucket AS bucket, COUNT(sess.setup_tcp_session_key) AS value " +
                                        "FROM buckets AS b " +
                                        "LEFT JOIN sessions AS sess " +
                                        "ON sess.session_start <= b.bucket + make_interval(secs => :bucket_size_s) " +
                                        "AND sess.session_end >= b.bucket " +
                                        "GROUP BY b.bucket ORDER BY b.bucket DESC")
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("bucket_size_s", bucketing.bucketSizeMs() / 1000.0)
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    public long getTopServersCount(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new RTSPFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT server_address FROM (" +
                                rtspSessionEndpointsSelect(filterFragment) +
                                ") AS sess WHERE server_address IS NOT NULL " +
                                "GROUP BY server_address" +
                                ") AS distinct_servers")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<L4AddressDataAddressNumberNumberAggregationResult> getTopServers(TimeRange timeRange,
                                                                                 Filters filters,
                                                                                 int limit, int offset,
                                                                                 ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                                 OrderDirection orderDirection,
                                                                                 List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new RTSPFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH sess AS (" + rtspSessionEndpointsSelectWithAttrs(filterFragment) + ") " +
                                "SELECT host(sess.server_address) AS key, " +
                                "host(sess.server_address) AS key_address, " +
                                "MAX(sess.server_mac) AS key_mac, MAX(sess.server_port) AS key_port, " +
                                "MAX(sess.server_geo_asn_number) AS key_address_geo_asn_number, " +
                                "MAX(sess.server_geo_asn_name) AS key_address_geo_asn_name, " +
                                "MAX(sess.server_geo_asn_domain) AS key_address_geo_asn_domain, " +
                                "MAX(sess.server_geo_city) AS key_address_geo_city, " +
                                "MAX(sess.server_geo_country_code) AS key_address_geo_country_code, " +
                                "MAX(sess.server_geo_latitude) AS key_address_geo_latitude, " +
                                "MAX(sess.server_geo_longitude) AS key_address_geo_longitude, " +
                                "BOOL_OR(sess.server_is_site_local) AS key_address_is_site_local, " +
                                "BOOL_OR(sess.server_is_loopback) AS key_address_is_loopback, " +
                                "BOOL_OR(sess.server_is_multicast) AS key_address_is_multicast, " +
                                "COUNT(*) AS value1, " +
                                "COALESCE(SUM(sess.bytes_exchanged), 0) AS value2 " +
                                "FROM sess " +
                                "WHERE sess.server_address IS NOT NULL " +
                                "GROUP BY sess.server_address " +
                                "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(L4AddressDataAddressNumberNumberAggregationResult.class)
                        .list()
        );
    }

    public long getTopClientsCount(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new RTSPFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT client_address FROM (" +
                                rtspSessionEndpointsSelect(filterFragment) +
                                ") AS sess WHERE client_address IS NOT NULL " +
                                "GROUP BY client_address" +
                                ") AS distinct_clients")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<L4AddressDataAddressNumberNumberAggregationResult> getTopClients(TimeRange timeRange,
                                                                                 Filters filters,
                                                                                 int limit,
                                                                                 int offset,
                                                                                 ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                                 OrderDirection orderDirection,
                                                                                 List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new RTSPFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH sess AS (" + rtspSessionEndpointsSelectWithAttrs(filterFragment) + ") " +
                                "SELECT host(sess.client_address) AS key, " +
                                "host(sess.client_address) AS key_address, " +
                                "MAX(sess.client_mac) AS key_mac, MAX(sess.client_port) AS key_port, " +
                                "MAX(sess.client_geo_asn_number) AS key_address_geo_asn_number, " +
                                "MAX(sess.client_geo_asn_name) AS key_address_geo_asn_name, " +
                                "MAX(sess.client_geo_asn_domain) AS key_address_geo_asn_domain, " +
                                "MAX(sess.client_geo_city) AS key_address_geo_city, " +
                                "MAX(sess.client_geo_country_code) AS key_address_geo_country_code, " +
                                "MAX(sess.client_geo_latitude) AS key_address_geo_latitude, " +
                                "MAX(sess.client_geo_longitude) AS key_address_geo_longitude, " +
                                "BOOL_OR(sess.client_is_site_local) AS key_address_is_site_local, " +
                                "BOOL_OR(sess.client_is_loopback) AS key_address_is_loopback, " +
                                "BOOL_OR(sess.client_is_multicast) AS key_address_is_multicast, " +
                                "COUNT(*) AS value1, " +
                                "COALESCE(SUM(sess.bytes_exchanged), 0) AS value2 " +
                                "FROM sess " +
                                "WHERE sess.client_address IS NOT NULL " +
                                "GROUP BY sess.client_address " +
                                "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(L4AddressDataAddressNumberNumberAggregationResult.class)
                        .list()
        );
    }

    private String rtspSessionEndpointsSelect(FilterSqlFragment filterFragment) {
        return "SELECT rtsp.setup_tcp_session_key, " +
                "MAX(setup.source_address) AS client_address, " +
                "MAX(setup.destination_address) AS server_address, " +
                "COALESCE(MAX(stream.bytes_rx_count), 0) + COALESCE(MAX(stream.bytes_tx_count), 0) " +
                "+ COALESCE(MAX(setup.bytes_rx_count), 0) + COALESCE(MAX(setup.bytes_tx_count), 0) " +
                "AS bytes_exchanged " +
                "FROM rtsp_streams AS rtsp " +
                "LEFT JOIN l4_sessions AS setup " +
                "ON setup.session_key = rtsp.setup_tcp_session_key " +
                "AND setup.start_time >= rtsp.setup_established_at - INTERVAL '10 seconds' " +
                "AND setup.start_time <= rtsp.setup_established_at + INTERVAL '10 seconds' " +
                "AND setup.tap_uuid = rtsp.tap_uuid " +
                "LEFT JOIN l4_sessions AS stream " +
                "ON stream.untimed_session_key = rtsp.stream_l4_untimed_session_key " +
                "AND stream.start_time >= rtsp.setup_established_at - INTERVAL '10 seconds' " +
                "AND stream.start_time <= rtsp.setup_established_at + INTERVAL '10 seconds' " +
                "AND stream.tap_uuid = rtsp.tap_uuid " +
                "WHERE ((rtsp.setup_most_recent_segment_time >= :tr_from " +
                "AND rtsp.setup_most_recent_segment_time <= :tr_to) " +
                "OR (stream.most_recent_segment_time >= :tr_from " +
                "AND stream.most_recent_segment_time <= :tr_to)) " +
                "AND rtsp.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                " GROUP BY rtsp.setup_tcp_session_key HAVING 1=1 " + filterFragment.havingSql();
    }

    private String rtspSessionEndpointsSelectWithAttrs(FilterSqlFragment filterFragment) {
        return "SELECT rtsp.setup_tcp_session_key, " +
                "MAX(setup.source_address) AS client_address, " +
                "MAX(setup.source_mac) AS client_mac, MAX(setup.source_port) AS client_port, " +
                "MAX(setup.source_address_geo_asn_number) AS client_geo_asn_number, " +
                "MAX(setup.source_address_geo_asn_name) AS client_geo_asn_name, " +
                "MAX(setup.source_address_geo_asn_domain) AS client_geo_asn_domain, " +
                "MAX(setup.source_address_geo_city) AS client_geo_city, " +
                "MAX(setup.source_address_geo_country_code) AS client_geo_country_code, " +
                "MAX(setup.source_address_geo_latitude) AS client_geo_latitude, " +
                "MAX(setup.source_address_geo_longitude) AS client_geo_longitude, " +
                "BOOL_OR(setup.source_address_is_site_local) AS client_is_site_local, " +
                "BOOL_OR(setup.source_address_is_loopback) AS client_is_loopback, " +
                "BOOL_OR(setup.source_address_is_multicast) AS client_is_multicast, " +
                "MAX(setup.destination_address) AS server_address, " +
                "MAX(setup.destination_mac) AS server_mac, MAX(setup.destination_port) AS server_port, " +
                "MAX(setup.destination_address_geo_asn_number) AS server_geo_asn_number, " +
                "MAX(setup.destination_address_geo_asn_name) AS server_geo_asn_name, " +
                "MAX(setup.destination_address_geo_asn_domain) AS server_geo_asn_domain, " +
                "MAX(setup.destination_address_geo_city) AS server_geo_city, " +
                "MAX(setup.destination_address_geo_country_code) AS server_geo_country_code, " +
                "MAX(setup.destination_address_geo_latitude) AS server_geo_latitude, " +
                "MAX(setup.destination_address_geo_longitude) AS server_geo_longitude, " +
                "BOOL_OR(setup.destination_address_is_site_local) AS server_is_site_local, " +
                "BOOL_OR(setup.destination_address_is_loopback) AS server_is_loopback, " +
                "BOOL_OR(setup.destination_address_is_multicast) AS server_is_multicast, " +
                "COALESCE(MAX(stream.bytes_rx_count), 0) + COALESCE(MAX(stream.bytes_tx_count), 0) " +
                "+ COALESCE(MAX(setup.bytes_rx_count), 0) + COALESCE(MAX(setup.bytes_tx_count), 0) " +
                "AS bytes_exchanged " +
                "FROM rtsp_streams AS rtsp " +
                "LEFT JOIN l4_sessions AS setup " +
                "ON setup.session_key = rtsp.setup_tcp_session_key " +
                "AND setup.start_time >= rtsp.setup_established_at - INTERVAL '10 seconds' " +
                "AND setup.start_time <= rtsp.setup_established_at + INTERVAL '10 seconds' " +
                "AND setup.tap_uuid = rtsp.tap_uuid " +
                "LEFT JOIN l4_sessions AS stream " +
                "ON stream.untimed_session_key = rtsp.stream_l4_untimed_session_key " +
                "AND stream.start_time >= rtsp.setup_established_at - INTERVAL '10 seconds' " +
                "AND stream.start_time <= rtsp.setup_established_at + INTERVAL '10 seconds' " +
                "AND stream.tap_uuid = rtsp.tap_uuid " +
                "WHERE ((rtsp.setup_most_recent_segment_time >= :tr_from " +
                "AND rtsp.setup_most_recent_segment_time <= :tr_to) " +
                "OR (stream.most_recent_segment_time >= :tr_from " +
                "AND stream.most_recent_segment_time <= :tr_to)) " +
                "AND rtsp.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                " GROUP BY rtsp.setup_tcp_session_key HAVING 1=1 " + filterFragment.havingSql();
    }

}
