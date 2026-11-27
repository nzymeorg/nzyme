package app.nzyme.core.dot11.monitoring.ssids;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.periodicals.Periodical;
import app.nzyme.core.security.authentication.db.TenantEntry;
import app.nzyme.plugin.RegistryKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.UUID;

public class KnownSSIDMonitor extends Periodical {

    private static final Logger LOG = LogManager.getLogger(KnownSSIDMonitor.class);

    private static final int RETENTION_DAYS = 30;

    private final NzymeNode nzyme;
    private final KnownNetworkMonitor knownNetworks;
    private final HiddenAccessPointMonitor hiddenAccessPoints;

    public KnownSSIDMonitor(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.knownNetworks = new KnownNetworkMonitor(nzyme);
        this.hiddenAccessPoints = new HiddenAccessPointMonitor(nzyme);
    }

    @Override
    protected void execute() {
        for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfAllOrganizations()) {
            UUID organizationId = tenant.organizationUuid();
            UUID tenantId = tenant.uuid();

            boolean hiddenAccessPointsEnabled;
            int dwellTimeMinutes;
            boolean eventingEnabled;
            try {
                if (!isTrue(KnownSSIDsRegistryKeys.IS_ENABLED, organizationId, tenantId)) {
                    continue;
                }

                hiddenAccessPointsEnabled = isTrue(KnownSSIDsRegistryKeys.HIDDEN_ACCESS_POINTS_ENABLED, organizationId, tenantId);
                dwellTimeMinutes = KnownSSIDsRegistryKeys.dwellTimeMinutes(nzyme.getDatabaseCoreRegistry()
                        .getValue(KnownSSIDsRegistryKeys.DWELL_TIME_MINUTES.key(), organizationId, tenantId));
                eventingEnabled = isTrue(KnownSSIDsRegistryKeys.EVENTING_IS_ENABLED, organizationId, tenantId);
            } catch (Exception e) {
                LOG.error("Could not read SSID monitoring configuration of tenant [{}/{}]. Skipping.",
                        organizationId, tenantId, e);
                continue;
            }

            try {
                knownNetworks.runForTenant(organizationId, tenantId, dwellTimeMinutes, eventingEnabled);
            } catch (Exception e) {
                LOG.error("Could not run SSID monitoring for tenant [{}/{}]. Skipping.", organizationId, tenantId, e);
            }

            if (hiddenAccessPointsEnabled) {
                try {
                    hiddenAccessPoints.runForTenant(organizationId, tenantId, dwellTimeMinutes, eventingEnabled);
                } catch (Exception e) {
                    LOG.error("Could not run hidden access point monitoring for tenant [{}/{}]. Skipping.",
                            organizationId, tenantId, e);
                }
            }
        }

        try {
            nzyme.getDot11().retentionCleanKnownNetworks(DateTime.now().minusDays(RETENTION_DAYS));
            nzyme.getDot11().hiddenAccessPoints().retentionClean(DateTime.now().minusDays(RETENTION_DAYS));
        } catch (Exception e) {
            LOG.error("Could not retention clean known networks.", e);
        }
    }

    private boolean isTrue(RegistryKey key, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabaseCoreRegistry().getValue(key.key(), organizationId, tenantId)
                .map("true"::equals)
                .orElse(false);
    }

    @Override
    public String getName() {
        return "KnownSSIDMonitor";
    }

}
