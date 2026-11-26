package app.nzyme.core.dot11.channelswitches;

import java.util.Arrays;
import java.util.List;

public enum ChannelSwitchAnomaly {

    INVALID_CHANNEL(true),
    REPEATED_TIMESTAMP(true),
    TIMESTAMP_MISMATCH(true),
    INTERLEAVED_BEACONS(true),
    SIGNAL_MISMATCH(true),
    STATIC_COUNT(false),
    TRAILING_ELEMENT(false);

    // Anomalies that do not alert are things a tool does and an unusual access point may do as well.
    private final boolean alerting;

    ChannelSwitchAnomaly(boolean alerting) {
        this.alerting = alerting;
    }

    public boolean isAlerting() {
        return alerting;
    }

    public static List<String> alertingNames() {
        return Arrays.stream(values()).filter(ChannelSwitchAnomaly::isAlerting).map(Enum::name).toList();
    }

}
