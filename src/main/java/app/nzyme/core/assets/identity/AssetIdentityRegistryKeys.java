package app.nzyme.core.assets.identity;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;

import java.util.ArrayList;
import java.util.Optional;

public class AssetIdentityRegistryKeys {

    public static final RegistryKey DUPLICATE_DETECTION_ENABLED =
            booleanKey("assets_identity_duplicate_detection_enabled", true);
    public static final RegistryKey DUPLICATE_WINDOW_MINUTES =
            numberKey("assets_identity_duplicate_window_minutes", 1, 1440, 15);
    public static final RegistryKey DUPLICATE_MINIMUM_SESSIONS =
            numberKey("assets_identity_duplicate_minimum_sessions", 1, 100000, 3);
    public static final RegistryKey DUPLICATE_REQUIRE_STRONG_DIFFERENCE =
            booleanKey("assets_identity_duplicate_require_strong_difference", true);
    public static final RegistryKey DUPLICATE_ALERT_ON_NEW =
            booleanKey("assets_identity_duplicate_alert_on_new", true);

    public static final RegistryKey CHANGE_DETECTION_ENABLED =
            booleanKey("assets_identity_change_detection_enabled", true);
    public static final RegistryKey CHANGE_WINDOW_HOURS =
            numberKey("assets_identity_change_window_hours", 1, 720, 24);
    public static final RegistryKey CHANGE_MINIMUM_ASSET_AGE_HOURS =
            numberKey("assets_identity_change_minimum_asset_age_hours", 0, 8760, 24);
    public static final RegistryKey CHANGE_SCORE_THRESHOLD =
            numberKey("assets_identity_change_score_threshold", 1, 500, 50);
    public static final RegistryKey CHANGE_ALERT_ON_NEW =
            booleanKey("assets_identity_change_alert_on_new", true);

    public static final RegistryKey SUCCESSOR_DETECTION_ENABLED =
            booleanKey("assets_identity_successor_detection_enabled", true);
    public static final RegistryKey SUCCESSOR_LOOKBACK_HOURS =
            numberKey("assets_identity_successor_lookback_hours", 1, 720, 72);
    public static final RegistryKey SUCCESSOR_QUIET_MINUTES =
            numberKey("assets_identity_successor_quiet_minutes", 1, 10080, 30);
    public static final RegistryKey SUCCESSOR_MINIMUM_CONFIDENCE =
            numberKey("assets_identity_successor_minimum_confidence", 1, 100, 50);
    public static final RegistryKey SUCCESSOR_ALERT_ON_NEW =
            booleanKey("assets_identity_successor_alert_on_new", false);

    public static final RegistryKey RETENTION_TIME_DAYS =
            numberKey("assets_identity_retention_time_days", 1, 3650, 365);

    public static final RegistryKey[] ALL = {
            DUPLICATE_DETECTION_ENABLED, DUPLICATE_WINDOW_MINUTES, DUPLICATE_MINIMUM_SESSIONS,
            DUPLICATE_REQUIRE_STRONG_DIFFERENCE, DUPLICATE_ALERT_ON_NEW,
            CHANGE_DETECTION_ENABLED, CHANGE_WINDOW_HOURS, CHANGE_MINIMUM_ASSET_AGE_HOURS, CHANGE_SCORE_THRESHOLD,
            CHANGE_ALERT_ON_NEW,
            SUCCESSOR_DETECTION_ENABLED, SUCCESSOR_LOOKBACK_HOURS, SUCCESSOR_QUIET_MINUTES,
            SUCCESSOR_MINIMUM_CONFIDENCE, SUCCESSOR_ALERT_ON_NEW,
            RETENTION_TIME_DAYS
    };

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
