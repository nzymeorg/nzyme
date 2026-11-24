package app.nzyme.core.search.sources;

import app.nzyme.core.rest.responses.search.SearchHitResponse;
import app.nzyme.core.search.SearchConditions;
import app.nzyme.core.search.SearchScope;
import app.nzyme.core.search.SearchSource;
import app.nzyme.core.search.SearchTerm;
import app.nzyme.core.search.SearchWindow;
import app.nzyme.core.search.SqlSearchSource;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jdbi.v3.core.Handle;

import java.util.List;
import java.util.Map;

public class Dot11Search {

    public static List<SearchSource> sources() {
        return List.of(new Monitoring(), new AccessPoints(), new Networks(), new Clients(), new ProbeRequests());
    }

    private static boolean recorded(SearchScope scope) {
        return scope.hasSubsystem(Subsystem.DOT11) && scope.hasTaps();
    }

    private static class Monitoring extends SqlSearchSource {

        @Override
        public String id() {
            return "dot11_monitoring";
        }

        @Override
        public String title() {
            return "WiFi Monitoring";
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return scope.hasSubsystem(Subsystem.DOT11) && scope.hasPermission("dot11_monitoring_manage");
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            List<SearchHitResponse> hits = Lists.newArrayList();

            SearchConditions networks = new SearchConditions(term)
                    .uuid("n.uuid")
                    .contains("n.ssid")
                    .custom("EXISTS (SELECT 1 FROM dot11_monitored_networks_bssids b "
                            + "WHERE b.monitored_network_id = n.id AND b.bssid = :s_mac)", SearchTerm.Kind.MAC)
                    .custom("EXISTS (SELECT 1 FROM dot11_monitored_networks_bssids b "
                            + "WHERE b.monitored_network_id = n.id AND b.bssid LIKE :s_mac_prefix)",
                            SearchTerm.Kind.MAC_PREFIX)
                    .custom("EXISTS (SELECT 1 FROM dot11_monitored_networks_bssids b "
                            + "JOIN dot11_monitored_networks_fingerprints f ON f.monitored_network_bssid_id = b.id "
                            + "WHERE b.monitored_network_id = n.id AND f.fingerprint = :s_lower)",
                            SearchTerm.Kind.HASH)
                    .custom("EXISTS (SELECT 1 FROM dot11_monitored_networks_restricted_substrings r "
                            + "WHERE r.monitored_network_id = n.id AND r.substring ILIKE :s_contains)")
                    .custom("EXISTS (SELECT 1 FROM dot11_monitored_networks_allowed_similar_ssids a "
                            + "WHERE a.monitored_network_id = n.id AND a.ssid ILIKE :s_contains)");

            for (Map<String, Object> row : rows(handle, "SELECT n.uuid, n.ssid, n.enabled, n.updated_at "
                    + "FROM dot11_monitored_networks n "
                    + "WHERE n.organization_id = :organization_id AND n.tenant_id = :tenant_id "
                    + "AND " + networks.sql() + " "
                    + "ORDER BY " + bestFirst("n.ssid") + ", n.ssid ASC, n.uuid ASC LIMIT :cap", networks, request)) {
                hits.add(hit(
                        "DOT11_MONITORED_NETWORK",
                        text(row, "ssid"),
                        "Monitored network",
                        facts(term)
                                .add("Monitoring", bool(row, "enabled") ? "Enabled" : "Disabled")
                                .add("BSSID", term.is(SearchTerm.Kind.MAC) ? term.mac() : null)
                                .build(),
                        time(row, "updated_at"),
                        term.is(SearchTerm.Kind.MAC) ? SCORE_EXACT : score(term, text(row, "ssid"), text(row, "uuid")),
                        link("DOT11_MONITORED_NETWORK", "uuid", text(row, "uuid"))
                ));
            }

            if (term.isAnyOf(SearchTerm.Kind.MAC, SearchTerm.Kind.MAC_PREFIX)) {
                SearchConditions clients = new SearchConditions(term).mac("kc.mac");

                for (Map<String, Object> row : rows(handle, "SELECT kc.mac, kc.is_approved, kc.last_seen, "
                        + "n.uuid AS network_uuid, n.ssid FROM dot11_known_clients kc "
                        + "JOIN dot11_monitored_networks n ON n.id = kc.monitored_network_id "
                        + "WHERE n.organization_id = :organization_id AND n.tenant_id = :tenant_id "
                        + "AND " + clients.sql() + " "
                        + "ORDER BY kc.last_seen DESC, kc.uuid ASC LIMIT :cap", clients, request)) {
                    hits.add(hit(
                            "DOT11_KNOWN_CLIENT",
                            text(row, "mac"),
                            "Known client of monitored network " + text(row, "ssid"),
                            facts(term).add("Approved", bool(row, "is_approved") ? "Yes" : "No").build(),
                            time(row, "last_seen"),
                            score(term, text(row, "mac")),
                            link("DOT11_MONITORED_CLIENTS", "uuid", text(row, "network_uuid"))
                    ));
                }
            }

            if (!term.isIdentifier()) {
                SearchConditions ssid = new SearchConditions(term).contains("k.ssid");

                for (Map<String, Object> row : rows(handle, "SELECT k.ssid, k.is_approved, k.last_seen "
                        + "FROM dot11_known_networks k "
                        + "WHERE k.organization_id = :organization_id AND k.tenant_id = :tenant_id "
                        + "AND " + ssid.sql() + " "
                        + "ORDER BY " + bestFirst("k.ssid") + ", k.last_seen DESC, k.uuid ASC LIMIT :cap", ssid, request)) {
                    hits.add(hit(
                            "DOT11_KNOWN_NETWORK",
                            text(row, "ssid"),
                            "Known network",
                            facts(term).add("Approved", bool(row, "is_approved") ? "Yes" : "No").build(),
                            time(row, "last_seen"),
                            score(term, text(row, "ssid")),
                            link("DOT11_KNOWN_NETWORKS")
                    ));
                }

                SearchConditions probes = new SearchConditions(term).contains("p.ssid", "p.notes");

                for (Map<String, Object> row : rows(handle, "SELECT p.ssid, p.notes, p.updated_at "
                        + "FROM dot11_monitored_probereqs p "
                        + "WHERE p.organization_id = :organization_id AND p.tenant_id = :tenant_id "
                        + "AND " + probes.sql() + " "
                        + "ORDER BY " + bestFirst("p.ssid") + ", p.ssid ASC, p.uuid ASC LIMIT :cap", probes, request)) {
                    hits.add(hit(
                            "DOT11_MONITORED_PROBE_REQUEST",
                            text(row, "ssid"),
                            "Monitored probe request",
                            facts(term).add("Notes", row.get("notes")).build(),
                            time(row, "updated_at"),
                            score(term, text(row, "ssid")),
                            link("DOT11_MONITORED_PROBE_REQUESTS")
                    ));
                }
            }

            SearchConditions bandits = new SearchConditions(term)
                    .uuid("b.uuid")
                    .contains("b.name", "b.description")
                    .custom("EXISTS (SELECT 1 FROM dot11_bandit_fingerprints f WHERE f.bandit_id = b.id "
                            + "AND f.fingerprint = :s_lower)", SearchTerm.Kind.HASH);

            for (Map<String, Object> row : rows(handle, "SELECT b.uuid, b.name, b.description, b.updated_at "
                    + "FROM dot11_bandits b "
                    + "WHERE b.organization_id = :organization_id AND b.tenant_id = :tenant_id "
                    + "AND " + bandits.sql() + " "
                    + "ORDER BY " + bestFirst("b.name") + ", b.name ASC, b.uuid ASC LIMIT :cap", bandits, request)) {
                hits.add(hit(
                        "DOT11_BANDIT",
                        text(row, "name"),
                        text(row, "description"),
                        facts(term).build(),
                        time(row, "updated_at"),
                        term.is(SearchTerm.Kind.HASH) ? SCORE_EXACT : score(term, text(row, "name"), text(row, "uuid")),
                        link("DOT11_BANDIT", "uuid", text(row, "uuid"))
                ));
            }

            return merged(hits, request);
        }

    }

