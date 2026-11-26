package app.nzyme.core.dot11.monitoring.channelswitches;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.AlertOrigin;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.channelswitches.ChannelSwitchAlertConfiguration;
import app.nzyme.core.dot11.channelswitches.ChannelSwitchMonitorRule;
import app.nzyme.core.dot11.channelswitches.ChannelSwitchRules;
import app.nzyme.core.dot11.db.monitoring.MonitoredBSSID;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.periodicals.Periodical;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.MetricNames;
import app.nzyme.plugin.Subsystem;
import com.codahale.metrics.Timer;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Dot11ChannelSwitchMonitor extends Periodical {

    private static final Logger LOG = LogManager.getLogger(Dot11ChannelSwitchMonitor.class);

    private static final int RECENT_MINUTES = 5;
    private static final int JUDGE_AFTER_MINUTES = ChannelSwitchRules.OUTCOME_EVIDENCE_MINUTES + 1;
    private static final int MAX_OUTCOMES_PER_RUN = 20000;
    private static final int MAX_OTHER_NETWORK_SIGHTINGS_PER_RUN = 25;

    private static final int SIGNAL_REPORT_SECONDS = 30;
    private static final int SIGNAL_JUDGE_WITHIN_MINUTES = 30;
    private static final int MAX_SIGNAL_JUDGEMENTS_PER_RUN = 5000;

    private final NzymeNode nzyme;
    private final Timer runTimer;

    private record Sighting(String bssid, Optional<UUID> locationId) {}

    public Dot11ChannelSwitchMonitor(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.runTimer = nzyme.getMetrics().timer(MetricNames.DOT11_CHANNEL_SWITCH_MONITOR_RUN_TIMER);
    }

    @Override
    protected void execute() {
        try (Timer.Context ignored = runTimer.time()) {
            DateTime now = DateTime.now();
            DateTime judgeBefore = now.minusMinutes(JUDGE_AFTER_MINUTES);

            try {
                // Monitored BSSIDs first, so that a flood of announcements for others does not delay their alerts.
                int judged = nzyme.getDot11().resolveChannelSwitchOutcomes(now, judgeBefore, true, MAX_OUTCOMES_PER_RUN)
                        + nzyme.getDot11().resolveChannelSwitchOutcomes(now, judgeBefore, false, MAX_OUTCOMES_PER_RUN);

                if (judged >= MAX_OUTCOMES_PER_RUN) {
                    LOG.warn("Judged the outcome of <{}> channel switch announcements in one run. More are waiting " +
                            "and are judged in the next runs.", judged);
                }
            } catch (Exception e) {
                LOG.error("Could not resolve outcomes of channel switch announcements.", e);
            }

            try {
                judgeSignals(now);
            } catch (Exception e) {
                LOG.error("Could not judge the signal strength of channel switch announcements.", e);
            }

            DateTime since = now.minusMinutes(RECENT_MINUTES);

            for (Dot11.TenantId tenant : nzyme.getDot11().findTenantsWithChannelSwitches(since)) {
                try {
                    alert(tenant, since);
                } catch (Exception e) {
                    LOG.error("Could not process channel switch announcements of tenant [{}/{}].",
                            tenant.organizationId(), tenant.tenantId(), e);
                }
            }
        }
    }

    // An announcement is judged when the minute it was reported in is over and its last reports have arrived.
    private void judgeSignals(DateTime now) {
        DateTime announcedBefore = now.minusSeconds(SIGNAL_REPORT_SECONDS).withSecondOfMinute(0).withMillisOfSecond(0);
        DateTime announcedAfter = now.minusMinutes(SIGNAL_JUDGE_WITHIN_MINUTES);

        nzyme.getDot11().closeChannelSwitchSignalJudgements(now, announcedAfter);

        for (Dot11.TenantId tenant : nzyme.getDot11().findTenantsWithUnjudgedChannelSwitchSignals(announcedBefore)) {
            try {
                List<UUID> taps = nzyme.getTapManager()
                        .findAllTapsOfTenant(tenant.organizationId(), tenant.tenantId())
                        .stream()
                        .map(Tap::uuid)
                        .toList();

                int judged = nzyme.getDot11().judgeChannelSwitchSignals(
                        taps,
                        now,
                        announcedAfter,
                        announcedBefore,
                        ChannelSwitchAlertConfiguration.load(nzyme, tenant.organizationId(), tenant.tenantId())
                                .signalMismatchMinimumDifference(),
                        MAX_SIGNAL_JUDGEMENTS_PER_RUN
                );

                if (judged >= MAX_SIGNAL_JUDGEMENTS_PER_RUN) {
                    LOG.warn("Judged the signal strength of <{}> channel switch announcements of tenant [{}/{}] " +
                                    "in one run. More are waiting and are judged in the next runs.",
                            judged, tenant.organizationId(), tenant.tenantId());
                }
            } catch (Exception e) {
                LOG.error("Could not judge the signal strength of channel switch announcements of tenant [{}/{}].",
                        tenant.organizationId(), tenant.tenantId(), e);
            }
        }
    }

    private void alert(Dot11.TenantId tenant, DateTime since) {
        Map<UUID, Tap> taps = nzyme.getTapManager()
                .findAllTapsOfTenant(tenant.organizationId(), tenant.tenantId())
                .stream()
                .collect(Collectors.toMap(Tap::uuid, Function.identity()));

        for (MonitoredSSID network : nzyme.getDot11()
                .findAllMonitoredSSIDs(tenant.organizationId(), tenant.tenantId())) {
            if (!network.isEnabled() || !network.enabledChannelSwitchMonitor()) {
                continue;
            }

            try {
                alert(network, taps, since);
            } catch (Exception e) {
                LOG.error("Could not process channel switch announcements of monitored network [{}].",
                        network.uuid(), e);
            }
        }

        ChannelSwitchAlertConfiguration configuration = ChannelSwitchAlertConfiguration
                .load(nzyme, tenant.organizationId(), tenant.tenantId());

        if (!configuration.monitoredNetworksOnly()) {
            alertOtherNetworks(tenant, configuration, taps, since);
        }
    }

    private void alert(MonitoredSSID network, Map<UUID, Tap> taps, DateTime since) {
        List<String> bssids = nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(network.id())
                .stream()
                .map(MonitoredBSSID::bssid)
                .toList();

        List<Dot11.AlertingChannelSwitches> alerting = nzyme.getDot11().findAlertingChannelSwitches(
                List.copyOf(taps.keySet()),
                bssids,
                since,
                network.detectionConfigChannelSwitchBurstThreshold(),
                ChannelSwitchMonitorRule.parse(network.detectionConfigChannelSwitchDisabledRules())
        );

        for (Map.Entry<Sighting, List<Dot11.AlertingChannelSwitches>> sighting : sightings(alerting, taps).entrySet()) {
            String bssid = sighting.getKey().bssid();

            try {
                nzyme.getDetectionAlertService().raiseAlert(
                        network.organizationId(),
                        network.tenantId(),
                        network.uuid(),
                        AlertOrigin.taps(tapsOf(sighting.getValue())),
                        DetectionType.DOT11_MONITOR_CHANNEL_SWITCH_ANOMALIES,
                        Subsystem.DOT11,
                        "Channel switch announcements with anomalies for BSSID \"" + bssid + "\" of monitored " +
                                "network \"" + network.ssid() + "\"",
                        attributes(bssid, sighting.getValue(), network.detectionConfigChannelSwitchBurstThreshold()),
                        Set.of("bssid")
                );
            } catch (Exception e) {
                LOG.error("Could not raise channel switch alert for BSSID [{}] of monitored network [{}].",
                        bssid, network.uuid(), e);
            }
        }
    }

    private void alertOtherNetworks(Dot11.TenantId tenant,
                                    ChannelSwitchAlertConfiguration configuration,
                                    Map<UUID, Tap> taps,
                                    DateTime since) {
        List<Dot11.AlertingChannelSwitches> alerting = nzyme.getDot11().findAlertingChannelSwitchesOfOtherNetworks(
                List.copyOf(taps.keySet()),
                tenant.organizationId(),
                tenant.tenantId(),
                since,
                configuration.otherNetworksBurstThreshold(),
                configuration.otherNetworksDisabledRules(),
                MAX_OTHER_NETWORK_SIGHTINGS_PER_RUN
        );

        if (alerting.size() >= MAX_OTHER_NETWORK_SIGHTINGS_PER_RUN) {
            LOG.warn("More than <{}> access points outside of monitored networks have alerting channel switch " +
                            "announcements for tenant [{}/{}]. Alerting for those with the largest announcements.",
                    MAX_OTHER_NETWORK_SIGHTINGS_PER_RUN, tenant.organizationId(), tenant.tenantId());
        }

        for (Map.Entry<Sighting, List<Dot11.AlertingChannelSwitches>> sighting : sightings(alerting, taps).entrySet()) {
            String bssid = sighting.getKey().bssid();

            Set<String> ssids = new TreeSet<>();
            sighting.getValue().forEach(cs -> ssids.addAll(cs.ssids()));

            Map<String, String> attributes =
                    attributes(bssid, sighting.getValue(), configuration.otherNetworksBurstThreshold());
            attributes.put("ssids", String.join(", ", ssids));

            try {
                nzyme.getDetectionAlertService().raiseAlert(
                        tenant.organizationId(),
                        tenant.tenantId(),
                        null,
                        AlertOrigin.taps(tapsOf(sighting.getValue())),
                        DetectionType.DOT11_CHANNEL_SWITCH_ANOMALIES,
                        Subsystem.DOT11,
                        "Channel switch announcements with anomalies for BSSID \"" + bssid + "\""
                                + (ssids.isEmpty() ? "" : " (" + String.join(", ", ssids) + ")"),
                        attributes,
                        Set.of("bssid")
                );
            } catch (Exception e) {
                LOG.error("Could not raise channel switch alert for BSSID [{}] of tenant [{}/{}].",
                        bssid, tenant.organizationId(), tenant.tenantId(), e);
            }
        }
    }

    private static Map<Sighting, List<Dot11.AlertingChannelSwitches>> sightings(List<Dot11.AlertingChannelSwitches> alerting,
                                                                              Map<UUID, Tap> taps) {
        return alerting.stream().collect(Collectors.groupingBy(cs -> new Sighting(
                cs.bssid(), Optional.ofNullable(taps.get(cs.tapUuid()).locationId())
        )));
    }

    private static Set<UUID> tapsOf(List<Dot11.AlertingChannelSwitches> announcements) {
        return announcements.stream().map(Dot11.AlertingChannelSwitches::tapUuid).collect(Collectors.toSet());
    }

    // Existing alerts only take new values for the attributes they were raised with.
    private static Map<String, String> attributes(String bssid,
                                                  List<Dot11.AlertingChannelSwitches> announcements,
                                                  int burstThreshold) {
        Dot11.AlertingChannelSwitches latest = announcements.stream()
                .max(Comparator.comparing(Dot11.AlertingChannelSwitches::latest))
                .orElseThrow();
        DateTime earliest = announcements.stream()
                .map(Dot11.AlertingChannelSwitches::earliest)
                .min(Comparator.naturalOrder())
                .orElseThrow();

        Set<String> anomalies = new TreeSet<>();
        int peakFramesPerSecond = 0;
        boolean notFollowed = false;
        for (Dot11.AlertingChannelSwitches cs : announcements) {
            anomalies.addAll(cs.anomalies());
            peakFramesPerSecond = Math.max(peakFramesPerSecond, cs.peakFramesPerSecond());
            notFollowed = notFollowed || cs.notFollowed();
        }

        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("bssid", bssid);
        attributes.put("anomalies", String.join(",", anomalies));
        attributes.put("frame_type", latest.frameType());
        attributes.put("new_channel", String.valueOf(latest.newChannel()));
        attributes.put("frequency", latest.frequency() == null ? "" : String.valueOf(latest.frequency()));
        attributes.put("peak_frames_per_second", String.valueOf(peakFramesPerSecond));
        attributes.put("burst_threshold", String.valueOf(burstThreshold));
        attributes.put("not_followed", String.valueOf(notFollowed));
        attributes.put("announced_at", earliest.toString());

        return attributes;
    }

    @Override
    public String getName() {
        return "802.11 Channel Switch Monitor";
    }

}
