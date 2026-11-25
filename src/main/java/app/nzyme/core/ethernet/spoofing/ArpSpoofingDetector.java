package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.AlertOrigin;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.ethernet.spoofing.db.ArpSpoofingFindingEntry;
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

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

public class ArpSpoofingDetector {

    private static final Logger LOG = LogManager.getLogger(ArpSpoofingDetector.class);

    private static final int MAX_NDP_CONFLICTS_PER_RUN = 500;

    // A reply counts as solicited when the victim asked for the address this many seconds before it.
    private static final int SOLICITATION_WINDOW_SECONDS = 5;

    private static final String BROADCAST_MAC = "FF:FF:FF:FF:FF:FF";
    private static final String NULL_MAC = "00:00:00:00:00:00";

    private final NzymeNode nzyme;
    private final Spoofing spoofing;

    private final Timer runTimer;

    public ArpSpoofingDetector(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.spoofing = nzyme.getEthernet().spoofing();
        this.runTimer = nzyme.getMetrics().timer(MetricNames.SPOOFING_ARP_DETECTION_RUN_TIMER);
    }

    // A link-local IPv6 address is only unique on its link, so its conflict belongs to one tap.
    private record Conflict(String address, String spooferMac, String ownerMac, DateTime spooferFirstSeen,
                            DateTime spooferLastSeen, boolean ndp, @Nullable UUID linkTap) { }

    private static final String ALL_NODES_ADDRESS = "ff02::1";

    // Messages that did not arrive with a hop limit of 255 are ignored, as hosts do.
    static final String NDP_CLAIMS =
            "SELECT tap_uuid, first_seen, last_seen, packet_count, " +
                    "CASE WHEN message_type = 'NEIGHBOR_ADVERTISEMENT' THEN target_address " +
                    "ELSE source_address END AS address, " +
                    "CASE WHEN message_type = 'NEIGHBOR_ADVERTISEMENT' " +
                    "THEN COALESCE(target_link_layer_address, source_mac) " +
                    "ELSE COALESCE(source_link_layer_address, source_mac) END AS mac, " +
                    "message_type = 'NEIGHBOR_ADVERTISEMENT' AS reply, COALESCE(solicited, false) AS solicited, " +
                    "destination_mac AS target_mac, destination_address AS target_address " +
                    "FROM ndp_messages WHERE tap_uuid IN (<taps>) AND hop_limit = 255 " +
                    "AND last_seen > :after AND last_seen <= :until";

    // An advertisement was asked for when it says so and the receiver asked for the address around that time.
    private static final String NDP_UNSOLICITED =
            "(NOT p.solicited OR NOT EXISTS (SELECT 1 FROM ndp_messages r WHERE r.tap_uuid IN (<taps>) " +
                    "AND r.message_type = 'NEIGHBOR_SOLICITATION' AND r.target_address = p.address " +
                    "AND r.source_mac = p.target_mac " +
                    "AND r.last_seen >= p.first_seen - :solicitation_window::interval " +
                    "AND r.first_seen <= p.last_seen))";

    private record Increment(long claimCount, long replyCount, long unsolicitedCount, long broadcastCount,
                             long ownerClaimCount, Set<UUID> taps, @Nullable DateTime lastSeen) { }

    public void runForTenant(UUID organizationId, UUID tenantId) {
        SpoofingConfiguration config = spoofing.getConfiguration(organizationId, tenantId);

        spoofing.retentionCleanArpFindings(organizationId, tenantId, DateTime.now().minusDays(config.arpRetentionTimeDays()));

        if (!config.arpDetectionEnabled()) {
            LOG.debug("ARP spoofing detection disabled for tenant [{}/{}]. Skipping.", organizationId, tenantId);
            return;
        }

        List<UUID> taps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)
                .stream().map(Tap::uuid).collect(Collectors.toList());

        if (taps.isEmpty()) {
            LOG.debug("Tenant [{}/{}] has no taps. Skipping ARP spoofing detection.", organizationId, tenantId);
            return;
        }

        DateTime until = DateTime.now();
        DateTime since = until.minusMinutes(config.arpDetectionWindowMinutes());
        DateTime lookbackSince = until.minusHours(config.arpOwnerLookbackHours());

