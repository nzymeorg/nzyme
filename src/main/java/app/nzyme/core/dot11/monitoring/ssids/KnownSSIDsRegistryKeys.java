package app.nzyme.core.dot11.monitoring.ssids;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

public class KnownSSIDsRegistryKeys {

    public static final RegistryKey IS_ENABLED = RegistryKey.create(
            "dot11_ssid_monitoring_enabled",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createSimpleBooleanConstraint());
            }}),
            Optional.of("false"),
            false
    );

    public static final RegistryKey EVENTING_IS_ENABLED = RegistryKey.create(
            "dot11_ssid_monitoring_eventing_enabled",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createSimpleBooleanConstraint());
            }}),
            Optional.of("false"),
            false
    );

    public static final RegistryKey HIDDEN_ACCESS_POINTS_ENABLED = RegistryKey.create(
            "dot11_ssid_monitoring_hidden_access_points_enabled",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createSimpleBooleanConstraint());
            }}),
            Optional.of("false"),
            false
    );

    private static final int DEFAULT_DWELL_TIME_MINUTES = 5;

    // The constraint also lets fractions pass. A stored value that is no whole number must not stop monitoring.
    public static int dwellTimeMinutes(Optional<String> stored) {
        try {
            return stored.map(value -> new BigDecimal(value.trim())
                            .max(BigDecimal.ZERO)
                            .min(BigDecimal.valueOf(Integer.MAX_VALUE))
                            .intValue())
                    .orElse(DEFAULT_DWELL_TIME_MINUTES);
        } catch (NumberFormatException e) {
            return DEFAULT_DWELL_TIME_MINUTES;
        }
    }

    public static final RegistryKey DWELL_TIME_MINUTES = RegistryKey.create(
            "dot11_ssid_monitoring_dwell_time_minutes",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createNumberRangeConstraint(0, Integer.MAX_VALUE));
            }}),
            Optional.of("5"),
            false
    );

}
