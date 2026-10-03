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
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/api/context/networks")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class NetworkContextResource extends UserAuthenticatedResource  {

    @Inject
    private NzymeNode nzyme;

    @GET
    public Response allNetworks(@Context SecurityContext sc,
                                @QueryParam("organization_id") UUID organizationId,
                                @QueryParam("tenant_id") UUID tenantId,
                                @QueryParam("limit") @Max(250) int limit,
                                @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long count = nzyme.getContextService().countNetworkContext(organizationId, tenantId);

        List<NetworkContextDetailsResponse> networks = Lists.newArrayList();
        for (NetworkContextEntry m : nzyme.getContextService()
                .findAllNetworkContext(organizationId, tenantId, limit, offset)) {
            networks.add(entryToResponse(m));
        }

        return Response.ok(NetworkContextListResponse.create(count, networks)).build();
    }

    @GET
    @Path("/show/{address}")
    public Response network(@Context SecurityContext sc,
                            @QueryParam("organization_id") @NotNull UUID organizationId,
                            @QueryParam("tenant_id") @NotNull UUID tenantId,
                            @PathParam("address") InetAddress address) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<AssetDetailsResponse> assets = Lists.newArrayList();
        for (AssetEntry asset : nzyme.getAssetsManager()
                .findAssetsByIpAddress(address, organizationId, tenantId, Integer.MAX_VALUE, 0)) {
            Optional<MacAddressContextEntry> context = nzyme.getContextService()
                    .findMacAddressContext(asset.mac(), organizationId, tenantId);

            String oui = nzyme.getOuiService().lookup(asset.mac()).orElse(null);

            EthernetMacAddressResponse mac = EthernetMacAddressResponse.create(
                    asset.mac(),
                    oui,
                    asset.uuid(),
                    asset.isActive(),
                    context.map(ctx ->
                            EthernetMacAddressContextResponse.create(
                                    ctx.name(),
                                    ctx.description()
                            )
                    ).orElse(null)
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
    public Response networkByUuid(@Context SecurityContext sc,
                                  @QueryParam("organization_id") @NotNull UUID organizationId,
                                  @QueryParam("tenant_id") @NotNull UUID tenantId,
                                  @PathParam("uuid") UUID uuid) {
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
    public Response create(@Context SecurityContext sc, @Valid CreateNetworkContextRequest req) {
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
    public Response update(@Context SecurityContext sc,
                           @Valid UpdateNetworkContextRequest req,
                           @PathParam("uuid") UUID uuid) {
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
    public Response delete(@Context SecurityContext sc,
                           @PathParam("organization_id") UUID organizationId,
                           @PathParam("tenant_id") UUID tenantId,
                           @PathParam("uuid") UUID uuid) {
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
