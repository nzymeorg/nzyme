package app.nzyme.core.quota;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.security.authentication.db.TenantEntry;
import com.google.common.collect.Maps;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class QuotaService {

    private final NzymeNode nzyme;

    public QuotaService(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public Optional<Integer> getOrganizationQuota(UUID organizationId, QuotaType quotaType) {
        return nzyme.getDatabaseCoreRegistry()
                .getValue(constructRegistryKey(quotaType), organizationId)
                .map(Integer::parseInt);
    }

    public Map<QuotaType, Optional<Integer>> getAllOrganizationQuotas(UUID organizationId) {
        Map<QuotaType, Optional<Integer>> quotas = Maps.newTreeMap();

        for (QuotaType quotaType : Arrays.stream(QuotaType.values()).sorted().toList()) {
            quotas.put(quotaType, getOrganizationQuota(organizationId, quotaType));
        }

        return quotas;
    }

    public Optional<Integer> getTenantQuota(UUID organizationId, UUID tenantId, QuotaType quotaType) {
        return nzyme.getDatabaseCoreRegistry()
                .getValue(constructRegistryKey(quotaType), organizationId, tenantId)
                .map(Integer::parseInt);
    }

    public Map<QuotaType, Optional<Integer>> getAllTenantQuotas(UUID organizationId, UUID tenantId) {
        Map<QuotaType, Optional<Integer>> quotas = Maps.newTreeMap();

        for (QuotaType quotaType : Arrays.stream(QuotaType.values()).sorted().toList()) {
            quotas.put(quotaType, getTenantQuota(organizationId, tenantId, quotaType));
        }

        return quotas;
    }

    public void setOrganizationQuota(UUID organizationId, QuotaType quotaType, int quota) {
        if (quota < 0) {
            throw new IllegalArgumentException("Quota must be 0 or larger.");
        }

        nzyme.getDatabaseCoreRegistry()
                .setValue(constructRegistryKey(quotaType), String.valueOf(quota), organizationId);
    }

    public void eraseOrganizationQuota(UUID organizationId, QuotaType quotaType) {
        nzyme.getDatabaseCoreRegistry()
                .deleteValue(constructRegistryKey(quotaType), organizationId);
    }

    public void setTenantQuota(UUID organizationId, UUID tenantId, QuotaType quotaType, int quota) {
        if (quota < 0) {
            throw new IllegalArgumentException("Quota must be 0 or larger.");
        }

        nzyme.getDatabaseCoreRegistry()
                .setValue(constructRegistryKey(quotaType), String.valueOf(quota), organizationId, tenantId);
    }

    public void eraseTenantQuota(UUID organizationId, UUID tenantId, QuotaType quotaType) {

        nzyme.getDatabaseCoreRegistry()
                .deleteValue(constructRegistryKey(quotaType), organizationId, tenantId);
    }


    public int calculateOrganizationQuotaUse(UUID organizationUuid, QuotaType type) {
        switch (type) {
            case TAPS:
                return nzyme.getTapManager()
                        .findAllTapsOfOrganization(organizationUuid).size();
            case TENANTS:
                return nzyme.getAuthenticationService()
                        .findAllTenantsOfOrganization(organizationUuid).size();
            case TENANT_USERS:
                int users = 0;
                for (TenantEntry tenant : nzyme.getAuthenticationService()
                        .findAllTenantsOfOrganization(organizationUuid)) {
                    users += nzyme.getAuthenticationService()
                            .findAllUsersOfTenant(organizationUuid, tenant.uuid(), Integer.MAX_VALUE, 0)
                            .size();
                }

                return users;
            case DASHBOARDS:
                return (int) nzyme.getDashboards().countDashboardsOfOrganization(organizationUuid);
            case DASHBOARD_DISPLAYS:
                return (int) nzyme.getDashboards().countDisplaysOfOrganization(organizationUuid);
        };

        return -1;
    }

    public int calculateTenantQuotaUse(UUID organizationUuid, UUID tenantUuid, QuotaType type) {
        if (type == QuotaType.TENANTS) {
            throw new IllegalArgumentException("Tenants have no tenant quota.");
        }

        switch (type) {
            case TAPS:
                return nzyme.getTapManager()
                        .findAllTapsOfTenant(organizationUuid, tenantUuid)
                        .size();
            case TENANT_USERS:
                return nzyme.getAuthenticationService()
                        .findAllUsersOfTenant(organizationUuid, tenantUuid, Integer.MAX_VALUE, 0)
                        .size();
            case DASHBOARDS:
                return (int) nzyme.getDashboards().countDashboards(organizationUuid, tenantUuid);
            case DASHBOARD_DISPLAYS:
                return (int) nzyme.getDashboards().countDisplays(organizationUuid, tenantUuid, null, null);
        };

        return -1;
    }

    public boolean isOrganizationQuotaAvailable(UUID organizationId, QuotaType quotaType) {
        Optional<Integer> organizationQuota = getOrganizationQuota(organizationId, quotaType);

        if (organizationQuota.isEmpty()) {
            // Unlimited.
            return true;
        }

        return calculateOrganizationQuotaUse(organizationId, quotaType) < organizationQuota.get();
    }

    public boolean isTenantQuotaAvailable(UUID organizationId, UUID tenantId, QuotaType quotaType) {
        Optional<Integer> tenantQuota = getTenantQuota(organizationId, tenantId, quotaType);

        if (tenantQuota.isEmpty()) {
            // No tenant quota configured. The underlying organization quota applies.
            return isOrganizationQuotaAvailable(organizationId, quotaType);
        }

        return calculateTenantQuotaUse(organizationId, tenantId, quotaType) < tenantQuota.get();
    }

    private String constructRegistryKey(QuotaType quotaType) {
        return "quota_" + quotaType.name().toLowerCase();
    }

}
