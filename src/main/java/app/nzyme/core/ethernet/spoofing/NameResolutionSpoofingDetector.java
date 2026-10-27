package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.ethernet.spoofing.db.NameResolutionSpoofingFindingEntry;
import app.nzyme.core.ethernet.spoofing.db.SpoofingExclusionEntry;
import app.nzyme.core.rest.resources.taps.reports.tables.nameres.NameResolutionVictimReport;
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

public class NameResolutionSpoofingDetector {

    private static final Logger LOG = LogManager.getLogger(NameResolutionSpoofingDetector.class);

    private static final int NAMES_LIMIT = 500;
    private static final int VICTIM_NAMES_LIMIT = 50;
    private static final int ALERT_NAMES_SAMPLE = 10;

    private final NzymeNode nzyme;
    private final Spoofing spoofing;

    private final Timer runTimer;

    public NameResolutionSpoofingDetector(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.spoofing = nzyme.getEthernet().spoofing();
        this.runTimer = nzyme.getMetrics().timer(MetricNames.SPOOFING_NAMERES_DETECTION_RUN_TIMER);
    }

    private record Row(String protocol,
                       String responderMac,
                       String responderAddress,
                       String name,
                       List<String> answerAddresses,
                       long responseCount,
                       long unicastCount,
                       List<NameResolutionVictimReport> victims,
                       DateTime firstSeen,
                       DateTime lastSeen,
                       UUID tap) { }

    private static class NameActivity {
        final Set<String> protocols = Sets.newLinkedHashSet();
        final Set<String> answerAddresses = Sets.newLinkedHashSet();
        final List<Row> rows = Lists.newArrayList();
    }

    private static class ResponderActivity {
        final String mac;
        final Set<String> ownAddresses = Sets.newLinkedHashSet();
        final Map<String, NameActivity> names = Maps.newLinkedHashMap();

        ResponderActivity(String mac) {
            this.mac = mac;
        }
    }

    private static class Victim {
        String address;
        final Set<String> names = Sets.newLinkedHashSet();
        long responseCount;
        DateTime firstSeen;
        DateTime lastSeen;
    }

    public void runForTenant(UUID organizationId, UUID tenantId) {
        SpoofingConfiguration config = spoofing.getConfiguration(organizationId, tenantId);

        spoofing.retentionCleanNameResolutionFindings(
                organizationId, tenantId, DateTime.now().minusDays(config.nameresRetentionTimeDays())
        );

        List<UUID> taps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)
                .stream().map(Tap::uuid).collect(Collectors.toList());

        if (taps.isEmpty()) {
            LOG.debug("Tenant [{}/{}] has no taps. Skipping name resolution spoofing detection.", organizationId, tenantId);
            return;
        }

        spoofing.retentionCleanNameResolutionResponses(
                taps, DateTime.now().minusDays(config.nameresResponsesRetentionTimeDays())
        );

        if (!config.nameresDetectionEnabled()) {
            LOG.debug("Name resolution spoofing detection disabled for tenant [{}/{}]. Skipping.", organizationId, tenantId);
            return;
        }

        DateTime until = DateTime.now();
        DateTime since = until.minusMinutes(config.nameresDetectionWindowMinutes());

