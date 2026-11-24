package app.nzyme.core.search;

import app.nzyme.plugin.Subsystem;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/*
 * Everything that decides what a search may return. Two users with an equal scope get the same results, which
 * is what the result cache is keyed by. Anything that changes what a source returns must be part of fingerprint().
 */
public record SearchScope(UUID organizationId,
                          UUID tenantId,
                          List<UUID> taps,
                          boolean superAdministrator,
                          boolean organizationAdministrator,
                          Set<String> permissions,
                          Set<Subsystem> subsystems,
                          boolean dashboardsEnabled) {

    public boolean isAdministrator() {
        return superAdministrator || organizationAdministrator;
    }

    public boolean hasPermission(String permission) {
        return isAdministrator() || permissions.contains(permission);
    }

    public boolean hasSubsystem(Subsystem subsystem) {
        return subsystems.contains(subsystem);
    }

    public boolean hasTaps() {
        return !taps.isEmpty();
    }

    public String fingerprint() {
        // Permissions are stored as they were sent and can hold the separators, so each goes in with its length.
        String permissionList = permissions.stream().sorted()
                .map(permission -> permission.length() + ":" + permission)
                .collect(Collectors.joining(","));

        return organizationId + "|" + tenantId + "|"
                + taps.stream().map(UUID::toString).sorted().collect(Collectors.joining(",")) + "|"
                + (superAdministrator ? "S" : organizationAdministrator ? "O" : "U") + "|"
                + permissionList + "|"
                + subsystems.stream().map(Enum::name).sorted().collect(Collectors.joining(",")) + "|"
                + dashboardsEnabled;
    }

}
