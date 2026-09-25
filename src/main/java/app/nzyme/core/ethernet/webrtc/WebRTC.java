package app.nzyme.core.ethernet.webrtc;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.*;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.webrtc.db.WebRTCSessionEntry;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class WebRTC {

    public enum SessionOrderColumn {

        IS_ACTIVE("is_active"),
        SOURCE_MAC("source_mac"),
        SOURCE_ADDRESS("source_address"),
        DESTINATION_MAC("destination_mac"),
        DESTINATION_ADDRESS("destination_address"),
        STREAM_COUNT("stream_count"),
        HAS_RTP("has_rtp"),
        HAS_DTLS("has_dtls"),
        HAS_AUDIO("has_audio"),
        HAS_VIDEO("has_video"),
        BYTES("bytes_exchanged"),
        LAST_ACTIVITY("last_activity"),
        INITIATED_AT("first_seen"),
        DURATION("duration_ms");

        private final String columnName;

        SessionOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    private final NzymeNode nzyme;

    public WebRTC(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
    }

    public long countAllSessions(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new WebRTCFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                endpointsCteByTime() +
                                "SELECT 1 FROM webrtc_conversations AS w " +
                                "LEFT JOIN endpoints AS s ON s.negotiation_key = w.negotiation_key " +
                                "LEFT JOIN session_bytes AS sb ON sb.negotiation_key = w.negotiation_key " +
                                "WHERE w.last_activity >= :tr_from AND w.last_activity <= :tr_to " +
                                "AND w.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                                " GROUP BY w.negotiation_key HAVING 1=1 " + filterFragment.havingSql() +
                                ") AS ignored")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<WebRTCSessionEntry> findAllSessions(TimeRange timeRange,
                                                    Filters filters,
                                                    SessionOrderColumn orderColumn,
                                                    OrderDirection orderDirection,
                                                    int limit, int offset,
                                                    List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new WebRTCFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(endpointsCteByTime() + sessionSelect() +
                                "WHERE w.last_activity >= :tr_from AND w.last_activity <= :tr_to " +
                                "AND w.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                                " GROUP BY w.negotiation_key HAVING 1=1 " + filterFragment.havingSql() +
                                " ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(WebRTCSessionEntry.class)
                        .list()
        );
    }

    public Optional<WebRTCSessionEntry> findOneSession(String negotiationKeySha256, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(endpointsCteBySha() + sessionSelect() +
                                "WHERE w.negotiation_key_sha256 = :negotiation_key_sha256 " +
                                "AND w.tap_uuid IN (<taps>) " +
                                "GROUP BY w.negotiation_key")
                        .bindList("taps", taps)
                        .bind("negotiation_key_sha256", negotiationKeySha256)
                        .mapTo(WebRTCSessionEntry.class)
                        .findOne()
        );
    }

    public List<WebRTCSessionEntry> findSubSessionsOfSession(String negotiationKeySha256, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(conversationEndpointsCteBySha() +
                                "SELECT MAX(w.negotiation_key) AS negotiation_key, " +
                                "MAX(w.negotiation_key_sha256) AS negotiation_key_sha256, " +
                                "UPPER(MAX(w.transport)) AS transport, " +
                                "BOOL_OR(w.has_rtp) AS has_rtp, BOOL_OR(w.has_dtls) AS has_dtls, " +
                                "BOOL_OR(w.has_audio) AS has_audio, BOOL_OR(w.has_video) AS has_video, " +
                                "MAX(w.stream_count) AS stream_count, " +
                                "MAX(w.rtp_streams::text)::jsonb AS rtp_streams, " +
                                "MAX(w.dtls_app_data_records) AS dtls_app_data_records, " +
                                "MIN(w.first_seen) AS first_seen, MAX(w.last_activity) AS last_activity, " +
                                "(MAX(w.last_activity) >= NOW() - INTERVAL '60 seconds') AS is_active, " +
                                "(EXTRACT(EPOCH FROM (MAX(w.last_activity) - MIN(w.first_seen))) * 1000)::bigint AS duration_ms, " +
                                "MAX(s.bytes_rx_count + s.bytes_tx_count) AS bytes_exchanged, " +
                                "MAX(s.source_mac) AS source_mac, MAX(s.source_address) AS source_address, MAX(s.source_port) AS source_port, " +
                                "MAX(s.source_address_geo_asn_number) AS source_address_geo_asn_number, " +
                                "MAX(s.source_address_geo_asn_name) AS source_address_geo_asn_name, " +
                                "MAX(s.source_address_geo_asn_domain) AS source_address_geo_asn_domain, " +
                                "MAX(s.source_address_geo_city) AS source_address_geo_city, " +
                                "MAX(s.source_address_geo_country_code) AS source_address_geo_country_code, " +
                                "MAX(s.source_address_geo_latitude) AS source_address_geo_latitude, " +
                                "MAX(s.source_address_geo_longitude) AS source_address_geo_longitude, " +
                                "BOOL_OR(s.source_address_is_site_local) AS source_address_is_site_local, " +
                                "BOOL_OR(s.source_address_is_loopback) AS source_address_is_loopback, " +
                                "BOOL_OR(s.source_address_is_multicast) AS source_address_is_multicast, " +
                                "MAX(s.destination_mac) AS destination_mac, MAX(s.destination_address) AS destination_address, MAX(s.destination_port) AS destination_port, " +
                                "MAX(s.destination_address_geo_asn_number) AS destination_address_geo_asn_number, " +
                                "MAX(s.destination_address_geo_asn_name) AS destination_address_geo_asn_name, " +
                                "MAX(s.destination_address_geo_asn_domain) AS destination_address_geo_asn_domain, " +
                                "MAX(s.destination_address_geo_city) AS destination_address_geo_city, " +
                                "MAX(s.destination_address_geo_country_code) AS destination_address_geo_country_code, " +
                                "MAX(s.destination_address_geo_latitude) AS destination_address_geo_latitude, " +
                                "MAX(s.destination_address_geo_longitude) AS destination_address_geo_longitude, " +
                                "BOOL_OR(s.destination_address_is_site_local) AS destination_address_is_site_local, " +
                                "BOOL_OR(s.destination_address_is_loopback) AS destination_address_is_loopback, " +
                                "BOOL_OR(s.destination_address_is_multicast) AS destination_address_is_multicast " +
                                "FROM webrtc_conversations AS w " +
                                "LEFT JOIN conversation_endpoints AS s " +
                                "ON s.tap_uuid = w.tap_uuid AND s.session_key = w.l4_session_key " +
                                "WHERE w.negotiation_key_sha256 = :negotiation_key_sha256 " +
                                "AND w.tap_uuid IN (<taps>) " +
                                "GROUP BY w.l4_session_key " +
                                "ORDER BY MIN(w.first_seen) ASC")
                        .bindList("taps", taps)
                        .bind("negotiation_key_sha256", negotiationKeySha256)
                        .mapTo(WebRTCSessionEntry.class)
                        .list()
        );
    }

    public List<GenericIntegerHistogramEntry> getActiveSessionsHistogram(TimeRange timeRange,
                                                                         Bucketing.BucketingConfiguration bucketing,
                                                                         Filters filters,
                                                                         List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new WebRTCFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(endpointsCteByTime() + ", " +
                                "buckets AS (" +
                                "SELECT generate_series(" +
                                "date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                "date_trunc(:date_trunc, :tr_to::timestamptz), " +
                                "make_interval(secs => :bucket_size_s)" +
                                ") AS bucket" +
                                "), " +
                                "sessions AS (" +
                                "SELECT w.negotiation_key, " +
                                "MIN(w.first_seen) AS session_start, " +
                                "MAX(w.last_activity) AS session_end " +
                                "FROM webrtc_conversations AS w " +
                                "LEFT JOIN endpoints AS s ON s.negotiation_key = w.negotiation_key " +
                                "LEFT JOIN session_bytes AS sb ON sb.negotiation_key = w.negotiation_key " +
                                "WHERE w.last_activity >= :tr_from AND w.first_seen <= :tr_to " +
                                "AND w.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                                " GROUP BY w.negotiation_key HAVING 1=1 " + filterFragment.havingSql() +
                                ") " +
                                "SELECT b.bucket AS bucket, COUNT(sess.negotiation_key) AS value " +
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

    public long getTopPeerAddressPairsByBytesCount(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new WebRTCFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(endpointsCteByTime() +
                                "SELECT COUNT(*) FROM (" +
                                "SELECT LEAST(a, b) AS addr1, GREATEST(a, b) AS addr2 FROM (" +
                                peerPairSessionSelectByAddress(filterFragment) +
                                ") AS sess WHERE a IS NOT NULL AND b IS NOT NULL " +
                                "GROUP BY LEAST(a, b), GREATEST(a, b)" +
                                ") AS distinct_pairs")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<L4AddressPairNumberAggregationResult> getTopPeerAddressPairsByBytes(TimeRange timeRange,
                                                                                    Filters filters,
                                                                                    int limit,
                                                                                    int offset,
                                                                                    ThreeColumnHistogramOrderColumn orderColumn,
                                                                                    OrderDirection orderDirection,
                                                                                    List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new WebRTCFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(endpointsCteByTime() + ", " +
                                "address_attrs AS (" +
                                "SELECT DISTINCT ON (addr) addr, mac, port, " +
                                "geo_asn_number, geo_asn_name, geo_asn_domain, geo_city, " +
                                "geo_country_code, geo_latitude, geo_longitude, " +
                                "is_site_local, is_loopback, is_multicast FROM (" +
                                "SELECT source_address AS addr, source_mac AS mac, source_port AS port, " +
                                "source_address_geo_asn_number AS geo_asn_number, source_address_geo_asn_name AS geo_asn_name, " +
                                "source_address_geo_asn_domain AS geo_asn_domain, source_address_geo_city AS geo_city, " +
                                "source_address_geo_country_code AS geo_country_code, source_address_geo_latitude AS geo_latitude, " +
                                "source_address_geo_longitude AS geo_longitude, source_address_is_site_local AS is_site_local, " +
                                "source_address_is_loopback AS is_loopback, source_address_is_multicast AS is_multicast " +
                                "FROM conversation_endpoints " +
                                "UNION ALL " +
                                "SELECT destination_address, destination_mac, destination_port, " +
                                "destination_address_geo_asn_number, destination_address_geo_asn_name, " +
                                "destination_address_geo_asn_domain, destination_address_geo_city, " +
                                "destination_address_geo_country_code, destination_address_geo_latitude, " +
                                "destination_address_geo_longitude, destination_address_is_site_local, " +
                                "destination_address_is_loopback, destination_address_is_multicast " +
                                "FROM conversation_endpoints" +
                                ") AS all_addrs WHERE addr IS NOT NULL ORDER BY addr" +
                                "), " +
                                "pairs AS (" +
                                "SELECT LEAST(a, b) AS addr1, GREATEST(a, b) AS addr2, SUM(bytes_exchanged) AS value FROM (" +
                                peerPairSessionSelectByAddress(filterFragment) +
                                ") AS sess WHERE a IS NOT NULL AND b IS NOT NULL " +
                                "GROUP BY LEAST(a, b), GREATEST(a, b)" +
                                ") " +
                                "SELECT host(p.addr1) AS value1, host(p.addr1) AS value1_address, a1.mac AS value1_mac, a1.port AS value1_port, " +
                                "a1.geo_asn_number AS value1_address_geo_asn_number, a1.geo_asn_name AS value1_address_geo_asn_name, " +
                                "a1.geo_asn_domain AS value1_address_geo_asn_domain, a1.geo_city AS value1_address_geo_city, " +
                                "a1.geo_country_code AS value1_address_geo_country_code, a1.geo_latitude AS value1_address_geo_latitude, " +
                                "a1.geo_longitude AS value1_address_geo_longitude, a1.is_site_local AS value1_address_is_site_local, " +
                                "a1.is_loopback AS value1_address_is_loopback, a1.is_multicast AS value1_address_is_multicast, " +
                                "host(p.addr2) AS value2, host(p.addr2) AS value2_address, a2.mac AS value2_mac, a2.port AS value2_port, " +
                                "a2.geo_asn_number AS value2_address_geo_asn_number, a2.geo_asn_name AS value2_address_geo_asn_name, " +
                                "a2.geo_asn_domain AS value2_address_geo_asn_domain, a2.geo_city AS value2_address_geo_city, " +
                                "a2.geo_country_code AS value2_address_geo_country_code, a2.geo_latitude AS value2_address_geo_latitude, " +
                                "a2.geo_longitude AS value2_address_geo_longitude, a2.is_site_local AS value2_address_is_site_local, " +
                                "a2.is_loopback AS value2_address_is_loopback, a2.is_multicast AS value2_address_is_multicast, " +
                                "p.value AS value3 " +
                                "FROM pairs AS p " +
                                "LEFT JOIN address_attrs AS a1 ON a1.addr = p.addr1 " +
                                "LEFT JOIN address_attrs AS a2 ON a2.addr = p.addr2 " +
                                "ORDER BY <order_column> <order_direction> LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(L4AddressPairNumberAggregationResult.class)
                        .list()
        );
    }

    private String peerPairSessionSelectByAddress(FilterSqlFragment filterFragment) {
        return "SELECT MAX(s.source_address) AS a, MAX(s.destination_address) AS b, " +
                "MAX(sb.bytes_exchanged) AS bytes_exchanged " +
                "FROM webrtc_conversations AS w " +
                "LEFT JOIN endpoints AS s ON s.negotiation_key = w.negotiation_key " +
                "LEFT JOIN session_bytes AS sb ON sb.negotiation_key = w.negotiation_key " +
                "WHERE w.last_activity >= :tr_from AND w.last_activity <= :tr_to " +
                "AND w.tap_uuid IN (<taps>)" + filterFragment.whereSql() +
                " GROUP BY w.negotiation_key HAVING 1=1 " + filterFragment.havingSql();
    }

    private String endpointsCteByTime() {
        return "WITH webrtc_keys AS (" +
                "SELECT DISTINCT tap_uuid, l4_session_key, negotiation_key " +
                "FROM webrtc_conversations " +
                "WHERE tap_uuid IN (<taps>) " +
                "AND last_activity >= :tr_from AND last_activity <= :tr_to" +
                "), " +
                conversationEndpointsCteBody() + ", " +
                sessionEndpointsCteBody() + ", " +
                sessionBytesCteBody() + " ";
    }

    private String endpointsCteBySha() {
        return "WITH webrtc_keys AS (" +
                "SELECT DISTINCT tap_uuid, l4_session_key, negotiation_key " +
                "FROM webrtc_conversations " +
                "WHERE tap_uuid IN (<taps>) " +
                "AND negotiation_key_sha256 = :negotiation_key_sha256" +
                "), " +
                conversationEndpointsCteBody() + ", " +
                sessionEndpointsCteBody() + ", " +
                sessionBytesCteBody() + " ";
    }

    private String conversationEndpointsCteBySha() {
        return "WITH webrtc_keys AS (" +
                "SELECT DISTINCT tap_uuid, l4_session_key, negotiation_key " +
                "FROM webrtc_conversations " +
                "WHERE tap_uuid IN (<taps>) " +
                "AND negotiation_key_sha256 = :negotiation_key_sha256" +
                "), " +
                conversationEndpointsCteBody() + " ";
    }

    private String conversationEndpointsCteBody() {
        return "conversation_endpoints AS (" +
                "SELECT DISTINCT ON (s.tap_uuid, s.session_key) " +
                "k.negotiation_key, " +
                "s.tap_uuid, s.session_key, " +
                "s.source_mac, s.source_address, s.source_port, " +
                "s.source_address_geo_asn_number, s.source_address_geo_asn_name, " +
                "s.source_address_geo_asn_domain, s.source_address_geo_city, " +
                "s.source_address_geo_country_code, s.source_address_geo_latitude, " +
                "s.source_address_geo_longitude, s.source_address_is_site_local, " +
                "s.source_address_is_loopback, s.source_address_is_multicast, " +
                "s.destination_mac, s.destination_address, s.destination_port, " +
                "s.destination_address_geo_asn_number, s.destination_address_geo_asn_name, " +
                "s.destination_address_geo_asn_domain, s.destination_address_geo_city, " +
                "s.destination_address_geo_country_code, s.destination_address_geo_latitude, " +
                "s.destination_address_geo_longitude, s.destination_address_is_site_local, " +
                "s.destination_address_is_loopback, s.destination_address_is_multicast, " +
                "s.bytes_rx_count, s.bytes_tx_count, s.start_time " +
                "FROM l4_sessions AS s " +
                "JOIN webrtc_keys AS k " +
                "ON k.tap_uuid = s.tap_uuid AND k.l4_session_key = s.session_key " +
                "ORDER BY s.tap_uuid, s.session_key, s.start_time DESC" +
                ")";
    }

    private String sessionEndpointsCteBody() {
        return "endpoints AS (" +
                "SELECT DISTINCT ON (negotiation_key) * FROM conversation_endpoints " +
                "ORDER BY negotiation_key, source_address_is_site_local DESC, start_time DESC, " +
                "source_address, session_key" +
                ")";
    }

    private String sessionBytesCteBody() {
        return "session_bytes AS (" +
                "SELECT negotiation_key, SUM(bytes) AS bytes_exchanged FROM (" +
                "SELECT DISTINCT ON (session_key) negotiation_key, session_key, " +
                "(bytes_rx_count + bytes_tx_count) AS bytes " +
                "FROM conversation_endpoints " +
                "ORDER BY session_key, (bytes_rx_count + bytes_tx_count) DESC" +
                ") per_conversation GROUP BY negotiation_key" +
                ")";
    }

    private String sessionSelect() {
        return "SELECT MAX(w.negotiation_key) AS negotiation_key, " +
                "MAX(w.negotiation_key_sha256) AS negotiation_key_sha256, " +
                "UPPER(MAX(w.transport)) AS transport, " +
                "MIN(w.first_seen) AS first_seen, MAX(w.last_activity) AS last_activity, " +
                "(MAX(w.last_activity) >= NOW() - INTERVAL '60 seconds') AS is_active, " +
                "(EXTRACT(EPOCH FROM (MAX(w.last_activity) - MIN(w.first_seen))) * 1000)::bigint AS duration_ms, " +
                "BOOL_OR(w.has_rtp) AS has_rtp, " +
                "BOOL_OR(w.has_dtls) AS has_dtls, " +
                "BOOL_OR(w.has_audio) AS has_audio, " +
                "BOOL_OR(w.has_video) AS has_video, " +
                "MAX(w.stream_count) AS stream_count, " +
                "MAX(w.dtls_app_data_records) AS dtls_app_data_records, " +
                "MAX(sb.bytes_exchanged) AS bytes_exchanged, " +
                "MAX(s.source_mac) AS source_mac, " +
                "MAX(s.source_address) AS source_address, MAX(s.source_port) AS source_port, " +
                "MAX(s.source_address_geo_asn_number) AS source_address_geo_asn_number, " +
                "MAX(s.source_address_geo_asn_name) AS source_address_geo_asn_name, " +
                "MAX(s.source_address_geo_asn_domain) AS source_address_geo_asn_domain, " +
                "MAX(s.source_address_geo_city) AS source_address_geo_city, " +
                "MAX(s.source_address_geo_country_code) AS source_address_geo_country_code, " +
                "MAX(s.source_address_geo_latitude) AS source_address_geo_latitude, " +
                "MAX(s.source_address_geo_longitude) AS source_address_geo_longitude, " +
                "BOOL_OR(s.source_address_is_site_local) AS source_address_is_site_local, " +
                "BOOL_OR(s.source_address_is_loopback) AS source_address_is_loopback, " +
                "BOOL_OR(s.source_address_is_multicast) AS source_address_is_multicast, " +
                "MAX(s.destination_mac) AS destination_mac, " +
                "MAX(s.destination_address) AS destination_address, MAX(s.destination_port) AS destination_port, " +
                "MAX(s.destination_address_geo_asn_number) AS destination_address_geo_asn_number, " +
                "MAX(s.destination_address_geo_asn_name) AS destination_address_geo_asn_name, " +
                "MAX(s.destination_address_geo_asn_domain) AS destination_address_geo_asn_domain, " +
                "MAX(s.destination_address_geo_city) AS destination_address_geo_city, " +
                "MAX(s.destination_address_geo_country_code) AS destination_address_geo_country_code, " +
                "MAX(s.destination_address_geo_latitude) AS destination_address_geo_latitude, " +
                "MAX(s.destination_address_geo_longitude) AS destination_address_geo_longitude, " +
                "BOOL_OR(s.destination_address_is_site_local) AS destination_address_is_site_local, " +
                "BOOL_OR(s.destination_address_is_loopback) AS destination_address_is_loopback, " +
                "BOOL_OR(s.destination_address_is_multicast) AS destination_address_is_multicast, " +
                "COALESCE(jsonb_agg(DISTINCT rs.elem) FILTER (WHERE rs.elem IS NOT NULL), '[]'::jsonb) AS rtp_streams " +
                "FROM webrtc_conversations AS w " +
                "LEFT JOIN endpoints AS s ON s.negotiation_key = w.negotiation_key " +
                "LEFT JOIN session_bytes AS sb ON sb.negotiation_key = w.negotiation_key " +
                "LEFT JOIN LATERAL jsonb_array_elements(" +
                "CASE WHEN jsonb_typeof(w.rtp_streams) = 'array' THEN w.rtp_streams ELSE '[]'::jsonb END) " +
                "AS rs(elem) ON true ";
    }

}