        try (Timer.Context ignored = runTimer.time()) {
            nzyme.getDatabase().useTransaction(handle -> {
                // Only one node of a cluster processes a tenant at a time.
                boolean locked = handle.createQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                        .bind("key", "spoofing_nameres_detection:" + organizationId + ":" + tenantId)
                        .mapTo(Boolean.class)
                        .one();

                if (!locked) {
                    LOG.debug("Name resolution spoofing detection for tenant [{}/{}] is running on another node. " +
                            "Skipping.", organizationId, tenantId);
                    return;
                }

                Map<String, ResponderActivity> responders = aggregate(readRows(handle, taps, since, until));

                LOG.debug("Analyzing [{}] name resolution responders of tenant [{}/{}].",
                        responders.size(), organizationId, tenantId);

                processResponders(handle, responders, config, since, organizationId, tenantId);
            });
        }
    }

    private List<Row> readRows(Handle handle, List<UUID> taps, DateTime since, DateTime until) {
        return handle.createQuery("SELECT protocol, responder_mac, host(responder_address) AS responder_address, " +
                        "name, answer_addresses, response_count, unicast_count, victims, first_seen, last_seen, " +
                        "tap_uuid FROM name_resolution_responses WHERE tap_uuid IN (<taps>) " +
                        "AND last_seen >= :since AND last_seen <= :until ORDER BY last_seen ASC")
                .bindList("taps", taps)
                .bind("since", since)
                .bind("until", until)
                .map((rs, ctx) -> new Row(
                        rs.getString("protocol"),
                        rs.getString("responder_mac"),
                        rs.getString("responder_address"),
                        rs.getString("name"),
                        NameResolutionJson.parseStrings(rs.getString("answer_addresses")),
                        rs.getLong("response_count"),
                        rs.getLong("unicast_count"),
                        NameResolutionJson.parseVictims(rs.getString("victims")),
                        new DateTime(rs.getTimestamp("first_seen")),
                        new DateTime(rs.getTimestamp("last_seen")),
                        UUID.fromString(rs.getString("tap_uuid"))
                ))
                .list();
    }

    private static Map<String, ResponderActivity> aggregate(List<Row> rows) {
        Map<String, ResponderActivity> responders = Maps.newLinkedHashMap();

        for (Row row : rows) {
            ResponderActivity responder = responders.computeIfAbsent(row.responderMac, ResponderActivity::new);
            responder.ownAddresses.add(row.responderAddress);

            NameActivity name = responder.names.computeIfAbsent(row.name, k -> new NameActivity());
            name.protocols.add(row.protocol);
            name.answerAddresses.addAll(row.answerAddresses);
            name.rows.add(row);
        }

        return responders;
    }

    private void processResponders(Handle handle,
                                   Map<String, ResponderActivity> responders,
                                   SpoofingConfiguration config,
                                   DateTime since,
                                   UUID organizationId,
                                   UUID tenantId) {
        if (responders.isEmpty()) {
            return;
        }

        Map<String, NameResolutionSpoofingFindingEntry> activeFindings = Maps.newHashMap();
        for (NameResolutionSpoofingFindingEntry finding : spoofing.findActiveNameResolutionFindingsOfTenant(
                handle, organizationId, tenantId, since)) {
            activeFindings.put(finding.responderMac(), finding);
        }

        Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> exclusions = spoofing.exclusions()
                .compileEnabled(handle, organizationId, tenantId);
        Map<Long, Long> exclusionMatches = Maps.newHashMap();

        for (ResponderActivity responder : responders.values()) {
            try {
                Map<String, NameActivity> poisoned = poisonedNames(responder, config);
                if (poisoned.isEmpty()) {
                    continue;
                }

                SpoofingExclusionEntry exclusion = findMatchingExclusion(exclusions, responder);
                if (exclusion != null) {
                    exclusionMatches.merge(exclusion.id(), 1L, Long::sum);
                    continue;
                }

                DateTime firstSeen = null;
                DateTime lastSeen = null;
                for (NameActivity activity : poisoned.values()) {
                    for (Row row : activity.rows) {
                        firstSeen = firstSeen == null || row.firstSeen.isBefore(firstSeen) ? row.firstSeen : firstSeen;
                        lastSeen = lastSeen == null || row.lastSeen.isAfter(lastSeen) ? row.lastSeen : lastSeen;
                    }
                }

                NameResolutionSpoofingFindingEntry finding = activeFindings.get(responder.mac);
                boolean newFinding = false;
                DateTime countAfter;
                if (finding == null) {
                    finding = spoofing.createNameResolutionFinding(
                            handle, organizationId, tenantId, responder.mac, firstSeen, lastSeen
                    );
                    activeFindings.put(responder.mac, finding);
                    newFinding = true;
                    countAfter = since.minusMillis(1);
                } else {
                    countAfter = finding.lastSeen();
                }

                /*
                 * Several taps usually see the same answers. Counts are summed per tap and the highest tap wins,
                 * so an answer seen by three taps counts once. Names and victims are merged.
                 */
                long responseCount = 0;
                long unicastCount = 0;
                Set<UUID> taps = Sets.newLinkedHashSet();
                Set<String> protocols = Sets.newLinkedHashSet(finding.protocols());
                Map<String, Victim> victims = Maps.newLinkedHashMap();
                for (Map.Entry<String, NameActivity> entry : poisoned.entrySet()) {
                    protocols.addAll(entry.getValue().protocols);

                    Map<UUID, long[]> countsPerTap = Maps.newHashMap();
                    Map<String, Map<UUID, Long>> victimCountsPerTap = Maps.newHashMap();
                    for (Row row : entry.getValue().rows) {
                        if (!row.lastSeen.isAfter(countAfter)) {
                            continue;
                        }

                        long[] counts = countsPerTap.computeIfAbsent(row.tap, k -> new long[2]);
                        counts[0] += row.responseCount;
                        counts[1] += row.unicastCount;
                        taps.add(row.tap);

                        for (NameResolutionVictimReport v : row.victims) {
                            Victim victim = victims.computeIfAbsent(v.mac(), k -> new Victim());
                            if (victim.address == null) {
                                victim.address = v.address();
                            }
                            if (victim.names.size() < VICTIM_NAMES_LIMIT) {
                                victim.names.add(entry.getKey());
                            }
                            victimCountsPerTap.computeIfAbsent(v.mac(), k -> Maps.newHashMap())
                                    .merge(row.tap, v.count(), Long::sum);
                            victim.firstSeen = victim.firstSeen == null || v.firstSeen().isBefore(victim.firstSeen)
                                    ? v.firstSeen() : victim.firstSeen;
                            victim.lastSeen = victim.lastSeen == null || v.lastSeen().isAfter(victim.lastSeen)
                                    ? v.lastSeen() : victim.lastSeen;
                        }
                    }

                    responseCount += countsPerTap.values().stream().mapToLong(c -> c[0]).max().orElse(0);
                    unicastCount += countsPerTap.values().stream().mapToLong(c -> c[1]).max().orElse(0);
                    for (Map.Entry<String, Map<UUID, Long>> v : victimCountsPerTap.entrySet()) {
                        victims.get(v.getKey()).responseCount += v.getValue().values().stream()
                                .mapToLong(Long::longValue).max().orElse(0);
                    }
                }

                Set<String> names = Sets.newLinkedHashSet(finding.names());
                for (String name : poisoned.keySet()) {
                    if (names.size() >= NAMES_LIMIT) {
                        break;
                    }
                    names.add(name);
                }

                Set<String> addresses = Sets.newLinkedHashSet(finding.responderAddresses());
                addresses.addAll(responder.ownAddresses);

                List<Spoofing.NameResolutionVictimRecord> victimRecords = Lists.newArrayList();
                for (Map.Entry<String, Victim> v : victims.entrySet()) {
                    victimRecords.add(new Spoofing.NameResolutionVictimRecord(
                            v.getKey(), v.getValue().address, Lists.newArrayList(v.getValue().names),
                            v.getValue().responseCount, v.getValue().firstSeen, v.getValue().lastSeen
                    ));
                }

                spoofing.upsertNameResolutionVictims(handle, finding.id(), victimRecords);
                spoofing.addNameResolutionFindingActivity(
                        handle,
                        finding.id(),
                        Lists.newArrayList(addresses),
                        Lists.newArrayList(protocols),
                        Lists.newArrayList(names),
                        responseCount,
                        unicastCount,
                        finding.tapUuids(),
                        taps,
                        lastSeen
                );

                if (newFinding && config.nameresAlertOnNew()) {
                    spoofing.findNameResolutionFindingById(handle, finding.id())
                            .ifPresent(refreshed -> raiseNewFindingAlert(refreshed, organizationId, tenantId));
                }
            } catch (Exception e) {
                LOG.error("Could not process name resolution responder [{}] of tenant [{}/{}]. Skipping.",
                        responder.mac, organizationId, tenantId, e);
            }
        }

        for (Map.Entry<Long, Long> match : exclusionMatches.entrySet()) {
            spoofing.exclusions().recordMatches(handle, match.getKey(), match.getValue());
        }
    }

    private static Map<String, NameActivity> poisonedNames(ResponderActivity responder, SpoofingConfiguration config) {
        Map<String, Map<String, NameActivity>> selfPointingByProtocol = Maps.newHashMap();
        Map<String, Map<String, NameActivity>> allByProtocol = Maps.newHashMap();
        Set<String> distinctAnswers = Sets.newHashSet();

        for (Map.Entry<String, NameActivity> entry : responder.names.entrySet()) {
            NameActivity activity = entry.getValue();
            distinctAnswers.addAll(activity.answerAddresses);

            boolean selfPointing = !Collections.disjoint(activity.answerAddresses, responder.ownAddresses);
            for (String protocol : activity.protocols) {
                allByProtocol.computeIfAbsent(protocol, k -> Maps.newLinkedHashMap()).put(entry.getKey(), activity);
                if (selfPointing) {
                    selfPointingByProtocol.computeIfAbsent(protocol, k -> Maps.newLinkedHashMap())
                            .put(entry.getKey(), activity);
                }
            }
        }

        Map<String, NameActivity> result = Maps.newLinkedHashMap();
        for (Map.Entry<String, Map<String, NameActivity>> entry : selfPointingByProtocol.entrySet()) {
            if (entry.getValue().size() >= threshold(entry.getKey(), config)) {
                result.putAll(entry.getValue());
            }
        }

        if (result.isEmpty() && distinctAnswers.size() <= 2) {
            for (Map.Entry<String, Map<String, NameActivity>> entry : allByProtocol.entrySet()) {
                if (entry.getValue().size() >= threshold(entry.getKey(), config)) {
                    result.putAll(entry.getValue());
                }
            }
        }

        return result;
    }

    private static int threshold(String protocol, SpoofingConfiguration config) {
        try {
            return switch (NameResolutionProtocol.valueOf(protocol)) {
                case LLMNR -> config.nameresLlmnrNameThreshold();
                case NBTNS -> config.nameresNbtnsNameThreshold();
                case MDNS -> config.nameresMdnsNameThreshold();
            };
        } catch (IllegalArgumentException e) {
            return Integer.MAX_VALUE;
        }
    }

    @Nullable
    private static SpoofingExclusionEntry findMatchingExclusion(Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> exclusions,
                                                                ResponderActivity responder) {
        for (Map.Entry<SpoofingExclusionEntry, SpoofingExclusionMatcher> e : exclusions.entrySet()) {
            if (SpoofingExclusions.matchesNameResolutionResponder(e.getValue(), responder.ownAddresses, responder.mac)) {
                return e.getKey();
            }
        }

        return null;
    }

    private void raiseNewFindingAlert(NameResolutionSpoofingFindingEntry finding, UUID organizationId, UUID tenantId) {
        List<String> protocols = finding.protocols().stream().map(p -> {
            try {
                return NameResolutionProtocol.valueOf(p).getHumanReadable();
            } catch (IllegalArgumentException e) {
                return p;
            }
        }).toList();

        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("finding_uuid", finding.uuid().toString());
        attributes.put("responder_mac", finding.responderMac());
        attributes.put("responder_addresses", String.join(",", finding.responderAddresses()));
        attributes.put("protocols", String.join(",", finding.protocols()));
        attributes.put("name_count", String.valueOf(finding.nameCount()));
        attributes.put("names", String.join(",", finding.names().subList(0, Math.min(ALERT_NAMES_SAMPLE, finding.names().size()))));
        attributes.put("response_count", String.valueOf(finding.responseCount()));
        attributes.put("victim_count", String.valueOf(finding.victimCount()));

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                null,
                DetectionType.SPOOFING_NAME_RESOLUTION_POISONING,
                Subsystem.ETHERNET,
                "Name resolution poisoning by [" + finding.responderMac() + "] detected. It answered [" +
                        finding.nameCount() + "] names over " + String.join(", ", protocols) + ".",
                attributes,
                Set.of("finding_uuid")
        );
    }

}
