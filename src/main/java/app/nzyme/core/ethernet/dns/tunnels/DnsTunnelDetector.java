package app.nzyme.core.ethernet.dns.tunnels;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.ethernet.dns.tunnels.db.DnsTunnelExclusionEntry;
import app.nzyme.core.ethernet.dns.tunnels.db.DnsTunnelFindingEntry;
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

public class DnsTunnelDetector {

    private static final Logger LOG = LogManager.getLogger(DnsTunnelDetector.class);

    // The window ends this many seconds in the past so that all taps have had the chance to report.
    private static final int SETTLE_SECONDS = 30;

    // A reply to a query is looked for this many seconds after the query.
    private static final int REPLY_WINDOW_SECONDS = 5;

    private static final int NAMES_LIMIT = 100;
    private static final int SERVER_ADDRESSES_LIMIT = 25;
    private static final int ALERT_SAMPLE = 10;

    private static final Set<String> UNUSUAL_RECORD_TYPES = Set.of("TXT", "NULL", "CNAME", "MX", "KEY", "SRV");

    private static final String BROADCAST_MAC = "FF:FF:FF:FF:FF:FF";
    private static final String NULL_MAC = "00:00:00:00:00:00";

    private final NzymeNode nzyme;
    private final DnsTunnels tunnels;

    private final Timer runTimer;

    public DnsTunnelDetector(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.tunnels = nzyme.getEthernet().dnsTunnels();
        this.runTimer = nzyme.getMetrics().timer(MetricNames.DNS_TUNNEL_DETECTION_RUN_TIMER);
    }

    private record Candidate(String clientAddress, String domain) { }

    private record QueryRow(UUID tap,
                            @Nullable String clientMac,
                            int clientPort,
                            @Nullable Integer transactionId,
                            String serverAddress,
                            String name,
                            String dataType,
                            @Nullable Float entropy,
                            int subdomainLength,
                            DateTime timestamp) { }

    private record Stats(long queryCount,
                         long distinctNameCount,
                         double averageEntropy,
                         double maximumEntropy,
                         double averageNameLength,
                         int maximumNameLength,
                         Map<String, Long> recordTypes,
                         List<String> names,
                         List<String> serverAddresses,
                         @Nullable String clientMac,
                         Set<UUID> taps,
                         DateTime firstSeen,
                         DateTime lastSeen) { }

    public void runForTenant(UUID organizationId, UUID tenantId) {
        DnsTunnelConfiguration config = tunnels.getConfiguration(organizationId, tenantId);

        tunnels.retentionCleanFindings(organizationId, tenantId, DateTime.now().minusDays(config.retentionTimeDays()));

        if (!config.detectionEnabled()) {
            LOG.debug("DNS tunnel detection disabled for tenant [{}/{}]. Skipping.", organizationId, tenantId);
            return;
        }

        List<UUID> taps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)
                .stream().map(Tap::uuid).collect(Collectors.toList());

        if (taps.isEmpty()) {
            LOG.debug("Tenant [{}/{}] has no taps. Skipping DNS tunnel detection.", organizationId, tenantId);
            return;
        }

        DateTime until = DateTime.now().minusSeconds(SETTLE_SECONDS);
        DateTime since = until.minusMinutes(config.detectionWindowMinutes());

