package app.nzyme.core.ethernet.vpn;

import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;

import java.util.ArrayList;
import java.util.Optional;

public class VpnRegistryKeys {

    public static final RegistryKey EXCLUDE_TAILSCALE_BY_DEFAULT =
            booleanKey("ethernet_vpn_exclude_tailscale_by_default", false);

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

}
