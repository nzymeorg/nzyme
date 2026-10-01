package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.integrations.geoip.GeoIpLookupResult;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.ipaddresses.IPAddressDetailsResponse;
import app.nzyme.core.rest.responses.shared.GeoInformationResponse;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.net.InetAddress;
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
                        @QueryParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

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
                address.getHostAddress(), geoResponse
        )).build();
    }

}
