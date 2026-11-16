package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.periodicals.Periodical;
import app.nzyme.core.security.authentication.db.TenantEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SegmentationThread extends Periodical {

    private static final Logger LOG = LogManager.getLogger(SegmentationThread.class);

    private final NzymeNode nzyme;
    private final SegmentationEngine engine;

    public SegmentationThread(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.engine = new SegmentationEngine(nzyme);
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
    }

    @Override
    public String getName() {
        return "SegmentationThread";
    }

}
