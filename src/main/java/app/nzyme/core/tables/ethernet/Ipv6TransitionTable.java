package app.nzyme.core.tables.ethernet;

import app.nzyme.core.ethernet.InternalAddresses;
import app.nzyme.core.integrations.geoip.GeoIpLookupResult;
import app.nzyme.core.integrations.geoip.GeoIpService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
import app.nzyme.core.rest.resources.taps.reports.tables.ipv6transition.Ipv6TransitionTunnelReport;
import app.nzyme.core.rest.resources.taps.reports.tables.ipv6transition.Ipv6TransitionTunnelsReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.Tools;
import com.codahale.metrics.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.Tools.stringtoInetAddress;

public class Ipv6TransitionTable implements DataTable {

    private static final Logger LOG = LogManager.getLogger(Ipv6TransitionTable.class);

    private static final List<String> TUNNEL_TYPES = List.of("Teredo", "6in4", "6to4", "ISATAP");
    private static final List<String> TRANSPORTS = List.of("UDP", "IP");

    // What a tap reports at most.
    private static final int MAX_TUNNELS = 5_000;

    private final TablesService tablesService;
    private final GeoIpService geoIp;
    private final ObjectMapper om = JsonMapper.builder().build();

    private final Timer totalReportTimer;

    public Ipv6TransitionTable(TablesService tablesService) {
        this.tablesService = tablesService;
        this.geoIp = tablesService.getNzyme().getGeoIpService();

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.IPV6_TRANSITION_TOTAL_REPORT_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, Ipv6TransitionTunnelsReport report) {
        InternalAddresses.Rules internalAddresses = tablesService.getNzyme().getEthernet().internalAddresses().ofTap(tapUuid);

        tablesService.getNzyme().getDatabase().useHandle(handle -> {
            try(Timer.Context ignored = totalReportTimer.time()) {
                writeTunnels(handle, tapUuid, report.tunnels(), internalAddresses);
            }
        });
    }
    
