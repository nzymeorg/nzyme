package app.nzyme.core.briefing;

import app.nzyme.core.NzymeNode;
import app.nzyme.plugin.Subsystem;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record BriefingContext(NzymeNode nzyme,
                              UUID organizationId,
                              UUID tenantId,
                              List<UUID> taps,
                              BriefingWindow window,
                              boolean canViewAlerts,
                              boolean canManageDot11Monitoring,
                              boolean canManageEthernetMonitoring,
                              boolean canManageBluetoothMonitoring,
                              boolean isOrganizationAdministrator,
                              boolean isSuperAdministrator,
                              Set<Subsystem> subsystems) {

    public boolean isOrganizationAdministratorOrAbove() {
        return isOrganizationAdministrator || isSuperAdministrator;
    }

    // Tap pages live under System, which only organization administrators and above can open.
    public boolean canOpenTaps() {
        return isOrganizationAdministratorOrAbove();
    }

    public boolean hasSubsystem(Subsystem subsystem) {
        return subsystems.contains(subsystem);
    }

}
