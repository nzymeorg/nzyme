package app.nzyme.core.rest.resources.context;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.db.IpAddressContextEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.requests.CreateIpAddressContextRequest;
import app.nzyme.core.rest.requests.UpdateIpAddressContextRequest;
import app.nzyme.core.rest.responses.context.*;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressContextResponse;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.assets.AssetDetailsResponse;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
import app.nzyme.core.util.Tools;
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

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/api/context/ip")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class IPAddressContextResource extends UserAuthenticatedResource  {

    @Inject
    private NzymeNode nzyme;

    @GET
    public Response ips(@Context SecurityContext sc,
                        @QueryParam("organization_id") UUID organizationId,
                        @QueryParam("tenant_id") UUID tenantId,
                        @QueryParam("cidr_filter") @Nullable String cidrFilter,
                        @QueryParam("limit") @Max(250) int limit,
                        @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!Tools.isValidCidr(cidrFilter)) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        long count = nzyme.getContextService().countIpAddressContext(organizationId, tenantId, cidrFilter);

        List<IpAddressContextDetailsResponse> addresses = Lists.newArrayList();

        for (IpAddressContextEntry m : nzyme.getContextService()
                .findAllIpAddressContext(organizationId, tenantId, cidrFilter, limit, offset)) {
            addresses.add(entryToResponse(m));
        }

        return Response.ok(IpAddressContextListResponse.create(count, addresses)).build();
    }

    @GET
    @Path("/show/{address}")
    public Response ip(@Context SecurityContext sc,
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

        IpAddressContextDetailsResponse contextResponse = nzyme.getContextService()
                .findIpAddressContext(address, organizationId, tenantId)
                .map(this::entryToResponse)
                .orElse(null);

        return Response.ok(EnrichedIpAddressContextDetailsResponse.create(
                contextResponse,
                assets
        )).build();
    }


    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "ip_context_manage" })
    public Response createIp(@Context SecurityContext sc, @Valid CreateIpAddressContextRequest req) {
        if (!passedTenantDataAccessible(sc, req.organizationId(), req.tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Does this address exist already?
        if (nzyme.getContextService()
                .findIpAddressContext(req.ipAddress(), req.organizationId(), req.tenantId()).isPresent()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.create("Context for this IP address already exists."))
                    .build();
        }

        nzyme.getContextService().createIpAddressContext(
                req.ipAddress(),
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
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "ip_context_manage" })
    @Path("/show/uuid/{uuid}")
    public Response updateIp(@Context SecurityContext sc,
                             @Valid UpdateIpAddressContextRequest req,
                             @PathParam("uuid") UUID uuid) {
        if (!passedTenantDataAccessible(sc, req.organizationId(), req.tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Does this context exist for org and tenant? Don't allow to change org or tenant on existing context.
        if (nzyme.getContextService().findIpAddressContext(uuid, req.organizationId(), req.tenantId()).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getContextService().updateIpAddressContext(
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
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "ip_context_manage" })
    @Path("/show/uuid/{uuid}")
    public Response deleteIp(@Context SecurityContext sc,
                             @QueryParam("organization_id") UUID organizationId,
                             @QueryParam("tenant_id") UUID tenantId,
                             @PathParam("uuid") UUID uuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getContextService().deleteIpAddressContext(uuid, organizationId, tenantId);

        // Invalidate caches.
        invalidateContextCachesClusterWide();

        // Wait for caches to invalidate. TODO: Make this a blocking operation instead. This is whacky.
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {}

        return Response.status(Response.Status.OK).build();
    }

    private IpAddressContextDetailsResponse entryToResponse(IpAddressContextEntry e) {
        return IpAddressContextDetailsResponse.create(
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
                Map.of("cache_type", "context_ips"),
                false
        ));
    }

}
