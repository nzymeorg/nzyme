package app.nzyme.core.ethernet.segmentation;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;

import java.util.ArrayList;
import java.util.Optional;

public class SegmentationRegistryKeys {

    public static final RegistryKey ENABLED =
            booleanKey("ethernet_segmentation_enabled", true);

    // After this time a connection attempt has either been answered or it never will be: stacks give up on an
    // unanswered SYN after one to two minutes.
    public static final RegistryKey SETTLE_SECONDS =
            numberKey("ethernet_segmentation_settle_seconds", 60, 900, 120);

    public static final RegistryKey INITIAL_BACKFILL_HOURS =
            numberKey("ethernet_segmentation_initial_backfill_hours", 0, 168, 24);

    public static final RegistryKey FLOW_RETENTION_TIME_DAYS =
            numberKey("ethernet_segmentation_flow_retention_time_days", 1, 365, 30);

    public static final RegistryKey FINDING_RETENTION_TIME_DAYS =
            numberKey("ethernet_segmentation_finding_retention_time_days", 1, 3650, 365);

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

    private static RegistryKey numberKey(String key, int min, int max, int defaultValue) {
        return RegistryKey.create(
                key,
                Optional.of(new ArrayList<>() {{
                    add(ConfigurationEntryConstraint.createNumberRangeConstraint(min, max));
                }}),
                Optional.of(String.valueOf(defaultValue)),
                false
        );
    }

}
