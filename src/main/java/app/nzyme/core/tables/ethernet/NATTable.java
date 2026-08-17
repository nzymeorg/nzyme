package app.nzyme.core.tables.ethernet;

import app.nzyme.core.ethernet.GeoData;
import app.nzyme.core.ethernet.l4.db.L4AddressAttributes;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import app.nzyme.core.ethernet.nat.NATTraversalDiscoveryStatus;
import app.nzyme.core.integrations.geoip.GeoIpLookupResult;
import app.nzyme.core.integrations.geoip.GeoIpService;
import app.nzyme.core.rest.resources.taps.reports.tables.SocketAddressReport;
import app.nzyme.core.rest.resources.taps.reports.tables.nat.NatTraversalReport;
import app.nzyme.core.rest.resources.taps.reports.tables.nat.StunDiscoveryReport;
import app.nzyme.core.rest.resources.taps.reports.tables.nat.StunNegotiationFlowReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.Tools;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;
import tools.jackson.databind.ObjectMapper;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.Tools.stringtoInetAddress;

public class NATTable implements DataTable  {

    private static final Logger LOG = LogManager.getLogger(NATTable.class);

    private final TablesService tablesService;

    private final Timer totalReportTimer;
    private final Timer traversalDiscoveriesTimer;
    private final Timer traversalNegotiationsTimer;

    private final ObjectMapper om;

    private final GeoIpService geoIp;

