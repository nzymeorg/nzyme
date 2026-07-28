package app.nzyme.core.ethernet.rtsp;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.rtsp.db.RTSPStreamEntry;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RTSP {

    public enum OrderColumn {

        SETUP_ESTABLISHED_AT("setup_established_at"),
        STATE("state");

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
                                "MAX(stream.bytes_tx_count) AS stream_bytes_tx " +
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
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(RTSPStreamEntry.class)
                        .list()
        );
    }

}
