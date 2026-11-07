package app.nzyme.core.assets.identity;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.periodicals.Periodical;
import app.nzyme.core.security.authentication.db.TenantEntry;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

public class AssetIdentityThread extends Periodical {

    private static final Logger LOG = LogManager.getLogger(AssetIdentityThread.class);

    private final NzymeNode nzyme;
    private final DuplicateMacDetector duplicateDetector;
    private final IdentityChangeDetector changeDetector;
    private final AssetSuccessorDetector successorDetector;

    private final Timer duplicateTimer;
    private final Timer changeTimer;
    private final Timer successorTimer;

    public AssetIdentityThread(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.duplicateDetector = new DuplicateMacDetector(nzyme);
        this.changeDetector = new IdentityChangeDetector(nzyme);
        this.successorDetector = new AssetSuccessorDetector(nzyme);

        this.duplicateTimer = nzyme.getMetrics().timer(MetricNames.IDENTITY_DUPLICATE_MAC_DETECTION_RUN_TIMER);
        this.changeTimer = nzyme.getMetrics().timer(MetricNames.IDENTITY_CHANGE_DETECTION_RUN_TIMER);
        this.successorTimer = nzyme.getMetrics().timer(MetricNames.IDENTITY_SUCCESSOR_DETECTION_RUN_TIMER);
    }

    @Override
    protected void execute() {
        for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfAllOrganizations()) {
            UUID organizationId = tenant.organizationUuid();
            UUID tenantId = tenant.uuid();

            AssetIdentityConfiguration config;
            try {
                config = nzyme.getAssetsManager().identity().getConfiguration(organizationId, tenantId);
            } catch (Exception e) {
                LOG.error("Could not load asset identity configuration of tenant [{}/{}]. Skipping.",
                        organizationId, tenantId, e);
                continue;
            }

            try (Timer.Context ignored = duplicateTimer.time()) {
                duplicateDetector.runForTenant(organizationId, tenantId, config);
            } catch (Exception e) {
                LOG.error("Could not run duplicate MAC detection for tenant [{}/{}]. Skipping.",
                        organizationId, tenantId, e);
            }

            try (Timer.Context ignored = changeTimer.time()) {
                changeDetector.runForTenant(organizationId, tenantId, config);
            } catch (Exception e) {
                LOG.error("Could not run identity change detection for tenant [{}/{}]. Skipping.",
                        organizationId, tenantId, e);
            }

            try (Timer.Context ignored = successorTimer.time()) {
                successorDetector.runForTenant(organizationId, tenantId, config);
            } catch (Exception e) {
                LOG.error("Could not run MAC change detection for tenant [{}/{}]. Skipping.",
                        organizationId, tenantId, e);
            }
        }
    }

    @Override
    public String getName() {
        return "AssetIdentityThread";
    }

}