    public NATTable(TablesService tablesService) {
        this.tablesService = tablesService;
        this.om = new ObjectMapper();
        this.geoIp = tablesService.getNzyme().getGeoIpService();

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.NAT_TOTAL_REPORT_PROCESSING_TIMER);
        this.traversalDiscoveriesTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.NAT_DISCOVERIES_REPORT_PROCESSING_TIMER);
        this.traversalNegotiationsTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.NAT_NEGOTIATIONS_REPORT_PROCESSING_TIMER);
    }

    public void handleTraversalReport(UUID tapUuid, DateTime timestamp, NatTraversalReport report) {
        try(Timer.Context ignored1 = totalReportTimer.time()) {
            tablesService.getNzyme().getDatabase().useHandle(handle -> {
                try (Timer.Context ignored2 = traversalDiscoveriesTimer.time()) {
                    writeStunDiscoveries(handle, tapUuid, report.discoveries());
                }
                try (Timer.Context ignored2 = traversalNegotiationsTimer.time()) {
                    writeStunNegotiationFlows(handle, tapUuid, report.negotiationFlows());
                }
            });
        }
    }

    private void writeStunDiscoveries(Handle handle, UUID tapUuid, List<StunDiscoveryReport> discoveries) {
        if (discoveries == null || discoveries.isEmpty()) {
            return;
        }

        Map<String, StunDiscoveryReport> deduped = new LinkedHashMap<>();
        for (StunDiscoveryReport discovery : discoveries) {
            String sessionKey = Tools.buildL4Key(
                    discovery.firstSeen(),
                    discovery.sourceAddress(),
                    discovery.destinationAddress(),
                    discovery.sourcePort(),
                    discovery.destinationPort()
            );
            String dedupeKey = sessionKey + "|" + discovery.firstSeen().getMillis();
            deduped.merge(dedupeKey, discovery, (a, b) ->
                    b.lastActivity().isAfter(a.lastActivity()) ? b : a);
        }

        PreparedBatch batch = handle.prepareBatch("INSERT INTO nat_traversal_discoveries(uuid, " +
                "tap_uuid, l4_session_key, transport, mapped_addresses, status, most_recent_segment_time, " +
                "first_seen, updated_at, created_at) VALUES(:uuid, :tap_uuid, :l4_session_key, :transport, " +
                ":mapped_addresses::jsonb, :status, :most_recent_segment_time, :first_seen, NOW(), NOW()) " +
                "ON CONFLICT (tap_uuid, l4_session_key, first_seen) DO UPDATE SET " +
                "mapped_addresses = EXCLUDED.mapped_addresses, status = EXCLUDED.status, " +
                "most_recent_segment_time = EXCLUDED.most_recent_segment_time, updated_at = NOW()");

        for (StunDiscoveryReport discovery : deduped.values()) {
            try {
                String sessionKey = Tools.buildL4Key(
                        discovery.firstSeen(),
                        discovery.sourceAddress(),
                        discovery.destinationAddress(),
                        discovery.sourcePort(),
                        discovery.destinationPort()
                );

                List<L4AddressData> mappedAddresses = buildAddresses(discovery.mappedAddresses());
                String mappedAddressesJson = om.writeValueAsString(mappedAddresses);

                NATTraversalDiscoveryStatus status;
                if (discovery.sawSuccessResponse()) {
                    status = NATTraversalDiscoveryStatus.COMPLETE;
                } else if (discovery.sawErrorResponse()) {
                    status = NATTraversalDiscoveryStatus.ERROR;
                } else {
                    status = NATTraversalDiscoveryStatus.INCOMPLETE;
                }

                batch
                        .bind("uuid", UUID.randomUUID())
                        .bind("tap_uuid", tapUuid)
                        .bind("l4_session_key", sessionKey)
                        .bind("transport", discovery.transport())
                        .bind("mapped_addresses", mappedAddressesJson)
                        .bind("status", status)
                        .bind("most_recent_segment_time", discovery.lastActivity())
                        .bind("first_seen", discovery.firstSeen())
                        .add();
            } catch (Exception e) {
                LOG.error("Could not prepare NAT traversal discovery for write.", e);
            }
        }

        try {
            batch.execute();
        } catch (Exception e) {
            LOG.error("Could not write NAT traversal discoveries.", e);
        }
    }

    private void writeStunNegotiationFlows(Handle handle, UUID tapUuid, List<StunNegotiationFlowReport> negotiations) {
        if (negotiations == null || negotiations.isEmpty()) {
            return;
        }

        Map<String, StunNegotiationFlowReport> deduped = new LinkedHashMap<>();
        for (StunNegotiationFlowReport negotiation : negotiations) {
            if (negotiation.negotiationKey() == null) {
                LOG.debug("Received negotiation flow report without negotiation key. Skipping.");
                continue;
            }

            String sessionKey = Tools.buildL4Key(
                    negotiation.firstSeen(),
                    negotiation.sourceAddress(),
                    negotiation.destinationAddress(),
                    negotiation.sourcePort(),
                    negotiation.destinationPort()
            );
            String dedupeKey = sessionKey + "|" + negotiation.firstSeen().getMillis();
            deduped.merge(dedupeKey, negotiation, (a, b) ->
                    b.lastActivity().isAfter(a.lastActivity()) ? b : a);
        }

        PreparedBatch batch = handle.prepareBatch("INSERT INTO nat_stun_negotiation_flows(uuid, " +
                "tap_uuid, negotiation_key, negotiation_key_sha256, l4_session_key, transport, ufrags, successful, " +
                "is_turn, turn_usernames, mapped_addresses, relayed_addresses, peer_addresses, first_seen, " +
                "last_activity, updated_at, created_at) VALUES(:uuid, :tap_uuid, :negotiation_key, " +
                ":negotiation_key_sha256, :l4_session_key, :transport, :ufrags, :successful, :is_turn, " +
                ":turn_usernames, :mapped_addresses::jsonb, :relayed_addresses::jsonb, :peer_addresses::jsonb, " +
                ":first_seen, :last_activity, NOW(), NOW()) " +
                "ON CONFLICT (tap_uuid, l4_session_key, first_seen) DO UPDATE SET " +
                "ufrags = EXCLUDED.ufrags, " +
                "successful = nat_stun_negotiation_flows.successful OR EXCLUDED.successful, " +
                "is_turn = nat_stun_negotiation_flows.is_turn OR EXCLUDED.is_turn, " +
                "turn_usernames = EXCLUDED.turn_usernames, " +
                "mapped_addresses = EXCLUDED.mapped_addresses, " +
                "relayed_addresses = EXCLUDED.relayed_addresses, " +
                "peer_addresses = EXCLUDED.peer_addresses, " +
                "last_activity = EXCLUDED.last_activity, updated_at = NOW()");

        for (StunNegotiationFlowReport negotiation : deduped.values()) {
            try {
                String negotiationKeySha256 = Hashing.sha256()
                        .hashString(negotiation.negotiationKey(), StandardCharsets.UTF_8)
                        .toString();

                String sessionKey = Tools.buildL4Key(
                        negotiation.firstSeen(),
                        negotiation.sourceAddress(),
                        negotiation.destinationAddress(),
                        negotiation.sourcePort(),
                        negotiation.destinationPort()
                );

                List<L4AddressData> mappedAddresses = buildAddresses(negotiation.mappedAddresses());
                String mappedAddressesJson = om.writeValueAsString(mappedAddresses);
                List<L4AddressData> relayedAddresses = buildAddresses(negotiation.relayedAddresses());
                String relayedAddressesJson = om.writeValueAsString(relayedAddresses);
                List<L4AddressData> peerAddresses = buildAddresses(negotiation.peerAddresses());
                String peerAddressesJson = om.writeValueAsString(peerAddresses);

                String[] ufrags = negotiation.ufrags().toArray(new String[0]);
                String[] turnUsernames = negotiation.turnUsernames().toArray(new String[0]);

                batch
                        .bind("uuid", UUID.randomUUID())
                        .bind("tap_uuid", tapUuid)
                        .bind("negotiation_key", negotiation.negotiationKey())
                        .bind("negotiation_key_sha256", negotiationKeySha256)
                        .bind("l4_session_key", sessionKey)
                        .bind("transport", negotiation.transport())
                        .bindBySqlType("ufrags", ufrags, Types.ARRAY)
                        .bind("successful", negotiation.successful())
                        .bind("is_turn", negotiation.isTurn())
                        .bindBySqlType("turn_usernames", turnUsernames, Types.ARRAY)
                        .bind("mapped_addresses", mappedAddressesJson)
                        .bind("relayed_addresses", relayedAddressesJson)
                        .bind("peer_addresses", peerAddressesJson)
                        .bind("first_seen", negotiation.firstSeen())
                        .bind("last_activity", negotiation.lastActivity())
                        .add();
            } catch (Exception e) {
                LOG.error("Could not prepare NAT negotiation flow for write.", e);
            }
        }

        try {
            batch.execute();
        } catch (Exception e) {
            LOG.error("Could not write NAT traversal negotiation flows.", e);
        }
    }

    private List<L4AddressData> buildAddresses(List<SocketAddressReport> addresses) {
        List<L4AddressData> result = Lists.newArrayList();

        if (addresses == null) {
            return result;
        }

        for (var mapped : addresses) {
            try {
                InetAddress address = stringtoInetAddress(mapped.address());
                GeoData geoData;
                if (address.isSiteLocalAddress() || address.isLoopbackAddress()
                        || address.isAnyLocalAddress() || address.isLinkLocalAddress()) {
                    geoData = GeoData.create(null, null, null, null, null, null, null);
                } else {
                    Optional<GeoIpLookupResult> geo = geoIp.lookup(address);
                    geoData = GeoData.create(
                            geo.map(g -> g.asn().number() == null ? null : g.asn().number().intValue()).orElse(null),
                            geo.map(g -> g.asn().name()).orElse(null),
                            geo.map(g -> g.asn().domain()).orElse(null),
                            geo.map(g -> g.geo().city()).orElse(null),
                            geo.map(g -> g.geo().countryCode()).orElse(null),
                            geo.map(g -> g.geo().latitude()).orElse(null),
                            geo.map(g -> g.geo().longitude()).orElse(null)
                    );
                }

                result.add(L4AddressData.create(
                        null,
                        mapped.address(),
                        mapped.port(),
                        geoData,
                        L4AddressAttributes.create(
                                address.isSiteLocalAddress(),
                                address.isLoopbackAddress(),
                                address.isMulticastAddress()
                        )
                ));
            } catch (Exception e) {
                LOG.error("Could not enrich mapped address [{}].", mapped, e);
            }
        }

        return result;
    }

    @Override
    public void retentionClean() {
        // NOOP
    }
}