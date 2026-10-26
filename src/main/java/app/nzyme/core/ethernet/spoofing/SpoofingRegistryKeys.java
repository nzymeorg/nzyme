package app.nzyme.core.ethernet.spoofing;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;

import java.util.ArrayList;
import java.util.Optional;

public class SpoofingRegistryKeys {

    // ARP poisoning.
    public static final RegistryKey ARP_DETECTION_ENABLED = booleanKey("ethernet_spoofing_arp_detection_enabled", true);
    public static final RegistryKey ARP_DETECTION_WINDOW_MINUTES =
            numberKey("ethernet_spoofing_arp_detection_window_minutes", 1, 1440, 10);
    public static final RegistryKey ARP_OWNER_LOOKBACK_HOURS =
            numberKey("ethernet_spoofing_arp_owner_lookback_hours", 1, 720, 24);
    public static final RegistryKey ARP_MINIMUM_CLAIMS =
            numberKey("ethernet_spoofing_arp_minimum_claims", 1, 1000000, 3);
    public static final RegistryKey ARP_RETENTION_TIME_DAYS =
            numberKey("ethernet_spoofing_arp_retention_time_days", 1, 3650, 30);

    // Alerting.
    public static final RegistryKey ARP_ALERT_ON_NEW = booleanKey("ethernet_spoofing_arp_alert_on_new", true);

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
