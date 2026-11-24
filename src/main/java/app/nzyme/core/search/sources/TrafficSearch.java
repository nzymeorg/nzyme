package app.nzyme.core.search.sources;

import app.nzyme.core.rest.responses.briefing.BriefingLinkResponse;
import app.nzyme.core.rest.responses.search.SearchHitResponse;
import app.nzyme.core.search.SearchConditions;
import app.nzyme.core.search.SearchScope;
import app.nzyme.core.search.SearchSource;
import app.nzyme.core.search.SearchTerm;
import app.nzyme.core.search.SearchWindow;
import app.nzyme.core.search.SqlSearchSource;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;

/*
 * Network data the taps recorded on the Ethernet side. All of it is read by tap, like the pages of it do.
 *
 * Taps that see the same traffic record it once each, with session keys of their own. Hits are therefore
 * endpoints and pairs of endpoints and not single records, and where a hit counts records, it is the count of
 * the tap that saw the most.
 */
public class TrafficSearch {

    public static List<SearchSource> sources() {
        return List.of(new Sessions(), new DnsNames(), new TlsServers(), new Certificates(), new Dhcp(), new Arp(),
                new Ssh(), new Vpn(), new Socks(), new Ntp(), new Rtsp(), new WebRtc(), new Stun(),
                new PortalIntegrity());
    }

    private static final String TCP_OF = "JOIN l4_sessions tcp ON tcp.tap_uuid = x.tap_uuid "
            + "AND tcp.session_key = x.";

    private abstract static class TrafficSource extends SqlSearchSource {

        @Override
        public Tier tier() {
            return Tier.RECORDED;
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return scope.hasSubsystem(Subsystem.ETHERNET) && scope.hasTaps() && accepts(term);
        }

        protected abstract boolean accepts(SearchTerm term);

        protected SearchWindow window(Request request) {
            return request.windowOr(SearchWindow.lastHours(SearchWindow.DEFAULT_SCAN_HOURS));
        }

    }

    private static boolean addressed(SearchTerm term) {
        return term.isAnyOf(SearchTerm.Kind.MAC, SearchTerm.Kind.MAC_PREFIX, SearchTerm.Kind.IP,
                SearchTerm.Kind.CIDR);
    }

    private static boolean sessionKey(SearchTerm term) {
        return term.is(SearchTerm.Kind.HASH) && term.lower().length() == 64;
    }

    private static String endpoint(String address, Object port) {
        if (address == null) {
            return null;
        }

        if (port == null) {
            return address;
        }

        return (address.contains(":") ? "[" + address + "]" : address) + ":" + port;
    }

    private static BriefingLinkResponse listOf(String target, SearchTerm term, String addressField, String macField) {
        if (term.is(SearchTerm.Kind.IP) && addressField != null) {
            return SqlSearchSource.link(target, addressField, term.address());
        }

        if (term.is(SearchTerm.Kind.MAC) && macField != null) {
            return SqlSearchSource.link(target, macField, term.mac());
        }

        return null;
    }

    private static class Sessions extends TrafficSource {

        @Override
        public String id() {
            return "l4_sessions";
        }

