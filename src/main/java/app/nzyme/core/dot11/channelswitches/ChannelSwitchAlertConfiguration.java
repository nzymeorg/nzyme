package app.nzyme.core.dot11.channelswitches;

import app.nzyme.core.NzymeNode;
import app.nzyme.plugin.RegistryKey;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ChannelSwitchAlertConfiguration(boolean monitoredNetworksOnly,
                                              int signalMismatchMinimumDifference,
                                              int otherNetworksBurstThreshold,
                                              Set<ChannelSwitchMonitorRule> otherNetworksDisabledRules) {

    public static ChannelSwitchAlertConfiguration load(NzymeNode nzyme, UUID organizationId, UUID tenantId) {
        Set<ChannelSwitchMonitorRule> disabled = EnumSet.noneOf(ChannelSwitchMonitorRule.class);
        for (Map.Entry<ChannelSwitchMonitorRule, RegistryKey> rule
                : ChannelSwitchAlertRegistryKeys.OTHER_NETWORKS_RULES.entrySet()) {
            if (!Boolean.parseBoolean(value(nzyme, rule.getValue(), organizationId, tenantId))) {
                disabled.add(rule.getKey());
            }
        }

        return new ChannelSwitchAlertConfiguration(
                Boolean.parseBoolean(
                        value(nzyme, ChannelSwitchAlertRegistryKeys.MONITORED_NETWORKS_ONLY, organizationId, tenantId)
                ),
                number(
                        value(nzyme, ChannelSwitchAlertRegistryKeys.SIGNAL_MISMATCH_MINIMUM_DIFFERENCE, organizationId, tenantId),
                        5, 80, ChannelSwitchAlertRegistryKeys.SIGNAL_MISMATCH_MINIMUM_DIFFERENCE_DEFAULT
                ),
                number(
                        value(nzyme, ChannelSwitchAlertRegistryKeys.OTHER_NETWORKS_BURST_THRESHOLD, organizationId, tenantId),
                        ChannelSwitchRules.BURST_THRESHOLD_MINIMUM, ChannelSwitchRules.BURST_THRESHOLD_MAXIMUM,
                        ChannelSwitchRules.BURST_THRESHOLD_DEFAULT
                ),
                disabled
        );
    }

    private static String value(NzymeNode nzyme, RegistryKey key, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabaseCoreRegistry().getValue(key.key(), organizationId, tenantId)
                .orElse(key.defaultValue().orElseThrow());
    }

    private static int number(String value, int minimum, int maximum, int defaultValue) {
        try {
            int number = Integer.parseInt(value);
            if (number >= minimum && number <= maximum) {
                return number;
            }
        } catch (NumberFormatException ignored) { }

        return defaultValue;
    }

}
