package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.AlertOrigin;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.ethernet.ndp.NDP;
import app.nzyme.core.ethernet.dhcp.db.DHCPServerResponseEntry;
import app.nzyme.core.ethernet.dhcp.db.DHCPServerResponses;
import app.nzyme.core.ethernet.spoofing.db.DhcpSpoofingFindingEntry;
import app.nzyme.core.ethernet.spoofing.db.DhcpSpoofingServerEntry;
import app.nzyme.core.ethernet.spoofing.db.SpoofingExclusionEntry;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.MetricNames;
import app.nzyme.plugin.Subsystem;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.util.*;
import java.util.stream.Collectors;

public class DhcpSpoofingDetector {

    private static final Logger LOG = LogManager.getLogger(DhcpSpoofingDetector.class);

    private static final int SETTLE_MINUTES = 2;

    private static final int INITIAL_LOOKBACK_HOURS = 24;

    private static final String TRUST_SOURCE_LEARNED = "LEARNED";

    private final NzymeNode nzyme;
    private final Spoofing spoofing;

    private final Timer runTimer;

    public DhcpSpoofingDetector(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.spoofing = nzyme.getEthernet().spoofing();
        this.runTimer = nzyme.getMetrics().timer(MetricNames.SPOOFING_DHCP_DETECTION_RUN_TIMER);
    }

    // A router and a DHCPv6 server often share a MAC address and a link-local address, so the protocol is part of
    // what a server is.
    public static final String PROTOCOL_DHCP = "DHCP";
    public static final String PROTOCOL_DHCP6 = "DHCP6";
    public static final String PROTOCOL_ROUTER_ADVERTISEMENT = "RA";

    public static final List<String> PROTOCOLS = List.of(PROTOCOL_DHCP, PROTOCOL_DHCP6, PROTOCOL_ROUTER_ADVERTISEMENT);

    private static final String ROUTER_ADVERTISEMENT = "RouterAdvertisement";

    private static final int MAX_ROUTER_ADVERTISEMENTS = 10_000;

    // Per protocol. A flood of answers from made-up addresses would otherwise make every one of them a server.
    static final int MAX_NEW_SERVERS_PER_RUN = 50;
    public static final int MAX_UNTRUSTED_SERVERS = 200;
    static final int MAX_LEARNED_SERVERS = 250;

    private record Transaction(String protocol, long transactionId, @Nullable String clientMac,
                               List<DHCPServerResponseEntry> responses, @Nullable String selectedServerAddress,
                               DateTime latestPacket, Set<UUID> taps, @Nullable DhcpOfferedParameters parameters) { }

    private static class Victims {
        final Map<String, Spoofing.DhcpVictimRecord> byMac = Maps.newHashMap();

        void record(String clientMac, @Nullable String assignedAddress, boolean lease, DateTime seen) {
            merge(new Spoofing.DhcpVictimRecord(clientMac, assignedAddress, 1, lease ? 1 : 0, seen, seen));
        }

        void merge(Spoofing.DhcpVictimRecord v) {
            Spoofing.DhcpVictimRecord existing = byMac.get(v.mac());
            if (existing == null) {
                byMac.put(v.mac(), v);
                return;
            }

            byMac.put(v.mac(), new Spoofing.DhcpVictimRecord(
                    v.mac(),
                    v.assignedAddress() != null ? v.assignedAddress() : existing.assignedAddress(),
                    existing.transactionCount() + v.transactionCount(),
                    existing.leaseCount() + v.leaseCount(),
                    existing.firstSeen().isBefore(v.firstSeen()) ? existing.firstSeen() : v.firstSeen(),
                    existing.lastSeen().isAfter(v.lastSeen()) ? existing.lastSeen() : v.lastSeen()
            ));
        }
    }

    private static class ConfigurationActivity {
        final DhcpOfferedParameters parameters;
        long responseCount = 0;
        long offerCount = 0;
        long ackCount = 0;
        final Set<Long> transactions = Sets.newHashSet();
        final Victims victims = new Victims();
        DateTime firstSeen;
        DateTime lastSeen;

        ConfigurationActivity(DhcpOfferedParameters parameters) {
            this.parameters = parameters;
        }

