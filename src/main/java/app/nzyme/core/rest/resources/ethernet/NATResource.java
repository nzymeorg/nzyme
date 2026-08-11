package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.nat.NAT;
import app.nzyme.core.ethernet.nat.db.NATTraversalDiscoveryEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import app.nzyme.core.rest.responses.ethernet.nat.NATTraversalDiscoveryDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.nat.NATTraversalDiscoveryListResponse;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/nat")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class NATResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/traversal/discoveries")
    public Response traversalDiscoveries(@Context SecurityContext sc,
                                         @QueryParam("organization_id") UUID organizationId,
                                         @QueryParam("tenant_id") UUID tenantId,
                                         @QueryParam("time_range") @Valid String timeRangeParameter,
                                         @QueryParam("filters") String filtersParameter,
                                         @QueryParam("order_column") @Nullable String orderColumnParam,
                                         @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                         @QueryParam("limit") int limit,
                                         @QueryParam("offset") int offset,
                                         @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        NAT.DiscoveryOrderColumn orderColumn = NAT.DiscoveryOrderColumn.INITIATED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = NAT.DiscoveryOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().nat().countAllDiscoveries(timeRange, filters, taps);

        List<NATTraversalDiscoveryDetailsResponse> discoveries = Lists.newArrayList();
        for (NATTraversalDiscoveryEntry discovery : nzyme.getEthernet().nat()
                .findAllDiscoveries(timeRange, filters, orderColumn, orderDirection, limit, offset, taps)) {

            discoveries.add(buildDetailsResponse(discovery, organizationId, tenantId));
        }

        return Response.ok(NATTraversalDiscoveryListResponse.create(total, discoveries)).build();
    }

    private NATTraversalDiscoveryDetailsResponse buildDetailsResponse(NATTraversalDiscoveryEntry discovery,
                                                                      UUID organizationId,
                                                                      UUID tenantId) {
        List<L4AddressResponse> mappedAddresses = discovery.mappedAddresses()
                .stream()
                .map(ma -> RestHelpers.L4AddressDataToResponse(
                        nzyme,
                        organizationId,
                        tenantId,
                        L4Type.valueOf(discovery.transport().toUpperCase()), ma))
                .toList();

        return NATTraversalDiscoveryDetailsResponse.create(
                discovery.sessionKey(),
                discovery.transport(),
                mappedAddresses,
                discovery.mostRecentSegmentTime(),
                discovery.firstSeen(),
                discovery.terminatedAt(),
                RestHelpers.L4AddressDataToResponse(
                        nzyme,
                        organizationId,
                        tenantId,
                        L4Type.valueOf(discovery.transport().toUpperCase()),
                        discovery.source()
                ),
                RestHelpers.L4AddressDataToResponse(
                        nzyme,
                        organizationId,
                        tenantId,
                        L4Type.valueOf(discovery.transport().toUpperCase()),
                        discovery.destination()
                )
        );
    }


}
