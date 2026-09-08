package app.nzyme.core.subsystems;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;
import com.google.common.collect.ImmutableList;

import java.util.Optional;

public class SubsystemRegistryKeys {

    public static final RegistryKey ETHERNET_ENABLED = RegistryKey.create(
            "subsystem_ethernet_enabled",
            Optional.of(new ImmutableList.Builder().add(ConfigurationEntryConstraint.createSimpleBooleanConstraint()).build()),
            Optional.of("true"),
            false
    );

    public static final RegistryKey DOT11_ENABLED = RegistryKey.create(
            "subsystem_dot11_enabled",
            Optional.of(new ImmutableList.Builder().add(ConfigurationEntryConstraint.createSimpleBooleanConstraint()).build()),
            Optional.of("true"),
            false
    );

    public static final RegistryKey BLUETOOTH_ENABLED = RegistryKey.create(
            "subsystem_bluetooth_enabled",
            Optional.of(new ImmutableList.Builder().add(ConfigurationEntryConstraint.createSimpleBooleanConstraint()).build()),
            Optional.of("true"),
            false
    );

    public static final RegistryKey UAV_ENABLED = RegistryKey.create(
            "subsystem_uav_enabled",
            Optional.of(new ImmutableList.Builder().add(ConfigurationEntryConstraint.createSimpleBooleanConstraint()).build()),
            Optional.of("false"),
            false
    );

}