        Spoofing.DhcpActivityCounts counts() {
            return new Spoofing.DhcpActivityCounts(responseCount, offerCount, ackCount, transactions.size());
        }
    }

    private static class ServerActivity {
        final String protocol;
        final String mac;
        final String address;
        String serverIdentifier;
        final Map<String, ConfigurationActivity> configurations = new LinkedHashMap<>();
        final Set<UUID> taps = Sets.newLinkedHashSet();
        DateTime firstSeen;
        DateTime lastSeen;
        DhcpOfferedParameters latest;
        DateTime latestAt;

        ServerActivity(String protocol, String mac, String address) {
            this.protocol = protocol;
            this.mac = mac;
            this.address = address;
        }

        Spoofing.DhcpActivityCounts totalCounts() {
            long responses = 0, offers = 0, acks = 0;
            Set<Long> transactions = Sets.newHashSet();
            for (ConfigurationActivity c : configurations.values()) {
                responses += c.responseCount;
                offers += c.offerCount;
                acks += c.ackCount;
                transactions.addAll(c.transactions);
            }

            return new Spoofing.DhcpActivityCounts(responses, offers, acks, transactions.size());
        }

        Victims allVictims() {
            Victims all = new Victims();
            for (ConfigurationActivity c : configurations.values()) {
                for (Spoofing.DhcpVictimRecord v : c.victims.byMac.values()) {
                    all.merge(v);
                }
            }

            return all;
        }
    }

    public void runForTenant(UUID organizationId, UUID tenantId) {
        SpoofingConfiguration config = spoofing.getConfiguration(organizationId, tenantId);

        spoofing.retentionCleanDhcp(organizationId, tenantId, DateTime.now().minusDays(config.dhcpRetentionTimeDays()));

        if (!config.dhcpDetectionEnabled()) {
            LOG.debug("DHCP spoofing detection disabled for tenant [{}/{}]. Skipping.", organizationId, tenantId);
            return;
        }

        List<UUID> taps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)
                .stream().map(Tap::uuid).collect(Collectors.toList());

        if (taps.isEmpty()) {
            LOG.debug("Tenant [{}/{}] has no taps. Skipping DHCP spoofing detection.", organizationId, tenantId);
            return;
        }

        try (Timer.Context ignored = runTimer.time()) {
            nzyme.getDatabase().useTransaction(handle -> {
                // Only one node of a cluster processes a tenant at a time.
                boolean locked = handle.createQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                        .bind("key", "spoofing_dhcp_detection:" + organizationId + ":" + tenantId)
                        .mapTo(Boolean.class)
                        .one();

                if (!locked) {
                    LOG.debug("DHCP spoofing detection for tenant [{}/{}] is running on another node. Skipping.",
                            organizationId, tenantId);
                    return;
                }

                DateTime until = DateTime.now().minusMinutes(SETTLE_MINUTES);
                DateTime after = spoofing.findDhcpProcessedUntil(handle, organizationId, tenantId)
                        .orElse(until.minusHours(INITIAL_LOOKBACK_HOURS));

                // Also in a run that sees nothing: on a quiet network the first thing seen may be the attacker.
                DateTime ipv6LearningStartedAt = spoofing.ipv6LearningStartedAt(handle, organizationId, tenantId);

                if (!until.isAfter(after)) {
                    return;
                }

                List<Transaction> transactions = readTransactions(handle, taps, after, until);
                List<Transaction> advertisements = readRouterAdvertisements(handle, taps, after, until);
                if (advertisements.size() >= MAX_ROUTER_ADVERTISEMENTS) {
                    LOG.warn("Tenant [{}/{}] has more than {} different router advertisements in one run. The rest "
                            + "is not judged. This is what a router advertisement flood looks like.",
                            organizationId, tenantId, MAX_ROUTER_ADVERTISEMENTS);
                }
                transactions.addAll(advertisements);

                LOG.debug("Analyzing [{}] DHCP transactions with server responses of tenant [{}/{}].",
                        transactions.size(), organizationId, tenantId);

                Map<String, ServerActivity> activity = aggregate(transactions);
                processServers(handle, activity, config, until, ipv6LearningStartedAt, organizationId, tenantId);

                spoofing.setDhcpProcessedUntil(handle, organizationId, tenantId, until);
            });
        }