        try (Timer.Context ignored = runTimer.time()) {
            nzyme.getDatabase().useTransaction(handle -> {
                // Only one node of a cluster processes a tenant at a time.
                boolean locked = handle.createQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                        .bind("key", "dns_tunnel_detection:" + organizationId + ":" + tenantId)
                        .mapTo(Boolean.class)
                        .one();

                if (!locked) {
                    LOG.debug("DNS tunnel detection for tenant [{}/{}] is running on another node. Skipping.",
                            organizationId, tenantId);
                    return;
                }

                List<Candidate> candidates = readCandidates(handle, taps, config, since, until);

                LOG.debug("Analyzing [{}] DNS tunnel candidates of tenant [{}/{}].",
                        candidates.size(), organizationId, tenantId);

                processCandidates(handle, taps, candidates, config, since, until, organizationId, tenantId);
            });
        }
    }

    private List<Candidate> readCandidates(Handle handle,
                                           List<UUID> taps,
                                           DnsTunnelConfiguration config,
                                           DateTime since,
                                           DateTime until) {
        return handle.createQuery("SELECT host(client_address) AS client_address, data_value_etld AS domain " +
                        "FROM dns_log WHERE tap_uuid IN (<taps>) AND dns_type = 'query' " +
                        "AND timestamp > :since AND timestamp <= :until " +
                        "AND data_value_etld IS NOT NULL AND data_value IS NOT NULL " +
                        "AND data_value <> data_value_etld AND data_value LIKE ('%.' || data_value_etld) " +
                        "AND client_address_is_multicast = false AND server_address_is_multicast = false " +
                        "GROUP BY client_address, data_value_etld " +
                        "HAVING COUNT(DISTINCT data_value) >= :minimum_distinct_names")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .bind("minimum_distinct_names", config.minimumDistinctNames())
                .map((rs, ctx) -> new Candidate(rs.getString("client_address"), rs.getString("domain")))
                .list();
    }

    private void processCandidates(Handle handle,
                                   List<UUID> taps,
                                   List<Candidate> candidates,
                                   DnsTunnelConfiguration config,
                                   DateTime since,
                                   DateTime until,
                                   UUID organizationId,
                                   UUID tenantId) {
        if (candidates.isEmpty()) {
            return;
        }

        Map<String, DnsTunnelFindingEntry> activeFindings = Maps.newHashMap();
        for (DnsTunnelFindingEntry finding : tunnels.findActiveFindingsOfTenant(handle, organizationId, tenantId, since)) {
            activeFindings.put(identity(finding.clientAddress(), finding.domain()), finding);
        }

        Map<DnsTunnelExclusionEntry, DnsTunnelExclusionMatcher> exclusions = tunnels.exclusions()
                .compileEnabled(handle, organizationId, tenantId);
        Map<Long, Long> exclusionMatches = Maps.newHashMap();

        for (Candidate candidate : candidates) {
            try {
                DnsTunnelExclusionEntry exclusion = findMatchingExclusion(exclusions, candidate);
                if (exclusion != null) {
                    exclusionMatches.merge(exclusion.id(), 1L, Long::sum);
                    continue;
                }

                DnsTunnelFindingEntry finding = activeFindings.get(identity(candidate.clientAddress, candidate.domain));

                // An open finding only gets the queries it has not counted yet.
                DateTime after = finding == null ? since : finding.lastSeen();

                List<QueryRow> rows = readQueries(handle, taps, candidate, after, until);
                if (rows.isEmpty()) {
                    continue;
                }

                Stats stats = aggregate(rows, finding);
                Set<String> indicators = Sets.newLinkedHashSet();
                if (finding != null) {
                    indicators.addAll(finding.indicators());
                }
                indicators.addAll(evaluateIndicators(stats, config));

                boolean newFinding = false;
                if (finding == null) {
                    if (indicators.isEmpty()) {
                        continue;
                    }

                    finding = tunnels.createFinding(
                            handle,
                            organizationId,
                            tenantId,
                            candidate.clientAddress,
                            stats.clientMac,
                            candidate.domain,
                            stats.firstSeen,
                            stats.lastSeen
                    );
                    activeFindings.put(identity(candidate.clientAddress, candidate.domain), finding);
                    newFinding = true;
                }

                long negativeCount = readNegativeReplyCount(handle, taps, candidate, after, until);

                Set<UUID> allTaps = Sets.newLinkedHashSet(finding.tapUuids());
                allTaps.addAll(stats.taps);

                tunnels.addFindingActivity(handle, finding.id(), new DnsTunnels.Activity(
                        stats.queryCount,
                        stats.distinctNameCount,
                        negativeCount,
                        stats.averageEntropy,
                        stats.maximumEntropy,
                        stats.averageNameLength,
                        stats.maximumNameLength,
                        stats.recordTypes,
                        stats.names,
                        Lists.newArrayList(indicators),
                        stats.serverAddresses,
                        stats.clientMac,
                        allTaps,
                        stats.lastSeen
                ));

                if (newFinding && config.alertOnNew()) {
                    tunnels.findFindingById(handle, finding.id())
                            .ifPresent(refreshed -> raiseNewFindingAlert(refreshed, organizationId, tenantId));
                }
            } catch (Exception e) {
                LOG.error("Could not process DNS tunnel candidate [{}/{}] of tenant [{}/{}]. Skipping.",
                        candidate.clientAddress, candidate.domain, organizationId, tenantId, e);
            }
        }

        for (Map.Entry<Long, Long> match : exclusionMatches.entrySet()) {
            tunnels.exclusions().recordMatches(handle, match.getKey(), match.getValue());
        }
    }

    private List<QueryRow> readQueries(Handle handle, List<UUID> taps, Candidate candidate, DateTime after, DateTime until) {
        return handle.createQuery("SELECT tap_uuid, client_mac, client_port, transaction_id, " +
                        "host(server_address) AS server_address, data_value, data_type, data_value_entropy, " +
                        "length(data_value) - length(data_value_etld) - 1 AS subdomain_length, timestamp " +
                        "FROM dns_log WHERE tap_uuid IN (<taps>) AND dns_type = 'query' " +
                        "AND timestamp > :after AND timestamp <= :until " +
                        "AND client_address = :client_address::inet AND data_value_etld = :domain " +
                        "AND data_value IS NOT NULL AND data_value <> data_value_etld " +
                        "AND data_value LIKE ('%.' || data_value_etld) ORDER BY timestamp ASC")
                .bindList("taps", taps)
                .bind("after", after)
                .bind("until", until)
                .bind("client_address", candidate.clientAddress)
                .bind("domain", candidate.domain)
                .map((rs, ctx) -> {
                    Float entropy = rs.getObject("data_value_entropy") == null ? null : rs.getFloat("data_value_entropy");
                    Integer transactionId = rs.getObject("transaction_id") == null ? null : rs.getInt("transaction_id");

                    return new QueryRow(
                            UUID.fromString(rs.getString("tap_uuid")),
                            rs.getString("client_mac"),
                            rs.getInt("client_port"),
                            transactionId,
                            rs.getString("server_address"),
                            rs.getString("data_value"),
                            rs.getString("data_type"),
                            entropy,
                            rs.getInt("subdomain_length"),
                            new DateTime(rs.getTimestamp("timestamp"))
                    );
                })
                .list();
    }

    private long readNegativeReplyCount(Handle handle, List<UUID> taps, Candidate candidate, DateTime after, DateTime until) {
        return handle.createQuery("SELECT COUNT(DISTINCT (q.client_port, q.transaction_id, q.data_value)) " +
                        "FROM dns_log q JOIN dns_log r ON r.tap_uuid = q.tap_uuid AND r.dns_type = 'response' " +
                        "AND r.client_address = q.client_address AND r.client_port = q.client_port " +
                        "AND r.transaction_id IS NOT DISTINCT FROM q.transaction_id " +
                        "AND r.timestamp >= q.timestamp " +
                        "AND r.timestamp <= q.timestamp + :reply_window::interval " +
                        "AND r.response_code IS NOT NULL AND r.response_code <> 'NOERROR' " +
                        "WHERE q.tap_uuid IN (<taps>) AND q.dns_type = 'query' " +
                        "AND q.timestamp > :after AND q.timestamp <= :until " +
                        "AND q.client_address = :client_address::inet AND q.data_value_etld = :domain " +
                        "AND q.data_value IS NOT NULL AND q.data_value <> q.data_value_etld")
                .bindList("taps", taps)
                .bind("reply_window", REPLY_WINDOW_SECONDS + " seconds")
                .bind("after", after)
                .bind("until", until)
                .bind("client_address", candidate.clientAddress)
                .bind("domain", candidate.domain)
                .mapTo(Long.class)
                .one();
    }

    private static Stats aggregate(List<QueryRow> rows, @Nullable DnsTunnelFindingEntry finding) {
        Map<String, QueryRow> unique = Maps.newLinkedHashMap();
        Set<UUID> taps = Sets.newLinkedHashSet();
        String clientMac = null;
        for (QueryRow row : rows) {
            unique.putIfAbsent(row.clientPort + "/" + row.transactionId + "/" + row.name, row);
            taps.add(row.tap);
            if (row.clientMac != null && !row.clientMac.equals(NULL_MAC) && !row.clientMac.equals(BROADCAST_MAC)) {
                clientMac = row.clientMac;
            }
        }

        Set<String> names = Sets.newLinkedHashSet(finding == null ? Collections.emptyList() : finding.names());
        Set<String> servers = Sets.newLinkedHashSet(finding == null ? Collections.emptyList() : finding.serverAddresses());
        Map<String, Long> recordTypes = Maps.newLinkedHashMap(finding == null ? Collections.emptyMap() : finding.recordTypes());
        Set<String> distinctNames = Sets.newHashSet();

        double entropySum = 0;
        long entropyCount = 0;
        double maximumEntropy = 0;
        long lengthSum = 0;
        int maximumLength = 0;
        DateTime firstSeen = null;
        DateTime lastSeen = null;

        for (QueryRow row : unique.values()) {
            distinctNames.add(row.name);
            if (names.size() < NAMES_LIMIT) {
                names.add(row.name);
            }
            if (servers.size() < SERVER_ADDRESSES_LIMIT) {
                servers.add(row.serverAddress);
            }
            recordTypes.merge(row.dataType, 1L, Long::sum);

            if (row.entropy != null) {
                entropySum += row.entropy;
                entropyCount++;
                maximumEntropy = Math.max(maximumEntropy, row.entropy);
            }

            lengthSum += row.subdomainLength;
            maximumLength = Math.max(maximumLength, row.subdomainLength);

            firstSeen = firstSeen == null || row.timestamp.isBefore(firstSeen) ? row.timestamp : firstSeen;
            lastSeen = lastSeen == null || row.timestamp.isAfter(lastSeen) ? row.timestamp : lastSeen;
        }

        long queryCount = unique.size();

        return new Stats(
                queryCount,
                distinctNames.size(),
                entropyCount == 0 ? 0 : entropySum / entropyCount,
                maximumEntropy,
                queryCount == 0 ? 0 : (double) lengthSum / queryCount,
                maximumLength,
                recordTypes,
                Lists.newArrayList(names),
                Lists.newArrayList(servers),
                clientMac,
                taps,
                firstSeen,
                lastSeen
        );
    }

    private static List<String> evaluateIndicators(Stats stats, DnsTunnelConfiguration config) {
        List<String> indicators = Lists.newArrayList();

        if (stats.queryCount == 0) {
            return indicators;
        }

        if (stats.averageEntropy >= config.entropyThreshold()) {
            indicators.add(DnsTunnelIndicator.HIGH_ENTROPY.name());
        }

        if (stats.averageNameLength >= config.nameLengthThreshold()) {
            indicators.add(DnsTunnelIndicator.LONG_NAMES.name());
        }

        long unusual = 0;
        long total = 0;
        for (Map.Entry<String, Long> type : stats.recordTypes.entrySet()) {
            total += type.getValue();
            if (UNUSUAL_RECORD_TYPES.contains(type.getKey())) {
                unusual += type.getValue();
            }
        }

        if (total > 0 && (unusual * 100.0 / total) >= config.recordTypeShareThreshold()) {
            indicators.add(DnsTunnelIndicator.UNUSUAL_RECORD_TYPES.name());
        }

        return indicators;
    }

    @Nullable
    private static DnsTunnelExclusionEntry findMatchingExclusion(Map<DnsTunnelExclusionEntry, DnsTunnelExclusionMatcher> exclusions,
                                                                 Candidate candidate) {
        for (Map.Entry<DnsTunnelExclusionEntry, DnsTunnelExclusionMatcher> e : exclusions.entrySet()) {
            if (e.getValue().matches(candidate.domain, candidate.clientAddress)) {
                return e.getKey();
            }
        }

        return null;
    }

    private void raiseNewFindingAlert(DnsTunnelFindingEntry finding, UUID organizationId, UUID tenantId) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("finding_uuid", finding.uuid().toString());
        attributes.put("client_address", finding.clientAddress());
        attributes.put("client_mac", finding.clientMac() == null ? "" : finding.clientMac());
        attributes.put("domain", finding.domain());
        attributes.put("indicators", String.join(",", finding.indicators()));
        attributes.put("query_count", String.valueOf(finding.queryCount()));
        attributes.put("distinct_name_count", String.valueOf(finding.distinctNameCount()));
        attributes.put("average_entropy", String.format(Locale.ROOT, "%.2f", finding.averageEntropy()));
        attributes.put("average_name_length", String.format(Locale.ROOT, "%.1f", finding.averageNameLength()));
        attributes.put("names", String.join(",", finding.names().subList(0, Math.min(ALERT_SAMPLE, finding.names().size()))));
        attributes.put("server_addresses", String.join(",", finding.serverAddresses()));

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                null,
                DetectionType.DNS_TUNNEL,
                Subsystem.ETHERNET,
                "DNS tunnel from [" + finding.clientAddress() + "] to domain [" + finding.domain() + "] detected. " +
                        "Indicators: " + String.join(", ", finding.indicators()) + ".",
                attributes,
                Set.of("finding_uuid")
        );
    }

    private static String identity(String clientAddress, String domain) {
        return clientAddress + "/" + domain;
    }

}
