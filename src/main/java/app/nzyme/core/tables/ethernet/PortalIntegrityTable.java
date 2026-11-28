package app.nzyme.core.tables.ethernet;

import app.nzyme.core.ethernet.InternalAddresses;
import app.nzyme.core.ethernet.portalintegrity.PortalIntegrity;
import app.nzyme.core.ethernet.portalintegrity.PortalIntegrityJudge;
import app.nzyme.core.ethernet.portalintegrity.PortalUrls;
import app.nzyme.core.ethernet.tls.CertificateStore;
import app.nzyme.core.integrations.geoip.GeoIpLookupResult;
import app.nzyme.core.rest.resources.taps.reports.tables.portalintegrity.PortalIntegrityCheckContext;
import app.nzyme.core.rest.resources.taps.reports.tables.portalintegrity.PortalIntegrityHopReport;
import app.nzyme.core.rest.resources.taps.reports.tables.portalintegrity.PortalIntegrityUrlReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import jakarta.annotation.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.util.*;

public class PortalIntegrityTable implements DataTable {

    private static final Logger LOG = LogManager.getLogger(PortalIntegrityTable.class);

    // More than the longest answer of a connectivity check, which is all the first response is compared to.
    private static final int JUDGED_RESPONSE_BYTES = 16 * 1024;

    private final TablesService tablesService;
    private final Timer totalReportTimer;
    private final ObjectMapper om;

    public PortalIntegrityTable(TablesService tablesService) {
        this.tablesService = tablesService;
        this.om = new ObjectMapper();

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.PORTAL_INTEGRITY_URL_REPORT_PROCESSING_TIMER);
    }

    public void handleUrlReport(UUID tapUuid,
                                @Nullable UUID organizationId,
                                @Nullable UUID tenantId,
                                PortalIntegrityUrlReport report) {
        try (Timer.Context ignored = totalReportTimer.time()) {
            MessageDigest dg;
            try {
                dg = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }

            UUID reportUuid = UUID.randomUUID();
            PortalIntegrityCheckContext ctx = report.context();

            InternalAddresses.Rules internalAddresses = tablesService.getNzyme().getEthernet().internalAddresses().ofTap(tapUuid);

            List<PortalIntegrityJudge.Hop> judgedHops = new ArrayList<>();

            tablesService.getNzyme().getDatabase().useTransaction(handle -> {
                handle.createUpdate("INSERT INTO portal_integrity_reports(uuid, tap_uuid, control_url, " +
                                "probe_name, error, probe_interface, probe_mac, " +
                                "probed_at, created_at) VALUES(:uuid, :tap_uuid, :control_url, :probe_name, " +
                                ":error, :probe_interface, :probe_mac, :probed_at, NOW())")
                        .bind("uuid", reportUuid)
                        .bind("tap_uuid", tapUuid)
                        .bind("control_url", text(report.controlUrl()))
                        .bind("probe_name", text(report.probeName()))
                        .bind("error", text(report.error()))
                        .bind("probe_interface", ctx.networkInterface())
                        .bind("probe_mac", ctx.mac())
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

                            CertificateStore.upsert(handle, der, report.probedAt());
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
                            .bind("url", text(hop.url()))
                            .bind("resolved_address", hop.resolvedIp())
                            .bind("resolved_address_is_site_local", internalAddresses.isSiteLocal(resolvedIpAddr))
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
                            .bind("followed_to", text(hop.followedTo()))
                            .bind("completeness", hop.completeness())
                            .bind("raw", raw)
                            .bind("body_sha256", bodySha256)
                            .bind("tls_leaf_sha256", leafSha256 == null ? null : bytesToHex(leafSha256))
                            .bind("tls", tlsJson)
                            .execute();

                    judgedHops.add(new PortalIntegrityJudge.Hop(
                            text(hop.url()),
                            hop.status(),
                            hop.resolvedIp(),
                            leafSha256 == null ? null : bytesToHex(leafSha256),
                            hopIndex > 0 ? new byte[0] : Arrays.copyOf(raw, Math.min(raw.length, JUDGED_RESPONSE_BYTES))
                    ));

                    hopIndex++;
                }
            });

            try {
                recordControlUrl(tapUuid, organizationId, tenantId, reportUuid, report, judgedHops);
            } catch (Exception e) {
                LOG.error("Could not record the control URL of portal integrity report [{}] of tap [{}].",
                        reportUuid, tapUuid, e);
            }
        }
    }

    private void recordControlUrl(UUID tapUuid,
                                  @Nullable UUID organizationId,
                                  @Nullable UUID tenantId,
                                  UUID reportUuid,
                                  PortalIntegrityUrlReport report,
                                  List<PortalIntegrityJudge.Hop> hops) {
        if (organizationId == null || tenantId == null) {
            return;
        }

        String probeName = text(report.probeName());
        String controlUrl = text(report.controlUrl());

        // The two are part of a unique index, which limits the size of its rows in bytes.
        if (probeName.isEmpty()
                || probeName.getBytes(StandardCharsets.UTF_8).length > PortalIntegrity.MAX_PROBE_NAME_LENGTH
                || controlUrl.getBytes(StandardCharsets.UTF_8).length > PortalUrls.MAX_URL_LENGTH
                || !PortalUrls.isHttpUrl(controlUrl)) {
            LOG.debug("Not recording the control URL of portal integrity report [{}] of tap [{}]: Probe name or " +
                    "control URL not usable.", reportUuid, tapUuid);
            return;
        }

        tablesService.getNzyme().getEthernet().portalIntegrity().recordObservation(
                organizationId,
                tenantId,
                tapUuid,
                probeName,
                controlUrl,
                report.context().assignedCidr().contains(":") ? 6 : 4,
                reportUuid,
                PortalIntegrityJudge.observe(controlUrl, text(report.error()), hops)
        );
    }

    // Redirect targets and errors carry bytes of the response. PostgreSQL refuses text with a NUL character.
    @Nullable
    private static String text(@Nullable String value) {
        return value == null ? null : value.replace("\u0000", "");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
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