package app.nzyme.core.dot11.monitoring;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.AlertOrigin;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.dot11.db.SignalTrackHistogramEntry;
import app.nzyme.core.dot11.db.monitoring.MonitoredBSSID;
import app.nzyme.core.dot11.db.monitoring.MonitoredChannel;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.dot11.tracks.Track;
import app.nzyme.core.dot11.tracks.TrackDetector;
import app.nzyme.core.dot11.tracks.db.TrackDetectorConfig;
import app.nzyme.core.periodicals.Periodical;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.TimeRangeFactory;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class Dot11SignalTrackMonitor extends Periodical {

    private static final Logger LOG = LogManager.getLogger(Dot11SignalTrackMonitor.class);

    private final NzymeNode nzyme;

    public Dot11SignalTrackMonitor(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    @Override
    protected void execute() {
        LOG.debug("Starting 802.11 signal track monitor run.");

        for (MonitoredSSID monitoredSSID : nzyme.getDot11().findAllMonitoredSSIDs(null, null)) {
            if (!monitoredSSID.isEnabled() || !monitoredSSID.enabledUnexpectedSignalTracks()) {
                continue;
            }

            List<Tap> accessibleTaps = nzyme.getTapManager()
                    .allTapsAccessibleByScope(monitoredSSID.organizationId(), monitoredSSID.tenantId());

            for (MonitoredBSSID monitoredBSSID : nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(monitoredSSID.id())) {
                for (MonitoredChannel frequency : nzyme.getDot11().findMonitoredChannelsOfMonitoredNetwork(monitoredSSID.id())) {
                    for (Tap tap : accessibleTaps) {
                        List<SignalTrackHistogramEntry> signals = nzyme.getDot11().getSSIDSignalStrengthWaterfall(
                                monitoredBSSID.bssid(), monitoredSSID.ssid(), (int) frequency.frequency(), TimeRangeFactory.eightHours(), tap.uuid());

                        TrackDetectorConfig config = nzyme.getDot11()
                                .findCustomTrackDetectorConfiguration(
                                        tap.organizationId(),
                                        tap.uuid(),
                                        monitoredBSSID.bssid(),
                                        monitoredSSID.ssid(),
                                        (int)frequency.frequency()
                                )
                                .orElse(TrackDetector.DEFAULT_CONFIG);

                        TrackDetector.TrackDetectorHeatmapData heatmap = TrackDetector.toChartAxisMaps(signals);
                        TrackDetector td = new TrackDetector();
                        List<Track> tracks = td.detect(heatmap.z(), heatmap.y(), config);

                        if (tracks.size() > 1) {
                            // Multiple tracks detected.
                            Map<String, String> attributes = Maps.newHashMap();
                            attributes.put("bssid", monitoredBSSID.bssid());
                            attributes.put("channel", String.valueOf(frequency.frequency()));
                            attributes.put("tap_id", tap.uuid().toString());
                            attributes.put("tap_name", tap.name());

                            nzyme.getDetectionAlertService().raiseAlert(
                                    monitoredSSID.organizationId(),
                                    monitoredSSID.tenantId(),
                                    monitoredSSID.uuid(),
                                    AlertOrigin.tap(tap.uuid()),
                                    DetectionType.DOT11_MONITOR_SIGNAL_TRACK,
                                    Subsystem.DOT11,
                                    "Monitored network \"" + monitoredSSID.ssid() + "\" advertised " +
                                            "with multiple signal tracks on channel \"" + frequency.frequency() + "\". " +
                                            "(Tap: \"" + tap.name() + "\")",
                                    attributes,
                                    Set.of("bssid", "channel", "tap_id")
                            );
                        }
                    }
                }
            }
        }
    }

    @Override
    public String getName() {
        return "802.11 Signal Track Monitor";
    }

}