        @Override
        public String title() {
            return "TCP/UDP Sessions";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return addressed(term) || sessionKey(term);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            List<SearchHitResponse> hits = Lists.newArrayList();

            if (sessionKey(term)) {
                // The index on the session key answers this over all data.
                SearchConditions key = new SearchConditions(term).custom("s.session_key = :s_lower");

                // Taps that saw the session start in the same millisecond record it under the same key.
                for (Map<String, Object> row : rows(handle, "SELECT * FROM ("
                        + "SELECT DISTINCT ON (s.l4_type) s.session_key, s.l4_type, s.start_time, "
                        + "host(s.source_address) AS source, host(s.destination_address) AS destination, "
                        + "s.destination_port, s.most_recent_segment_time FROM l4_sessions s "
                        + "WHERE s.tap_uuid IN (<taps>) AND " + key.sql() + " "
                        + "ORDER BY s.l4_type ASC, s.most_recent_segment_time DESC, s.tap_uuid ASC) x "
                        + "ORDER BY x.most_recent_segment_time DESC, x.l4_type ASC "
                        + "LIMIT :limit OFFSET :offset", key, request)) {
                    DateTime start = time(row, "start_time");

                    hits.add(hit(
                            "L4_SESSION",
                            text(row, "source") + " to " + endpoint(text(row, "destination"),
                                    row.get("destination_port")),
                            text(row, "l4_type") + " session",
                            facts(term).add("Session Key", row.get("session_key")).build(),
                            time(row, "most_recent_segment_time"),
                            SCORE_EXACT,
                            link("L4_SESSION", "type", text(row, "l4_type"), "key", text(row, "session_key"),
                                    "start", start == null ? null : start.toString())
                    ));
                }

                return Result.of(hits, request);
            }

            SearchWindow window = window(request);
            SearchConditions conditions = new SearchConditions(term)
                    .address("s.source_address", "s.destination_address")
                    .mac("s.source_mac", "s.destination_mac");

            String sql = "SELECT host(x.source_address) AS source, host(x.destination_address) AS destination, "
                    + "x.destination_port, x.l4_type, max(x.sessions) AS sessions, max(x.last_seen) AS last_seen "
                    + "FROM (SELECT s.tap_uuid, s.source_address, s.destination_address, s.destination_port, "
                    + "s.l4_type, count(*) AS sessions, max(s.most_recent_segment_time) AS last_seen "
                    + "FROM l4_sessions s WHERE s.tap_uuid IN (<taps>) "
                    + "AND s.most_recent_segment_time >= :w_from AND s.most_recent_segment_time <= :w_to "
                    + "AND " + conditions.sql() + " "
                    + "GROUP BY s.tap_uuid, s.source_address, s.destination_address, s.destination_port, s.l4_type) x "
                    + "GROUP BY x.source_address, x.destination_address, x.destination_port, x.l4_type "
                    + "ORDER BY last_seen DESC, sessions DESC, x.source_address, x.destination_address, "
                    + "x.destination_port, x.l4_type LIMIT :limit OFFSET :offset";

            for (Map<String, Object> row : rows(handle, sql, conditions, request, window)) {
                String source = text(row, "source");
                String destination = text(row, "destination");
                String port = row.get("destination_port") == null ? null : row.get("destination_port").toString();

                hits.add(hit(
                        "L4_CONVERSATION",
                        source + " to " + endpoint(destination, port),
                        text(row, "l4_type") + ", " + count(number(row, "sessions"), "session", "sessions"),
                        facts(term).build(),
                        time(row, "last_seen"),
                        SCORE_EXACT,
                        link("L4_SESSIONS", "source_address", source, "destination_address", destination,
                                "destination_port", port)
                ));
            }

            return Result.of(hits, request, window, listOf("L4_SESSIONS", term, "address", null));
        }

    }

    private static class DnsNames extends TrafficSource {

        @Override
        public String id() {
            return "dns";
        }

        @Override
        public String title() {
            return "DNS";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return term.isAnyOf(SearchTerm.Kind.IP, SearchTerm.Kind.MAC) || term.isRecordedText();
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchWindow window = window(request);
            List<SearchHitResponse> hits = Lists.newArrayList();

            String perName = "SELECT x.name, max(x.queries) AS queries, max(x.last_seen) AS last_seen FROM ("
                    + "SELECT q.tap_uuid, q.data_value AS name, count(*) AS queries, max(q.timestamp) AS last_seen "
                    + "FROM dns_log q %s WHERE q.dns_type = 'query' AND q.tap_uuid IN (<taps>) "
                    + "AND q.timestamp >= :w_from AND q.timestamp <= :w_to AND q.data_value <> '' AND %s "
                    + "GROUP BY q.tap_uuid, q.data_value) x GROUP BY x.name";
            String page = " ORDER BY %s last_seen DESC, name ASC LIMIT :limit OFFSET :offset";

            if (term.isRecordedText()) {
                SearchConditions names = new SearchConditions(term).contains("q.data_value");

                for (Map<String, Object> row : rows(handle, String.format(perName, "", names.sql())
                        + String.format(page, "(lower(x.name) = :s_lower) DESC,"), names, request, window)) {
                    hits.add(name(term, row, "Queried " + times(number(row, "queries")), null));
                }

                return Result.of(hits, request, window, null);
            }

            if (term.is(SearchTerm.Kind.MAC)) {
                SearchConditions client = new SearchConditions(term).mac("q.client_mac");

                for (Map<String, Object> row : rows(handle, String.format(perName, "", client.sql())
                        + String.format(page, ""), client, request, window)) {
                    hits.add(name(term, row, "Queried by this device " + times(number(row, "queries")), null));
                }

                return Result.of(hits, request, window, listOf("DNS_LOG", term, null, "client_mac"));
            }

            /*
             * Names that were answered with the address first, then names the address asked for. One statement,
             * so that the pages of the request run through both in one order. The answers are bound to the
             * window as well: transaction IDs repeat, and without it the join reads responses of all time.
             */
            SearchConditions address = new SearchConditions(term)
                    .custom("r.data_value = :s_ip")
                    .address("q.client_address");

            String answered = String.format(perName,
                    "JOIN dns_log r ON r.tap_uuid = q.tap_uuid AND r.transaction_id = q.transaction_id "
                            + "AND r.dns_type = 'response' AND r.data_type IN ('A', 'AAAA') "
                            + "AND r.timestamp >= q.timestamp AND r.timestamp <= q.timestamp + interval '10 seconds' "
                            + "AND r.timestamp >= :w_from AND r.timestamp <= :w_to",
                    "r.data_value = :s_ip");
            String asked = String.format(perName, "", "q.client_address = :s_ip::inet");

            String sql = "SELECT u.* FROM (SELECT 0 AS kind, a.* FROM (" + answered + ") a "
                    + "UNION ALL SELECT 1 AS kind, c.* FROM (" + asked + ") c) u "
                    + "ORDER BY u.kind ASC, u.last_seen DESC, u.name ASC LIMIT :limit OFFSET :offset";

            for (Map<String, Object> row : rows(handle, sql, address, request, window)) {
                hits.add(number(row, "kind") == 0
                        ? name(term, row, "Resolves to this address", SCORE_EXACT)
                        : name(term, row, "Queried by this address " + times(number(row, "queries")), SCORE_PREFIX));
            }

            return Result.of(hits, request, window, listOf("DNS_LOG", term, "client_address", null));
        }

