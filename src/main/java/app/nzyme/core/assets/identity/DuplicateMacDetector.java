package app.nzyme.core.assets.identity;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.identity.db.AssetIdentityExclusionEntry;
import app.nzyme.core.assets.identity.db.DuplicateMacFindingEntry;
import app.nzyme.core.assets.identity.db.DuplicateMacStack;
import app.nzyme.core.assets.profile.AssetProfileBatch;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.taps.Tap;
import app.nzyme.plugin.Subsystem;
import app.nzyme.core.assets.identity.db.DuplicateMacDestination;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.sql.Array;
import java.sql.SQLException;
import app.nzyme.core.ethernet.l4.tcp.TcpSignature;

import java.util.*;
import java.util.stream.Collectors;

public class DuplicateMacDetector {

    private static final Logger LOG = LogManager.getLogger(DuplicateMacDetector.class);

    private static final int MAXIMUM_SAMPLES = 10;
    private static final int MAXIMUM_TAP_SAMPLES = 25;
    private static final int MAXIMUM_DESTINATIONS = 25;

    // The stack count column keeps the true number, so randomized TCP options cannot grow a finding without bound.
    private static final int MAXIMUM_STACKS = 10;

    private static final ObjectMapper OM = JsonMapper.builder().build();

    private final NzymeNode nzyme;
    private final AssetIdentity identity;

    public DuplicateMacDetector(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.identity = nzyme.getAssetsManager().identity();
    }

    private record StackRow(String mac, String fingerprint, long sessions, DateTime firstSeen, DateTime lastSeen,
                            List<String> taps, List<String> sourceAddresses,
                            List<DuplicateMacDestination> destinations, long destinationCount,
                            int sourcePortMin, int sourcePortMax,
                            List<String> signatures) { }

    public void runForTenant(UUID organizationId, UUID tenantId, AssetIdentityConfiguration config) {
        if (!config.duplicateDetectionEnabled()) {
            LOG.debug("Duplicate MAC detection disabled for tenant [{}/{}]. Skipping.", organizationId, tenantId);
            return;
        }

        List<UUID> taps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)
                .stream().map(Tap::uuid).collect(Collectors.toList());

        if (taps.isEmpty()) {
            return;
        }

        DateTime until = DateTime.now();
        DateTime since = until.minusMinutes(config.duplicateWindowMinutes());

