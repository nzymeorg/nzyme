package app.nzyme.core.rest.resources.dot11;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.hidden.HiddenAccessPoints;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.requests.UUIDListRequest;
import app.nzyme.core.rest.responses.dot11.Dot11MacAddressContextResponse;
import app.nzyme.core.rest.responses.dot11.Dot11MacAddressResponse;
import app.nzyme.core.rest.responses.dot11.monitoring.ssids.HiddenAccessPointDetailsResponse;
import app.nzyme.core.rest.responses.dot11.monitoring.ssids.HiddenAccessPointsListResponse;
import app.nzyme.core.rest.responses.dot11.monitoring.ssids.KnownNetworksBulkActionResponse;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Path("/api/dot11/monitoring/hiddenaccesspoints")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Monitoring")
public class Dot11HiddenAccessPointsResource extends UserAuthenticatedResource {

    private static final int MAX_PAGE_SIZE = 250;

    @Inject
    private NzymeNode nzyme;

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "findHiddenAccessPoints", summary = "List hidden access points of a tenant",
            description = "Returns the access points that SSID monitoring recorded because they advertise without a "
                    + "network name, with their approval and ignore status. Recording is off by default and is "
                    + "enabled in the SSID monitoring configuration. An access point is recorded once it was "
                    + "active for the dwell time of SSID monitoring. Sorted by BSSID ascending unless both sorting "
                    + "parameters are passed. Access points that have not been seen for 30 days are deleted "
                    + "automatically. A tenant holds at most 10,000 hidden access points, and new ones are not "
                    + "recorded while it does. The page size is limited to 250. "
                    + "Requires the dot11_monitoring_manage feature permission.",
            externalDocs = @ExternalDocumentation(description = "SSID monitoring in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-ssid-monitoring"))
    @ApiResponse(responseCode = "200", description = "Hidden access points found.",
            content = @Content(schema = @Schema(implementation = HiddenAccessPointsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Page size is not between 1 and 250, the offset is negative, or the sorting parameters are invalid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                            @Parameter(description = "Sorting column. One of BSSID, STATUS, FIRST_SEEN, LAST_SEEN. Case insensitive.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction: ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Organization UUID.") @PathParam("organization_id") @NotNull UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") @NotNull UUID tenantId) {
        if (limit < 1 || limit > MAX_PAGE_SIZE || offset < 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        HiddenAccessPoints.OrderColumn orderColumn = HiddenAccessPoints.OrderColumn.BSSID;
        OrderDirection orderDirection = OrderDirection.ASC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = HiddenAccessPoints.OrderColumn.valueOf(orderColumnParam.toUpperCase(Locale.ROOT));
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        HiddenAccessPoints hidden = nzyme.getDot11().hiddenAccessPoints();

        List<HiddenAccessPointDetailsResponse> accessPoints = Lists.newArrayList();
        for (HiddenAccessPoints.Entry entry
                : hidden.findAll(organizationId, tenantId, orderColumn, orderDirection, limit, offset)) {
            accessPoints.add(HiddenAccessPointDetailsResponse.create(
                    entry.uuid(),
                    entry.organizationId(),
                    entry.tenantId(),
                    Dot11MacAddressResponse.create(
                            entry.bssid(),
                            nzyme.getOuiService().lookup(entry.bssid()).orElse(null),
                            null,
                            nzyme.getContextService().findMacAddressContext(entry.bssid(), organizationId, tenantId)
                                    .map(context -> Dot11MacAddressContextResponse.create(
                                            context.name(), context.description(), context.notes()
                                    ))
                                    .orElse(null)
                    ),
                    entry.isApproved(),
                    entry.isIgnored(),
                    entry.siblingSsids(),
                    entry.revealedSsids(),
                    entry.firstSeen(),
                    entry.lastSeen()
            ));
        }

        return Response.ok(HiddenAccessPointsListResponse.create(
                hidden.count(organizationId, tenantId), HiddenAccessPoints.MAX_ENTRIES_PER_TENANT, accessPoints
        )).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/many/{action}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "applyToHiddenAccessPoints", summary = "Approve, ignore or delete hidden access points",
            description = "Applies one action to every hidden access point in the list that belongs to the tenant. "
                    + "APPROVE and IGNORE only apply to access points that are neither approved nor ignored, REVOKE "
                    + "only to approved and UNIGNORE only to ignored access points. Approved and ignored access "
                    + "points do not trigger new alerts and their existing alerts become inactive within a few "
                    + "minutes. DELETE removes the records, and a deleted access point reappears as a new, "
                    + "unapproved one the next time it is observed. Access points the action does not apply to and "
                    + "unknown UUIDs are skipped. At most 250 UUIDs per call. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Action applied.",
            content = @Content(schema = @Schema(implementation = KnownNetworksBulkActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown action, missing request body or more than 250 UUIDs.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response applyToMany(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Organization UUID.") @PathParam("organization_id") @NotNull UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") @NotNull UUID tenantId,
                                @Parameter(description = "APPROVE, REVOKE, IGNORE, UNIGNORE or DELETE. Case insensitive.") @PathParam("action") String actionParam,
                                @RequestBody(description = "The UUIDs of the hidden access points.", required = true, content = @Content(mediaType = "application/json")) UUIDListRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (req == null || req.uuids() == null || req.uuids().size() > MAX_PAGE_SIZE
                || req.uuids().stream().anyMatch(Objects::isNull)) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Dot11.KnownNetworkBulkAction action;
        try {
            action = Dot11.KnownNetworkBulkAction.valueOf(actionParam.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        int affected = nzyme.getDot11().hiddenAccessPoints().apply(req.uuids(), action, organizationId, tenantId);

        return Response.ok(KnownNetworksBulkActionResponse.create(affected)).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/approve")
    @Operation(operationId = "approveHiddenAccessPoints", summary = "Approve all hidden access points of a tenant",
            description = "Approves every hidden access point of the tenant that is neither approved nor ignored. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Hidden access points approved.",
            content = @Content(schema = @Schema(implementation = KnownNetworksBulkActionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response approveAllOfTenant(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @Parameter(description = "Organization UUID.") @PathParam("organization_id") @NotNull UUID organizationId,
                                       @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") @NotNull UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        int affected = nzyme.getDot11().hiddenAccessPoints().approveAllOfTenant(organizationId, tenantId);

        return Response.ok(KnownNetworksBulkActionResponse.create(affected)).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "deleteHiddenAccessPoints", summary = "Delete all hidden access points of a tenant",
            description = "Deletes every hidden access point record of the tenant, including approved and ignored "
                    + "ones. Each reappears as a new, unapproved access point the next time it is observed. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Hidden access points deleted.",
            content = @Content(schema = @Schema(implementation = KnownNetworksBulkActionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteAllOfTenant(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Organization UUID.") @PathParam("organization_id") @NotNull UUID organizationId,
                                      @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") @NotNull UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        int affected = nzyme.getDot11().hiddenAccessPoints().deleteAllOfTenant(organizationId, tenantId);

        return Response.ok(KnownNetworksBulkActionResponse.create(affected)).build();
    }

}
