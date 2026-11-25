package app.nzyme.core.ethernet.tls;

import app.nzyme.core.ethernet.InternalAddresses;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.StringNumberNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.tls.db.TLSCertificateDetailsEntry;
import app.nzyme.core.ethernet.tls.db.TLSCertificateEntry;
import app.nzyme.core.ethernet.tls.db.TLSChainCertificateEntry;
import app.nzyme.core.ethernet.tls.db.TLSSessionEntry;
import app.nzyme.core.ethernet.tls.db.TLSSessionTapEntry;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;

import com.google.common.io.BaseEncoding;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public class TLS {

    public enum SessionOrderColumn {

        SESSION_ID("tls.tcp_session_key"),
        CLIENT_ADDRESS("MIN(tls.source_address)"),
        CLIENT_MAC("MIN(tls.source_mac)"),
        SERVER_ADDRESS("MIN(tls.destination_address)"),
        SERVER_MAC("MIN(tls.destination_mac)"),
        SERVER_NAME("MIN(tls.server_name)"),
        VERSION("MIN(tls.version)"),
        CIPHER_SUITE("MIN(tls.cipher_suite)"),
        ALPN("MIN(tls.alpn_selected)"),
        JA4("MIN(tls.ja4)"),
        LEAF_COMMON_NAME("MIN(leaf.common_name)"),
        LEAF_ISSUER("MIN(leaf.issuer)"),
        CHAIN_TRUSTED("BOOL_AND(tls.chain_trusted)"),
        CONNECTION_STATUS("MIN(tls.connection_status)"),
        BYTES("MAX(tls.bytes_count)"),
        DURATION("duration_ms"),
        ESTABLISHED_AT("established_at");

        private final String columnName;

        SessionOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    public enum CertificateOrderColumn {

        FINGERPRINT("c.sha256"),
        COMMON_NAME("c.common_name"),
        SUBJECT("c.subject"),
        ISSUER("c.issuer"),
        NOT_BEFORE("c.not_before"),
        NOT_AFTER("c.not_after"),
        PUBLIC_KEY("c.public_key_algorithm, c.public_key_bits"),
        SIGNATURE_ALGORITHM("c.signature_algorithm"),
        SESSION_COUNT("session_count"),
        FIRST_SEEN("c.first_seen"),
        LAST_SEEN("c.last_seen");

        private final String columnName;

        CertificateOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    private static final String FROM_SESSIONS =
            "FROM tls_sessions AS tls " +
                    "LEFT JOIN certificates AS leaf ON leaf.sha256 = tls.server_leaf_sha256 ";

    private static final String SESSIONS_TIME_RANGE =
            "tls.most_recent_segment_time >= :tr_from AND tls.most_recent_segment_time <= :tr_to ";

    private static final String SESSIONS_TIME_RANGE_OVERLAPS =
            "tls.most_recent_segment_time >= :tr_from AND tls.established_at <= :tr_to ";

    private static final String SESSION_ENTRY_COLUMNS =
            "SELECT tls.tcp_session_key, " +
                    "MIN(tls.source_mac) AS source_mac, " +
                    "MIN(tls.destination_mac) AS destination_mac, " +
                    "MIN(tls.source_address)::text AS source_address, " +
                    "MIN(tls.source_port) AS source_port, " +
                    "MIN(tls.destination_address)::text AS destination_address, " +
                    "MIN(tls.destination_port) AS destination_port, " +
                    "MIN(tls.connection_status) AS connection_status, " +
                    "MAX(tls.bytes_count) AS bytes_count, " +
                    "MIN(tls.server_name) AS server_name, " +
                    "MIN(tls.version) AS version, " +
                    "MIN(tls.client_max_version) AS client_max_version, " +
                    "MIN(tls.cipher_suite) AS cipher_suite, " +
                    "MIN(tls.alpn_selected) AS alpn_selected, " +
                    "MIN(tls.client_hello->>'alpn') AS alpn_offered, " +
                    "MIN(tls.ja4) AS ja4, " +
                    "BOOL_OR(tls.session_resumed) AS session_resumed, " +
                    "BOOL_OR(tls.hello_retry_request) AS hello_retry_request, " +
                    "BOOL_OR(tls.encrypted_client_hello) AS encrypted_client_hello, " +
                    "BOOL_OR(tls.client_certificate_requested) AS client_certificate_requested, " +
                    "MIN(encode(tls.server_leaf_sha256, 'hex')) AS server_leaf_sha256, " +
                    "MIN(leaf.common_name) AS server_leaf_common_name, " +
                    "MIN(leaf.issuer) AS server_leaf_issuer, " +
                    "MIN(leaf.not_after) AS server_leaf_not_after, " +
                    "MIN(tls.server_chain_length) AS server_chain_length, " +
                    "MIN(encode(tls.client_leaf_sha256, 'hex')) AS client_leaf_sha256, " +
                    "BOOL_OR(tls.server_leaf_self_signed) AS server_leaf_self_signed, " +
                    "BOOL_AND(tls.sni_matches_certificate) AS sni_matches_certificate, " +
                    "BOOL_AND(tls.leaf_valid_at_handshake) AS leaf_valid_at_handshake, " +
                    "BOOL_AND(tls.chain_trusted) AS chain_trusted, " +
                    "MIN(tls.chain_trust_error) AS chain_trust_error, " +
                    "MIN(tls.established_at) AS established_at, " +
                    "MAX(tls.terminated_at) AS terminated_at, " +
                    "MAX(tls.most_recent_segment_time) AS most_recent_segment_time, " +
                    "(EXTRACT(EPOCH FROM " +
                    "(MAX(tls.most_recent_segment_time) - MIN(tls.established_at))) * 1000" +
                    ") AS duration_ms ";

    private static final String CERTIFICATE_REFERENCES =
            "refs AS (" +
                    "SELECT h.sha256 AS sha256, s.tap_uuid AS tap_uuid, s.most_recent_segment_time AS seen_at, " +
                    "'TLS' AS source, s.tcp_session_key AS reference_key " +
                    "FROM tls_sessions AS s " +
                    "CROSS JOIN LATERAL unnest(COALESCE(s.server_chain_sha256, '{}') || COALESCE(s.client_chain_sha256, '{}')) AS h(sha256) " +
                    "WHERE s.most_recent_segment_time >= :tr_from AND s.most_recent_segment_time <= :tr_to " +
                    "AND s.tap_uuid IN (<taps>) " +
                    "UNION ALL " +
                    "SELECT decode(j.hex, 'hex') AS sha256, r.tap_uuid AS tap_uuid, r.probed_at AS seen_at, " +
                    "'PORTAL_INTEGRITY' AS source, hop.uuid::text AS reference_key " +
                    "FROM portal_integrity_hops AS hop " +
                    "JOIN portal_integrity_reports AS r ON r.uuid = hop.report_uuid " +
                    "CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(hop.tls->'chain_sha256', '[]'::jsonb)) AS j(hex) " +
                    "WHERE r.probed_at >= :tr_from AND r.probed_at <= :tr_to " +
                    "AND r.tap_uuid IN (<taps>)" +
                    ") ";

    private static final String FROM_CERTIFICATES =
            "FROM refs JOIN certificates AS c ON c.sha256 = refs.sha256 ";

    private static final String CERTIFICATE_ENTRY_COLUMNS =
            "SELECT encode(c.sha256, 'hex') AS sha256, encode(c.sha1, 'hex') AS sha1, c.parsed_at, c.parse_error, " +
                    "c.subject, c.issuer, c.common_name, c.subject_alternative_names, c.not_before, c.not_after, " +
                    "c.is_ca, c.self_signed, c.public_key_algorithm, c.public_key_bits, c.signature_algorithm, " +
                    "c.extended_key_usage, c.first_seen, c.last_seen, " +
                    "COUNT(DISTINCT refs.reference_key) AS session_count, " +
                    "ARRAY_AGG(DISTINCT refs.source) AS sources ";

    private static final String CERTIFICATE_GROUP_BY =
            "GROUP BY c.sha256, c.sha1, c.parsed_at, c.parse_error, c.subject, c.issuer, c.common_name, " +
                    "c.subject_alternative_names, c.not_before, c.not_after, c.is_ca, c.self_signed, " +
                    "c.public_key_algorithm, c.public_key_bits, c.signature_algorithm, c.extended_key_usage, " +
                    "c.first_seen, c.last_seen ";

    private static final String EXPIRING_CONDITION = "AND c.not_after < NOW() + make_interval(days => :days) ";

    private final NzymeNode nzyme;

    public TLS(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
    }

    public long countAllSessions(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new TLSSessionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (" +
                                "SELECT tls.tcp_session_key " +
                                FROM_SESSIONS +
                                "WHERE " + SESSIONS_TIME_RANGE +
                                "AND tls.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY tls.tcp_session_key HAVING 1=1 " + filterFragment.havingSql() + " " +
                                ") AS sessions")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<TLSSessionEntry> findAllSessions(TimeRange timeRange,
                                                 Filters filters,
                                                 SessionOrderColumn orderColumn,
                                                 OrderDirection orderDirection,
                                                 int limit,
                                                 int offset,
                                                 List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new TLSSessionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SESSION_ENTRY_COLUMNS +
                                FROM_SESSIONS +
                                "WHERE " + SESSIONS_TIME_RANGE +
                                "AND tls.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY tls.tcp_session_key HAVING 1=1 " + filterFragment.havingSql() + " " +
                                "ORDER BY <order_column> <order_direction> NULLS LAST " +
                                "LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(TLSSessionEntry.class)
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

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new TLSSessionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH buckets AS (" +
                                "SELECT generate_series(" +
                                "date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                "date_trunc(:date_trunc, :tr_to::timestamptz), " +
                                "make_interval(secs => :bucket_size_s)" +
                                ") AS bucket" +
                                "), " +
                                "sess AS (" +
                                "SELECT tls.tcp_session_key, " +
                                "MIN(tls.established_at) AS established_at, " +
                                "MAX(tls.most_recent_segment_time) AS most_recent_segment_time " +
                                FROM_SESSIONS +
                                "WHERE " + SESSIONS_TIME_RANGE_OVERLAPS +
                                "AND tls.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                                "GROUP BY tls.tcp_session_key HAVING 1=1 " + filterFragment.havingSql() +
                                "), " +
                                "deltas AS (" +
                                "SELECT GREATEST(FLOOR(EXTRACT(EPOCH FROM (sess.established_at - " +
                                "date_trunc(:date_trunc, :tr_from::timestamptz))) / :bucket_size_s), 0)::bigint AS idx, " +
                                "1 AS delta FROM sess " +
                                "UNION ALL " +
                                "SELECT FLOOR(EXTRACT(EPOCH FROM (sess.most_recent_segment_time - " +
                                "date_trunc(:date_trunc, :tr_from::timestamptz))) / :bucket_size_s)::bigint + 1 AS idx, " +
                                "-1 AS delta FROM sess" +
                                "), " +
                                "per_bucket AS (" +
                                "SELECT idx, SUM(delta) AS delta FROM deltas GROUP BY idx" +
                                ") " +
                                "SELECT b.bucket AS bucket, " +
                                "SUM(COALESCE(p.delta, 0)) OVER (ORDER BY b.bucket)::bigint AS value " +
                                "FROM buckets AS b " +
                                "LEFT JOIN per_bucket AS p ON p.idx = FLOOR(EXTRACT(EPOCH FROM (b.bucket - " +
                                "date_trunc(:date_trunc, :tr_from::timestamptz))) / :bucket_size_s)::bigint " +
                                "ORDER BY b.bucket DESC")
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

    private enum Side {

        CLIENT("client", "source"),
        SERVER("server", "destination");

        private final String alias;
        private final String tlsPrefix;

        Side(String alias, String tlsPrefix) {
            this.alias = alias;
            this.tlsPrefix = tlsPrefix;
        }

    }

    private String sessionAggregateSelect(FilterSqlFragment filterFragment) {
        return "SELECT tls.tcp_session_key AS tcp_session_key, " +
                "MIN(tls.source_address) AS client_address, " +
                "MIN(tls.source_mac) AS client_mac, " +
                "MIN(tls.source_port) AS client_port, " +
                "MIN(tls.destination_address) AS server_address, " +
                "MIN(tls.destination_mac) AS server_mac, " +
                "MIN(tls.destination_port) AS server_port, " +
                "MIN(tls.server_name) AS server_name, " +
                "MIN(tls.version) AS version, " +
                "MIN(tls.ja4) AS ja4, " +
                "MAX(tls.bytes_count) AS bytes_count " +
                FROM_SESSIONS +
                "WHERE " + SESSIONS_TIME_RANGE +
                "AND tls.tap_uuid IN (<taps>) " + filterFragment.whereSql() + " " +
                "GROUP BY tls.tcp_session_key HAVING 1=1 " + filterFragment.havingSql() + " ";
    }

    private static String keyAddressColumns(Side side, InternalAddresses.Rules internalAddresses) {
        String a = "sess." + side.alias + "_address";

        return "host(" + a + ") AS key, " +
                "host(" + a + ") AS key_address, " +
                "MAX(sess." + side.alias + "_mac) AS key_mac, " +
                "MAX(sess." + side.alias + "_port) AS key_port, " +
                "NULL::integer AS key_address_geo_asn_number, " +
                "NULL::text AS key_address_geo_asn_name, " +
                "NULL::text AS key_address_geo_asn_domain, " +
                "NULL::text AS key_address_geo_city, " +
                "NULL::text AS key_address_geo_country_code, " +
                "NULL::float AS key_address_geo_latitude, " +
                "NULL::float AS key_address_geo_longitude, " +
                "(" + internalAddresses.sqlCondition(a) + ") AS key_address_is_site_local, " +
                "(" + a + " <<= '127.0.0.0/8'::cidr OR " + a + " = '::1'::inet) AS key_address_is_loopback, " +
                "(" + a + " <<= '224.0.0.0/4'::cidr OR " + a + " <<= 'ff00::/8'::cidr) AS key_address_is_multicast, ";
    }

    public long getTopClientsCount(TimeRange timeRange, Filters filters, List<UUID> taps) {
        return countDistinct("sess.client_address", timeRange, filters, taps);
    }

    public List<L4AddressDataAddressNumberNumberAggregationResult> getTopClients(TimeRange timeRange, Filters filters, int limit, int offset, ThreeColumnWithKeyHistogramOrderColumn orderColumn, OrderDirection orderDirection, List<UUID> taps, InternalAddresses.Rules internalAddresses) {
        return findTopAddresses(Side.CLIENT, timeRange, filters, limit, offset, orderColumn, orderDirection, taps, internalAddresses);
    }

    public long getTopServersCount(TimeRange timeRange, Filters filters, List<UUID> taps) {
        return countDistinct("sess.server_address", timeRange, filters, taps);
    }

    public List<L4AddressDataAddressNumberNumberAggregationResult> getTopServers(TimeRange timeRange, Filters filters, int limit, int offset, ThreeColumnWithKeyHistogramOrderColumn orderColumn, OrderDirection orderDirection, List<UUID> taps, InternalAddresses.Rules internalAddresses) {
        return findTopAddresses(Side.SERVER, timeRange, filters, limit, offset, orderColumn, orderDirection, taps, internalAddresses);
    }

    public long getTopServerNamesCount(TimeRange timeRange, Filters filters, List<UUID> taps) {
        return countDistinct("sess.server_name", timeRange, filters, taps);
    }

    public List<StringNumberNumberAggregationResult> getTopServerNames(TimeRange timeRange, Filters filters, int limit, int offset, ThreeColumnWithKeyHistogramOrderColumn orderColumn, OrderDirection orderDirection, List<UUID> taps) {
        return findTopStrings("sess.server_name", timeRange, filters, limit, offset, orderColumn, orderDirection, taps);
    }

    public long getTopServerPortsCount(TimeRange timeRange, Filters filters, List<UUID> taps) {
        return countDistinct("sess.server_port", timeRange, filters, taps);
    }

    public List<StringNumberNumberAggregationResult> getTopServerPorts(TimeRange timeRange, Filters filters, int limit, int offset, ThreeColumnWithKeyHistogramOrderColumn orderColumn, OrderDirection orderDirection, List<UUID> taps) {
        return findTopStrings("sess.server_port::text", timeRange, filters, limit, offset, orderColumn, orderDirection, taps);
    }

    public long getVersionsCount(TimeRange timeRange, Filters filters, List<UUID> taps) {
        return countDistinct("sess.version", timeRange, filters, taps);
    }

    public List<StringNumberNumberAggregationResult> getVersions(TimeRange timeRange, Filters filters, int limit, int offset, ThreeColumnWithKeyHistogramOrderColumn orderColumn, OrderDirection orderDirection, List<UUID> taps) {
        return findTopStrings("sess.version", timeRange, filters, limit, offset, orderColumn, orderDirection, taps);
    }

    public long getTopJa4Count(TimeRange timeRange, Filters filters, List<UUID> taps) {
        return countDistinct("sess.ja4", timeRange, filters, taps);
    }

    public List<StringNumberNumberAggregationResult> getTopJa4(TimeRange timeRange, Filters filters, int limit, int offset, ThreeColumnWithKeyHistogramOrderColumn orderColumn, OrderDirection orderDirection, List<UUID> taps) {
        return findTopStrings("sess.ja4", timeRange, filters, limit, offset, orderColumn, orderDirection, taps);
    }

    private long countDistinct(String keySql, TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new TLSSessionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT " + keySql + ") FROM (" +
                                sessionAggregateSelect(filterFragment) + ") AS sess")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    private List<L4AddressDataAddressNumberNumberAggregationResult> findTopAddresses(Side side,
                                                                                     TimeRange timeRange,
                                                                                     Filters filters,
                                                                                     int limit,
                                                                                     int offset,
                                                                                     ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                                     OrderDirection orderDirection,
                                                                                     List<UUID> taps,
                                                                                     InternalAddresses.Rules internalAddresses) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new TLSSessionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH sess AS (" + sessionAggregateSelect(filterFragment) + ") " +
                                "SELECT " + keyAddressColumns(side, internalAddresses) +
                                "COUNT(*) AS value1, " +
                                "COALESCE(SUM(sess.bytes_count), 0)::bigint AS value2 " +
                                "FROM sess " +
                                "WHERE sess." + side.alias + "_address IS NOT NULL " +
                                "GROUP BY sess." + side.alias + "_address " +
                                "ORDER BY <order_column> <order_direction>, key ASC " +
                                "LIMIT :limit OFFSET :offset")
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

    private List<StringNumberNumberAggregationResult> findTopStrings(String keySql,
                                                                     TimeRange timeRange,
                                                                     Filters filters,
                                                                     int limit,
                                                                     int offset,
                                                                     ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                     OrderDirection orderDirection,
                                                                     List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new TLSSessionFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH sess AS (" + sessionAggregateSelect(filterFragment) + ") " +
                                "SELECT t.type_key AS key, " +
                                "COUNT(*) AS value1, " +
                                "COALESCE(SUM(t.bytes_count), 0)::bigint AS value2 " +
                                "FROM (SELECT " + keySql + " AS type_key, sess.bytes_count FROM sess) AS t " +
                                "WHERE t.type_key IS NOT NULL " +
                                "GROUP BY t.type_key " +
                                "ORDER BY <order_column> <order_direction>, key ASC " +
                                "LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(StringNumberNumberAggregationResult.class)
                        .list()
        );
    }

    public long countAllCertificates(TimeRange timeRange, Filters filters, List<UUID> taps) {
        return countCertificates(timeRange, filters, taps, null);
    }

    public List<TLSCertificateEntry> findAllCertificates(TimeRange timeRange,
                                                         Filters filters,
                                                         CertificateOrderColumn orderColumn,
                                                         OrderDirection orderDirection,
                                                         int limit,
                                                         int offset,
                                                         List<UUID> taps) {
        return findCertificates(timeRange, filters, orderColumn, orderDirection, limit, offset, taps, null);
    }

    public long countExpiringCertificates(TimeRange timeRange, Filters filters, int days, List<UUID> taps) {
        return countCertificates(timeRange, filters, taps, days);
    }

    public List<TLSCertificateEntry> findExpiringCertificates(TimeRange timeRange,
                                                              Filters filters,
                                                              int days,
                                                              CertificateOrderColumn orderColumn,
                                                              OrderDirection orderDirection,
                                                              int limit,
                                                              int offset,
                                                              List<UUID> taps) {
        return findCertificates(timeRange, filters, orderColumn, orderDirection, limit, offset, taps, days);
    }

    private long countCertificates(TimeRange timeRange, Filters filters, List<UUID> taps, Integer expiringDays) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new TLSCertificateFilters());

        return nzyme.getDatabase().withHandle(handle -> {
            var query = handle.createQuery("WITH " + CERTIFICATE_REFERENCES +
                            "SELECT COUNT(*) FROM (" +
                            "SELECT c.sha256 " +
                            FROM_CERTIFICATES +
                            "WHERE 1=1 " + (expiringDays == null ? "" : EXPIRING_CONDITION) +
                            filterFragment.whereSql() + " " +
                            "GROUP BY c.sha256 HAVING 1=1 " + filterFragment.havingSql() + " " +
                            ") AS certificates")
                    .bindList("taps", taps)
                    .bindMap(filterFragment.bindings())
                    .bind("tr_from", timeRange.from())
                    .bind("tr_to", timeRange.to());

            if (expiringDays != null) {
                query.bind("days", expiringDays);
            }

            return query.mapTo(Long.class).one();
        });
    }

    private List<TLSCertificateEntry> findCertificates(TimeRange timeRange,
                                                       Filters filters,
                                                       CertificateOrderColumn orderColumn,
                                                       OrderDirection orderDirection,
                                                       int limit,
                                                       int offset,
                                                       List<UUID> taps,
                                                       Integer expiringDays) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new TLSCertificateFilters());

        return nzyme.getDatabase().withHandle(handle -> {
            var query = handle.createQuery("WITH " + CERTIFICATE_REFERENCES +
                            CERTIFICATE_ENTRY_COLUMNS +
                            FROM_CERTIFICATES +
                            "WHERE 1=1 " + (expiringDays == null ? "" : EXPIRING_CONDITION) +
                            filterFragment.whereSql() + " " +
                            CERTIFICATE_GROUP_BY + "HAVING 1=1 " + filterFragment.havingSql() + " " +
                            "ORDER BY <order_column> <order_direction> NULLS LAST " +
                            "LIMIT :limit OFFSET :offset")
                    .bindList("taps", taps)
                    .bindMap(filterFragment.bindings())
                    .bind("tr_from", timeRange.from())
                    .bind("tr_to", timeRange.to())
                    .bind("limit", limit)
                    .bind("offset", offset)
                    .define("order_column", orderColumn.getColumnName())
                    .define("order_direction", orderDirection);

            if (expiringDays != null) {
                query.bind("days", expiringDays);
            }

            return query.mapTo(TLSCertificateEntry.class).list();
        });
    }

    // Distinct certificates by the bucket their first sighting falls into, among those referenced in range.
    public List<GenericIntegerHistogramEntry> getNewCertificatesHistogram(TimeRange timeRange,
                                                                          Bucketing.BucketingConfiguration bucketing,
                                                                          Filters filters,
                                                                          List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new TLSCertificateFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH " + CERTIFICATE_REFERENCES + ", " +
                                "buckets AS (" +
                                "SELECT generate_series(" +
                                "date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                "date_trunc(:date_trunc, :tr_to::timestamptz), " +
                                "make_interval(secs => :bucket_size_s)" +
                                ") AS bucket" +
                                "), " +
                                "certs AS (" +
                                "SELECT c.sha256, c.first_seen " +
                                FROM_CERTIFICATES +
                                "WHERE c.first_seen >= :tr_from AND c.first_seen <= :tr_to " + filterFragment.whereSql() + " " +
                                "GROUP BY c.sha256, c.first_seen HAVING 1=1 " + filterFragment.havingSql() +
                                ") " +
                                "SELECT b.bucket AS bucket, COUNT(certs.sha256) AS value " +
                                "FROM buckets AS b " +
                                "LEFT JOIN certs ON certs.first_seen >= b.bucket " +
                                "AND certs.first_seen < b.bucket + make_interval(secs => :bucket_size_s) " +
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

    public enum CertificateHistogramKey {

        ISSUER("c.issuer"),
        KEY_TYPE("NULLIF(TRIM(COALESCE(c.public_key_algorithm, '') || ' ' || COALESCE(c.public_key_bits::text, '')), '')"),
        SIGNATURE_ALGORITHM("c.signature_algorithm");

        private final String keySql;

        CertificateHistogramKey(String keySql) {
            this.keySql = keySql;
        }

    }

    public long getCertificateHistogramCount(CertificateHistogramKey key, TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new TLSCertificateFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH " + CERTIFICATE_REFERENCES +
                                "SELECT COUNT(DISTINCT " + key.keySql + ") " +
                                FROM_CERTIFICATES +
                                "WHERE 1=1 " + filterFragment.whereSql() + " " +
                                (filterFragment.havingSql().isEmpty() ? "" :
                                        "AND c.sha256 IN (SELECT c.sha256 " + FROM_CERTIFICATES +
                                                "WHERE 1=1 " + filterFragment.whereSql() +
                                                " GROUP BY c.sha256 HAVING 1=1 " + filterFragment.havingSql() + ") "))
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<StringNumberNumberAggregationResult> getCertificateHistogram(CertificateHistogramKey key,
                                                                             TimeRange timeRange,
                                                                             Filters filters,
                                                                             int limit,
                                                                             int offset,
                                                                             ThreeColumnWithKeyHistogramOrderColumn orderColumn,
                                                                             OrderDirection orderDirection,
                                                                             List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new TLSCertificateFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH " + CERTIFICATE_REFERENCES +
                                "SELECT t.type_key AS key, " +
                                "COUNT(DISTINCT t.reference_key) AS value1, " +
                                "COUNT(DISTINCT t.sha256) AS value2 " +
                                "FROM (SELECT " + key.keySql + " AS type_key, refs.reference_key, c.sha256 " +
                                FROM_CERTIFICATES +
                                "WHERE 1=1 " + filterFragment.whereSql() + " " +
                                (filterFragment.havingSql().isEmpty() ? "" :
                                        "AND c.sha256 IN (SELECT c.sha256 " + FROM_CERTIFICATES +
                                                "WHERE 1=1 " + filterFragment.whereSql() +
                                                " GROUP BY c.sha256 HAVING 1=1 " + filterFragment.havingSql() + ") ") +
                                ") AS t " +
                                "WHERE t.type_key IS NOT NULL " +
                                "GROUP BY t.type_key " +
                                "ORDER BY <order_column> <order_direction>, key ASC " +
                                "LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(StringNumberNumberAggregationResult.class)
                        .list()
        );
    }

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    public Optional<TLSSessionEntry> findSession(String sessionKey, List<UUID> taps) {
        if (sessionKey == null || taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SESSION_ENTRY_COLUMNS +
                                FROM_SESSIONS +
                                "WHERE tls.tcp_session_key = :tcp_session_key AND tls.tap_uuid IN (<taps>) " +
                                "GROUP BY tls.tcp_session_key")
                        .bindList("taps", taps)
                        .bind("tcp_session_key", sessionKey)
                        .mapTo(TLSSessionEntry.class)
                        .findOne()
        );
    }

    public List<TLSSessionTapEntry> findSessionTapEntries(String sessionKey, List<UUID> taps) {
        if (sessionKey == null || taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT tap_uuid, client_hello::text AS client_hello, " +
                                "server_hello::text AS server_hello, alerts::text AS alerts, " +
                                "ARRAY(SELECT encode(h, 'hex') FROM unnest(server_chain_sha256) AS h) AS server_chain_sha256, " +
                                "ARRAY(SELECT encode(h, 'hex') FROM unnest(client_chain_sha256) AS h) AS client_chain_sha256, " +
                                "client_change_cipher_spec, server_change_cipher_spec, " +
                                "client_app_data_records, client_app_data_bytes, " +
                                "server_app_data_records, server_app_data_bytes, most_recent_segment_time " +
                                "FROM tls_sessions " +
                                "WHERE tcp_session_key = :tcp_session_key AND tap_uuid IN (<taps>) " +
                                "ORDER BY most_recent_segment_time DESC")
                        .bindList("taps", taps)
                        .bind("tcp_session_key", sessionKey)
                        .mapTo(TLSSessionTapEntry.class)
                        .list()
        );
    }

    public List<TLSChainCertificateEntry> findChainCertificates(List<String> sha256Hashes) {
        List<byte[]> hashes = sha256Hashes.stream()
                .filter(h -> h != null && SHA256_HEX.matcher(h).matches())
                .map(h -> BaseEncoding.base16().lowerCase().decode(h))
                .toList();

        if (hashes.isEmpty()) {
            return Collections.emptyList();
        }

        List<TLSChainCertificateEntry> found = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT encode(c.sha256, 'hex') AS sha256, c.parsed_at, c.parse_error, " +
                                "c.subject, c.issuer, c.common_name, c.not_before, c.not_after, c.is_ca, " +
                                "c.self_signed, c.public_key_algorithm, c.public_key_bits, c.signature_algorithm " +
                                "FROM certificates AS c WHERE c.sha256 IN (<hashes>)")
                        .bindList("hashes", hashes)
                        .mapTo(TLSChainCertificateEntry.class)
                        .list()
        );

        return sha256Hashes.stream()
                .flatMap(h -> found.stream().filter(c -> c.sha256().equals(h)))
                .toList();
    }

    public boolean isCertificateVisible(String sha256, List<UUID> taps) {
        if (sha256 == null || !SHA256_HEX.matcher(sha256).matches() || taps.isEmpty()) {
            return false;
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT EXISTS(SELECT 1 FROM tls_sessions " +
                                "WHERE tap_uuid IN (<taps>) " +
                                "AND (server_chain_sha256 @> ARRAY[decode(:sha256, 'hex')] " +
                                "OR client_chain_sha256 @> ARRAY[decode(:sha256, 'hex')])) " +
                                "OR EXISTS(SELECT 1 FROM portal_integrity_hops AS hop " +
                                "JOIN portal_integrity_reports AS r ON r.uuid = hop.report_uuid " +
                                "WHERE r.tap_uuid IN (<taps>) " +
                                "AND jsonb_exists(COALESCE(hop.tls->'chain_sha256', '[]'::jsonb), :sha256))")
                        .bindList("taps", taps)
                        .bind("sha256", sha256)
                        .mapTo(Boolean.class)
                        .one()
        );
    }

    public Optional<TLSCertificateDetailsEntry> findCertificate(String sha256, TimeRange timeRange, List<UUID> taps) {
        if (sha256 == null || !SHA256_HEX.matcher(sha256).matches() || taps.isEmpty()) {
            return Optional.empty();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("WITH " + CERTIFICATE_REFERENCES +
                                "SELECT encode(c.sha256, 'hex') AS sha256, encode(c.sha1, 'hex') AS sha1, " +
                                "c.parsed_at, c.parse_error, c.version, c.serial, c.subject, c.issuer, " +
                                "c.common_name, c.subject_alternative_names, c.not_before, c.not_after, " +
                                "c.is_ca, c.self_signed, c.public_key_algorithm, c.public_key_bits, " +
                                "c.signature_algorithm, c.extended_key_usage, " +
                                "encode(c.subject_key_identifier, 'hex') AS subject_key_identifier, " +
                                "encode(c.authority_key_identifier, 'hex') AS authority_key_identifier, " +
                                "c.der, c.first_seen, c.last_seen, " +
                                "COUNT(DISTINCT refs.reference_key) AS session_count, " +
                                "ARRAY_REMOVE(ARRAY_AGG(DISTINCT refs.source), NULL) AS sources " +
                                "FROM certificates AS c LEFT JOIN refs ON refs.sha256 = c.sha256 " +
                                "WHERE c.sha256 = decode(:sha256, 'hex') " +
                                "GROUP BY c.sha256")
                        .bindList("taps", taps)
                        .bind("sha256", sha256)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(TLSCertificateDetailsEntry.class)
                        .findOne()
        );
    }

    public Optional<TLSChainCertificateEntry> findIssuerCertificate(String authorityKeyIdentifier, List<UUID> taps) {
        if (authorityKeyIdentifier == null || authorityKeyIdentifier.isEmpty()
                || !authorityKeyIdentifier.matches("^[0-9a-f]+$") || taps.isEmpty()) {
            return Optional.empty();
        }

        Optional<TLSChainCertificateEntry> issuer = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT encode(c.sha256, 'hex') AS sha256, c.parsed_at, c.parse_error, " +
                                "c.subject, c.issuer, c.common_name, c.not_before, c.not_after, c.is_ca, " +
                                "c.self_signed, c.public_key_algorithm, c.public_key_bits, c.signature_algorithm " +
                                "FROM certificates AS c " +
                                "WHERE c.subject_key_identifier = decode(:ski, 'hex') AND c.is_ca = true " +
                                "ORDER BY c.last_seen DESC LIMIT 1")
                        .bind("ski", authorityKeyIdentifier)
                        .mapTo(TLSChainCertificateEntry.class)
                        .findOne()
        );

        if (issuer.isPresent() && !isCertificateVisible(issuer.get().sha256(), taps)) {
            return Optional.empty();
        }

        return issuer;
    }

}