        nzyme.getDatabase().useTransaction(handle -> {
            boolean locked = handle.createQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                    .bind("key", "assets_identity_duplicate:" + organizationId + ":" + tenantId)
                    .mapTo(Boolean.class)
                    .one();

            if (!locked) {
                LOG.debug("Duplicate MAC detection for tenant [{}/{}] is running on another node. Skipping.",
                        organizationId, tenantId);
                return;
            }

            Map<String, List<StackRow>> byMac = readStacks(handle, taps, since, until, config.duplicateMinimumSessions(), null);

            Map<String, DuplicateMacFindingEntry> activeFindings = Maps.newHashMap();
            for (DuplicateMacFindingEntry finding : identity.findActiveDuplicateFindingsOfTenant(handle, organizationId, tenantId, since)) {
                activeFindings.put(finding.mac(), finding);
            }

            Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> exclusions = identity.exclusions()
                    .compileEnabled(handle, organizationId, tenantId);
            Map<Long, Long> exclusionMatches = Maps.newHashMap();

            for (Map.Entry<String, List<StackRow>> entry : byMac.entrySet()) {
                String mac = entry.getKey();
                List<StackRow> stacks = entry.getValue();

                if (!AssetProfileBatch.isUsableMac(mac) || countDistinctStacks(stacks, config) < 2) {
                    continue;
                }

                try {
                    AssetIdentityExclusionEntry exclusion = AssetIdentityExclusions.findMatching(exclusions, mac,
                            AssetIdentityType.DUPLICATE_MAC);
                    if (exclusion != null) {
                        exclusionMatches.merge(exclusion.id(), 1L, Long::sum);
                        continue;
                    }

                    DuplicateMacFindingEntry finding = activeFindings.get(mac);
                    if (finding == null) {
                        DuplicateMacFindingEntry created = identity.createDuplicateFinding(
                                handle,
                                organizationId,
                                tenantId,
                                mac,
                                toStacks(stacks),
                                stacks.size(),
                                stacks.stream().mapToLong(StackRow::sessions).sum(),
                                tapsOf(stacks),
                                stacks.stream().map(StackRow::firstSeen).min(Comparator.naturalOrder()).orElse(since),
                                stacks.stream().map(StackRow::lastSeen).max(Comparator.naturalOrder()).orElse(until)
                        );

                        if (config.duplicateAlertOnNew()) {
                            raiseNewFindingAlert(created, organizationId, tenantId);
                        }
                    } else {
                        // Rebuilt over the whole lifetime of the finding so counts keep growing.
                        List<StackRow> lifetime = readStacks(handle, taps, finding.firstSeen(), until, config.duplicateMinimumSessions(), mac).get(mac);
                        if (lifetime == null || lifetime.isEmpty()) {
                            lifetime = stacks;
                        }

                        identity.updateDuplicateFinding(
                                handle,
                                finding.id(),
                                toStacks(lifetime),
                                lifetime.size(),
                                lifetime.stream().mapToLong(StackRow::sessions).sum(),
                                tapsOf(lifetime),
                                lifetime.stream().map(StackRow::lastSeen).max(Comparator.naturalOrder()).orElse(until)
                        );
                    }
                } catch (Exception e) {
                    LOG.error("Could not process duplicate MAC [{}] of tenant [{}/{}]. Skipping.",
                            mac, organizationId, tenantId, e);
                }
            }

            for (Map.Entry<Long, Long> match : exclusionMatches.entrySet()) {
                identity.exclusions().recordMatches(handle, match.getKey(), match.getValue());
            }
        });
    }

    public static String strongPart(String fingerprint) {
        return TcpSignature.comparisonKey(fingerprint);
    }

    private static int countDistinctStacks(List<StackRow> stacks, AssetIdentityConfiguration config) {
        Set<String> distinct = new HashSet<>();
        for (StackRow stack : stacks) {
            distinct.add(config.duplicateRequireStrongDifference() ? strongPart(stack.fingerprint) : stack.fingerprint);
        }

        return distinct.size();
    }

    private Map<String, List<StackRow>> readStacks(Handle handle, List<UUID> taps, DateTime since, DateTime until,
                                                   int minimumSessions, @Nullable String mac) {
        Map<String, List<StackRow>> result = Maps.newHashMap();

        // A session seen by several taps is one row per tap with different session keys, because the key includes
        // the start time each tap observed. The untimed key is addresses and ports only.
        handle.createQuery("SELECT source_mac AS mac, fingerprint, COUNT(DISTINCT untimed_session_key) AS sessions, " +
                        "MIN(start_time) AS first_seen, MAX(most_recent_segment_time) AS last_seen, " +
                        "array_agg(DISTINCT tap_uuid::text) AS taps, " +
                        "(SELECT array_agg(a.address) FROM (SELECT DISTINCT host(a2.source_address) AS address " +
                        "FROM l4_sessions a2 WHERE a2.tap_uuid IN (<taps>) AND a2.l4_type = 'TCP' " +
                        "AND a2.ip_hop_distance = 0 AND a2.source_mac = l4_sessions.source_mac " +
                        "AND a2.fingerprint = l4_sessions.fingerprint " +
                        "AND a2.start_time >= :since AND a2.start_time <= :until LIMIT :maximum_samples) a) AS source_addresses, " +
                        // The innermost query collapses the per-tap rows of a session so bytes are not counted once per tap.
                        "(SELECT COALESCE(jsonb_agg(jsonb_build_object('address', host(d.destination_address), " +
                        "'port', d.destination_port, 'sessions', d.n, 'bytes', d.bytes) ORDER BY d.n DESC), " +
                        "'[]'::jsonb) FROM (SELECT s.destination_address, s.destination_port, COUNT(*) AS n, " +
                        "SUM(s.bytes) AS bytes FROM (SELECT d2.untimed_session_key, d2.destination_address, " +
                        "d2.destination_port, MAX(COALESCE(d2.bytes_rx_count, 0) + COALESCE(d2.bytes_tx_count, 0)) AS bytes " +
                        "FROM l4_sessions d2 WHERE d2.tap_uuid IN (<taps>) AND d2.l4_type = 'TCP' " +
                        "AND d2.ip_hop_distance = 0 AND d2.source_mac = l4_sessions.source_mac " +
                        "AND d2.fingerprint = l4_sessions.fingerprint " +
                        "AND d2.start_time >= :since AND d2.start_time <= :until " +
                        "GROUP BY d2.untimed_session_key, d2.destination_address, d2.destination_port) s " +
                        "GROUP BY s.destination_address, s.destination_port " +
                        "ORDER BY n DESC LIMIT :maximum_destinations) d)::text AS destinations, " +
                        "COUNT(DISTINCT (destination_address, destination_port)) AS destination_count, " +
                        "MIN(source_port) AS source_port_min, MAX(source_port) AS source_port_max, " +
                        "(SELECT array_agg(g.signature) FROM (SELECT DISTINCT g2.signature FROM l4_sessions g2 " +
                        "WHERE g2.tap_uuid IN (<taps>) AND g2.l4_type = 'TCP' AND g2.ip_hop_distance = 0 " +
                        "AND g2.source_mac = l4_sessions.source_mac AND g2.fingerprint = l4_sessions.fingerprint " +
                        "AND g2.start_time >= :since AND g2.start_time <= :until AND g2.signature IS NOT NULL " +
                        "LIMIT :maximum_samples) g) AS signatures " +
                        "FROM l4_sessions WHERE tap_uuid IN (<taps>) AND l4_type = 'TCP' " +
                        "AND source_mac IS NOT NULL AND fingerprint IS NOT NULL AND ip_hop_distance = 0 " +
                        "AND start_time >= :since AND start_time <= :until " +
                        (mac == null ? "" : "AND source_mac = :mac ") +
                        "GROUP BY source_mac, fingerprint HAVING COUNT(DISTINCT untimed_session_key) >= :minimum_sessions")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .bind("minimum_sessions", minimumSessions)
                .bind("maximum_destinations", MAXIMUM_DESTINATIONS)
                .bind("maximum_samples", MAXIMUM_SAMPLES)
                .bind("mac", mac)
                .map((rs, ctx) -> new StackRow(
                        rs.getString("mac"),
                        rs.getString("fingerprint"),
                        rs.getLong("sessions"),
                        new DateTime(rs.getTimestamp("first_seen")),
                        new DateTime(rs.getTimestamp("last_seen")),
                        strings(rs.getArray("taps"), MAXIMUM_TAP_SAMPLES),
                        strings(rs.getArray("source_addresses"), MAXIMUM_SAMPLES),
                        OM.readValue(rs.getString("destinations"), new TypeReference<List<DuplicateMacDestination>>() {}),
                        rs.getLong("destination_count"),
                        rs.getInt("source_port_min"),
                        rs.getInt("source_port_max"),
                        strings(rs.getArray("signatures"), MAXIMUM_SAMPLES)
                ))
                .forEach(row -> result.computeIfAbsent(row.mac, k -> Lists.newArrayList()).add(row));

        return result;
    }

    private static List<String> strings(Array array, int maximum) throws SQLException {
        if (array == null) {
            return List.of();
        }

        List<String> result = Lists.newArrayList();
        for (Object o : (Object[]) array.getArray()) {
            if (o != null && result.size() < maximum) {
                result.add(o.toString());
            }
        }

        return result;
    }

    private static List<DuplicateMacStack> toStacks(List<StackRow> rows) {
        List<DuplicateMacStack> result = Lists.newArrayList();
        for (StackRow row : rows) {
            result.add(new DuplicateMacStack(
                    row.fingerprint,
                    row.signatures,
                    row.sessions,
                    row.firstSeen.toString(),
                    row.lastSeen.toString(),
                    row.sourceAddresses,
                    row.destinations,
                    row.destinationCount,
                    row.sourcePortMin,
                    row.sourcePortMax,
                    row.taps
            ));
        }

        result.sort(Comparator.comparingLong(DuplicateMacStack::sessions).reversed());

        return result.size() > MAXIMUM_STACKS ? Lists.newArrayList(result.subList(0, MAXIMUM_STACKS)) : result;
    }

    private static List<UUID> tapsOf(List<StackRow> rows) {
        Set<UUID> taps = new LinkedHashSet<>();
        for (StackRow row : rows) {
            for (String tap : row.taps) {
                try {
                    taps.add(UUID.fromString(tap));
                } catch (IllegalArgumentException ignored) { }
            }
        }

        return Lists.newArrayList(taps);
    }

    private void raiseNewFindingAlert(DuplicateMacFindingEntry finding, UUID organizationId, UUID tenantId) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("finding_uuid", finding.uuid().toString());
        attributes.put("mac", finding.mac());
        attributes.put("stack_count", String.valueOf(finding.stackCount()));
        attributes.put("fingerprints", finding.stacks().stream()
                .map(DuplicateMacStack::fingerprint).collect(Collectors.joining(", ")));
        attributes.put("session_count", String.valueOf(finding.sessionCount()));

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                finding.tapUuids().isEmpty() ? null : finding.tapUuids().get(0),
                DetectionType.ASSETS_MAC_DUPLICATE,
                Subsystem.ETHERNET,
                "MAC address \"" + finding.mac() + "\" is used by " + finding.stackCount() +
                        " different TCP stacks at the same time.",
                attributes,
                Set.of("finding_uuid")
        );
    }

}
