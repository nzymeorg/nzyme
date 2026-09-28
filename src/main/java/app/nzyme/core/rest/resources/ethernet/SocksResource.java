package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.socks.SOCKS;
import app.nzyme.core.ethernet.socks.db.SocksTunnelEntry;
import app.nzyme.core.ethernet.l4.tcp.db.TcpSessionEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import app.nzyme.core.rest.responses.ethernet.socks.SocksTunnelDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.socks.SocksTunnelsListResponse;
import app.nzyme.core.rest.responses.shared.*;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/socks")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class SocksResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/tunnels")
    public Response tunnels(@Context SecurityContext sc,
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

        SOCKS.OrderColumn orderColumn = SOCKS.OrderColumn.ESTABLISHED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = SOCKS.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().socks().countAllTunnels(timeRange, filters, taps);

        List<SocksTunnelDetailsResponse> tunnels = Lists.newArrayList();
        for (SocksTunnelEntry tunnel : nzyme.getEthernet().socks()
                .findAllTunnels(timeRange, filters, orderColumn, orderDirection, limit, offset, taps)) {
            tunnels.add(buildTunnelDetails(tunnel, organizationId, tenantId, taps));
        }

        return Response.ok(SocksTunnelsListResponse.create(total, tunnels)).build();
    }

    @GET
    @Path("/tunnels/show/{session_key}")
    public Response tunnel(@Context SecurityContext sc,
                           @PathParam("session_key") String sessionKey,
                           @QueryParam("organization_id") UUID organizationId,
                           @QueryParam("tenant_id") UUID tenantId,
                           @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<SocksTunnelEntry> tunnel = nzyme.getEthernet().socks().findTunnel(sessionKey, taps);

        if (tunnel.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(buildTunnelDetails(tunnel.get(), organizationId, tenantId, taps)).build();
    }

    @GET
    @Path("/tunnels/active/histogram")
    public Response activeTunnelsHistogram(@Context SecurityContext sc,
                                           @QueryParam("time_range") @Valid String timeRangeParameter,
                                           @QueryParam("filters") String filtersParameter,
                                           @QueryParam("taps") String taps) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Map<DateTime, Integer> buckets = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().socks()
                .getActiveTunnelsHistogram(timeRange, bucketing, filters, tapUUIDs)) {
            buckets.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(NumericHistogramResponse.create(buckets, bucketing.bucketSizeMs())).build();
    }

    @GET
    @Path("/tunnels/clients/top/histogram")
    public Response topClientsHistogram(@Context SecurityContext sc,
                                        @QueryParam("organization_id") UUID organizationId,
                                        @QueryParam("tenant_id") UUID tenantId,
                                        @QueryParam("time_range") @Valid String timeRangeParameter,
                                        @QueryParam("filters") String filtersParameter,
                                        @QueryParam("taps") String taps,
                                        @QueryParam("order_column") @Nullable String orderColumnParam,
                                        @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                        @QueryParam("limit") int limit,
                                        @QueryParam("offset") int offset) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ThreeColumnWithKeyHistogramOrderColumn orderColumn = ThreeColumnWithKeyHistogramOrderColumn.VALUE1;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ThreeColumnWithKeyHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long count = nzyme.getEthernet().socks().getTopClientsCount(timeRange, filters, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (L4AddressDataAddressNumberNumberAggregationResult x : nzyme.getEthernet().socks()
                .getTopClients(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.UDP, x.key()),
                            HistogramValueType.L4_ADDRESS,
                            null),
                    HistogramValueStructureResponse.create(x.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(x.value2(), HistogramValueType.BYTES, null),
                    x.key().address()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, true, values)).build();
    }

    @GET
    @Path("/tunnels/servers/top/histogram")
    public Response topServersHistogram(@Context SecurityContext sc,
                                        @QueryParam("organization_id") UUID organizationId,
                                        @QueryParam("tenant_id") UUID tenantId,
                                        @QueryParam("time_range") @Valid String timeRangeParameter,
                                        @QueryParam("filters") String filtersParameter,
                                        @QueryParam("taps") String taps,
                                        @QueryParam("order_column") @Nullable String orderColumnParam,
                                        @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                        @QueryParam("limit") int limit,
                                        @QueryParam("offset") int offset) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ThreeColumnWithKeyHistogramOrderColumn orderColumn = ThreeColumnWithKeyHistogramOrderColumn.VALUE1;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ThreeColumnWithKeyHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long count = nzyme.getEthernet().socks().getTopServersCount(timeRange, filters, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (L4AddressDataAddressNumberNumberAggregationResult x : nzyme.getEthernet().socks()
                .getTopServers(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.UDP, x.key()),
                            HistogramValueType.L4_ADDRESS,
                            null),
                    HistogramValueStructureResponse.create(x.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(x.value2(), HistogramValueType.BYTES, null),
                    x.key().address()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, true, values)).build();
    }


    private SocksTunnelDetailsResponse buildTunnelDetails(SocksTunnelEntry t,
                                                          UUID organizationId,
                                                          UUID tenantId,
                                                          List<UUID> taps) {
        // Get underlying TCP session. (Can be NULL)
        Optional<TcpSessionEntry> tcpSession = nzyme.getEthernet().tcp()
                .findSessionBySessionKey(t.tcpSessionKey(), t.establishedAt(), taps);

        L4AddressResponse client = null;
        L4AddressResponse socksServer = null;
        if (tcpSession.isPresent()) {
            client = RestHelpers.L4AddressDataToResponse(
                    nzyme, organizationId, tenantId, L4Type.TCP, tcpSession.get().source()
            );
            socksServer = RestHelpers.L4AddressDataToResponse(
                    nzyme, organizationId, tenantId, L4Type.TCP, tcpSession.get().destination()
            );
        }

        return SocksTunnelDetailsResponse.create(
                client,
                socksServer,
                t.tcpSessionKey(),
                t.socksType(),
                t.authenticationStatus(),
                t.handshakeStatus(),
                tcpSession.map(tcp -> RestHelpers
                                .tcpSessionStateToGeneric(tcp.state()))
                        .orElse("Invalid"),
                t.username(),
                t.tunneledBytes(),
                t.tunneledDestinationAddress(),
                t.tunneledDestinationHost(),
                t.tunneledDestinationPort(),
                t.establishedAt(),
                t.terminatedAt(),
                t.mostRecentSegmentTime(),
                t.durationMs()
        );
    }

}