        private static String times(long count) {
            return count(count, "time", "times");
        }

        private static SearchHitResponse name(SearchTerm term,
                                              Map<String, Object> row,
                                              String subtitle,
                                              @Nullable Integer score) {
            String name = text(row, "name");

            return hit(
                    "DNS_NAME",
                    name,
                    subtitle,
                    facts(term).build(),
                    time(row, "last_seen"),
                    score == null ? score(term, name) : score,
                    link("DNS_LOG", "query_value", (String) row.get("name"))
            );
        }

    }

    private static class TlsServers extends TrafficSource {

        @Override
        public String id() {
            return "tls";
        }

        @Override
        public String title() {
            return "TLS";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return addressed(term) || term.is(SearchTerm.Kind.JA4) || sessionKey(term) || term.isRecordedText();
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchWindow window = window(request);

            SearchConditions conditions = new SearchConditions(term)
                    .address("t.source_address", "t.destination_address")
                    .mac("t.source_mac", "t.destination_mac")
                    .custom("t.ja4 = :s_lower", SearchTerm.Kind.JA4);
            if (sessionKey(term)) {
                conditions.custom("t.server_leaf_sha256 = decode(:s_lower, 'hex')")
                        .custom("t.tcp_session_key = :s_lower");
            } else if (term.isRecordedText() && !term.is(SearchTerm.Kind.JA4)) {
                conditions.contains("t.server_name");
            }

            // A name is one hit, whatever addresses serve it. Servers without a name are told apart by address.
            String sql = "SELECT x.server_name, host(x.address) AS server, x.port, max(x.sessions) AS sessions, "
                    + "max(x.last_seen) AS last_seen, max(x.version) AS version "
                    + "FROM (SELECT t.tap_uuid, t.server_name, "
                    + "CASE WHEN t.server_name IS NULL THEN t.destination_address END AS address, "
                    + "CASE WHEN t.server_name IS NULL THEN t.destination_port END AS port, "
                    + "count(*) AS sessions, max(t.most_recent_segment_time) AS last_seen, max(t.version) AS version "
                    + "FROM tls_sessions t WHERE t.tap_uuid IN (<taps>) "
                    + "AND t.most_recent_segment_time >= :w_from AND t.most_recent_segment_time <= :w_to "
                    + "AND " + conditions.sql() + " GROUP BY 1, 2, 3, 4) x "
                    + "GROUP BY x.server_name, x.address, x.port "
                    + "ORDER BY (lower(x.server_name) = :s_lower) DESC NULLS LAST, last_seen DESC, x.server_name, "
                    + "x.address, x.port LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request, window)) {
                String name = text(row, "server_name");

                hits.add(hit(
                        "TLS_SERVER",
                        name == null ? endpoint(text(row, "server"), row.get("port")) : name,
                        "TLS server, " + count(number(row, "sessions"), "session", "sessions"),
                        facts(term).add("Version", row.get("version")).build(),
                        time(row, "last_seen"),
                        term.isIdentifier() || term.is(SearchTerm.Kind.JA4) || sessionKey(term)
                                ? SCORE_EXACT : score(term, name),
                        name == null
                                ? link("TLS_SESSIONS", "server_address", text(row, "server"))
                                : link("TLS_SESSIONS", "server_name", (String) row.get("server_name"))
                ));
            }

