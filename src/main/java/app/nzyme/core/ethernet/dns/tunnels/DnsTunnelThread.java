package app.nzyme.core.ethernet.dns.tunnels;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.periodicals.Periodical;
import app.nzyme.core.security.authentication.db.TenantEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DnsTunnelThread extends Periodical {

    private static final Logger LOG = LogManager.getLogger(DnsTunnelThread.class);

    private final NzymeNode nzyme;
    private final DnsTunnelDetector detector;

    public DnsTunnelThread(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.detector = new DnsTunnelDetector(nzyme);
    }

    @Override
    protected void execute() {
        for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfAllOrganizations()) {
            try {
                detector.runForTenant(tenant.organizationUuid(), tenant.uuid());
            } catch (Exception e) {
                LOG.error("Could not run DNS tunnel detection for tenant [{}/{}]. Skipping.",
                        tenant.organizationUuid(), tenant.uuid(), e);
            }
        }
    }

    @Override
    public String getName() {
        return "DnsTunnelThread";
    }

}
