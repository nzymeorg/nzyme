package app.nzyme.core.ethernet.dns.tunnels;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;

import java.util.ArrayList;
import java.util.Optional;

public class DnsTunnelRegistryKeys {

    public static final RegistryKey DETECTION_ENABLED =
            booleanKey("ethernet_dns_tunnel_detection_enabled", true);
    public static final RegistryKey DETECTION_WINDOW_MINUTES =
            numberKey("ethernet_dns_tunnel_detection_window_minutes", 1, 1440, 10);
    public static final RegistryKey MINIMUM_DISTINCT_NAMES =
            numberKey("ethernet_dns_tunnel_minimum_distinct_names", 1, 100000, 50);
    // Tenths of a bit per character, because configuration values are integers. 35 is 3.5 bits per character.
    public static final RegistryKey ENTROPY_THRESHOLD_TENTHS =
            numberKey("ethernet_dns_tunnel_entropy_threshold_tenths", 1, 80, 35);
    public static final RegistryKey NAME_LENGTH_THRESHOLD =
            numberKey("ethernet_dns_tunnel_name_length_threshold", 1, 253, 30);
    public static final RegistryKey RECORD_TYPE_SHARE_THRESHOLD =
            numberKey("ethernet_dns_tunnel_record_type_share_threshold", 1, 100, 50);
    public static final RegistryKey RETENTION_TIME_DAYS =
            numberKey("ethernet_dns_tunnel_retention_time_days", 1, 3650, 365);
    public static final RegistryKey ALERT_ON_NEW =
            booleanKey("ethernet_dns_tunnel_alert_on_new", true);

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
