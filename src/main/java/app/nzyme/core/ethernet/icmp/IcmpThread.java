package app.nzyme.core.ethernet.icmp;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.periodicals.Periodical;
import app.nzyme.core.security.authentication.db.TenantEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IcmpThread extends Periodical {

    private static final Logger LOG = LogManager.getLogger(IcmpThread.class);

    private final NzymeNode nzyme;
    private final IcmpDetector detector;

    public IcmpThread(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.detector = new IcmpDetector(nzyme);
    }

    @Override
    protected void execute() {
        for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfAllOrganizations()) {
            try {
                detector.runForTenant(tenant.organizationUuid(), tenant.uuid());
            } catch (Exception e) {
                LOG.error("Could not run ICMP detection for tenant [{}/{}]. Skipping.",
                        tenant.organizationUuid(), tenant.uuid(), e);
            }
        }
    }

    @Override
    public String getName() {
        return "IcmpThread";
    }

}