    private static class AccessPoints extends SqlSearchSource {

        @Override
        public String id() {
            return "dot11_bssids";
        }

        @Override
        public String title() {
            return "Access Points";
        }

        @Override
        public Tier tier() {
            return Tier.RECORDED;
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return recorded(scope)
                    && term.isAnyOf(SearchTerm.Kind.MAC, SearchTerm.Kind.MAC_PREFIX, SearchTerm.Kind.HASH);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();

            // The index on (bssid, created_at) answers a full address over all data. Anything else is a scan.
            SearchWindow window = request.windowOr(term.is(SearchTerm.Kind.MAC)
                    ? SearchWindow.all() : SearchWindow.lastHours(SearchWindow.DEFAULT_SCAN_HOURS));

            SearchConditions conditions = new SearchConditions(term)
                    .mac("b.bssid")
                    .custom("b.fingerprints @> ARRAY[:s_lower]::text[]", SearchTerm.Kind.HASH);

            String sql = "SELECT b.bssid, max(b.created_at) AS last_seen, max(b.id) AS latest_id, max(b.oui) AS oui "
                    + "FROM dot11_bssids b "
                    + "WHERE b.tap_uuid IN (<taps>) AND b.created_at >= :w_from AND b.created_at <= :w_to "
                    + "AND " + conditions.sql() + " "
                    + "GROUP BY b.bssid ORDER BY last_seen DESC, b.bssid ASC LIMIT :limit OFFSET :offset";

            // Over all data, only the newest row of the address is read. An access point has one per minute.
            if (term.is(SearchTerm.Kind.MAC) && request.window() == null) {
                sql = "SELECT b.bssid, b.created_at AS last_seen, b.id AS latest_id, b.oui FROM dot11_bssids b "
                        + "WHERE b.bssid = :s_mac AND b.tap_uuid IN (<taps>) "
                        + "ORDER BY b.created_at DESC LIMIT 1 OFFSET :offset";
            }

            List<Map<String, Object>> rows = rows(handle, sql, conditions, request, window);

            Map<Long, String> ssids = Maps.newHashMap();
            if (!rows.isEmpty()) {
                limitTime(handle, request);

                handle.createQuery("SELECT x.bssid_id, string_agg(x.ssid, ', ' ORDER BY x.ssid) AS ssids FROM ("
                                + "SELECT d.bssid_id, d.ssid, "
                                + "row_number() OVER (PARTITION BY d.bssid_id ORDER BY d.ssid) AS position FROM ("
                                + "SELECT DISTINCT s.bssid_id, s.ssid FROM dot11_ssids s "
                                + "WHERE s.bssid_id IN (<ids>)) d) x "
                                + "WHERE x.position <= 5 GROUP BY x.bssid_id")
                        .bindList("ids", rows.stream().map(row -> number(row, "latest_id")).toList())
                        .mapToMap()
                        .forEach(row -> ssids.put(number(row, "bssid_id"), text(row, "ssids")));
            }

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows) {
                String bssid = text(row, "bssid");
                String advertised = ssids.get(number(row, "latest_id"));

                hits.add(hit(
                        "DOT11_BSSID",
                        bssid,
                        advertised == null ? "Access point" : "Access point of " + advertised,
                        facts(term).add("Vendor", row.get("oui")).build(),
                        time(row, "last_seen"),
                        term.is(SearchTerm.Kind.HASH) ? SCORE_EXACT : score(term, bssid),
                        link("DOT11_BSSID", "bssid", bssid)
                ));
            }

