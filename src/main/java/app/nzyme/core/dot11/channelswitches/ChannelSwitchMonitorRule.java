package app.nzyme.core.dot11.channelswitches;

import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum ChannelSwitchMonitorRule {

    INVALID_CHANNEL(ChannelSwitchAnomaly.INVALID_CHANNEL),
    REPEATED_TIMESTAMP(ChannelSwitchAnomaly.REPEATED_TIMESTAMP),
    TIMESTAMP_MISMATCH(ChannelSwitchAnomaly.TIMESTAMP_MISMATCH),
    INTERLEAVED_BEACONS(ChannelSwitchAnomaly.INTERLEAVED_BEACONS),
    SIGNAL_MISMATCH(ChannelSwitchAnomaly.SIGNAL_MISMATCH),
    BURST_RATE(null),
    NOT_FOLLOWED(null);

    @Nullable
    private final ChannelSwitchAnomaly anomaly;

    ChannelSwitchMonitorRule(@Nullable ChannelSwitchAnomaly anomaly) {
        this.anomaly = anomaly;
    }

    // Names that are no rule are skipped: a rule may be removed while networks still list it as disabled.
    public static Set<ChannelSwitchMonitorRule> parse(Collection<String> names) {
        Set<ChannelSwitchMonitorRule> rules = EnumSet.noneOf(ChannelSwitchMonitorRule.class);
        for (ChannelSwitchMonitorRule rule : values()) {
            if (names.contains(rule.name())) {
                rules.add(rule);
            }
        }

        return rules;
    }

    public static List<String> enabledAnomalies(Set<ChannelSwitchMonitorRule> disabled) {
        return Arrays.stream(values())
                .filter(rule -> rule.anomaly != null && !disabled.contains(rule))
                .map(rule -> rule.anomaly.name())
                .toList();
    }

}
