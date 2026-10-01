package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.integrations.geoip.GeoIpLookupResult;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressContextResponse;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.assets.AssetDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.assets.AssetsListResponse;
import app.nzyme.core.rest.responses.ethernet.ipaddresses.IPAddressDetailsResponse;
import app.nzyme.core.rest.responses.shared.GeoInformationResponse;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
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

@Path("/api/ethernet/ips")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class IPAddressesResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/show/{address}")
    public Response one(@Context SecurityContext sc,
                        @PathParam("address") InetAddress address,
                        @QueryParam("organization_id") UUID organizationId,
                        @QueryParam("tenant_id") UUID tenantId,
                        @QueryParam("limit") int limit,
                        @QueryParam("offset") int offset) {
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
                    geo.get().geo().latitude(),
                    geo.get().geo().longitude()
            );
        }

        return Response.ok(IPAddressDetailsResponse.create(
                address.getHostAddress(), AssetsListResponse.create(totalAssets, assets), geoResponse
        )).build();
    }

}
