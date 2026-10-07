package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.context.db.NetworkContextEntry;
import app.nzyme.core.integrations.geoip.GeoIpLookupResult;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.context.NetworkContextDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressContextResponse;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.assets.AssetDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.assets.AssetsListResponse;
import app.nzyme.core.rest.responses.ethernet.ipaddresses.IPAddressDetailsResponse;
import app.nzyme.core.rest.responses.shared.GeoInformationResponse;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.rest.RestHelpers.macContextEntryToResponse;

@Path("/api/ethernet/ips")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "IP Addresses", description = "Everything Nzyme knows about a single IP address seen on the "
        + "Ethernet network, including the assets that used it, network context and geo information.")
public class IPAddressesResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/show/{address}")
    @Operation(operationId = "findIpAddress", summary = "Get details of an IP address",
            description = "Returns all assets of the tenant that were seen using this IP address, the network "
                    + "context entries the address falls into and geo information from the GeoIP databases. The "
                    + "asset list is paginated. Geo information is null if no GeoIP database is configured or the "
                    + "address is not in it.")
    @ApiResponse(responseCode = "200", description = "IP address details found.",
            content = @Content(schema = @Schema(implementation = IPAddressDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response one(@Parameter(hidden = true) @Context SecurityContext sc,
                        @Parameter(description = "IP address to look up.") @PathParam("address") InetAddress address,
                        @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                        @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                        @Parameter(description = "Page size of the asset list.") @QueryParam("limit") int limit,
                        @Parameter(description = "Page offset of the asset list.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Associated assets.
        List<AssetDetailsResponse> assets = Lists.newArrayList();
        long totalAssets = nzyme.getAssetsManager().countAssetsOfIpAddress(address, organizationId, tenantId);
        for (AssetEntry asset : nzyme.getAssetsManager()
                .findAssetsByIpAddress(address, organizationId, tenantId, limit, offset)) {
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

        // Geo info.
        GeoInformationResponse geoResponse = null;
        Optional<GeoIpLookupResult> geo = nzyme.getGeoIpService().lookup(address);
        if (geo.isPresent()) {
            geoResponse = GeoInformationResponse.create(
                    geo.get().asn().number(),
                    geo.get().asn().name(),
                    geo.get().asn().domain(),
                    geo.get().geo().city(),
                    geo.get().geo().countryCode(),
                    geo.get().geo().countryName(),
                    geo.get().geo().latitude(),
                    geo.get().geo().longitude()
            );
        }

        List<NetworkContextDetailsResponse> context = Lists.newArrayList();
        for (NetworkContextEntry ctx : nzyme.getContextService()
                .findNetworkContext(address, organizationId, tenantId)) {
            context.add(NetworkContextDetailsResponse.create(
                    ctx.uuid(),
                    ctx.network().toString(),
                    ctx.name(),
                    ctx.description(),
                    ctx.notes(),
                    ctx.createdAt(),
                    ctx.updatedAt()
            ));
        }

        return Response.ok(IPAddressDetailsResponse.create(
                address.getHostAddress(), AssetsListResponse.create(totalAssets, assets), context, geoResponse
        )).build();
    }

}
