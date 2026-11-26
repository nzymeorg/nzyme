package app.nzyme.core.dot11.channelswitches;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ChannelSwitchAlertRegistryKeys {

    public static final RegistryKey MONITORED_NETWORKS_ONLY =
            booleanKey("dot11_channel_switch_alerts_monitored_networks_only", true);

    public static final RegistryKey OTHER_NETWORKS_BURST_THRESHOLD = RegistryKey.create(
            "dot11_channel_switch_alerts_other_networks_burst_threshold",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createNumberRangeConstraint(
                        ChannelSwitchRules.BURST_THRESHOLD_MINIMUM, ChannelSwitchRules.BURST_THRESHOLD_MAXIMUM
                ));
            }}),
            Optional.of(String.valueOf(ChannelSwitchRules.BURST_THRESHOLD_DEFAULT)),
            false
    );

    public static final int SIGNAL_MISMATCH_MINIMUM_DIFFERENCE_DEFAULT = 20;

    public static final RegistryKey SIGNAL_MISMATCH_MINIMUM_DIFFERENCE = RegistryKey.create(
            "dot11_channel_switch_signal_mismatch_minimum_difference",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createNumberRangeConstraint(5, 80));
            }}),
            Optional.of(String.valueOf(SIGNAL_MISMATCH_MINIMUM_DIFFERENCE_DEFAULT)),
            false
    );

    public static final Map<ChannelSwitchMonitorRule, RegistryKey> OTHER_NETWORKS_RULES =
            new EnumMap<>(ChannelSwitchMonitorRule.class);

    public static final RegistryKey[] ALL;

    static {
        List<RegistryKey> all = new ArrayList<>(List.of(
                MONITORED_NETWORKS_ONLY, SIGNAL_MISMATCH_MINIMUM_DIFFERENCE, OTHER_NETWORKS_BURST_THRESHOLD
        ));
        for (ChannelSwitchMonitorRule rule : ChannelSwitchMonitorRule.values()) {
            RegistryKey key = booleanKey(
                    "dot11_channel_switch_alerts_other_networks_" + rule.name().toLowerCase() + "_enabled", true
            );
            OTHER_NETWORKS_RULES.put(rule, key);
            all.add(key);
        }

        ALL = all.toArray(new RegistryKey[0]);
    }

    private static RegistryKey booleanKey(String key, boolean defaultValue) {
        return RegistryKey.create(
                key,
                Optional.of(new ArrayList<>() {{
                    add(ConfigurationEntryConstraint.createSimpleBooleanConstraint());
                }}),
                Optional.of(String.valueOf(defaultValue)),
                false
        );
    }

}
