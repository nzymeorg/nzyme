package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.periodicals.Periodical;
import app.nzyme.core.security.authentication.db.TenantEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SpoofingThread extends Periodical {

    private static final Logger LOG = LogManager.getLogger(SpoofingThread.class);

    private final NzymeNode nzyme;
    private final ArpSpoofingDetector arpDetector;
    private final DhcpSpoofingDetector dhcpDetector;

    public SpoofingThread(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.arpDetector = new ArpSpoofingDetector(nzyme);
        this.dhcpDetector = new DhcpSpoofingDetector(nzyme);
    }

    @Override
    protected void execute() {
        for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfAllOrganizations()) {
            try {
                arpDetector.runForTenant(tenant.organizationUuid(), tenant.uuid());
            } catch (Exception e) {
                LOG.error("Could not run ARP spoofing detection for tenant [{}/{}]. Skipping.",
                        tenant.organizationUuid(), tenant.uuid(), e);
            }

            try {
                dhcpDetector.runForTenant(tenant.organizationUuid(), tenant.uuid());
            } catch (Exception e) {
                LOG.error("Could not run DHCP spoofing detection for tenant [{}/{}]. Skipping.",
                        tenant.organizationUuid(), tenant.uuid(), e);
            }
        }
    }

    @Override
    public String getName() {
        return "SpoofingThread";
    }

}