        // After the transaction, so that the routers this run trusted are in.
        nzyme.getEthernet().internalAddresses().syncLearnedNetworks(organizationId, tenantId);
    }

    /*
     * Copies of the same transaction from several taps share the transaction ID and have their first packet in
     * the same ten second bucket.
     */
    private List<Transaction> readTransactions(Handle handle, List<UUID> taps, DateTime after, DateTime until) {
        List<Transaction> result = Lists.newArrayList();

        handle.createQuery("SELECT transaction_id, ip_version, MIN(client_mac) AS client_mac, " +
                        "FLOOR(EXTRACT(EPOCH FROM first_packet)::numeric / 10) AS time_bucket, " +
                        "jsonb_agg(server_responses) AS server_responses, " +
                        "MIN(selected_server_address) AS selected_server_address, " +
                        "MAX(latest_packet) AS latest_packet, string_agg(DISTINCT tap_uuid::text, ',') AS taps " +
                        "FROM dhcp_transactions WHERE tap_uuid IN (<taps>) " +
                        "AND latest_packet > :after AND latest_packet <= :until " +
                        "AND server_responses <> '[]'::jsonb " +
                        "GROUP BY transaction_id, time_bucket, ip_version ORDER BY latest_packet ASC")
                .bindList("taps", taps)
                .bind("after", after)
                .bind("until", until)
                .map((rs, ctx) -> new Transaction(
                        rs.getInt("ip_version") == 6 ? PROTOCOL_DHCP6 : PROTOCOL_DHCP,
                        rs.getLong("transaction_id"),
                        rs.getString("client_mac"),
                        DHCPServerResponses.fromJson(rs.getString("server_responses")),
                        rs.getString("selected_server_address"),
                        new DateTime(rs.getTimestamp("latest_packet")),
                        parseTaps(rs.getString("taps")),
                        null
                ))
                .forEach(result::add);

        return result;
    }

    // Messages that did not arrive with a hop limit of 255 are ignored, as hosts do.
    private List<Transaction> readRouterAdvertisements(Handle handle, List<UUID> taps, DateTime after, DateTime until) {
        String configuration = "managed, other_configuration, preference, router_lifetime, prefixes, dns_servers, "
                + "dns_search_list";

        // Taps that see the same link record the same advertisements: per minute, the tap that saw the most counts.
        return handle.createQuery("WITH per_tap AS (SELECT tap_uuid, " +
                        NDP.ROUTER_MAC + " AS mac, source_address, managed, " +
                        "other_configuration, preference, router_lifetime, prefixes::text AS prefixes, " +
                        "dns_servers::text AS dns_servers, dns_search_list::text AS dns_search_list, " +
                        "date_trunc('minute', last_seen) AS bucket, SUM(packet_count) AS packets, " +
                        "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen " +
                        "FROM ndp_router_advertisements WHERE tap_uuid IN (<taps>) AND hop_limit = 255 " +
                        "AND last_seen > :after AND last_seen <= :until " +
                        "AND " + NDP.ROUTER_MAC + " IS NOT NULL " +
                        "GROUP BY tap_uuid, mac, source_address, " + configuration + ", bucket) " +
                        "SELECT mac, host(source_address) AS address, " + configuration + ", " +
                        "EXTRACT(EPOCH FROM bucket)::bigint AS bucket, MAX(packets) AS packet_count, " +
                        "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen, " +
                        "string_agg(DISTINCT tap_uuid::text, ',') AS taps FROM per_tap " +
                        "GROUP BY mac, source_address, " + configuration + ", bucket " +
                        "ORDER BY MAX(last_seen) ASC, mac ASC LIMIT :limit")
                .bindList("taps", taps)
                .bind("after", after)
                .bind("until", until)
                .bind("limit", MAX_ROUTER_ADVERTISEMENTS)
                .map((rs, ctx) -> {
                    String address = rs.getString("address");
                    long lifetime = rs.getLong("router_lifetime");
                    List<String> prefixes = RouterAdvertisementJson.prefixes(rs.getString("prefixes"));
                    List<String> dnsServers = RouterAdvertisementJson.addresses(rs.getString("dns_servers"));
                    List<String> searchList = RouterAdvertisementJson.domains(rs.getString("dns_search_list"));

                    String flags = (rs.getBoolean("managed") ? "DHCPv6 addresses"
                            : rs.getBoolean("other_configuration") ? "SLAAC with DHCPv6 options" : "SLAAC")
                            + ", " + (lifetime > 0 ? "default router" : "no default router")
                            + ", " + preference(rs.getString("preference")) + " preference";

                    DhcpOfferedParameters parameters = DhcpOfferedParameters.create(
                            prefixes.isEmpty() ? null : String.join(", ", prefixes),
                            lifetime > 0 ? List.of(address) : List.of(),
                            dnsServers,
                            searchList.isEmpty() ? null : String.join(", ", searchList),
                            lifetime,
                            flags
                    );

                    DateTime lastSeen = new DateTime(rs.getTimestamp("last_seen"));
                    DHCPServerResponseEntry response = DHCPServerResponseEntry.create(
                            ROUTER_ADVERTISEMENT, rs.getString("mac"), address, null, null,
                            parameters.subnetMask(), parameters.routers(), dnsServers, parameters.domainName(),
                            lifetime, rs.getLong("packet_count"), new DateTime(rs.getTimestamp("first_seen")), lastSeen
                    );

                    return new Transaction(
                            PROTOCOL_ROUTER_ADVERTISEMENT,
                            rs.getLong("bucket"),
                            null,
                            List.of(response),
                            null,
                            lastSeen,
                            parseTaps(rs.getString("taps")),
                            parameters
                    );
                })
                .list();
    }

    private static String preference(@Nullable String preference) {
        return switch (preference == null ? "" : preference) {
            case "HIGH" -> "high";
            case "LOW" -> "low";
            default -> "medium";
        };
    }

    private Map<String, ServerActivity> aggregate(List<Transaction> transactions) {
        Map<String, ServerActivity> servers = new LinkedHashMap<>();

        for (Transaction tx : transactions) {
            for (DHCPServerResponseEntry r : tx.responses) {
                if (r.serverMac() == null || r.serverAddress() == null) {
                    continue;
                }

                String key = identity(tx.protocol, r.serverMac(), r.serverAddress());
                ServerActivity server = servers.computeIfAbsent(key,
                        k -> new ServerActivity(tx.protocol, r.serverMac(), r.serverAddress()));

                // A DHCPv6 server identifies itself with a DUID, which is no address.
                if (r.serverIdentifier() != null && InetAddresses.isInetAddress(r.serverIdentifier())) {
                    server.serverIdentifier = r.serverIdentifier();
                }

                server.taps.addAll(tx.taps);
                server.firstSeen = earliest(server.firstSeen, r.firstSeen());
                server.lastSeen = latest(server.lastSeen, r.lastSeen());

                // The latest known value of every option, since clients ask for different options.
                DhcpOfferedParameters parameters = tx.parameters != null
                        ? tx.parameters : DhcpOfferedParameters.fromResponse(r);
                if (server.latestAt == null || r.lastSeen().isAfter(server.latestAt)) {
                    server.latest = parameters.mergedWith(server.latest);
                    server.latestAt = r.lastSeen();
                } else {
                    server.latest = server.latest.mergedWith(parameters);
                }

                ConfigurationActivity c = server.configurations
                        .computeIfAbsent(parameters.configurationKey(), k -> new ConfigurationActivity(parameters));

                boolean ack = "Ack".equalsIgnoreCase(r.messageType()) || "Reply".equalsIgnoreCase(r.messageType());
                c.responseCount += r.count();
                if (ack) {
                    c.ackCount += r.count();
                } else {
                    c.offerCount += r.count();
                }
                c.transactions.add(tx.transactionId);
                c.firstSeen = earliest(c.firstSeen, r.firstSeen());
                c.lastSeen = latest(c.lastSeen, r.lastSeen());

                // A client that only received an offer and went with another server is not a victim.
                boolean selected = tx.selectedServerAddress != null
                        && (tx.selectedServerAddress.equals(r.serverAddress())
                        || tx.selectedServerAddress.equals(r.serverIdentifier()));
                if (tx.clientMac != null && (ack || selected)) {
                    c.victims.record(tx.clientMac, r.offeredAddress(), ack, r.lastSeen());
                }
            }
        }

        return servers;
    }

    private void processServers(Handle handle,
                                Map<String, ServerActivity> activity,
                                SpoofingConfiguration config,
                                DateTime until,
                                DateTime ipv6LearningStartedAt,
                                UUID organizationId,
                                UUID tenantId) {
        if (activity.isEmpty()) {
            return;
        }

        Map<String, DhcpSpoofingServerEntry> servers = Maps.newHashMap();
        Map<String, DateTime> earliestOfProtocol = Maps.newHashMap();
        for (DhcpSpoofingServerEntry server : spoofing.findAllDhcpServersOfTenant(handle, organizationId, tenantId)) {
            servers.put(identity(server.protocol(), server.mac(), server.address()), server);
            earliestOfProtocol.merge(server.protocol(), server.firstSeen(), DhcpSpoofingDetector::earliest);
        }

        // A restarted learning period also trusts servers that are already known but untrusted.
        DateTime learningRestartedAt = spoofing.findDhcpLearningStartedAt(handle, organizationId, tenantId)
                .orElse(null);

        DateTime learningEndedAt = spoofing.findDhcpLearningEndedAt(handle, organizationId, tenantId).orElse(null);

        DateTime since = until.minusMinutes(config.dhcpDetectionWindowMinutes());
        Map<String, DhcpSpoofingFindingEntry> activeFindings = Maps.newHashMap();
        for (DhcpSpoofingFindingEntry finding : spoofing.findActiveDhcpFindingsOfTenant(handle, organizationId, tenantId, since)) {
            activeFindings.put(findingIdentity(finding.type(), finding.protocol(), finding.serverMac(),
                    finding.serverAddress()), finding);
        }

        Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> exclusions = spoofing.exclusions()
                .compileEnabled(handle, organizationId, tenantId);
        Map<Long, Long> exclusionMatches = Maps.newHashMap();

        // Oldest first, so that learning works out the same way regardless of how many runs were skipped.
        List<ServerActivity> ordered = Lists.newArrayList(activity.values());
        ordered.sort(Comparator.comparing(a -> a.firstSeen));

        Map<String, Integer> newServers = Maps.newHashMap();
        Map<String, Integer> untrustedServers = Maps.newHashMap();
        Map<String, Integer> trustedServers = Maps.newHashMap();
        for (DhcpSpoofingServerEntry s : servers.values()) {
            (s.trusted() ? trustedServers : untrustedServers).merge(s.protocol(), 1, Integer::sum);
        }
        int skippedServers = 0;

        for (ServerActivity a : ordered) {
            try {
                DhcpSpoofingServerEntry server = servers.get(identity(a.protocol, a.mac, a.address));

                if (server == null) {
                    if (newServers.getOrDefault(a.protocol, 0) >= MAX_NEW_SERVERS_PER_RUN
                            || untrustedServers.getOrDefault(a.protocol, 0) >= MAX_UNTRUSTED_SERVERS) {
                        skippedServers++;
                        continue;
                    }

                    newServers.merge(a.protocol, 1, Integer::sum);
                }
                DhcpOfferedParameters latest = a.latest.mergedWith(server == null ? null : server.latest());

                Spoofing.DhcpLearningPeriod learning = Spoofing.dhcpLearningPeriod(
                        Spoofing.dhcpLearningStart(
                                a.protocol,
                                earliestOfProtocol.computeIfAbsent(a.protocol, k -> a.firstSeen),
                                ipv6LearningStartedAt
                        ),
                        learningRestartedAt,
                        learningEndedAt,
                        config.dhcpLearningPeriodHours()
                );
                boolean inLearningPeriod = config.dhcpLearningPeriodHours() > 0
                        && !a.lastSeen.isBefore(learning.start())
                        && a.firstSeen.isBefore(learning.end());

                if (server == null) {
                    boolean trusted = inLearningPeriod
                            && trustedServers.getOrDefault(a.protocol, 0) < MAX_LEARNED_SERVERS;
                    (trusted ? trustedServers : untrustedServers).merge(a.protocol, 1, Integer::sum);

                    server = spoofing.createDhcpServer(
                            handle,
                            organizationId,
                            tenantId,
                            a.protocol,
                            a.mac,
                            a.address,
                            a.serverIdentifier,
                            trusted,
                            trusted ? TRUST_SOURCE_LEARNED : null,
                            trusted ? latest : null,
                            a.firstSeen,
                            a.lastSeen
                    );
                    servers.put(identity(a.protocol, a.mac, a.address), server);
                } else if (!server.trusted() && learningRestartedAt != null && inLearningPeriod) {
                    spoofing.setDhcpServerLearned(handle, server.id());
                    spoofing.setDhcpServerBaseline(handle, server.id(), latest);
                    server = spoofing.findDhcpServerById(handle, server.id()).orElse(server);
                    servers.put(identity(a.protocol, a.mac, a.address), server);
                }

                spoofing.addDhcpServerActivity(
                        handle, server.id(), a.totalCounts(), latest, a.serverIdentifier,
                        server.tapUuids(), a.taps, a.firstSeen, a.lastSeen
                );

                SpoofingExclusionEntry exclusion = findMatchingExclusion(exclusions, a);
                if (exclusion != null) {
                    exclusionMatches.merge(exclusion.id(), 1L, Long::sum);
                    continue;
                }

                if (!server.trusted()) {
                    recordFinding(
                            handle, DhcpSpoofingFindingType.ROGUE_SERVER, a, a.totalCounts(), a.latest, null,
                            a.allVictims(), a.firstSeen, a.lastSeen, activeFindings, config, organizationId, tenantId
                    );
                    continue;
                }

                DhcpOfferedParameters baseline = server.baseline();
                if (baseline == null) {
                    spoofing.setDhcpServerBaseline(handle, server.id(), latest);
                    continue;
                }

                Spoofing.DhcpActivityCounts deviating = new Spoofing.DhcpActivityCounts(0, 0, 0, 0);
                Victims deviatingVictims = new Victims();
                DhcpOfferedParameters deviatingLatest = null;
                DateTime deviatingLatestAt = null;
                DateTime deviatingFirst = null;
                DateTime deviatingLast = null;
                Set<Long> deviatingTransactions = Sets.newHashSet();
                for (ConfigurationActivity c : a.configurations.values()) {
                    if (!c.parameters.deviatesFrom(baseline)) {
                        continue;
                    }

                    deviating = new Spoofing.DhcpActivityCounts(
                            deviating.responseCount() + c.responseCount,
                            deviating.offerCount() + c.offerCount,
                            deviating.ackCount() + c.ackCount,
                            0
                    );
                    deviatingTransactions.addAll(c.transactions);
                    for (Spoofing.DhcpVictimRecord v : c.victims.byMac.values()) {
                        deviatingVictims.merge(v);
                    }
                    if (deviatingLatestAt == null || c.lastSeen.isAfter(deviatingLatestAt)) {
                        deviatingLatest = c.parameters;
                        deviatingLatestAt = c.lastSeen;
                    }
                    deviatingFirst = earliest(deviatingFirst, c.firstSeen);
                    deviatingLast = latest(deviatingLast, c.lastSeen);
                }

                if (deviatingLatest == null) {
                    continue;
                }

                recordFinding(
                        handle, DhcpSpoofingFindingType.PARAMETERS_CHANGED, a,
                        new Spoofing.DhcpActivityCounts(deviating.responseCount(), deviating.offerCount(),
                                deviating.ackCount(), deviatingTransactions.size()),
                        deviatingLatest, baseline, deviatingVictims, deviatingFirst, deviatingLast,
                        activeFindings, config, organizationId, tenantId
                );
            } catch (Exception e) {
                LOG.error("Could not process DHCP server [{}/{}] of tenant [{}/{}]. Skipping.",
                        a.mac, a.address, organizationId, tenantId, e);
            }
        }

        if (skippedServers > 0) {
            LOG.warn("Not recording [{}] new DHCP servers, DHCPv6 servers or IPv6 routers of tenant [{}/{}]: more "
                            + "than {} new ones of a protocol in one run or more than {} untrusted ones. This is "
                            + "what a flood of DHCP answers or router advertisements looks like.",
                    skippedServers, organizationId, tenantId, MAX_NEW_SERVERS_PER_RUN, MAX_UNTRUSTED_SERVERS);
        }

        for (Map.Entry<Long, Long> match : exclusionMatches.entrySet()) {
            spoofing.exclusions().recordMatches(handle, match.getKey(), match.getValue());
        }
    }

    private void recordFinding(Handle handle,
                               DhcpSpoofingFindingType type,
                               ServerActivity a,
                               Spoofing.DhcpActivityCounts counts,
                               @Nullable DhcpOfferedParameters offered,
                               @Nullable DhcpOfferedParameters expected,
                               Victims victims,
                               DateTime firstSeen,
                               DateTime lastSeen,
                               Map<String, DhcpSpoofingFindingEntry> activeFindings,
                               SpoofingConfiguration config,
                               UUID organizationId,
                               UUID tenantId) {
        String key = findingIdentity(type, a.protocol, a.mac, a.address);
        DhcpSpoofingFindingEntry finding = activeFindings.get(key);

        boolean newFinding = false;
        if (finding == null) {
            finding = spoofing.createDhcpFinding(
                    handle, organizationId, tenantId, type, a.protocol, a.mac, a.address, a.serverIdentifier,
                    offered, expected, firstSeen, lastSeen
            );
            activeFindings.put(key, finding);
            newFinding = true;
        }

        spoofing.upsertDhcpVictims(handle, finding.id(), victims.byMac.values());
        spoofing.addDhcpFindingActivity(
                handle, finding.id(), counts, offered, a.serverIdentifier, finding.tapUuids(), a.taps, lastSeen
        );

        boolean alert = type == DhcpSpoofingFindingType.ROGUE_SERVER
                ? config.dhcpAlertOnRogueServer() : config.dhcpAlertOnParametersChanged();

        if (alert && (newFinding || AlertOrigin.seenElsewhere(finding.tapUuids(), a.taps))) {
            List<UUID> alertedTaps = newFinding ? List.of() : finding.tapUuids();
            spoofing.findDhcpFindingById(handle, finding.id())
                    .ifPresent(refreshed -> raiseAlert(refreshed, alertedTaps, organizationId, tenantId));
        }
    }

    @Nullable
    private static SpoofingExclusionEntry findMatchingExclusion(Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> exclusions,
                                                                ServerActivity a) {
        for (Map.Entry<SpoofingExclusionEntry, SpoofingExclusionMatcher> e : exclusions.entrySet()) {
            if (e.getValue().matches(SpoofingType.DHCP, a.address, a.mac)) {
                return e.getKey();
            }
        }

        return null;
    }

    private void raiseAlert(DhcpSpoofingFindingEntry finding,
                            List<UUID> alertedTaps,
                            UUID organizationId,
                            UUID tenantId) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("finding_uuid", finding.uuid().toString());
        attributes.put("finding_type", finding.type().name());
        attributes.put("protocol", finding.protocol());
        attributes.put("server_mac", finding.serverMac());
        attributes.put("server_address", finding.serverAddress());
        attributes.put("victim_count", String.valueOf(finding.victimCount()));
        attributes.put("transaction_count", String.valueOf(finding.transactionCount()));

        if (finding.offered() != null) {
            attributes.put("routers", String.join(",", finding.offered().routers()));
            attributes.put("dns_servers", String.join(",", finding.offered().dnsServers()));
            attributes.put("domain_name", finding.offered().domainName() == null ? "" : finding.offered().domainName());
        }

        if (finding.expected() != null) {
            attributes.put("expected_routers", String.join(",", finding.expected().routers()));
            attributes.put("expected_dns_servers", String.join(",", finding.expected().dnsServers()));
            attributes.put("expected_domain_name", finding.expected().domainName() == null ? "" : finding.expected().domainName());
            attributes.put("expected_prefixes", finding.expected().subnetMask() == null ? "" : finding.expected().subnetMask());
            attributes.put("expected_flags", finding.expected().flags() == null ? "" : finding.expected().flags());
        }

        if (finding.offered() != null) {
            attributes.put("prefixes", finding.offered().subnetMask() == null ? "" : finding.offered().subnetMask());
            attributes.put("flags", finding.offered().flags() == null ? "" : finding.offered().flags());
        }

        boolean rogue = finding.type() == DhcpSpoofingFindingType.ROGUE_SERVER;
        DetectionType detectionType;
        String details;
        if (PROTOCOL_ROUTER_ADVERTISEMENT.equals(finding.protocol())) {
            detectionType = rogue ? DetectionType.SPOOFING_RA_ROGUE_ROUTER
                    : DetectionType.SPOOFING_RA_PARAMETERS_CHANGED;
            details = (rogue ? "Untrusted IPv6 router [" : "Trusted IPv6 router [") + finding.serverAddress() + "] ("
                    + finding.serverMac() + (rogue ? ") is sending router advertisements"
                    : ") is advertising a changed configuration") + advertisedSummary(finding.offered()) + ".";
        } else {
            String server = PROTOCOL_DHCP6.equals(finding.protocol()) ? "DHCPv6 server [" : "DHCP server [";
            detectionType = rogue ? DetectionType.SPOOFING_DHCP_ROGUE_SERVER
                    : DetectionType.SPOOFING_DHCP_PARAMETERS_CHANGED;
            details = (rogue ? "Untrusted " : "Trusted ") + server + finding.serverAddress() + "] ("
                    + finding.serverMac() + (rogue ? ") is answering clients"
                    : ") is handing out a changed configuration") + offeredSummary(finding.offered()) + ".";
        }

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                AlertOrigin.taps(finding.tapUuids()).excludingLocationsOf(alertedTaps),
                detectionType,
                Subsystem.ETHERNET,
                details,
                attributes,
                Set.of("finding_uuid")
        );
    }

    private static String offeredSummary(@Nullable DhcpOfferedParameters offered) {
        if (offered == null) {
            return "";
        }

        List<String> parts = Lists.newArrayList();
        if (!offered.routers().isEmpty()) {
            parts.add("gateway " + firstFew(offered.routers()));
        }
        if (!offered.dnsServers().isEmpty()) {
            parts.add("DNS " + firstFew(offered.dnsServers()));
        }

        return parts.isEmpty() ? "" : " with " + String.join(" and ", parts);
    }

    private static String firstFew(List<String> values) {
        int shown = Math.min(values.size(), 3);
        String text = String.join(", ", values.subList(0, shown));

        return values.size() > shown ? text + " and " + (values.size() - shown) + " more" : text;
    }

    private static String advertisedSummary(@Nullable DhcpOfferedParameters advertised) {
        if (advertised == null) {
            return "";
        }

        List<String> parts = Lists.newArrayList();
        if (advertised.subnetMask() != null) {
            parts.add("prefix " + firstFew(List.of(advertised.subnetMask().split(", "))));
        }
        if (!advertised.dnsServers().isEmpty()) {
            parts.add("DNS " + firstFew(advertised.dnsServers()));
        }

        return parts.isEmpty() ? "" : " with " + String.join(" and ", parts);
    }

    private static String identity(String protocol, String mac, String address) {
        return protocol + "/" + mac.toUpperCase() + "/" + address;
    }

    private static String findingIdentity(DhcpSpoofingFindingType type, String protocol, String mac, String address) {
        return type.name() + "/" + identity(protocol, mac, address);
    }

    @Nullable
    private static DateTime earliest(@Nullable DateTime a, @Nullable DateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    @Nullable
    private static DateTime latest(@Nullable DateTime a, @Nullable DateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static Set<UUID> parseTaps(@Nullable String taps) {
        Set<UUID> result = Sets.newLinkedHashSet();
        if (taps == null || taps.isBlank()) {
            return result;
        }

        for (String t : taps.split(",")) {
            try {
                result.add(UUID.fromString(t.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return result;
    }

}
