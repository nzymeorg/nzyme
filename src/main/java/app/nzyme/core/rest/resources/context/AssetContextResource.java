package app.nzyme.core.rest.resources.context;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.context.db.MacAddressTransparentContextEntry;
import app.nzyme.core.dot11.Dot11MacAddressMetadata;
import app.nzyme.core.dot11.db.monitoring.MonitoredBSSID;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.constraints.MacAddress;
import app.nzyme.core.rest.misc.CategorizedTransparentContextData;
import app.nzyme.core.rest.requests.CreateMacAddressContextRequest;
import app.nzyme.core.rest.requests.UpdateMacAddressContextNameRequest;
import app.nzyme.core.rest.requests.UpdateMacAddressContextRequest;
import app.nzyme.core.rest.responses.context.*;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.distributed.messaging.ClusterMessage;
import app.nzyme.plugin.distributed.messaging.MessageType;
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
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import javax.annotation.Nullable;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/api/context/mac")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Context", description = "Context is the knowledge you attach to an identifier, a MAC address or a "
        + "network in CIDR notation, as a short name, a description and free form notes. Nzyme shows it everywhere "
        + "the identifier appears in the web interface. Context always belongs to one tenant and is not shared "
        + "across tenants.",
        externalDocs = @ExternalDocumentation(description = "Context in the Nzyme documentation",
                url = "https://go.nzyme.org/context"))