    private void writeTunnels(Handle handle,
                              UUID tapUuid,
                              List<Ipv6TransitionTunnelReport> tunnels,
                              InternalAddresses.Rules internalAddresses) {
        PreparedBatch batch = handle.prepareBatch("INSERT INTO ipv6_transition_tunnels(uuid, tap_uuid, session_key, " +
                "untimed_session_key, transport, tunnel_type, details, connection_status, " +
                "tunneled_bytes, source_mac, source_address, source_port, source_address_is_site_local, " +
                "source_address_is_loopback, source_address_is_multicast, source_address_geo_asn_number, " +
                "source_address_geo_asn_name, source_address_geo_asn_domain, source_address_geo_city, " +
                "source_address_geo_country_code, source_address_geo_latitude, source_address_geo_longitude, " +
                "destination_mac, destination_address, destination_port, destination_address_is_site_local, " +
                "destination_address_is_loopback, destination_address_is_multicast, " +
                "destination_address_geo_asn_number, destination_address_geo_asn_name, " +
                "destination_address_geo_asn_domain, destination_address_geo_city, " +
                "destination_address_geo_country_code, destination_address_geo_latitude, " +
                "destination_address_geo_longitude, established_at, terminated_at, most_recent_segment_time, " +
                "updated_at, created_at) VALUES(:uuid, :tap_uuid, :session_key, :untimed_session_key, :transport, " +
                ":tunnel_type, :details::jsonb, :connection_status, " +
                ":tunneled_bytes, :source_mac, " +
                ":source_address::inet, :source_port, :source_address_is_site_local, :source_address_is_loopback, " +
                ":source_address_is_multicast, :source_address_geo_asn_number, :source_address_geo_asn_name, " +
                ":source_address_geo_asn_domain, :source_address_geo_city, :source_address_geo_country_code, " +
                ":source_address_geo_latitude, :source_address_geo_longitude, :destination_mac, " +
                ":destination_address::inet, :destination_port, :destination_address_is_site_local, " +
                ":destination_address_is_loopback, :destination_address_is_multicast, " +
                ":destination_address_geo_asn_number, :destination_address_geo_asn_name, " +
                ":destination_address_geo_asn_domain, :destination_address_geo_city, " +
                ":destination_address_geo_country_code, :destination_address_geo_latitude, " +
                ":destination_address_geo_longitude, :established_at, :terminated_at, :most_recent_segment_time, " +
                "NOW(), NOW()) " +
                "ON CONFLICT (tap_uuid, session_key) DO UPDATE SET " +
                "tunnel_type = EXCLUDED.tunnel_type, " +
                "details = EXCLUDED.details, " +
                "connection_status = EXCLUDED.connection_status, " +
                "tunneled_bytes = GREATEST(ipv6_transition_tunnels.tunneled_bytes, EXCLUDED.tunneled_bytes), " +
                "source_mac = COALESCE(EXCLUDED.source_mac, ipv6_transition_tunnels.source_mac), " +
                "source_address = EXCLUDED.source_address, " +
                "source_port = EXCLUDED.source_port, " +
                "source_address_is_site_local = EXCLUDED.source_address_is_site_local, " +
                "source_address_is_loopback = EXCLUDED.source_address_is_loopback, " +
                "source_address_is_multicast = EXCLUDED.source_address_is_multicast, " +
                "source_address_geo_asn_number = EXCLUDED.source_address_geo_asn_number, " +
                "source_address_geo_asn_name = EXCLUDED.source_address_geo_asn_name, " +
                "source_address_geo_asn_domain = EXCLUDED.source_address_geo_asn_domain, " +
                "source_address_geo_city = EXCLUDED.source_address_geo_city, " +
                "source_address_geo_country_code = EXCLUDED.source_address_geo_country_code, " +
                "source_address_geo_latitude = EXCLUDED.source_address_geo_latitude, " +
                "source_address_geo_longitude = EXCLUDED.source_address_geo_longitude, " +
                "destination_mac = COALESCE(EXCLUDED.destination_mac, ipv6_transition_tunnels.destination_mac), " +
                "destination_address = EXCLUDED.destination_address, " +
                "destination_port = EXCLUDED.destination_port, " +
                "destination_address_is_site_local = EXCLUDED.destination_address_is_site_local, " +
                "destination_address_is_loopback = EXCLUDED.destination_address_is_loopback, " +
                "destination_address_is_multicast = EXCLUDED.destination_address_is_multicast, " +
                "destination_address_geo_asn_number = EXCLUDED.destination_address_geo_asn_number, " +
                "destination_address_geo_asn_name = EXCLUDED.destination_address_geo_asn_name, " +
                "destination_address_geo_asn_domain = EXCLUDED.destination_address_geo_asn_domain, " +
                "destination_address_geo_city = EXCLUDED.destination_address_geo_city, " +
                "destination_address_geo_country_code = EXCLUDED.destination_address_geo_country_code, " +
                "destination_address_geo_latitude = EXCLUDED.destination_address_geo_latitude, " +
                "destination_address_geo_longitude = EXCLUDED.destination_address_geo_longitude, " +
                "terminated_at = EXCLUDED.terminated_at, " +
                "most_recent_segment_time = GREATEST(ipv6_transition_tunnels.most_recent_segment_time, EXCLUDED.most_recent_segment_time), " +
                "updated_at = NOW() " +
                "WHERE (ipv6_transition_tunnels.tunnel_type, ipv6_transition_tunnels.details, ipv6_transition_tunnels.connection_status, " +
                "ipv6_transition_tunnels.tunneled_bytes, ipv6_transition_tunnels.source_mac, ipv6_transition_tunnels.source_address, " +
                "ipv6_transition_tunnels.source_port, ipv6_transition_tunnels.source_address_is_site_local, " +
                "ipv6_transition_tunnels.source_address_is_loopback, ipv6_transition_tunnels.source_address_is_multicast, " +
                "ipv6_transition_tunnels.source_address_geo_asn_number, ipv6_transition_tunnels.source_address_geo_asn_name, " +
                "ipv6_transition_tunnels.source_address_geo_asn_domain, ipv6_transition_tunnels.source_address_geo_city, " +
                "ipv6_transition_tunnels.source_address_geo_country_code, ipv6_transition_tunnels.source_address_geo_latitude, " +
                "ipv6_transition_tunnels.source_address_geo_longitude, ipv6_transition_tunnels.destination_mac, " +
                "ipv6_transition_tunnels.destination_address, ipv6_transition_tunnels.destination_port, " +
                "ipv6_transition_tunnels.destination_address_is_site_local, ipv6_transition_tunnels.destination_address_is_loopback, " +
                "ipv6_transition_tunnels.destination_address_is_multicast, ipv6_transition_tunnels.destination_address_geo_asn_number, " +
                "ipv6_transition_tunnels.destination_address_geo_asn_name, ipv6_transition_tunnels.destination_address_geo_asn_domain, " +
                "ipv6_transition_tunnels.destination_address_geo_city, ipv6_transition_tunnels.destination_address_geo_country_code, " +
                "ipv6_transition_tunnels.destination_address_geo_latitude, ipv6_transition_tunnels.destination_address_geo_longitude, " +
                "ipv6_transition_tunnels.terminated_at, ipv6_transition_tunnels.most_recent_segment_time) IS DISTINCT FROM " +
                "(EXCLUDED.tunnel_type, EXCLUDED.details, EXCLUDED.connection_status, GREATEST(ipv6_transition_tunnels.tunneled_bytes, " +
                "EXCLUDED.tunneled_bytes), COALESCE(EXCLUDED.source_mac, ipv6_transition_tunnels.source_mac), " +
                "EXCLUDED.source_address, EXCLUDED.source_port, EXCLUDED.source_address_is_site_local, " +
                "EXCLUDED.source_address_is_loopback, EXCLUDED.source_address_is_multicast, " +
                "EXCLUDED.source_address_geo_asn_number, EXCLUDED.source_address_geo_asn_name, " +
                "EXCLUDED.source_address_geo_asn_domain, EXCLUDED.source_address_geo_city, " +
                "EXCLUDED.source_address_geo_country_code, EXCLUDED.source_address_geo_latitude, " +
                "EXCLUDED.source_address_geo_longitude, COALESCE(EXCLUDED.destination_mac, " +
                "ipv6_transition_tunnels.destination_mac), EXCLUDED.destination_address, EXCLUDED.destination_port, " +
                "EXCLUDED.destination_address_is_site_local, EXCLUDED.destination_address_is_loopback, " +
                "EXCLUDED.destination_address_is_multicast, EXCLUDED.destination_address_geo_asn_number, " +
                "EXCLUDED.destination_address_geo_asn_name, EXCLUDED.destination_address_geo_asn_domain, " +
                "EXCLUDED.destination_address_geo_city, EXCLUDED.destination_address_geo_country_code, " +
                "EXCLUDED.destination_address_geo_latitude, EXCLUDED.destination_address_geo_longitude, " +
                "EXCLUDED.terminated_at, GREATEST(ipv6_transition_tunnels.most_recent_segment_time, " +
                "EXCLUDED.most_recent_segment_time))");

        int count = 0;
        for (Ipv6TransitionTunnelReport tunnel : tunnels) {
            if (count >= MAX_TUNNELS) {
                LOG.warn("IPv6 transition tunnels report of tap [{}] has more than {} tunnels. Skipping the rest.",
                        tapUuid, MAX_TUNNELS);
                break;
            }

            try {
                if (!TUNNEL_TYPES.contains(tunnel.tunnelType()) || !TRANSPORTS.contains(tunnel.transport())) {
                    continue;
                }

                // A tunnel that is carried directly in IP has no ports. Its keys are built as if they were zero.
                int sourcePort = tunnel.sourcePort() == null ? 0 : tunnel.sourcePort();
                int destinationPort = tunnel.destinationPort() == null ? 0 : tunnel.destinationPort();

                String sessionKey = Tools.buildL4Key(
                        tunnel.establishedAt(),
                        tunnel.sourceAddress(),
                        tunnel.destinationAddress(),
                        sourcePort,
                        destinationPort);

                String untimedSessionKey = Tools.buildUntimedL4Key(
                        tunnel.sourceAddress(),
                        tunnel.destinationAddress(),
                        sourcePort,
                        destinationPort);

                InetAddress sourceAddress = stringtoInetAddress(tunnel.sourceAddress());
                InetAddress destinationAddress = stringtoInetAddress(tunnel.destinationAddress());
                Optional<GeoIpLookupResult> sourceGeo = geoIp.lookup(sourceAddress);
                Optional<GeoIpLookupResult> destinationGeo = geoIp.lookup(destinationAddress);

                batch
                        .bind("uuid", UUID.randomUUID())
                        .bind("tap_uuid", tapUuid)
                        .bind("session_key", sessionKey)
                        .bind("untimed_session_key", untimedSessionKey)
                        .bind("transport", tunnel.transport())
                        .bind("tunnel_type", tunnel.tunnelType())
                        .bind("details", om.writeValueAsString(tunnel.details() == null ? Map.of() : tunnel.details()))
                        .bind("connection_status", tunnel.connectionStatus())
                        .bind("tunneled_bytes", tunnel.tunneledBytes())
                        .bind("source_mac", tunnel.sourceMac())
                        .bind("source_address", tunnel.sourceAddress())
                        .bind("source_port", tunnel.sourcePort())
                        .bind("source_address_is_site_local", internalAddresses.isSiteLocal(sourceAddress))
                        .bind("source_address_is_loopback", sourceAddress.isLoopbackAddress())
                        .bind("source_address_is_multicast", sourceAddress.isMulticastAddress())
                        .bind("source_address_geo_asn_number", sourceGeo.map(g -> g.asn().number()).orElse(null))
                        .bind("source_address_geo_asn_name", sourceGeo.map(g -> g.asn().name()).orElse(null))
                        .bind("source_address_geo_asn_domain", sourceGeo.map(g -> g.asn().domain()).orElse(null))
                        .bind("source_address_geo_city", sourceGeo.map(g -> g.geo().city()).orElse(null))
                        .bind("source_address_geo_country_code", sourceGeo.map(g -> g.geo().countryCode()).orElse(null))
                        .bind("source_address_geo_latitude", sourceGeo.map(g -> g.geo().latitude()).orElse(null))
                        .bind("source_address_geo_longitude", sourceGeo.map(g -> g.geo().longitude()).orElse(null))
                        .bind("destination_mac", tunnel.destinationMac())
                        .bind("destination_address", tunnel.destinationAddress())
                        .bind("destination_port", tunnel.destinationPort())
                        .bind("destination_address_is_site_local", internalAddresses.isSiteLocal(destinationAddress))
                        .bind("destination_address_is_loopback", destinationAddress.isLoopbackAddress())
                        .bind("destination_address_is_multicast", destinationAddress.isMulticastAddress())
                        .bind("destination_address_geo_asn_number", destinationGeo.map(g -> g.asn().number()).orElse(null))
                        .bind("destination_address_geo_asn_name", destinationGeo.map(g -> g.asn().name()).orElse(null))
                        .bind("destination_address_geo_asn_domain", destinationGeo.map(g -> g.asn().domain()).orElse(null))
                        .bind("destination_address_geo_city", destinationGeo.map(g -> g.geo().city()).orElse(null))
                        .bind("destination_address_geo_country_code", destinationGeo.map(g -> g.geo().countryCode()).orElse(null))
                        .bind("destination_address_geo_latitude", destinationGeo.map(g -> g.geo().latitude()).orElse(null))
                        .bind("destination_address_geo_longitude", destinationGeo.map(g -> g.geo().longitude()).orElse(null))
                        .bind("established_at", tunnel.establishedAt())
                        .bind("terminated_at", tunnel.terminatedAt())
                        .bind("most_recent_segment_time", tunnel.mostRecentSegmentTime())
                        .add();
                count++;
            } catch (Exception e) {
                LOG.error("Could not write IPv6 transition tunnel.", e);
            }
        }

        if (count > 0) {
            batch.execute();
        }
    }

    @Override
    public void retentionClean() {
        // NOOP. Database cleaned by category/tenant independently.
    }

}
