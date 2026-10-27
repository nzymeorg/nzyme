package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.dns.tunnels.DnsTunnelExclusionMatcher;
import app.nzyme.core.ethernet.dns.tunnels.DnsTunnelExclusions;
import app.nzyme.core.ethernet.dns.tunnels.db.DnsTunnelExclusionEntry;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.DnsTunnelExclusionRequest;
import app.nzyme.core.rest.responses.ethernet.dnstunnels.*;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/dns/tunnels/exclusions")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "DNS Tunnel Exclusions", description = "Exclusions keep known setups out of DNS tunnel detection. "
        + "An exclusion is a domain, a client address or network, or both. The domain matches a finding's "
        + "registered domain exactly or as a parent domain. Every criterion that is set must match. Use them for "
        + "antivirus and endpoint products that look up file hashes over DNS, DNS blocklists, update checks and "
        + "telemetry that encode data in names. An exclusion can also be applied to the existing findings, which "
        + "deletes them.")
public class DnsTunnelExclusionsResource extends TapDataHandlingResource {

    private static final String MANAGE_PERMISSION = "ethernet_monitoring_manage";

    private static final int MAXIMUM_NAME_LENGTH = 128;
    private static final int MAXIMUM_DESCRIPTION_LENGTH = 10000;

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findDnsTunnelExclusions", summary = "List DNS tunnel exclusions of a tenant",
            description = "Returns the exclusions of the tenant, sorted by name by default. Each exclusion includes "
                    + "which existing findings it currently applies to. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Exclusions found.",
            content = @Content(schema = @Schema(implementation = DnsTunnelExclusionsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Fields: name, description, enabled, domain, client_address_cidr, match_count.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Sorting column: NAME, ENABLED, LAST_MATCHED_AT, MATCH_COUNT, CREATED_AT or UPDATED_AT. Defaults to NAME.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);

        DnsTunnelExclusions.OrderColumn orderColumn = DnsTunnelExclusions.OrderColumn.NAME;
        OrderDirection orderDirection = OrderDirection.ASC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = DnsTunnelExclusions.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        DnsTunnelExclusions exclusions = nzyme.getEthernet().dnsTunnels().exclusions();

        long total = exclusions.count(filters, organizationId, tenantId);
        List<DnsTunnelExclusionResponse> result = Lists.newArrayList();
        for (DnsTunnelExclusionEntry exclusion : exclusions.findAll(
                filters, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            result.add(toResponse(exclusion, organizationId, tenantId));
        }

        return Response.ok(DnsTunnelExclusionsListResponse.create(total, result)).build();
    }

    @GET
    @Path("/show/{exclusion_uuid}")
    @Operation(operationId = "findDnsTunnelExclusion", summary = "Get a DNS tunnel exclusion",
            description = "Returns one exclusion, including which existing findings it currently applies to.")
    @ApiResponse(responseCode = "200", description = "Exclusion found.",
            content = @Content(schema = @Schema(implementation = DnsTunnelExclusionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Exclusion, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Exclusion UUID.") @PathParam("exclusion_uuid") UUID exclusionUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<DnsTunnelExclusionEntry> exclusion = nzyme.getEthernet().dnsTunnels().exclusions()
                .find(exclusionUuid, organizationId, tenantId);
        if (exclusion.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(toResponse(exclusion.get(), organizationId, tenantId)).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/preview")
    @Operation(operationId = "previewDnsTunnelExclusion", summary = "Preview which existing findings an exclusion would apply to",
            description = "Validates the criteria and returns the existing findings they match, without saving "
                    + "anything. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Impact calculated.",
            content = @Content(schema = @Schema(implementation = DnsTunnelExclusionImpactResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid criteria. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response preview(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                            @RequestBody(description = "The criteria. Name and description are ignored.", required = true, content = @Content(mediaType = "application/json"))
                            DnsTunnelExclusionRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DnsTunnelExclusionMatcher matcher;
        try {
            matcher = criteria(req).normalized().matcher();
        } catch (IllegalArgumentException e) {
            return unprocessable(e.getMessage());
        }

        DnsTunnelExclusions.Impact impact = nzyme.getEthernet().dnsTunnels().exclusions()
                .calculateImpact(matcher, organizationId, tenantId);

        return Response.ok(toResponse(impact)).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "createDnsTunnelExclusion", summary = "Create a DNS tunnel exclusion",
            description = "Creates an exclusion. It takes effect with the next detection run. Pass "
                    + "apply_to_existing_findings to also delete the existing findings it matches right away. Requires "
                    + "the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "201", description = "Exclusion created.",
            content = @Content(schema = @Schema(implementation = DnsTunnelExclusionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid name or criteria. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response create(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           @RequestBody(description = "The exclusion.", required = true, content = @Content(mediaType = "application/json"))
                           DnsTunnelExclusionRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String name;
        String description;
        DnsTunnelExclusions.Criteria criteria;
        try {
            name = validateName(req.name());
            description = validateDescription(req.description());
            criteria = criteria(req).normalized();
        } catch (IllegalArgumentException e) {
            return unprocessable(e.getMessage());
        }

        DnsTunnelExclusions exclusions = nzyme.getEthernet().dnsTunnels().exclusions();
        AuthenticatedUser user = getAuthenticatedUser(sc);

        DnsTunnelExclusionEntry created = exclusions.create(
                organizationId,
                tenantId,
                name,
                description,
                req.enabled() == null || req.enabled(),
                criteria,
                user.getUserId(),
                user.getName(),
                user.getEmail()
        );

        if (Boolean.TRUE.equals(req.applyToExistingFindings())) {
            exclusions.applyToExistingFindings(criteria.matcher(), organizationId, tenantId);
        }

        return Response.status(Response.Status.CREATED)
                .entity(toResponse(created, organizationId, tenantId))
                .build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{exclusion_uuid}")
    @Operation(operationId = "updateDnsTunnelExclusion", summary = "Update a DNS tunnel exclusion",
            description = "Replaces name, description, enabled state and all criteria of an exclusion. Changes take "
                    + "effect with the next detection run. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Exclusion updated.",
            content = @Content(schema = @Schema(implementation = DnsTunnelExclusionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Exclusion, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid name or criteria. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response update(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Exclusion UUID.") @PathParam("exclusion_uuid") UUID exclusionUuid,
                           @RequestBody(description = "The new exclusion data.", required = true, content = @Content(mediaType = "application/json"))
                           DnsTunnelExclusionRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DnsTunnelExclusions exclusions = nzyme.getEthernet().dnsTunnels().exclusions();

        Optional<DnsTunnelExclusionEntry> existing = exclusions.find(exclusionUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String name;
        String description;
        DnsTunnelExclusions.Criteria criteria;
        try {
            name = validateName(req.name());
            description = validateDescription(req.description());
            criteria = criteria(req).normalized();
        } catch (IllegalArgumentException e) {
            return unprocessable(e.getMessage());
        }

        exclusions.update(
                existing.get().id(),
                name,
                description,
                req.enabled() == null ? existing.get().enabled() : req.enabled(),
                criteria
        );

        return Response.ok(toResponse(exclusions.find(exclusionUuid, organizationId, tenantId).orElseThrow(),
                organizationId, tenantId)).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{exclusion_uuid}/enabled/{enabled}")
    @Operation(operationId = "setDnsTunnelExclusionEnabled", summary = "Enable or disable a DNS tunnel exclusion",
            description = "A disabled exclusion is kept but no longer applied. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Exclusion updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Exclusion, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response setEnabled(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                               @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                               @Parameter(description = "Exclusion UUID.") @PathParam("exclusion_uuid") UUID exclusionUuid,
                               @Parameter(description = "true or false.") @PathParam("enabled") boolean enabled) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DnsTunnelExclusions exclusions = nzyme.getEthernet().dnsTunnels().exclusions();

        Optional<DnsTunnelExclusionEntry> existing = exclusions.find(exclusionUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        exclusions.setEnabled(existing.get().id(), enabled);

        return Response.ok().build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{exclusion_uuid}/apply")
    @Operation(operationId = "applyDnsTunnelExclusion", summary = "Apply a DNS tunnel exclusion to the existing findings",
            description = "Deletes every existing finding that matches the exclusion. This cannot be undone, but a "
                    + "deleted finding is detected again if the exclusion is removed and the client keeps querying. "
                    + "Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Exclusion applied. The response describes what was deleted.",
            content = @Content(schema = @Schema(implementation = DnsTunnelExclusionImpactResponse.class)))
    @ApiResponse(responseCode = "404", description = "Exclusion, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The exclusion has invalid criteria.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response apply(@Parameter(hidden = true) @Context SecurityContext sc,
                          @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                          @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                          @Parameter(description = "Exclusion UUID.") @PathParam("exclusion_uuid") UUID exclusionUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DnsTunnelExclusions exclusions = nzyme.getEthernet().dnsTunnels().exclusions();

        Optional<DnsTunnelExclusionEntry> existing = exclusions.find(exclusionUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DnsTunnelExclusionMatcher matcher;
        try {
            matcher = DnsTunnelExclusionMatcher.create(existing.get());
        } catch (IllegalArgumentException e) {
            return unprocessable(e.getMessage());
        }

        return Response.ok(toResponse(exclusions.applyToExistingFindings(matcher, organizationId, tenantId))).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{exclusion_uuid}")
    @Operation(operationId = "deleteDnsTunnelExclusion", summary = "Delete a DNS tunnel exclusion",
            description = "Deletes an exclusion. Findings it matched are detected again with the next detection run. "
                    + "Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Exclusion deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Exclusion, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response delete(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Exclusion UUID.") @PathParam("exclusion_uuid") UUID exclusionUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DnsTunnelExclusions exclusions = nzyme.getEthernet().dnsTunnels().exclusions();

        Optional<DnsTunnelExclusionEntry> existing = exclusions.find(exclusionUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        exclusions.delete(existing.get().id());

        return Response.ok().build();
    }

    /*
     * Helpers.
     */

    private static DnsTunnelExclusions.Criteria criteria(DnsTunnelExclusionRequest req) {
        return new DnsTunnelExclusions.Criteria(req.domain(), req.clientAddressCidr());
    }

    private static String validateName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("The name cannot be empty.");
        }

        if (trimmed.length() > MAXIMUM_NAME_LENGTH) {
            throw new IllegalArgumentException("The name cannot be longer than " + MAXIMUM_NAME_LENGTH + " characters.");
        }

        return trimmed;
    }

    private static String validateDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return null;
        }

        if (description.length() > MAXIMUM_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("The description is too long.");
        }

        return description.trim();
    }

    private static Response unprocessable(String message) {
        return Response.status(422).entity(ErrorResponse.create(message)).build();
    }

    private DnsTunnelExclusionResponse toResponse(DnsTunnelExclusionEntry exclusion, UUID organizationId, UUID tenantId) {
        DnsTunnelExclusionImpactResponse impact = null;
        try {
            impact = toResponse(nzyme.getEthernet().dnsTunnels().exclusions()
                    .calculateImpact(DnsTunnelExclusionMatcher.create(exclusion), organizationId, tenantId));
        } catch (IllegalArgumentException e) {
            // Invalid criteria in the database. The exclusion is shown, but without impact.
        }

        return DnsTunnelExclusionResponse.create(
                exclusion.uuid(),
                exclusion.name(),
                exclusion.description(),
                exclusion.enabled(),
                exclusion.domain(),
                exclusion.clientAddressCidr(),
                exclusion.matchCount(),
                exclusion.lastMatchedAt(),
                impact,
                exclusion.createdByName(),
                exclusion.createdByEmail(),
                exclusion.createdAt(),
                exclusion.updatedAt()
        );
    }

    private static DnsTunnelExclusionImpactResponse toResponse(DnsTunnelExclusions.Impact impact) {
        List<DnsTunnelExclusionMatchedFindingResponse> findings = Lists.newArrayList();
        for (DnsTunnelExclusions.MatchedFinding f : impact.findings()) {
            findings.add(DnsTunnelExclusionMatchedFindingResponse.create(f.uuid(), f.clientAddress(), f.domain()));
        }

        return DnsTunnelExclusionImpactResponse.create(impact.matchingFindings(), findings);
    }

}
