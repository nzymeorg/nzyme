package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.ethernet.spoofing.db.DnsSpoofingFindingEntry;
import app.nzyme.core.ethernet.spoofing.db.SpoofingExclusionEntry;
import app.nzyme.plugin.Subsystem;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.MetricNames;
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

public class DnsSpoofingDetector {

    private static final Logger LOG = LogManager.getLogger(DnsSpoofingDetector.class);

    // The owner's reply to the same query is looked for this many seconds around the forged reply.
    private static final int RACE_WINDOW_SECONDS = 5;

    // Replies are only judged once all taps have had the chance to report both replies of a race.
    private static final int SETTLE_SECONDS = 30;

    private static final int NAMES_LIMIT = 500;
    private static final int ANSWERS_LIMIT = 50;
    private static final int VICTIM_NAMES_LIMIT = 50;
    private static final int ALERT_SAMPLE = 10;

    private static final String BROADCAST_MAC = "FF:FF:FF:FF:FF:FF";
    private static final String NULL_MAC = "00:00:00:00:00:00";

    private final NzymeNode nzyme;
    private final Spoofing spoofing;

    private final Timer runTimer;

    public DnsSpoofingDetector(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.spoofing = nzyme.getEthernet().spoofing();
        this.runTimer = nzyme.getMetrics().timer(MetricNames.SPOOFING_DNS_DETECTION_RUN_TIMER);
    }

    private record Candidate(UUID tap, String serverAddress) { }

    private record Conflict(String serverAddress, String spooferMac, String ownerMac, Set<UUID> taps,
                            DateTime firstSeen, DateTime lastSeen) { }

    private record ForgedReply(UUID tap,
                               String clientAddress,
                               int clientPort,
                               @Nullable String clientMac,
                               @Nullable Integer transactionId,
                               DateTime timestamp,
                               List<String> answers,
                               @Nullable DateTime ownerTimestamp,
                               @Nullable String name) { }

    private static class Race {
        String clientAddress;
        String clientMac;
        String name;
        final Set<String> answers = Sets.newLinkedHashSet();
        final Set<UUID> taps = Sets.newLinkedHashSet();
        DateTime timestamp;
        boolean won = false;
        boolean answered = false;
    }

    private static class Victim {
        String address;
        final Set<String> names = Sets.newLinkedHashSet();
        long forgedCount;
        long wonCount;
        long unansweredCount;
        DateTime firstSeen;
        DateTime lastSeen;
    }

    public void runForTenant(UUID organizationId, UUID tenantId) {
        SpoofingConfiguration config = spoofing.getConfiguration(organizationId, tenantId);

        spoofing.retentionCleanDnsFindings(organizationId, tenantId, DateTime.now().minusDays(config.dnsRetentionTimeDays()));

        if (!config.dnsDetectionEnabled()) {
            LOG.debug("DNS spoofing detection disabled for tenant [{}/{}]. Skipping.", organizationId, tenantId);
            return;
        }

        List<UUID> taps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)
                .stream().map(Tap::uuid).collect(Collectors.toList());

        if (taps.isEmpty()) {
            LOG.debug("Tenant [{}/{}] has no taps. Skipping DNS spoofing detection.", organizationId, tenantId);
            return;
        }

        DateTime until = DateTime.now().minusSeconds(SETTLE_SECONDS);
        DateTime since = until.minusMinutes(config.dnsDetectionWindowMinutes());
        DateTime lookbackSince = until.minusHours(config.dnsOwnerLookbackHours());

