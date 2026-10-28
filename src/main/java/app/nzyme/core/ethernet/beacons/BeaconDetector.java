package app.nzyme.core.ethernet.beacons;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.ethernet.beacons.db.BeaconEntry;
import app.nzyme.core.ethernet.beacons.db.BeaconExclusionEntry;
import app.nzyme.core.ethernet.beacons.db.BeaconSourceEntry;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Timer;
import app.nzyme.plugin.Subsystem;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BeaconDetector {

    private static final Logger LOG = LogManager.getLogger(BeaconDetector.class);

    private static final List<String> EXCLUDED_L4_TAGS = List.of(
            "TLS", "SOCKS", "DNS", "NTP", "STUN", "TURN", "DHCP4", "RTP", "WebRTC", "DTLS", "WireGuard", "OpenVPN", "DERP"
    );

    private static final Duration MAXIMUM_COLLAPSE_WINDOW = Duration.standardSeconds(5);

    private final NzymeNode nzyme;
    private final Beacons beacons;
    private final ObjectMapper om;

    private final Timer runTimer;
    private final Timer candidateDiscoveryTimer;

    public BeaconDetector(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.beacons = nzyme.getEthernet().beacons();
        this.om = new ObjectMapper();
        this.runTimer = nzyme.getMetrics().timer(MetricNames.BEACON_DETECTION_RUN_TIMER);
        this.candidateDiscoveryTimer = nzyme.getMetrics().timer(MetricNames.BEACON_CANDIDATE_DISCOVERY_TIMER);
    }

    private static class Candidate {
        final BeaconType type;
        final String destinationKey;
        @Nullable final String destinationHost;
        @Nullable final String destinationAddress;
        @Nullable final Integer destinationPort;
        final boolean siteLocal;
        final String sourceAddress;
        @Nullable final String sourceMac;
        final List<DateTime> timestamps;
        final List<String> checkinDestinationAddresses;
        final List<String> checkinTapUuids;
        final Map<String, Object> enrichment;

        @Nullable String l4Type = null;
        @Nullable List<String> checkinDestinationMacs = null;
        @Nullable List<String> checkinSessionKeys = null;
        @Nullable List<Long> checkinBytesRx = null;
        @Nullable List<Long> checkinBytesTx = null;
        @Nullable List<Long> checkinBytesTotal = null;

        Candidate(BeaconType type, String destinationKey, String destinationHost, String destinationAddress,
                  Integer destinationPort, boolean siteLocal, String sourceAddress, String sourceMac,
                  List<DateTime> timestamps, List<String> checkinDestinationAddresses, List<String> checkinTapUuids,
                  Map<String, Object> enrichment) {
            this.type = type;
            this.destinationKey = destinationKey;
            this.destinationHost = destinationHost;
            this.destinationAddress = destinationAddress;
            this.destinationPort = destinationPort;
            this.siteLocal = siteLocal;
            this.sourceAddress = sourceAddress;
            this.sourceMac = sourceMac;
            this.timestamps = timestamps;
            this.checkinDestinationAddresses = checkinDestinationAddresses;
            this.checkinTapUuids = checkinTapUuids;
            this.enrichment = enrichment;
        }
    }

    public void runForTenant(UUID organizationId, UUID tenantId) {
        BeaconsConfiguration config = beacons.getConfiguration(organizationId, tenantId);

        beacons.retentionCleanCheckins(
                organizationId, tenantId, DateTime.now().minusDays(config.checkinRetentionTimeDays())
        );

        if (!config.detectionEnabled() || config.enabledSources().isEmpty()) {
            LOG.debug("Beacon detection disabled for tenant [{}/{}]. Skipping.", organizationId, tenantId);
            return;
        }

        List<UUID> taps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)
                .stream().map(Tap::uuid).collect(Collectors.toList());

        if (taps.isEmpty()) {
            LOG.debug("Tenant [{}/{}] has no taps. Skipping beacon detection.", organizationId, tenantId);
            return;
        }

        DateTime until = DateTime.now();
        DateTime since = until.minusMinutes(config.detectionWindowMinutes());

        try (Timer.Context ignored = runTimer.time()) {
            runDetection(config, taps, since, until, organizationId, tenantId);
        }
    }

    private void runDetection(BeaconsConfiguration config,
                              List<UUID> taps,
                              DateTime since,
                              DateTime until,
                              UUID organizationId,
                              UUID tenantId) {
        nzyme.getDatabase().useTransaction(handle -> {
            /*
             * All nodes of a cluster run this detector. The advisory lock makes sure that only one node processes
             * a tenant at a time. Another node that finds the lock taken simply skips this run.
             */
            boolean locked = handle.createQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                    .bind("key", "beacon_detection:" + organizationId + ":" + tenantId)
                    .mapTo(Boolean.class)
                    .one();

            if (!locked) {
                LOG.debug("Beacon detection for tenant [{}/{}] is running on another node. Skipping.",
                        organizationId, tenantId);
                return;
            }

            List<Candidate> candidates = Lists.newArrayList();

            try (Timer.Context ignored = candidateDiscoveryTimer.time()) {
                if (config.enabledSources().contains(BeaconType.TLS)) {
                    readSource(handle, "TLS", candidates, () -> readTlsCandidates(handle, taps, since, until, config));
                }

                if (config.enabledSources().contains(BeaconType.SOCKS)) {
                    readSource(handle, "SOCKS", candidates, () -> readSocksCandidates(handle, taps, since, until, config));
                }

                if (config.enabledSources().contains(BeaconType.DNS)) {
                    readSource(handle, "DNS", candidates, () -> readDnsCandidates(handle, taps, since, until, config));
                }

                if (config.enabledSources().contains(BeaconType.TCP) || config.enabledSources().contains(BeaconType.UDP)
                        || config.enabledSources().contains(BeaconType.HTTP)
                        || config.enabledSources().contains(BeaconType.SSH)) {
                    readSource(handle, "L4", candidates, () -> readL4Candidates(handle, taps, since, until, config));
                }
            }

            LOG.debug("Analyzing [{}] beacon candidates of tenant [{}/{}].",
                    candidates.size(), organizationId, tenantId);

            processCandidates(handle, candidates, config, organizationId, tenantId);
        });
    }

    private void readSource(Handle handle, String name, List<Candidate> target, Supplier<List<Candidate>> reader) {
        String savepoint = "beacon_source_" + name.toLowerCase();
        handle.savepoint(savepoint);
        try {
            target.addAll(reader.get());
            handle.release(savepoint);
        } catch (Exception e) {
            LOG.error("Could not read [{}] beacon candidates. Skipping this data source.", name, e);
            handle.rollbackToSavepoint(savepoint);
        }
    }

    private void processCandidates(Handle handle,
                                   List<Candidate> candidates,
                                   BeaconsConfiguration config,
                                   UUID organizationId,
                                   UUID tenantId) {
        Map<String, BeaconEntry> knownBeacons = Maps.newHashMap();
        for (BeaconEntry beacon : beacons.findAllBeaconsOfTenant(handle, organizationId, tenantId)) {
            knownBeacons.put(beacon.destinationKey(), beacon);
        }

        Map<String, BeaconSourceEntry> knownSources = Maps.newHashMap();
        for (BeaconSourceEntry source : beacons.findAllSourcesOfBeacons(handle,
                knownBeacons.values().stream().map(BeaconEntry::id).collect(Collectors.toList()))) {
            knownSources.put(sourceKey(source.beaconId(), source.sourceAddress(), source.sourceMac()), source);
        }

        double tolerance = config.maximumJitterPercent() / 100.0;
        double minimumRegularity = config.minimumRegularityPercent() / 100.0;

        Map<BeaconExclusionEntry, BeaconExclusionMatcher> exclusions = beacons.exclusions()
                .compileEnabled(handle, organizationId, tenantId);
        Map<Long, Long> exclusionMatches = Maps.newHashMap();

        Map<Long, Candidate> touchedBeacons = Maps.newHashMap();
        Set<Long> beaconsWithNewCheckins = new HashSet<>();

        for (Candidate candidate : candidates) {
            try {
                if (!config.enabledSources().contains(candidate.type)) {
                    continue;
                }

                if (candidate.siteLocal && !config.includeSiteLocalDestinations()) {
                    continue;
                }

                if (isMulticast(candidate.destinationAddress)) {
                    continue;
                }

                BeaconExclusionEntry exclusion = findMatchingExclusion(exclusions, candidate);
                if (exclusion != null) {
                    exclusionMatches.merge(exclusion.id(), 1L, Long::sum);
                    continue;
                }

                int minimumInterval = candidate.type == BeaconType.DNS
                        ? config.dnsMinimumIntervalSeconds() : config.minimumIntervalSeconds();

                Duration collapseWindow = Duration.millis(Math.min(
                        MAXIMUM_COLLAPSE_WINDOW.getMillis(), (minimumInterval * 1000L) / 2
                ));

                IntervalAnalysis.Result analysis = IntervalAnalysis.analyze(
                        candidate.timestamps, collapseWindow, tolerance
                );

                boolean isBeacon = IntervalAnalysis.isBeacon(
                        analysis, config.minimumCheckins(), minimumInterval, minimumRegularity
                );

                BeaconEntry beacon = knownBeacons.get(candidate.destinationKey);

                if (beacon != null && isMoreSpecificType(candidate.type, beacon.type())) {
                    // A generic TCP/UDP beacon turned out to carry a known protocol. Upgrade its type.
                    beacons.updateBeaconType(handle, beacon.id(), candidate.type);
                    beacon = beacons.findBeaconById(handle, beacon.id()).orElse(beacon);
                    knownBeacons.put(beacon.destinationKey(), beacon);
                }

                BeaconSourceEntry source = beacon == null ? null
                        : knownSources.get(sourceKey(beacon.id(), candidate.sourceAddress, candidate.sourceMac));

                boolean newBeacon = false;
                boolean newSource = false;

                if (source == null) {
                    // Unknown source. Only becomes part of a beacon if it is actually beaconing.
                    if (!isBeacon) {
                        continue;
                    }

                    if (beacon == null) {
                        beacon = beacons.createBeacon(
                                handle,
                                organizationId,
                                tenantId,
                                candidate.type,
                                candidate.destinationKey,
                                candidate.destinationHost,
                                candidate.destinationAddress,
                                candidate.destinationPort,
                                candidate.siteLocal,
                                serializeEnrichment(candidate.enrichment),
                                analysis.firstSeen(),
                                analysis.lastSeen()
                        );
                        knownBeacons.put(beacon.destinationKey(), beacon);
                        newBeacon = true;
                    }

                    source = beacons.createSource(
                            handle, beacon.id(), candidate.sourceAddress, candidate.sourceMac, analysis
                    );
                    knownSources.put(sourceKey(beacon.id(), source.sourceAddress(), source.sourceMac()), source);
                    newSource = true;
                }

                // Record the check-ins of this source. Skips check-ins already recorded by a previous run.
                int newCheckins = recordCheckins(handle, beacon.id(), source.id(), candidate, analysis);
                beacons.updateSourceStatistics(handle, source.id(), analysis, newCheckins);

                touchedBeacons.put(beacon.id(), candidate);
                if (newCheckins > 0) {
                    beaconsWithNewCheckins.add(beacon.id());
                }

                if (newBeacon && config.alertOnNewBeacon()) {
                    raiseNewBeaconAlert(beacon, candidate, organizationId, tenantId);
                } else if (newSource && config.alertOnNewSource()) {
                    raiseNewSourceAlert(beacon, source, organizationId, tenantId);
                }
            } catch (Exception e) {
                LOG.error("Could not process beacon candidate [{}/{}] of tenant [{}/{}]. Skipping.",
                        candidate.type, candidate.destinationKey, organizationId, tenantId, e);
            }
        }

        for (Map.Entry<Long, Long> match : exclusionMatches.entrySet()) {
            beacons.exclusions().recordMatches(handle, match.getKey(), match.getValue());
        }

        // Refresh aggregates and raise activity alerts for all beacons that were touched in this run.
        for (Map.Entry<Long, Candidate> touched : touchedBeacons.entrySet()) {
            Candidate candidate = touched.getValue();
            beacons.refreshBeaconAggregates(
                    handle,
                    touched.getKey(),
                    candidate.destinationAddress,
                    serializeEnrichment(candidate.enrichment)
            );

            if (!beaconsWithNewCheckins.contains(touched.getKey())) {
                continue;
            }

            Optional<BeaconEntry> refreshed = beacons.findBeaconById(handle, touched.getKey());
            if (refreshed.isEmpty()) {
                continue;
            }

            BeaconClassification classification;
            try {
                classification = BeaconClassification.valueOf(refreshed.get().classification());
            } catch (IllegalArgumentException e) {
                LOG.error("Unknown classification [{}] of beacon [{}].",
                        refreshed.get().classification(), refreshed.get().uuid());
                continue;
            }

            if (classification == BeaconClassification.SUSPICIOUS && config.alertOnSuspiciousActivity()) {
                raiseActivityAlert(refreshed.get(), DetectionType.BEACON_ACTIVITY_SUSPICIOUS, organizationId, tenantId);
            } else if (classification == BeaconClassification.MALICIOUS && config.alertOnMaliciousActivity()) {
                raiseActivityAlert(refreshed.get(), DetectionType.BEACON_ACTIVITY_MALICIOUS, organizationId, tenantId);
            }
        }
    }

    @Nullable
    private static BeaconExclusionEntry findMatchingExclusion(Map<BeaconExclusionEntry, BeaconExclusionMatcher> exclusions,
                                                              Candidate candidate) {
        for (Map.Entry<BeaconExclusionEntry, BeaconExclusionMatcher> e : exclusions.entrySet()) {
            if (e.getValue().matches(candidate.type.name(), candidate.destinationHost, candidate.destinationAddress,
                    candidate.destinationPort, candidate.sourceAddress, candidate.sourceMac)) {
                return e.getKey();
            }
        }

        return null;
    }

    private int recordCheckins(Handle handle,
                               long beaconId,
                               long sourceId,
                               Candidate candidate,
                               IntervalAnalysis.Result analysis) {
        List<DateTime> checkins = analysis.checkins();
        if (checkins.isEmpty()) {
            return 0;
        }

        List<BeaconCheckinRecord> records = Lists.newArrayList();
        for (DateTime checkin : checkins) {
            records.add(new BeaconCheckinRecord(checkin, candidate.l4Type));
        }

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < candidate.timestamps.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing(candidate.timestamps::get));

        int j = 0;
        for (int i : order) {
            DateTime ts = candidate.timestamps.get(i);
            while (j + 1 < checkins.size() && !checkins.get(j + 1).isAfter(ts)) {
                j++;
            }

            records.get(j).addConnection(
                    elementAt(candidate.checkinDestinationAddresses, i),
                    elementAt(candidate.checkinDestinationMacs, i),
                    elementAt(candidate.checkinTapUuids, i),
                    elementAt(candidate.checkinSessionKeys, i),
                    elementAt(candidate.checkinBytesRx, i),
                    elementAt(candidate.checkinBytesTx, i),
                    elementAt(candidate.checkinBytesTotal, i)
            );
        }

        return beacons.insertCheckins(handle, beaconId, sourceId, records);
    }

    @Nullable
    private static <T> T elementAt(@Nullable List<T> list, int index) {
        if (list == null || index >= list.size()) {
            return null;
        }

        return list.get(index);
    }

    private List<Candidate> readL4Candidates(Handle handle,
                                             List<UUID> taps,
                                             DateTime since,
                                             DateTime until,
                                             BeaconsConfiguration config) {
        // Multicast destinations (mDNS, SSDP, etc) are periodic by nature and never a beacon.
        String siteLocalCondition = "AND destination_address_is_multicast = false " +
                (config.includeSiteLocalDestinations()
                        ? "" : "AND destination_address_is_site_local = false AND destination_address_is_loopback = false ");

        String excludedTags = String.join(",", EXCLUDED_L4_TAGS);

        List<Candidate> result = Lists.newArrayList();
        handle.createQuery("WITH candidates AS (SELECT l4_type, source_address, source_mac, destination_address, " +
                        "destination_port FROM l4_sessions WHERE tap_uuid IN (<taps>) AND start_time >= :since " +
                        "AND start_time <= :until " + siteLocalCondition +
                        "AND NOT jsonb_exists_any(COALESCE(tags, '[]'::jsonb), string_to_array(:excluded_tags, ',')) " +
                        "GROUP BY l4_type, source_address, source_mac, destination_address, destination_port " +
                        "HAVING COUNT(*) >= :minimum_checkins) " +
                        "SELECT s.l4_type, host(s.source_address) AS source_address, s.source_mac, " +
                        "host(s.destination_address) AS destination_address, s.destination_port, " +
                        "bool_or(s.destination_address_is_site_local) AS site_local, " +
                        "bool_or(jsonb_exists(COALESCE(s.tags, '[]'::jsonb), 'HTTP')) AS has_http, " +
                        "bool_or(jsonb_exists(COALESCE(s.tags, '[]'::jsonb), 'SSH')) AS has_ssh, " +
                        "(array_agg(s.fingerprint ORDER BY s.start_time DESC))[1] AS fingerprint, " +
                        "array_agg(s.start_time ORDER BY s.start_time) AS timestamps, " +
                        "array_agg(s.tap_uuid::text ORDER BY s.start_time) AS tap_uuids, " +
                        "array_agg(s.session_key ORDER BY s.start_time) AS session_keys, " +
                        "array_agg(s.destination_mac ORDER BY s.start_time) AS destination_macs, " +
                        "array_agg(s.bytes_rx_count ORDER BY s.start_time) AS bytes_rx, " +
                        "array_agg(s.bytes_tx_count ORDER BY s.start_time) AS bytes_tx " +
                        "FROM l4_sessions s JOIN candidates c ON c.l4_type = s.l4_type " +
                        "AND c.source_address = s.source_address AND c.source_mac IS NOT DISTINCT FROM s.source_mac " +
                        "AND c.destination_address = s.destination_address AND c.destination_port = s.destination_port " +
                        "WHERE s.tap_uuid IN (<taps>) AND s.start_time >= :since AND s.start_time <= :until " +
                        "AND NOT jsonb_exists_any(COALESCE(s.tags, '[]'::jsonb), string_to_array(:excluded_tags, ',')) " +
                        "GROUP BY s.l4_type, s.source_address, s.source_mac, s.destination_address, s.destination_port")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .bind("excluded_tags", excludedTags)
                .bind("minimum_checkins", config.minimumCheckins())
                .mapToMap()
                .forEach(row -> {
                    try {
                        BeaconType type;
                        if (Boolean.TRUE.equals(row.get("has_ssh"))) {
                            type = BeaconType.SSH;
                        } else if (Boolean.TRUE.equals(row.get("has_http"))) {
                            type = BeaconType.HTTP;
                        } else if ("UDP".equals(row.get("l4_type"))) {
                            type = BeaconType.UDP;
                        } else {
                            type = BeaconType.TCP;
                        }

                        String destinationAddress = (String) row.get("destination_address");
                        int destinationPort = (Integer) row.get("destination_port");
                        List<DateTime> timestamps = timestampArray(row.get("timestamps"));

                        Map<String, Object> enrichment = Maps.newHashMap();
                        enrichment.put("l4_type", row.get("l4_type"));
                        if (row.get("fingerprint") != null) {
                            enrichment.put("fingerprint", row.get("fingerprint"));
                        }

                        Candidate candidate = new Candidate(
                                type,
                                "ip:" + destinationAddress + ":" + destinationPort,
                                null,
                                destinationAddress,
                                destinationPort,
                                Boolean.TRUE.equals(row.get("site_local")),
                                (String) row.get("source_address"),
                                (String) row.get("source_mac"),
                                timestamps,
                                Collections.nCopies(timestamps.size(), destinationAddress),
                                stringArray(row.get("tap_uuids")),
                                enrichment
                        );
                        candidate.l4Type = (String) row.get("l4_type");
                        candidate.checkinSessionKeys = stringArray(row.get("session_keys"));
                        candidate.checkinDestinationMacs = stringArray(row.get("destination_macs"));
                        candidate.checkinBytesRx = longArray(row.get("bytes_rx"));
                        candidate.checkinBytesTx = longArray(row.get("bytes_tx"));
                        result.add(candidate);
                    } catch (Exception e) {
                        LOG.error("Could not read L4 beacon candidate.", e);
                    }
                });

        return result;
    }

    private List<Candidate> readTlsCandidates(Handle handle,
                                              List<UUID> taps,
                                              DateTime since,
                                              DateTime until,
                                              BeaconsConfiguration config) {
        List<Candidate> result = Lists.newArrayList();
        handle.createQuery("WITH candidates AS (SELECT source_address, source_mac, " +
                        "COALESCE(server_name, host(destination_address)) AS destination_host, destination_port " +
                        "FROM tls_sessions WHERE tap_uuid IN (<taps>) AND established_at >= :since " +
                        "AND established_at <= :until " +
                        "GROUP BY source_address, source_mac, COALESCE(server_name, host(destination_address)), destination_port " +
                        "HAVING COUNT(*) >= :minimum_checkins) " +
                        "SELECT host(t.source_address) AS source_address, t.source_mac, " +
                        "COALESCE(t.server_name, host(t.destination_address)) AS destination_host, t.destination_port, " +
                        "bool_or(t.server_name IS NOT NULL) AS has_server_name, " +
                        "(array_agg(host(t.destination_address) ORDER BY t.established_at DESC))[1] AS destination_address, " +
                        "(array_agg(t.ja4 ORDER BY t.established_at DESC))[1] AS ja4, " +
                        "(array_agg(t.version ORDER BY t.established_at DESC))[1] AS version, " +
                        "(array_agg(t.alpn_selected ORDER BY t.established_at DESC))[1] AS alpn_selected, " +
                        "(array_agg(encode(t.server_leaf_sha256, 'hex') ORDER BY t.established_at DESC))[1] AS server_leaf_sha256, " +
                        "(array_agg(cert.common_name ORDER BY t.established_at DESC))[1] AS server_leaf_common_name, " +
                        "(array_agg(cert.issuer ORDER BY t.established_at DESC))[1] AS server_leaf_issuer, " +
                        "(array_agg(t.server_leaf_self_signed ORDER BY t.established_at DESC))[1] AS server_leaf_self_signed, " +
                        "(array_agg(t.chain_trusted ORDER BY t.established_at DESC))[1] AS chain_trusted, " +
                        "(array_agg(t.sni_matches_certificate ORDER BY t.established_at DESC))[1] AS sni_matches_certificate, " +
                        "array_agg(t.established_at ORDER BY t.established_at) AS timestamps, " +
                        "array_agg(host(t.destination_address) ORDER BY t.established_at) AS destination_addresses, " +
                        "array_agg(t.tap_uuid::text ORDER BY t.established_at) AS tap_uuids, " +
                        "array_agg(t.tcp_session_key ORDER BY t.established_at) AS session_keys, " +
                        "array_agg(t.destination_mac ORDER BY t.established_at) AS destination_macs, " +
                        "array_agg(t.server_app_data_bytes ORDER BY t.established_at) AS bytes_rx, " +
                        "array_agg(t.client_app_data_bytes ORDER BY t.established_at) AS bytes_tx, " +
                        "array_agg(t.bytes_count ORDER BY t.established_at) AS bytes_total " +
                        "FROM tls_sessions t JOIN candidates c ON c.source_address = t.source_address " +
                        "AND c.source_mac IS NOT DISTINCT FROM t.source_mac " +
                        "AND c.destination_host = COALESCE(t.server_name, host(t.destination_address)) " +
                        "AND c.destination_port = t.destination_port " +
                        "LEFT JOIN certificates cert ON cert.sha256 = t.server_leaf_sha256 " +
                        "WHERE t.tap_uuid IN (<taps>) AND t.established_at >= :since AND t.established_at <= :until " +
                        "GROUP BY t.source_address, t.source_mac, COALESCE(t.server_name, host(t.destination_address)), t.destination_port")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .bind("minimum_checkins", config.minimumCheckins())
                .mapToMap()
                .forEach(row -> {
                    try {
                        boolean hasServerName = Boolean.TRUE.equals(row.get("has_server_name"));
                        String destinationHost = (String) row.get("destination_host");
                        String destinationAddress = (String) row.get("destination_address");
                        int destinationPort = (Integer) row.get("destination_port");
                        List<String> destinationAddresses = stringArray(row.get("destination_addresses"));

                        Map<String, Object> enrichment = Maps.newHashMap();
                        putIfNotNull(enrichment, "server_name", hasServerName ? destinationHost : null);
                        putIfNotNull(enrichment, "ja4", row.get("ja4"));
                        putIfNotNull(enrichment, "version", row.get("version"));
                        putIfNotNull(enrichment, "alpn_selected", row.get("alpn_selected"));
                        putIfNotNull(enrichment, "server_leaf_sha256", row.get("server_leaf_sha256"));
                        putIfNotNull(enrichment, "server_leaf_common_name", row.get("server_leaf_common_name"));
                        putIfNotNull(enrichment, "server_leaf_issuer", row.get("server_leaf_issuer"));
                        putIfNotNull(enrichment, "server_leaf_self_signed", row.get("server_leaf_self_signed"));
                        putIfNotNull(enrichment, "chain_trusted", row.get("chain_trusted"));
                        putIfNotNull(enrichment, "sni_matches_certificate", row.get("sni_matches_certificate"));

                        Candidate candidate = new Candidate(
                                BeaconType.TLS,
                                (hasServerName ? "sni:" : "ip:") + destinationHost + ":" + destinationPort,
                                hasServerName ? destinationHost : null,
                                destinationAddress,
                                destinationPort,
                                anySiteLocal(destinationAddresses),
                                (String) row.get("source_address"),
                                (String) row.get("source_mac"),
                                timestampArray(row.get("timestamps")),
                                destinationAddresses,
                                stringArray(row.get("tap_uuids")),
                                enrichment
                        );
                        candidate.l4Type = "TCP";
                        candidate.checkinSessionKeys = stringArray(row.get("session_keys"));
                        candidate.checkinDestinationMacs = stringArray(row.get("destination_macs"));
                        candidate.checkinBytesRx = longArray(row.get("bytes_rx"));
                        candidate.checkinBytesTx = longArray(row.get("bytes_tx"));
                        candidate.checkinBytesTotal = longArray(row.get("bytes_total"));
                        result.add(candidate);
                    } catch (Exception e) {
                        LOG.error("Could not read TLS beacon candidate.", e);
                    }
                });

        return result;
    }

    private List<Candidate> readSocksCandidates(Handle handle,
                                                List<UUID> taps,
                                                DateTime since,
                                                DateTime until,
                                                BeaconsConfiguration config) {
        List<Candidate> result = Lists.newArrayList();
        handle.createQuery("WITH tunnels AS (SELECT l.source_address, l.source_mac, " +
                        "l.destination_address AS proxy_address, l.destination_port AS proxy_port, l.destination_mac AS proxy_mac, " +
                        "COALESCE(s.tunneled_destination_host, host(s.tunneled_destination_address)) AS destination_host, " +
                        "host(s.tunneled_destination_address) AS destination_address, s.tunneled_destination_port AS destination_port, " +
                        "s.socks_type, s.username, s.established_at, s.tap_uuid, s.tcp_session_key, s.tunneled_bytes " +
                        "FROM socks_tunnels s JOIN l4_sessions l ON l.tap_uuid = s.tap_uuid " +
                        "AND l.session_key = s.tcp_session_key AND l.l4_type = 'TCP' " +
                        "WHERE s.tap_uuid IN (<taps>) AND s.established_at >= :since AND s.established_at <= :until " +
                        "AND COALESCE(s.tunneled_destination_host, host(s.tunneled_destination_address)) IS NOT NULL), " +
                        "candidates AS (SELECT source_address, source_mac, destination_host, destination_port " +
                        "FROM tunnels GROUP BY source_address, source_mac, destination_host, destination_port " +
                        "HAVING COUNT(*) >= :minimum_checkins) " +
                        "SELECT host(t.source_address) AS source_address, t.source_mac, t.destination_host, " +
                        "t.destination_port, " +
                        "(array_agg(t.destination_address ORDER BY t.established_at DESC))[1] AS destination_address, " +
                        "(array_agg(host(t.proxy_address) ORDER BY t.established_at DESC))[1] AS proxy_address, " +
                        "(array_agg(t.proxy_port ORDER BY t.established_at DESC))[1] AS proxy_port, " +
                        "(array_agg(t.socks_type ORDER BY t.established_at DESC))[1] AS socks_type, " +
                        "(array_agg(t.username ORDER BY t.established_at DESC))[1] AS username, " +
                        "array_agg(t.established_at ORDER BY t.established_at) AS timestamps, " +
                        "array_agg(host(t.proxy_address) ORDER BY t.established_at) AS proxy_addresses, " +
                        "array_agg(t.tap_uuid::text ORDER BY t.established_at) AS tap_uuids, " +
                        "array_agg(t.tcp_session_key ORDER BY t.established_at) AS session_keys, " +
                        "array_agg(t.proxy_mac ORDER BY t.established_at) AS destination_macs, " +
                        "array_agg(t.tunneled_bytes ORDER BY t.established_at) AS bytes_total " +
                        "FROM tunnels t JOIN candidates c ON c.source_address = t.source_address " +
                        "AND c.source_mac IS NOT DISTINCT FROM t.source_mac " +
                        "AND c.destination_host = t.destination_host AND c.destination_port = t.destination_port " +
                        "GROUP BY t.source_address, t.source_mac, t.destination_host, t.destination_port")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .bind("minimum_checkins", config.minimumCheckins())
                .mapToMap()
                .forEach(row -> {
                    try {
                        String destinationHost = (String) row.get("destination_host");
                        String destinationAddress = (String) row.get("destination_address");
                        int destinationPort = (Integer) row.get("destination_port");
                        String proxyAddress = (String) row.get("proxy_address");

                        // Tunneled destination addresses are only known for some SOCKS requests. We fall back to
                        // the proxy address to decide if this is a site-local destination.
                        boolean siteLocal = destinationAddress != null && isValidAddress(destinationAddress)
                                ? isSiteLocal(destinationAddress) : isSiteLocal(proxyAddress);

                        Map<String, Object> enrichment = Maps.newHashMap();
                        putIfNotNull(enrichment, "socks_type", row.get("socks_type"));
                        putIfNotNull(enrichment, "username", row.get("username"));
                        putIfNotNull(enrichment, "proxy_address", proxyAddress);
                        putIfNotNull(enrichment, "proxy_port", row.get("proxy_port"));
                        putIfNotNull(enrichment, "tunneled_destination_host", row.get("destination_host"));

                        Candidate candidate = new Candidate(
                                BeaconType.SOCKS,
                                "socks:" + destinationHost + ":" + destinationPort,
                                destinationHost,
                                destinationAddress != null && isValidAddress(destinationAddress) ? destinationAddress : null,
                                destinationPort,
                                siteLocal,
                                (String) row.get("source_address"),
                                (String) row.get("source_mac"),
                                timestampArray(row.get("timestamps")),
                                stringArray(row.get("proxy_addresses")),
                                stringArray(row.get("tap_uuids")),
                                enrichment
                        );
                        candidate.l4Type = "TCP";
                        candidate.checkinSessionKeys = stringArray(row.get("session_keys"));
                        candidate.checkinDestinationMacs = stringArray(row.get("destination_macs"));
                        candidate.checkinBytesTotal = longArray(row.get("bytes_total"));
                        result.add(candidate);
                    } catch (Exception e) {
                        LOG.error("Could not read SOCKS beacon candidate.", e);
                    }
                });

        return result;
    }

    private List<Candidate> readDnsCandidates(Handle handle,
                                              List<UUID> taps,
                                              DateTime since,
                                              DateTime until,
                                              BeaconsConfiguration config) {
        List<Candidate> result = Lists.newArrayList();
        handle.createQuery("WITH candidates AS (SELECT client_address, client_mac, " +
                        "COALESCE(data_value_etld, data_value) AS domain FROM dns_log " +
                        "WHERE tap_uuid IN (<taps>) AND data_type = 'query' AND timestamp >= :since " +
                        "AND timestamp <= :until AND data_value IS NOT NULL " +
                        "GROUP BY client_address, client_mac, COALESCE(data_value_etld, data_value) " +
                        "HAVING COUNT(*) >= :minimum_checkins) " +
                        "SELECT host(d.client_address) AS source_address, d.client_mac AS source_mac, " +
                        "COALESCE(d.data_value_etld, d.data_value) AS domain, " +
                        "array_agg(DISTINCT d.dns_type) AS dns_types, " +
                        "array_agg(DISTINCT host(d.server_address)) AS servers, " +
                        "array_agg(DISTINCT d.data_value) AS names, " +
                        "array_agg(d.timestamp ORDER BY d.timestamp) AS timestamps, " +
                        "array_agg(host(d.server_address) ORDER BY d.timestamp) AS server_addresses, " +
                        "array_agg(d.tap_uuid::text ORDER BY d.timestamp) AS tap_uuids, " +
                        "array_agg(d.server_mac ORDER BY d.timestamp) AS destination_macs " +
                        "FROM dns_log d JOIN candidates c ON c.client_address = d.client_address " +
                        "AND c.client_mac IS NOT DISTINCT FROM d.client_mac " +
                        "AND c.domain = COALESCE(d.data_value_etld, d.data_value) " +
                        "WHERE d.tap_uuid IN (<taps>) AND d.data_type = 'query' AND d.timestamp >= :since " +
                        "AND d.timestamp <= :until " +
                        "GROUP BY d.client_address, d.client_mac, COALESCE(d.data_value_etld, d.data_value)")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .bind("minimum_checkins", config.minimumCheckins())
                .mapToMap()
                .forEach(row -> {
                    try {
                        String domain = (String) row.get("domain");

                        if (domain == null || domain.isEmpty() || isReverseLookupName(domain)) {
                            return;
                        }

                        List<String> names = stringArray(row.get("names"));

                        Map<String, Object> enrichment = Maps.newHashMap();
                        enrichment.put("dns_types", stringArray(row.get("dns_types")));
                        enrichment.put("dns_servers", stringArray(row.get("servers")));
                        enrichment.put("distinct_name_count", names.size());
                        // Enough names to show what the beacon queries, without blowing up the enrichment.
                        enrichment.put("names", names.stream().limit(25).collect(Collectors.toList()));

                        Candidate candidate = new Candidate(
                                BeaconType.DNS,
                                "dns:" + domain,
                                domain,
                                null,
                                null,
                                isLocalDomainName(domain),
                                (String) row.get("source_address"),
                                (String) row.get("source_mac"),
                                timestampArray(row.get("timestamps")),
                                stringArray(row.get("server_addresses")),
                                stringArray(row.get("tap_uuids")),
                                enrichment
                        );
                        candidate.l4Type = "UDP";
                        candidate.checkinDestinationMacs = stringArray(row.get("destination_macs"));
                        result.add(candidate);
                    } catch (Exception e) {
                        LOG.error("Could not read DNS beacon candidate.", e);
                    }
                });

        return result;
    }

    private void raiseNewBeaconAlert(BeaconEntry beacon, Candidate candidate, UUID organizationId, UUID tenantId) {
        Map<String, String> attributes = beaconAttributes(beacon);
        attributes.put("source_address", candidate.sourceAddress);

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                null,
                DetectionType.BEACON_NEW,
                Subsystem.ETHERNET,
                "New " + BeaconType.valueOf(beacon.type()).getHumanReadable() + " \"" + beacon.name()
                        + "\" to [" + destinationDescription(beacon) + "] detected.",
                attributes,
                Set.of("beacon_uuid")
        );
    }

    private void raiseNewSourceAlert(BeaconEntry beacon, BeaconSourceEntry source, UUID organizationId, UUID tenantId) {
        Map<String, String> attributes = beaconAttributes(beacon);
        attributes.put("source_address", source.sourceAddress());
        if (source.sourceMac() != null) {
            attributes.put("source_mac", source.sourceMac());
        }

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                null,
                DetectionType.BEACON_NEW_SOURCE,
                Subsystem.ETHERNET,
                "New device [" + source.sourceAddress() + "] joined " + BeaconType.valueOf(beacon.type()).getHumanReadable()
                        + " \"" + beacon.name() + "\" to [" + destinationDescription(beacon) + "].",
                attributes,
                Set.of("beacon_uuid", "source_address")
        );
    }

    private void raiseActivityAlert(BeaconEntry beacon, DetectionType type, UUID organizationId, UUID tenantId) {
        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                null,
                type,
                Subsystem.ETHERNET,
                "Activity on " + beacon.classification().toLowerCase() + " " + BeaconType.valueOf(beacon.type()).getHumanReadable()
                        + " \"" + beacon.name() + "\" to [" + destinationDescription(beacon) + "] detected.",
                beaconAttributes(beacon),
                Set.of("beacon_uuid")
        );
    }

    private Map<String, String> beaconAttributes(BeaconEntry beacon) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("beacon_uuid", beacon.uuid().toString());
        attributes.put("beacon_name", beacon.name());
        attributes.put("beacon_type", beacon.type());
        attributes.put("classification", beacon.classification());
        attributes.put("destination", destinationDescription(beacon));
        if (beacon.destinationHost() != null) {
            attributes.put("destination_host", beacon.destinationHost());
        }
        if (beacon.destinationAddress() != null) {
            attributes.put("destination_address", beacon.destinationAddress());
        }
        if (beacon.destinationPort() != null) {
            attributes.put("destination_port", String.valueOf(beacon.destinationPort()));
        }

        return attributes;
    }

    public static String destinationDescription(BeaconEntry beacon) {
        String host = beacon.destinationHost() != null ? beacon.destinationHost() : beacon.destinationAddress();
        if (beacon.destinationPort() == null) {
            return host;
        }

        return host + ":" + beacon.destinationPort();
    }

    static boolean isMoreSpecificType(BeaconType candidateType, String existingType) {
        boolean existingIsGeneric = BeaconType.TCP.name().equals(existingType)
                || BeaconType.UDP.name().equals(existingType);
        boolean candidateIsSpecific = candidateType == BeaconType.HTTP || candidateType == BeaconType.SSH
                || candidateType == BeaconType.TLS;

        return existingIsGeneric && candidateIsSpecific;
    }

    private static String sourceKey(long beaconId, String address, @Nullable String mac) {
        return beaconId + "/" + address + "/" + (mac == null ? "" : mac.toUpperCase());
    }

    @Nullable
    private String serializeEnrichment(Map<String, Object> enrichment) {
        if (enrichment == null || enrichment.isEmpty()) {
            return null;
        }

        try {
            return om.writeValueAsString(enrichment);
        } catch (JacksonException e) {
            LOG.error("Could not serialize beacon enrichment.", e);
            return null;
        }
    }

    private static void putIfNotNull(Map<String, Object> map, String key, @Nullable Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static List<DateTime> timestampArray(Object value) throws SQLException {
        List<DateTime> result = Lists.newArrayList();
        if (value == null) {
            return result;
        }

        for (Object o : (Object[]) ((Array) value).getArray()) {
            if (o != null) {
                result.add(new DateTime(((Timestamp) o).getTime()));
            }
        }

        return result;
    }

    private static List<Long> longArray(Object value) throws SQLException {
        List<Long> result = Lists.newArrayList();
        if (value == null) {
            return result;
        }

        for (Object o : (Object[]) ((Array) value).getArray()) {
            result.add(o == null ? null : ((Number) o).longValue());
        }

        return result;
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

    private static boolean anySiteLocal(List<String> addresses) {
        for (String address : addresses) {
            if (address != null && isSiteLocal(address)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isValidAddress(String address) {
        if (address == null) {
            return false;
        }

        return address.matches("^[0-9a-fA-F:.]+$") && (address.contains(":") || address.chars().filter(c -> c == '.').count() == 3);
    }

    public static boolean isMulticast(@Nullable String address) {
        if (!isValidAddress(address)) {
            return false;
        }

        try {
            return InetAddress.getByName(address).isMulticastAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public static boolean isSiteLocal(String address) {
        if (!isValidAddress(address)) {
            return false;
        }

        try {
            InetAddress a = InetAddress.getByName(address);
            return a.isSiteLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public static boolean isReverseLookupName(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".in-addr.arpa") || lower.endsWith(".ip6.arpa")
                || lower.equals("in-addr.arpa") || lower.equals("ip6.arpa");
    }

    public static boolean isLocalDomainName(String name) {
        String lower = name.toLowerCase();
        return !lower.contains(".") || lower.endsWith(".local") || lower.endsWith(".localdomain")
                || lower.endsWith(".lan") || lower.endsWith(".home") || lower.endsWith(".internal")
                || lower.endsWith(".home.arpa") || lower.endsWith(".arpa") || lower.endsWith(".corp")
                || lower.endsWith(".intranet") || lower.endsWith(".private");
    }

}
