package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.dhcp.DHCPFingerprintExclusionMatcher;
import app.nzyme.core.ethernet.dhcp.DHCPFingerprintExclusions;
import app.nzyme.core.ethernet.dhcp.db.DHCPFingerprintExclusionEntry;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.DHCPFingerprintExclusionRequest;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.dhcp.DHCPFingerprintExclusionImpactResponse;
import app.nzyme.core.rest.responses.ethernet.dhcp.DHCPFingerprintExclusionMatchedAssetResponse;
import app.nzyme.core.rest.responses.ethernet.dhcp.DHCPFingerprintExclusionResponse;
import app.nzyme.core.rest.responses.ethernet.dhcp.DHCPFingerprintExclusionsListResponse;
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

import static app.nzyme.core.rest.RestHelpers.macContextEntryToResponse;
import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/dhcp/fingerprints/exclusions")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "DHCP Fingerprint Exclusions", description = "Exclusions keep assets with known flaky DHCP "
        + "fingerprints from raising the \"new DHCP fingerprint\" alert. An exclusion is a MAC address or a "
        + "vendor prefix, optionally limited to certain DHCP transaction types. Nzyme keeps updating the "
        + "fingerprints of excluded assets, it only stops alerting on changes. Use them for devices that "
        + "legitimately alternate between DHCP clients, like dual-boot machines, or for device classes whose "
        + "firmware randomizes DHCP options.")
public class DHCPFingerprintExclusionsResource extends TapDataHandlingResource {

    private static final String MANAGE_PERMISSION = "ethernet_assets_manage";

