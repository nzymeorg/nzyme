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
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "SOCKS", description = "SOCKS4, SOCKS4A and SOCKS5 tunnels that Nzyme taps observed on the "
        + "network, including the tunnel endpoints, the authentication status and the tunneled destination. Each "
        + "tunnel is tracked over its lifetime and keyed by its underlying TCP session.",
        externalDocs = @ExternalDocumentation(description = "SOCKS in the Nzyme documentation",
                url = "https://go.nzyme.org/ethernet-socks"))
public class SocksResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/tunnels")
    @Operation(operationId = "findSocksTunnels", summary = "List SOCKS tunnels",
            description = "Returns all SOCKS tunnels observed in the time range, newest established first by default. "
                    + "Each tunnel carries the SOCKS version, the handshake and authentication status and the "
                    + "tunneled destination.")
    @ApiResponse(responseCode = "200", description = "Tunnels found.",
            content = @Content(schema = @Schema(implementation = SocksTunnelsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response tunnels(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                            @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
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
    @Operation(operationId = "findSocksTunnel", summary = "Get a SOCKS tunnel",
            description = "Returns a single SOCKS tunnel by the session key of the underlying TCP session.")
    @ApiResponse(responseCode = "200", description = "Tunnel found.",
            content = @Content(schema = @Schema(implementation = SocksTunnelDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Tunnel not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response tunnel(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Session key of the underlying TCP session.") @PathParam("session_key") String sessionKey,
                           @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
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
    @Operation(operationId = "findSocksActiveTunnelsHistogram", summary = "Get histogram of active SOCKS tunnels",
            description = "Returns the number of SOCKS tunnels that were active in each bucket of the time range. "
                    + "The bucket size is chosen automatically based on the length of the time range.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = NumericHistogramResponse.class)))
    public Response activeTunnelsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                           @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                           @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
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
    @Operation(operationId = "findSocksTopClients", summary = "List top SOCKS clients",
            description = "Returns the client addresses with the most SOCKS tunnels in the time range, together with "
                    + "the tunnel count and the number of tunneled bytes.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response topClientsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                        @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                        @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                        @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                        @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                        @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                        @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                        @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                        @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
    @Operation(operationId = "findSocksTopServers", summary = "List top SOCKS servers",
            description = "Returns the SOCKS server addresses with the most tunnels in the time range, together with "
                    + "the tunnel count and the number of tunneled bytes.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response topServersHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                        @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                        @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                        @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                        @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                        @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                        @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                        @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                        @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
