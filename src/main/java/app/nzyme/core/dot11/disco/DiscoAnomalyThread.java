package app.nzyme.core.dot11.disco;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.periodicals.Periodical;
import app.nzyme.core.security.authentication.db.TenantEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DiscoAnomalyThread extends Periodical {

    private static final Logger LOG = LogManager.getLogger(DiscoAnomalyThread.class);

    private final NzymeNode nzyme;
    private final DiscoAnomalyDetector detector;

    public DiscoAnomalyThread(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.detector = new DiscoAnomalyDetector(nzyme);
    }

    @Override
    protected void execute() {
        for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfAllOrganizations()) {
            try {
                detector.runForTenant(tenant.organizationUuid(), tenant.uuid());
            } catch (Exception e) {
                LOG.error("Could not run disconnection anomaly detection for tenant [{}/{}]. Skipping.",
                        tenant.organizationUuid(), tenant.uuid(), e);
            }
        }
    }

    @Override
    public String getName() {
        return "802.11 Disco Anomaly Detector";
    }

}
