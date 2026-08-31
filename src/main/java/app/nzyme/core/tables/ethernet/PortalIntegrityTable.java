package app.nzyme.core.tables.ethernet;

import app.nzyme.core.integrations.geoip.GeoIpLookupResult;
import app.nzyme.core.rest.resources.taps.reports.tables.portalintegrity.PortalIntegrityCheckContext;
import app.nzyme.core.rest.resources.taps.reports.tables.portalintegrity.PortalIntegrityHopReport;
import app.nzyme.core.rest.resources.taps.reports.tables.portalintegrity.PortalIntegrityUrlReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Timer;
import org.joda.time.DateTime;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.util.*;

public class PortalIntegrityTable implements DataTable {

    public enum PortalVerdict {
        OK, // Matched expectations.
        MISSING, // Expected a portal, none observed.
        MISMATCH, // Portal observed but shape is wrong.
        INCONCLUSIVE, // Probe error / truncated / chain didn't terminate. Can't judge.
        NONE // No monitoring configured.
    }

    public final class PortalExpectation {
        public final boolean expectRedirect; // Redirect-style portal
        public final Integer expectedInitialStatus; // Control URL's first response (null means don't check)
        public final List<String> requiredBodyMarkers; // All must be present in final body
        public final List<String> forbiddenBodyMarkers; // None may be present

        public PortalExpectation(boolean expectRedirect,
                                 Integer expectedInitialStatus,
                                 List<String> requiredBodyMarkers,
                                 List<String> forbiddenBodyMarkers) {
            this.expectRedirect = expectRedirect;
            this.expectedInitialStatus = expectedInitialStatus;
            this.requiredBodyMarkers = requiredBodyMarkers != null ? requiredBodyMarkers : List.of();
            this.forbiddenBodyMarkers = forbiddenBodyMarkers != null ? forbiddenBodyMarkers : List.of();
        }
    }

    public final class PortalEvaluation {
        public final PortalVerdict verdict;
        public final List<String> reasons;

        PortalEvaluation(PortalVerdict verdict, List<String> reasons) {
            this.verdict = verdict;
            this.reasons = reasons;
        }
    }

    private final TablesService tablesService;
    private final Timer totalReportTimer;
    private final ObjectMapper om;

