package app.nzyme.core.tables.dot11;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.AlertOrigin;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.channelswitches.ChannelSwitchRules;
import app.nzyme.core.dot11.disco.ConnectionFrameType;
import app.nzyme.core.dot11.db.monitoring.*;
import app.nzyme.core.dot11.bandits.Dot11BanditDescription;
import app.nzyme.core.dot11.bandits.Dot11Bandits;
import app.nzyme.core.dot11.db.monitoring.probereq.MonitoredProbeRequestEntry;
import app.nzyme.core.rest.resources.taps.reports.tables.dot11.*;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.tables.dot11.monitoring.PreLoadedMonitoredBSSID;
import app.nzyme.core.tables.dot11.monitoring.PreLoadedMonitoredSSID;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.Subsystem;
import tools.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import info.debatty.java.stringsimilarity.JaroWinkler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;
import com.codahale.metrics.Timer;

import jakarta.annotation.Nullable;

import java.sql.Types;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class Dot11Table implements DataTable {

    private static final Logger LOG = LogManager.getLogger(Dot11Table.class);

    private final TablesService tablesService;
    private final ObjectMapper om;

    private final Timer totalReportTimer;
    private final Timer bssidReportTimer;
    private final Timer clientsReportTimer;
    private final Timer discoReportTimer;
    private final Timer connectionFramesReportTimer;
    private final Timer channelSwitchReportTimer;
    private final Timer alertTimer;

    public Dot11Table(TablesService tablesService) {
        this.tablesService = tablesService;
        this.om = new ObjectMapper();

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.DOT11_TOTAL_REPORT_PROCESSING_TIMER);
        this.bssidReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.DOT11_BSSID_REPORT_PROCESSING_TIMER);
        this.clientsReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.DOT11_CLIENTS_REPORT_PROCESSING_TIMER);
        this.discoReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.DOT11_DISCO_REPORT_PROCESSING_TIMER);
        this.connectionFramesReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.DOT11_CONNECTION_FRAMES_REPORT_PROCESSING_TIMER);
        this.channelSwitchReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.DOT11_CHANNEL_SWITCH_REPORT_PROCESSING_TIMER);
        this.alertTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.DOT11_ALERT_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, Dot11TablesReport report) {
        try (Timer.Context ignored = totalReportTimer.time()) {
            Optional<Tap> tap = tablesService.getNzyme().getTapManager().findTap(tapUuid);

            if (tap.isEmpty()) {
                LOG.warn("Not handling report of unknown tap [{}].", tapUuid);
                return;
            }

            try (Timer.Context ignored2 = bssidReportTimer.time()) {
                writeBSSIDs(tap.get(), timestamp, report.bssids(), tap.get().organizationId(), tap.get().tenantId());
            }

            try (Timer.Context ignored2 = clientsReportTimer.time()) {
                writeClients(tap.get(), timestamp, report.clients());
            }

            try (Timer.Context ignored2 = discoReportTimer.time()) {
                writeDisco(tap.get(), timestamp, report.disco());
            }

            try (Timer.Context ignored2 = connectionFramesReportTimer.time()) {
                writeConnectionFrames(tap.get(), timestamp, report.connectionFrames());
                writeFrameOverflows(tap.get(), timestamp, report.disco().overflow(), report.connectionFrameOverflow());
            }

            try (Timer.Context ignored2 = channelSwitchReportTimer.time()) {
                writeChannelSwitches(tap.get(), timestamp, report.channelSwitches());
            }

            try (Timer.Context ignored2 = alertTimer.time()) {
                handleAlerts(tap.get(), report.alerts());
            }
        }
    }

    private void writeClients(Tap tap, DateTime timestamp, Map<String, Dot11ClientReport> clients) {
        Map<String, MonitoredProbeRequestEntry> monitoredProbeRequests = tablesService.getNzyme().getDot11()
                .findAllMonitoredProbeRequests(tap.organizationId(), tap.tenantId(), Integer.MAX_VALUE, 0)
                .stream()
                .collect(Collectors.toMap(MonitoredProbeRequestEntry::ssid, entry -> entry));

        for (Map.Entry<String, Dot11ClientReport> entry : clients.entrySet()) {
            String clientMac = entry.getKey();
            Dot11ClientReport report = entry.getValue();

            long clientDatabaseId = tablesService.getNzyme().getDatabase().withHandle(handle ->
                    handle.createQuery("INSERT INTO dot11_clients(tap_uuid, client_mac, client_mac_is_randomized, " +
                                    "wildcard_probe_requests, signal_strength_average, signal_strength_max, " +
                                    "signal_strength_min, signal_by_frequency, created_at) VALUES(:tap_uuid, " +
                                    ":client_mac, :client_mac_is_randomized, :wildcard_probe_requests, " +
                                    ":signal_strength_average, :signal_strength_max, :signal_strength_min, " +
                                    ":signal_by_frequency::jsonb, :created_at) RETURNING id")
                            .bind("tap_uuid", tap.uuid())
                            .bind("client_mac", clientMac)
                            .bind("client_mac_is_randomized", Tools.macAddressIsRandomized(clientMac))
                            .bind("wildcard_probe_requests", report.wildcardProbeRequests())
                            .bind("signal_strength_average", report.signalStrength().average())
                            .bind("signal_strength_min", report.signalStrength().min())
                            .bind("signal_strength_max", report.signalStrength().max())
                            .bind("signal_by_frequency", signalByFrequencyJson(report.signalByFrequency()))
                            .bind("created_at", timestamp)
                            .mapTo(Long.class)
                            .one()
            );

            tablesService.getNzyme().getDatabase().useHandle(handle -> {
                PreparedBatch batch = handle.prepareBatch(
                        "INSERT INTO dot11_client_probereq_ssids(client_id, ssid, frame_count, tap_uuid) " +
                                "VALUES(:client_id, :ssid, :frame_count, :tap_uuid)"
                );

                for (Map.Entry<String, Long> pr : report.probeRequestSSIDs().entrySet()) {
                    String ssid = Tools.sanitizeSSID(pr.getKey());

                    // Check if we are monitoring for this probe request SSID and raise alert if so.
                    if (monitoredProbeRequests.containsKey(ssid)) {
                        // Raise alert.
                        Map<String, String> attributes = Maps.newHashMap();
                        attributes.put("ssid", ssid);
                        attributes.put("client_mac", clientMac);

                        tablesService.getNzyme().getDetectionAlertService().raiseAlert(
                                tap.organizationId(),
                                tap.tenantId(),
                                null,
                                AlertOrigin.tap(tap.uuid()),
                                DetectionType.DOT11_PROBEREQ,
                                Subsystem.DOT11,
                                "Monitored probe request for SSID \"" + ssid + "\" detected in range.",
                                attributes,
                                Set.of("ssid")
                        );
                    }

                    batch.bind("client_id", clientDatabaseId)
                            .bind("ssid", ssid)
                            .bind("frame_count", pr.getValue())
                            .bind("tap_uuid", tap.uuid())
                            .add();
                }

                batch.execute();
            });
        }
    }

    public void writeBSSIDs(Tap tap, DateTime timestamp,
                            Map<String, Dot11BSSIDReport> bssids,
                            UUID organizationId,
                            UUID tenantId) {
        // Collect all monitored SSIDs and their attributes.
        Map<String, PreLoadedMonitoredSSID> monitoredSSIDs = Maps.newHashMap();
        List<String> monitoredSSIDNames = Lists.newArrayList();
        NzymeNode nzyme = tablesService.getNzyme();

        nzyme.getDatabase().useHandle(handle -> {
            for (MonitoredSSID s : nzyme.getDot11().findAllMonitoredSSIDs(tap.organizationId(), tap.tenantId())) {
                if (!s.isEnabled()) {
                    continue;
                }

                monitoredSSIDNames.add(s.ssid());

                Map<String, PreLoadedMonitoredBSSID> preLoadedBSSIDs = Maps.newHashMap();
                for (MonitoredBSSID b : nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(s.id())) {
                    List<String> fingerprints = Lists.newArrayList();
                    for (MonitoredFingerprint f : nzyme.getDot11().findMonitoredFingerprintsOfMonitoredBSSID(b.id())) {
                        fingerprints.add(f.fingerprint());
                    }

                    // Two requests at the same time can have stored a BSSID twice. Both rows count.
                    PreLoadedMonitoredBSSID existing = preLoadedBSSIDs.get(b.bssid());
                    if (existing != null) {
                        fingerprints.addAll(existing.fingerprints());
                    }

                    preLoadedBSSIDs.put(b.bssid(), PreLoadedMonitoredBSSID.create(b.bssid(), fingerprints));
                }

                List<Integer> preLoadedChannels = Lists.newArrayList();
                for (MonitoredChannel c : nzyme.getDot11().findMonitoredChannelsOfMonitoredNetwork(s.id())) {
                    preLoadedChannels.add((int) c.frequency());
                }

                List<String> preLoadedSecuritySuites = Lists.newArrayList();
                for (MonitoredSecuritySuite ss : nzyme.getDot11().findMonitoredSecuritySuitesOfMonitoredNetwork(s.id())) {
                    preLoadedSecuritySuites.add(ss.securitySuite());
                }

                monitoredSSIDs.put(s.ssid(), PreLoadedMonitoredSSID.create(
                        s.id(),
                        s.uuid(),
                        s.ssid(),
                        preLoadedBSSIDs,
                        preLoadedChannels,
                        preLoadedSecuritySuites,
                        s.enabledUnexpectedBSSID(),
                        s.enabledUnexpectedChannel(),
                        s.enabledUnexpectedSecuritySuites(),
                        s.enabledUnexpectedFingerprint(),
                        s.enabledUnexpectedSignalTracks(),
                        s.enabledSimilarLookingSSID(),
                        s.enabledSSIDSubstring(),
                        s.detectionConfigSimilarLookingSSIDThreshold(),
                        s.enabledSimilarLookingSSID()
                                ? Sets.newHashSet(nzyme.getDot11().findAllowedSimilarSSIDs(s.id())) : Set.of()
                ));
            }


            // Load all bandits.
            List<Dot11BanditDescription> bandits = Lists.newArrayList(Dot11Bandits.BUILT_IN);
            for (CustomBanditDescription bandit : nzyme.getDot11()
                    .findAllCustomBandits(organizationId, tenantId, Integer.MAX_VALUE, 0)) {
                List<String> fingerprints = nzyme.getDot11().findFingerprintsOfCustomBandit(bandit.id());

                bandits.add(Dot11BanditDescription.create(
                        bandit.uuid().toString(),
                        true,
                        bandit.name(),
                        bandit.description(),
                        fingerprints
                ));
            }

            List<SSIDProcessingTask> ssidProcessingTasks = Lists.newArrayList();

            for (Map.Entry<String, Dot11BSSIDReport> entry : bssids.entrySet()) {
                String bssid = entry.getKey();
                Dot11BSSIDReport report = entry.getValue();

                String[] fingerprintsArr = report.fingerprints().toArray(new String[0]);

                // A name that is empty once sanitized is no name. Its frames belong to the hidden network.
                long hiddenSSIDFrames = count(report.hiddenSSIDFrames());
                List<Dot11AdvertisedNetworkReport> hiddenNetworks = Lists.newArrayList();
                if (report.hiddenNetwork() != null) {
                    hiddenNetworks.add(report.hiddenNetwork());
                }
                for (Map.Entry<String, Dot11AdvertisedNetworkReport> network : report.advertisedNetworks().entrySet()) {
                    if (Tools.sanitizeSSID(network.getKey()).isEmpty()) {
                        hiddenSSIDFrames += count(network.getValue().beaconAdvertisements())
                                + count(network.getValue().probeResponseAdvertisements());
                        hiddenNetworks.add(network.getValue());
                    }
                }

                HiddenNetwork hiddenNetwork = HiddenNetwork.of(hiddenNetworks, om);
                checkHiddenNetworks(nzyme, monitoredSSIDs, bssid, hiddenNetworks, tap);

                long bssidDatabaseId = handle.createQuery(
                        "INSERT INTO dot11_bssids(tap_uuid, bssid, oui, " +
                                "signal_strength_average, signal_strength_max, signal_strength_min, " +
                                "hidden_ssid_frames, fingerprints, declared_frequencies, " +
                                "hidden_beacon_advertisements, hidden_proberesp_advertisements, " +
                                "hidden_channel_statistics, hidden_signal_histogram, hidden_declared_frequencies, " +
                                "created_at) VALUES(:tap_uuid, :bssid, NULL, " +
                                ":signal_strength_average, :signal_strength_max, :signal_strength_min, " +
                                ":hidden_ssid_frames, :fingerprints, CAST(:declared_frequencies AS jsonb), " +
                                ":hidden_beacon_advertisements, :hidden_proberesp_advertisements, " +
                                "CAST(:hidden_channel_statistics AS jsonb), CAST(:hidden_signal_histogram AS jsonb), " +
                                "CAST(:hidden_declared_frequencies AS jsonb), :created_at) RETURNING id")
                        .bind("hidden_beacon_advertisements", hiddenNetwork.beaconAdvertisements())
                        .bind("hidden_proberesp_advertisements", hiddenNetwork.probeResponseAdvertisements())
                        .bind("hidden_channel_statistics", hiddenNetwork.channelStatistics())
                        .bind("hidden_signal_histogram", hiddenNetwork.signalHistogram())
                        .bind("hidden_declared_frequencies", hiddenNetwork.declaredFrequencies())
                        .bind("tap_uuid", tap.uuid())
                        .bind("bssid", bssid)
                        .bind("signal_strength_average", report.signalStrength().average())
                        .bind("signal_strength_max", report.signalStrength().max())
                        .bind("signal_strength_min", report.signalStrength().min())
                        .bind("hidden_ssid_frames", hiddenSSIDFrames)
                        .bindBySqlType("fingerprints", fingerprintsArr, Types.ARRAY)
                        .bind("declared_frequencies", declaredFrequenciesJson(
                                declaredFrequencies(report.declaredFrequencies(), MAX_DECLARED_FREQUENCIES_OF_BSSID)
                        ))
                        .bind("created_at", timestamp)
                        .mapTo(Long.class)
                        .one();

                // Check BSSID Fingerprints.
                for (String fingerprint : report.fingerprints()) {
                    // Is this a known bandit fingerprint?
                    for (Dot11BanditDescription bandit : bandits) {
                        if (bandit.fingerprints() != null && bandit.fingerprints().contains(fingerprint)) {
                            Map<String, String> attributes = Maps.newHashMap();
                            attributes.put("fingerprint", fingerprint);
                            attributes.put("bssid", bssid);
                            attributes.put("tap_uuid", tap.uuid().toString());
                            attributes.put("bandit_name", bandit.name());
                            attributes.put("bandit_description", bandit.description());
                            attributes.put("bandit_is_custom", String.valueOf(bandit.isCustom()));

                            tablesService.getNzyme().getDetectionAlertService().raiseAlert(
                                    tap.organizationId(),
                                    tap.tenantId(),
                                    null,
                                    AlertOrigin.tap(tap.uuid()),
                                    DetectionType.DOT11_BANDIT_CONTACT,
                                    Subsystem.DOT11,
                                    "Bandit \"" + bandit.name() + "\" advertising BSSID \"" + bssid + "\" " +
                                            "detected in range.",
                                    attributes,
                                    Set.of("bssid", "fingerprint", "bandit_is_custom")
                            );
                        }
                    }
                }

                // BSSID Clients.
                PreparedBatch bssidClientsBatch = handle.prepareBatch(
                        "INSERT INTO dot11_bssid_clients(bssid_id, client_mac, tx_frames, " +
                                "tx_bytes, rx_frames, rx_bytes, signal_strength_average, " +
                                "signal_strength_min, signal_strength_max, signal_by_frequency) " +
                                "VALUES(:bssid_id, :client_mac, :tx_frames, :tx_bytes, :rx_frames, :rx_bytes, " +
                                ":signal_strength_average, :signal_strength_min, :signal_strength_max, " +
                                ":signal_by_frequency::jsonb)");

                for (Map.Entry<String, Dot11ClientStatisticsReport> client : report.clients().entrySet()) {
                    String mac = client.getKey();
                    Dot11ClientStatisticsReport stats = client.getValue();
                    Dot11SignalStrengthReport signal = stats.signalStrength() != null
                            && isSignalStrength(stats.signalStrength()) ? stats.signalStrength() : null;

                    if (!bssid.equals(mac)) { // Don't record BSSID itself.
                        bssidClientsBatch
                                .bind("bssid_id", bssidDatabaseId)
                                .bind("client_mac", mac)
                                .bind("tx_frames", stats.txFrames())
                                .bind("tx_bytes", stats.txBytes())
                                .bind("rx_frames", stats.rxFrames())
                                .bind("rx_bytes", stats.rxBytes())
                                .bind("signal_strength_average", signal == null ? null : signal.average())
                                .bind("signal_strength_min", signal == null ? null : signal.min())
                                .bind("signal_strength_max", signal == null ? null : signal.max())
                                .bind("signal_by_frequency", signalByFrequencyJson(stats.signalByFrequency()))
                                .add();
                    }
                }
                bssidClientsBatch.execute();

                // Pre-process all SSIDs.
                for (Map.Entry<String, Dot11AdvertisedNetworkReport> ssidEntry : report.advertisedNetworks().entrySet()) {
                    ssidProcessingTasks.add(SSIDProcessingTask.create(
                            bssid, ssidEntry.getKey(), ssidEntry.getValue(), bssidDatabaseId, tap, timestamp
                    ));
                }
            }

            // Write all SSIDs.
            CountDownLatch latch = new CountDownLatch(ssidProcessingTasks.size());
            for (SSIDProcessingTask ssidProcessingTask : ssidProcessingTasks) {
                tablesService.getProcessorPool().submit(() -> {
                    writeSSID(nzyme, handle, monitoredSSIDNames, monitoredSSIDs, ssidProcessingTask);

                    latch.countDown();
                });
            }

            // Wait for SSID processing to finish.
            try {
                latch.await();
            } catch (InterruptedException e) {
                LOG.error("SSID writer process interrupted.", e);
            }
        });
    }

    private void writeSSID(NzymeNode nzyme,
                           Handle handle,
                           List<String> monitoredSSIDNames,
                           Map<String, PreLoadedMonitoredSSID> monitoredSSIDs,
                           SSIDProcessingTask task) {

        try {
            // Replace all non-printable characters.
            final String ssid = Tools.sanitizeSSID(task.ssid());

            /*
             * If all characters were sanitized away, this is a hidden SSID.
             * (some access points build hidden SSIDs this way)
             */
            if (ssid.isEmpty()) {
                return;
            }

            Double[] ratesArr = task.ssidReport().rates().toArray(new Double[0]);
            String[] infraArr = task.ssidReport().infrastructureTypes().toArray(new String[0]);
            Boolean[] wpsArr = task.ssidReport().wps().toArray(new Boolean[0]);
            String[] fingerprintsArr = task.ssidReport().fingerprints().toArray(new String[0]);
            String securitySettingsJson = om.writeValueAsString(task.ssidReport().security());
            Map<Integer, Long> declaredFrequencies = declaredFrequencies(
                    task.ssidReport().declaredFrequencies(), MAX_DECLARED_FREQUENCIES_OF_SSID
            );

            Long ssidDatabaseId = handle.createQuery(
                    "INSERT INTO dot11_ssids(bssid_id, tap_uuid, ssid, bssid, " +
                            "signal_strength_average, signal_strength_max, signal_strength_min, " +
                            "beacon_advertisements, proberesp_advertisements, rates, infrastructure_types, " +
                            "has_wps, security_settings, fingerprints, declared_frequencies, created_at) " +
                            "VALUES(:bssid_id, :tap_uuid, :ssid, :bssid, :signal_strength_average, " +
                            ":signal_strength_max, :signal_strength_min, :beacon_advertisements, " +
                            ":proberesp_advertisements, :rates::double precision[], :infrastructure_types::text[], " +
                            ":has_wps::bool[], :security_settings::jsonb, :fingerprints, " +
                            "CAST(:declared_frequencies AS jsonb), :created_at) RETURNING *")
                    .bind("bssid_id", task.bssidDatabaseId())
                    .bind("tap_uuid", task.tap().uuid())
                    .bind("ssid", ssid)
                    .bind("bssid", task.bssid())
                    .bind("signal_strength_average", task.ssidReport().signalStrength().average())
                    .bind("signal_strength_max", task.ssidReport().signalStrength().max())
                    .bind("signal_strength_min", task.ssidReport().signalStrength().min())
                    .bind("beacon_advertisements", task.ssidReport().beaconAdvertisements())
                    .bind("proberesp_advertisements", task.ssidReport().probeResponseAdvertisements())
                    .bind("security_settings", securitySettingsJson)
                    .bindBySqlType("rates", ratesArr, Types.ARRAY)
                    .bindBySqlType("infrastructure_types", infraArr, Types.ARRAY)
                    .bindBySqlType("has_wps", wpsArr, Types.ARRAY)
                    .bindBySqlType("fingerprints", fingerprintsArr, Types.ARRAY)
                    .bind("declared_frequencies", declaredFrequenciesJson(declaredFrequencies))
                    .bind("created_at", task.timestamp())
                    .mapTo(Long.class)
                    .one();

            // Channel Statistics.
            PreparedBatch statsBatch = handle.prepareBatch(
                    "INSERT INTO dot11_channels(ssid_id, frequency, " +
                            "frame_type, stats_bytes, stats_frames) VALUES(:ssid_id, " +
                            ":frequency, :frame_type, :stats_bytes, :stats_frames)");
            for (Map.Entry<Long, Map<String, Dot11ChannelStatisticsReport>> cs : task.ssidReport().channelStatistics().entrySet()) {
                long frequency = cs.getKey();
                for (Map.Entry<String, Dot11ChannelStatisticsReport> ft : cs.getValue().entrySet()) {
                    String frameType = ft.getKey();
                    Dot11ChannelStatisticsReport stats = ft.getValue();

                    statsBatch
                            .bind("ssid_id", ssidDatabaseId)
                            .bind("frequency", frequency)
                            .bind("frame_type", frameType.toLowerCase())
                            .bind("stats_bytes", stats.bytes())
                            .bind("stats_frames", stats.frames())
                            .add();
                }
            }
            statsBatch.execute();

            // Write channel signal histogram.
            PreparedBatch histoBatch = handle.prepareBatch(
                    "INSERT INTO dot11_channel_histograms(ssid_id, frequency, " +
                            "signal_strength, frame_count) VALUES(:ssid_id, :frequency, " +
                            ":signal_strength, :frame_count)");
            for (Map.Entry<Long, Map<Long, Long>> channel : task.ssidReport().signalHistogram().entrySet()) {
                long frequency = channel.getKey();
                for (Map.Entry<Long, Long> histo : channel.getValue().entrySet()) {
                    histoBatch
                            .bind("ssid_id", ssidDatabaseId)
                            .bind("frequency", frequency)
                            .bind("signal_strength", histo.getKey())
                            .bind("frame_count", histo.getValue())
                            .add();
                }
            }
            histoBatch.execute();

            /*
             * Check if this SSID is similar to any monitored SSIDs or includes a monitored substring. Skip
             * other monitored SSIDs because they are considered trusted.
             */
            JaroWinkler jaroWinkler = new JaroWinkler();
            for (PreLoadedMonitoredSSID monitoredSSID : monitoredSSIDs.values()) {
                if (!monitoredSSIDNames.contains(ssid)) {
                    // Similar looking SSIDs.
                    if (monitoredSSID.enabledSimilarLookingSSID()
                            && !monitoredSSID.allowedSimilarSSIDs().contains(ssid)) {
                        double similarity = jaroWinkler
                                .similarity(monitoredSSID.ssid().toLowerCase(), ssid.toLowerCase()) * 100.0;

                        if (similarity > monitoredSSID.detectionConfigSimilarLookingSSIDThreshold()) {
                            Map<String, String> attributes = Maps.newHashMap();
                            attributes.put("similar_ssid", ssid);
                            attributes.put("similarity", String.valueOf(similarity));
                            attributes.put("similarity_threshold",
                                    String.valueOf(monitoredSSID.detectionConfigSimilarLookingSSIDThreshold()));

                            nzyme.getDetectionAlertService().raiseAlert(
                                    task.tap().organizationId(),
                                    task.tap().tenantId(),
                                    monitoredSSID.uuid(),
                                    AlertOrigin.tap(task.tap().uuid()),
                                    DetectionType.DOT11_MONITOR_SIMILAR_LOOKING_SSID,
                                    Subsystem.DOT11,
                                    "SSID \"" + ssid + "\" looking similar to monitored network SSID " +
                                            "\"" + monitoredSSID.ssid() + "\"",
                                    attributes,
                                    Set.of("similar_ssid")
                            );
                        }
                    }

                    // Restricted substrings.
                    if (monitoredSSID.enabledSSIDSubstring()) {
                        // Pull all restricted substrings.
                        for (RestrictedSSIDSubstring rss :
                                nzyme.getDot11().findAllRestrictedSSIDSubstrings(monitoredSSID.id())) {
                            if (ssid.toLowerCase().contains(rss.substring().toLowerCase())) {
                                Map<String, String> attributes = Maps.newHashMap();
                                attributes.put("ssid", ssid);
                                attributes.put("restricted_substring", rss.substring());

                                nzyme.getDetectionAlertService().raiseAlert(
                                        task.tap().organizationId(),
                                        task.tap().tenantId(),
                                        monitoredSSID.uuid(),
                                        AlertOrigin.tap(task.tap().uuid()),
                                        DetectionType.DOT11_MONITOR_SSID_SUBSTRING,
                                        Subsystem.DOT11,
                                        "SSID \"" + ssid + "\" contains restricted " +
                                                "substring \"" + rss.substring() + "\"",
                                        attributes,
                                        Set.of("ssid", "restricted_substring")
                                );
                            }
                        }
                    }
                }
            }

            // Network Monitoring / Alerting.
            PreLoadedMonitoredSSID monitoredSSID = monitoredSSIDs.get(ssid);
            if (monitoredSSID != null) {
                // This is a monitored SSID.

                PreLoadedMonitoredBSSID monitoredBSSID = monitoredSSID.bssids().get(task.bssid());
                if (monitoredBSSID == null) {
                    if (monitoredSSID.enabledUnexpectedBSSID()) {
                        // Unexpected BSSID.
                        Map<String, String> attributes = Maps.newHashMap();
                        attributes.put("bssid", task.bssid());

                        nzyme.getDetectionAlertService().raiseAlert(
                                task.tap().organizationId(),
                                task.tap().tenantId(),
                                monitoredSSID.uuid(),
                                AlertOrigin.tap(task.tap().uuid()),
                                DetectionType.DOT11_MONITOR_BSSID,
                                Subsystem.DOT11,
                                "Monitored network \"" + monitoredSSID.ssid() + "\" advertised with " +
                                        "unexpected BSSID \"" + task.bssid() + "\"",
                                attributes,
                                Set.of("bssid")
                        );
                    }
                } else {
                    checkFingerprints(nzyme, monitoredSSID, monitoredBSSID, task.bssid(),
                            task.ssidReport().fingerprints(), task.tap());
                }

                checkChannels(nzyme, monitoredSSID, declaredFrequencies.keySet(), task.tap());
                checkSecuritySuites(nzyme, monitoredSSID, task.ssidReport().security(), task.tap());
            }
        } catch(Exception e) {
            LOG.error("Could not write SSID.", e);
        }
    }

    // Frames without a network name cannot be matched by name. They are held to what is expected of their BSSID.
    private void checkHiddenNetworks(NzymeNode nzyme,
                                     Map<String, PreLoadedMonitoredSSID> monitoredSSIDs,
                                     String bssid,
                                     List<Dot11AdvertisedNetworkReport> hiddenNetworks,
                                     Tap tap) {
        if (hiddenNetworks.isEmpty()) {
            return;
        }

        for (PreLoadedMonitoredSSID monitoredSSID : monitoredSSIDs.values()) {
            PreLoadedMonitoredBSSID monitoredBSSID = monitoredSSID.bssids().get(bssid);
            if (monitoredBSSID == null) {
                continue;
            }

            for (Dot11AdvertisedNetworkReport hiddenNetwork : hiddenNetworks) {
                try {
                    checkFingerprints(nzyme, monitoredSSID, monitoredBSSID, bssid, hiddenNetwork.fingerprints(), tap);
                    checkChannels(nzyme, monitoredSSID, declaredFrequencies(
                            hiddenNetwork.declaredFrequencies(), MAX_DECLARED_FREQUENCIES_OF_SSID
                    ).keySet(), tap);
                    checkSecuritySuites(nzyme, monitoredSSID, hiddenNetwork.security(), tap);
                } catch (Exception e) {
                    LOG.error("Could not check frames without SSID of monitored BSSID [{}].", bssid, e);
                }
            }
        }
    }

    private void checkFingerprints(NzymeNode nzyme,
                                   PreLoadedMonitoredSSID monitoredSSID,
                                   PreLoadedMonitoredBSSID monitoredBSSID,
                                   String bssid,
                                   List<String> fingerprints,
                                   Tap tap) {
        if (!monitoredSSID.enabledUnexpectedFingerprint()) {
            return;
        }

        for (String observedFingerprint : fingerprints) {
            if (!monitoredBSSID.fingerprints().contains(observedFingerprint)) {
                Map<String, String> attributes = Maps.newHashMap();
                attributes.put("bssid", bssid);
                attributes.put("fingerprint", observedFingerprint);

                nzyme.getDetectionAlertService().raiseAlert(
                        tap.organizationId(),
                        tap.tenantId(),
                        monitoredSSID.uuid(),
                        AlertOrigin.tap(tap.uuid()),
                        DetectionType.DOT11_MONITOR_FINGERPRINT,
                        Subsystem.DOT11,
                        "Monitored network \"" + monitoredSSID.ssid() + "\" advertised " +
                                "with unexpected fingerprint \"" + observedFingerprint + "\".",
                        attributes,
                        Set.of("bssid", "fingerprint")
                );
            }
        }
    }

    // Taps decode frames of neighboring channels, so only the frequencies a frame declares are judged.
    private void checkChannels(NzymeNode nzyme,
                               PreLoadedMonitoredSSID monitoredSSID,
                               Collection<Integer> declaredFrequencies,
                               Tap tap) {
        if (!monitoredSSID.enabledUnexpectedChannel()) {
            return;
        }

        for (Integer frequency : declaredFrequencies) {
            if (!monitoredSSID.channels().contains(frequency)) {
                Map<String, String> attributes = Maps.newHashMap();
                attributes.put("frequency", String.valueOf(frequency));

                nzyme.getDetectionAlertService().raiseAlert(
                        tap.organizationId(),
                        tap.tenantId(),
                        monitoredSSID.uuid(),
                        AlertOrigin.tap(tap.uuid()),
                        DetectionType.DOT11_MONITOR_CHANNEL,
                        Subsystem.DOT11,
                        "Monitored network \"" + monitoredSSID.ssid() + "\" advertised on " +
                                "unexpected frequency " + frequency + "MHz",
                        attributes,
                        Set.of("frequency")
                );
            }
        }
    }

    private void checkSecuritySuites(NzymeNode nzyme,
                                     PreLoadedMonitoredSSID monitoredSSID,
                                     List<Dot11SecurityInformationReport> securitySettings,
                                     Tap tap) {
        if (!monitoredSSID.enabledUnexpectedSecuritySuites()) {
            return;
        }

        for (Dot11SecurityInformationReport security : securitySettings) {
            String suite = Dot11.securitySuitesToIdentifier(security);
            if (!Dot11.isMonitoredSecuritySuite(monitoredSSID.securitySuites(), suite)) {
                Map<String, String> attributes = Maps.newHashMap();
                attributes.put("suite", suite);

                nzyme.getDetectionAlertService().raiseAlert(
                        tap.organizationId(),
                        tap.tenantId(),
                        monitoredSSID.uuid(),
                        AlertOrigin.tap(tap.uuid()),
                        DetectionType.DOT11_MONITOR_SECURITY_SUITE,
                        Subsystem.DOT11,
                        "Monitored network \"" + monitoredSSID.ssid() + "\" advertised with " +
                                "unexpected security suites \"" + suite + "\"",
                        attributes,
                        Set.of("suite")
                );
            }
        }
    }

    private static final int MAX_DISCO_FRAMES_PER_REPORT = 2500;
    private static final int MAX_CONNECTION_FRAMES_PER_REPORT = 5000;
    private static final long MAX_FRAME_AGE_MS = 120000;

    private void writeDisco(Tap tap, DateTime timestamp, Dot11DiscoReport disco) {
        List<Dot11DiscoFrameReport> frames = disco.frames();
        if (frames.isEmpty()) {
            return;
        }

        if (frames.size() > MAX_DISCO_FRAMES_PER_REPORT) {
            LOG.warn("Tap [{}] reported <{}> disconnection frame records. Recording the first <{}>.",
                    tap.uuid(), frames.size(), MAX_DISCO_FRAMES_PER_REPORT);
            frames = frames.subList(0, MAX_DISCO_FRAMES_PER_REPORT);
        }

        List<Dot11DiscoFrameReport> valid = frames.stream().filter(Dot11Table::isValidDiscoFrame).toList();
        if (valid.size() < frames.size()) {
            LOG.warn("Tap [{}] reported <{}> invalid disconnection frame records. Skipping them.",
                    tap.uuid(), frames.size() - valid.size());
        }

        if (valid.isEmpty()) {
            return;
        }

        tablesService.getNzyme().getDatabase().useHandle(handle -> {
            PreparedBatch batch = handle.prepareBatch(
                    "INSERT INTO dot11_disco_frames(tap_uuid, disco_type, transmitter, receiver, bssid, " +
                            "reason_code, is_protected, frequency, signal_strength_min, signal_strength_max, " +
                            "signal_strength_average, frame_count, retry_frames, pair_sequence_numbers, " +
                            "integrity_frames, order_frames, signal_histogram, first_seen, last_seen, created_at) " +
                            "VALUES(:tap_uuid, :disco_type, " +
                            ":transmitter, :receiver, :bssid, :reason_code, :is_protected, :frequency, " +
                            ":signal_strength_min, :signal_strength_max, :signal_strength_average, :frame_count, " +
                            ":retry_frames, :pair_sequence_numbers, :integrity_frames, :order_frames, " +
                            ":signal_histogram::jsonb, :first_seen, :last_seen, :created_at)"
            );

            for (Dot11DiscoFrameReport frame : valid) {
                Dot11SignalStrengthReport signal = frame.signalStrength();

                batch.bind("tap_uuid", tap.uuid())
                        .bind("disco_type", frame.discoType())
                        .bind("transmitter", frame.transmitter().toUpperCase())
                        .bind("receiver", frame.receiver().toUpperCase())
                        .bind("bssid", frame.bssid().toUpperCase())
                        .bind("reason_code", frame.isProtected() ? null : frame.reasonCode())
                        .bind("is_protected", frame.isProtected())
                        .bind("frequency", frame.frequency())
                        .bind("signal_strength_min", signal == null ? null : signal.min())
                        .bind("signal_strength_max", signal == null ? null : signal.max())
                        .bind("signal_strength_average", signal == null ? null : signal.average())
                        .bind("frame_count", frame.frameCount())
                        .bind("retry_frames", frame.retryFrames())
                        .bind("pair_sequence_numbers", frame.pairSequenceNumbers())
                        .bind("integrity_frames", frame.integrityFrames())
                        .bind("order_frames", frame.orderFrames())
                        .bind("signal_histogram", signalHistogramJson(frame.signalHistogram()))
                        .bind("first_seen", timestamp.minus(Math.min(frame.oldestFrameAgeMs(), MAX_FRAME_AGE_MS)))
                        .bind("last_seen", timestamp.minus(Math.min(frame.newestFrameAgeMs(), MAX_FRAME_AGE_MS)))
                        .bind("created_at", timestamp)
                        .add();
            }

            batch.execute();
        });
    }

    private void writeConnectionFrames(Tap tap, DateTime timestamp, List<Dot11ConnectionFrameReport> frames) {
        if (frames.isEmpty()) {
            return;
        }

        if (frames.size() > MAX_CONNECTION_FRAMES_PER_REPORT) {
            LOG.warn("Tap [{}] reported <{}> connection frame records. Recording the first <{}>.",
                    tap.uuid(), frames.size(), MAX_CONNECTION_FRAMES_PER_REPORT);
            frames = frames.subList(0, MAX_CONNECTION_FRAMES_PER_REPORT);
        }

        List<Dot11ConnectionFrameReport> valid = frames.stream().filter(Dot11Table::isValidConnectionFrame).toList();
        if (valid.size() < frames.size()) {
            LOG.warn("Tap [{}] reported <{}> invalid connection frame records. Skipping them.",
                    tap.uuid(), frames.size() - valid.size());
        }

        if (valid.isEmpty()) {
            return;
        }

        tablesService.getNzyme().getDatabase().useHandle(handle -> {
            PreparedBatch batch = handle.prepareBatch(
                    "INSERT INTO dot11_connection_frames(tap_uuid, frame_type, transmitter, receiver, bssid, " +
                            "algorithm, transaction, status_code, frequency, signal_strength_min, " +
                            "signal_strength_max, signal_strength_average, frame_count, retry_frames, first_seen, " +
                            "last_seen, created_at) VALUES(:tap_uuid, :frame_type, :transmitter, :receiver, :bssid, " +
                            ":algorithm, :transaction, :status_code, :frequency, :signal_strength_min, " +
                            ":signal_strength_max, :signal_strength_average, :frame_count, :retry_frames, " +
                            ":first_seen, :last_seen, :created_at)"
            );

            for (Dot11ConnectionFrameReport frame : valid) {
                Dot11SignalStrengthReport signal = frame.signalStrength();

                batch.bind("tap_uuid", tap.uuid())
                        .bind("frame_type", frame.frameType())
                        .bind("transmitter", frame.transmitter().toUpperCase())
                        .bind("receiver", frame.receiver().toUpperCase())
                        .bind("bssid", frame.bssid().toUpperCase())
                        .bind("algorithm", frame.algorithm())
                        .bind("transaction", frame.transaction())
                        .bind("status_code", frame.statusCode())
                        .bind("frequency", frame.frequency())
                        .bind("signal_strength_min", signal == null ? null : signal.min())
                        .bind("signal_strength_max", signal == null ? null : signal.max())
                        .bind("signal_strength_average", signal == null ? null : signal.average())
                        .bind("frame_count", frame.frameCount())
                        .bind("retry_frames", frame.retryFrames())
                        .bind("first_seen", timestamp.minus(Math.min(frame.oldestFrameAgeMs(), MAX_FRAME_AGE_MS)))
                        .bind("last_seen", timestamp.minus(Math.min(frame.newestFrameAgeMs(), MAX_FRAME_AGE_MS)))
                        .bind("created_at", timestamp)
                        .add();
            }

            batch.execute();
        });
    }

    private static boolean isValidDiscoFrame(Dot11DiscoFrameReport frame) {
        return frame != null
                && isDiscoType(frame.discoType())
                && Tools.isValidMacAddress(frame.transmitter())
                && Tools.isValidMacAddress(frame.receiver())
                && Tools.isValidMacAddress(frame.bssid())
                && (frame.isProtected() || isWord(frame.reasonCode()))
                && (frame.frequency() == null || Dot11.frequencyToChannel(frame.frequency()) != -1)
                && frame.frameCount() > 0
                && isCount(frame.frameCount())
                && frame.retryFrames() >= 0 && frame.retryFrames() <= frame.frameCount()
                && frame.integrityFrames() >= 0 && frame.integrityFrames() <= frame.frameCount()
                && frame.orderFrames() >= 0 && frame.orderFrames() <= frame.frameCount()
                && isSignalHistogram(frame.signalHistogram(), frame.frameCount())
                && frame.pairSequenceNumbers() >= 0 && frame.pairSequenceNumbers() <= 4096
                && isFrameAge(frame.oldestFrameAgeMs(), frame.newestFrameAgeMs())
                && (frame.signalStrength() == null || isSignalStrength(frame.signalStrength()));
    }

    private static final int MAX_FRAME_OVERFLOWS_PER_REPORT = 1000;
    private static final Set<String> DISCO_FRAME_TYPES = Set.of("DEAUTHENTICATION", "DISASSOCIATION");

    private void writeFrameOverflows(Tap tap, DateTime timestamp, List<Dot11FrameOverflowReport> disco,
                                     List<Dot11FrameOverflowReport> connection) {
        List<Dot11FrameOverflowReport> valid = Lists.newArrayList();
        disco.stream().filter(o -> isValidFrameOverflow(o) && DISCO_FRAME_TYPES.contains(o.frameType()))
                .forEach(valid::add);
        connection.stream().filter(o -> isValidFrameOverflow(o) && ConnectionFrameType.isKnown(o.frameType()))
                .forEach(valid::add);

        if (valid.size() < disco.size() + connection.size()) {
            LOG.warn("Tap [{}] reported <{}> invalid frame overflow records. Skipping them.",
                    tap.uuid(), disco.size() + connection.size() - valid.size());
        }

        if (valid.isEmpty()) {
            return;
        }

        if (valid.size() > MAX_FRAME_OVERFLOWS_PER_REPORT) {
            LOG.warn("Tap [{}] reported <{}> frame overflow records. Recording the first <{}>.",
                    tap.uuid(), valid.size(), MAX_FRAME_OVERFLOWS_PER_REPORT);
            valid = valid.subList(0, MAX_FRAME_OVERFLOWS_PER_REPORT);
        }

        List<Dot11FrameOverflowReport> overflows = valid;
        tablesService.getNzyme().getDatabase().useHandle(handle -> {
            PreparedBatch batch = handle.prepareBatch(
                    "INSERT INTO dot11_frame_overflows(tap_uuid, frame_type, bssid, frame_count, first_seen, " +
                            "last_seen, created_at) VALUES(:tap_uuid, :frame_type, :bssid, :frame_count, " +
                            ":first_seen, :last_seen, :created_at)"
            );

            for (Dot11FrameOverflowReport overflow : overflows) {
                batch.bind("tap_uuid", tap.uuid())
                        .bind("frame_type", overflow.frameType())
                        .bind("bssid", overflow.bssid().toUpperCase())
                        .bind("frame_count", overflow.frameCount())
                        .bind("first_seen", timestamp.minus(Math.min(overflow.oldestFrameAgeMs(), MAX_FRAME_AGE_MS)))
                        .bind("last_seen", timestamp.minus(Math.min(overflow.newestFrameAgeMs(), MAX_FRAME_AGE_MS)))
                        .bind("created_at", timestamp)
                        .add();
            }

            batch.execute();
        });
    }

    private static boolean isValidFrameOverflow(Dot11FrameOverflowReport overflow) {
        return overflow != null
                && overflow.frameType() != null
                && Tools.isValidMacAddress(overflow.bssid())
                && overflow.frameCount() > 0
                && isCount(overflow.frameCount())
                && isFrameAge(overflow.oldestFrameAgeMs(), overflow.newestFrameAgeMs());
    }

    private static boolean isValidConnectionFrame(Dot11ConnectionFrameReport frame) {
        return frame != null
                && ConnectionFrameType.isKnown(frame.frameType())
                && Tools.isValidMacAddress(frame.transmitter())
                && Tools.isValidMacAddress(frame.receiver())
                && Tools.isValidMacAddress(frame.bssid())
                && (frame.algorithm() == null || isWord(frame.algorithm()))
                && (frame.transaction() == null || isWord(frame.transaction()))
                && (frame.statusCode() == null || isWord(frame.statusCode()))
                && (frame.frequency() == null || Dot11.frequencyToChannel(frame.frequency()) != -1)
                && frame.frameCount() > 0
                && isCount(frame.frameCount())
                && frame.retryFrames() >= 0 && frame.retryFrames() <= frame.frameCount()
                && isFrameAge(frame.oldestFrameAgeMs(), frame.newestFrameAgeMs())
                && (frame.signalStrength() == null || isSignalStrength(frame.signalStrength()));
    }

    // Not every frame comes with a signal strength, so the histogram can hold fewer frames than the record.
    private static boolean isSignalHistogram(@Nullable Map<Integer, Long> histogram, long frameCount) {
        if (histogram == null) {
            return true;
        }

        long frames = 0;
        for (Map.Entry<Integer, Long> entry : histogram.entrySet()) {
            if (entry.getKey() == null || entry.getKey() < -128 || entry.getKey() > 127
                    || entry.getValue() == null || entry.getValue() <= 0 || entry.getValue() > frameCount) {
                return false;
            }

            frames += entry.getValue();
        }

        return frames <= frameCount;
    }

    private static final int MAX_CLIENT_SIGNAL_FREQUENCIES = 32;

    @Nullable
    private String signalByFrequencyJson(@Nullable Map<Integer, Dot11FrequencySignalStrengthReport> byFrequency) {
        if (byFrequency == null) {
            return null;
        }

        Map<Integer, List<Number>> valid = new TreeMap<>();
        for (Map.Entry<Integer, Dot11FrequencySignalStrengthReport> entry : byFrequency.entrySet()) {
            Dot11FrequencySignalStrengthReport signal = entry.getValue();

            if (valid.size() >= MAX_CLIENT_SIGNAL_FREQUENCIES || entry.getKey() == null || signal == null
                    || Dot11.frequencyToChannel(entry.getKey()) == -1 || signal.frames() <= 0 || !isCount(signal.frames())
                    || signal.min() < -128 || signal.min() > signal.max() || signal.max() > 127
                    || !Float.isFinite(signal.average())
                    || signal.average() < signal.min() - 1 || signal.average() > signal.max() + 1) {
                continue;
            }

            valid.put(entry.getKey(), List.of(
                    Math.round(signal.average() * 10) / 10.0, signal.min(), signal.max(), signal.frames()
            ));
        }

        return valid.isEmpty() ? null : om.writeValueAsString(valid);
    }

    @Nullable
    private String signalHistogramJson(@Nullable Map<Integer, Long> histogram) {
        if (histogram == null || histogram.isEmpty()) {
            return null;
        }

        return om.writeValueAsString(new TreeMap<>(histogram));
    }

    private static boolean isDiscoType(String discoType) {
        try {
            Dot11.DiscoType.valueOf(discoType);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    private static boolean isWord(@Nullable Integer value) {
        return value != null && value >= 0 && value <= 65535;
    }

    private static boolean isFrameAge(long oldest, long newest) {
        return newest >= 0 && oldest >= newest;
    }

    private static final int MAX_CHANNEL_SWITCHES_PER_REPORT = 1000;

    private void writeChannelSwitches(Tap tap, DateTime timestamp, List<Dot11ChannelSwitchReport> channelSwitches) {
        if (channelSwitches.isEmpty()) {
            return;
        }

        if (channelSwitches.size() > MAX_CHANNEL_SWITCHES_PER_REPORT) {
            LOG.warn("Tap [{}] reported <{}> channel switch announcements. Recording the first <{}>.",
                    tap.uuid(), channelSwitches.size(), MAX_CHANNEL_SWITCHES_PER_REPORT);
            channelSwitches = channelSwitches.subList(0, MAX_CHANNEL_SWITCHES_PER_REPORT);
        }

        List<Dot11ChannelSwitchReport> valid = channelSwitches.stream()
                .filter(Dot11Table::isValidChannelSwitch)
                .toList();

        if (valid.size() < channelSwitches.size()) {
            LOG.warn("Tap [{}] reported <{}> invalid channel switch announcements. Skipping them.",
                    tap.uuid(), channelSwitches.size() - valid.size());
        }

        if (valid.isEmpty()) {
            return;
        }

        tablesService.getNzyme().getDatabase().useHandle(handle -> {
            PreparedBatch batch = handle.prepareBatch(
                    "INSERT INTO dot11_channel_switches(tap_uuid, bssid, frame_type, announcement_type, " +
                            "switch_mode, frequency, new_operating_class, new_channel, new_frequency, count_min, " +
                            "count_max, frame_count, peak_frames_per_second, timestamp_repetitions, " +
                            "timestamp_mismatch_frames, interleaved_beacons, trailing_element_frames, anomalies, " +
                            "signal_strength_min, signal_strength_max, signal_strength_average, " +
                            "signal_histograms, signal_judged_at, created_at) " +
                            "VALUES(:tap_uuid, :bssid, :frame_type, :announcement_type, :switch_mode, :frequency, " +
                            ":new_operating_class, :new_channel, :new_frequency, :count_min, :count_max, " +
                            ":frame_count, :peak_frames_per_second, :timestamp_repetitions, " +
                            ":timestamp_mismatch_frames, :interleaved_beacons, :trailing_element_frames, " +
                            ":anomalies, :signal_strength_min, :signal_strength_max, :signal_strength_average, " +
                            ":signal_histograms::jsonb, :signal_judged_at, :created_at)"
            );

            for (Dot11ChannelSwitchReport cs : valid) {
                Dot11SignalStrengthReport signal = cs.signalStrength();
                String signalHistograms = cs.signalHistogram().isEmpty() ? null : om.writeValueAsString(
                        cs.signalHistogram().entrySet().stream().collect(Collectors.toMap(
                                Map.Entry::getKey, e -> new TreeMap<>(e.getValue()), (a, b) -> a, TreeMap::new
                        ))
                );
                String[] anomalies = ChannelSwitchRules.anomalies(cs).stream()
                        .map(Enum::name)
                        .toArray(String[]::new);

                batch.bind("tap_uuid", tap.uuid())
                        .bind("bssid", cs.bssid().toUpperCase())
                        .bind("frame_type", cs.frameType())
                        .bind("announcement_type", cs.announcementType())
                        .bind("switch_mode", cs.switchMode())
                        .bind("frequency", cs.frequency())
                        .bind("new_operating_class", cs.newOperatingClass())
                        .bind("new_channel", cs.newChannel())
                        .bind("new_frequency", ChannelSwitchRules.newFrequency(cs))
                        .bind("count_min", cs.countMin())
                        .bind("count_max", cs.countMax())
                        .bind("frame_count", cs.frameCount())
                        .bind("peak_frames_per_second", cs.peakFramesPerSecond())
                        .bind("timestamp_repetitions", cs.timestampRepetitions())
                        .bind("timestamp_mismatch_frames", cs.timestampMismatchFrames())
                        .bind("interleaved_beacons", cs.interleavedBeacons())
                        .bind("trailing_element_frames", cs.trailingElementFrames())
                        .bindBySqlType("anomalies", anomalies, Types.ARRAY)
                        .bind("signal_strength_min", signal == null ? null : signal.min())
                        .bind("signal_strength_max", signal == null ? null : signal.max())
                        .bind("signal_strength_average", signal == null ? null : signal.average())
                        .bind("signal_histograms", signalHistograms)
                        // Without the signal strength of its frames, there is nothing left to judge later.
                        .bind("signal_judged_at", signalHistograms == null ? timestamp : null)
                        .bind("created_at", timestamp)
                        .add();
            }

            batch.execute();
        });
    }

    private static final int MAX_SIGNAL_FREQUENCIES = 8;

    private static boolean isSignalHistograms(Map<Integer, Map<Integer, Long>> histograms, long frameCount) {
        if (histograms.size() > MAX_SIGNAL_FREQUENCIES) {
            return false;
        }

        long frames = 0;
        for (Map.Entry<Integer, Map<Integer, Long>> histogram : histograms.entrySet()) {
            if (histogram.getKey() == null || Dot11.frequencyToChannel(histogram.getKey()) == -1
                    || histogram.getValue() == null || histogram.getValue().isEmpty()
                    || !isSignalHistogram(histogram.getValue(), frameCount)) {
                return false;
            }

            frames += histogram.getValue().values().stream().mapToLong(Long::longValue).sum();
        }

        return frames <= frameCount;
    }

    private static final int MAX_DECLARED_FREQUENCIES_OF_BSSID = 8;
    private static final int MAX_DECLARED_FREQUENCIES_OF_SSID = 16;
    private static final int MAX_FREQUENCIES_OF_HIDDEN_NETWORK = 16;

    // What a tap recorded of frames without a network name, as it is stored with the BSSID. All null without such frames.
    private record HiddenNetwork(@Nullable Long beaconAdvertisements,
                                 @Nullable Long probeResponseAdvertisements,
                                 @Nullable String channelStatistics,
                                 @Nullable String signalHistogram,
                                 @Nullable String declaredFrequencies) {

        private static HiddenNetwork of(List<Dot11AdvertisedNetworkReport> networks, ObjectMapper om) {
            if (networks.isEmpty()) {
                return new HiddenNetwork(null, null, null, null, null);
            }

            long beacons = 0;
            long probeResponses = 0;
            Map<Integer, long[]> statistics = Maps.newTreeMap();
            Map<Integer, Map<Integer, Long>> histogram = Maps.newTreeMap();
            Map<Integer, Long> declared = Maps.newHashMap();

            for (Dot11AdvertisedNetworkReport network : networks) {
                beacons += count(network.beaconAdvertisements());
                probeResponses += count(network.probeResponseAdvertisements());

                network.channelStatistics().forEach((frequency, types) -> {
                    if (frequency == null || types == null || Dot11.frequencyToChannel(frequency.intValue()) == -1) {
                        return;
                    }

                    long[] sum = statistics.computeIfAbsent(frequency.intValue(), f -> new long[2]);
                    types.values().stream()
                            .filter(stats -> stats != null && isCount(stats.frames()) && stats.bytes() >= 0)
                            .forEach(stats -> {
                                sum[0] += stats.frames();
                                sum[1] = stats.bytes() > Long.MAX_VALUE - sum[1] ? Long.MAX_VALUE : sum[1] + stats.bytes();
                            });
                });

                network.signalHistogram().forEach((frequency, signals) -> {
                    if (frequency == null || signals == null || Dot11.frequencyToChannel(frequency.intValue()) == -1) {
                        return;
                    }

                    Map<Integer, Long> sum = histogram.computeIfAbsent(frequency.intValue(), f -> Maps.newTreeMap());
                    signals.forEach((signal, frames) -> {
                        if (signal != null && frames != null && signal >= -128 && signal <= 127 && isCount(frames)) {
                            sum.merge(signal.intValue(), frames, Long::sum);
                        }
                    });
                });

                network.declaredFrequencies().forEach((frequency, frames) -> {
                    if (frequency != null && frames != null && isCount(frames)) {
                        declared.merge(frequency, frames, Long::sum);
                    }
                });
            }

            // The frequencies with the most frames are kept. The histogram only holds frequencies that have statistics.
            Map<Integer, Map<String, Long>> keptStatistics = Maps.newTreeMap();
            statistics.entrySet().stream()
                    .filter(e -> e.getValue()[0] > 0)
                    .sorted(Comparator.<Map.Entry<Integer, long[]>>comparingLong(e -> e.getValue()[0]).reversed()
                            .thenComparing(Map.Entry::getKey))
                    .limit(MAX_FREQUENCIES_OF_HIDDEN_NETWORK)
                    .forEach(e -> keptStatistics.put(e.getKey(), Map.of("frames", e.getValue()[0], "bytes", e.getValue()[1])));
            histogram.keySet().retainAll(keptStatistics.keySet());
            histogram.values().removeIf(Map::isEmpty);

            return new HiddenNetwork(
                    beacons,
                    probeResponses,
                    keptStatistics.isEmpty() ? null : om.writeValueAsString(keptStatistics),
                    histogram.isEmpty() ? null : om.writeValueAsString(histogram),
                    declaredFrequenciesJson(Dot11Table.declaredFrequencies(declared, MAX_DECLARED_FREQUENCIES_OF_SSID))
            );
        }

    }

    private static Map<Integer, Long> declaredFrequencies(Map<Integer, Long> reported, int limit) {
        Map<Integer, Long> declared = Maps.newLinkedHashMap();
        reported.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null
                        && Dot11.frequencyToChannel(e.getKey()) != -1 && e.getValue() > 0 && isCount(e.getValue()))
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .forEach(e -> declared.put(e.getKey(), e.getValue()));

        return declared;
    }

    @Nullable
    private static String declaredFrequenciesJson(Map<Integer, Long> declaredFrequencies) {
        if (declaredFrequencies.isEmpty()) {
            return null;
        }

        return declaredFrequencies.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static boolean isValidChannelSwitch(Dot11ChannelSwitchReport cs) {
        return cs != null
                && Tools.isValidMacAddress(cs.bssid())
                && ChannelSwitchRules.FRAME_TYPES.contains(cs.frameType())
                && ChannelSwitchRules.ANNOUNCEMENT_TYPES.contains(cs.announcementType())
                && isByte(cs.switchMode())
                && isByte(cs.newChannel())
                && isByte(cs.countMin())
                && isByte(cs.countMax())
                && cs.countMin() <= cs.countMax()
                && (cs.newOperatingClass() == null || isByte(cs.newOperatingClass()))
                && (cs.frequency() == null || Dot11.frequencyToChannel(cs.frequency()) != -1)
                && cs.frameCount() > 0
                && isCount(cs.frameCount())
                && isCount(cs.peakFramesPerSecond())
                && isCount(cs.timestampRepetitions())
                && isCount(cs.timestampMismatchFrames())
                && isCount(cs.interleavedBeacons())
                && isCount(cs.trailingElementFrames())
                && (cs.signalStrength() == null || isSignalStrength(cs.signalStrength()))
                && isSignalHistograms(cs.signalHistogram(), cs.frameCount());
    }

    private static boolean isByte(int value) {
        return value >= 0 && value <= 255;
    }

    private static boolean isCount(long value) {
        return value >= 0 && value <= Integer.MAX_VALUE;
    }

    private static long count(long value) {
        return isCount(value) ? value : 0;
    }

    private static boolean isSignalStrength(Dot11SignalStrengthReport signal) {
        return signal.min() >= -128 && signal.min() <= signal.max() && signal.max() <= 127
                && signal.average() >= signal.min() - 1 && signal.average() <= signal.max() + 1;
    }

    private void handleAlerts(Tap tap, List<Dot11AlertReport> alerts) {
        for (Dot11AlertReport alert : alerts) {
            switch (alert.alertType()) {
                case PwnagotchiDetected:
                    Map<String, String> attributes = reportAlertAttributesToAlertAttributes(alert.attributes());
                    attributes.put("bandit_name", Dot11Bandits.CUSTOM_PWNAGOTCHI_NAME);
                    attributes.put("bandit_description", Dot11Bandits.CUSTOM_PWNAGOTCHI_DESCRIPTION);

                    tablesService.getNzyme().getDetectionAlertService().raiseAlert(
                            tap.organizationId(),
                            tap.tenantId(),
                            null,
                            AlertOrigin.tap(tap.uuid()),
                            DetectionType.DOT11_BANDIT_CONTACT,
                            Subsystem.DOT11,
                            "Bandit \"Pwnagotchi\" with name \"" + attributes.get("name") + "\" detected in range.",
                            attributes,
                            Set.of("identity")
                    );

                    break;
                default:
                    LOG.warn("Unknown tap alert type: [{}]. Skipping.", alert.alertType());
            }
        }

    }

    /*
     * This is somewhat over-complicated to allow Rust to populate maps with different types of values.
     * We are just calling .toString(), but that may change if we ever report more complex attributes.
     */
    private Map<String, String> reportAlertAttributesToAlertAttributes(
            Map<String, Map<Dot11AlertReport.AlertAttributeType, Object>> attributes) {
        Map<String, String> alertAttributes = Maps.newHashMap();

        for (Map.Entry<String, Map<Dot11AlertReport.AlertAttributeType, Object>> attr : attributes.entrySet()) {
            String key = attr.getKey();
            String value = null;
            for (Map.Entry<Dot11AlertReport.AlertAttributeType, Object> attrValue : attr.getValue().entrySet()) {
                switch (attrValue.getKey()) {
                    case Number:
                    case String:
                        value = attrValue.getValue().toString();
                        break;
                }
            }

            alertAttributes.put(key, value);
        }

        return alertAttributes;
    }

    @Override
    public void retentionClean() {
        // NOOP. Remove from plugin APIs if there remains no use. Database cleaned by category/tenant independently.
    }

}
