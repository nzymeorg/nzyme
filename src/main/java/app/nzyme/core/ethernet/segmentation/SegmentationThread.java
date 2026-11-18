package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.periodicals.Periodical;
import app.nzyme.core.security.authentication.db.TenantEntry;
import app.nzyme.core.util.TenantCacheKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.List;

public class SegmentationThread extends Periodical {

    private static final Logger LOG = LogManager.getLogger(SegmentationThread.class);

    // One budget for all tenants: judging stored sessions delays the next evaluation of new ones by this at most.
    private static final int BACKFILL_BUDGET_SECONDS = 30;

    private final NzymeNode nzyme;
    private final SegmentationEngine engine;

    private int backfillTurn = 0;

    public SegmentationThread(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.engine = nzyme.getEthernet().segmentation().engine();
    }

    @Override
    protected void execute() {
        for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfAllOrganizations()) {
            try {
                engine.runForTenant(tenant.organizationUuid(), tenant.uuid());
            } catch (Exception e) {
                LOG.error("Could not run segmentation for tenant [{}/{}]. Skipping.",
                        tenant.organizationUuid(), tenant.uuid(), e);
            }
        }

        List<TenantCacheKey> pending = nzyme.getEthernet().segmentation().policies().findTenantsWithPendingBackfill();
        DateTime deadline = DateTime.now().plusSeconds(BACKFILL_BUDGET_SECONDS);

        // Another tenant goes first in every run, so that one with a lot to judge cannot keep the others waiting.
        backfillTurn++;
        for (int i = 0; i < pending.size() && DateTime.now().isBefore(deadline); i++) {
            TenantCacheKey tenant = pending.get(Math.floorMod(backfillTurn + i, pending.size()));

            try {
                engine.backfill(tenant.organization(), tenant.tenant(), deadline);
            } catch (Exception e) {
                LOG.error("Could not judge stored sessions of tenant [{}/{}]. Skipping.",
                        tenant.organization(), tenant.tenant(), e);
            }
        }
    }

    @Override
    public String getName() {
        return "SegmentationThread";
    }

}