    private static final int MAXIMUM_NAME_LENGTH = 128;
    private static final int MAXIMUM_DESCRIPTION_LENGTH = 10000;

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findDhcpFingerprintExclusions", summary = "List DHCP fingerprint exclusions of a tenant",
            description = "Returns the exclusions of the tenant, sorted by name by default. Each exclusion includes "
                    + "which known assets it currently covers. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Exclusions found.",
            content = @Content(schema = @Schema(implementation = DHCPFingerprintExclusionsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Fields: name, description, enabled, mac, match_count.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Sorting column: NAME, ENABLED, MAC, LAST_MATCHED_AT, MATCH_COUNT, CREATED_AT or UPDATED_AT. Defaults to NAME.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);

        DHCPFingerprintExclusions.OrderColumn orderColumn = DHCPFingerprintExclusions.OrderColumn.NAME;
        OrderDirection orderDirection = OrderDirection.ASC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = DHCPFingerprintExclusions.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        DHCPFingerprintExclusions exclusions = nzyme.getEthernet().dhcp().fingerprintExclusions();

        long total = exclusions.count(filters, organizationId, tenantId);
        List<DHCPFingerprintExclusionResponse> result = Lists.newArrayList();
        for (DHCPFingerprintExclusionEntry exclusion : exclusions.findAll(
                filters, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            result.add(toResponse(exclusion, organizationId, tenantId));
        }

        return Response.ok(DHCPFingerprintExclusionsListResponse.create(total, result)).build();
    }

    @GET
    @Path("/show/{exclusion_uuid}")
    @Operation(operationId = "findDhcpFingerprintExclusion", summary = "Get a DHCP fingerprint exclusion",
            description = "Returns one exclusion, including which known assets it currently covers.")
    @ApiResponse(responseCode = "200", description = "Exclusion found.",
            content = @Content(schema = @Schema(implementation = DHCPFingerprintExclusionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Exclusion, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Exclusion UUID.") @PathParam("exclusion_uuid") UUID exclusionUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<DHCPFingerprintExclusionEntry> exclusion = nzyme.getEthernet().dhcp().fingerprintExclusions()
                .find(exclusionUuid, organizationId, tenantId);
        if (exclusion.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(toResponse(exclusion.get(), organizationId, tenantId)).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/preview")
    @Operation(operationId = "previewDhcpFingerprintExclusion", summary = "Preview which known assets an exclusion would cover",
            description = "Validates the criteria and returns the known assets they match, without saving "
                    + "anything. Requires the ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Impact calculated.",
            content = @Content(schema = @Schema(implementation = DHCPFingerprintExclusionImpactResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid criteria. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response preview(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                            @RequestBody(description = "The criteria. Name and description are ignored.", required = true, content = @Content(mediaType = "application/json"))
                            DHCPFingerprintExclusionRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DHCPFingerprintExclusionMatcher matcher;
        try {
            matcher = criteria(req).normalized().matcher();
        } catch (IllegalArgumentException e) {
            return unprocessable(e.getMessage());
        }

        DHCPFingerprintExclusions.Impact impact = nzyme.getEthernet().dhcp().fingerprintExclusions()
                .calculateImpact(matcher, organizationId, tenantId);

        return Response.ok(toResponse(impact, organizationId, tenantId)).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "createDhcpFingerprintExclusion", summary = "Create a DHCP fingerprint exclusion",
            description = "Creates an exclusion. It takes effect with the next DHCP transaction the taps report. "
                    + "Requires the ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "201", description = "Exclusion created.",
            content = @Content(schema = @Schema(implementation = DHCPFingerprintExclusionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid name or criteria. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response create(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           @RequestBody(description = "The exclusion.", required = true, content = @Content(mediaType = "application/json"))
                           DHCPFingerprintExclusionRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String name;
        String description;
        DHCPFingerprintExclusions.Criteria criteria;
        try {
            name = validateName(req.name());
            description = validateDescription(req.description());
            criteria = criteria(req).normalized();
        } catch (IllegalArgumentException e) {
            return unprocessable(e.getMessage());
        }

        AuthenticatedUser user = getAuthenticatedUser(sc);

        DHCPFingerprintExclusionEntry created = nzyme.getEthernet().dhcp().fingerprintExclusions().create(
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

        return Response.status(Response.Status.CREATED)
                .entity(toResponse(created, organizationId, tenantId))
                .build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{exclusion_uuid}")
    @Operation(operationId = "updateDhcpFingerprintExclusion", summary = "Update a DHCP fingerprint exclusion",
            description = "Replaces name, description, enabled state and all criteria of an exclusion. Requires "
                    + "the ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Exclusion updated.",
            content = @Content(schema = @Schema(implementation = DHCPFingerprintExclusionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Exclusion, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid name or criteria. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response update(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Exclusion UUID.") @PathParam("exclusion_uuid") UUID exclusionUuid,
                           @RequestBody(description = "The new exclusion data.", required = true, content = @Content(mediaType = "application/json"))
                           DHCPFingerprintExclusionRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DHCPFingerprintExclusions exclusions = nzyme.getEthernet().dhcp().fingerprintExclusions();

        Optional<DHCPFingerprintExclusionEntry> existing = exclusions.find(exclusionUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String name;
        String description;
        DHCPFingerprintExclusions.Criteria criteria;
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
    @Operation(operationId = "setDhcpFingerprintExclusionEnabled", summary = "Enable or disable a DHCP fingerprint exclusion",
            description = "A disabled exclusion is kept but no longer applied. Requires the ethernet_assets_manage "
                    + "feature permission.")
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

        DHCPFingerprintExclusions exclusions = nzyme.getEthernet().dhcp().fingerprintExclusions();

        Optional<DHCPFingerprintExclusionEntry> existing = exclusions.find(exclusionUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        exclusions.setEnabled(existing.get().id(), enabled);

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{exclusion_uuid}")
    @Operation(operationId = "deleteDhcpFingerprintExclusion", summary = "Delete a DHCP fingerprint exclusion",
            description = "Deletes an exclusion. Fingerprint changes of the assets it covered raise alerts again. "
                    + "Requires the ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Exclusion deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Exclusion, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response delete(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Exclusion UUID.") @PathParam("exclusion_uuid") UUID exclusionUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DHCPFingerprintExclusions exclusions = nzyme.getEthernet().dhcp().fingerprintExclusions();

        Optional<DHCPFingerprintExclusionEntry> existing = exclusions.find(exclusionUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        exclusions.delete(existing.get().id());

        return Response.ok().build();
    }

    private static DHCPFingerprintExclusions.Criteria criteria(DHCPFingerprintExclusionRequest req) {
        return new DHCPFingerprintExclusions.Criteria(req.mac(), req.transactionTypes());
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

    private DHCPFingerprintExclusionResponse toResponse(DHCPFingerprintExclusionEntry exclusion,
                                                        UUID organizationId,
                                                        UUID tenantId) {
        DHCPFingerprintExclusionImpactResponse impact = null;
        boolean macIsPrefix = false;
        try {
            DHCPFingerprintExclusionMatcher matcher = DHCPFingerprintExclusionMatcher.create(exclusion);
            macIsPrefix = matcher.isPrefix();
            impact = toResponse(nzyme.getEthernet().dhcp().fingerprintExclusions()
                    .calculateImpact(matcher, organizationId, tenantId), organizationId, tenantId);
        } catch (IllegalArgumentException e) {
            // Invalid criteria in the database. The exclusion is shown, but without impact.
        }

        return DHCPFingerprintExclusionResponse.create(
                exclusion.uuid(),
                exclusion.name(),
                exclusion.description(),
                exclusion.enabled(),
                exclusion.mac(),
                macIsPrefix,
                exclusion.transactionTypes(),
                exclusion.matchCount(),
                exclusion.lastMatchedAt(),
                impact,
                exclusion.createdByName(),
                exclusion.createdByEmail(),
                exclusion.createdAt(),
                exclusion.updatedAt()
        );
    }

    private DHCPFingerprintExclusionImpactResponse toResponse(DHCPFingerprintExclusions.Impact impact,
                                                              UUID organizationId,
                                                              UUID tenantId) {
        List<DHCPFingerprintExclusionMatchedAssetResponse> assets = Lists.newArrayList();
        for (DHCPFingerprintExclusions.MatchedAsset a : impact.assets()) {
            assets.add(DHCPFingerprintExclusionMatchedAssetResponse.create(
                    a.uuid(), buildMacResponse(a.mac(), organizationId, tenantId)
            ));
        }

        return DHCPFingerprintExclusionImpactResponse.create(impact.matchingAssets(), assets);
    }

    private EthernetMacAddressResponse buildMacResponse(String mac, UUID organizationId, UUID tenantId) {
        Optional<MacAddressContextEntry> context = nzyme.getContextService()
                .findMacAddressContext(mac, organizationId, tenantId);
        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAssetByMac(mac, organizationId, tenantId);

        return EthernetMacAddressResponse.create(
                mac,
                nzyme.getOuiService().lookup(mac).orElse(null),
                asset.map(AssetEntry::uuid).orElse(null),
                asset.map(AssetEntry::isActive).orElse(null),
                macContextEntryToResponse(context)
        );
    }

}
