package app.nzyme.core.dot11.monitoring;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.AlertOrigin;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.db.monitoring.MonitoredBSSID;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.periodicals.Periodical;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.MetricNames;
import app.nzyme.plugin.Subsystem;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Dot11MultipleChannelsMonitor extends Periodical {

    private static final Logger LOG = LogManager.getLogger(Dot11MultipleChannelsMonitor.class);

    private static final int MAX_SIGHTINGS = 50000;

    private final NzymeNode nzyme;
    private final Timer runTimer;

    private record Observation(String bssid, UUID tapUuid) {}

    private record Placement(String bssid, Optional<UUID> locationId) {}

    public Dot11MultipleChannelsMonitor(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.runTimer = nzyme.getMetrics().timer(MetricNames.DOT11_MULTIPLE_CHANNELS_MONITOR_RUN_TIMER);
    }

    @Override
    protected void execute() {
        try (Timer.Context ignored = runTimer.time()) {
            DateTime since = DateTime.now().minusMinutes(MultipleChannelsRule.WINDOW_MINUTES);

            for (Dot11.TenantId tenant : nzyme.getDot11().findTenantsMonitoringMultipleChannels()) {
                try {
                    check(tenant, since);
                } catch (Exception e) {
                    LOG.error("Could not check channels of monitored networks of tenant [{}/{}].",
                            tenant.organizationId(), tenant.tenantId(), e);
                }
            }
        }
    }

    private void check(Dot11.TenantId tenant, DateTime since) {
        Map<UUID, Tap> taps = nzyme.getTapManager()
                .findAllTapsOfTenant(tenant.organizationId(), tenant.tenantId())
                .stream()
                .collect(Collectors.toMap(Tap::uuid, Function.identity()));

        for (MonitoredSSID network : nzyme.getDot11()
                .findAllMonitoredSSIDs(tenant.organizationId(), tenant.tenantId())) {
            if (!network.isEnabled() || !network.enabledMultipleChannels()) {
                continue;
            }

            try {
                check(network, taps, since);
            } catch (Exception e) {
                LOG.error("Could not check channels of monitored network [{}].", network.uuid(), e);
            }
        }
    }

    private void check(MonitoredSSID network, Map<UUID, Tap> taps, DateTime since) {
        List<UUID> tapIds = List.copyOf(taps.keySet());
        List<String> bssids = nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(network.id())
                .stream()
                .map(MonitoredBSSID::bssid)
                .toList();

        List<String> candidates = nzyme.getDot11().findBssidsDeclaringSeveralFrequencies(tapIds, bssids, since);
        if (candidates.isEmpty()) {
            return;
        }

        List<Dot11.DeclaredFrequency> declared = nzyme.getDot11().findDeclaredFrequencies(
                tapIds, candidates, since, MultipleChannelsRule.MINIMUM_BEACONS_PER_REPORT, MAX_SIGHTINGS
        );

        if (declared.size() == MAX_SIGHTINGS) {
            LOG.warn("More than <{}> channel sightings of monitored network [{}] in the last <{}> minutes. " +
                    "Considering the latest only.", MAX_SIGHTINGS, network.uuid(), MultipleChannelsRule.WINDOW_MINUTES);
        }

        Map<Observation, List<MultipleChannelsRule.Sighting>> observations = Maps.newHashMap();
        for (Dot11.DeclaredFrequency d : declared) {
            observations.computeIfAbsent(new Observation(d.bssid(), d.tapUuid()), o -> Lists.newArrayList())
                    .add(new MultipleChannelsRule.Sighting(d.at(), d.frequency()));
        }

        Map<Placement, Set<UUID>> placementTaps = Maps.newHashMap();
        Map<Placement, Set<Integer>> placementFrequencies = Maps.newHashMap();
        for (Map.Entry<Observation, List<MultipleChannelsRule.Sighting>> observation : observations.entrySet()) {
            Optional<MultipleChannelsRule.Finding> finding = MultipleChannelsRule.evaluate(observation.getValue());
            if (finding.isEmpty()) {
                continue;
            }

            UUID tapUuid = observation.getKey().tapUuid();
            Placement placement = new Placement(
                    observation.getKey().bssid(), Optional.ofNullable(taps.get(tapUuid).locationId())
            );

            placementTaps.computeIfAbsent(placement, p -> Sets.newHashSet()).add(tapUuid);
            placementFrequencies.computeIfAbsent(placement, p -> new TreeSet<>())
                    .addAll(List.of(finding.get().firstFrequency(), finding.get().secondFrequency()));
        }

        for (Map.Entry<Placement, Set<UUID>> placement : placementTaps.entrySet()) {
            String bssid = placement.getKey().bssid();

            Map<String, String> attributes = Maps.newHashMap();
            attributes.put("bssid", bssid);
            attributes.put("frequencies", placementFrequencies.get(placement.getKey()).stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")));

            try {
                nzyme.getDetectionAlertService().raiseAlert(
                        network.organizationId(),
                        network.tenantId(),
                        network.uuid(),
                        AlertOrigin.taps(placement.getValue()),
                        DetectionType.DOT11_MONITOR_MULTIPLE_CHANNELS,
                        Subsystem.DOT11,
                        "BSSID \"" + bssid + "\" of monitored network \"" + network.ssid() + "\" is on " +
                                "multiple channels at once",
                        attributes,
                        Set.of("bssid")
                );
            } catch (Exception e) {
                LOG.error("Could not raise multiple channels alert for BSSID [{}] of monitored network [{}].",
                        bssid, network.uuid(), e);
            }
        }
    }

    @Override
    public String getName() {
        return "802.11 Multiple Channels Monitor";
    }

}
