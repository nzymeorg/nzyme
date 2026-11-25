package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.core.ethernet.beacons.BeaconExclusionMatcher;
import app.nzyme.core.ethernet.beacons.BeaconExclusions;
import app.nzyme.core.ethernet.beacons.db.BeaconExclusionEntry;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.BeaconExclusionRequest;
import app.nzyme.core.rest.responses.ethernet.beacons.*;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
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


@Path("/api/ethernet/beacons/exclusions")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Beacon Exclusions", description = "Exclusions keep traffic out of beacon detection. An exclusion "
        + "is a set of criteria for the destination (address or network, host name pattern, ports, beacon types) "
        + "and for the calling device (address or network, MAC address). Every criterion that is set must match. "
        + "Traffic matching an enabled exclusion never becomes a beacon, and existing beacons that match stop "
        + "receiving check-ins. An exclusion can also be applied to the existing beacons, which deletes them.")
public class BeaconExclusionsResource extends TapDataHandlingResource {

    private static final String MANAGE_PERMISSION = "ethernet_monitoring_manage";

    private static final int MAXIMUM_NAME_LENGTH = 128;
    private static final int MAXIMUM_DESCRIPTION_LENGTH = 10000;

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findBeaconExclusions", summary = "List beacon exclusions of a tenant",
            description = "Returns the exclusions of the tenant, sorted by name by default. Each exclusion includes "
                    + "which existing beacons it currently applies to. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Exclusions found.",
            content = @Content(schema = @Schema(implementation = BeaconExclusionsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Sorting column: NAME, ENABLED, LAST_MATCHED_AT, MATCH_COUNT, CREATED_AT or UPDATED_AT. Defaults to NAME.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFilters(filtersParameter, sc);

        BeaconExclusions.OrderColumn orderColumn = BeaconExclusions.OrderColumn.NAME;
        OrderDirection orderDirection = OrderDirection.ASC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = BeaconExclusions.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        BeaconExclusions exclusions = nzyme.getEthernet().beacons().exclusions();

        long total = exclusions.count(filters, organizationId, tenantId);
        List<BeaconExclusionResponse> result = Lists.newArrayList();
        for (BeaconExclusionEntry exclusion : exclusions.findAll(
                filters, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            result.add(toResponse(exclusion, organizationId, tenantId));
        }

        return Response.ok(BeaconExclusionsListResponse.create(total, result)).build();
    }

    @GET
    @Path("/show/{exclusion_uuid}")
    @Operation(operationId = "findBeaconExclusion", summary = "Get a beacon exclusion",
            description = "Returns one exclusion, including which existing beacons it currently applies to.")
    @ApiResponse(responseCode = "200", description = "Exclusion found.",
            content = @Content(schema = @Schema(implementation = BeaconExclusionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Exclusion, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Exclusion UUID.") @PathParam("exclusion_uuid") UUID exclusionUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<BeaconExclusionEntry> exclusion = nzyme.getEthernet().beacons().exclusions()
                .find(exclusionUuid, organizationId, tenantId);
        if (exclusion.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(toResponse(exclusion.get(), organizationId, tenantId)).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/preview")
    @Operation(operationId = "previewBeaconExclusion", summary = "Preview which existing beacons an exclusion would apply to",
            description = "Validates the criteria and returns the existing beacons and devices they match, without "
                    + "saving anything. Use this to show the impact of an exclusion before creating it. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Impact calculated.",
            content = @Content(schema = @Schema(implementation = BeaconExclusionImpactResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid criteria. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response preview(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                            @RequestBody(description = "The criteria. Name and description are ignored.", required = true, content = @Content(mediaType = "application/json"))
                            BeaconExclusionRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        BeaconExclusionMatcher matcher;
        try {
            matcher = criteria(req).normalized().matcher();
        } catch (IllegalArgumentException e) {
            return unprocessable(e.getMessage());
        }

        BeaconExclusions.Impact impact = nzyme.getEthernet().beacons().exclusions()
                .calculateImpact(matcher, organizationId, tenantId);

        return Response.ok(toResponse(impact)).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "createBeaconExclusion", summary = "Create a beacon exclusion",
            description = "Creates an exclusion. It takes effect with the next detection run. Pass "
                    + "apply_to_existing_beacons to also delete the existing beacons it matches right away. Requires "
                    + "the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "201", description = "Exclusion created.",
            content = @Content(schema = @Schema(implementation = BeaconExclusionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid name or criteria. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response create(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           @RequestBody(description = "The exclusion.", required = true, content = @Content(mediaType = "application/json"))
                           BeaconExclusionRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String name;
        String description;
        BeaconExclusions.Criteria criteria;
        try {
            name = validateName(req.name());
            description = validateDescription(req.description());
            criteria = criteria(req).normalized();
        } catch (IllegalArgumentException e) {
            return unprocessable(e.getMessage());
        }

        BeaconExclusions exclusions = nzyme.getEthernet().beacons().exclusions();
        AuthenticatedUser user = getAuthenticatedUser(sc);

        BeaconExclusionEntry created = exclusions.create(
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

        if (Boolean.TRUE.equals(req.applyToExistingBeacons())) {
            exclusions.applyToExistingBeacons(criteria.matcher(), organizationId, tenantId);
        }

        return Response.status(Response.Status.CREATED)
                .entity(toResponse(created, organizationId, tenantId))
                .build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{exclusion_uuid}")
    @Operation(operationId = "updateBeaconExclusion", summary = "Update a beacon exclusion",
            description = "Replaces name, description, enabled state and all criteria of an exclusion. Changes take "
                    + "effect with the next detection run. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Exclusion updated.",
            content = @Content(schema = @Schema(implementation = BeaconExclusionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Exclusion, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid name or criteria. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response update(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Exclusion UUID.") @PathParam("exclusion_uuid") UUID exclusionUuid,
                           @RequestBody(description = "The new exclusion data.", required = true, content = @Content(mediaType = "application/json"))
                           BeaconExclusionRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        BeaconExclusions exclusions = nzyme.getEthernet().beacons().exclusions();

        Optional<BeaconExclusionEntry> existing = exclusions.find(exclusionUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String name;
        String description;
        BeaconExclusions.Criteria criteria;
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
    @Operation(operationId = "setBeaconExclusionEnabled", summary = "Enable or disable a beacon exclusion",
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

        BeaconExclusions exclusions = nzyme.getEthernet().beacons().exclusions();

        Optional<BeaconExclusionEntry> existing = exclusions.find(exclusionUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        exclusions.setEnabled(existing.get().id(), enabled);

        return Response.ok().build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{exclusion_uuid}/apply")
    @Operation(operationId = "applyBeaconExclusion", summary = "Apply a beacon exclusion to the existing beacons",
            description = "Deletes every existing beacon whose calling devices all match the exclusion, and removes "
                    + "the matching devices from beacons where only some devices match. This cannot be undone, but "
                    + "a deleted beacon is detected again under a new name if the exclusion is removed and the "
                    + "devices keep calling out. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Exclusion applied. The response describes what was deleted.",
            content = @Content(schema = @Schema(implementation = BeaconExclusionImpactResponse.class)))
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

        BeaconExclusions exclusions = nzyme.getEthernet().beacons().exclusions();

        Optional<BeaconExclusionEntry> existing = exclusions.find(exclusionUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        BeaconExclusionMatcher matcher;
        try {
            matcher = BeaconExclusionMatcher.create(existing.get());
        } catch (IllegalArgumentException e) {
            return unprocessable(e.getMessage());
        }

        return Response.ok(toResponse(exclusions.applyToExistingBeacons(matcher, organizationId, tenantId))).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{exclusion_uuid}")
    @Operation(operationId = "deleteBeaconExclusion", summary = "Delete a beacon exclusion",
            description = "Deletes an exclusion. Traffic it matched is detected again with the next detection run. "
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

        BeaconExclusions exclusions = nzyme.getEthernet().beacons().exclusions();

        Optional<BeaconExclusionEntry> existing = exclusions.find(exclusionUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        exclusions.delete(existing.get().id());

        return Response.ok().build();
    }

    /*
     * Helpers.
     */

    private static BeaconExclusions.Criteria criteria(BeaconExclusionRequest req) {
        return new BeaconExclusions.Criteria(
                req.destinationCidr(),
                req.destinationHost(),
                req.destinationPorts(),
                req.beaconTypes(),
                req.sourceCidr(),
                req.sourceMac()
        );
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

    private BeaconExclusionResponse toResponse(BeaconExclusionEntry exclusion, UUID organizationId, UUID tenantId) {
        BeaconExclusionImpactResponse impact = null;
        try {
            impact = toResponse(nzyme.getEthernet().beacons().exclusions()
                    .calculateImpact(BeaconExclusionMatcher.create(exclusion), organizationId, tenantId));
        } catch (IllegalArgumentException e) {
            // Invalid criteria in the database. The exclusion is shown, but without impact.
        }

        return BeaconExclusionResponse.create(
                exclusion.uuid(),
                exclusion.name(),
                exclusion.description(),
                exclusion.enabled(),
                exclusion.destinationCidr(),
                exclusion.destinationHost(),
                exclusion.destinationPorts(),
                exclusion.beaconTypes(),
                exclusion.sourceCidr(),
                exclusion.sourceMac(),
                exclusion.matchCount(),
                exclusion.lastMatchedAt(),
                impact,
                exclusion.createdByName(),
                exclusion.createdByEmail(),
                exclusion.createdAt(),
                exclusion.updatedAt()
        );
    }

    private static BeaconExclusionImpactResponse toResponse(BeaconExclusions.Impact impact) {
        List<BeaconExclusionMatchedBeaconResponse> beacons = Lists.newArrayList();
        for (BeaconExclusions.MatchedBeacon b : impact.beacons()) {
            beacons.add(BeaconExclusionMatchedBeaconResponse.create(
                    b.uuid(), b.name(), b.type(), b.destination(), b.matchingSources(), b.totalSources(), b.complete()
            ));
        }

        return BeaconExclusionImpactResponse.create(
                impact.matchingBeacons(), impact.completeBeacons(), impact.matchingSources(), beacons
        );
    }

}
