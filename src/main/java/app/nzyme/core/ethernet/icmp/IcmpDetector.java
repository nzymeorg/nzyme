package app.nzyme.core.ethernet.icmp;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.ethernet.icmp.db.IcmpExclusionEntry;
import app.nzyme.core.ethernet.icmp.db.IcmpRedirectEntry;
import app.nzyme.core.ethernet.icmp.db.IcmpTracerouteEntry;
import app.nzyme.core.ethernet.icmp.db.IcmpTunnelEntry;
import app.nzyme.core.taps.Tap;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.sql.Array;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class IcmpDetector {

    private static final Logger LOG = LogManager.getLogger(IcmpDetector.class);

    private static final int MAXIMUM_REDIRECTED_DESTINATIONS = 100;
    private static final int MAXIMUM_ROUTERS = 64;

    // Classic UDP traceroute probes walk up from port 33434, one port per probe.
    private static final int TRACEROUTE_PORT_START = 33434;
    private static final int TRACEROUTE_PORT_END = 33534;

    // A run of distinct low TTL values from one source to one destination is a TCP traceroute even with no
    // router answering: no real TCP stack ramps a connection's TTL like that.
    private static final int TTL_RAMP_MINIMUM_DISTINCT = 3;
    private static final int TTL_RAMP_MAXIMUM_START = 2;
    private static final int TTL_RAMP_MAXIMUM_TTL = 64;

    private record TtlRamp(String sourceAddress, @Nullable String sourceMac, String destinationAddress,
                           long sessionCount, boolean destinationAnswered, Set<UUID> taps,
                           DateTime firstSeen, DateTime lastSeen) { }

    private final NzymeNode nzyme;
    private final ICMP icmp;

    public IcmpDetector(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.icmp = nzyme.getEthernet().icmp();
    }

    public void runForTenant(UUID organizationId, UUID tenantId) {
        IcmpConfiguration config = icmp.getConfiguration(organizationId, tenantId);

        icmp.retentionCleanDetections(organizationId, tenantId,
                DateTime.now().minusDays(config.detectionRetentionTimeDays()));

        if (!config.redirectDetectionEnabled() && !config.tracerouteDetectionEnabled() && !config.tunnelDetectionEnabled()) {
            return;
        }

        List<UUID> taps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)
                .stream().map(Tap::uuid).collect(Collectors.toList());

        if (taps.isEmpty()) {
            return;
        }

        DateTime until = DateTime.now();
        DateTime since = until.minusMinutes(config.detectionWindowMinutes());

        nzyme.getDatabase().useTransaction(handle -> {
            boolean locked = handle.createQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                    .bind("key", "icmp_detection:" + organizationId + ":" + tenantId)
                    .mapTo(Boolean.class)
                    .one();

            if (!locked) {
                return;
            }

            Map<IcmpExclusionEntry, IcmpExclusionMatcher> exclusions = icmp.exclusions()
                    .compileEnabled(handle, organizationId, tenantId);
            Map<Long, Long> exclusionMatches = new HashMap<>();

            if (config.redirectDetectionEnabled()) {
                try {
                    detectRedirects(handle, config, taps, since, until, organizationId, tenantId, exclusions, exclusionMatches);
                } catch (Exception e) {
                    LOG.error("Could not run ICMP redirect detection for tenant [{}/{}].", organizationId, tenantId, e);
                }
            }

            if (config.tracerouteDetectionEnabled()) {
                try {
                    detectTraceroutes(handle, config, taps, since, until, organizationId, tenantId, exclusions, exclusionMatches);
                } catch (Exception e) {
                    LOG.error("Could not run ICMP traceroute detection for tenant [{}/{}].", organizationId, tenantId, e);
                }
            }

            if (config.tunnelDetectionEnabled()) {
                try {
                    detectTunnels(handle, config, taps, since, until, organizationId, tenantId, exclusions, exclusionMatches);
                } catch (Exception e) {
                    LOG.error("Could not run ICMP tunnel detection for tenant [{}/{}].", organizationId, tenantId, e);
                }
            }

            for (Map.Entry<Long, Long> match : exclusionMatches.entrySet()) {
                icmp.exclusions().recordMatches(handle, match.getKey(), match.getValue());
            }
        });
    }

    private void detectRedirects(Handle handle,
                                 IcmpConfiguration config,
                                 List<UUID> taps,
                                 DateTime since,
                                 DateTime until,
                                 UUID organizationId,
                                 UUID tenantId,
                                 Map<IcmpExclusionEntry, IcmpExclusionMatcher> exclusions,
                                 Map<Long, Long> exclusionMatches) {
        handle.createQuery("SELECT host(source_address) AS sender_address, " +
                        "mode() WITHIN GROUP (ORDER BY source_mac) AS sender_mac, " +
                        "host(redirect_gateway) AS gateway_address, host(destination_address) AS target_address, " +
                        "mode() WITHIN GROUP (ORDER BY destination_mac) AS target_mac, " +
                        "SUM(packet_count) AS packet_count, " +
                        "array_agg(DISTINCT host(quoted_destination_address)) AS redirected_destinations, " +
                        "array_agg(DISTINCT tap_uuid::text) AS taps, " +
                        "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen, MAX(id) AS max_id " +
                        "FROM icmp_packets WHERE tap_uuid IN (<taps>) AND icmp_type = 5 " +
                        "AND redirect_gateway IS NOT NULL AND last_seen >= :since AND last_seen <= :until " +
                        "GROUP BY source_address, redirect_gateway, destination_address")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .mapToMap()
                .forEach(row -> {
                    try {
                        String senderAddress = (String) row.get("sender_address");
                        String gatewayAddress = (String) row.get("gateway_address");
                        String targetAddress = (String) row.get("target_address");
                        String senderMac = (String) row.get("sender_mac");

                        IcmpExclusionEntry exclusion = icmp.exclusions().findMatching(exclusions,
                                IcmpDetectionType.REDIRECT, senderAddress, senderMac, gatewayAddress);
                        if (exclusion != null) {
                            exclusionMatches.merge(exclusion.id(), 1L, Long::sum);
                            return;
                        }
                        List<String> destinations = stringArray(row.get("redirected_destinations")).stream()
                                .filter(Objects::nonNull).limit(MAXIMUM_REDIRECTED_DESTINATIONS).toList();
                        Set<UUID> tapUuids = uuidArray(row.get("taps"));
                        long packetCount = ((Number) row.get("packet_count")).longValue();
                        DateTime firstSeen = new DateTime(row.get("first_seen"));
                        DateTime lastSeen = new DateTime(row.get("last_seen"));

                        Optional<IcmpRedirectEntry> existing = icmp.findRedirectByIdentity(
                                handle, organizationId, tenantId, senderAddress, gatewayAddress, targetAddress
                        );

                        if (existing.isPresent()) {
                            // Window overlaps previous runs: count only rows with last_seen after the stored last_seen.
                            long newPackets = countRedirectPacketsAfter(handle, taps, senderAddress, gatewayAddress,
                                    targetAddress, existing.get().lastSeen(), until);
                            icmp.updateRedirect(handle, existing.get().id(), destinations, newPackets, tapUuids, lastSeen);
                            return;
                        }

                        IcmpRedirectEntry created = icmp.createRedirect(handle, organizationId, tenantId,
                                senderAddress, senderMac, gatewayAddress, targetAddress,
                                (String) row.get("target_mac"), destinations, packetCount, tapUuids, firstSeen, lastSeen);

                        if (config.alertOnRedirect()) {
                            raiseRedirectAlert(created, organizationId, tenantId);
                        }
                    } catch (Exception e) {
                        LOG.error("Could not process ICMP redirect candidate.", e);
                    }
                });
    }

    private long countRedirectPacketsAfter(Handle handle, List<UUID> taps, String sender, String gateway,
                                           String target, DateTime after, DateTime until) {
        return handle.createQuery("SELECT COALESCE(SUM(packet_count), 0) FROM icmp_packets WHERE tap_uuid IN (<taps>) " +
                        "AND icmp_type = 5 AND source_address = :sender::inet AND redirect_gateway = :gateway::inet " +
                        "AND destination_address = :target::inet AND last_seen > :after AND last_seen <= :until")
                .bindList("taps", taps)
                .bind("sender", sender)
                .bind("gateway", gateway)
                .bind("target", target)
                .bind("after", after)
                .bind("until", until)
                .mapTo(Long.class)
                .one();
    }

    private void detectTraceroutes(Handle handle,
                                   IcmpConfiguration config,
                                   List<UUID> taps,
                                   DateTime since,
                                   DateTime until,
                                   UUID organizationId,
                                   UUID tenantId,
                                   Map<IcmpExclusionEntry, IcmpExclusionMatcher> exclusions,
                                   Map<Long, Long> exclusionMatches) {
        Map<String, TtlRamp> ramps = readTcpTtlRamps(handle, taps, since, until);
        Set<String> handled = Sets.newHashSet();

        handle.createQuery("SELECT host(destination_address) AS source_address, " +
                        "mode() WITHIN GROUP (ORDER BY destination_mac) AS source_mac, " +
                        "host(quoted_destination_address) AS traced_destination, " +
                        "mode() WITHIN GROUP (ORDER BY quoted_protocol) AS protocol, " +
                        "array_agg(DISTINCT host(source_address)) AS routers, " +
                        "SUM(packet_count) AS packet_count, " +
                        "array_agg(DISTINCT tap_uuid::text) AS taps, " +
                        "bool_or(quoted_protocol = 17 AND quoted_destination_port BETWEEN :port_start AND :port_end) AS udp_traceroute_ports, " +
                        "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen " +
                        "FROM icmp_packets WHERE tap_uuid IN (<taps>) AND icmp_type = 11 " +
                        "AND quoted_destination_address IS NOT NULL AND last_seen >= :since AND last_seen <= :until " +
                        "GROUP BY destination_address, quoted_destination_address")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .bind("port_start", TRACEROUTE_PORT_START)
                .bind("port_end", TRACEROUTE_PORT_END)
                .mapToMap()
                .forEach(row -> {
                    try {
                        String sourceAddress = (String) row.get("source_address");
                        String destinationAddress = (String) row.get("traced_destination");
                        Integer protocol = row.get("protocol") == null ? null : ((Number) row.get("protocol")).intValue();
                        List<String> routers = stringArray(row.get("routers")).stream()
                                .filter(Objects::nonNull).limit(MAXIMUM_ROUTERS).toList();
                        Set<UUID> tapUuids = uuidArray(row.get("taps"));
                        long packetCount = ((Number) row.get("packet_count")).longValue();
                        DateTime firstSeen = new DateTime(row.get("first_seen"));
                        DateTime lastSeen = new DateTime(row.get("last_seen"));

                        String sourceMac = (String) row.get("source_mac");

                        IcmpExclusionEntry exclusion = icmp.exclusions().findMatching(exclusions,
                                IcmpDetectionType.TRACEROUTE, sourceAddress, sourceMac, null);
                        if (exclusion != null) {
                            exclusionMatches.merge(exclusion.id(), 1L, Long::sum);
                            return;
                        }

                        String pair = sourceAddress + "/" + destinationAddress;
                        TtlRamp ramp = ramps.get(pair);

                        // The destination itself answers the last probe with a port unreachable (UDP) or an echo reply.
                        boolean reached = destinationReached(handle, taps, sourceAddress, destinationAddress, since, until)
                                || (ramp != null && ramp.destinationAnswered());
                        boolean udpTraceroutePorts = Boolean.TRUE.equals(row.get("udp_traceroute_ports"));

                        // Accept a sub-threshold router count only when the trace is unmistakable anyway:
                        // reached, traceroute port range, or a TCP TTL ramp.
                        if (routers.size() < config.tracerouteMinimumRouters() && !reached && !udpTraceroutePorts && ramp == null) {
                            return;
                        }

                        handled.add(pair);
                        if (ramp != null) {
                            tapUuids.addAll(ramp.taps());
                            packetCount = Math.max(packetCount, ramp.sessionCount());
                        }

                        Optional<IcmpTracerouteEntry> existing = icmp.findActiveTraceroute(
                                handle, organizationId, tenantId, sourceAddress, destinationAddress, since
                        );

                        if (existing.isPresent()) {
                            Set<String> merged = new LinkedHashSet<>(existing.get().routers());
                            merged.addAll(routers);
                            icmp.updateTraceroute(handle, existing.get().id(), Lists.newArrayList(merged),
                                    packetCount, reached, tapUuids, lastSeen);
                            return;
                        }

                        IcmpTracerouteEntry created = icmp.createTraceroute(handle, organizationId, tenantId,
                                sourceAddress, sourceMac, destinationAddress, protocol, routers,
                                packetCount, reached, tapUuids, firstSeen, lastSeen);

                        if (config.alertOnTraceroute()) {
                            raiseTracerouteAlert(created, organizationId, tenantId);
                        }
                    } catch (Exception e) {
                        LOG.error("Could not process ICMP traceroute candidate.", e);
                    }
                });

        // TCP traceroutes where no router answered at all, or only the time exceeded messages were out of view.
        for (Map.Entry<String, TtlRamp> entry : ramps.entrySet()) {
            if (handled.contains(entry.getKey())) {
                continue;
            }

            TtlRamp ramp = entry.getValue();
            try {
                IcmpExclusionEntry exclusion = icmp.exclusions().findMatching(exclusions,
                        IcmpDetectionType.TRACEROUTE, ramp.sourceAddress(), ramp.sourceMac(), null);
                if (exclusion != null) {
                    exclusionMatches.merge(exclusion.id(), 1L, Long::sum);
                    continue;
                }

                Optional<IcmpTracerouteEntry> existing = icmp.findActiveTraceroute(
                        handle, organizationId, tenantId, ramp.sourceAddress(), ramp.destinationAddress(), since
                );

                if (existing.isPresent()) {
                    icmp.updateTraceroute(handle, existing.get().id(), existing.get().routers(), ramp.sessionCount(),
                            ramp.destinationAnswered(), ramp.taps(), ramp.lastSeen());
                    continue;
                }

                IcmpTracerouteEntry created = icmp.createTraceroute(handle, organizationId, tenantId,
                        ramp.sourceAddress(), ramp.sourceMac(), ramp.destinationAddress(), 6, Collections.emptyList(),
                        ramp.sessionCount(), ramp.destinationAnswered(), ramp.taps(), ramp.firstSeen(), ramp.lastSeen());

                if (config.alertOnTraceroute()) {
                    raiseTracerouteAlert(created, organizationId, tenantId);
                }
            } catch (Exception e) {
                LOG.error("Could not process TCP traceroute candidate.", e);
            }
        }
    }

    private Map<String, TtlRamp> readTcpTtlRamps(Handle handle, List<UUID> taps, DateTime since, DateTime until) {
        Map<String, TtlRamp> result = Maps.newHashMap();

        handle.createQuery("SELECT host(source_address) AS source_address, " +
                        "mode() WITHIN GROUP (ORDER BY source_mac) AS source_mac, " +
                        "host(destination_address) AS destination_address, COUNT(*) AS session_count, " +
                        "bool_or(state NOT IN ('SYNSENT', 'CLOSEDTIMEOUT', 'CLOSEDTIMEOUTNODE') OR bytes_rx_count > 0) AS answered, " +
                        "array_agg(DISTINCT tap_uuid::text) AS taps, " +
                        "MIN(start_time) AS first_seen, MAX(most_recent_segment_time) AS last_seen " +
                        "FROM l4_sessions WHERE tap_uuid IN (<taps>) AND l4_type = 'TCP' AND ip_ttl IS NOT NULL " +
                        "AND ip_ttl <= :maximum_ttl AND start_time >= :since AND start_time <= :until " +
                        "GROUP BY source_address, destination_address " +
                        "HAVING COUNT(DISTINCT ip_ttl) >= :minimum_distinct AND MIN(ip_ttl) <= :maximum_start")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .bind("maximum_ttl", TTL_RAMP_MAXIMUM_TTL)
                .bind("minimum_distinct", TTL_RAMP_MINIMUM_DISTINCT)
                .bind("maximum_start", TTL_RAMP_MAXIMUM_START)
                .mapToMap()
                .forEach(row -> {
                    try {
                        String source = (String) row.get("source_address");
                        String destination = (String) row.get("destination_address");
                        result.put(source + "/" + destination, new TtlRamp(
                                source,
                                (String) row.get("source_mac"),
                                destination,
                                ((Number) row.get("session_count")).longValue(),
                                Boolean.TRUE.equals(row.get("answered")),
                                uuidArray(row.get("taps")),
                                new DateTime(row.get("first_seen")),
                                new DateTime(row.get("last_seen"))
                        ));
                    } catch (Exception e) {
                        LOG.error("Could not read TCP TTL ramp candidate.", e);
                    }
                });

        return result;
    }

    private boolean destinationReached(Handle handle, List<UUID> taps, String source, String destination,
                                       DateTime since, DateTime until) {
        return handle.createQuery("SELECT COUNT(*) FROM icmp_packets WHERE tap_uuid IN (<taps>) " +
                        "AND source_address = :destination::inet AND destination_address = :source::inet " +
                        "AND ((icmp_type = 3 AND icmp_code = 3) OR icmp_type = 0) " +
                        "AND last_seen >= :since AND last_seen <= :until")
                .bindList("taps", taps)
                .bind("source", source)
                .bind("destination", destination)
                .bind("since", since)
                .bind("until", until)
                .mapTo(Long.class)
                .one() > 0;
    }

    private void raiseRedirectAlert(IcmpRedirectEntry redirect, UUID organizationId, UUID tenantId) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("redirect_uuid", redirect.uuid().toString());
        attributes.put("sender_address", redirect.senderAddress());
        if (redirect.senderMac() != null) {
            attributes.put("sender_mac", redirect.senderMac());
        }
        attributes.put("gateway_address", redirect.gatewayAddress());
        attributes.put("target_address", redirect.targetAddress());
        attributes.put("packet_count", String.valueOf(redirect.packetCount()));

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                null,
                DetectionType.ICMP_REDIRECT,
                Subsystem.ETHERNET,
                "ICMP redirect from [" + redirect.senderAddress() + "] telling [" + redirect.targetAddress()
                        + "] to route via [" + redirect.gatewayAddress() + "] detected.",
                attributes,
                Set.of("sender_address", "gateway_address", "target_address")
        );
    }

    private void detectTunnels(Handle handle,
                               IcmpConfiguration config,
                               List<UUID> taps,
                               DateTime since,
                               DateTime until,
                               UUID organizationId,
                               UUID tenantId,
                               Map<IcmpExclusionEntry, IcmpExclusionMatcher> exclusions,
                               Map<Long, Long> exclusionMatches) {
        handle.createQuery("SELECT host(source_address) AS source_address, " +
                        "mode() WITHIN GROUP (ORDER BY source_mac) AS source_mac, " +
                        "host(destination_address) AS destination_address, " +
                        "mode() WITHIN GROUP (ORDER BY destination_mac) AS destination_mac, " +
                        "MAX(bytes_max) AS max_payload, SUM(packet_count) AS packet_count, " +
                        "array_agg(DISTINCT tap_uuid::text) AS taps, " +
                        "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen " +
                        "FROM icmp_packets WHERE tap_uuid IN (<taps>) AND icmp_type = 8 " +
                        "AND last_seen >= :since AND last_seen <= :until " +
                        "GROUP BY source_address, destination_address " +
                        "HAVING MAX(bytes_max) >= :min_payload AND SUM(packet_count) >= :min_packets")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .bind("min_payload", config.tunnelMinimumPayloadBytes())
                .bind("min_packets", config.tunnelMinimumPackets())
                .mapToMap()
                .forEach(row -> {
                    try {
                        String sourceAddress = (String) row.get("source_address");
                        String sourceMac = (String) row.get("source_mac");
                        String destinationAddress = (String) row.get("destination_address");
                        String destinationMac = (String) row.get("destination_mac");

                        IcmpExclusionEntry exclusion = icmp.exclusions().findMatching(exclusions,
                                IcmpDetectionType.TUNNEL, sourceAddress, sourceMac, null);
                        if (exclusion != null) {
                            exclusionMatches.merge(exclusion.id(), 1L, Long::sum);
                            return;
                        }

                        long maxPayload = ((Number) row.get("max_payload")).longValue();
                        long packetCount = ((Number) row.get("packet_count")).longValue();
                        Set<UUID> tapUuids = uuidArray(row.get("taps"));
                        DateTime firstSeen = new DateTime(row.get("first_seen"));
                        DateTime lastSeen = new DateTime(row.get("last_seen"));

                        Optional<IcmpTunnelEntry> existing = icmp.findActiveTunnel(
                                handle, organizationId, tenantId, sourceAddress, destinationAddress, since
                        );

                        if (existing.isPresent()) {
                            icmp.updateTunnel(handle, existing.get().id(), maxPayload, packetCount, tapUuids, lastSeen);
                            return;
                        }

                        IcmpTunnelEntry created = icmp.createTunnel(handle, organizationId, tenantId, sourceAddress,
                                sourceMac, destinationAddress, destinationMac, maxPayload, packetCount, tapUuids,
                                firstSeen, lastSeen);

                        if (config.alertOnTunnel()) {
                            raiseTunnelAlert(created, organizationId, tenantId);
                        }
                    } catch (Exception e) {
                        LOG.error("Could not process ICMP tunnel candidate.", e);
                    }
                });
    }

    private void raiseTunnelAlert(IcmpTunnelEntry tunnel, UUID organizationId, UUID tenantId) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("tunnel_uuid", tunnel.uuid().toString());
        attributes.put("source_address", tunnel.sourceAddress());
        if (tunnel.sourceMac() != null) {
            attributes.put("source_mac", tunnel.sourceMac());
        }
        attributes.put("destination_address", tunnel.destinationAddress());
        attributes.put("max_payload_bytes", String.valueOf(tunnel.maxPayloadBytes()));

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                null,
                DetectionType.ICMP_TUNNEL,
                Subsystem.ETHERNET,
                "Possible ICMP tunnel from [" + tunnel.sourceAddress() + "] to [" + tunnel.destinationAddress()
                        + "] with echo payloads up to " + tunnel.maxPayloadBytes() + " bytes detected.",
                attributes,
                Set.of("tunnel_uuid")
        );
    }

    private void raiseTracerouteAlert(IcmpTracerouteEntry traceroute, UUID organizationId, UUID tenantId) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("traceroute_uuid", traceroute.uuid().toString());
        attributes.put("source_address", traceroute.sourceAddress());
        if (traceroute.sourceMac() != null) {
            attributes.put("source_mac", traceroute.sourceMac());
        }
        attributes.put("destination_address", traceroute.destinationAddress());
        attributes.put("router_count", String.valueOf(traceroute.routers().size()));

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                null,
                DetectionType.ICMP_TRACEROUTE,
                Subsystem.ETHERNET,
                "Traceroute from [" + traceroute.sourceAddress() + "] to [" + traceroute.destinationAddress()
                        + "] through " + traceroute.routers().size() + " routers detected.",
                attributes,
                Set.of("traceroute_uuid")
        );
    }

    private static List<String> stringArray(Object value) throws SQLException {
        List<String> result = Lists.newArrayList();
        if (value == null) {
            return result;
        }

        for (Object o : (Object[]) ((Array) value).getArray()) {
            result.add(o == null ? null : o.toString());
        }

        return result;
    }

    private static Set<UUID> uuidArray(Object value) throws SQLException {
        Set<UUID> result = Sets.newHashSet();
        for (String s : stringArray(value)) {
            if (s != null) {
                result.add(UUID.fromString(s));
            }
        }

        return result;
    }

}
