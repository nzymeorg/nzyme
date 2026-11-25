package app.nzyme.core.rest;

import app.nzyme.core.util.filters.FilterParser;
import app.nzyme.core.util.filters.FilterScope;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.security.authentication.TenantScopedEntity;
import app.nzyme.core.security.authentication.db.OrganizationEntry;
import app.nzyme.core.security.authentication.db.TenantEntry;
import app.nzyme.core.security.authentication.roles.Permission;
import com.google.common.base.Strings;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UserAuthenticatedResource extends RestResource {

    public static final String SELECTED_ORGANIZATION_HEADER = "X-Nzyme-Organization";
    public static final String SELECTED_TENANT_HEADER = "X-Nzyme-Tenant";

    protected record SelectedTenant(UUID organizationId, UUID tenantId) { }

    @Context
    private HttpHeaders httpHeaders;

    @Inject
    private NzymeNode nzyme;

    protected AuthenticatedUser getAuthenticatedUser(SecurityContext sc) {
        return (AuthenticatedUser) sc.getUserPrincipal();
    }

    protected Filters parseFilters(String filters, SecurityContext sc) {
        return FilterParser.parseFiltersQueryParameter(filters, FilterScope.of(getAuthenticatedUser(sc)));
    }

    /*
     * The tenant the web interface is looking at. Other clients do not send it. It is never a grant: use it
     * only to narrow down what the user could access anyway.
     */
    protected Optional<SelectedTenant> selectedTenant() {
        String organization = httpHeaders == null ? null : httpHeaders.getHeaderString(SELECTED_ORGANIZATION_HEADER);
        String tenant = httpHeaders == null ? null : httpHeaders.getHeaderString(SELECTED_TENANT_HEADER);
        if (Strings.isNullOrEmpty(organization) && Strings.isNullOrEmpty(tenant)) {
            return Optional.empty();
        }

        if (Strings.isNullOrEmpty(organization) || Strings.isNullOrEmpty(tenant)) {
            throw new IllegalArgumentException("Organization and tenant must be selected together.");
        }

        return Optional.of(new SelectedTenant(UUID.fromString(organization.trim()), UUID.fromString(tenant.trim())));
    }

    protected boolean passedTenantDataAccessible(SecurityContext sc,
                                                 @NotNull UUID organizationId,
                                                 @NotNull UUID tenantId) {
        if (organizationId == null || tenantId == null) {
            throw new IllegalArgumentException("Organization and Tenant ID must be set.");
        }

        AuthenticatedUser user = getAuthenticatedUser(sc);

        // Super admin?
        if (user.isSuperAdministrator()) {
            return true;
        }

        Optional<OrganizationEntry> dbOrg = nzyme.getAuthenticationService().findOrganization(organizationId);
        if (dbOrg.isEmpty()) {
            return false;
        }

        Optional<TenantEntry> dbTenant = nzyme.getAuthenticationService().findTenant(tenantId);
        if (dbTenant.isEmpty()) {
            return false;
        }

        // Is tenant part of organization?
        if (!dbTenant.get().organizationUuid().equals(organizationId)) {
            return false;
        }

        // Organization admin of the requested organization?
        if (user.isOrganizationAdministrator() && user.getOrganizationId().equals(organizationId)) {
            return true;
        }

        // User of tenant and organization that was requested?
        return user.getOrganizationId().equals(organizationId) && user.getTenantId().equals(tenantId);
    }

    protected boolean entityAccessible(AuthenticatedUser user, TenantScopedEntity entity) {
        if (user.isSuperAdministrator()) {
            return true;
        }

        if (user.isOrganizationAdministrator() && entity.organizationId().equals(user.getOrganizationId())) {
            return true;
        }

        if (entity.organizationId().equals(user.getOrganizationId()) && entity.tenantId().equals(user.getTenantId())) {
            return true;
        }

        return false;
    }

    protected boolean userHasPermission(AuthenticatedUser user, String permission) {
        if (user.isSuperAdministrator() || user.isOrganizationAdministrator()) {
            return true;
        }

        // Tenant user. Check if they have permission.
        List<String> userPermissions = nzyme.getAuthenticationService().findPermissionsOfUser(user.getUserId());
        return userPermissions.contains(permission);
    }

}
