package app.nzyme.core.rest.authentication;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.rest.parameters.FiltersParameter;
import app.nzyme.core.util.filters.FilterParser;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jdbi.v3.core.statement.Query;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// The lookup of the MAC addresses that carry a tag knows nothing of the tenant of the list it filters. Filters that
// name a tag the user has no access to are refused here, before they reach a list.
@RESTSecured(PermissionLevel.ANY)
@Provider
@Priority(Priorities.AUTHORIZATION)
public class AssetTagFilterGuard implements ContainerRequestFilter {

    private final NzymeNode nzyme;

    public AssetTagFilterGuard(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!(requestContext.getSecurityContext().getUserPrincipal() instanceof AuthenticatedUser user)) {
            return;
        }

        String filters = requestContext.getUriInfo().getQueryParameters().getFirst("filters");
        if (!permitted(nzyme, user, filters)) {
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
        }
    }

    // Every UUID in the filters is checked, whatever its field and operator are: which fields filter by tag is only
    // known to the lists.
    public static boolean permitted(NzymeNode nzyme, AuthenticatedUser user, @Nullable String filters) {
        if (filters == null || filters.isBlank() || user.isSuperAdministrator()) {
            return true;
        }

        Map<String, List<FiltersParameter>> parsed;
        try {
            parsed = FilterParser.OM.readValue(filters, new TypeReference<>() {});
        } catch (JacksonException e) {
            // What cannot be parsed is refused by the resource.
            return true;
        }

        Set<UUID> uuids = Sets.newHashSet();
        for (List<FiltersParameter> parameters : parsed.values()) {
            if (parameters == null) {
                continue;
            }

            for (FiltersParameter parameter : parameters) {
                if (parameter == null || parameter.value() == null) {
                    continue;
                }

                try {
                    uuids.add(UUID.fromString(parameter.value().trim()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if (uuids.isEmpty()) {
            return true;
        }

        boolean wholeOrganization = user.isOrganizationAdministrator();

        return nzyme.getDatabase().withHandle(handle -> {
            Query query = handle.createQuery("SELECT COUNT(*) FROM assets_tags WHERE uuid IN (<uuids>) "
                            + "AND (organization_id <> :organization_id"
                            + (wholeOrganization ? ")" : " OR tenant_id <> :tenant_id)"))
                    .bindList("uuids", uuids)
                    .bind("organization_id", user.getOrganizationId());

            if (!wholeOrganization) {
                query.bind("tenant_id", user.getTenantId());
            }

            return query.mapTo(Long.class).one();
        }) == 0;
    }

}
