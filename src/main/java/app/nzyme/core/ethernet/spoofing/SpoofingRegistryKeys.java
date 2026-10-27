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
            numberKey("ethernet_spoofing_arp_retention_time_days", 1, 3650, 365);

    // DHCP spoofing.
    public static final RegistryKey DHCP_DETECTION_ENABLED = booleanKey("ethernet_spoofing_dhcp_detection_enabled", true);
    public static final RegistryKey DHCP_LEARNING_PERIOD_HOURS =
            numberKey("ethernet_spoofing_dhcp_learning_period_hours", 0, 720, 24);
    public static final RegistryKey DHCP_DETECTION_WINDOW_MINUTES =
            numberKey("ethernet_spoofing_dhcp_detection_window_minutes", 1, 1440, 60);
    public static final RegistryKey DHCP_RETENTION_TIME_DAYS =
            numberKey("ethernet_spoofing_dhcp_retention_time_days", 1, 3650, 365);

    // Name resolution poisoning.
    public static final RegistryKey NAMERES_DETECTION_ENABLED =
            booleanKey("ethernet_spoofing_nameres_detection_enabled", true);
    public static final RegistryKey NAMERES_DETECTION_WINDOW_MINUTES =
            numberKey("ethernet_spoofing_nameres_detection_window_minutes", 1, 1440, 15);
    public static final RegistryKey NAMERES_LLMNR_NAME_THRESHOLD =
            numberKey("ethernet_spoofing_nameres_llmnr_name_threshold", 1, 100000, 3);
    public static final RegistryKey NAMERES_NBTNS_NAME_THRESHOLD =
            numberKey("ethernet_spoofing_nameres_nbtns_name_threshold", 1, 100000, 3);
    public static final RegistryKey NAMERES_MDNS_NAME_THRESHOLD =
            numberKey("ethernet_spoofing_nameres_mdns_name_threshold", 1, 100000, 5);
    public static final RegistryKey NAMERES_RETENTION_TIME_DAYS =
            numberKey("ethernet_spoofing_nameres_retention_time_days", 1, 3650, 365);
    public static final RegistryKey NAMERES_RESPONSES_RETENTION_TIME_DAYS =
            numberKey("ethernet_spoofing_nameres_responses_retention_time_days", 1, 3650, 1);

    // Alerting.
    public static final RegistryKey ARP_ALERT_ON_NEW = booleanKey("ethernet_spoofing_arp_alert_on_new", true);
    public static final RegistryKey DHCP_ALERT_ON_ROGUE_SERVER =
            booleanKey("ethernet_spoofing_dhcp_alert_on_rogue_server", true);
    public static final RegistryKey DHCP_ALERT_ON_PARAMETERS_CHANGED =
            booleanKey("ethernet_spoofing_dhcp_alert_on_parameters_changed", true);
    public static final RegistryKey NAMERES_ALERT_ON_NEW =
            booleanKey("ethernet_spoofing_nameres_alert_on_new", true);

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
