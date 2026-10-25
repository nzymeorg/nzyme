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
                    readSource(handle, "TCP", candidates, () -> readL4Candidates(handle, "TCP", since, until, config));
                }

                if (config.sourceUdpEnabled()) {
                    readSource(handle, "UDP", candidates, () -> readL4Candidates(handle, "UDP", since, until, config));
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

            processCandidates(handle, candidates, config, since, organizationId, tenantId);
        });
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

    private void processCandidates(Handle handle,
                                   List<Candidate> candidates,
                                   ReconConfiguration config,
                                   DateTime since,
                                   UUID organizationId,
                                   UUID tenantId) {
        Map<String, ReconScanEntry> activeScans = Maps.newHashMap();
        for (ReconScanEntry scan : recon.findActiveScansOfTenant(handle, organizationId, tenantId, since)) {
            activeScans.put(scanIdentity(scan.type(), scan.sourceAddress(), scan.sourceMac(), scan.targetAddress()), scan);
        }

        Map<ReconExclusionEntry, ReconExclusionMatcher> exclusions = recon.exclusions()
                .compileEnabled(handle, organizationId, tenantId);
        Map<Long, Long> exclusionMatches = Maps.newHashMap();

        double minimumFailureShare = config.minimumFailurePercent() / 100.0;

        for (Candidate candidate : candidates) {
            try {
                if (!candidate.type.isFingerprinting() && candidate.failureShare() < minimumFailureShare) {
                    continue;
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

        handle.execute("DROP TABLE IF EXISTS recon_probes");
        handle.createUpdate("CREATE TEMPORARY TABLE recon_probes ON COMMIT DROP AS " +
                        "SELECT s.l4_type, host(s.source_address) AS source_address, s.source_mac, " +
                        "host(s.destination_address) AS destination_address, s.destination_port, " +
                        "MAX(CASE WHEN s.state = 'REFUSED' THEN 1 WHEN s.bytes_rx_count > 0 THEN 2 " +
                        "WHEN u.destination_port IS NOT NULL THEN 1 ELSE 0 END) AS outcome, " +
                        "COUNT(*) AS session_count, MIN(s.start_time) AS first_seen, " +
                        "MAX(s.most_recent_segment_time) AS last_seen, " +
                        "string_agg(DISTINCT s.tap_uuid::text, ',') AS taps " +
                        "FROM l4_sessions s LEFT JOIN recon_udp_unreachable u ON s.l4_type = 'UDP' " +
                        "AND u.source_address = s.source_address AND u.destination_address = s.destination_address " +
                        "AND u.destination_port = s.destination_port " +
                        "WHERE s.tap_uuid IN (<taps>) AND s.start_time >= :since AND s.start_time <= :until " +
                        "AND s.destination_address_is_multicast = false AND s.source_address_is_multicast = false " +
                        sourceCondition + targetCondition +
                        "AND (s.l4_type <> 'UDP' OR (s.destination_port < :udp_dynamic_port_start " +
                        "AND NOT jsonb_exists_any(COALESCE(s.tags, '[]'::jsonb), string_to_array(:excluded_udp_tags, ',')))) " +
                        "GROUP BY s.l4_type, s.source_address, s.source_mac, s.destination_address, s.destination_port")
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
                                readL4Enrichment(handle, l4Type, sourceAddress, sourceMac, null, since, until),
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
                                readL4Enrichment(handle, l4Type, sourceAddress, sourceMac, targetAddress, since, until),
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

    private Map<String, Object> readL4Enrichment(Handle handle,
                                                 String l4Type,
                                                 String sourceAddress,
                                                 @Nullable String sourceMac,
                                                 @Nullable String targetAddress,
                                                 DateTime since,
                                                 DateTime until) {
        Map<String, Object> enrichment = Maps.newHashMap();
        enrichment.put("l4_type", l4Type);

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

        handle.createQuery("WITH requests AS (SELECT source_address, source_mac, destination_address, " +
                        "SUM(packet_count) AS packets, MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen, " +
                        "string_agg(DISTINCT tap_uuid::text, ',') AS taps, MIN(bytes_min) AS bytes_min, " +
                        "MAX(bytes_max) AS bytes_max, mode() WITHIN GROUP (ORDER BY ip_ttl) AS ip_ttl, " +
                        "array_agg(DISTINCT icmp_type) AS types " +
                        "FROM icmp_packets WHERE tap_uuid IN (<taps>) AND icmp_type IN (" + ICMP_REQUEST_TYPES + ") " +
                        "AND last_seen >= :since AND last_seen <= :until " + siteLocalCondition("source_address", config.includeExternalSources()) +
                        siteLocalCondition("destination_address", config.includeExternalTargets()) +
                        "GROUP BY source_address, source_mac, destination_address), " +
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
        return handle.createQuery("WITH requests AS (SELECT destination_address, SUM(packet_count) AS packets, " +
                        "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen FROM icmp_packets " +
                        "WHERE tap_uuid IN (<taps>) AND icmp_type IN (" + ICMP_REQUEST_TYPES + ") " +
                        "AND source_address = :source_address::inet AND source_mac IS NOT DISTINCT FROM :source_mac " +
                        "AND last_seen >= :since AND last_seen <= :until " +
                        siteLocalCondition("destination_address", config.includeExternalTargets()) +
                        "GROUP BY destination_address), " +
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

    // Nmap OS-detection signatures. The first three are nmap-specific; timestamp and address mask are weak
    // (some discovery tools use them) and only count alongside a strong signature.
    static final String SIGNATURE_ECHO_NONZERO_CODE = "ECHO_NONZERO_CODE";
    static final String SIGNATURE_ECHO_TOS_4 = "ECHO_TOS_4";
    static final String SIGNATURE_INFORMATION_REQUEST = "INFORMATION_REQUEST";
    static final String SIGNATURE_TIMESTAMP_REQUEST = "TIMESTAMP_REQUEST";
    static final String SIGNATURE_ADDRESS_MASK_REQUEST = "ADDRESS_MASK_REQUEST";

    private List<Candidate> readFingerprintingCandidates(Handle handle,
                                                         List<UUID> taps,
                                                         DateTime since,
                                                         DateTime until,
                                                         ReconConfiguration config) {
        List<Candidate> result = Lists.newArrayList();

        handle.createQuery("WITH probes AS (SELECT source_address, source_mac, destination_address, " +
                        "CASE WHEN icmp_type = 8 AND icmp_code <> 0 THEN '" + SIGNATURE_ECHO_NONZERO_CODE + "' " +
                        "WHEN icmp_type = 8 AND ip_tos = 4 THEN '" + SIGNATURE_ECHO_TOS_4 + "' " +
                        "WHEN icmp_type = 15 THEN '" + SIGNATURE_INFORMATION_REQUEST + "' " +
                        "WHEN icmp_type = 13 THEN '" + SIGNATURE_TIMESTAMP_REQUEST + "' " +
                        "WHEN icmp_type = 17 THEN '" + SIGNATURE_ADDRESS_MASK_REQUEST + "' END AS signature, " +
                        "packet_count, first_seen, last_seen, tap_uuid, ip_ttl, bytes_max " +
                        "FROM icmp_packets WHERE tap_uuid IN (<taps>) AND last_seen >= :since AND last_seen <= :until " +
                        "AND ((icmp_type = 8 AND (icmp_code <> 0 OR ip_tos = 4)) OR icmp_type IN (13, 15, 17)) " +
                        siteLocalCondition("source_address", config.includeExternalSources()) +
                        siteLocalCondition("destination_address", config.includeExternalTargets()) + "), " +
                        "replies AS (SELECT DISTINCT source_address AS replier, destination_address AS requester " +
                        "FROM icmp_packets WHERE tap_uuid IN (<taps>) AND icmp_type IN (0, 14, 16, 18) " +
                        "AND last_seen >= :since AND last_seen <= :until) " +
                        "SELECT host(p.source_address) AS source_address, p.source_mac, " +
                        "host(p.destination_address) AS target_address, " +
                        "array_agg(DISTINCT p.signature) AS signatures, SUM(p.packet_count) AS probe_count, " +
                        "MIN(p.first_seen) AS first_seen, MAX(p.last_seen) AS last_seen, " +
                        "string_agg(DISTINCT p.tap_uuid::text, ',') AS taps, " +
                        "mode() WITHIN GROUP (ORDER BY p.ip_ttl) AS ip_ttl, MAX(p.bytes_max) AS bytes_max, " +
                        "bool_or(r.replier IS NOT NULL) AS replied " +
                        "FROM probes p LEFT JOIN replies r ON r.replier = p.destination_address AND r.requester = p.source_address " +
                        "GROUP BY p.source_address, p.source_mac, p.destination_address " +
                        "HAVING bool_or(p.signature IN ('" + SIGNATURE_ECHO_NONZERO_CODE + "', '" + SIGNATURE_ECHO_TOS_4 + "', '" +
                        SIGNATURE_INFORMATION_REQUEST + "'))")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .mapToMap()
                .forEach(row -> {
                    try {
                        String sourceAddress = (String) row.get("source_address");
                        String sourceMac = (String) row.get("source_mac");
                        String targetAddress = (String) row.get("target_address");
                        boolean replied = Boolean.TRUE.equals(row.get("replied"));
                        long probeCount = ((Number) row.get("probe_count")).longValue();
                        List<String> signatures = stringArray(row.get("signatures"));

                        Map<String, Object> enrichment = Maps.newHashMap();
                        enrichment.put("l4_type", "ICMP");
                        enrichment.put("signatures", signatures);
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
                                0,
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
