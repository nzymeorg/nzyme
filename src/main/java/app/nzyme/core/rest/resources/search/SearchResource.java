package app.nzyme.core.rest.resources.search;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.responses.search.SearchGroupResponse;
import app.nzyme.core.rest.responses.search.SearchResponse;
import app.nzyme.core.search.SearchScope;
import app.nzyme.core.search.SearchService;
import app.nzyme.core.search.SearchTerm;
import app.nzyme.core.search.SearchWindow;
import app.nzyme.plugin.Subsystem;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Path("/api/search")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Search", description = "Search across everything the calling user can access: assets, access points, "
        + "clients, devices, alerts, findings, configuration and recorded network data. A query can be a MAC "
        + "address or the start of one, an IP address, a network in CIDR notation, a UUID, a fingerprint or any "
        + "text. Results come in groups, one per source. Configuration and findings are searched right away. "
        + "Sources of recorded network data are slower and are requested one by one.")
public class SearchResource extends UserAuthenticatedResource {

    private static final String QUERY_DESCRIPTION = "What to search for, between " + SearchTerm.MINIMUM_TEXT_LENGTH
            + " and " + SearchTerm.MAXIMUM_LENGTH + " characters.";
    private static final String TIME_RANGE_DESCRIPTION = "Time range of the recorded network data to search, in "
            + "the format of the web interface time range picker. Without it, a full MAC address is looked up in "
            + "all recorded WiFi and Bluetooth data, a session key or certificate fingerprint in all recorded "
            + "data, and everything else covers the last " + SearchWindow.DEFAULT_SCAN_HOURS + " hours. Each "
            + "group says what it covers. Configuration and findings are always searched in full.";

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "search", summary = "Search",
            description = "Searches configuration and findings and returns the groups that found something. "
                    + "Sources of recorded network data that apply to the query are listed as pending and are "
                    + "requested one by one. Searches the selected tenant with all of its taps the user can "
                    + "access. Sources the user has no permission or subsystem for are left out. Administrators "
                    + "also find taps, organizations, tenants and users they administer, beyond the selected "
                    + "tenant. Results are cached for a few seconds.")
    @ApiResponse(responseCode = "200", description = "Search performed.",
            content = @Content(schema = @Schema(implementation = SearchResponse.class)))
    @ApiResponse(responseCode = "400", description = "Query too short or too long, or invalid time range.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response search(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = QUERY_DESCRIPTION) @QueryParam("query") String query,
                           @Parameter(description = "Organization UUID.") @QueryParam("organization_id") @NotNull UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") @NotNull UUID tenantId,
                           @Parameter(description = "Maximum number of hits per group, up to " + SearchService.MAXIMUM_LIMIT + ".") @QueryParam("limit") @DefaultValue("5") int limit,
                           @Parameter(description = TIME_RANGE_DESCRIPTION) @QueryParam("time_range") @Nullable String timeRange) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<SearchTerm> term = SearchTerm.parse(query);
        if (term.isEmpty() || limit < 1 || limit > SearchService.MAXIMUM_LIMIT) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        return Response.ok(nzyme.getSearch().search(
                term.get(), scope(getAuthenticatedUser(sc), organizationId, tenantId), limit, window(timeRange)
        )).build();
    }

    @GET
    @Path("/sources/{source}")
    @Operation(operationId = "searchSource", summary = "Search one source",
            description = "Searches a single source, for recorded network data or for more hits of a group. A "
                    + "source of recorded network data can take seconds and is given up on after a time limit, "
                    + "which the status of the group says. Results are cached for up to two minutes.")
    @ApiResponse(responseCode = "200", description = "Source searched.",
            content = @Content(schema = @Schema(implementation = SearchGroupResponse.class)))
    @ApiResponse(responseCode = "400", description = "Query too short or too long, invalid paging or time range.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found or not accessible, or "
            + "the source does not exist, does not apply to the query or is not accessible by the calling user.", content = @Content)
    public Response source(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Source ID, as listed by a search.") @PathParam("source") String source,
                           @Parameter(description = QUERY_DESCRIPTION) @QueryParam("query") String query,
                           @Parameter(description = "Organization UUID.") @QueryParam("organization_id") @NotNull UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") @NotNull UUID tenantId,
                           @Parameter(description = "Maximum number of hits, up to " + SearchService.MAXIMUM_LIMIT + ".") @QueryParam("limit") @DefaultValue("5") int limit,
                           @Parameter(description = "Number of hits to skip, up to " + SearchService.MAXIMUM_OFFSET + ".") @QueryParam("offset") @DefaultValue("0") int offset,
                           @Parameter(description = TIME_RANGE_DESCRIPTION) @QueryParam("time_range") @Nullable String timeRange) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<SearchTerm> term = SearchTerm.parse(query);
        if (term.isEmpty() || limit < 1 || limit > SearchService.MAXIMUM_LIMIT
                || offset < 0 || offset > SearchService.MAXIMUM_OFFSET) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        AuthenticatedUser user = getAuthenticatedUser(sc);
        Optional<SearchGroupResponse> group = nzyme.getSearch().searchSource(
                source, term.get(), scope(user, organizationId, tenantId), limit, offset, window(timeRange),
                user.getUserId()
        );

        if (group.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(group.get()).build();
    }

    @Nullable
    private SearchWindow window(@Nullable String timeRange) {
        if (Strings.isNullOrEmpty(timeRange)) {
            return null;
        }

        return SearchWindow.of(timeRange, parseTimeRangeQueryParameter(timeRange));
    }

    // Built for every request, so a revoked permission or tap is gone with the next search.
    private SearchScope scope(AuthenticatedUser user, UUID organizationId, UUID tenantId) {
        Set<String> permissions = Sets.newHashSet();
        if (!user.isSuperAdministrator() && !user.isOrganizationAdministrator()) {
            permissions.addAll(nzyme.getAuthenticationService().findPermissionsOfUser(user.getUserId()));
        }

        // As the session of the web interface does it: these are the subsystems the user can open pages of.
        Set<Subsystem> subsystems = Sets.newHashSet();
        for (Subsystem subsystem : Set.of(Subsystem.ETHERNET, Subsystem.DOT11, Subsystem.BLUETOOTH, Subsystem.UAV)) {
            if (nzyme.getSubsystems().isEnabled(subsystem, user.getOrganizationId(), user.getTenantId())) {
                subsystems.add(subsystem);
            }
        }

        return new SearchScope(
                organizationId,
                tenantId,
                nzyme.getTapManager().allTapUUIDsAccessibleByUser(user, organizationId, tenantId),
                user.isSuperAdministrator(),
                user.isOrganizationAdministrator(),
                permissions,
                subsystems,
                nzyme.getDashboards().isEnabled(organizationId, tenantId)
        );
    }

}
