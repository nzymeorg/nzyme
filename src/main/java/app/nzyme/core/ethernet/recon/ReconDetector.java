package app.nzyme.core.ethernet.recon;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.ethernet.recon.db.ReconExclusionEntry;
import app.nzyme.core.ethernet.recon.db.ReconScanEntry;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ReconDetector {

    private static final Logger LOG = LogManager.getLogger(ReconDetector.class);

    static final int MAXIMUM_STORED_TARGETS = 25000;

    static final int UDP_DYNAMIC_PORT_START = 32768;
    static final List<String> EXCLUDED_UDP_TAGS = List.of(
            "DNS", "NTP", "STUN", "TURN", "DHCP4", "RTP", "WebRTC", "DTLS"
    );

    private final NzymeNode nzyme;
    private final Recon recon;
    private final ObjectMapper om;

    private final Timer runTimer;
    private final Timer candidateDiscoveryTimer;

    public ReconDetector(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.recon = nzyme.getEthernet().recon();
        this.om = new ObjectMapper();
        this.runTimer = nzyme.getMetrics().timer(MetricNames.RECON_DETECTION_RUN_TIMER);
        this.candidateDiscoveryTimer = nzyme.getMetrics().timer(MetricNames.RECON_CANDIDATE_DISCOVERY_TIMER);
    }

    static class Candidate {
        final ReconScanType type;
        @Nullable final String sourceAddress;
        @Nullable final String sourceMac;
        @Nullable final String targetAddress;
        final int hostCount;
        final int portCount;
        final long probeCount;
        final long openCount;
        final long closedCount;
        final long unansweredCount;
        final DateTime firstSeen;
        final DateTime lastSeen;
        final Set<UUID> tapUuids;
        final Map<String, Object> enrichment;
        final Function<Integer, List<Recon.TargetRecord>> targetLoader;

        Candidate(ReconScanType type, String sourceAddress, String sourceMac, String targetAddress, int hostCount,
                  int portCount, long probeCount, long openCount, long closedCount, long unansweredCount,
                  DateTime firstSeen, DateTime lastSeen, Set<UUID> tapUuids, Map<String, Object> enrichment,
                  Function<Integer, List<Recon.TargetRecord>> targetLoader) {
            this.type = type;
            this.sourceAddress = sourceAddress;
            this.sourceMac = sourceMac;
            this.targetAddress = targetAddress;
            this.hostCount = hostCount;
            this.portCount = portCount;
            this.probeCount = probeCount;
            this.openCount = openCount;
            this.closedCount = closedCount;
            this.unansweredCount = unansweredCount;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
            this.tapUuids = tapUuids;
            this.enrichment = enrichment;
            this.targetLoader = targetLoader;
        }

        double failureShare() {
            return probeCount == 0 ? 0 : (double) (closedCount + unansweredCount) / (double) probeCount;
        }

        String identity() {
            return scanIdentity(type.name(), sourceAddress, sourceMac, targetAddress);
        }
    }

    public void runForTenant(UUID organizationId, UUID tenantId) {
        ReconConfiguration config = recon.getConfiguration(organizationId, tenantId);

        recon.retentionCleanScans(organizationId, tenantId, DateTime.now().minusDays(config.scanRetentionTimeDays()));
        recon.decayProbeHistory(organizationId, tenantId, DateTime.now().minusDays(config.slowScanMemoryDays()));

        if (!config.detectionEnabled()) {
            LOG.debug("Recon detection disabled for tenant [{}/{}]. Skipping.", organizationId, tenantId);
            return;
        }

        List<UUID> taps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)
                .stream().map(Tap::uuid).collect(Collectors.toList());

        if (taps.isEmpty()) {
            LOG.debug("Tenant [{}/{}] has no taps. Skipping recon detection.", organizationId, tenantId);
            return;
        }

        DateTime until = DateTime.now();
        DateTime since = until.minusMinutes(config.detectionWindowMinutes());

        try (Timer.Context ignored = runTimer.time()) {
            runDetection(config, taps, since, until, organizationId, tenantId);
        }
    }

    private void runDetection(ReconConfiguration config,
                              List<UUID> taps,
                              DateTime since,
                              DateTime until,
                              UUID organizationId,
                              UUID tenantId) {
        nzyme.getDatabase().useTransaction(handle -> {
            // Only one node of a cluster processes a tenant at a time.
            boolean locked = handle.createQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                    .bind("key", "recon_detection:" + organizationId + ":" + tenantId)
                    .mapTo(Boolean.class)
                    .one();

            if (!locked) {
                LOG.debug("Recon detection for tenant [{}/{}] is running on another node. Skipping.",
                        organizationId, tenantId);
                return;
            }

            List<Candidate> candidates = Lists.newArrayList();

            try (Timer.Context ignored = candidateDiscoveryTimer.time()) {
                if (config.sourceTcpEnabled() || config.sourceUdpEnabled()) {
                    buildProbesTable(handle, taps, since, until, config);
                }

                if (config.sourceTcpEnabled()) {
                    readSource(handle, "TCP", candidates, () -> readL4Candidates(handle, taps, "TCP", since, until, config));
                }

                if (config.sourceUdpEnabled()) {
                    readSource(handle, "UDP", candidates, () -> readL4Candidates(handle, taps, "UDP", since, until, config));
                }

                if (config.sourceArpEnabled()) {
                    readSource(handle, "ARP", candidates, () -> readArpCandidates(handle, taps, since, until, config));
                }

                if (config.sourceIcmpEnabled()) {
                    readSource(handle, "ICMP", candidates, () -> readIcmpCandidates(handle, taps, since, until, config));
                }

                if (config.fingerprintingEnabled()) {
                    readSource(handle, "FINGERPRINTING", candidates, () -> readFingerprintingCandidates(handle, taps, since, until, config));
                }
            }

            LOG.debug("Analyzing [{}] recon candidates of tenant [{}/{}].",
                    candidates.size(), organizationId, tenantId);

            Set<String> attributedSources = processCandidates(handle, candidates, config, since, organizationId, tenantId);

            if (config.slowScanEnabled() && (config.sourceTcpEnabled() || config.sourceUdpEnabled())) {
                detectSlowScans(handle, attributedSources, config, since, until, organizationId, tenantId);
            }
        });
    }

    /*
     * Probes are added to the history with every run. Runs are a minute apart and the upsert is idempotent, so a
     * few minutes of overlap costs nothing, while a longer gap between runs would lose probes.
     */
    private static final int PROBE_HISTORY_LOOKBACK_MINUTES = 5;

    /*
     * Everything the window detector did not account for goes into the probe history, where the distinct
     * (target, port) pairs of a source accumulate over the memory period. A scanner that stays under the window
     * thresholds, however slowly it goes, crosses the slow scan threshold eventually.
     */
    private void detectSlowScans(Handle handle,
                                 Set<String> attributedSources,
                                 ReconConfiguration config,
                                 DateTime since,
                                 DateTime until,
                                 UUID organizationId,
                                 UUID tenantId) {
        DateTime memorySince = until.minusDays(config.slowScanMemoryDays());

        try {
            recon.recordProbeHistory(handle, organizationId, tenantId, until.minusMinutes(PROBE_HISTORY_LOOKBACK_MINUTES));
            recon.attributeProbeHistory(handle, organizationId, tenantId, attributedSources, since);
        } catch (Exception e) {
            LOG.error("Could not update recon probe history of tenant [{}/{}].", organizationId, tenantId, e);
            return;
        }

        List<Candidate> candidates = Lists.newArrayList();
        readSource(handle, "SLOW_SCAN", candidates,
                () -> readSlowScanCandidates(handle, config, memorySince, organizationId, tenantId));

        processCandidates(handle, candidates, config, memorySince, organizationId, tenantId);
    }

    private void readSource(Handle handle, String name, List<Candidate> target, java.util.function.Supplier<List<Candidate>> reader) {
        String savepoint = "recon_source_" + name.toLowerCase();
        handle.savepoint(savepoint);
        try {
            target.addAll(reader.get());
            handle.release(savepoint);
        } catch (Exception e) {
            LOG.error("Could not read [{}] recon candidates. Skipping this data source.", name, e);
            handle.rollbackToSavepoint(savepoint);
        }
    }

    private Set<String> processCandidates(Handle handle,
                                          List<Candidate> candidates,
                                          ReconConfiguration config,
                                          DateTime activeSince,
                                          UUID organizationId,
                                          UUID tenantId) {
        Map<String, ReconScanEntry> activeScans = Maps.newHashMap();
        for (ReconScanEntry scan : recon.findActiveScansOfTenant(handle, organizationId, tenantId, activeSince)) {
            activeScans.put(scanIdentity(scan.type(), scan.sourceAddress(), scan.sourceMac(), scan.targetAddress()), scan);
        }

        Set<String> attributedSources = Sets.newHashSet();

        Map<ReconExclusionEntry, ReconExclusionMatcher> exclusions = recon.exclusions()
                .compileEnabled(handle, organizationId, tenantId);
        Map<Long, Long> exclusionMatches = Maps.newHashMap();

        double minimumFailureShare = config.minimumFailurePercent() / 100.0;

        for (Candidate candidate : candidates) {
            try {
                if (!candidate.type.isFingerprinting() && candidate.failureShare() < minimumFailureShare) {
                    continue;
                }

                if (candidate.type.isL4() && candidate.sourceAddress != null) {
                    attributedSources.add(Recon.probeHistorySourceKey(candidate.sourceAddress, candidate.sourceMac));
                }

                ReconScanEntry scan = activeScans.get(candidate.identity());

                int limit = MAXIMUM_STORED_TARGETS;
                if (scan != null) {
                    limit = (int) Math.max(0, MAXIMUM_STORED_TARGETS - recon.countStoredTargets(handle, scan.id()));
                }

                List<Recon.TargetRecord> targets = candidate.targetLoader.apply(Math.max(limit, 1));

                ReconExclusionEntry exclusion = findMatchingExclusion(exclusions, candidate, targets);
                if (exclusion != null) {
                    exclusionMatches.merge(exclusion.id(), 1L, Long::sum);
                    continue;
                }

                boolean newScan = false;
                if (scan == null) {
                    scan = recon.createScan(
                            handle,
                            organizationId,
                            tenantId,
                            candidate.type,
                            candidate.sourceAddress,
                            candidate.sourceMac,
                            candidate.targetAddress,
                            candidate.firstSeen,
                            candidate.lastSeen
                    );
                    activeScans.put(candidate.identity(), scan);
                    newScan = true;
                }

                Recon.TargetUpsertResult stored = new Recon.TargetUpsertResult(0, null);
                if (limit > 0) {
                    stored = recon.upsertTargets(handle, scan.id(), targets.size() > limit ? targets.subList(0, limit) : targets);
                }

                boolean truncated = candidate.probeCount > MAXIMUM_STORED_TARGETS
                        || (limit == 0 && candidate.probeCount > 0)
                        || targets.size() > limit;

                DateTime progressedAt = null;
                if (stored.inserted() > 0) {
                    progressedAt = stored.latestNewTargetFirstSeen();
                } else if (limit == 0 && candidate.probeCount > 0) {
                    progressedAt = candidate.lastSeen;
                }

                recon.updateScanAggregates(
                        handle,
                        scan.id(),
                        candidate.probeCount,
                        candidate.openCount,
                        candidate.closedCount,
                        candidate.unansweredCount,
                        candidate.hostCount,
                        candidate.portCount,
                        truncated,
                        serializeEnrichment(candidate.enrichment),
                        candidate.tapUuids,
                        candidate.firstSeen,
                        progressedAt
                );

                boolean alert = candidate.type.isFingerprinting() ? config.alertOnNewFingerprinting() : config.alertOnNewScan();
                if (newScan && alert) {
                    recon.findScanById(handle, scan.id())
                            .ifPresent(refreshed -> raiseNewScanAlert(refreshed, organizationId, tenantId));
                }
            } catch (Exception e) {
                LOG.error("Could not process recon candidate [{}/{}] of tenant [{}/{}]. Skipping.",
                        candidate.type, candidate.sourceAddress, organizationId, tenantId, e);
            }
        }

        for (Map.Entry<Long, Long> match : exclusionMatches.entrySet()) {
            recon.exclusions().recordMatches(handle, match.getKey(), match.getValue());
        }

        return attributedSources;
    }

    @Nullable
    private static ReconExclusionEntry findMatchingExclusion(Map<ReconExclusionEntry, ReconExclusionMatcher> exclusions,
                                                             Candidate candidate,
                                                             List<Recon.TargetRecord> targets) {
        if (exclusions.isEmpty()) {
            return null;
        }

        List<String> targetAddresses = null;
        for (Map.Entry<ReconExclusionEntry, ReconExclusionMatcher> e : exclusions.entrySet()) {
            ReconExclusionMatcher matcher = e.getValue();
            if (!matcher.matchesType(candidate.type.name())
                    || !matcher.matchesSource(candidate.sourceAddress, candidate.sourceMac)) {
                continue;
            }

            if (!matcher.hasTargetCriteria()) {
                return e.getKey();
            }

            if (targetAddresses == null) {
                targetAddresses = targets.stream().map(Recon.TargetRecord::address).distinct().toList();
            }

            if (matcher.matchesTargets(candidate.targetAddress, targetAddresses)) {
                return e.getKey();
            }
        }

        return null;
    }

    private void buildProbesTable(Handle handle, List<UUID> taps, DateTime since, DateTime until, ReconConfiguration config) {
        String sourceCondition = config.includeExternalSources() ? "" : "AND s.source_address_is_site_local = true ";

        String targetCondition = config.includeExternalTargets() ? "" : "AND s.destination_address_is_site_local = true ";

        // ICMP port-unreachable replies quote the UDP probe that drew them, turning "unanswered" into "refused".
        handle.execute("DROP TABLE IF EXISTS recon_udp_unreachable");
        handle.createUpdate("CREATE TEMPORARY TABLE recon_udp_unreachable ON COMMIT DROP AS " +
                        "SELECT DISTINCT quoted_source_address AS source_address, " +
                        "quoted_destination_address AS destination_address, quoted_destination_port AS destination_port " +
                        "FROM icmp_packets WHERE tap_uuid IN (<taps>) AND icmp_type = 3 AND icmp_code = 3 " +
                        "AND quoted_protocol = 17 AND quoted_destination_port IS NOT NULL " +
                        "AND last_seen >= :since AND last_seen <= :until")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .execute();
        handle.execute("CREATE INDEX ON recon_udp_unreachable (source_address, destination_address, destination_port)");

        /*
         * Stateless probes (NULL, FIN, Xmas, crafted ACK) are answered with a reset by closed ports and with
         * silence by open ones, so a reply means closed and no reply means open or filtered.
         */
        String stealthProbes = "";
        if (config.sourceTcpEnabled()) {
            stealthProbes = "UNION ALL SELECT 'TCP' AS l4_type, host(p.source_address) AS source_address, " +
                    "p.source_mac, host(p.destination_address) AS destination_address, p.destination_port, " +
                    "CASE WHEN p.reply_count > 0 THEN 1 ELSE 0 END AS outcome, " +
                    "p.packet_count AS session_count, p.packet_count AS stealth_count, " +
                    "p.first_seen, p.last_seen, p.tap_uuid::text AS taps " +
                    "FROM tcp_probes p WHERE p.tap_uuid IN (<taps>) AND p.last_seen >= :since AND p.last_seen <= :until " +
                    (config.includeExternalSources() ? "" : "AND p.source_address_is_site_local = true ") +
                    (config.includeExternalTargets() ? "" : "AND p.destination_address_is_site_local = true ");
        }

        // Taps that see the same traffic each report it. Counts are taken per tap and the highest wins.
        handle.execute("DROP TABLE IF EXISTS recon_probes");
        handle.createUpdate("CREATE TEMPORARY TABLE recon_probes ON COMMIT DROP AS " +
                        "SELECT l4_type, source_address, source_mac, destination_address, destination_port, " +
                        "MAX(outcome) AS outcome, MAX(session_count) AS session_count, " +
                        "MAX(stealth_count) AS stealth_count, MIN(first_seen) AS first_seen, " +
                        "MAX(last_seen) AS last_seen, string_agg(DISTINCT taps, ',') AS taps " +
                        "FROM (SELECT l4_type, source_address, source_mac, destination_address, destination_port, taps, " +
                        "MAX(outcome) AS outcome, SUM(session_count)::bigint AS session_count, " +
                        "SUM(stealth_count)::bigint AS stealth_count, MIN(first_seen) AS first_seen, " +
                        "MAX(last_seen) AS last_seen " +
                        "FROM (SELECT s.l4_type, host(s.source_address) AS source_address, s.source_mac, " +
                        "host(s.destination_address) AS destination_address, s.destination_port, " +
                        "CASE WHEN s.state = 'REFUSED' THEN 1 WHEN s.bytes_rx_count > 0 THEN 2 " +
                        "WHEN u.destination_port IS NOT NULL THEN 1 ELSE 0 END AS outcome, " +
                        "1::bigint AS session_count, 0::bigint AS stealth_count, s.start_time AS first_seen, " +
                        "s.most_recent_segment_time AS last_seen, s.tap_uuid::text AS taps " +
                        "FROM l4_sessions s LEFT JOIN recon_udp_unreachable u ON s.l4_type = 'UDP' " +
                        "AND u.source_address = s.source_address AND u.destination_address = s.destination_address " +
                        "AND u.destination_port = s.destination_port " +
                        "WHERE s.tap_uuid IN (<taps>) AND s.start_time >= :since AND s.start_time <= :until " +
                        "AND s.destination_address_is_multicast = false AND s.source_address_is_multicast = false " +
                        sourceCondition + targetCondition +
                        "AND (s.l4_type <> 'UDP' OR (s.destination_port < :udp_dynamic_port_start " +
                        "AND NOT jsonb_exists_any(COALESCE(s.tags, '[]'::jsonb), string_to_array(:excluded_udp_tags, ',')))) " +
                        stealthProbes + ") probes " +
                        "GROUP BY l4_type, source_address, source_mac, destination_address, destination_port, taps) per_tap " +
                        "GROUP BY l4_type, source_address, source_mac, destination_address, destination_port")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .bind("udp_dynamic_port_start", UDP_DYNAMIC_PORT_START)
                .bind("excluded_udp_tags", String.join(",", EXCLUDED_UDP_TAGS))
                .execute();

        handle.execute("CREATE INDEX ON recon_probes (l4_type, source_address, source_mac, destination_address)");

        handle.execute("DROP TABLE IF EXISTS recon_sweep_ports");
        handle.createUpdate("CREATE TEMPORARY TABLE recon_sweep_ports ON COMMIT DROP AS " +
                        "SELECT l4_type, source_address, source_mac, destination_port FROM recon_probes " +
                        "GROUP BY l4_type, source_address, source_mac, destination_port " +
                        "HAVING COUNT(DISTINCT destination_address) >= :minimum_hosts " +
                        "AND COUNT(*) FILTER (WHERE outcome < 2) * 100 >= :minimum_failure_percent * COUNT(*)")
                .bind("minimum_hosts", config.hostSweepMinimumHosts())
                .bind("minimum_failure_percent", config.minimumFailurePercent())
                .execute();

        handle.execute("CREATE INDEX ON recon_sweep_ports (l4_type, source_address, source_mac, destination_port)");
    }

    private List<Candidate> readL4Candidates(Handle handle,
                                             List<UUID> taps,
                                             String l4Type,
                                             DateTime since,
                                             DateTime until,
                                             ReconConfiguration config) {
        List<Candidate> result = Lists.newArrayList();

        ReconScanType sweepType = "UDP".equals(l4Type) ? ReconScanType.UDP_HOST_SWEEP : ReconScanType.TCP_HOST_SWEEP;
        ReconScanType portScanType = "UDP".equals(l4Type) ? ReconScanType.UDP_PORT_SCAN : ReconScanType.TCP_PORT_SCAN;

        handle.createQuery("SELECT p.source_address, p.source_mac, " +
                        "COUNT(DISTINCT p.destination_address) AS host_count, " +
                        "COUNT(DISTINCT p.destination_port) AS port_count, COUNT(*) AS probe_count, " +
                        "COUNT(*) FILTER (WHERE p.outcome = 2) AS open_count, " +
                        "COUNT(*) FILTER (WHERE p.outcome = 1) AS closed_count, " +
                        "COUNT(*) FILTER (WHERE p.outcome = 0) AS unanswered_count, " +
                        "MIN(p.first_seen) AS first_seen, MAX(p.last_seen) AS last_seen, " +
                        "string_agg(p.taps, ',') AS taps " +
                        "FROM recon_probes p JOIN recon_sweep_ports sp ON sp.l4_type = p.l4_type " +
                        "AND sp.source_address = p.source_address AND sp.source_mac IS NOT DISTINCT FROM p.source_mac " +
                        "AND sp.destination_port = p.destination_port " +
                        "WHERE p.l4_type = :l4_type " +
                        "GROUP BY p.source_address, p.source_mac")
                .bind("l4_type", l4Type)
                .mapToMap()
                .forEach(row -> {
                    try {
                        String sourceAddress = (String) row.get("source_address");
                        String sourceMac = (String) row.get("source_mac");

                        result.add(new Candidate(
                                sweepType,
                                sourceAddress,
                                sourceMac,
                                null,
                                ((Number) row.get("host_count")).intValue(),
                                ((Number) row.get("port_count")).intValue(),
                                ((Number) row.get("probe_count")).longValue(),
                                ((Number) row.get("open_count")).longValue(),
                                ((Number) row.get("closed_count")).longValue(),
                                ((Number) row.get("unanswered_count")).longValue(),
                                new DateTime(row.get("first_seen")),
                                new DateTime(row.get("last_seen")),
                                parseTaps((String) row.get("taps")),
                                readL4Enrichment(handle, taps, l4Type, sourceAddress, sourceMac, null, since, until),
                                limit -> loadL4SweepTargets(handle, l4Type, sourceAddress, sourceMac, limit)
                        ));
                    } catch (Exception e) {
                        LOG.error("Could not read [{}] host sweep candidate.", l4Type, e);
                    }
                });

        // Port scans. A host whose probes are mostly covered by a sweep of the same source is part of that sweep.
        handle.createQuery("SELECT p.source_address, p.source_mac, p.destination_address, " +
                        "COUNT(*) AS probe_count, " +
                        "COUNT(*) FILTER (WHERE p.outcome = 2) AS open_count, " +
                        "COUNT(*) FILTER (WHERE p.outcome = 1) AS closed_count, " +
                        "COUNT(*) FILTER (WHERE p.outcome = 0) AS unanswered_count, " +
                        "MIN(p.first_seen) AS first_seen, MAX(p.last_seen) AS last_seen, " +
                        "string_agg(p.taps, ',') AS taps " +
                        "FROM recon_probes p LEFT JOIN recon_sweep_ports sp ON sp.l4_type = p.l4_type " +
                        "AND sp.source_address = p.source_address AND sp.source_mac IS NOT DISTINCT FROM p.source_mac " +
                        "AND sp.destination_port = p.destination_port " +
                        "WHERE p.l4_type = :l4_type " +
                        "GROUP BY p.source_address, p.source_mac, p.destination_address " +
                        "HAVING COUNT(*) >= :minimum_ports " +
                        "AND COUNT(sp.destination_port) * 2 < COUNT(*)")
                .bind("l4_type", l4Type)
                .bind("minimum_ports", config.portScanMinimumPorts())
                .mapToMap()
                .forEach(row -> {
                    try {
                        String sourceAddress = (String) row.get("source_address");
                        String sourceMac = (String) row.get("source_mac");
                        String targetAddress = (String) row.get("destination_address");
                        int ports = ((Number) row.get("probe_count")).intValue();

                        result.add(new Candidate(
                                portScanType,
                                sourceAddress,
                                sourceMac,
                                targetAddress,
                                1,
                                ports,
                                ports,
                                ((Number) row.get("open_count")).longValue(),
                                ((Number) row.get("closed_count")).longValue(),
                                ((Number) row.get("unanswered_count")).longValue(),
                                new DateTime(row.get("first_seen")),
                                new DateTime(row.get("last_seen")),
                                parseTaps((String) row.get("taps")),
                                readL4Enrichment(handle, taps, l4Type, sourceAddress, sourceMac, targetAddress, since, until),
                                limit -> loadL4Targets(handle, l4Type, sourceAddress, sourceMac, targetAddress, limit)
                        ));
                    } catch (Exception e) {
                        LOG.error("Could not read [{}] port scan candidate.", l4Type, e);
                    }
                });

        return result;
    }

    private List<Recon.TargetRecord> loadL4SweepTargets(Handle handle,
                                                        String l4Type,
                                                        String sourceAddress,
                                                        @Nullable String sourceMac,
                                                        int limit) {
        return handle.createQuery("SELECT p.destination_address, p.destination_port, p.outcome, p.session_count, " +
                        "p.first_seen, p.last_seen FROM recon_probes p JOIN recon_sweep_ports sp ON sp.l4_type = p.l4_type " +
                        "AND sp.source_address = p.source_address AND sp.source_mac IS NOT DISTINCT FROM p.source_mac " +
                        "AND sp.destination_port = p.destination_port " +
                        "WHERE p.l4_type = :l4_type AND p.source_address = :source_address " +
                        "AND p.source_mac IS NOT DISTINCT FROM :source_mac " +
                        "ORDER BY p.outcome DESC, p.last_seen DESC LIMIT :limit")
                .bind("l4_type", l4Type)
                .bind("source_address", sourceAddress)
                .bind("source_mac", sourceMac)
                .bind("limit", limit)
                .map((rs, ctx) -> new Recon.TargetRecord(
                        rs.getString("destination_address"),
                        rs.getInt("destination_port"),
                        ReconTargetOutcome.fromRank(rs.getInt("outcome")),
                        rs.getLong("session_count"),
                        new DateTime(rs.getTimestamp("first_seen")),
                        new DateTime(rs.getTimestamp("last_seen"))
                ))
                .list();
    }

    private List<Recon.TargetRecord> loadL4Targets(Handle handle,
                                                   String l4Type,
                                                   String sourceAddress,
                                                   @Nullable String sourceMac,
                                                   String targetAddress,
                                                   int limit) {
        return handle.createQuery("SELECT destination_address, destination_port, outcome, session_count, " +
                        "first_seen, last_seen FROM recon_probes WHERE l4_type = :l4_type " +
                        "AND source_address = :source_address AND source_mac IS NOT DISTINCT FROM :source_mac " +
                        "AND destination_address = :target_address " +
                        "ORDER BY outcome DESC, last_seen DESC LIMIT :limit")
                .bind("l4_type", l4Type)
                .bind("source_address", sourceAddress)
                .bind("source_mac", sourceMac)
                .bind("target_address", targetAddress)
                .bind("limit", limit)
                .map((rs, ctx) -> new Recon.TargetRecord(
                        rs.getString("destination_address"),
                        rs.getInt("destination_port"),
                        ReconTargetOutcome.fromRank(rs.getInt("outcome")),
                        rs.getLong("session_count"),
                        new DateTime(rs.getTimestamp("first_seen")),
                        new DateTime(rs.getTimestamp("last_seen"))
                ))
                .list();
    }

    private static final String TCP_FLAGS_LABEL =
            "COALESCE(NULLIF(concat_ws('/', CASE WHEN flag_syn THEN 'SYN' END, CASE WHEN flag_ack THEN 'ACK' END, " +
            "CASE WHEN flag_fin THEN 'FIN' END, CASE WHEN flag_rst THEN 'RST' END, CASE WHEN flag_psh THEN 'PSH' END, " +
            "CASE WHEN flag_urg THEN 'URG' END), ''), 'NULL')";

    private Map<String, Object> readL4Enrichment(Handle handle,
                                                 List<UUID> taps,
                                                 String l4Type,
                                                 String sourceAddress,
                                                 @Nullable String sourceMac,
                                                 @Nullable String targetAddress,
                                                 DateTime since,
                                                 DateTime until) {
        Map<String, Object> enrichment = Maps.newHashMap();
        enrichment.put("l4_type", l4Type);

        if ("TCP".equals(l4Type)) {
            String stealthCondition = targetAddress != null
                    ? "AND destination_address = :target_address::inet "
                    : "AND EXISTS (SELECT 1 FROM recon_sweep_ports sp WHERE sp.l4_type = 'TCP' " +
                      "AND sp.source_address = host(tcp_probes.source_address) " +
                      "AND sp.source_mac IS NOT DISTINCT FROM tcp_probes.source_mac " +
                      "AND sp.destination_port = tcp_probes.destination_port) ";

            try {
                handle.createQuery("SELECT MAX(stealth_probe_count) AS stealth_probe_count, " +
                                "mode() WITHIN GROUP (ORDER BY stealth_flags) AS stealth_flags " +
                                "FROM (SELECT tap_uuid, SUM(packet_count) AS stealth_probe_count, " +
                                "mode() WITHIN GROUP (ORDER BY " + TCP_FLAGS_LABEL + ") AS stealth_flags " +
                                "FROM tcp_probes WHERE tap_uuid IN (<taps>) AND source_address = :source_address::inet " +
                                "AND source_mac IS NOT DISTINCT FROM :source_mac " +
                                "AND last_seen >= :since AND last_seen <= :until " + stealthCondition +
                                "GROUP BY tap_uuid) per_tap")
                        .bindList("taps", taps)
                        .bind("source_address", sourceAddress)
                        .bind("source_mac", sourceMac)
                        .bind("target_address", targetAddress)
                        .bind("since", since)
                        .bind("until", until)
                        .mapToMap()
                        .findOne()
                        .ifPresent(row -> {
                            if (row.get("stealth_probe_count") != null) {
                                enrichment.put("stealth_probe_count", ((Number) row.get("stealth_probe_count")).longValue());
                                putIfNotNull(enrichment, "stealth_flags", row.get("stealth_flags"));
                            }
                        });
            } catch (Exception e) {
                LOG.debug("Could not read stealth probe enrichment for [{}].", sourceAddress, e);
            }
        }

        String probeCondition = targetAddress != null
                ? "AND destination_address = :target_address::inet "
                : "AND EXISTS (SELECT 1 FROM recon_sweep_ports sp WHERE sp.l4_type = l4_sessions.l4_type " +
                  "AND sp.source_address = host(l4_sessions.source_address) " +
                  "AND sp.source_mac IS NOT DISTINCT FROM l4_sessions.source_mac " +
                  "AND sp.destination_port = l4_sessions.destination_port) ";

        try {
            handle.createQuery("SELECT mode() WITHIN GROUP (ORDER BY ip_ttl) AS ip_ttl, " +
                            "mode() WITHIN GROUP (ORDER BY ip_df) AS ip_df, " +
                            "mode() WITHIN GROUP (ORDER BY tcp_syn_window_size) AS window_size, " +
                            "mode() WITHIN GROUP (ORDER BY tcp_syn_maximum_segment_size) AS mss, " +
                            "mode() WITHIN GROUP (ORDER BY tcp_syn_options::text) AS options, " +
                            "mode() WITHIN GROUP (ORDER BY fingerprint) AS fingerprint, " +
                            "COUNT(DISTINCT fingerprint) AS fingerprint_count " +
                            "FROM l4_sessions WHERE l4_type = :l4_type AND source_address = :source_address::inet " +
                            "AND source_mac IS NOT DISTINCT FROM :source_mac " +
                            "AND start_time >= :since AND start_time <= :until " + probeCondition)
                    .bind("l4_type", l4Type)
                    .bind("source_address", sourceAddress)
                    .bind("source_mac", sourceMac)
                    .bind("target_address", targetAddress)
                    .bind("since", since)
                    .bind("until", until)
                    .mapToMap()
                    .findOne()
                    .ifPresent(row -> {
                        putIfNotNull(enrichment, "ip_ttl", row.get("ip_ttl"));
                        putIfNotNull(enrichment, "ip_df", row.get("ip_df"));
                        if ("TCP".equals(l4Type)) {
                            putIfNotNull(enrichment, "tcp_window_size", row.get("window_size"));
                            putIfNotNull(enrichment, "tcp_mss", row.get("mss"));
                            putIfNotNull(enrichment, "tcp_options", row.get("options"));
                        }
                        putIfNotNull(enrichment, "fingerprint", row.get("fingerprint"));
                        putIfNotNull(enrichment, "fingerprint_count", row.get("fingerprint_count"));
                    });
        } catch (Exception e) {
            LOG.debug("Could not read recon enrichment for [{}].", sourceAddress, e);
        }

        return enrichment;
    }

    // Replies are matched by the reversed address pair, which is what the scanner learned.
    private static final String ICMP_REQUEST_TYPES = "8, 13, 17";
    private static final String ICMP_REPLY_TYPES = "0, 14, 18";

    private List<Candidate> readIcmpCandidates(Handle handle,
                                               List<UUID> taps,
                                               DateTime since,
                                               DateTime until,
                                               ReconConfiguration config) {
        List<Candidate> result = Lists.newArrayList();

        // Taps that see the same requests each report them. Counts are taken per tap and the highest wins.
        handle.createQuery("WITH per_tap AS (SELECT source_address, source_mac, destination_address, tap_uuid, " +
                        "SUM(packet_count) AS packets, MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen, " +
                        "MIN(bytes_min) AS bytes_min, MAX(bytes_max) AS bytes_max, " +
                        "mode() WITHIN GROUP (ORDER BY ip_ttl) AS ip_ttl " +
                        "FROM icmp_packets WHERE tap_uuid IN (<taps>) AND icmp_type IN (" + ICMP_REQUEST_TYPES + ") " +
                        "AND last_seen >= :since AND last_seen <= :until " + siteLocalCondition("source_address", config.includeExternalSources()) +
                        siteLocalCondition("destination_address", config.includeExternalTargets()) +
                        "GROUP BY source_address, source_mac, destination_address, tap_uuid), " +
                        "requests AS (SELECT source_address, source_mac, destination_address, " +
                        "MAX(packets) AS packets, MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen, " +
                        "string_agg(DISTINCT tap_uuid::text, ',') AS taps, MIN(bytes_min) AS bytes_min, " +
                        "MAX(bytes_max) AS bytes_max, mode() WITHIN GROUP (ORDER BY ip_ttl) AS ip_ttl " +
                        "FROM per_tap GROUP BY source_address, source_mac, destination_address), " +
                        "replies AS (SELECT DISTINCT source_address AS replier, destination_address AS requester " +
                        "FROM icmp_packets WHERE tap_uuid IN (<taps>) AND icmp_type IN (" + ICMP_REPLY_TYPES + ") " +
                        "AND last_seen >= :since AND last_seen <= :until) " +
                        "SELECT host(r.source_address) AS source_address, r.source_mac, " +
                        "COUNT(*) AS host_count, COUNT(p.replier) AS replied_count, SUM(r.packets) AS request_count, " +
                        "MIN(r.first_seen) AS first_seen, MAX(r.last_seen) AS last_seen, " +
                        "string_agg(r.taps, ',') AS taps, MIN(r.bytes_min) AS bytes_min, MAX(r.bytes_max) AS bytes_max, " +
                        "mode() WITHIN GROUP (ORDER BY r.ip_ttl) AS ip_ttl " +
                        "FROM requests r LEFT JOIN replies p ON p.replier = r.destination_address " +
                        "AND p.requester = r.source_address " +
                        "GROUP BY r.source_address, r.source_mac HAVING COUNT(*) >= :minimum_hosts")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .bind("minimum_hosts", config.hostSweepMinimumHosts())
                .mapToMap()
                .forEach(row -> {
                    try {
                        String sourceAddress = (String) row.get("source_address");
                        String sourceMac = (String) row.get("source_mac");
                        int hosts = ((Number) row.get("host_count")).intValue();
                        long replied = ((Number) row.get("replied_count")).longValue();

                        Map<String, Object> enrichment = Maps.newHashMap();
                        enrichment.put("l4_type", "ICMP");
                        enrichment.put("request_count", ((Number) row.get("request_count")).longValue());
                        putIfNotNull(enrichment, "ip_ttl", row.get("ip_ttl"));
                        putIfNotNull(enrichment, "payload_bytes_min", row.get("bytes_min"));
                        putIfNotNull(enrichment, "payload_bytes_max", row.get("bytes_max"));

                        result.add(new Candidate(
                                ReconScanType.ICMP_SWEEP,
                                sourceAddress,
                                sourceMac,
                                null,
                                hosts,
                                0,
                                hosts,
                                replied,
                                0,
                                hosts - replied,
                                new DateTime(row.get("first_seen")),
                                new DateTime(row.get("last_seen")),
                                parseTaps((String) row.get("taps")),
                                enrichment,
                                limit -> loadIcmpTargets(handle, taps, sourceAddress, sourceMac, since, until, config, limit)
                        ));
                    } catch (Exception e) {
                        LOG.error("Could not read ping sweep candidate.", e);
                    }
                });

        return result;
    }

    private List<Recon.TargetRecord> loadIcmpTargets(Handle handle,
                                                     List<UUID> taps,
                                                     String sourceAddress,
                                                     @Nullable String sourceMac,
                                                     DateTime since,
                                                     DateTime until,
                                                     ReconConfiguration config,
                                                     int limit) {
        return handle.createQuery("WITH per_tap AS (SELECT destination_address, tap_uuid, SUM(packet_count) AS packets, " +
                        "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen FROM icmp_packets " +
                        "WHERE tap_uuid IN (<taps>) AND icmp_type IN (" + ICMP_REQUEST_TYPES + ") " +
                        "AND source_address = :source_address::inet AND source_mac IS NOT DISTINCT FROM :source_mac " +
                        "AND last_seen >= :since AND last_seen <= :until " +
                        siteLocalCondition("destination_address", config.includeExternalTargets()) +
                        "GROUP BY destination_address, tap_uuid), " +
                        "requests AS (SELECT destination_address, MAX(packets) AS packets, MIN(first_seen) AS first_seen, " +
                        "MAX(last_seen) AS last_seen FROM per_tap GROUP BY destination_address), " +
                        "replies AS (SELECT DISTINCT source_address AS replier FROM icmp_packets " +
                        "WHERE tap_uuid IN (<taps>) AND icmp_type IN (" + ICMP_REPLY_TYPES + ") " +
                        "AND destination_address = :source_address::inet AND last_seen >= :since AND last_seen <= :until) " +
                        "SELECT host(r.destination_address) AS address, r.packets, r.first_seen, r.last_seen, " +
                        "(p.replier IS NOT NULL) AS replied FROM requests r LEFT JOIN replies p ON p.replier = r.destination_address " +
                        "ORDER BY replied DESC, r.last_seen DESC LIMIT :limit")
                .bindList("taps", taps)
                .bind("source_address", sourceAddress)
                .bind("source_mac", sourceMac)
                .bind("since", since)
                .bind("until", until)
                .bind("limit", limit)
                .map((rs, ctx) -> new Recon.TargetRecord(
                        rs.getString("address"),
                        null,
                        rs.getBoolean("replied") ? ReconTargetOutcome.OPEN : ReconTargetOutcome.UNANSWERED,
                        rs.getLong("packets"),
                        new DateTime(rs.getTimestamp("first_seen")),
                        new DateTime(rs.getTimestamp("last_seen"))
                ))
                .list();
    }

    /*
     * Nmap OS-detection probe signatures. The probes are described in nmap's osscan2.cc and
     * https://nmap.org/book/osdetect-methods.html. Strong signatures are shapes nothing else on a network
     * sends. Weak signatures are used by some discovery tools as well and only count alongside a strong one.
     */

    // ICMP (IE probes). The first three are nmap-specific.
    static final String SIGNATURE_ECHO_NONZERO_CODE = "ECHO_NONZERO_CODE";
    static final String SIGNATURE_ECHO_TOS_4 = "ECHO_TOS_4";
    static final String SIGNATURE_INFORMATION_REQUEST = "INFORMATION_REQUEST";
    static final String SIGNATURE_TIMESTAMP_REQUEST = "TIMESTAMP_REQUEST";
    static final String SIGNATURE_ADDRESS_MASK_REQUEST = "ADDRESS_MASK_REQUEST";

    // TCP. The six SEQ probes are SYNs to an open port with fixed window sizes and option layouts. A single
    // shape is weak, two or more distinct shapes are strong. ECN, T3 and T5 are individually unique shapes.
    static final String SIGNATURE_TCP_SEQ_PROBES = "TCP_SEQ_PROBES";
    static final String SIGNATURE_TCP_ECN_PROBE = "TCP_ECN_PROBE";
    static final String SIGNATURE_TCP_T3_PROBE = "TCP_T3_PROBE";
    static final String SIGNATURE_TCP_T5_PROBE = "TCP_T5_PROBE";

    // TCP probes without a session (recorded by the tap in tcp_probes): T2 NULL, T4/T6 bare ACK with SYN-only
    // options, T7 FIN/PSH/URG. T3 (SYN/FIN/PSH/URG) also lands here on taps that know the probe table.
    static final String SIGNATURE_TCP_T2_PROBE = "TCP_T2_PROBE";
    static final String SIGNATURE_TCP_T4_PROBE = "TCP_T4_PROBE";
    static final String SIGNATURE_TCP_T6_PROBE = "TCP_T6_PROBE";
    static final String SIGNATURE_TCP_T7_PROBE = "TCP_T7_PROBE";

    // UDP (U1 probe): A single datagram with 300 bytes of payload to a closed port. Weak on its own.
    static final String SIGNATURE_UDP_U1_PROBE = "UDP_U1_PROBE";

    /* U1 payload (300 bytes) plus UDP, IPv4 and Ethernet headers, as the tap sizes datagrams. */
    static final int UDP_U1_PROBE_BYTES = 300 + 8 + 20 + 14;

    static final List<String> STRONG_SIGNATURES = List.of(
            SIGNATURE_ECHO_NONZERO_CODE,
            SIGNATURE_ECHO_TOS_4,
            SIGNATURE_INFORMATION_REQUEST,
            SIGNATURE_TCP_ECN_PROBE,
            SIGNATURE_TCP_T2_PROBE,
            SIGNATURE_TCP_T3_PROBE,
            SIGNATURE_TCP_T4_PROBE,
            SIGNATURE_TCP_T5_PROBE,
            SIGNATURE_TCP_T6_PROBE,
            SIGNATURE_TCP_T7_PROBE
    );

    /*
     * TCP SYN shapes of the nmap SEQ probes, as (window size, option kinds in order). Option kinds are what the tap
     * records: 0 EOL, 1 NOP, 2 MSS, 3 window scale, 4 SACK permitted, 8 timestamp.
     */
    private static final String TCP_SEQ_SHAPE_CONDITION =
            "((tcp_syn_window_size = 1 AND tcp_syn_options = '[3,1,2,8,4]'::jsonb) " +
            "OR (tcp_syn_window_size = 63 AND tcp_syn_options = '[2,3,4,8,0]'::jsonb) " +
            "OR (tcp_syn_window_size = 4 AND tcp_syn_options = '[8,1,1,3,1,2]'::jsonb) " +
            "OR (tcp_syn_window_size = 4 AND tcp_syn_options = '[4,8,3,0]'::jsonb) " +
            "OR (tcp_syn_window_size = 16 AND tcp_syn_options = '[2,4,8,3,0]'::jsonb) " +
            "OR (tcp_syn_window_size = 512 AND tcp_syn_options = '[2,4,8]'::jsonb))";

    // ECN probe: SYN with ECE and CWR set, window 3, options WScale/NOP/MSS/SACK/NOP/NOP.
    private static final String TCP_ECN_SHAPE_CONDITION =
            "(tcp_syn_ece = true AND tcp_syn_cwr = true AND tcp_syn_window_size = 3 " +
            "AND tcp_syn_options = '[3,1,2,4,1,1]'::jsonb)";

    // T3: SYN/FIN/URG/PSH to an open port, window 256. T5: SYN to a closed port, window 31337. Both use MSS 265.
    private static final String TCP_T3_SHAPE_CONDITION =
            "(tcp_syn_window_size = 256 AND tcp_syn_maximum_segment_size = 265 " +
            "AND tcp_syn_options = '[3,1,2,8,4]'::jsonb)";
    private static final String TCP_T5_SHAPE_CONDITION =
            "(tcp_syn_window_size = 31337 AND tcp_syn_maximum_segment_size = 265 " +
            "AND tcp_syn_options = '[3,1,2,8,4]'::jsonb)";

    // Stateless probes (tcp_probes). All nmap T probes carry MSS 265 and the same option kinds.
    private static final String PROBE_NMAP_OPTIONS_CONDITION =
            "maximum_segment_size = 265 AND options = '[3,1,2,8,4]'::jsonb";
    private static final String PROBE_T2_CONDITION =
            "(NOT flag_syn AND NOT flag_ack AND NOT flag_fin AND NOT flag_rst AND NOT flag_psh AND NOT flag_urg " +
            "AND window_size = 128 AND " + PROBE_NMAP_OPTIONS_CONDITION + ")";
    private static final String PROBE_T3_CONDITION =
            "(flag_syn AND flag_fin AND flag_psh AND flag_urg AND NOT flag_ack AND window_size = 256 " +
            "AND " + PROBE_NMAP_OPTIONS_CONDITION + ")";
    private static final String PROBE_T4_CONDITION =
            "(flag_ack AND NOT flag_syn AND NOT flag_fin AND NOT flag_rst AND NOT flag_psh AND NOT flag_urg " +
            "AND window_size = 1024 AND " + PROBE_NMAP_OPTIONS_CONDITION + ")";
    private static final String PROBE_T6_CONDITION =
            "(flag_ack AND NOT flag_syn AND NOT flag_fin AND NOT flag_rst AND NOT flag_psh AND NOT flag_urg " +
            "AND window_size = 32768 AND " + PROBE_NMAP_OPTIONS_CONDITION + ")";
    private static final String PROBE_T7_CONDITION =
            "(flag_fin AND flag_psh AND flag_urg AND NOT flag_syn AND NOT flag_ack AND window_size = 65535 " +
            "AND " + PROBE_NMAP_OPTIONS_CONDITION + ")";

    private List<Candidate> readFingerprintingCandidates(Handle handle,
                                                         List<UUID> taps,
                                                         DateTime since,
                                                         DateTime until,
                                                         ReconConfiguration config) {
        List<Candidate> result = Lists.newArrayList();

        String l4SourceCondition = config.includeExternalSources() ? "" : "AND source_address_is_site_local = true ";
        String l4TargetCondition = config.includeExternalTargets() ? "" : "AND destination_address_is_site_local = true ";

        String strongSignatures = STRONG_SIGNATURES.stream()
                .map(s -> "'" + s + "'")
                .collect(Collectors.joining(", "));

        handle.createQuery("WITH probes AS (" +
                        "SELECT 'ICMP' AS protocol, source_address, source_mac, destination_address, " +
                        "NULL::integer AS destination_port, " +
                        "CASE WHEN icmp_type = 8 AND icmp_code <> 0 THEN '" + SIGNATURE_ECHO_NONZERO_CODE + "' " +
                        "WHEN icmp_type = 8 AND ip_tos = 4 THEN '" + SIGNATURE_ECHO_TOS_4 + "' " +
                        "WHEN icmp_type = 15 THEN '" + SIGNATURE_INFORMATION_REQUEST + "' " +
                        "WHEN icmp_type = 13 THEN '" + SIGNATURE_TIMESTAMP_REQUEST + "' " +
                        "WHEN icmp_type = 17 THEN '" + SIGNATURE_ADDRESS_MASK_REQUEST + "' END AS signature, " +
                        "NULL::text AS seq_shape, packet_count AS probe_count, first_seen, last_seen, tap_uuid, " +
                        "ip_ttl, bytes_max, false AS replied " +
                        "FROM icmp_packets WHERE tap_uuid IN (<taps>) AND last_seen >= :since AND last_seen <= :until " +
                        "AND ((icmp_type = 8 AND (icmp_code <> 0 OR ip_tos = 4)) OR icmp_type IN (13, 15, 17)) " +
                        siteLocalCondition("source_address", config.includeExternalSources()) +
                        siteLocalCondition("destination_address", config.includeExternalTargets()) +
                        "UNION ALL " +
                        "SELECT 'TCP' AS protocol, source_address, source_mac, destination_address, destination_port, " +
                        "CASE WHEN " + TCP_ECN_SHAPE_CONDITION + " THEN '" + SIGNATURE_TCP_ECN_PROBE + "' " +
                        "WHEN " + TCP_T5_SHAPE_CONDITION + " THEN '" + SIGNATURE_TCP_T5_PROBE + "' " +
                        "WHEN " + TCP_T3_SHAPE_CONDITION + " THEN '" + SIGNATURE_TCP_T3_PROBE + "' " +
                        "WHEN " + TCP_SEQ_SHAPE_CONDITION + " THEN '" + SIGNATURE_TCP_SEQ_PROBES + "' END AS signature, " +
                        "CASE WHEN " + TCP_SEQ_SHAPE_CONDITION + " THEN tcp_syn_window_size::text || '/' || tcp_syn_options::text " +
                        "END AS seq_shape, 1 AS probe_count, start_time AS first_seen, " +
                        "most_recent_segment_time AS last_seen, tap_uuid, ip_ttl, NULL::integer AS bytes_max, " +
                        "bytes_rx_count > 0 AS replied " +
                        "FROM l4_sessions WHERE tap_uuid IN (<taps>) AND l4_type = 'TCP' " +
                        "AND start_time >= :since AND start_time <= :until " +
                        "AND source_address_is_multicast = false AND destination_address_is_multicast = false " +
                        l4SourceCondition + l4TargetCondition +
                        "AND (" + TCP_SEQ_SHAPE_CONDITION + " OR " + TCP_ECN_SHAPE_CONDITION + " OR " +
                        TCP_T3_SHAPE_CONDITION + " OR " + TCP_T5_SHAPE_CONDITION + ") " +
                        "UNION ALL " +
                        "SELECT 'TCP' AS protocol, source_address, source_mac, destination_address, destination_port, " +
                        "CASE WHEN " + PROBE_T2_CONDITION + " THEN '" + SIGNATURE_TCP_T2_PROBE + "' " +
                        "WHEN " + PROBE_T3_CONDITION + " THEN '" + SIGNATURE_TCP_T3_PROBE + "' " +
                        "WHEN " + PROBE_T4_CONDITION + " THEN '" + SIGNATURE_TCP_T4_PROBE + "' " +
                        "WHEN " + PROBE_T6_CONDITION + " THEN '" + SIGNATURE_TCP_T6_PROBE + "' " +
                        "WHEN " + PROBE_T7_CONDITION + " THEN '" + SIGNATURE_TCP_T7_PROBE + "' END AS signature, " +
                        "NULL::text AS seq_shape, packet_count AS probe_count, first_seen, last_seen, tap_uuid, " +
                        "ip_ttl, NULL::integer AS bytes_max, false AS replied " +
                        "FROM tcp_probes WHERE tap_uuid IN (<taps>) AND last_seen >= :since AND last_seen <= :until " +
                        l4SourceCondition + l4TargetCondition +
                        "AND (" + PROBE_T2_CONDITION + " OR " + PROBE_T3_CONDITION + " OR " + PROBE_T4_CONDITION +
                        " OR " + PROBE_T6_CONDITION + " OR " + PROBE_T7_CONDITION + ") " +
                        "UNION ALL " +
                        "SELECT 'UDP' AS protocol, source_address, source_mac, destination_address, destination_port, " +
                        "'" + SIGNATURE_UDP_U1_PROBE + "' AS signature, NULL::text AS seq_shape, 1 AS probe_count, " +
                        "start_time AS first_seen, most_recent_segment_time AS last_seen, tap_uuid, ip_ttl, " +
                        "NULL::integer AS bytes_max, bytes_rx_count > 0 AS replied " +
                        "FROM l4_sessions WHERE tap_uuid IN (<taps>) AND l4_type = 'UDP' " +
                        "AND start_time >= :since AND start_time <= :until " +
                        "AND source_address_is_multicast = false AND destination_address_is_multicast = false " +
                        l4SourceCondition + l4TargetCondition +
                        "AND segments_count = 1 AND bytes_tx_count = :u1_bytes AND bytes_rx_count = 0), " +
                        "replies AS (SELECT DISTINCT source_address AS replier, destination_address AS requester " +
                        "FROM icmp_packets WHERE tap_uuid IN (<taps>) AND icmp_type IN (0, 14, 16, 18) " +
                        "AND last_seen >= :since AND last_seen <= :until), " +
                        "per_tap AS (SELECT source_address, source_mac, destination_address, tap_uuid, " +
                        "SUM(probe_count) AS probe_count FROM probes WHERE signature IS NOT NULL " +
                        "GROUP BY source_address, source_mac, destination_address, tap_uuid) " +
                        "SELECT host(p.source_address) AS source_address, p.source_mac, " +
                        "host(p.destination_address) AS target_address, " +
                        "array_agg(DISTINCT p.signature) AS signatures, array_agg(DISTINCT p.protocol) AS protocols, " +
                        "array_agg(DISTINCT p.destination_port) FILTER (WHERE p.protocol = 'TCP') AS tcp_ports, " +
                        "COUNT(DISTINCT p.seq_shape) AS tcp_seq_probes, " +
                        "MAX(t.probe_count) AS probe_count, " +
                        "MIN(p.first_seen) AS first_seen, MAX(p.last_seen) AS last_seen, " +
                        "string_agg(DISTINCT p.tap_uuid::text, ',') AS taps, " +
                        "mode() WITHIN GROUP (ORDER BY p.ip_ttl) FILTER (WHERE p.ip_ttl IS NOT NULL) AS ip_ttl, " +
                        "MAX(p.bytes_max) AS bytes_max, " +
                        "bool_or(p.replied OR r.replier IS NOT NULL) AS replied " +
                        "FROM probes p LEFT JOIN replies r ON r.replier = p.destination_address AND r.requester = p.source_address " +
                        "JOIN per_tap t ON t.source_address = p.source_address AND t.source_mac IS NOT DISTINCT FROM p.source_mac " +
                        "AND t.destination_address = p.destination_address AND t.tap_uuid = p.tap_uuid " +
                        "WHERE p.signature IS NOT NULL " +
                        "GROUP BY p.source_address, p.source_mac, p.destination_address " +
                        "HAVING bool_or(p.signature IN (" + strongSignatures + ")) OR COUNT(DISTINCT p.seq_shape) >= 2")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .bind("u1_bytes", UDP_U1_PROBE_BYTES)
                .mapToMap()
                .forEach(row -> {
                    try {
                        String sourceAddress = (String) row.get("source_address");
                        String sourceMac = (String) row.get("source_mac");
                        String targetAddress = (String) row.get("target_address");
                        boolean replied = Boolean.TRUE.equals(row.get("replied"));
                        long probeCount = ((Number) row.get("probe_count")).longValue();
                        List<String> signatures = stringArray(row.get("signatures"));
                        List<String> protocols = stringArray(row.get("protocols"));
                        List<String> tcpPorts = stringArray(row.get("tcp_ports"));
                        long tcpSeqProbes = ((Number) row.get("tcp_seq_probes")).longValue();

                        Map<String, Object> enrichment = Maps.newHashMap();
                        enrichment.put("l4_type", protocols.size() == 1 ? protocols.get(0) : "MIXED");
                        enrichment.put("protocols", protocols);
                        enrichment.put("signatures", signatures);
                        if (!tcpPorts.isEmpty()) {
                            enrichment.put("tcp_ports", tcpPorts.stream().map(Integer::parseInt).toList());
                        }
                        if (tcpSeqProbes > 0) {
                            enrichment.put("tcp_seq_probes", tcpSeqProbes);
                        }
                        putIfNotNull(enrichment, "ip_ttl", row.get("ip_ttl"));
                        putIfNotNull(enrichment, "payload_bytes_max", row.get("bytes_max"));

                        DateTime firstSeen = new DateTime(row.get("first_seen"));
                        DateTime lastSeen = new DateTime(row.get("last_seen"));
                        Set<UUID> tapUuids = parseTaps((String) row.get("taps"));

                        result.add(new Candidate(
                                ReconScanType.OS_FINGERPRINTING,
                                sourceAddress,
                                sourceMac,
                                targetAddress,
                                1,
                                tcpPorts.size(),
                                probeCount,
                                replied ? 1 : 0,
                                0,
                                replied ? 0 : 1,
                                firstSeen,
                                lastSeen,
                                tapUuids,
                                enrichment,
                                limit -> List.of(new Recon.TargetRecord(
                                        targetAddress,
                                        null,
                                        replied ? ReconTargetOutcome.OPEN : ReconTargetOutcome.UNANSWERED,
                                        probeCount,
                                        firstSeen,
                                        lastSeen
                                ))
                        ));
                    } catch (Exception e) {
                        LOG.error("Could not read OS fingerprinting candidate.", e);
                    }
                });

        return result;
    }

    /** The ICMP table has no site-local flag, so the private ranges are matched directly. */
    private static String siteLocalCondition(String column, boolean includeExternal) {
        if (includeExternal) {
            return "";
        }

        return "AND (" + column + " << '10.0.0.0/8'::inet OR " + column + " << '172.16.0.0/12'::inet " +
                "OR " + column + " << '192.168.0.0/16'::inet) ";
    }

    private static List<String> stringArray(Object value) throws java.sql.SQLException {
        List<String> result = Lists.newArrayList();
        if (value == null) {
            return result;
        }

        for (Object o : (Object[]) ((java.sql.Array) value).getArray()) {
            if (o != null) {
                result.add(o.toString());
            }
        }

        return result;
    }

    private List<Candidate> readSlowScanCandidates(Handle handle,
                                                   ReconConfiguration config,
                                                   DateTime memorySince,
                                                   UUID organizationId,
                                                   UUID tenantId) {
        List<Candidate> result = Lists.newArrayList();

        handle.createQuery("SELECT host(source_address) AS source_address, source_mac, " +
                        "COUNT(*) AS pair_count, " +
                        "COUNT(*) FILTER (WHERE outcome = 2) AS open_count, " +
                        "COUNT(*) FILTER (WHERE outcome = 1) AS closed_count, " +
                        "COUNT(*) FILTER (WHERE outcome = 0) AS unanswered_count, " +
                        "COUNT(DISTINCT target_address) AS host_count, COUNT(DISTINCT target_port) AS port_count, " +
                        "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen, " +
                        "array_agg(DISTINCT l4_type) AS l4_types " +
                        "FROM recon_probe_history WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND attributed = false AND last_seen >= :memory_since " +
                        "GROUP BY source_address, source_mac " +
                        "HAVING COUNT(*) FILTER (WHERE outcome < 2) >= :minimum_targets")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("memory_since", memorySince)
                .bind("minimum_targets", config.slowScanMinimumTargets())
                .mapToMap()
                .forEach(row -> {
                    try {
                        String sourceAddress = (String) row.get("source_address");
                        String sourceMac = (String) row.get("source_mac");
                        List<String> l4Types = stringArray(row.get("l4_types"));
                        DateTime firstSeen = new DateTime(row.get("first_seen"));
                        DateTime lastSeen = new DateTime(row.get("last_seen"));

                        Map<String, Object> enrichment = Maps.newHashMap();
                        enrichment.put("l4_type", l4Types.size() == 1 ? l4Types.get(0) : "MIXED");
                        enrichment.put("memory_days", config.slowScanMemoryDays());
                        enrichment.put("active_days", Math.max(1,
                                (int) Math.ceil((lastSeen.getMillis() - firstSeen.getMillis()) / 86_400_000.0)));

                        result.add(new Candidate(
                                ReconScanType.SLOW_SCAN,
                                sourceAddress,
                                sourceMac,
                                null,
                                ((Number) row.get("host_count")).intValue(),
                                ((Number) row.get("port_count")).intValue(),
                                ((Number) row.get("pair_count")).longValue(),
                                ((Number) row.get("open_count")).longValue(),
                                ((Number) row.get("closed_count")).longValue(),
                                ((Number) row.get("unanswered_count")).longValue(),
                                firstSeen,
                                lastSeen,
                                Set.of(),
                                enrichment,
                                limit -> loadSlowScanTargets(handle, sourceAddress, sourceMac, memorySince, organizationId, tenantId, limit)
                        ));
                    } catch (Exception e) {
                        LOG.error("Could not read slow scan candidate.", e);
                    }
                });

        return result;
    }

    private List<Recon.TargetRecord> loadSlowScanTargets(Handle handle,
                                                         String sourceAddress,
                                                         @Nullable String sourceMac,
                                                         DateTime memorySince,
                                                         UUID organizationId,
                                                         UUID tenantId,
                                                         int limit) {
        return handle.createQuery("SELECT host(target_address) AS target_address, target_port, outcome, " +
                        "first_seen, last_seen FROM recon_probe_history WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND source_address = :source_address::inet " +
                        "AND source_mac IS NOT DISTINCT FROM :source_mac AND attributed = false " +
                        "AND last_seen >= :memory_since ORDER BY outcome DESC, last_seen DESC LIMIT :limit")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("source_address", sourceAddress)
                .bind("source_mac", sourceMac)
                .bind("memory_since", memorySince)
                .bind("limit", limit)
                .map((rs, ctx) -> new Recon.TargetRecord(
                        rs.getString("target_address"),
                        rs.getInt("target_port"),
                        ReconTargetOutcome.fromRank(rs.getInt("outcome")),
                        1,
                        new DateTime(rs.getTimestamp("first_seen")),
                        new DateTime(rs.getTimestamp("last_seen"))
                ))
                .list();
    }

    private List<Candidate> readArpCandidates(Handle handle,
                                              List<UUID> taps,
                                              DateTime since,
                                              DateTime until,
                                              ReconConfiguration config) {
        List<Candidate> result = Lists.newArrayList();

        handle.createQuery("WITH requests AS (SELECT arp_sender_mac, arp_sender_address, arp_target_address, " +
                        "timestamp, tap_uuid FROM arp_packets WHERE tap_uuid IN (<taps>) AND operation = 'Request' " +
                        "AND timestamp >= :since AND timestamp <= :until " +
                        "AND arp_target_address <> arp_sender_address AND arp_sender_mac <> '00:00:00:00:00:00'), " +
                        "targets AS (SELECT r.arp_sender_mac, r.arp_target_address, " +
                        "bool_or(p.id IS NOT NULL) AS replied FROM requests r " +
                        "LEFT JOIN arp_packets p ON p.tap_uuid IN (<taps>) AND p.operation = 'Reply' " +
                        "AND p.arp_target_mac = r.arp_sender_mac AND p.arp_sender_address = r.arp_target_address " +
                        "AND p.timestamp >= :since AND p.timestamp <= :until " +
                        "GROUP BY r.arp_sender_mac, r.arp_target_address) " +
                        "SELECT r.arp_sender_mac AS source_mac, " +
                        "mode() WITHIN GROUP (ORDER BY host(r.arp_sender_address)) " +
                        "FILTER (WHERE NOT r.arp_sender_address = '0.0.0.0'::inet) AS source_address, " +
                        "COUNT(DISTINCT r.arp_target_address) AS host_count, COUNT(*) AS request_count, " +
                        "MIN(r.timestamp) AS first_seen, MAX(r.timestamp) AS last_seen, " +
                        "string_agg(DISTINCT r.tap_uuid::text, ',') AS taps, " +
                        "(SELECT COUNT(*) FROM targets t WHERE t.arp_sender_mac = r.arp_sender_mac AND t.replied) AS replied_count " +
                        "FROM requests r GROUP BY r.arp_sender_mac " +
                        "HAVING COUNT(DISTINCT r.arp_target_address) >= :minimum_hosts")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .bind("minimum_hosts", config.arpSweepMinimumHosts())
                .mapToMap()
                .forEach(row -> {
                    try {
                        String sourceMac = (String) row.get("source_mac");
                        String sourceAddress = (String) row.get("source_address");
                        int hosts = ((Number) row.get("host_count")).intValue();
                        long replied = ((Number) row.get("replied_count")).longValue();

                        Map<String, Object> enrichment = Maps.newHashMap();
                        enrichment.put("request_count", ((Number) row.get("request_count")).longValue());

                        result.add(new Candidate(
                                ReconScanType.ARP_SWEEP,
                                sourceAddress,
                                sourceMac,
                                null,
                                hosts,
                                0,
                                hosts,
                                replied,
                                0,
                                hosts - replied,
                                new DateTime(row.get("first_seen")),
                                new DateTime(row.get("last_seen")),
                                parseTaps((String) row.get("taps")),
                                enrichment,
                                limit -> loadArpTargets(handle, taps, sourceMac, since, until, limit)
                        ));
                    } catch (Exception e) {
                        LOG.error("Could not read ARP sweep candidate.", e);
                    }
                });

        return result;
    }

    private List<Recon.TargetRecord> loadArpTargets(Handle handle,
                                                    List<UUID> taps,
                                                    String sourceMac,
                                                    DateTime since,
                                                    DateTime until,
                                                    int limit) {
        return handle.createQuery("WITH targets AS (SELECT arp_target_address AS address, COUNT(*) AS request_count, " +
                        "MIN(timestamp) AS first_seen, MAX(timestamp) AS last_seen FROM arp_packets " +
                        "WHERE tap_uuid IN (<taps>) AND operation = 'Request' AND arp_sender_mac = :source_mac " +
                        "AND timestamp >= :since AND timestamp <= :until " +
                        "AND arp_target_address <> arp_sender_address GROUP BY arp_target_address), " +
                        "replies AS (SELECT DISTINCT arp_sender_address AS address FROM arp_packets " +
                        "WHERE tap_uuid IN (<taps>) AND operation = 'Reply' AND arp_target_mac = :source_mac " +
                        "AND timestamp >= :since AND timestamp <= :until) " +
                        "SELECT host(t.address) AS address, t.request_count, t.first_seen, t.last_seen, " +
                        "(r.address IS NOT NULL) AS replied FROM targets t LEFT JOIN replies r ON r.address = t.address " +
                        "ORDER BY replied DESC, t.last_seen DESC LIMIT :limit")
                .bindList("taps", taps)
                .bind("source_mac", sourceMac)
                .bind("since", since)
                .bind("until", until)
                .bind("limit", limit)
                .map((rs, ctx) -> new Recon.TargetRecord(
                        rs.getString("address"),
                        null,
                        rs.getBoolean("replied") ? ReconTargetOutcome.OPEN : ReconTargetOutcome.UNANSWERED,
                        rs.getLong("request_count"),
                        new DateTime(rs.getTimestamp("first_seen")),
                        new DateTime(rs.getTimestamp("last_seen"))
                ))
                .list();
    }

    private void raiseNewScanAlert(ReconScanEntry scan, UUID organizationId, UUID tenantId) {
        Map<String, String> attributes = scanAttributes(scan);

        ReconScanType type = ReconScanType.valueOf(scan.type());

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                null,
                type.isFingerprinting() ? DetectionType.RECON_FINGERPRINTING_NEW : DetectionType.RECON_SCAN_NEW,
                Subsystem.ETHERNET,
                type.getHumanReadable() + " from [" + sourceDescription(scan) + "] against ["
                        + targetDescription(scan) + "] detected.",
                attributes,
                Set.of("scan_uuid")
        );
    }

    private static Map<String, String> scanAttributes(ReconScanEntry scan) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("scan_uuid", scan.uuid().toString());
        attributes.put("scan_type", scan.type());
        attributes.put("source", sourceDescription(scan));
        attributes.put("target", targetDescription(scan));
        if (scan.sourceAddress() != null) {
            attributes.put("source_address", scan.sourceAddress());
        }
        if (scan.sourceMac() != null) {
            attributes.put("source_mac", scan.sourceMac());
        }
        if (scan.targetAddress() != null) {
            attributes.put("target_address", scan.targetAddress());
        }
        attributes.put("host_count", String.valueOf(scan.hostCount()));
        attributes.put("port_count", String.valueOf(scan.portCount()));
        attributes.put("probe_count", String.valueOf(scan.probeCount()));
        attributes.put("open_count", String.valueOf(scan.openCount()));

        return attributes;
    }

    public static String sourceDescription(ReconScanEntry scan) {
        if (scan.sourceAddress() != null) {
            return scan.sourceAddress();
        }

        return scan.sourceMac() == null ? "unknown" : scan.sourceMac();
    }

    public static String targetDescription(ReconScanEntry scan) {
        if (scan.targetAddress() != null) {
            return scan.targetAddress();
        }

        return scan.hostCount() + " hosts";
    }

    static String scanIdentity(String type, @Nullable String sourceAddress, @Nullable String sourceMac, @Nullable String targetAddress) {
        return type + "/" + (sourceAddress == null ? "" : sourceAddress) + "/"
                + (sourceMac == null ? "" : sourceMac.toUpperCase()) + "/" + (targetAddress == null ? "" : targetAddress);
    }

    private static Set<UUID> parseTaps(@Nullable String csv) {
        Set<UUID> result = Sets.newHashSet();
        if (csv == null) {
            return result;
        }

        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            try {
                result.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ignored) { }
        }

        return result;
    }

    @Nullable
    private String serializeEnrichment(Map<String, Object> enrichment) {
        if (enrichment == null || enrichment.isEmpty()) {
            return null;
        }

        try {
            return om.writeValueAsString(enrichment);
        } catch (JacksonException e) {
            LOG.error("Could not serialize recon enrichment.", e);
            return null;
        }
    }

    private static void putIfNotNull(Map<String, Object> map, String key, @Nullable Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

}
