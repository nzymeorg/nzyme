package app.nzyme.core.assets;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;

import java.util.ArrayList;
import java.util.Optional;

public class AssetRegistryKeys {

    public static final RegistryKey ASSETS_STATISTICS_RETENTION_TIME_DAYS = RegistryKey.create(
            "assets_statistics_retention_time_days",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createNumberRangeConstraint(0, Integer.MAX_VALUE));
            }}),
            Optional.of("365"),
            false
    );

    public static final RegistryKey ASSETS_ACTIVITY_RETENTION_TIME_DAYS = RegistryKey.create(
            "assets_activity_retention_time_days",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createNumberRangeConstraint(0, Integer.MAX_VALUE));
            }}),
            Optional.of("365"),
            false
    );

    public static final RegistryKey ASSETS_ATTRIBUTES_RETENTION_TIME_DAYS = RegistryKey.create(
            "assets_attributes_retention_time_days",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createNumberRangeConstraint(0, Integer.MAX_VALUE));
            }}),
            Optional.of("730"),
            false
    );

    public static final RegistryKey ASSETS_COMMUNICATION_RETENTION_TIME_DAYS = RegistryKey.create(
            "assets_communication_retention_time_days",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createNumberRangeConstraint(0, Integer.MAX_VALUE));
            }}),
            Optional.of("90"),
            false
    );

    public static final RegistryKey DHCP_FINGERPRINT_MONITORING_ENABLED = RegistryKey.create(
            "assets_dhcp_fingerprint_monitoring_enabled",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createSimpleBooleanConstraint());
            }}),
            Optional.of("true"),
            false
    );

}
