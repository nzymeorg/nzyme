package app.nzyme.core.rest.resources.context;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.db.NetworkContextEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.ethernet.CIDR;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.requests.CreateNetworkContextRequest;
import app.nzyme.core.rest.requests.UpdateNetworkContextRequest;
import app.nzyme.core.rest.responses.context.*;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressContextResponse;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.assets.AssetDetailsResponse;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
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
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.rest.RestHelpers.macContextEntryToResponse;

@Path("/api/context/networks")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Context", description = "Context is the knowledge you attach to an identifier, a MAC address or a "
        + "network in CIDR notation, as a short name, a description and free form notes. Nzyme shows it everywhere "
        + "the identifier appears in the web interface. Context always belongs to one tenant and is not shared "
        + "across tenants.",
        externalDocs = @ExternalDocumentation(description = "Context in the Nzyme documentation",
                url = "https://go.nzyme.org/context"))
public class NetworkContextResource extends UserAuthenticatedResource  {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findNetworkContext", summary = "List network context of a tenant",
            description = "Returns all network context entries of a tenant, most specific prefix first. A network "
                    + "context entry describes a network in CIDR notation, for example a user VLAN, a guest WiFi "
                    + "range or a VPN address pool, and applies to every IP address inside it.")
    @ApiResponse(responseCode = "200", description = "Context entries found.",
            content = @Content(schema = @Schema(implementation = NetworkContextListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response allNetworks(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                @Parameter(description = "Only return networks that contain this string. Omit to return all networks.") @QueryParam("address_filter") @Nullable String addressFilter,
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

        long count = nzyme.getContextService().countNetworkContext(organizationId, tenantId, filter);

        List<NetworkContextDetailsResponse> networks = Lists.newArrayList();
        for (NetworkContextEntry m : nzyme.getContextService()
                .findAllNetworkContext(organizationId, tenantId, filter, limit, offset)) {
            networks.add(entryToResponse(m));
        }

        return Response.ok(NetworkContextListResponse.create(count, networks)).build();
    }

    @GET
    @Path("/show/{address}")
    @Operation(operationId = "findNetworkContextByAddress", summary = "Get network context of an IP address",
            description = "Returns every network context entry whose network contains this IP address, most specific "
                    + "prefix first, together with all assets that were seen using the address in the previous 7 "
                    + "days. Overlapping networks are expected and all of them match. Both lists are empty when "
                    + "nothing matches.")
    @ApiResponse(responseCode = "200", description = "Address looked up.",
            content = @Content(schema = @Schema(implementation = EnrichedIpAddressContextDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response network(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") @NotNull UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") @NotNull UUID tenantId,
                            @Parameter(description = "IP address to look up.") @PathParam("address") InetAddress address) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<AssetDetailsResponse> assets = Lists.newArrayList();
        for (AssetEntry asset : nzyme.getAssetsManager()
                .findAssetsByIpAddress(address, organizationId, tenantId, new DateTime().minusDays(7), Integer.MAX_VALUE, 0)) {
            Optional<MacAddressContextEntry> context = nzyme.getContextService()
                    .findMacAddressContext(asset.mac(), organizationId, tenantId);

            String oui = nzyme.getOuiService().lookup(asset.mac()).orElse(null);

            EthernetMacAddressResponse mac = EthernetMacAddressResponse.create(
                    asset.mac(),
                    oui,
                    asset.uuid(),
                    asset.isActive(),
                    macContextEntryToResponse(context)
            );

            assets.add(AssetDetailsResponse.create(
                    asset.uuid(),
                    mac,
                    oui,
                    asset.isActive(),
                    context.map(MacAddressContextEntry::name).orElse(null),
                    asset.dhcpFingerprintInitial(),
                    asset.dhcpFingerprintRenew(),
                    asset.dhcpFingerprintReboot(),
                    asset.dhcpFingerprintRebind(),
                    asset.seenArp(),
                    asset.seenDhcp(),
                    asset.seenTcp(),
                    asset.seenUdp(),
                    asset.firstSeen(),
                    asset.lastSeen()
            ));
        }

        List<NetworkContextDetailsResponse> contextResponse = nzyme.getContextService()
                .findNetworkContext(address, organizationId, tenantId)
                .stream()
                .map(this::entryToResponse)
                .toList();

        return Response.ok(EnrichedIpAddressContextDetailsResponse.create(
                contextResponse,
                assets
        )).build();
    }

    @GET
    @Path("/show/uuid/{uuid}")
    @Operation(operationId = "findNetworkContextByUuid", summary = "Get network context by UUID")
    @ApiResponse(responseCode = "200", description = "Context entry found.",
            content = @Content(schema = @Schema(implementation = NetworkContextDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Context entry not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response networkByUuid(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Organization UUID.") @QueryParam("organization_id") @NotNull UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") @NotNull UUID tenantId,
                                  @Parameter(description = "UUID of the context entry.") @PathParam("uuid") UUID uuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<NetworkContextEntry> ctx = nzyme.getContextService()
                .findNetworkContext(uuid, organizationId, tenantId);

        if (ctx.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(entryToResponse(ctx.get())).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "network_context_manage" })
    @Operation(operationId = "createNetworkContext", summary = "Create network context",
            description = "Creates context for an IPv4 or IPv6 network. The address has to be the start of the "
                    + "range, so host bits must not be set, and leaving out the prefix length matches that single "
                    + "address. Only one context entry can exist per exact network and tenant, but overlapping "
                    + "networks with different prefix lengths are allowed. The call waits a few seconds for the "
                    + "context caches of all cluster nodes to invalidate before it returns. Requires the "
                    + "network_context_manage feature permission.")
    @ApiResponse(responseCode = "201", description = "Context created.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Context for this network exists already.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response create(@Parameter(hidden = true) @Context SecurityContext sc,
                           @RequestBody(description = "CIDR range, context fields, organization and tenant.", required = true, content = @Content(mediaType = "application/json"))
                           @Valid CreateNetworkContextRequest req) {
        if (!passedTenantDataAccessible(sc, req.organizationId(), req.tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        CIDR cidr = CIDR.parse(req.cidr());

        // Does this address exist already?
        if (nzyme.getContextService()
                .findNetworkContext(cidr, req.organizationId(), req.tenantId()).isPresent()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.create("Context for this network already exists."))
                    .build();
        }

        nzyme.getContextService().createNetworkContext(
                cidr,
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
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "network_context_manage" })
    @Path("/show/uuid/{uuid}")
    @Operation(operationId = "updateNetworkContext", summary = "Update network context",
            description = "Updates the name, description and notes of a network context entry. The network, "
                    + "organization and tenant of an existing entry cannot be changed. Requires the "
                    + "network_context_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Context updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Context entry not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response update(@Parameter(hidden = true) @Context SecurityContext sc,
                           @RequestBody(description = "New name, description and notes, plus organization and tenant.", required = true, content = @Content(mediaType = "application/json"))
                           @Valid UpdateNetworkContextRequest req,
                           @Parameter(description = "UUID of the context entry.") @PathParam("uuid") UUID uuid) {
        if (!passedTenantDataAccessible(sc, req.organizationId(), req.tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Does this context exist for org and tenant? Don't allow to change org or tenant on existing context.
        if (nzyme.getContextService().findNetworkContext(uuid, req.organizationId(), req.tenantId()).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getContextService().updateNetworkContext(
                uuid, req.organizationId(), req.tenantId(), req.name(), req.description(), req.notes()
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
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "network_context_manage" })
    @Path("/show/organization/show/{organization_id}/tenant/show/{tenant_id}/uuid/{uuid}")
    @Operation(operationId = "deleteNetworkContext", summary = "Delete network context",
            description = "Deletes a network context entry. The call succeeds even if no entry with this UUID "
                    + "exists. Requires the network_context_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Context deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response delete(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           @Parameter(description = "UUID of the context entry.") @PathParam("uuid") UUID uuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getContextService().deleteNetworkContext(uuid, organizationId, tenantId);

        // Invalidate caches.
        invalidateContextCachesClusterWide();

        // Wait for caches to invalidate. TODO: Make this a blocking operation instead. This is whacky.
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {}

        return Response.status(Response.Status.OK).build();
    }

    private NetworkContextDetailsResponse entryToResponse(NetworkContextEntry e) {
        return NetworkContextDetailsResponse.create(
                e.uuid(),
                e.network().toString(),
                e.name(),
                e.description(),
                e.notes(),
                e.createdAt(),
                e.updatedAt()
        );
    }

    private void invalidateContextCachesClusterWide() {
        nzyme.getMessageBus().sendToAllOnlineNodes(ClusterMessage.create(
                MessageType.INVALIDATE_CACHE,
                Map.of("cache_type", "context_networks"),
                false
        ));
    }

}
