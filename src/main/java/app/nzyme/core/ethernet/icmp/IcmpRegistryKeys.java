package app.nzyme.core.ethernet.icmp;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;

import java.util.ArrayList;
import java.util.Optional;

public class IcmpRegistryKeys {

    public static final RegistryKey REDIRECT_DETECTION_ENABLED = booleanKey("ethernet_icmp_redirect_detection_enabled", true);
    public static final RegistryKey TRACEROUTE_DETECTION_ENABLED = booleanKey("ethernet_icmp_traceroute_detection_enabled", true);
    public static final RegistryKey TUNNEL_DETECTION_ENABLED = booleanKey("ethernet_icmp_tunnel_detection_enabled", true);

    public static final RegistryKey DETECTION_WINDOW_MINUTES =
            numberKey("ethernet_icmp_detection_window_minutes", 5, 1440, 30);
    public static final RegistryKey TRACEROUTE_MINIMUM_ROUTERS =
            numberKey("ethernet_icmp_traceroute_minimum_routers", 1, 255, 2);
    public static final RegistryKey TUNNEL_MINIMUM_PAYLOAD_BYTES =
            numberKey("ethernet_icmp_tunnel_minimum_payload_bytes", 16, 65535, 128);
    public static final RegistryKey TUNNEL_MINIMUM_PACKETS =
            numberKey("ethernet_icmp_tunnel_minimum_packets", 1, 1000000, 10);

    public static final RegistryKey DETECTION_RETENTION_TIME_DAYS =
            numberKey("ethernet_icmp_detection_retention_time_days", 1, 3650, 30);

    public static final RegistryKey ALERT_ON_REDIRECT = booleanKey("ethernet_icmp_alert_on_redirect", true);
    public static final RegistryKey ALERT_ON_TRACEROUTE = booleanKey("ethernet_icmp_alert_on_traceroute", false);
    public static final RegistryKey ALERT_ON_TUNNEL = booleanKey("ethernet_icmp_alert_on_tunnel", false);

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
