package app.nzyme.core.util.filters;

import app.nzyme.core.rest.authentication.AuthenticatedUser;
import jakarta.annotation.Nullable;

import java.util.UUID;

// Whose asset tags a filter may refer to. Without it, a user could filter their own data by the tag of another
// tenant.
public record FilterScope(String organizationId, String tenantId) {

    static final String ANY = "*";
    private static final String NO_ONE = "-";

    public static FilterScope all() {
        return new FilterScope(ANY, ANY);
    }

    public static FilterScope none() {
        return new FilterScope(NO_ONE, NO_ONE);
    }

    public static FilterScope organization(@Nullable UUID organizationId) {
        return organizationId == null ? none() : new FilterScope(organizationId.toString(), ANY);
    }

    public static FilterScope tenant(@Nullable UUID organizationId, @Nullable UUID tenantId) {
        return organizationId == null || tenantId == null
                ? none() : new FilterScope(organizationId.toString(), tenantId.toString());
    }

    public static FilterScope of(AuthenticatedUser user) {
        if (user.isSuperAdministrator()) {
            return all();
        }

        if (user.isOrganizationAdministrator()) {
            return organization(user.getOrganizationId());
        }

        return tenant(user.getOrganizationId(), user.getTenantId());
    }

}
