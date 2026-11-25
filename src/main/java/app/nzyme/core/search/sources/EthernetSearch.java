package app.nzyme.core.search.sources;

import app.nzyme.core.ethernet.spoofing.DhcpSpoofingDetector;
import app.nzyme.core.ethernet.recon.ReconScanType;
import app.nzyme.core.rest.responses.search.SearchHitResponse;
import app.nzyme.core.search.SearchConditions;
import app.nzyme.core.search.SearchScope;
import app.nzyme.core.search.SearchSource;
import app.nzyme.core.search.SearchTerm;
import app.nzyme.core.search.SqlSearchSource;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Lists;
import org.jdbi.v3.core.Handle;

import java.util.List;
import java.util.Map;

/*
 * What the node keeps per tenant about the Ethernet side: assets, findings of the detectors and their
 * configuration. The pages of these read them by organization and tenant, and so does the search.
 */
public class EthernetSearch {

    private static final String TENANT = "organization_id = :organization_id AND tenant_id = :tenant_id";

    private static String tenant(String alias) {
        return alias + ".organization_id = :organization_id AND " + alias + ".tenant_id = :tenant_id";
    }

    public static List<SearchSource> sources() {
        return List.of(new Assets(), new AssetIdentity(), new Beacons(), new Spoofing(), new ReconScans(),
                new DnsTunnels(), new Icmp(), new Segmentation(), new AssetTags(), new Exclusions());
    }

    private abstract static class EthernetSource extends SqlSearchSource {

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return scope.hasSubsystem(Subsystem.ETHERNET) && accepts(term);
        }