public class AssetContextResource extends UserAuthenticatedResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/organization/show/{organization_id}/tenant/show/{tenant_id}")
    @Operation(operationId = "findAssetContext", summary = "List MAC address context of a tenant",
            description = "Returns all MAC address context entries of a tenant, ordered by address. Entries Nzyme "
                    + "created transparently are included and have no name. Each entry also carries the transparent "
                    + "context Nzyme learned for the address, the recently observed IP addresses and hostnames.")
    @ApiResponse(responseCode = "200", description = "Context entries found.",
            content = @Content(schema = @Schema(implementation = MacAddressContextListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response macs(@Parameter(hidden = true) @Context SecurityContext sc,
                         @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                         @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                         @Parameter(description = "Only return addresses that contain this string. Omit to return all addresses.") @QueryParam("address_filter") @Nullable String addressFilter,
                         @Parameter(description = "Page size. The maximum is 250.") @QueryParam("limit") @Max(250) int limit,
                         @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String filter;
        if (addressFilter == null || addressFilter.trim().isEmpty()) {
            // Set to SQL wildcard matcher to find all addresses if no filter set.
            filter = "%";
        } else {
            filter = "%" + addressFilter.trim().toUpperCase() + "%";
        }

        long count = nzyme.getContextService().countMacAddressContext(organizationId, tenantId, filter);

        List<MacAddressContextDetailsResponse> addresses = Lists.newArrayList();

        nzyme.getDatabase().useHandle(handle -> {
            for (MacAddressContextEntry m : nzyme.getContextService()
                    .findAllMacAddressContext(organizationId, tenantId, filter, limit, offset)) {
                List<MacAddressTransparentContextEntry> transparent = nzyme.getContextService()
                        .findTransparentMacAddressContext(handle, m.id());

                addresses.add(entryToResponse(m, transparent));
            }
        });

        return Response.ok(MacAddressContextListResponse.create(count, addresses)).build();
    }

    @GET
    @Path("/organization/show/{organization_id}/tenant/show/{tenant_id}/uuid/{uuid}")
    @Operation(operationId = "findAssetContextByUuid", summary = "Get MAC address context by UUID")
    @ApiResponse(responseCode = "200", description = "Context entry found.",
            content = @Content(schema = @Schema(implementation = MacAddressContextDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Context entry not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response macByUuid(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                              @Parameter(description = "UUID of the context entry.") @PathParam("uuid") UUID uuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<MacAddressContextEntry> ctx = nzyme.getContextService()
                .findMacAddressContext(uuid, organizationId, tenantId);

        if (ctx.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<MacAddressTransparentContextEntry> transparent = nzyme.getContextService()
                .findTransparentMacAddressContext(ctx.get().id());

        return Response.ok(entryToResponse(ctx.get(), transparent)).build();
    }

    @GET
    @Path("/show/{mac}")
    @Operation(operationId = "findAssetContextByMac", summary = "Get enriched MAC address context",
            description = "Returns the context of a MAC address together with what Nzyme learned about the address "
                    + "itself: if it acts as a WiFi access point or client, and if it serves a monitored network. The "
                    + "context is null when you have not created one for this address yet.")
    @ApiResponse(responseCode = "200", description = "Address found.",
            content = @Content(schema = @Schema(implementation = EnrichedMacAddressContextDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response mac(@Parameter(hidden = true) @Context SecurityContext sc,
                        @Parameter(description = "Organization UUID.") @QueryParam("organization_id") @NotNull UUID organizationId,
                        @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") @NotNull UUID tenantId,
                        @Parameter(description = "MAC address to look up.") @PathParam("mac") @MacAddress String mac) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<MacAddressContextEntry> ctx = nzyme.getContextService().findMacAddressContext(
                mac, organizationId, tenantId
        );

        MacAddressContextDetailsResponse contextDetails;
        if (ctx.isPresent()) {
            List<MacAddressTransparentContextEntry> transparent = nzyme.getContextService()
                    .findTransparentMacAddressContext(ctx.get().id());

            contextDetails = entryToResponse(ctx.get(), transparent);
        } else {
            contextDetails = null;
        }

        Dot11MacAddressMetadata dot11Metadata = nzyme.getDot11().getMacAddressMetadata(
                mac,
                nzyme.getTapManager().allTapUUIDsAccessibleByUser(authenticatedUser)
        );

        // This is where will hook in for Ethernet types.
        MacAddressContextTypeResponse contextType;
        switch (dot11Metadata.type()) {
            case ACCESS_POINT:
                contextType = MacAddressContextTypeResponse.DOT11_AP;
                break;
            case CLIENT:
                contextType = MacAddressContextTypeResponse.DOT11_CLIENT;
                break;
            case MULTIPLE:
                contextType = MacAddressContextTypeResponse.DOT11_MIXED;
                break;
            case UNKNOWN:
            default:
                contextType = MacAddressContextTypeResponse.UNKNOWN;
        }

        // Find the first monitored SSID this MAC address may be part of. Unlikely to be more than one.
        Dot11MonitoredNetworkContextResponse servesMonitoredNetwork = null;
        for (MonitoredSSID monitoredNetwork : nzyme.getDot11()
                .findAllMonitoredSSIDs(organizationId, tenantId)) {
            for (MonitoredBSSID bssid : nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(monitoredNetwork.id())) {
                if (bssid.bssid().equalsIgnoreCase(mac)) {
                    servesMonitoredNetwork = Dot11MonitoredNetworkContextResponse.create(
                            monitoredNetwork.uuid(),
                            monitoredNetwork.isEnabled(),
                            monitoredNetwork.ssid()
                    );
                }
            }
        }

        return Response.ok(EnrichedMacAddressContextDetailsResponse.create(
                contextDetails, contextType, servesMonitoredNetwork
        )).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "mac_context_manage" })
    @Operation(operationId = "createAssetContext", summary = "Create MAC address context",
            description = "Creates context for a MAC address. The entry applies wherever the address appears, in "
                    + "WiFi, Ethernet and Bluetooth data, and only one entry can exist per address and tenant. The "
                    + "call waits a few seconds for the context caches of all cluster nodes to invalidate before it "
                    + "returns. Requires the mac_context_manage feature permission.")
    @ApiResponse(responseCode = "201", description = "Context created.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Context for this MAC address exists already.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response createMac(@Parameter(hidden = true) @Context SecurityContext sc,
                              @RequestBody(description = "MAC address, context fields, organization and tenant.", required = true, content = @Content(mediaType = "application/json"))
                              @Valid CreateMacAddressContextRequest req) {
        if (!passedTenantDataAccessible(sc, req.organizationId(), req.tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Does this address exist already?
        if (nzyme.getContextService()
                .findMacAddressContext(req.macAddress(), req.organizationId(), req.tenantId()).isPresent()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.create("Context for this MAC address already exists."))
                    .build();
        }

        nzyme.getContextService().createMacAddressContext(
                req.macAddress(),
                req.name(),
                req.description(),
                req.notes(),
                req.organizationId(),
                req.tenantId()
        );

        // Invalidate caches.
        invalidateContextCachesClusterWide();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {}

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "mac_context_manage" })
    @Path("/organization/show/{organization_id}/tenant/show/{tenant_id}/uuid/{uuid}")
    @Operation(operationId = "updateAssetContext", summary = "Update MAC address context",
            description = "Updates the name, description and notes of a MAC address context entry. The MAC address, "
                    + "organization and tenant of an existing entry cannot be changed. Requires the "
                    + "mac_context_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Context updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Context entry not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response updateMac(@Parameter(hidden = true) @Context SecurityContext sc,
                              @RequestBody(description = "New name, description and notes.", required = true, content = @Content(mediaType = "application/json"))
                              @Valid UpdateMacAddressContextRequest req,
                              @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                              @Parameter(description = "UUID of the context entry.") @PathParam("uuid") UUID uuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Does this context exist for org and tenant? Don't allow to change org or tenant on existing context.
        if (nzyme.getContextService().findMacAddressContext(uuid, organizationId, tenantId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getContextService().updateMacAddressContext(
                uuid, organizationId, tenantId, req.name(), req.description(), req.notes()
        );

        // Invalidate caches.
        invalidateContextCachesClusterWide();

        // Wait for caches to invalidate. TODO: Make this a blocking operation instead. This is whacky.
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {}

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "mac_context_manage" })
    @Path("/organization/show/{organization_id}/tenant/show/{tenant_id}/uuid/{uuid}")
    @Operation(operationId = "deleteAssetContext", summary = "Delete MAC address context",
            description = "Deletes a MAC address context entry. The call succeeds even if no entry with this UUID "
                    + "exists. If the address is still being observed, Nzyme transparently creates a new entry "
                    + "without a name. Requires the mac_context_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Context deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteMac(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                              @Parameter(description = "UUID of the context entry.") @PathParam("uuid") UUID uuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getContextService().deleteMacAddressContext(uuid, organizationId, tenantId);

        // Invalidate caches.
        invalidateContextCachesClusterWide();

        // Wait for caches to invalidate. TODO: Make this a blocking operation instead. This is whacky.
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {}

        return Response.status(Response.Status.OK).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "mac_context_manage" })
    @Path("/organization/show/{organization_id}/tenant/show/{tenant_id}/mac/{mac}/name")
    @Operation(operationId = "updateAssetContextName", summary = "Update the name of MAC address context",
            description = "Updates only the name of the context of a MAC address, addressed by the MAC address "
                    + "instead of the context UUID. The name of a MAC address context doubles as the asset name in "
                    + "the Ethernet asset inventory. Context has to exist for the address already. Requires the "
                    + "mac_context_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Name updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "No context exists for this MAC address, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response updateMacName(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @RequestBody(description = "New name for the context.", required = true, content = @Content(mediaType = "application/json"))
                                  @Valid UpdateMacAddressContextNameRequest req,
                                  @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                  @Parameter(description = "MAC address the context belongs to.") @PathParam("mac") @MacAddress String mac) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Do we have context?
        if (nzyme.getContextService().findMacAddressContext(mac, organizationId, tenantId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getContextService().updateMacAddressContextName(
                mac, organizationId, tenantId, req.name()
        );

        // Invalidate caches.
        invalidateContextCachesClusterWide();

        // Wait for caches to invalidate. TODO: Make this a blocking operation instead. This is whacky.
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {}

        return Response.ok().build();
    }

    private MacAddressContextDetailsResponse entryToResponse(MacAddressContextEntry m,
                                                             List<MacAddressTransparentContextEntry> transparent) {
        String organizationName = nzyme.getAuthenticationService()
                .findOrganization(m.organizationId())
                .map(o -> o.name())
                .orElse("Unknown");
        String tenantName = nzyme.getAuthenticationService()
                .findTenant(m.tenantId())
                .map(t -> t.name())
                .orElse("Unknown");

        CategorizedTransparentContextData transparentData = RestHelpers.transparentContextDataToResponses(transparent);
        return MacAddressContextDetailsResponse.create(
                m.uuid(),
                m.macAddress(),
                Tools.macAddressIsRandomized(m.macAddress()),
                m.name(),
                m.description(),
                m.notes(),
                transparentData.ipAddresses(),
                transparentData.hostnames(),
                m.organizationId(),
                organizationName,
                m.tenantId(),
                tenantName,
                m.createdAt(),
                m.updatedAt()
        );
    }

    private void invalidateContextCachesClusterWide() {
        nzyme.getMessageBus().sendToAllOnlineNodes(ClusterMessage.create(
                MessageType.INVALIDATE_CACHE,
                Map.of("cache_type", "context_macs"),
                false
        ));
    }

}