        try (Timer.Context ignored = runTimer.time()) {
            nzyme.getDatabase().useTransaction(handle -> {
                // Only one node of a cluster processes a tenant at a time.
                boolean locked = handle.createQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                        .bind("key", "spoofing_arp_detection:" + organizationId + ":" + tenantId)
                        .mapTo(Boolean.class)
                        .one();

                if (!locked) {
                    LOG.debug("ARP spoofing detection for tenant [{}/{}] is running on another node. Skipping.",
                            organizationId, tenantId);
                    return;
                }

                List<Conflict> conflicts = readConflicts(handle, taps, config, since, until, lookbackSince);
                List<Conflict> ndpConflicts = readNdpConflicts(handle, taps, config, since, until, lookbackSince);
                if (ndpConflicts.size() > MAX_NDP_CONFLICTS_PER_RUN) {
                    LOG.warn("Tenant [{}/{}] has [{}] conflicting NDP claims in one run. Judging the first {}. "
                                    + "This is what a flood of made-up neighbor advertisements looks like.",
                            organizationId, tenantId, ndpConflicts.size(), MAX_NDP_CONFLICTS_PER_RUN);
                    ndpConflicts = ndpConflicts.subList(0, MAX_NDP_CONFLICTS_PER_RUN);
                }
                conflicts.addAll(ndpConflicts);

                LOG.debug("Analyzing [{}] ARP and NDP conflicts of tenant [{}/{}].", conflicts.size(), organizationId, tenantId);

                processConflicts(handle, conflicts, config, taps, since, until, lookbackSince, organizationId, tenantId);
            });
        }
    }

    private List<Conflict> readConflicts(Handle handle,
                                         List<UUID> taps,
                                         SpoofingConfiguration config,
                                         DateTime since,
                                         DateTime until,
                                         DateTime lookbackSince) {
        List<Conflict> result = Lists.newArrayList();

        handle.createQuery("WITH claims AS (SELECT arp_sender_address AS address, arp_sender_mac AS mac, " +
                        "COUNT(*) FILTER (WHERE timestamp >= :since) AS window_claim_count, " +
                        "MIN(timestamp) AS first_seen, MAX(timestamp) AS last_seen " +
                        "FROM arp_packets WHERE tap_uuid IN (<taps>) " +
                        "AND timestamp >= :lookback_since AND timestamp <= :until " +
                        "AND family(arp_sender_address) = 4 AND arp_sender_address <> '0.0.0.0'::inet " +
                        "AND arp_sender_mac NOT IN (:null_mac, :broadcast_mac) " +
                        "GROUP BY arp_sender_address, arp_sender_mac), " +
                        "owners AS (SELECT DISTINCT ON (address) address, mac, first_seen, last_seen " +
                        "FROM claims ORDER BY address, first_seen ASC, mac ASC) " +
                        "SELECT host(c.address) AS address, c.mac AS spoofer_mac, o.mac AS owner_mac, " +
                        "c.first_seen AS spoofer_first_seen, c.last_seen AS spoofer_last_seen " +
                        "FROM claims c JOIN owners o ON o.address = c.address AND o.mac <> c.mac " +
                        "WHERE c.last_seen >= :since AND o.last_seen >= :since " +
                        "AND o.last_seen > c.first_seen AND c.window_claim_count >= :minimum_claims " +
                        "ORDER BY c.address, c.first_seen")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .bind("lookback_since", lookbackSince)
                .bind("null_mac", NULL_MAC)
                .bind("broadcast_mac", BROADCAST_MAC)
                .bind("minimum_claims", config.arpMinimumClaims())
                .mapToMap()
                .forEach(row -> result.add(new Conflict(
                        (String) row.get("address"),
                        (String) row.get("spoofer_mac"),
                        (String) row.get("owner_mac"),
                        new DateTime(row.get("spoofer_first_seen")),
                        new DateTime(row.get("spoofer_last_seen")),
                        false,
                        null
                )));

        return result;
    }

    private List<Conflict> readNdpConflicts(Handle handle,
                                            List<UUID> taps,
                                            SpoofingConfiguration config,
                                            DateTime since,
                                            DateTime until,
                                            DateTime lookbackSince) {
        return handle.createQuery("WITH messages AS (" + NDP_CLAIMS + "), " +
                        "claims AS (SELECT address, mac, " +
                        "CASE WHEN address <<= 'fe80::/10'::inet THEN tap_uuid::text ELSE '' END AS link, " +
                        "COALESCE(SUM(packet_count) FILTER (WHERE last_seen >= :since), 0) AS window_claim_count, " +
                        "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen " +
                        "FROM messages WHERE address IS NOT NULL AND mac IS NOT NULL AND address <> '::'::inet " +
                        "AND mac NOT IN (:null_mac, :broadcast_mac) GROUP BY address, mac, link), " +
                        "owners AS (SELECT DISTINCT ON (address, link) address, link, mac, first_seen, last_seen " +
                        "FROM claims ORDER BY address, link, first_seen ASC, mac ASC) " +
                        "SELECT host(c.address) AS address, c.link, c.mac AS spoofer_mac, o.mac AS owner_mac, " +
                        "c.first_seen AS spoofer_first_seen, c.last_seen AS spoofer_last_seen " +
                        "FROM claims c JOIN owners o ON o.address = c.address AND o.link = c.link AND o.mac <> c.mac " +
                        "WHERE c.last_seen >= :since AND o.last_seen >= :since " +
                        "AND o.last_seen > c.first_seen AND c.window_claim_count >= :minimum_claims " +
                        "ORDER BY c.address, c.first_seen")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("after", lookbackSince.minusMillis(1))
                .bind("until", until)
                .bind("null_mac", NULL_MAC)
                .bind("broadcast_mac", BROADCAST_MAC)
                .bind("minimum_claims", config.arpMinimumClaims())
                .map((rs, ctx) -> new Conflict(
                        rs.getString("address"),
                        rs.getString("spoofer_mac"),
                        rs.getString("owner_mac"),
                        new DateTime(rs.getTimestamp("spoofer_first_seen")),
                        new DateTime(rs.getTimestamp("spoofer_last_seen")),
                        true,
                        rs.getString("link").isEmpty() ? null : UUID.fromString(rs.getString("link"))
                ))
                .list();
    }

    private void processConflicts(Handle handle,
                                  List<Conflict> conflicts,
                                  SpoofingConfiguration config,
                                  List<UUID> taps,
                                  DateTime since,
                                  DateTime until,
                                  DateTime lookbackSince,
                                  UUID organizationId,
                                  UUID tenantId) {
        if (conflicts.isEmpty()) {
            return;
        }

        Map<String, ArpSpoofingFindingEntry> activeFindings = Maps.newHashMap();
        for (ArpSpoofingFindingEntry finding : spoofing.findActiveArpFindingsOfTenant(handle, organizationId, tenantId, since)) {
            activeFindings.put(identity(finding.address(), finding.spooferMac()), finding);
        }

        Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> exclusions = spoofing.exclusions()
                .compileEnabled(handle, organizationId, tenantId);
        Map<Long, Long> exclusionMatches = Maps.newHashMap();

        for (Conflict conflict : conflicts) {
            try {
                SpoofingExclusionEntry exclusion = findMatchingExclusion(exclusions, conflict);
                if (exclusion != null) {
                    exclusionMatches.merge(exclusion.id(), 1L, Long::sum);
                    continue;
                }

                ArpSpoofingFindingEntry finding = activeFindings.get(identity(conflict.address, conflict.spooferMac));

                boolean newFinding = false;
                DateTime countAfter;
                if (finding == null) {
                    finding = spoofing.createArpFinding(
                            handle,
                            organizationId,
                            tenantId,
                            conflict.address,
                            conflict.spooferMac,
                            conflict.ownerMac,
                            conflict.spooferFirstSeen,
                            conflict.spooferLastSeen
                    );
                    activeFindings.put(identity(conflict.address, conflict.spooferMac), finding);
                    newFinding = true;

                    // A new finding counts everything the spoofer sent since it first claimed the address.
                    countAfter = lookbackSince.minusMillis(1);
                } else {
                    countAfter = finding.lastSeen();
                }

                List<UUID> conflictTaps = conflict.linkTap == null ? taps : List.of(conflict.linkTap);
                Increment increment = conflict.ndp
                        ? readNdpIncrement(handle, conflictTaps, conflict, finding, countAfter, until)
                        : readIncrement(handle, taps, conflict, finding, countAfter, until);
                List<Spoofing.VictimRecord> victims = conflict.ndp
                        ? readNdpVictims(handle, conflictTaps, conflict, countAfter, until)
                        : readVictims(handle, taps, conflict, countAfter, until);

                spoofing.upsertArpVictims(handle, finding.id(), victims);
                spoofing.addArpFindingCounts(
                        handle,
                        finding.id(),
                        increment.claimCount,
                        increment.replyCount,
                        increment.unsolicitedCount,
                        increment.broadcastCount,
                        increment.ownerClaimCount,
                        finding.tapUuids(),
                        increment.taps,
                        increment.lastSeen == null ? conflict.spooferLastSeen : increment.lastSeen
                );

                if (config.arpAlertOnNew() && (newFinding || AlertOrigin.seenElsewhere(finding.tapUuids(), increment.taps))) {
                    List<UUID> alertedTaps = newFinding ? List.of() : finding.tapUuids();
                    spoofing.findArpFindingById(handle, finding.id()).ifPresent(refreshed -> raiseFindingAlert(
                            refreshed, alertedTaps, organizationId, tenantId
                    ));
                }
            } catch (Exception e) {
                LOG.error("Could not process ARP conflict [{}/{}] of tenant [{}/{}]. Skipping.",
                        conflict.address, conflict.spooferMac, organizationId, tenantId, e);
            }
        }

        for (Map.Entry<Long, Long> match : exclusionMatches.entrySet()) {
            spoofing.exclusions().recordMatches(handle, match.getKey(), match.getValue());
        }
    }

    /*
     * Counts of the packets a finding has not accounted for yet: the spoofer's claims of the address, how many of
     * them were replies, how many of those replies nobody asked for, how many went to the broadcast address, and
     * how often the owner claimed the address in the same period.
     */
    private Increment readIncrement(Handle handle,
                                    List<UUID> taps,
                                    Conflict conflict,
                                    ArpSpoofingFindingEntry finding,
                                    DateTime after,
                                    DateTime until) {
        return handle.createQuery("SELECT COUNT(*) FILTER (WHERE arp_sender_mac = :spoofer_mac) AS claim_count, " +
                        "COUNT(*) FILTER (WHERE arp_sender_mac = :spoofer_mac AND operation = 'Reply') AS reply_count, " +
                        "COUNT(*) FILTER (WHERE arp_sender_mac = :spoofer_mac AND operation = 'Reply' " +
                        "AND arp_target_mac <> :broadcast_mac AND NOT EXISTS (SELECT 1 FROM arp_packets r " +
                        "WHERE r.tap_uuid IN (<taps>) AND r.operation = 'Request' " +
                        "AND r.arp_sender_mac = p.arp_target_mac AND r.arp_target_address = p.arp_sender_address " +
                        "AND r.timestamp >= p.timestamp - :solicitation_window::interval " +
                        "AND r.timestamp <= p.timestamp)) AS unsolicited_count, " +
                        "COUNT(*) FILTER (WHERE arp_sender_mac = :spoofer_mac AND operation = 'Reply' " +
                        "AND arp_target_mac = :broadcast_mac) AS broadcast_count, " +
                        "COUNT(*) FILTER (WHERE arp_sender_mac = :owner_mac) AS owner_claim_count, " +
                        "string_agg(DISTINCT tap_uuid::text, ',') FILTER (WHERE arp_sender_mac = :spoofer_mac) AS taps, " +
                        "MAX(timestamp) FILTER (WHERE arp_sender_mac = :spoofer_mac) AS last_seen " +
                        "FROM arp_packets p WHERE p.tap_uuid IN (<taps>) " +
                        "AND p.arp_sender_address = :address::inet AND p.arp_sender_mac IN (:spoofer_mac, :owner_mac) " +
                        "AND p.timestamp > :after AND p.timestamp <= :until")
                .bindList("taps", taps)
                .bind("address", conflict.address)
                .bind("spoofer_mac", conflict.spooferMac)
                .bind("owner_mac", finding.ownerMac())
                .bind("broadcast_mac", BROADCAST_MAC)
                .bind("solicitation_window", SOLICITATION_WINDOW_SECONDS + " seconds")
                .bind("after", after)
                .bind("until", until)
                .map((rs, ctx) -> {
                    Timestamp lastSeen = rs.getTimestamp("last_seen");
                    return new Increment(
                            rs.getLong("claim_count"),
                            rs.getLong("reply_count"),
                            rs.getLong("unsolicited_count"),
                            rs.getLong("broadcast_count"),
                            rs.getLong("owner_claim_count"),
                            parseTaps(rs.getString("taps")),
                            lastSeen == null ? null : new DateTime(lastSeen)
                    );
                })
                .one();
    }

    /*
     * The devices that received the spoofer's replies for the address, which are the devices whose ARP cache may
     * now point at the spoofer.
     */
    private List<Spoofing.VictimRecord> readVictims(Handle handle,
                                                    List<UUID> taps,
                                                    Conflict conflict,
                                                    DateTime after,
                                                    DateTime until) {
        return handle.createQuery("SELECT arp_target_mac AS mac, " +
                        "mode() WITHIN GROUP (ORDER BY host(arp_target_address)) " +
                        "FILTER (WHERE arp_target_address <> '0.0.0.0'::inet) AS address, " +
                        "COUNT(*) AS packet_count, " +
                        "COUNT(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM arp_packets r " +
                        "WHERE r.tap_uuid IN (<taps>) AND r.operation = 'Request' " +
                        "AND r.arp_sender_mac = p.arp_target_mac AND r.arp_target_address = p.arp_sender_address " +
                        "AND r.timestamp >= p.timestamp - :solicitation_window::interval " +
                        "AND r.timestamp <= p.timestamp)) AS unsolicited_count, " +
                        "MIN(timestamp) AS first_seen, MAX(timestamp) AS last_seen " +
                        "FROM arp_packets p WHERE p.tap_uuid IN (<taps>) AND p.operation = 'Reply' " +
                        "AND p.arp_sender_address = :address::inet AND p.arp_sender_mac = :spoofer_mac " +
                        "AND p.arp_target_mac NOT IN (:null_mac, :broadcast_mac) " +
                        "AND p.timestamp > :after AND p.timestamp <= :until " +
                        "GROUP BY arp_target_mac")
                .bindList("taps", taps)
                .bind("address", conflict.address)
                .bind("spoofer_mac", conflict.spooferMac)
                .bind("null_mac", NULL_MAC)
                .bind("broadcast_mac", BROADCAST_MAC)
                .bind("solicitation_window", SOLICITATION_WINDOW_SECONDS + " seconds")
                .bind("after", after)
                .bind("until", until)
                .map((rs, ctx) -> new Spoofing.VictimRecord(
                        rs.getString("mac"),
                        rs.getString("address"),
                        rs.getLong("packet_count"),
                        rs.getLong("unsolicited_count"),
                        new DateTime(rs.getTimestamp("first_seen")),
                        new DateTime(rs.getTimestamp("last_seen"))
                ))
                .list();
    }

    private Increment readNdpIncrement(Handle handle,
                                       List<UUID> taps,
                                       Conflict conflict,
                                       ArpSpoofingFindingEntry finding,
                                       DateTime after,
                                       DateTime until) {
        String spoofer = "p.mac = :spoofer_mac";

        return handle.createQuery("WITH messages AS (" + NDP_CLAIMS + ") " +
                        "SELECT COALESCE(SUM(packet_count) FILTER (WHERE " + spoofer + "), 0) AS claim_count, " +
                        "COALESCE(SUM(packet_count) FILTER (WHERE " + spoofer + " AND p.reply), 0) AS reply_count, " +
                        "COALESCE(SUM(packet_count) FILTER (WHERE " + spoofer + " AND p.reply " +
                        "AND p.target_address <> :all_nodes::inet AND " + NDP_UNSOLICITED + "), 0) AS unsolicited_count, " +
                        "COALESCE(SUM(packet_count) FILTER (WHERE " + spoofer + " AND p.reply " +
                        "AND p.target_address = :all_nodes::inet), 0) AS broadcast_count, " +
                        "COALESCE(SUM(packet_count) FILTER (WHERE p.mac = :owner_mac), 0) AS owner_claim_count, " +
                        "string_agg(DISTINCT tap_uuid::text, ',') FILTER (WHERE " + spoofer + ") AS taps, " +
                        "MAX(last_seen) FILTER (WHERE " + spoofer + ") AS last_seen " +
                        "FROM messages p WHERE p.address = :address::inet AND p.mac IN (:spoofer_mac, :owner_mac)")
                .bindList("taps", taps)
                .bind("address", conflict.address)
                .bind("spoofer_mac", conflict.spooferMac)
                .bind("owner_mac", finding.ownerMac())
                .bind("all_nodes", ALL_NODES_ADDRESS)
                .bind("solicitation_window", SOLICITATION_WINDOW_SECONDS + " seconds")
                .bind("after", after)
                .bind("until", until)
                .map((rs, ctx) -> {
                    Timestamp lastSeen = rs.getTimestamp("last_seen");
                    return new Increment(
                            rs.getLong("claim_count"),
                            rs.getLong("reply_count"),
                            rs.getLong("unsolicited_count"),
                            rs.getLong("broadcast_count"),
                            rs.getLong("owner_claim_count"),
                            parseTaps(rs.getString("taps")),
                            lastSeen == null ? null : new DateTime(lastSeen)
                    );
                })
                .one();
    }

    private List<Spoofing.VictimRecord> readNdpVictims(Handle handle,
                                                       List<UUID> taps,
                                                       Conflict conflict,
                                                       DateTime after,
                                                       DateTime until) {
        return handle.createQuery("WITH messages AS (" + NDP_CLAIMS + ") " +
                        "SELECT p.target_mac AS mac, " +
                        "mode() WITHIN GROUP (ORDER BY host(p.target_address)) AS address, " +
                        "SUM(packet_count) AS packet_count, " +
                        "COALESCE(SUM(packet_count) FILTER (WHERE " + NDP_UNSOLICITED + "), 0) AS unsolicited_count, " +
                        "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen " +
                        "FROM messages p WHERE p.reply AND p.address = :address::inet AND p.mac = :spoofer_mac " +
                        "AND p.target_mac IS NOT NULL AND NOT p.target_address <<= 'ff00::/8'::inet " +
                        "GROUP BY p.target_mac")
                .bindList("taps", taps)
                .bind("address", conflict.address)
                .bind("spoofer_mac", conflict.spooferMac)
                .bind("solicitation_window", SOLICITATION_WINDOW_SECONDS + " seconds")
                .bind("after", after)
                .bind("until", until)
                .map((rs, ctx) -> new Spoofing.VictimRecord(
                        rs.getString("mac"),
                        rs.getString("address"),
                        rs.getLong("packet_count"),
                        rs.getLong("unsolicited_count"),
                        new DateTime(rs.getTimestamp("first_seen")),
                        new DateTime(rs.getTimestamp("last_seen"))
                ))
                .list();
    }

    @Nullable
    private static SpoofingExclusionEntry findMatchingExclusion(Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> exclusions,
                                                                Conflict conflict) {
        for (Map.Entry<SpoofingExclusionEntry, SpoofingExclusionMatcher> e : exclusions.entrySet()) {
            if (e.getValue().matches(SpoofingType.ARP, conflict.address, conflict.spooferMac)) {
                return e.getKey();
            }
        }

        return null;
    }

    private void raiseFindingAlert(ArpSpoofingFindingEntry finding,
                                   List<UUID> alertedTaps,
                                   UUID organizationId,
                                   UUID tenantId) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("finding_uuid", finding.uuid().toString());
        attributes.put("address", finding.address());
        attributes.put("spoofer_mac", finding.spooferMac());
        attributes.put("owner_mac", finding.ownerMac());
        attributes.put("claim_count", String.valueOf(finding.claimCount()));
        attributes.put("unsolicited_count", String.valueOf(finding.unsolicitedCount()));
        attributes.put("victim_count", String.valueOf(finding.victimCount()));

        boolean ndp = finding.address().contains(":");

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                AlertOrigin.taps(finding.tapUuids()).excludingLocationsOf(alertedTaps),
                ndp ? DetectionType.SPOOFING_NDP_NEW : DetectionType.SPOOFING_ARP_NEW,
                Subsystem.ETHERNET,
                (ndp ? "NDP spoofing of [" : "ARP poisoning of [") + finding.address() + "] by ["
                        + finding.spooferMac() + "] detected. The address belongs to [" + finding.ownerMac() + "].",
                attributes,
                Set.of("finding_uuid")
        );
    }

    private static String identity(String address, String spooferMac) {
        return address + "/" + spooferMac;
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
                // Skip.
            }
        }

        return result;
    }

}
