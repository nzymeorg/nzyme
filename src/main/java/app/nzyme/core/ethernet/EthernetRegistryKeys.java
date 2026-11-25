package app.nzyme.core.ethernet;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;

import java.util.ArrayList;
import java.util.Optional;

public class EthernetRegistryKeys {

    public static final RegistryKey L4_RETENTION_TIME_DAYS = RegistryKey.create(
            "ethernet_l4_retention_time_days",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createNumberRangeConstraint(1, Integer.MAX_VALUE));
            }}),
            Optional.of("7"),
            false
    );

    public static final RegistryKey DNS_RETENTION_TIME_DAYS = RegistryKey.create(
            "ethernet_dns_retention_time_days",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createNumberRangeConstraint(1, Integer.MAX_VALUE));
            }}),
            Optional.of("7"),
            false
    );

    public static final RegistryKey PORTAL_INTEGRITY_RETENTION_TIME_DAYS = RegistryKey.create(
            "ethernet_portal_integrity_retention_time_days",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createNumberRangeConstraint(1, Integer.MAX_VALUE));
            }}),
            Optional.of("360"),
            false
    );

    public static final RegistryKey TLS_CERTIFICATES_RETENTION_TIME_DAYS = RegistryKey.create(
            "ethernet_tls_certificates_retention_time_days",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createNumberRangeConstraint(1, Integer.MAX_VALUE));
            }}),
            Optional.of("180"),
            false
    );

    public static final RegistryKey CGNAT_IS_INTERNAL = RegistryKey.create(
            "ethernet_cgnat_is_internal",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createSimpleBooleanConstraint());
            }}),
            Optional.of("false"),
            false
    );

    public static final RegistryKey INTERNAL_NETWORKS = RegistryKey.create(
            "ethernet_internal_networks",
            Optional.empty(),
            Optional.of("[]"),
            false
    );

    public static final RegistryKey LEARN_INTERNAL_NETWORKS = RegistryKey.create(
            "ethernet_learn_internal_networks",
            Optional.of(new ArrayList<>() {{
                add(ConfigurationEntryConstraint.createSimpleBooleanConstraint());
            }}),
            Optional.of("true"),
            false
    );

    public static final RegistryKey LEARNED_INTERNAL_NETWORKS = RegistryKey.create(
            "ethernet_learned_internal_networks",
            Optional.empty(),
            Optional.of("[]"),
            false
    );

}
