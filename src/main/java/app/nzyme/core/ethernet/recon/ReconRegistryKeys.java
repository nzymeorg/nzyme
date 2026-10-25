package app.nzyme.core.ethernet.recon;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;

import java.util.ArrayList;
import java.util.Optional;

public class ReconRegistryKeys {

    public static final RegistryKey DETECTION_ENABLED = booleanKey("ethernet_recon_detection_enabled", true);

    // Data sources.
    public static final RegistryKey SOURCE_TCP_ENABLED = booleanKey("ethernet_recon_source_tcp_enabled", true);
    public static final RegistryKey SOURCE_UDP_ENABLED = booleanKey("ethernet_recon_source_udp_enabled", true);
    public static final RegistryKey SOURCE_ARP_ENABLED = booleanKey("ethernet_recon_source_arp_enabled", true);
    public static final RegistryKey SOURCE_ICMP_ENABLED = booleanKey("ethernet_recon_source_icmp_enabled", true);
    public static final RegistryKey FINGERPRINTING_ENABLED = booleanKey("ethernet_recon_fingerprinting_enabled", true);

    public static final RegistryKey INCLUDE_EXTERNAL_SOURCES =
            booleanKey("ethernet_recon_include_external_sources", false);
    public static final RegistryKey INCLUDE_EXTERNAL_TARGETS =
            booleanKey("ethernet_recon_include_external_targets", false);

    // Detection parameters.
    public static final RegistryKey DETECTION_WINDOW_MINUTES =
            numberKey("ethernet_recon_detection_window_minutes", 5, 1440, 30);
    public static final RegistryKey PORT_SCAN_MINIMUM_PORTS =
            numberKey("ethernet_recon_port_scan_minimum_ports", 2, 65535, 10);
    public static final RegistryKey HOST_SWEEP_MINIMUM_HOSTS =
            numberKey("ethernet_recon_host_sweep_minimum_hosts", 2, 1000000, 10);
    public static final RegistryKey ARP_SWEEP_MINIMUM_HOSTS =
            numberKey("ethernet_recon_arp_sweep_minimum_hosts", 2, 1000000, 25);
    public static final RegistryKey MINIMUM_FAILURE_PERCENT =
            numberKey("ethernet_recon_minimum_failure_percent", 0, 100, 50);

    public static final RegistryKey SCAN_RETENTION_TIME_DAYS =
            numberKey("ethernet_recon_scan_retention_time_days", 1, 3650, 30);

    // Alerting.
    public static final RegistryKey ALERT_ON_NEW_SCAN = booleanKey("ethernet_recon_alert_on_new_scan", true);
    public static final RegistryKey ALERT_ON_NEW_FINGERPRINTING = booleanKey("ethernet_recon_alert_on_new_fingerprinting", true);

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
