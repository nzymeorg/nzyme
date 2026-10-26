package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.DetectionType;
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

    private record Transaction(long transactionId, String clientMac, List<DHCPServerResponseEntry> responses,
                               @Nullable String selectedServerAddress, DateTime latestPacket, Set<UUID> taps) { }

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
        final String mac;
        final String address;
        String serverIdentifier;
        final Map<String, ConfigurationActivity> configurations = new LinkedHashMap<>();
        final Set<UUID> taps = Sets.newLinkedHashSet();
        DateTime firstSeen;
        DateTime lastSeen;
        DhcpOfferedParameters latest;
        DateTime latestAt;

        ServerActivity(String mac, String address) {
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

                if (!until.isAfter(after)) {
                    return;
                }

                List<Transaction> transactions = readTransactions(handle, taps, after, until);

                LOG.debug("Analyzing [{}] DHCP transactions with server responses of tenant [{}/{}].",
                        transactions.size(), organizationId, tenantId);

                Map<String, ServerActivity> activity = aggregate(transactions);
                processServers(handle, activity, config, until, organizationId, tenantId);

                spoofing.setDhcpProcessedUntil(handle, organizationId, tenantId, until);
            });
        }
    }

    /*
     * Copies of the same transaction from several taps share the transaction ID and have their first packet in
     * the same ten second bucket.
     */
    private List<Transaction> readTransactions(Handle handle, List<UUID> taps, DateTime after, DateTime until) {
        List<Transaction> result = Lists.newArrayList();

        handle.createQuery("SELECT transaction_id, MIN(client_mac) AS client_mac, " +
                        "FLOOR(EXTRACT(EPOCH FROM first_packet)::numeric / 10) AS time_bucket, " +
                        "jsonb_agg(server_responses) AS server_responses, " +
                        "MIN(selected_server_address) AS selected_server_address, " +
                        "MAX(latest_packet) AS latest_packet, string_agg(DISTINCT tap_uuid::text, ',') AS taps " +
                        "FROM dhcp_transactions WHERE tap_uuid IN (<taps>) " +
                        "AND latest_packet > :after AND latest_packet <= :until " +
                        "AND server_responses <> '[]'::jsonb " +
                        "GROUP BY transaction_id, time_bucket ORDER BY latest_packet ASC")
                .bindList("taps", taps)
                .bind("after", after)
                .bind("until", until)
                .map((rs, ctx) -> new Transaction(
                        rs.getLong("transaction_id"),
                        rs.getString("client_mac"),
                        DHCPServerResponses.fromJson(rs.getString("server_responses")),
                        rs.getString("selected_server_address"),
                        new DateTime(rs.getTimestamp("latest_packet")),
                        parseTaps(rs.getString("taps"))
                ))
                .forEach(result::add);

        return result;
    }

    private Map<String, ServerActivity> aggregate(List<Transaction> transactions) {
        Map<String, ServerActivity> servers = new LinkedHashMap<>();

        for (Transaction tx : transactions) {
            for (DHCPServerResponseEntry r : tx.responses) {
                if (r.serverMac() == null || r.serverAddress() == null) {
                    continue;
                }

                String key = identity(r.serverMac(), r.serverAddress());
                ServerActivity server = servers.computeIfAbsent(key, k -> new ServerActivity(r.serverMac(), r.serverAddress()));

                if (r.serverIdentifier() != null) {
                    server.serverIdentifier = r.serverIdentifier();
                }

                server.taps.addAll(tx.taps);
                server.firstSeen = earliest(server.firstSeen, r.firstSeen());
                server.lastSeen = latest(server.lastSeen, r.lastSeen());

                DhcpOfferedParameters parameters = DhcpOfferedParameters.fromResponse(r);
                if (server.latestAt == null || r.lastSeen().isAfter(server.latestAt)) {
                    server.latest = parameters;
                    server.latestAt = r.lastSeen();
                }

                ConfigurationActivity c = server.configurations
                        .computeIfAbsent(parameters.configurationKey(), k -> new ConfigurationActivity(parameters));

                boolean ack = "Ack".equalsIgnoreCase(r.messageType());
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
                if (ack || selected) {
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
                                UUID organizationId,
                                UUID tenantId) {
        if (activity.isEmpty()) {
            return;
        }

        Map<String, DhcpSpoofingServerEntry> servers = Maps.newHashMap();
        DateTime earliestServer = null;
        for (DhcpSpoofingServerEntry server : spoofing.findAllDhcpServersOfTenant(handle, organizationId, tenantId)) {
            servers.put(identity(server.mac(), server.address()), server);
            earliestServer = earliest(earliestServer, server.firstSeen());
        }

        // A restarted learning period also trusts servers that are already known but untrusted.
        DateTime learningRestartedAt = spoofing.findDhcpLearningStartedAt(handle, organizationId, tenantId)
                .orElse(null);
        DateTime learningStart = learningRestartedAt != null ? learningRestartedAt : earliestServer;

        DateTime learningEndedAt = spoofing.findDhcpLearningEndedAt(handle, organizationId, tenantId).orElse(null);

        DateTime since = until.minusMinutes(config.dhcpDetectionWindowMinutes());
        Map<String, DhcpSpoofingFindingEntry> activeFindings = Maps.newHashMap();
        for (DhcpSpoofingFindingEntry finding : spoofing.findActiveDhcpFindingsOfTenant(handle, organizationId, tenantId, since)) {
            activeFindings.put(findingIdentity(finding.type(), finding.serverMac(), finding.serverAddress()), finding);
        }

        Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> exclusions = spoofing.exclusions()
                .compileEnabled(handle, organizationId, tenantId);
        Map<Long, Long> exclusionMatches = Maps.newHashMap();

        // Oldest first, so that learning works out the same way regardless of how many runs were skipped.
        List<ServerActivity> ordered = Lists.newArrayList(activity.values());
        ordered.sort(Comparator.comparing(a -> a.firstSeen));

        for (ServerActivity a : ordered) {
            try {
                DhcpSpoofingServerEntry server = servers.get(identity(a.mac, a.address));

                if (server == null && learningStart == null) {
                    learningStart = a.firstSeen;
                }

                DateTime learningEnd = learningStart == null ? null
                        : earliest(learningStart.plusHours(config.dhcpLearningPeriodHours()), learningEndedAt);
                boolean inLearningPeriod = config.dhcpLearningPeriodHours() > 0
                        && learningStart != null
                        && !a.lastSeen.isBefore(learningStart)
                        && a.firstSeen.isBefore(learningEnd);

                if (server == null) {
                    boolean trusted = inLearningPeriod;

                    server = spoofing.createDhcpServer(
                            handle,
                            organizationId,
                            tenantId,
                            a.mac,
                            a.address,
                            a.serverIdentifier,
                            trusted,
                            trusted ? TRUST_SOURCE_LEARNED : null,
                            trusted ? a.latest : null,
                            a.firstSeen,
                            a.lastSeen
                    );
                    servers.put(identity(a.mac, a.address), server);
                } else if (!server.trusted() && learningRestartedAt != null && inLearningPeriod) {
                    spoofing.setDhcpServerLearned(handle, server.id());
                    spoofing.setDhcpServerBaseline(handle, server.id(), a.latest);
                    server = spoofing.findDhcpServerById(handle, server.id()).orElse(server);
                    servers.put(identity(a.mac, a.address), server);
                }

                spoofing.addDhcpServerActivity(
                        handle, server.id(), a.totalCounts(), a.latest, a.serverIdentifier,
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
                    spoofing.setDhcpServerBaseline(handle, server.id(), a.latest);
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
                    if (c.parameters.sameConfigurationAs(baseline)) {
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
        String key = findingIdentity(type, a.mac, a.address);
        DhcpSpoofingFindingEntry finding = activeFindings.get(key);

        boolean newFinding = false;
        if (finding == null) {
            finding = spoofing.createDhcpFinding(
                    handle, organizationId, tenantId, type, a.mac, a.address, a.serverIdentifier,
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

        if (newFinding && alert) {
            spoofing.findDhcpFindingById(handle, finding.id())
                    .ifPresent(refreshed -> raiseAlert(refreshed, organizationId, tenantId));
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

    private void raiseAlert(DhcpSpoofingFindingEntry finding, UUID organizationId, UUID tenantId) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("finding_uuid", finding.uuid().toString());
        attributes.put("finding_type", finding.type().name());
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
        }

        DetectionType detectionType;
        String details;
        if (finding.type() == DhcpSpoofingFindingType.ROGUE_SERVER) {
            detectionType = DetectionType.SPOOFING_DHCP_ROGUE_SERVER;
            details = "Untrusted DHCP server [" + finding.serverAddress() + "] (" + finding.serverMac() + ") is "
                    + "answering clients" + offeredSummary(finding.offered()) + ".";
        } else {
            detectionType = DetectionType.SPOOFING_DHCP_PARAMETERS_CHANGED;
            details = "Trusted DHCP server [" + finding.serverAddress() + "] (" + finding.serverMac() + ") is "
                    + "handing out a changed configuration" + offeredSummary(finding.offered()) + ".";
        }

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                null,
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
            parts.add("gateway " + String.join(", ", offered.routers()));
        }
        if (!offered.dnsServers().isEmpty()) {
            parts.add("DNS " + String.join(", ", offered.dnsServers()));
        }

        return parts.isEmpty() ? "" : " with " + String.join(" and ", parts);
    }

    private static String identity(String mac, String address) {
        return mac.toUpperCase() + "/" + address;
    }

    private static String findingIdentity(DhcpSpoofingFindingType type, String mac, String address) {
        return type.name() + "/" + identity(mac, address);
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