        protected boolean accepts(SearchTerm term) {
            return true;
        }

    }

    private static boolean addressed(SearchTerm term) {
        return term.isAnyOf(SearchTerm.Kind.MAC, SearchTerm.Kind.MAC_PREFIX, SearchTerm.Kind.IP,
                SearchTerm.Kind.CIDR, SearchTerm.Kind.UUID);
    }

    private static class Assets extends EthernetSource {

        @Override
        public String id() {
            return "assets";
        }

        @Override
        public String title() {
            return "Assets";
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();

            SearchConditions conditions = new SearchConditions(term)
                    .mac("a.mac")
                    .uuid("a.uuid")
                    .custom("EXISTS (SELECT 1 FROM assets_ip_addresses i WHERE i.asset_id = a.id "
                            + "AND i.address = :s_ip::inet)", SearchTerm.Kind.IP)
                    .custom("EXISTS (SELECT 1 FROM assets_ip_addresses i WHERE i.asset_id = a.id "
                            + "AND i.address <<= :s_cidr::inet)", SearchTerm.Kind.CIDR)
                    .custom("a.dhcp_fingerprint_initial = :s_lower OR a.dhcp_fingerprint_renew = :s_lower "
                            + "OR a.dhcp_fingerprint_reboot = :s_lower OR a.dhcp_fingerprint_rebind = :s_lower",
                            SearchTerm.Kind.HASH);

            if (!term.isIdentifier()) {
                conditions
                        .custom("EXISTS (SELECT 1 FROM assets_hostnames h WHERE h.asset_id = a.id "
                                + "AND h.hostname ILIKE :s_contains)")
                        .custom("EXISTS (SELECT 1 FROM context_mac_addresses c WHERE c.mac_address = a.mac "
                                + "AND c.organization_id = a.organization_id AND c.tenant_id = a.tenant_id "
                                + "AND c.name ILIKE :s_contains)")
                        .custom("EXISTS (SELECT 1 FROM assets_tag_assignments ta JOIN assets_tags t "
                                + "ON t.id = ta.tag_id WHERE ta.asset_id = a.id AND lower(t.name) = :s_lower)");
            }

            String sql = "SELECT a.uuid, a.mac, a.last_seen, "
                    + "(SELECT c.name FROM context_mac_addresses c WHERE c.mac_address = a.mac "
                    + "AND c.organization_id = a.organization_id AND c.tenant_id = a.tenant_id LIMIT 1) AS name, "
                    + "(SELECT string_agg(x.hostname, ', ') FROM (SELECT h.hostname FROM assets_hostnames h "
                    + "WHERE h.asset_id = a.id GROUP BY h.hostname "
                    + "ORDER BY bool_or(h.hostname ILIKE :s_contains) DESC, max(h.last_seen) DESC LIMIT 3) x) "
                    + "AS hostnames, "
                    + "(SELECT string_agg(x.address, ', ') FROM (SELECT host(i.address) AS address "
                    + "FROM assets_ip_addresses i WHERE i.asset_id = a.id GROUP BY i.address "
                    + "ORDER BY (host(i.address) = :s_raw) DESC, max(i.last_seen) DESC LIMIT 3) x) AS addresses "
                    + "FROM assets a WHERE " + tenant("a") + " "
                    + "AND " + conditions.sql() + " "
                    + "ORDER BY a.last_seen DESC, a.mac ASC LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request)) {
                String mac = text(row, "mac");
                String name = text(row, "name");

                hits.add(hit(
                        "ASSET",
                        name == null ? mac : name,
                        "Asset",
                        facts(term)
                                .add("MAC Address", name == null ? null : mac)
                                .add("Hostname", row.get("hostnames"))
                                .add("IP Address", row.get("addresses"))
                                .build(),
                        time(row, "last_seen"),
                        term.isAnyOf(SearchTerm.Kind.IP, SearchTerm.Kind.HASH)
                                ? SCORE_EXACT : score(term, mac, name, text(row, "uuid")),
                        link("ETHERNET_ASSET", "uuid", text(row, "uuid"))
                ));
            }

            return Result.of(hits, request);
        }

    }

    private static class AssetIdentity extends EthernetSource {

        @Override
        public String id() {
            return "asset_identity";
        }

        @Override
        public String title() {
            return "Asset Identity";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return term.isAnyOf(SearchTerm.Kind.MAC, SearchTerm.Kind.MAC_PREFIX, SearchTerm.Kind.UUID);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            List<SearchHitResponse> hits = Lists.newArrayList();

            SearchConditions findings = new SearchConditions(term).mac("mac").uuid("uuid");

            for (Map<String, Object> row : rows(handle, "SELECT uuid, mac, stack_count, last_seen "
                    + "FROM assets_identity_duplicate_findings WHERE " + TENANT + " AND " + findings.sql() + " "
                    + "ORDER BY last_seen DESC, uuid ASC LIMIT :cap", findings, request)) {
                hits.add(hit(
                        "ASSET_DUPLICATE_MAC",
                        text(row, "mac"),
                        count(number(row, "stack_count"), "device", "devices") + " behind one MAC address",
                        facts(term).build(),
                        time(row, "last_seen"),
                        score(term, text(row, "mac"), text(row, "uuid")),
                        link("ASSET_DUPLICATE_MAC", "uuid", text(row, "uuid"))
                ));
            }

            for (Map<String, Object> row : rows(handle, "SELECT uuid, mac, last_seen "
                    + "FROM assets_identity_change_findings WHERE " + TENANT + " AND " + findings.sql() + " "
                    + "ORDER BY last_seen DESC, uuid ASC LIMIT :cap", findings, request)) {
                hits.add(hit(
                        "ASSET_IDENTITY_CHANGE",
                        text(row, "mac"),
                        "Asset identity changed",
                        facts(term).build(),
                        time(row, "last_seen"),
                        score(term, text(row, "mac"), text(row, "uuid")),
                        link("ASSET_IDENTITY_CHANGE", "uuid", text(row, "uuid"))
                ));
            }

            SearchConditions links = new SearchConditions(term).mac("predecessor_mac", "successor_mac").uuid("uuid");

            for (Map<String, Object> row : rows(handle, "SELECT uuid, predecessor_mac, successor_mac, status, "
                    + "detected_at FROM assets_links WHERE " + TENANT + " AND " + links.sql() + " "
                    + "ORDER BY detected_at DESC, uuid ASC LIMIT :cap", links, request)) {
                hits.add(hit(
                        "ASSET_LINK",
                        text(row, "predecessor_mac") + " to " + text(row, "successor_mac"),
                        "Asset moved to a new MAC address",
                        facts(term).add("Status", capitalize(text(row, "status"))).build(),
                        time(row, "detected_at"),
                        score(term, text(row, "predecessor_mac"), text(row, "successor_mac"), text(row, "uuid")),
                        link("ASSET_LINK", "uuid", text(row, "uuid"))
                ));
            }

            return merged(hits, request);
        }

    }

    private static class Beacons extends EthernetSource {

        @Override
        public String id() {
            return "beacons";
        }

        @Override
        public String title() {
            return "Beacons";
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();

            SearchConditions conditions = new SearchConditions(term)
                    .uuid("b.uuid")
                    .address("b.destination_address")
                    .custom("EXISTS (SELECT 1 FROM beacon_sources s WHERE s.beacon_id = b.id "
                            + "AND s.source_address = :s_ip::inet)", SearchTerm.Kind.IP)
                    .custom("EXISTS (SELECT 1 FROM beacon_sources s WHERE s.beacon_id = b.id "
                            + "AND s.source_address <<= :s_cidr::inet)", SearchTerm.Kind.CIDR)
                    .custom("EXISTS (SELECT 1 FROM beacon_sources s WHERE s.beacon_id = b.id "
                            + "AND s.source_mac = :s_mac)", SearchTerm.Kind.MAC)
                    .custom("EXISTS (SELECT 1 FROM beacon_sources s WHERE s.beacon_id = b.id "
                            + "AND s.source_mac LIKE :s_mac_prefix)", SearchTerm.Kind.MAC_PREFIX);

            if (!term.isIdentifier()) {
                conditions.contains("b.name", "b.destination_host", "b.description");
            }

            String sql = "SELECT b.uuid, b.type, b.name, b.destination_host, host(b.destination_address) AS address, "
                    + "b.destination_port, b.classification, b.source_count, b.last_seen "
                    + "FROM beacons b WHERE " + tenant("b") + " "
                    + "AND " + conditions.sql() + " "
                    + "ORDER BY b.last_seen DESC, b.name ASC, b.uuid ASC LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request)) {
                String host = text(row, "destination_host");
                String address = text(row, "address");
                String destination = host != null ? host : address;
                if (destination != null && row.get("destination_port") != null) {
                    destination += ":" + row.get("destination_port");
                }

                hits.add(hit(
                        "BEACON",
                        text(row, "name"),
                        text(row, "type") + " beacon" + (destination == null ? "" : " to " + destination),
                        facts(term)
                                .add("Address", host == null ? null : address)
                                .add("Classification", capitalize(text(row, "classification")))
                                .add("Devices", count(number(row, "source_count"), "device", "devices"))
                                .build(),
                        time(row, "last_seen"),
                        score(term, text(row, "name"), host, address, text(row, "uuid")),
                        link("BEACON", "uuid", text(row, "uuid"))
                ));
            }

            return Result.of(hits, request);
        }

    }

    private static class Spoofing extends EthernetSource {

        @Override
        public String id() {
            return "spoofing";
        }

        @Override
        public String title() {
            return "Spoofing";
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            List<SearchHitResponse> hits = Lists.newArrayList();

            if (addressed(term)) {
                SearchConditions arp = new SearchConditions(term)
                        .uuid("f.uuid")
                        .address("f.address")
                        .mac("f.spoofer_mac", "f.owner_mac")
                        .custom("EXISTS (SELECT 1 FROM spoofing_arp_victims v WHERE v.finding_id = f.id "
                                + "AND (v.mac = :s_mac))", SearchTerm.Kind.MAC)
                        .custom("EXISTS (SELECT 1 FROM spoofing_arp_victims v WHERE v.finding_id = f.id "
                                + "AND (v.address = :s_ip::inet))", SearchTerm.Kind.IP);

                for (Map<String, Object> row : rows(handle, "SELECT f.uuid, host(f.address) AS address, "
                        + "f.spoofer_mac, f.owner_mac, f.victim_count, f.last_seen FROM spoofing_arp_findings f "
                        + "WHERE " + tenant("f") + " AND " + arp.sql() + " "
                        + "ORDER BY f.last_seen DESC, f.uuid ASC LIMIT :cap", arp, request)) {
                    hits.add(hit(
                            "SPOOFING_ARP",
                            text(row, "address"),
                            (text(row, "address") != null && text(row, "address").contains(":") ? "NDP" : "ARP")
                                    + " poisoning of this address",
                            facts(term)
                                    .add("Spoofer", row.get("spoofer_mac"))
                                    .add("Owner", row.get("owner_mac"))
                                    .add("Victims", count(number(row, "victim_count"), "device", "devices"))
                                    .build(),
                            time(row, "last_seen"),
                            score(term, text(row, "address"), text(row, "spoofer_mac"), text(row, "owner_mac"),
                                    text(row, "uuid")),
                            link("SPOOFING_ARP", "uuid", text(row, "uuid"))
                    ));
                }

                SearchConditions dhcp = new SearchConditions(term)
                        .uuid("f.uuid")
                        .address("f.server_address", "f.server_identifier")
                        .mac("f.server_mac")
                        .custom("EXISTS (SELECT 1 FROM spoofing_dhcp_victims v WHERE v.finding_id = f.id "
                                + "AND v.mac = :s_mac)", SearchTerm.Kind.MAC)
                        .custom("EXISTS (SELECT 1 FROM spoofing_dhcp_victims v WHERE v.finding_id = f.id "
                                + "AND v.assigned_address = :s_ip::inet)", SearchTerm.Kind.IP);

                for (Map<String, Object> row : rows(handle, "SELECT f.uuid, f.type, f.protocol, f.server_mac, "
                        + "host(f.server_address) AS address, f.victim_count, f.last_seen "
                        + "FROM spoofing_dhcp_findings f "
                        + "WHERE " + tenant("f") + " AND " + dhcp.sql() + " "
                        + "ORDER BY f.last_seen DESC, f.uuid ASC LIMIT :cap", dhcp, request)) {
                    String server = text(row, "address") == null ? text(row, "server_mac") : text(row, "address");

                    hits.add(hit(
                            "SPOOFING_DHCP",
                            server,
                            dhcpFindingText(text(row, "type"), text(row, "protocol")),
                            facts(term)
                                    .add("Server MAC", row.get("server_mac"))
                                    .add("Victims", count(number(row, "victim_count"), "device", "devices"))
                                    .build(),
                            time(row, "last_seen"),
                            score(term, text(row, "address"), text(row, "server_mac"), text(row, "uuid")),
                            link("SPOOFING_DHCP", "uuid", text(row, "uuid"))
                    ));
                }
            }

            SearchConditions dns = new SearchConditions(term)
                    .uuid("f.uuid")
                    .address("f.server_address")
                    .mac("f.spoofer_mac", "f.owner_mac")
                    .custom("EXISTS (SELECT 1 FROM spoofing_dns_victims v WHERE v.finding_id = f.id "
                            + "AND v.mac = :s_mac)", SearchTerm.Kind.MAC)
                    .custom("EXISTS (SELECT 1 FROM spoofing_dns_victims v WHERE v.finding_id = f.id "
                            + "AND v.address = :s_ip::inet)", SearchTerm.Kind.IP);
            if (term.isRecordedText()) {
                dns.custom("f.names::text ILIKE :s_contains");
            }

            for (Map<String, Object> row : rows(handle, "SELECT f.uuid, host(f.server_address) AS address, "
                    + "f.spoofer_mac, f.name_count, f.victim_count, f.last_seen FROM spoofing_dns_findings f "
                    + "WHERE " + tenant("f") + " AND " + dns.sql() + " "
                    + "ORDER BY f.last_seen DESC, f.uuid ASC LIMIT :cap", dns, request)) {
                hits.add(hit(
                        "SPOOFING_DNS",
                        text(row, "address"),
                        "DNS spoofing of answers of this server",
                        facts(term)
                                .add("Spoofer", row.get("spoofer_mac"))
                                .add("Names", count(number(row, "name_count"), "name", "names"))
                                .add("Victims", count(number(row, "victim_count"), "device", "devices"))
                                .build(),
                        time(row, "last_seen"),
                        score(term, text(row, "address"), text(row, "spoofer_mac"), text(row, "uuid")),
                        link("SPOOFING_DNS", "uuid", text(row, "uuid"))
                ));
            }

            SearchConditions nameres = new SearchConditions(term)
                    .uuid("f.uuid")
                    .mac("f.responder_mac")
                    .custom("jsonb_exists(f.responder_addresses, :s_ip)", SearchTerm.Kind.IP)
                    .custom("EXISTS (SELECT 1 FROM spoofing_nameres_victims v WHERE v.finding_id = f.id "
                            + "AND v.mac = :s_mac)", SearchTerm.Kind.MAC)
                    .custom("EXISTS (SELECT 1 FROM spoofing_nameres_victims v WHERE v.finding_id = f.id "
                            + "AND v.address = :s_ip::inet)", SearchTerm.Kind.IP);
            if (term.isRecordedText()) {
                nameres.custom("f.names::text ILIKE :s_contains");
            }

            for (Map<String, Object> row : rows(handle, "SELECT f.uuid, f.responder_mac, f.name_count, "
                    + "f.victim_count, f.last_seen FROM spoofing_nameres_findings f "
                    + "WHERE " + tenant("f") + " AND " + nameres.sql() + " "
                    + "ORDER BY f.last_seen DESC, f.uuid ASC LIMIT :cap", nameres, request)) {
                hits.add(hit(
                        "SPOOFING_NAMERES",
                        text(row, "responder_mac"),
                        "Name resolution poisoning by this device",
                        facts(term)
                                .add("Names", count(number(row, "name_count"), "name", "names"))
                                .add("Victims", count(number(row, "victim_count"), "device", "devices"))
                                .build(),
                        time(row, "last_seen"),
                        score(term, text(row, "responder_mac"), text(row, "uuid")),
                        link("SPOOFING_NAMERES", "uuid", text(row, "uuid"))
                ));
            }

            return merged(hits, request);
        }

    }

    private static class ReconScans extends EthernetSource {

        @Override
        public String id() {
            return "recon_scans";
        }

        @Override
        public String title() {
            return "Recon";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return addressed(term);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();

            SearchConditions conditions = new SearchConditions(term)
                    .uuid("s.uuid")
                    .address("s.source_address", "s.target_address")
                    .mac("s.source_mac")
                    .custom("EXISTS (SELECT 1 FROM recon_scan_targets t WHERE t.scan_id = s.id "
                            + "AND t.target_address = :s_ip::inet)", SearchTerm.Kind.IP);

            String sql = "SELECT s.uuid, s.type, host(s.source_address) AS source, s.source_mac, "
                    + "host(s.target_address) AS target, s.host_count, s.port_count, s.last_seen "
                    + "FROM recon_scans s WHERE " + tenant("s") + " "
                    + "AND " + conditions.sql() + " "
                    + "ORDER BY s.last_seen DESC, s.uuid ASC LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request)) {
                String source = text(row, "source") == null ? text(row, "source_mac") : text(row, "source");

                hits.add(hit(
                        "RECON_SCAN",
                        source,
                        scanTitle(text(row, "type")) + " by this device",
                        facts(term)
                                .add("Source MAC", row.get("source_mac"))
                                .add("Target", row.get("target"))
                                .add("Hosts", count(number(row, "host_count"), "host", "hosts"))
                                .add("Ports", count(number(row, "port_count"), "port", "ports"))
                                .build(),
                        time(row, "last_seen"),
                        score(term, text(row, "source"), text(row, "source_mac"), text(row, "target"),
                                text(row, "uuid")),
                        link("RECON_SCAN", "uuid", text(row, "uuid"))
                ));
            }

            return Result.of(hits, request);
        }

    }

    private static class DnsTunnels extends EthernetSource {

        @Override
        public String id() {
            return "dns_tunnels";
        }

        @Override
        public String title() {
            return "DNS Tunnels";
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();

            SearchConditions conditions = new SearchConditions(term)
                    .uuid("f.uuid")
                    .address("f.client_address")
                    .mac("f.client_mac");
            if (term.isRecordedText()) {
                conditions.contains("f.domain");
            }

            String sql = "SELECT f.uuid, host(f.client_address) AS client, f.client_mac, f.domain, f.query_count, "
                    + "f.last_seen FROM dns_tunnel_findings f "
                    + "WHERE " + tenant("f") + " AND " + conditions.sql() + " "
                    + "ORDER BY f.last_seen DESC, f.uuid ASC LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request)) {
                hits.add(hit(
                        "DNS_TUNNEL",
                        text(row, "domain"),
                        "DNS tunnel",
                        facts(term)
                                .add("Client", row.get("client"))
                                .add("Client MAC", row.get("client_mac"))
                                .add("Queries", count(number(row, "query_count"), "query", "queries"))
                                .build(),
                        time(row, "last_seen"),
                        score(term, text(row, "domain"), text(row, "client"), text(row, "client_mac"),
                                text(row, "uuid")),
                        link("DNS_TUNNEL", "uuid", text(row, "uuid"))
                ));
            }

            return Result.of(hits, request);
        }

    }

    private static class Icmp extends EthernetSource {

        // The ICMP pages only list findings that at least one of the taps of the user saw.
        private static final String SEEN = "EXISTS (SELECT 1 FROM jsonb_array_elements_text(f.tap_uuids) AS tap "
                + "WHERE tap IN (<tap_ids>))";

        @Override
        public String id() {
            return "icmp";
        }

        @Override
        public String title() {
            return "ICMP";
        }

        @Override
        public boolean applies(SearchTerm term, SearchScope scope) {
            return super.applies(term, scope) && scope.hasTaps();
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return addressed(term);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            List<SearchHitResponse> hits = Lists.newArrayList();

            SearchConditions redirects = new SearchConditions(term)
                    .uuid("f.uuid")
                    .address("f.sender_address", "f.gateway_address", "f.target_address")
                    .mac("f.sender_mac", "f.target_mac");

            for (Map<String, Object> row : rows(handle, "SELECT f.uuid, host(f.sender_address) AS sender, "
                    + "host(f.gateway_address) AS gateway, host(f.target_address) AS target, f.last_seen "
                    + "FROM icmp_redirects f WHERE " + tenant("f") + " AND " + SEEN + " "
                    + "AND " + redirects.sql() + " "
                    + "ORDER BY f.last_seen DESC, f.uuid ASC LIMIT :cap", redirects, request)) {
                hits.add(hit(
                        "ICMP_REDIRECT",
                        text(row, "sender"),
                        "ICMP redirect sent by this device",
                        facts(term).add("New Gateway", row.get("gateway")).add("Target", row.get("target")).build(),
                        time(row, "last_seen"),
                        score(term, text(row, "sender"), text(row, "gateway"), text(row, "target"), text(row, "uuid")),
                        link("ICMP_REDIRECT", "uuid", text(row, "uuid"))
                ));
            }

            SearchConditions traceroutes = new SearchConditions(term)
                    .uuid("f.uuid")
                    .address("f.source_address", "f.destination_address")
                    .mac("f.source_mac");

            for (Map<String, Object> row : rows(handle, "SELECT f.uuid, host(f.source_address) AS source, "
                    + "host(f.destination_address) AS destination, f.last_seen "
                    + "FROM icmp_traceroutes f WHERE " + tenant("f") + " AND " + SEEN + " "
                    + "AND " + traceroutes.sql() + " "
                    + "ORDER BY f.last_seen DESC, f.uuid ASC LIMIT :cap", traceroutes, request)) {
                hits.add(hit(
                        "ICMP_TRACEROUTE",
                        text(row, "source"),
                        "Traceroute by this device",
                        facts(term).add("Destination", row.get("destination")).build(),
                        time(row, "last_seen"),
                        score(term, text(row, "source"), text(row, "destination"), text(row, "uuid")),
                        link("ICMP_TRACEROUTE", "uuid", text(row, "uuid"))
                ));
            }

            SearchConditions tunnels = new SearchConditions(term)
                    .uuid("f.uuid")
                    .address("f.source_address", "f.destination_address")
                    .mac("f.source_mac", "f.destination_mac");

            for (Map<String, Object> row : rows(handle, "SELECT f.uuid, host(f.source_address) AS source, "
                    + "host(f.destination_address) AS destination, f.last_seen "
                    + "FROM icmp_tunnels f WHERE " + tenant("f") + " AND " + SEEN + " "
                    + "AND " + tunnels.sql() + " ORDER BY f.last_seen DESC, f.uuid ASC LIMIT :cap", tunnels, request)) {
                hits.add(hit(
                        "ICMP_TUNNEL",
                        text(row, "source"),
                        "ICMP tunnel from this device",
                        facts(term).add("Destination", row.get("destination")).build(),
                        time(row, "last_seen"),
                        score(term, text(row, "source"), text(row, "destination"), text(row, "uuid")),
                        link("ICMP_TUNNEL", "uuid", text(row, "uuid"))
                ));
            }

            return merged(hits, request);
        }

    }

    private static class Segmentation extends EthernetSource {

        @Override
        public String id() {
            return "segmentation";
        }

        @Override
        public String title() {
            return "Segmentation";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return !term.isAnyOf(SearchTerm.Kind.MAC, SearchTerm.Kind.MAC_PREFIX);
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            List<SearchHitResponse> hits = Lists.newArrayList();

            SearchConditions segments = new SearchConditions(term)
                    .uuid("s.uuid")
                    .custom("EXISTS (SELECT 1 FROM segmentation_segment_members m WHERE m.segment_id = s.id "
                            + "AND m.network >>= :s_ip::inet)", SearchTerm.Kind.IP)
                    .custom("EXISTS (SELECT 1 FROM segmentation_segment_members m WHERE m.segment_id = s.id "
                            + "AND m.network && :s_cidr::inet)", SearchTerm.Kind.CIDR);
            if (!term.isIdentifier()) {
                segments.contains("s.name", "s.description");
            }

            for (Map<String, Object> row : rows(handle, "SELECT s.uuid, s.name, s.description, s.kind, s.updated_at, "
                    + "(SELECT string_agg(x.network, ', ') FROM (SELECT m.network::text AS network "
                    + "FROM segmentation_segment_members m WHERE m.segment_id = s.id AND m.network IS NOT NULL "
                    + "ORDER BY m.network LIMIT 3) x) AS networks "
                    + "FROM segmentation_segments s WHERE " + tenant("s") + " "
                    + "AND " + segments.sql() + " "
                    + "ORDER BY " + bestFirst("s.name") + ", s.name ASC, s.uuid ASC LIMIT :cap", segments, request)) {
                hits.add(hit(
                        "SEGMENT",
                        text(row, "name"),
                        text(row, "description") == null ? "Segment" : text(row, "description"),
                        facts(term).add("Networks", row.get("networks")).build(),
                        time(row, "updated_at"),
                        term.isAnyOf(SearchTerm.Kind.IP, SearchTerm.Kind.CIDR)
                                ? SCORE_EXACT : score(term, text(row, "name"), text(row, "uuid")),
                        link("SEGMENT", "uuid", text(row, "uuid"))
                ));
            }

            if (!term.isAnyOf(SearchTerm.Kind.IP, SearchTerm.Kind.CIDR)) {
                SearchConditions policies = new SearchConditions(term)
                        .uuid("p.uuid")
                        .contains("p.name", "p.description");

                for (Map<String, Object> row : rows(handle, "SELECT p.uuid, p.name, p.description, p.status, "
                        + "p.updated_at FROM segmentation_policies p "
                        + "WHERE " + tenant("p") + " AND " + policies.sql() + " "
                        + "ORDER BY " + bestFirst("p.name") + ", p.name ASC, p.uuid ASC "
                        + "LIMIT :cap", policies, request)) {
                    hits.add(hit(
                            "SEGMENTATION_POLICY",
                            text(row, "name"),
                            text(row, "description") == null ? "Segmentation policy" : text(row, "description"),
                            facts(term).add("Status", capitalize(text(row, "status"))).build(),
                            time(row, "updated_at"),
                            score(term, text(row, "name"), text(row, "uuid")),
                            link("SEGMENTATION_POLICY", "uuid", text(row, "uuid"))
                    ));
                }
            }

            if (term.isAnyOf(SearchTerm.Kind.IP, SearchTerm.Kind.CIDR, SearchTerm.Kind.UUID)) {
                SearchConditions findings = new SearchConditions(term).uuid("f.uuid").address("f.source_address");

                for (Map<String, Object> row : rows(handle, "SELECT f.uuid, host(f.source_address) AS source, "
                        + "f.source_key, f.target_count, f.last_seen, p.name AS policy_name "
                        + "FROM segmentation_findings f LEFT JOIN segmentation_policies p ON p.id = f.policy_id "
                        + "WHERE " + tenant("f") + " AND " + findings.sql() + " "
                        + "ORDER BY f.last_seen DESC, f.uuid ASC LIMIT :cap", findings, request)) {
                    hits.add(hit(
                            "SEGMENTATION_FINDING",
                            text(row, "source") == null ? text(row, "source_key") : text(row, "source"),
                            "Segmentation policy violated by this source",
                            facts(term)
                                    .add("Policy", row.get("policy_name"))
                                    .add("Targets", count(number(row, "target_count"), "target", "targets"))
                                    .build(),
                            time(row, "last_seen"),
                            score(term, text(row, "source"), text(row, "uuid")),
                            link("SEGMENTATION_FINDING", "uuid", text(row, "uuid"))
                    ));
                }
            }

            return merged(hits, request);
        }

    }

    private static class AssetTags extends EthernetSource {

        @Override
        public String id() {
            return "asset_tags";
        }

        @Override
        public String title() {
            return "Asset Tags";
        }

        @Override
        protected boolean accepts(SearchTerm term) {
            return !term.isIdentifier();
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            SearchConditions conditions = new SearchConditions(term).contains("t.name", "t.description");

            String sql = "SELECT t.uuid, t.name, t.description, t.updated_at, "
                    + "(SELECT count(*) FROM assets_tag_assignments ta WHERE ta.tag_id = t.id) AS assets "
                    + "FROM assets_tags t WHERE " + tenant("t") + " "
                    + "AND " + conditions.sql() + " "
                    + "ORDER BY " + bestFirst("t.name") + ", t.name ASC, t.uuid ASC LIMIT :limit OFFSET :offset";

            List<SearchHitResponse> hits = Lists.newArrayList();
            for (Map<String, Object> row : rows(handle, sql, conditions, request)) {
                hits.add(hit(
                        "ASSET_TAG",
                        text(row, "name"),
                        text(row, "description") == null ? "Asset tag" : text(row, "description"),
                        facts(term).add("Assets", count(number(row, "assets"), "asset", "assets")).build(),
                        time(row, "updated_at"),
                        score(term, text(row, "name")),
                        link("ETHERNET_ASSETS_OF_TAG", "tag", text(row, "uuid"))
                ));
            }

            return Result.of(hits, request);
        }

    }

    private record ExclusionTable(String table,
                                  String label,
                                  String target,
                                  List<String> macColumns,
                                  List<String> networkColumns,
                                  List<String> textColumns) { }

    private static final List<ExclusionTable> EXCLUSION_TABLES = List.of(
            new ExclusionTable("beacon_exclusions", "Beacon exclusion", "BEACON_EXCLUSION",
                    List.of("source_mac"), List.of("destination_cidr", "source_cidr"), List.of("destination_host")),
            new ExclusionTable("recon_exclusions", "Recon exclusion", "RECON_EXCLUSION",
                    List.of("source_mac"), List.of("source_cidr", "target_cidr"), List.of()),
            new ExclusionTable("spoofing_exclusions", "Spoofing exclusion", "SPOOFING_EXCLUSION",
                    List.of("mac"), List.of("address_cidr"), List.of()),
            new ExclusionTable("icmp_exclusions", "ICMP exclusion", "ICMP_EXCLUSION",
                    List.of("source_mac"), List.of("source_cidr", "gateway_cidr"), List.of()),
            new ExclusionTable("dns_tunnel_exclusions", "DNS tunnel exclusion", "DNS_TUNNEL_EXCLUSION",
                    List.of(), List.of("client_address_cidr"), List.of("domain")),
            new ExclusionTable("dhcp_fingerprint_exclusions", "DHCP fingerprint exclusion",
                    "DHCP_FINGERPRINT_EXCLUSION", List.of("mac"), List.of(), List.of()),
            new ExclusionTable("assets_identity_exclusions", "Asset identity exclusion",
                    "ASSET_IDENTITY_EXCLUSION", List.of("mac"), List.of(), List.of())
    );

    private static class Exclusions extends EthernetSource {

        @Override
        public String id() {
            return "exclusions";
        }

        @Override
        public String title() {
            return "Exclusions";
        }

        @Override
        public Result search(Handle handle, Request request) {
            SearchTerm term = request.term();
            List<SearchHitResponse> hits = Lists.newArrayList();

            for (ExclusionTable table : EXCLUSION_TABLES) {
                SearchConditions conditions = new SearchConditions(term)
                        .uuid("uuid")
                        .mac(table.macColumns().toArray(new String[0]))
                        .networkText(table.networkColumns().toArray(new String[0]));
                if (!term.isIdentifier()) {
                    conditions.contains("name", "description");
                    conditions.contains(table.textColumns().toArray(new String[0]));
                }

                if (conditions.isEmpty()) {
                    continue;
                }

                List<String> shown = Lists.newArrayList(table.macColumns());
                shown.addAll(table.networkColumns());
                shown.addAll(table.textColumns());

                String sql = "SELECT uuid, name, description, enabled, updated_at"
                        + (shown.isEmpty() ? "" : ", " + String.join(", ", shown))
                        + " FROM " + table.table() + " WHERE " + TENANT + " AND " + conditions.sql()
                        + " ORDER BY " + bestFirst("name") + ", name ASC, uuid ASC LIMIT :cap";

                for (Map<String, Object> row : rows(handle, sql, conditions, request)) {
                    Facts facts = facts(term);
                    List<String> candidates = Lists.newArrayList(text(row, "name"), text(row, "uuid"));
                    for (String column : shown) {
                        facts.add("Matches", row.get(column));
                        candidates.add(text(row, column));
                    }
                    facts.add("State", bool(row, "enabled") ? "Enabled" : "Disabled");

                    hits.add(hit(
                            "EXCLUSION",
                            text(row, "name"),
                            table.label(),
                            facts.build(),
                            time(row, "updated_at"),
                            score(term, candidates.toArray(new String[0])),
                            link(table.target(), "uuid", text(row, "uuid"))
                    ));
                }
            }

            return merged(hits, request);
        }

    }

    private static String scanTitle(String type) {
        try {
            return ReconScanType.valueOf(type).getHumanReadable();
        } catch (IllegalArgumentException | NullPointerException e) {
            return "Scan";
        }
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String spaced = value.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String dhcpFindingText(String type, String protocol) {
        boolean rogue = "ROGUE_SERVER".equals(type);

        if (DhcpSpoofingDetector.PROTOCOL_ROUTER_ADVERTISEMENT.equals(protocol)) {
            return rogue ? "Rogue IPv6 router" : "IPv6 router advertising a changed configuration";
        }

        String server = DhcpSpoofingDetector.PROTOCOL_DHCP6.equals(protocol) ? "DHCPv6 server" : "DHCP server";
        return rogue ? "Rogue " + server : server + " handing out changed configuration";
    }

}