            return Result.of(hits, request, window, null);
        }

    }

    private static class Certificates extends TrafficSource {

        // Certificates are kept once for all taps. One is visible if a tap of the user saw it in use.
        private static final String VISIBLE = "(EXISTS (SELECT 1 FROM tls_sessions s WHERE s.tap_uuid IN (<taps>) "
                + "AND (s.server_chain_sha256 @> ARRAY[c.sha256] OR s.client_chain_sha256 @> ARRAY[c.sha256])) "
                + "OR EXISTS (SELECT 1 FROM portal_integrity_hops hop "
                + "JOIN portal_integrity_reports r ON r.uuid = hop.report_uuid WHERE r.tap_uuid IN (<taps>) "
                + "AND jsonb_exists(COALESCE(hop.tls->'chain_sha256', '[]'::jsonb), encode(c.sha256, 'hex'))))";

        private static final String SEEN = "s.tap_uuid IN (<taps>) AND s.most_recent_segment_time >= :w_from "
                + "AND s.most_recent_segment_time <= :w_to";

        /*
         * What the taps of the user saw in use in the window. A search by text starts from this and not from the
         * certificates: those of everyone else on the node would be candidates too, and each would be checked
         * against the taps on its own.
         */
        private static final String REFERENCED = "WITH refs AS ("
                + "SELECT unnest(s.server_chain_sha256) AS sha256 FROM tls_sessions s WHERE " + SEEN + " "
                + "UNION SELECT unnest(s.client_chain_sha256) FROM tls_sessions s WHERE " + SEEN + " "
                + "UNION SELECT decode(e.value, 'hex') FROM portal_integrity_reports r "
                + "JOIN portal_integrity_hops hop ON hop.report_uuid = r.uuid "
                + "CROSS JOIN LATERAL jsonb_array_elements_text(CASE WHEN jsonb_typeof(hop.tls->'chain_sha256') = "
                + "'array' THEN hop.tls->'chain_sha256' ELSE '[]'::jsonb END) AS e(value) "
                + "WHERE r.tap_uuid IN (<taps>) AND r.probed_at >= :w_from AND r.probed_at <= :w_to "
                + "AND e.value ~ '^[0-9a-f]{64}$') ";

        private static final String COLUMNS = "SELECT encode(c.sha256, 'hex') AS sha256, c.common_name, c.subject, "
                + "c.issuer, c.san_text, c.not_after, c.last_seen FROM certificates c ";

        @Override
        public String id() {
            return "tls_certificates";
        }

        @Override
        public String title() {
            return "TLS Certificates";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return term.is(SearchTerm.Kind.HASH) ? fingerprint(term) : term.isRecordedText();
        }

        private static boolean fingerprint(SearchTerm term) {
            return term.is(SearchTerm.Kind.HASH) && (term.lower().length() == 64 || term.lower().length() == 40);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchConditions conditions = new SearchConditions(term);
            SearchWindow window;
            String sql;

            if (fingerprint(term)) {
                // One certificate at most, found by its key. It is checked against the taps over all data.
                window = null;
                conditions.custom(term.lower().length() == 64
                        ? "c.sha256 = decode(:s_lower, 'hex')" : "c.sha1 = decode(:s_lower, 'hex')");
                sql = COLUMNS + "WHERE " + conditions.sql() + " AND " + VISIBLE + " "
                        + "ORDER BY c.last_seen DESC, c.sha256 ASC LIMIT :limit OFFSET :offset";
            } else {
                window = window(request);
                conditions.contains("c.common_name", "c.san_text", "c.subject", "c.issuer");
                sql = REFERENCED + COLUMNS + "JOIN refs ON refs.sha256 = c.sha256 WHERE " + conditions.sql() + " "
                        + "ORDER BY (lower(c.common_name) = :s_lower) DESC NULLS LAST, c.last_seen DESC, c.sha256 ASC "
                        + "LIMIT :limit OFFSET :offset";
            }

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request, window)) {
                String commonName = text(row, "common_name");
                DateTime notAfter = time(row, "not_after");

                hits.add(hit(
                        "TLS_CERTIFICATE",
                        commonName == null ? text(row, "subject") == null ? text(row, "sha256") : text(row, "subject")
                                : commonName,
                        "TLS certificate",
                        facts(term)
                                .add("Alternative Names", row.get("san_text"))
                                .add("Issuer", row.get("issuer"))
                                .add("Fingerprint", term.is(SearchTerm.Kind.HASH) ? row.get("sha256") : null)
                                .add("Expires", notAfter == null ? null : notAfter.toString("yyyy-MM-dd"))
                                .build(),
                        time(row, "last_seen"),
                        term.is(SearchTerm.Kind.HASH) ? SCORE_EXACT : score(term, commonName),
                        link("TLS_CERTIFICATE", "sha256", text(row, "sha256"))
                ));
            }

            return Result.of(hits, request, window, null);
        }

    }

    private static class Dhcp extends TrafficSource {

        @Override
        public String id() {
            return "dhcp";
        }

        @Override
        public String title() {
            return "DHCP";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return term.isAnyOf(SearchTerm.Kind.MAC, SearchTerm.Kind.MAC_PREFIX, SearchTerm.Kind.IP,
                    SearchTerm.Kind.HASH) || term.isRecordedText();
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchWindow window = window(request);

            SearchConditions conditions = new SearchConditions(term)
                    .mac("d.client_mac", "d.server_mac")
                    .address("d.requested_ip_address")
                    .custom("d.fingerprint = :s_lower", SearchTerm.Kind.HASH);
            if (term.isRecordedText() && !term.isAnyOf(SearchTerm.Kind.MAC_PREFIX, SearchTerm.Kind.HASH)) {
                conditions.contains("d.hostname", "d.fqdn", "d.vendor_class");
            }

            // A transaction seen by several taps is one hit.
            String sql = "SELECT x.* FROM (SELECT DISTINCT ON (d.transaction_id, d.client_mac) d.transaction_id, "
                    + "d.transaction_type, d.client_mac, d.server_mac, host(d.requested_ip_address) AS requested, "
                    + "d.hostname, d.fqdn, d.vendor_class, d.first_packet, d.latest_packet "
                    + "FROM dhcp_transactions d WHERE d.tap_uuid IN (<taps>) "
                    + "AND d.latest_packet >= :w_from AND d.latest_packet <= :w_to AND " + conditions.sql() + " "
                    + "ORDER BY d.transaction_id, d.client_mac, d.latest_packet DESC) x "
                    + "ORDER BY x.latest_packet DESC, x.transaction_id, x.client_mac LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request, window)) {
                DateTime first = time(row, "first_packet");
                String hostname = text(row, "hostname") == null ? text(row, "fqdn") : text(row, "hostname");

                hits.add(hit(
                        "DHCP_TRANSACTION",
                        text(row, "client_mac"),
                        "DHCP transaction" + (text(row, "transaction_type") == null
                                ? "" : " (" + text(row, "transaction_type") + ")"),
                        facts(term)
                                .add("Hostname", hostname)
                                .add("Requested", row.get("requested"))
                                .add("Server", row.get("server_mac"))
                                .add("Vendor Class", row.get("vendor_class"))
                                .build(),
                        time(row, "latest_packet"),
                        score(term, text(row, "client_mac"), text(row, "server_mac"), text(row, "requested"), hostname),
                        link("DHCP_TRANSACTION", "id", String.valueOf(row.get("transaction_id")),
                                "time", first == null ? null : first.toString())
                ));
            }

            return Result.of(hits, request, window, listOf("DHCP", term, "requested_ip", "client_mac"));
        }

    }

    private static class Arp extends TrafficSource {

        @Override
        public String id() {
            return "arp";
        }

        @Override
        public String title() {
            return "ARP";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return addressed(term);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchWindow window = window(request);
            SearchConditions conditions = new SearchConditions(term)
                    .address("a.arp_sender_address")
                    .mac("a.arp_sender_mac");

            String sql = "SELECT a.arp_sender_mac AS mac, host(a.arp_sender_address) AS address, "
                    + "max(a.timestamp) AS last_seen FROM arp_packets a WHERE a.tap_uuid IN (<taps>) "
                    + "AND a.timestamp >= :w_from AND a.timestamp <= :w_to AND " + conditions.sql() + " "
                    + "GROUP BY a.arp_sender_mac, a.arp_sender_address "
                    + "ORDER BY last_seen DESC, a.arp_sender_mac, a.arp_sender_address LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request, window)) {
                hits.add(hit(
                        "ARP_BINDING",
                        text(row, "address") + " is at " + text(row, "mac"),
                        "Address announced over ARP",
                        facts(term).build(),
                        time(row, "last_seen"),
                        SCORE_EXACT,
                        term.is(SearchTerm.Kind.IP) || term.is(SearchTerm.Kind.CIDR)
                                ? link("ARP_PACKETS", "arp_sender_mac", text(row, "mac"))
                                : link("ARP_PACKETS", "arp_sender_address", text(row, "address"))
                ));
            }

            return Result.of(hits, request, window,
                    listOf("ARP_PACKETS", term, "arp_sender_address", "arp_sender_mac"));
        }

    }

    private static class Ssh extends TrafficSource {

        @Override
        public String id() {
            return "ssh";
        }

        @Override
        public String title() {
            return "SSH";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return addressed(term) || term.isRecordedText();
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchWindow window = window(request);

            SearchConditions conditions = new SearchConditions(term)
                    .address("tcp.source_address", "tcp.destination_address")
                    .mac("tcp.source_mac", "tcp.destination_mac");
            if (term.isRecordedText() && !term.is(SearchTerm.Kind.MAC_PREFIX)) {
                conditions.contains("x.client_version_software", "x.server_version_software");
            }

            String sql = "SELECT host(tcp.source_address) AS client, host(tcp.destination_address) AS server, "
                    + "tcp.destination_port, max(x.client_version_software) AS client_software, "
                    + "max(x.server_version_software) AS server_software, "
                    + "max(x.most_recent_segment_time) AS last_seen FROM ssh_sessions x "
                    + TCP_OF + "tcp_session_key AND tcp.l4_type = 'TCP' "
                    + "WHERE x.tap_uuid IN (<taps>) AND x.most_recent_segment_time >= :w_from "
                    + "AND x.most_recent_segment_time <= :w_to AND " + conditions.sql() + " "
                    + "GROUP BY tcp.source_address, tcp.destination_address, tcp.destination_port "
                    + "ORDER BY last_seen DESC, tcp.source_address, tcp.destination_address, tcp.destination_port "
                    + "LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request, window)) {
                hits.add(hit(
                        "SSH_CONNECTION",
                        text(row, "client") + " to " + endpoint(text(row, "server"), row.get("destination_port")),
                        "SSH sessions",
                        facts(term)
                                .add("Client", row.get("client_software"))
                                .add("Server", row.get("server_software"))
                                .build(),
                        time(row, "last_seen"),
                        term.isIdentifier() ? SCORE_EXACT : SCORE_CONTAINS,
                        link("SSH_SESSIONS", "client_address", text(row, "client"),
                                "server_address", text(row, "server"))
                ));
            }

            return Result.of(hits, request, window, null);
        }

    }

    private static class Vpn extends TrafficSource {

        @Override
        public String id() {
            return "vpn";
        }

        @Override
        public String title() {
            return "VPN Tunnels";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return addressed(term) || term.isRecordedText();
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchWindow window = window(request);

            SearchConditions conditions = new SearchConditions(term)
                    .address("v.source_address", "v.destination_address")
                    .mac("v.source_mac", "v.destination_mac");
            if (term.isRecordedText() && !term.is(SearchTerm.Kind.MAC_PREFIX)) {
                conditions.contains("v.server_name", "v.provider", "v.vpn_type");
            }

            String sql = "SELECT host(v.source_address) AS client, host(v.destination_address) AS server, "
                    + "v.destination_port, v.vpn_type, max(v.provider) AS provider, max(v.server_name) AS server_name, "
                    + "max(v.most_recent_segment_time) AS last_seen FROM vpn_tunnels v "
                    + "WHERE v.tap_uuid IN (<taps>) AND v.most_recent_segment_time >= :w_from "
                    + "AND v.most_recent_segment_time <= :w_to AND " + conditions.sql() + " "
                    + "GROUP BY v.source_address, v.destination_address, v.destination_port, v.vpn_type "
                    + "ORDER BY last_seen DESC, v.source_address, v.destination_address, v.destination_port, "
                    + "v.vpn_type LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request, window)) {
                hits.add(hit(
                        "VPN_CONNECTION",
                        text(row, "client") + " to " + endpoint(text(row, "server"), row.get("destination_port")),
                        text(row, "vpn_type") + " tunnels",
                        facts(term)
                                .add("Provider", row.get("provider"))
                                .add("Server Name", row.get("server_name"))
                                .build(),
                        time(row, "last_seen"),
                        term.isIdentifier() ? SCORE_EXACT : SCORE_CONTAINS,
                        link("VPN_TUNNELS", "client_address", text(row, "client"),
                                "server_address", text(row, "server"))
                ));
            }

            return Result.of(hits, request, window, null);
        }

    }

    private static class Socks extends TrafficSource {

        @Override
        public String id() {
            return "socks";
        }

        @Override
        public String title() {
            return "SOCKS Tunnels";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return addressed(term) || term.isRecordedText();
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchWindow window = window(request);

            SearchConditions conditions = new SearchConditions(term)
                    .address("tcp.source_address", "tcp.destination_address", "x.tunneled_destination_address")
                    .mac("tcp.source_mac", "tcp.destination_mac");
            if (term.isRecordedText() && !term.is(SearchTerm.Kind.MAC_PREFIX)) {
                conditions.contains("x.tunneled_destination_host", "x.username");
            }

            String sql = "SELECT host(tcp.source_address) AS client, host(tcp.destination_address) AS server, "
                    + "tcp.destination_port, COALESCE(x.tunneled_destination_host, "
                    + "host(x.tunneled_destination_address)) AS tunneled, "
                    + "max(x.most_recent_segment_time) AS last_seen FROM socks_tunnels x "
                    + TCP_OF + "tcp_session_key AND tcp.l4_type = 'TCP' "
                    + "WHERE x.tap_uuid IN (<taps>) AND x.most_recent_segment_time >= :w_from "
                    + "AND x.most_recent_segment_time <= :w_to AND " + conditions.sql() + " "
                    + "GROUP BY tcp.source_address, tcp.destination_address, tcp.destination_port, 4 "
                    + "ORDER BY last_seen DESC, tcp.source_address, tcp.destination_address, tcp.destination_port, 4 "
                    + "LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request, window)) {
                hits.add(hit(
                        "SOCKS_CONNECTION",
                        text(row, "client") + " to " + endpoint(text(row, "server"), row.get("destination_port")),
                        "SOCKS tunnels",
                        facts(term).add("Tunneled Destination", row.get("tunneled")).build(),
                        time(row, "last_seen"),
                        term.isIdentifier() ? SCORE_EXACT : SCORE_CONTAINS,
                        link("SOCKS_TUNNELS", "client_address", text(row, "client"),
                                "server_address", text(row, "server"))
                ));
            }

            return Result.of(hits, request, window, null);
        }

    }

    private static class Ntp extends TrafficSource {

        private static final String SEEN = "COALESCE(n.timestamp_client_tap_receive, n.timestamp_server_tap_receive)";

        @Override
        public String id() {
            return "ntp";
        }

        @Override
        public String title() {
            return "NTP";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return addressed(term);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchWindow window = window(request);
            SearchConditions conditions = new SearchConditions(term)
                    .address("n.client_address", "n.server_address")
                    .mac("n.client_mac", "n.server_mac");

            String sql = "SELECT host(n.client_address) AS client, host(n.server_address) AS server, "
                    + "max(" + SEEN + ") AS last_seen FROM ntp_transactions n "
                    + "WHERE n.tap_uuid IN (<taps>) AND " + SEEN + " >= :w_from AND " + SEEN + " <= :w_to "
                    + "AND " + conditions.sql() + " "
                    + "GROUP BY n.client_address, n.server_address "
                    + "ORDER BY last_seen DESC, n.client_address, n.server_address LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request, window)) {
                hits.add(hit(
                        "NTP_CONNECTION",
                        text(row, "client") + " to " + text(row, "server"),
                        "NTP transactions",
                        facts(term).build(),
                        time(row, "last_seen"),
                        SCORE_EXACT,
                        link("NTP_TRANSACTIONS", "client_address", text(row, "client"),
                                "server_address", text(row, "server"))
                ));
            }

            return Result.of(hits, request, window, null);
        }

    }

    private static class Rtsp extends TrafficSource {

        @Override
        public String id() {
            return "rtsp";
        }

        @Override
        public String title() {
            return "RTSP Streams";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return addressed(term) || term.isRecordedText();
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchWindow window = window(request);

            SearchConditions conditions = new SearchConditions(term)
                    .address("tcp.source_address", "tcp.destination_address")
                    .mac("tcp.source_mac", "tcp.destination_mac");
            if (term.isRecordedText() && !term.is(SearchTerm.Kind.MAC_PREFIX)) {
                conditions.contains("x.request_uri", "x.client_agent", "x.server_info");
            }

            String sql = "SELECT y.* FROM (SELECT DISTINCT ON (x.request_uri, tcp.source_address, "
                    + "tcp.destination_address) x.setup_tcp_session_key, x.request_uri, x.client_agent, "
                    + "x.server_info, host(tcp.source_address) AS client, host(tcp.destination_address) AS server, "
                    + "x.setup_most_recent_segment_time AS last_seen FROM rtsp_streams x "
                    + TCP_OF + "setup_tcp_session_key AND tcp.l4_type = 'TCP' "
                    + "WHERE x.tap_uuid IN (<taps>) AND x.setup_most_recent_segment_time >= :w_from "
                    + "AND x.setup_most_recent_segment_time <= :w_to AND " + conditions.sql() + " "
                    + "ORDER BY x.request_uri, tcp.source_address, tcp.destination_address, "
                    + "x.setup_most_recent_segment_time DESC) y "
                    + "ORDER BY y.last_seen DESC, y.setup_tcp_session_key LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request, window)) {
                hits.add(hit(
                        "RTSP_STREAM",
                        text(row, "request_uri") == null ? text(row, "server") : text(row, "request_uri"),
                        "RTSP stream",
                        facts(term)
                                .add("Client", row.get("client"))
                                .add("Server", row.get("server"))
                                .add("Server Software", row.get("server_info"))
                                .build(),
                        time(row, "last_seen"),
                        term.isIdentifier() ? SCORE_EXACT : SCORE_CONTAINS,
                        link("RTSP_STREAM", "key", text(row, "setup_tcp_session_key"))
                ));
            }

            return Result.of(hits, request, window, null);
        }

    }

    // WebRTC conversations and STUN connections are both kept by negotiation, with the sessions that carry them.
    private abstract static class Negotiations extends TrafficSource {

        protected abstract String table();

        protected abstract String type();

        protected abstract String description();

        @Override
        protected boolean accepts(SearchTerm term) {
            return addressed(term);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchWindow window = window(request);
            SearchConditions conditions = new SearchConditions(term)
                    .address("tcp.source_address", "tcp.destination_address")
                    .mac("tcp.source_mac", "tcp.destination_mac");

            String sql = "SELECT y.* FROM (SELECT DISTINCT ON (x.negotiation_key_sha256) x.negotiation_key_sha256, "
                    + "host(tcp.source_address) AS source, host(tcp.destination_address) AS destination, "
                    + "x.last_activity AS last_seen FROM " + table() + " x "
                    + TCP_OF + "l4_session_key "
                    + "WHERE x.tap_uuid IN (<taps>) AND x.last_activity >= :w_from AND x.last_activity <= :w_to "
                    + "AND x.negotiation_key_sha256 IS NOT NULL AND " + conditions.sql() + " "
                    + "ORDER BY x.negotiation_key_sha256, x.last_activity DESC) y "
                    + "ORDER BY y.last_seen DESC, y.negotiation_key_sha256 LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request, window)) {
                hits.add(hit(
                        type(),
                        text(row, "source") + " to " + text(row, "destination"),
                        description(),
                        facts(term).build(),
                        time(row, "last_seen"),
                        SCORE_EXACT,
                        link(type(), "key", text(row, "negotiation_key_sha256"))
                ));
            }

            return Result.of(hits, request, window, null);
        }

    }

    private static class WebRtc extends Negotiations {

        @Override
        public String id() {
            return "webrtc";
        }

        @Override
        public String title() {
            return "WebRTC";
        }

        @Override
        protected String table() {
            return "webrtc_conversations";
        }

        @Override
        protected String type() {
            return "WEBRTC_SESSION";
        }

        @Override
        protected String description() {
            return "WebRTC session";
        }

    }

    private static class Stun extends Negotiations {

        @Override
        public String id() {
            return "stun";
        }

        @Override
        public String title() {
            return "STUN Connections";
        }

        @Override
        protected String table() {
            return "nat_stun_negotiation_flows";
        }

        @Override
        protected String type() {
            return "STUN_CONNECTION";
        }

        @Override
        protected String description() {
            return "STUN connection";
        }

    }

    private static class PortalIntegrity extends TrafficSource {

        @Override
        public String id() {
            return "portal_integrity";
        }

        @Override
        public String title() {
            return "Portal Integrity";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return term.isAnyOf(SearchTerm.Kind.MAC, SearchTerm.Kind.IP) || term.isRecordedText();
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchWindow window = window(request);

            SearchConditions conditions = new SearchConditions(term)
                    .mac("r.probe_mac")
                    .custom("EXISTS (SELECT 1 FROM portal_integrity_hops h WHERE h.report_uuid = r.uuid "
                            + "AND h.resolved_address = :s_ip::inet)", SearchTerm.Kind.IP)
                    .custom("EXISTS (SELECT 1 FROM portal_integrity_dhcp_leases l WHERE l.report_uuid = r.uuid "
                            + "AND (l.assigned_address = :s_ip::inet OR l.gateway_address = :s_ip::inet "
                            + "OR l.dhcp_server_address = :s_ip::inet))", SearchTerm.Kind.IP);
            if (term.isRecordedText()) {
                conditions.contains("r.control_url", "r.probe_name")
                        .custom("EXISTS (SELECT 1 FROM portal_integrity_hops h WHERE h.report_uuid = r.uuid "
                                + "AND h.url ILIKE :s_contains)");
            }

            // Probes report every few minutes. The latest report of a probe stands for all of them.
            String sql = "SELECT y.* FROM (SELECT DISTINCT ON (r.tap_uuid, r.probe_mac) r.uuid, r.control_url, "
                    + "r.probe_mac, r.probe_name, r.error, r.probed_at FROM portal_integrity_reports r "
                    + "WHERE r.tap_uuid IN (<taps>) AND r.probed_at >= :w_from AND r.probed_at <= :w_to "
                    + "AND " + conditions.sql() + " ORDER BY r.tap_uuid, r.probe_mac, r.probed_at DESC) y "
                    + "ORDER BY y.probed_at DESC, y.uuid LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request, window)) {
                hits.add(hit(
                        "PORTAL_INTEGRITY_REPORT",
                        text(row, "probe_name") == null ? text(row, "probe_mac") : text(row, "probe_name"),
                        "Latest portal integrity report of this probe",
                        facts(term)
                                .add("Probe MAC", row.get("probe_mac"))
                                .add("Control URL", row.get("control_url"))
                                .add("Error", row.get("error"))
                                .build(),
                        time(row, "probed_at"),
                        score(term, text(row, "probe_mac"), text(row, "probe_name")),
                        link("PORTAL_INTEGRITY_REPORT", "uuid", text(row, "uuid"))
                ));
            }

            return Result.of(hits, request, window, null);
        }

    }

}