    public PortalIntegrityTable(TablesService tablesService) {
        this.tablesService = tablesService;
        this.om = new ObjectMapper();

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.PORTAL_INTEGRITY_URL_REPORT_PROCESSING_TIMER);
    }

    public void handleUrlReport(UUID tapUuid, DateTime timestamp, PortalIntegrityUrlReport report) {
        try (Timer.Context ignored = totalReportTimer.time()) {
            MessageDigest dg;
            try {
                dg = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }

            UUID reportUuid = UUID.randomUUID();
            PortalIntegrityCheckContext ctx = report.context();

            // Evaluate.
            PortalEvaluation evaluationResult = evaluate(report, new PortalExpectation(
                    false, 200, null, null
            ));

            tablesService.getNzyme().getDatabase().useTransaction(handle -> {
                handle.createUpdate("INSERT INTO portal_integrity_reports(uuid, tap_uuid, control_url, " +
                                "probe_name, error, probe_interface, probe_mac, verdict, verdict_reasons, " +
                                "probed_at, created_at) VALUES(:uuid, :tap_uuid, :control_url, :probe_name, " +
                                ":error, :probe_interface, :probe_mac, :verdict, :verdict_reasons, :probed_at, NOW())")
                        .bind("uuid", reportUuid)
                        .bind("tap_uuid", tapUuid)
                        .bind("control_url", report.controlUrl())
                        .bind("probe_name", report.probeName())
                        .bind("error", report.error())
                        .bind("probe_interface", ctx.networkInterface())
                        .bind("probe_mac", ctx.mac())
                        .bind("verdict", evaluationResult.verdict)
                        .bindBySqlType("verdict_reasons", evaluationResult.reasons.toArray(new String[0]), Types.ARRAY)
                        .bind("probed_at", report.probedAt())
                        .execute();

                List<String> dns = ctx.dnsServers() != null ? ctx.dnsServers() : new ArrayList<>();
                handle.createUpdate("INSERT INTO portal_integrity_dhcp_leases(uuid, report_uuid, assigned_address, " +
                                "gateway_address, dhcp_server_address, dns_servers) " +
                                "VALUES(:uuid, :report_uuid, :assigned_address::inet, :gateway_address::inet, " +
                                ":dhcp_server_address::inet, :dns_servers::inet[])")
                        .bind("uuid", UUID.randomUUID())
                        .bind("report_uuid", reportUuid)
                        .bind("assigned_address", ctx.assignedCidr())
                        .bind("gateway_address", ctx.gateway())
                        .bind("dhcp_server_address", ctx.dhcpServer())
                        .bindBySqlType("dns_servers", dns.toArray(new String[0]), Types.ARRAY)
                        .execute();

                short hopIndex = 0;
                for (PortalIntegrityHopReport hop : report.hops()) {
                    byte[] raw = Base64.getDecoder().decode(hop.raw());
                    byte[] bodySha256 = dg.digest(raw);

                    byte[] leafSha256 = null;
                    String tlsJson = null;

                    if (hop.tls() != null) {
                        List<String> chainHexes = new ArrayList<>();

                        int pos = 0;
                        for (String certB64 : hop.tls().chainDer()) {
                            byte[] der = Base64.getDecoder().decode(certB64);
                            byte[] sha256 = dg.digest(der);
                            if (pos == 0) {
                                leafSha256 = sha256;
                            }
                            pos++;
                            chainHexes.add(bytesToHex(sha256));

                            // Dedup store: one row per unique cert, keyed by its own hash.
                            handle.createUpdate("INSERT INTO certificates(sha256, der, first_seen, last_seen, created_at) " +
                                            "VALUES(:sha256, :der, :first_seen, :last_seen, NOW()) " +
                                            "ON CONFLICT (sha256) DO UPDATE SET last_seen = EXCLUDED.last_seen")
                                    .bind("sha256", sha256)
                                    .bind("der", der)
                                    .bind("first_seen", report.probedAt())
                                    .bind("last_seen", report.probedAt())
                                    .execute();
                        }

                        ObjectNode tlsNode = om.createObjectNode();
                        tlsNode.put("leaf_sha256", bytesToHex(leafSha256));
                        ArrayNode chainArr = tlsNode.putArray("chain_sha256");
                        for (String h : chainHexes) {
                            chainArr.add(h);
                        }
                        if (hop.tls().protocolVersion() != null) {
                            tlsNode.put("protocol_version", hop.tls().protocolVersion());
                        }
                        if (hop.tls().cipherSuite() != null) {
                            tlsNode.put("cipher_suite", hop.tls().cipherSuite().intValue());
                        }
                        if (hop.tls().sni() != null) {
                            tlsNode.put("sni", hop.tls().sni());
                        }
                        tlsJson = om.writeValueAsString(tlsNode);
                    }

                    InetAddress resolvedIpAddr = stringtoInetAddress(hop.resolvedIp());
                    Optional<GeoIpLookupResult> resolvedIpGeo = tablesService.getNzyme()
                            .getGeoIpService()
                            .lookup(resolvedIpAddr);

                    handle.createUpdate("INSERT INTO portal_integrity_hops(uuid, report_uuid, hop_index, url, " +
                                    "resolved_address, resolved_address_geo_asn_number, " +
                                    "resolved_address_geo_asn_name, resolved_address_geo_asn_domain, " +
                                    "resolved_address_geo_city, resolved_address_geo_country_code, " +
                                    "resolved_address_geo_latitude, resolved_address_geo_longitude, " +
                                    "resolved_address_is_site_local, resolved_address_is_multicast, " +
                                    "resolved_address_is_loopback, status, followed_to, completeness, raw, " +
                                    "body_sha256, tls_leaf_sha256, tls) VALUES(:uuid, :report_uuid, :hop_index, " +
                                    ":url, :resolved_address::inet, :resolved_address_geo_asn_number, " +
                                    ":resolved_address_geo_asn_name, :resolved_address_geo_asn_domain, " +
                                    ":resolved_address_geo_city, :resolved_address_geo_country_code, " +
                                    ":resolved_address_geo_latitude, :resolved_address_geo_longitude, " +
                                    ":resolved_address_is_site_local, :resolved_address_is_multicast, " +
                                    ":resolved_address_is_loopback, :status, :followed_to, :completeness, :raw, " +
                                    ":body_sha256, :tls_leaf_sha256, :tls::jsonb)")
                            .bind("uuid", UUID.randomUUID())
                            .bind("report_uuid", reportUuid)
                            .bind("hop_index", hopIndex)
                            .bind("url", hop.url())
                            .bind("resolved_address", hop.resolvedIp())
                            .bind("resolved_address_is_site_local", resolvedIpAddr.isSiteLocalAddress())
                            .bind("resolved_address_is_loopback", resolvedIpAddr.isLoopbackAddress())
                            .bind("resolved_address_is_multicast", resolvedIpAddr.isMulticastAddress())
                            .bind("resolved_address_geo_asn_number", resolvedIpGeo.map(g -> g.asn().number()).orElse(null))
                            .bind("resolved_address_geo_asn_name", resolvedIpGeo.map(g -> g.asn().name()).orElse(null))
                            .bind("resolved_address_geo_asn_domain", resolvedIpGeo.map(g -> g.asn().domain()).orElse(null))
                            .bind("resolved_address_geo_city", resolvedIpGeo.map(g -> g.geo().city()).orElse(null))
                            .bind("resolved_address_geo_country_code", resolvedIpGeo.map(g -> g.geo().countryCode()).orElse(null))
                            .bind("resolved_address_geo_latitude", resolvedIpGeo.map(g -> g.geo().latitude()).orElse(null))
                            .bind("resolved_address_geo_longitude", resolvedIpGeo.map(g -> g.geo().longitude()).orElse(null))
                            .bind("status", hop.status())
                            .bind("followed_to", hop.followedTo())
                            .bind("completeness", hop.completeness())
                            .bind("raw", raw)
                            .bind("body_sha256", bodySha256)
                            .bind("tls_leaf_sha256", leafSha256)
                            .bind("tls", tlsJson)
                            .execute();

                    hopIndex++;
                }
            });
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public PortalEvaluation evaluate(PortalIntegrityUrlReport report, PortalExpectation exp) {
        List<String> reasons = new ArrayList<>();

        // Probe-level failure — nothing to assert against.
        if (report.error() != null) {
            return new PortalEvaluation(PortalVerdict.INCONCLUSIVE,
                    List.of("probe error: " + report.error()));
        }
        if (report.hops().isEmpty()) {
            return new PortalEvaluation(PortalVerdict.INCONCLUSIVE, List.of("no hops recorded"));
        }

        boolean redirected = report.hops().stream().anyMatch(h -> h.followedTo() != null);

        // Condition 1: expected a redirect-style portal, saw no redirect.
        if (exp.expectRedirect && !redirected) {
            reasons.add("expected a portal redirect but none was observed");
            return new PortalEvaluation(PortalVerdict.MISSING, reasons);
        }

        // Condition 2: verify the observed portal matches the expected shape.

        // Initial response code = the control URL's own (first) response.
        if (exp.expectedInitialStatus != null) {
            int initialStatus = report.hops().get(0).status();
            if (initialStatus != exp.expectedInitialStatus) {
                reasons.add("initial status " + initialStatus
                        + " != expected " + exp.expectedInitialStatus);
            }
        }

        // Final body = the terminal (non-redirect) hop.
        PortalIntegrityHopReport finalHop = report.hops().get(report.hops().size() - 1);

        // If the chain still redirects at the end, max_redirects was exhausted — we never
        // reached the landing page, so a body check would be meaningless.
        if (finalHop.followedTo() != null) {
            reasons.add("redirect chain did not terminate; landing page not reached");
            return new PortalEvaluation(PortalVerdict.INCONCLUSIVE, reasons);
        }

        boolean bodyChecks = !exp.requiredBodyMarkers.isEmpty() || !exp.forbiddenBodyMarkers.isEmpty();
        if (bodyChecks) {
            // Body markers are only trustworthy on a fully-read message.
            if (!"Complete".equalsIgnoreCase(finalHop.completeness())) {
                reasons.add("final hop body incomplete (" + finalHop.completeness()
                        + "); cannot verify body");
                return new PortalEvaluation(PortalVerdict.INCONCLUSIVE, reasons);
            }
            String body = extractBody(finalHop.raw());
            for (String marker : exp.requiredBodyMarkers) {
                if (!body.contains(marker)) {
                    reasons.add("missing required body marker: " + marker);
                }
            }
            for (String marker : exp.forbiddenBodyMarkers) {
                if (body.contains(marker)) {
                    reasons.add("contains forbidden body marker: " + marker);
                }
            }
        }

        return reasons.isEmpty()
                ? new PortalEvaluation(PortalVerdict.OK, reasons)
                : new PortalEvaluation(PortalVerdict.MISMATCH, reasons);
    }

    private static String extractBody(String rawBase64) {
        byte[] raw = Base64.getDecoder().decode(rawBase64);
        int crlf = indexOf(raw, new byte[]{'\r', '\n', '\r', '\n'});
        int lf = indexOf(raw, new byte[]{'\n', '\n'});
        int a = crlf >= 0 ? crlf + 4 : -1;
        int b = lf >= 0 ? lf + 2 : -1;
        int end = (a >= 0 && b >= 0) ? Math.min(a, b) : Math.max(a, b);
        if (end < 0) return "";
        return new String(raw, end, raw.length - end, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    public static InetAddress stringtoInetAddress(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            // This shouldn't happen because we pass IP addresses.
            throw new RuntimeException(e);
        }
    }

    @Override
    public void retentionClean() {
        // NOOP
    }

}