        try (Timer.Context ignored = runTimer.time()) {
            nzyme.getDatabase().useTransaction(handle -> {
                // Only one node of a cluster processes a tenant at a time.
                boolean locked = handle.createQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                        .bind("key", "spoofing_dns_detection:" + organizationId + ":" + tenantId)
                        .mapTo(Boolean.class)
                        .one();

                if (!locked) {
                    LOG.debug("DNS spoofing detection for tenant [{}/{}] is running on another node. Skipping.",
                            organizationId, tenantId);
                    return;
                }

                List<Conflict> conflicts = readConflicts(handle, taps, config, since, until, lookbackSince);

                LOG.debug("Analyzing [{}] DNS reply conflicts of tenant [{}/{}].",
                        conflicts.size(), organizationId, tenantId);

                processConflicts(handle, conflicts, config, since, until, lookbackSince, organizationId, tenantId);
            });
        }
    }

    /*
     * Conflicts are rare and the lookback is long, so the window is scanned first for resolver addresses that
     * more than one MAC address answered from on the same tap. Only those are looked at over the lookback.
     */
    private List<Conflict> readConflicts(Handle handle,
                                         List<UUID> taps,
                                         SpoofingConfiguration config,
                                         DateTime since,
                                         DateTime until,
                                         DateTime lookbackSince) {
        List<Candidate> candidates = handle.createQuery("SELECT tap_uuid, host(server_address) AS server_address " +
                        "FROM dns_log WHERE tap_uuid IN (<taps>) AND dns_type = 'response' AND server_port = 53 " +
                        "AND server_mac IS NOT NULL AND server_mac NOT IN (:null_mac, :broadcast_mac) " +
                        "AND server_address_is_multicast = false " +
                        "AND timestamp >= :since AND timestamp <= :until " +
                        "GROUP BY tap_uuid, server_address HAVING COUNT(DISTINCT server_mac) > 1")
                .bindList("taps", taps)
                .bind("null_mac", NULL_MAC)
                .bind("broadcast_mac", BROADCAST_MAC)
                .bind("since", since)
                .bind("until", until)
                .map((rs, ctx) -> new Candidate(
                        UUID.fromString(rs.getString("tap_uuid")), rs.getString("server_address")
                ))
                .list();

        // Conflicts of the same spoofer and resolver address seen by several taps are one conflict.
        Map<String, Conflict> conflicts = Maps.newLinkedHashMap();
        for (Candidate candidate : candidates) {
            handle.createQuery("WITH replies AS (SELECT server_mac, MIN(timestamp) AS first_seen, " +
                            "MAX(timestamp) AS last_seen, " +
                            "COUNT(DISTINCT (client_address, client_port, transaction_id, timestamp)) " +
                            "FILTER (WHERE timestamp >= :since) AS window_count " +
                            "FROM dns_log WHERE tap_uuid = :tap AND dns_type = 'response' AND server_port = 53 " +
                            "AND server_address = :server_address::inet " +
                            "AND server_mac IS NOT NULL AND server_mac NOT IN (:null_mac, :broadcast_mac) " +
                            "AND timestamp >= :lookback_since AND timestamp <= :until GROUP BY server_mac), " +
                            "owner AS (SELECT server_mac, last_seen FROM replies " +
                            "ORDER BY first_seen ASC, server_mac ASC LIMIT 1) " +
                            "SELECT r.server_mac AS spoofer_mac, o.server_mac AS owner_mac, " +
                            "r.first_seen, r.last_seen FROM replies r, owner o " +
                            "WHERE r.server_mac <> o.server_mac AND r.last_seen >= :since " +
                            "AND o.last_seen >= :since AND o.last_seen > r.first_seen " +
                            "AND r.window_count >= :minimum_forged ORDER BY r.first_seen")
                    .bind("tap", candidate.tap)
                    .bind("server_address", candidate.serverAddress)
                    .bind("null_mac", NULL_MAC)
                    .bind("broadcast_mac", BROADCAST_MAC)
                    .bind("since", since)
                    .bind("until", until)
                    .bind("lookback_since", lookbackSince)
                    .bind("minimum_forged", config.dnsMinimumForgedReplies())
                    .mapToMap()
                    .forEach(row -> {
                        String spooferMac = (String) row.get("spoofer_mac");
                        DateTime firstSeen = new DateTime(row.get("first_seen"));
                        DateTime lastSeen = new DateTime(row.get("last_seen"));

                        Conflict existing = conflicts.get(identity(candidate.serverAddress, spooferMac));
                        if (existing == null) {
                            Set<UUID> conflictTaps = Sets.newLinkedHashSet();
                            conflictTaps.add(candidate.tap);
                            conflicts.put(identity(candidate.serverAddress, spooferMac), new Conflict(
                                    candidate.serverAddress,
                                    spooferMac,
                                    (String) row.get("owner_mac"),
                                    conflictTaps,
                                    firstSeen,
                                    lastSeen
                            ));
                        } else {
                            existing.taps.add(candidate.tap);
                            conflicts.put(identity(candidate.serverAddress, spooferMac), new Conflict(
                                    existing.serverAddress,
                                    existing.spooferMac,
                                    existing.ownerMac,
                                    existing.taps,
                                    firstSeen.isBefore(existing.firstSeen) ? firstSeen : existing.firstSeen,
                                    lastSeen.isAfter(existing.lastSeen) ? lastSeen : existing.lastSeen
                            ));
                        }
                    });
        }

        return Lists.newArrayList(conflicts.values());
    }

    private void processConflicts(Handle handle,
                                  List<Conflict> conflicts,
                                  SpoofingConfiguration config,
                                  DateTime since,
                                  DateTime until,
                                  DateTime lookbackSince,
                                  UUID organizationId,
                                  UUID tenantId) {
        if (conflicts.isEmpty()) {
            return;
        }

        Map<String, DnsSpoofingFindingEntry> activeFindings = Maps.newHashMap();
        for (DnsSpoofingFindingEntry finding : spoofing.findActiveDnsFindingsOfTenant(handle, organizationId, tenantId, since)) {
            activeFindings.put(identity(finding.serverAddress(), finding.spooferMac()), finding);
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

                DnsSpoofingFindingEntry finding = activeFindings.get(identity(conflict.serverAddress, conflict.spooferMac));

                boolean newFinding = false;
                DateTime countAfter;
                if (finding == null) {
                    finding = spoofing.createDnsFinding(
                            handle,
                            organizationId,
                            tenantId,
                            conflict.serverAddress,
                            conflict.spooferMac,
                            conflict.ownerMac,
                            conflict.firstSeen,
                            conflict.lastSeen
                    );
                    activeFindings.put(identity(conflict.serverAddress, conflict.spooferMac), finding);
                    newFinding = true;

                    // A new finding counts everything the spoofer sent since it first forged a reply.
                    countAfter = lookbackSince.minusMillis(1);
                } else {
                    countAfter = finding.lastSeen();
                }

                List<ForgedReply> replies = readForgedReplies(
                        handle, Lists.newArrayList(conflict.taps), conflict, finding.ownerMac(), countAfter, until
                );

                if (replies.isEmpty()) {
                    continue;
                }

                /*
                 * Several taps usually see the same reply. Replies are merged by client, port and transaction,
                 * so a reply seen by three taps counts once. A race is won when any tap saw the owner answer
                 * later, and unanswered when no tap saw the owner answer at all.
                 */
                Map<String, Race> races = Maps.newLinkedHashMap();
                for (ForgedReply reply : replies) {
                    String key = reply.clientAddress + "/" + reply.clientPort + "/" + reply.transactionId + "/" + reply.name;
                    Race race = races.computeIfAbsent(key, k -> new Race());
                    race.clientAddress = reply.clientAddress;
                    if (race.clientMac == null) {
                        race.clientMac = reply.clientMac;
                    }
                    race.name = reply.name;
                    race.answers.addAll(reply.answers);
                    race.taps.add(reply.tap);
                    race.timestamp = race.timestamp == null || reply.timestamp.isBefore(race.timestamp)
                            ? reply.timestamp : race.timestamp;
                    if (reply.ownerTimestamp != null) {
                        race.answered = true;
                        if (reply.ownerTimestamp.isAfter(reply.timestamp)) {
                            race.won = true;
                        }
                    }
                }

                long forgedCount = 0;
                long wonCount = 0;
                long unansweredCount = 0;
                DateTime lastSeen = null;
                Set<UUID> taps = Sets.newLinkedHashSet();
                Set<String> names = Sets.newLinkedHashSet(finding.names());
                Set<String> answers = Sets.newLinkedHashSet(finding.answers());
                Map<String, Victim> victims = Maps.newLinkedHashMap();

                for (Race race : races.values()) {
                    forgedCount++;
                    boolean won = race.answered && race.won;
                    boolean unanswered = !race.answered;
                    if (won) {
                        wonCount++;
                    }
                    if (unanswered) {
                        unansweredCount++;
                    }

                    taps.addAll(race.taps);
                    lastSeen = lastSeen == null || race.timestamp.isAfter(lastSeen) ? race.timestamp : lastSeen;

                    if (race.name != null && names.size() < NAMES_LIMIT) {
                        names.add(race.name);
                    }
                    for (String answer : race.answers) {
                        if (answers.size() >= ANSWERS_LIMIT) {
                            break;
                        }
                        answers.add(answer);
                    }

                    if (race.clientMac == null || race.clientMac.equals(NULL_MAC) || race.clientMac.equals(BROADCAST_MAC)) {
                        continue;
                    }

                    Victim victim = victims.computeIfAbsent(race.clientMac, k -> new Victim());
                    if (victim.address == null) {
                        victim.address = race.clientAddress;
                    }
                    if (race.name != null && victim.names.size() < VICTIM_NAMES_LIMIT) {
                        victim.names.add(race.name);
                    }
                    victim.forgedCount++;
                    if (won) {
                        victim.wonCount++;
                    }
                    if (unanswered) {
                        victim.unansweredCount++;
                    }
                    victim.firstSeen = victim.firstSeen == null || race.timestamp.isBefore(victim.firstSeen)
                            ? race.timestamp : victim.firstSeen;
                    victim.lastSeen = victim.lastSeen == null || race.timestamp.isAfter(victim.lastSeen)
                            ? race.timestamp : victim.lastSeen;
                }

                List<Spoofing.DnsVictimRecord> victimRecords = Lists.newArrayList();
                for (Map.Entry<String, Victim> v : victims.entrySet()) {
                    victimRecords.add(new Spoofing.DnsVictimRecord(
                            v.getKey(),
                            v.getValue().address,
                            Lists.newArrayList(v.getValue().names),
                            v.getValue().forgedCount,
                            v.getValue().wonCount,
                            v.getValue().unansweredCount,
                            v.getValue().firstSeen,
                            v.getValue().lastSeen
                    ));
                }

                spoofing.upsertDnsVictims(handle, finding.id(), victimRecords);
                spoofing.addDnsFindingActivity(
                        handle,
                        finding.id(),
                        forgedCount,
                        wonCount,
                        unansweredCount,
                        Lists.newArrayList(names),
                        Lists.newArrayList(answers),
                        finding.tapUuids(),
                        taps,
                        lastSeen == null ? conflict.lastSeen : lastSeen
                );

                if (newFinding && config.dnsAlertOnNew()) {
                    spoofing.findDnsFindingById(handle, finding.id())
                            .ifPresent(refreshed -> raiseNewFindingAlert(refreshed, organizationId, tenantId));
                }
            } catch (Exception e) {
                LOG.error("Could not process DNS spoofing conflict [{}/{}] of tenant [{}/{}]. Skipping.",
                        conflict.serverAddress, conflict.spooferMac, organizationId, tenantId, e);
            }
        }

        for (Map.Entry<Long, Long> match : exclusionMatches.entrySet()) {
            spoofing.exclusions().recordMatches(handle, match.getKey(), match.getValue());
        }
    }

    /*
     * The forged replies a finding has not accounted for yet, each with the owner's reply to the same query if
     * there was one, and the name the client had asked for. A reply is one packet: its answer records are
     * collapsed into one row.
     */
    private List<ForgedReply> readForgedReplies(Handle handle,
                                                List<UUID> taps,
                                                Conflict conflict,
                                                String ownerMac,
                                                DateTime after,
                                                DateTime until) {
        return handle.createQuery("WITH r AS (SELECT tap_uuid, client_address, client_port, client_mac, " +
                        "transaction_id, timestamp, server_mac, " +
                        "array_remove(array_agg(DISTINCT data_value), NULL) AS answers " +
                        "FROM dns_log WHERE tap_uuid IN (<taps>) AND dns_type = 'response' AND server_port = 53 " +
                        "AND server_address = :server_address::inet AND server_mac IN (:spoofer_mac, :owner_mac) " +
                        "AND timestamp > :after::timestamptz - :race_window::interval " +
                        "AND timestamp <= :until::timestamptz + :race_window::interval " +
                        "GROUP BY tap_uuid, client_address, client_port, client_mac, transaction_id, timestamp, " +
                        "server_mac) " +
                        "SELECT f.tap_uuid, host(f.client_address) AS client_address, f.client_port, f.client_mac, " +
                        "f.transaction_id, f.timestamp, f.answers, " +
                        "(SELECT MIN(o.timestamp) FROM r o WHERE o.server_mac = :owner_mac " +
                        "AND o.tap_uuid = f.tap_uuid AND o.client_address = f.client_address " +
                        "AND o.client_port = f.client_port " +
                        "AND o.transaction_id IS NOT DISTINCT FROM f.transaction_id " +
                        "AND o.timestamp >= f.timestamp - :race_window::interval " +
                        "AND o.timestamp <= f.timestamp + :race_window::interval) AS owner_timestamp, " +
                        "(SELECT q.data_value FROM dns_log q WHERE q.tap_uuid = f.tap_uuid " +
                        "AND q.dns_type = 'query' AND q.client_address = f.client_address " +
                        "AND q.client_port = f.client_port " +
                        "AND q.transaction_id IS NOT DISTINCT FROM f.transaction_id " +
                        "AND q.timestamp >= f.timestamp - :race_window::interval AND q.timestamp <= f.timestamp " +
                        "ORDER BY q.timestamp DESC LIMIT 1) AS name " +
                        "FROM r f WHERE f.server_mac = :spoofer_mac AND f.timestamp > :after " +
                        "AND f.timestamp <= :until ORDER BY f.timestamp ASC")
                .bindList("taps", taps)
                .bind("server_address", conflict.serverAddress)
                .bind("spoofer_mac", conflict.spooferMac)
                .bind("owner_mac", ownerMac)
                .bind("race_window", RACE_WINDOW_SECONDS + " seconds")
                .bind("after", after)
                .bind("until", until)
                .map((rs, ctx) -> {
                    java.sql.Array answers = rs.getArray("answers");
                    java.sql.Timestamp ownerTimestamp = rs.getTimestamp("owner_timestamp");
                    Integer transactionId = rs.getObject("transaction_id") == null ? null : rs.getInt("transaction_id");

                    return new ForgedReply(
                            UUID.fromString(rs.getString("tap_uuid")),
                            rs.getString("client_address"),
                            rs.getInt("client_port"),
                            rs.getString("client_mac"),
                            transactionId,
                            new DateTime(rs.getTimestamp("timestamp")),
                            answers == null ? Collections.emptyList() : Arrays.asList((String[]) answers.getArray()),
                            ownerTimestamp == null ? null : new DateTime(ownerTimestamp),
                            rs.getString("name")
                    );
                })
                .list();
    }

    @Nullable
    private static SpoofingExclusionEntry findMatchingExclusion(Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> exclusions,
                                                                Conflict conflict) {
        for (Map.Entry<SpoofingExclusionEntry, SpoofingExclusionMatcher> e : exclusions.entrySet()) {
            if (e.getValue().matches(SpoofingType.DNS, conflict.serverAddress, conflict.spooferMac)) {
                return e.getKey();
            }
        }

        return null;
    }

    private void raiseNewFindingAlert(DnsSpoofingFindingEntry finding, UUID organizationId, UUID tenantId) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("finding_uuid", finding.uuid().toString());
        attributes.put("server_address", finding.serverAddress());
        attributes.put("spoofer_mac", finding.spooferMac());
        attributes.put("owner_mac", finding.ownerMac());
        attributes.put("forged_count", String.valueOf(finding.forgedCount()));
        attributes.put("won_count", String.valueOf(finding.wonCount()));
        attributes.put("unanswered_count", String.valueOf(finding.unansweredCount()));
        attributes.put("name_count", String.valueOf(finding.nameCount()));
        attributes.put("names", String.join(",", finding.names().subList(0, Math.min(ALERT_SAMPLE, finding.names().size()))));
        attributes.put("answers", String.join(",", finding.answers().subList(0, Math.min(ALERT_SAMPLE, finding.answers().size()))));
        attributes.put("victim_count", String.valueOf(finding.victimCount()));

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                null,
                DetectionType.SPOOFING_DNS_NEW,
                Subsystem.ETHERNET,
                "DNS spoofing of resolver [" + finding.serverAddress() + "] by [" + finding.spooferMac() + "] " +
                        "detected. The resolver's replies normally come from [" + finding.ownerMac() + "].",
                attributes,
                Set.of("finding_uuid")
        );
    }

    private static String identity(String serverAddress, String spooferMac) {
        return serverAddress + "/" + spooferMac;
    }

}