            return Result.of(hits, request, window, null);
        }

    }

    private static class Networks extends SqlSearchSource {

        @Override
        public String id() {
            return "dot11_ssids";
        }

        @Override
        public String title() {
            return "WiFi Networks";
        }

        @Override
        public Tier tier() {
            return Tier.RECORDED;
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return recorded(scope) && term.isRecordedText();
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchWindow window = request.windowOr(SearchWindow.lastHours(SearchWindow.DEFAULT_SCAN_HOURS));
            SearchConditions conditions = new SearchConditions(term).contains("s.ssid");

            String sql = "SELECT s.ssid, count(DISTINCT s.bssid) AS bssids, max(s.created_at) AS last_seen "
                    + "FROM dot11_ssids s "
                    + "WHERE s.tap_uuid IN (<taps>) AND s.created_at >= :w_from AND s.created_at <= :w_to "
                    + "AND " + conditions.sql() + " "
                    + "GROUP BY s.ssid ORDER BY (lower(s.ssid) = :s_lower) DESC, last_seen DESC, s.ssid ASC "
                    + "LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request, window)) {
                String ssid = text(row, "ssid");

                hits.add(hit(
                        "DOT11_SSID",
                        ssid,
                        "Advertised by " + count(number(row, "bssids"), "access point", "access points"),
                        facts(term).build(),
                        time(row, "last_seen"),
                        score(term, ssid),
                        link("DOT11_BSSIDS_OF_SSID", "ssid", (String) row.get("ssid"))
                ));
            }

            return Result.of(hits, request, window, null);
        }

    }

    private static class Clients extends SqlSearchSource {

        @Override
        public String id() {
            return "dot11_clients";
        }

        @Override
        public String title() {
            return "WiFi Clients";
        }

        @Override
        public Tier tier() {
            return Tier.RECORDED;
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return recorded(scope) && term.isAnyOf(SearchTerm.Kind.MAC, SearchTerm.Kind.MAC_PREFIX);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchWindow window = request.windowOr(term.is(SearchTerm.Kind.MAC)
                    ? SearchWindow.all() : SearchWindow.lastHours(SearchWindow.DEFAULT_SCAN_HOURS));

            SearchConditions disconnected = new SearchConditions(term).mac("c.client_mac");
            SearchConditions connected = new SearchConditions(term).mac("bc.client_mac");

            String newest = "SELECT :s_mac::text AS client_mac, max(x.seen) AS last_seen, max(x.bssid) AS bssid FROM ("
                    + "(SELECT l.seen, NULL::text AS bssid FROM taps t CROSS JOIN LATERAL ("
                    + "SELECT c.created_at AS seen FROM dot11_clients c WHERE c.tap_uuid = t.uuid "
                    + "AND c.client_mac = :s_mac ORDER BY c.created_at DESC LIMIT 1) l WHERE t.uuid IN (<taps>)) "
                    + "UNION ALL "
                    + "(SELECT b.created_at AS seen, b.bssid::text AS bssid FROM dot11_bssid_clients bc "
                    + "JOIN dot11_bssids b ON b.id = bc.bssid_id WHERE bc.client_mac = :s_mac "
                    + "AND b.tap_uuid IN (<taps>) ORDER BY bc.bssid_id DESC LIMIT 1)) x "
                    + "HAVING max(x.seen) IS NOT NULL OFFSET :offset";

            String sql = "SELECT x.client_mac, max(x.seen) AS last_seen, max(x.bssid) AS bssid FROM ("
                    + "SELECT c.client_mac, c.created_at AS seen, NULL::text AS bssid FROM dot11_clients c "
                    + "WHERE c.tap_uuid IN (<taps>) AND c.created_at >= :w_from AND c.created_at <= :w_to "
                    + "AND " + disconnected.sql() + " "
                    + "UNION ALL "
                    + "SELECT bc.client_mac, b.created_at AS seen, b.bssid::text AS bssid "
                    + "FROM dot11_bssid_clients bc JOIN dot11_bssids b ON b.id = bc.bssid_id "
                    + "WHERE b.tap_uuid IN (<taps>) AND b.created_at >= :w_from AND b.created_at <= :w_to "
                    + "AND " + connected.sql() + ") x "
                    + "GROUP BY x.client_mac ORDER BY last_seen DESC, x.client_mac ASC LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            // Over all data, only the newest sighting of the address is read, per tap and as a connected client.
            boolean overAllData = term.is(SearchTerm.Kind.MAC) && request.window() == null;

            for (Map<String, Object> row : rows(handle, overAllData ? newest : sql, disconnected, request, window)) {
                String mac = text(row, "client_mac");
                String bssid = text(row, "bssid");

                hits.add(hit(
                        "DOT11_CLIENT",
                        mac,
                        bssid == null ? "WiFi client, not seen connected" : "WiFi client",
                        facts(term).add("Connected to", bssid).build(),
                        time(row, "last_seen"),
                        score(term, mac),
                        link("DOT11_CLIENT", "mac", mac)
                ));
            }

            return Result.of(hits, request, window, null);
        }

    }

    private static class ProbeRequests extends SqlSearchSource {

        @Override
        public String id() {
            return "dot11_probe_requests";
        }

        @Override
        public String title() {
            return "Probe Requests";
        }

        @Override
        public Tier tier() {
            return Tier.RECORDED;
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return recorded(scope) && term.isRecordedText();
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchWindow window = request.windowOr(SearchWindow.lastHours(SearchWindow.DEFAULT_SCAN_HOURS));
            SearchConditions conditions = new SearchConditions(term).contains("pr.ssid");

            String sql = "SELECT pr.ssid, count(DISTINCT c.client_mac) AS clients, max(c.created_at) AS last_seen "
                    + "FROM dot11_client_probereq_ssids pr JOIN dot11_clients c ON c.id = pr.client_id "
                    + "WHERE c.tap_uuid IN (<taps>) AND c.created_at >= :w_from AND c.created_at <= :w_to "
                    + "AND " + conditions.sql() + " "
                    + "GROUP BY pr.ssid ORDER BY (lower(pr.ssid) = :s_lower) DESC, last_seen DESC, pr.ssid ASC "
                    + "LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request, window)) {
                String ssid = text(row, "ssid");

                hits.add(hit(
                        "DOT11_PROBE_REQUEST",
                        ssid,
                        "Requested by " + count(number(row, "clients"), "client", "clients"),
                        facts(term).build(),
                        time(row, "last_seen"),
                        score(term, ssid),
                        link("DOT11_CLIENTS_PROBING", "ssid", (String) row.get("ssid"))
                ));
            }

            return Result.of(hits, request, window, null);
        }

    }

}
