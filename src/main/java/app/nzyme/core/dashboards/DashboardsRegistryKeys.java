package app.nzyme.core.dashboards;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;
import com.google.common.collect.ImmutableList;

import java.util.Optional;

public class DashboardsRegistryKeys {

    public static final RegistryKey DASHBOARDS_ENABLED = RegistryKey.create(
            "dashboards_enabled",
            Optional.of(new ImmutableList.Builder().add(ConfigurationEntryConstraint.createSimpleBooleanConstraint()).build()),
            Optional.of("true"),
            false
    );

}
