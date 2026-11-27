package app.nzyme.core.timelines;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;

import java.util.ArrayList;
import java.util.Optional;

public class TimelinesRegistryKeys {

    public static final RegistryKey TIMELINES_DOT11_BSSIDS_LAST_EXECUTION = RegistryKey.create(
            "timelines_dot11_bssids_last_execution",
            Optional.empty(),
            Optional.empty(),
            false
    );

    public static final RegistryKey TIMELINES_DOT11_SSIDS_LAST_EXECUTION = RegistryKey.create(
            "timelines_dot11_ssids_last_execution",
            Optional.empty(),
            Optional.empty(),
            false
    );

    public static final RegistryKey DOT11_BSSID_TAPS_RECORDED_SINCE = RegistryKey.create(
            "timelines_dot11_bssid_taps_recorded_since",
            Optional.empty(),
            Optional.empty(),
            false
    );

    public static final RegistryKey DOT11_EVENTS_RETENTION_TIME_DAYS = RegistryKey.create(
            "timelines_dot11_events_retention_time_days",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createNumberRangeConstraint(1, Integer.MAX_VALUE));
            }}),
            Optional.of("365"),
            false
    );

}
