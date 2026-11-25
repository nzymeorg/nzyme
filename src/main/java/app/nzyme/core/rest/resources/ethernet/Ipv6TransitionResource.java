package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.ipv6transition.Ipv6Transition;
import app.nzyme.core.ethernet.ipv6transition.db.Ipv6TransitionTunnelEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.ipv6transition.Ipv6TransitionTunnelDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.ipv6transition.Ipv6TransitionTunnelsListResponse;
import app.nzyme.core.rest.responses.shared.*;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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


@Path("/api/ethernet/ipv6transition")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "IPv6 Transition Tunnels", description = "Tunnels that carry IPv6 over an IPv4 network, as Nzyme taps "
        + "observed them: Teredo over UDP, and 6in4, 6to4 and ISATAP directly in IP. They are no VPNs: nothing is "
        + "encrypted. Each tunnel carries its endpoints, the transport and the tunneled bytes. A tunnel that is "
        + "carried directly in IP has the transport IP and no ports.")
public class Ipv6TransitionResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/tunnels")
    @Operation(operationId = "findIpv6TransitionTunnels", summary = "List IPv6 transition tunnels",
            description = "Returns all IPv6 transition tunnels observed in the time range, newest established first "
                    + "by default. Each tunnel carries its type and the transport.")
    @ApiResponse(responseCode = "200", description = "Tunnels found.",
            content = @Content(schema = @Schema(implementation = Ipv6TransitionTunnelsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response tunnels(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size. 25 if not set, 250 at most.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                            @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Ipv6Transition.OrderColumn orderColumn = Ipv6Transition.OrderColumn.ESTABLISHED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Ipv6Transition.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().ipv6Transition().countAllTunnels(timeRange, filters, taps);

        List<Ipv6TransitionTunnelDetailsResponse> tunnels = Lists.newArrayList();
        for (Ipv6TransitionTunnelEntry tunnel : nzyme.getEthernet().ipv6Transition()
                .findAllTunnels(timeRange, filters, orderColumn, orderDirection, RestHelpers.pageSize(limit), Math.max(offset, 0), taps)) {
            tunnels.add(buildTunnelDetails(tunnel, organizationId, tenantId));
        }

        return Response.ok(Ipv6TransitionTunnelsListResponse.create(total, tunnels)).build();
    }

    @GET
    @Path("/tunnels/show/{session_key}")
    @Operation(operationId = "findIpv6TransitionTunnel", summary = "Get an IPv6 transition tunnel",
            description = "Returns a single IPv6 transition tunnel by its session key.")
    @ApiResponse(responseCode = "200", description = "Tunnel found.",
            content = @Content(schema = @Schema(implementation = Ipv6TransitionTunnelDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Tunnel not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response tunnel(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Session key of the tunnel.") @PathParam("session_key") String sessionKey,
                           @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Ipv6TransitionTunnelEntry> tunnel = nzyme.getEthernet().ipv6Transition().findTunnel(sessionKey, taps);

        if (tunnel.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(buildTunnelDetails(tunnel.get(), organizationId, tenantId)).build();
    }

    @GET
    @Path("/tunnels/l4/{l4_type}/{session_key}")
    @Operation(operationId = "findIpv6TransitionTunnelOfL4Session", summary = "Get the IPv6 transition tunnel of an L4 session",
            description = "Returns the Teredo tunnel that a UDP conversation belongs to. A tunnel is keyed by its "
                    + "first underlying conversation, but is usually carried by a chain of conversations between "
                    + "the same endpoints. Later conversations are matched by endpoints and time overlap. Tunnels "
                    + "that are carried directly in IP have no session.")
    @ApiResponse(responseCode = "200", description = "Tunnel found.",
            content = @Content(schema = @Schema(implementation = Ipv6TransitionTunnelDetailsResponse.class)))
    @ApiResponse(responseCode = "400", description = "The L4 type or start time is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Tunnel not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response tunnelOfL4Session(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "L4 type of the session, TCP or UDP.") @PathParam("l4_type") String l4TypeParam,
                                      @Parameter(description = "Session key of the L4 session.") @PathParam("session_key") String sessionKey,
                                      @Parameter(description = "Start time of the L4 session in ISO 8601 format.") @QueryParam("start_time") String startTimeParam,
                                      @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                      @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                      @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        L4Type l4Type;
        DateTime startTime;
        try {
            l4Type = L4Type.valueOf(l4TypeParam.toUpperCase());
            startTime = DateTime.parse(startTimeParam);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Optional<Ipv6TransitionTunnelEntry> tunnel = nzyme.getEthernet().ipv6Transition()
                .findTunnelOfL4Session(l4Type, sessionKey, startTime, taps);

        if (tunnel.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(buildTunnelDetails(tunnel.get(), organizationId, tenantId)).build();
    }

    @GET
    @Path("/tunnels/active/histogram")
    @Operation(operationId = "findIpv6TransitionActiveTunnelsHistogram", summary = "Get histogram of active IPv6 transition tunnels",
            description = "Returns the number of IPv6 transition tunnels that were active in each bucket of the time range. "
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
        Filters filters = parseFilters(filtersParameter, sc);

        Map<DateTime, Integer> buckets = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().ipv6Transition()
                .getActiveTunnelsHistogram(timeRange, bucketing, filters, tapUUIDs)) {
            buckets.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(NumericHistogramResponse.create(buckets, bucketing.bucketSizeMs())).build();
    }

    @GET
    @Path("/tunnels/clients/top/histogram")
    @Operation(operationId = "findIpv6TransitionTopClients", summary = "List top clients of IPv6 transition tunnels",
            description = "Returns the client addresses with the most IPv6 transition tunnels in the time range, together with "
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
                                        @Parameter(description = "Page size. 25 if not set, 250 at most.") @QueryParam("limit") int limit,
                                        @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        return topAddresses(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, taps,
                orderColumnParam, orderDirectionParam, limit, offset, true);
    }

    @GET
    @Path("/tunnels/servers/top/histogram")
    @Operation(operationId = "findIpv6TransitionTopServers", summary = "List top servers of IPv6 transition tunnels",
            description = "Returns the server addresses with the most IPv6 transition tunnels in the time range, together with "
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
                                        @Parameter(description = "Page size. 25 if not set, 250 at most.") @QueryParam("limit") int limit,
                                        @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        return topAddresses(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, taps,
                orderColumnParam, orderDirectionParam, limit, offset, false);
    }

    private Response topAddresses(SecurityContext sc,
                                  UUID organizationId,
                                  UUID tenantId,
                                  String timeRangeParameter,
                                  String filtersParameter,
                                  String taps,
                                  String orderColumnParam,
                                  String orderDirectionParam,
                                  int limit,
                                  int offset,
                                  boolean clients) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

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

        Ipv6Transition tunnels = nzyme.getEthernet().ipv6Transition();
        long count = clients
                ? tunnels.getTopClientsCount(timeRange, filters, tapUUIDs)
                : tunnels.getTopServersCount(timeRange, filters, tapUUIDs);
        List<L4AddressDataAddressNumberNumberAggregationResult> results = clients
                ? tunnels.getTopClients(timeRange, filters, RestHelpers.pageSize(limit), Math.max(offset, 0), orderColumn, orderDirection, tapUUIDs)
                : tunnels.getTopServers(timeRange, filters, RestHelpers.pageSize(limit), Math.max(offset, 0), orderColumn, orderDirection, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (L4AddressDataAddressNumberNumberAggregationResult x : results) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.NONE, x.key()),
                            HistogramValueType.L4_ADDRESS,
                            null),
                    HistogramValueStructureResponse.create(x.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(x.value2(), HistogramValueType.BYTES, null),
                    x.key().address()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, true, values)).build();
    }

    private Ipv6TransitionTunnelDetailsResponse buildTunnelDetails(Ipv6TransitionTunnelEntry t, UUID organizationId, UUID tenantId) {
        L4Type l4Type = t.transport().equals("UDP") ? L4Type.UDP : L4Type.NONE;

        return Ipv6TransitionTunnelDetailsResponse.create(
                RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, l4Type, t.client()),
                RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, l4Type, t.server()),
                t.sessionKey(),
                t.tunnelType(),
                t.transport(),
                t.details(),
                t.connectionStatus(),
                t.tunneledBytes(),
                t.establishedAt(),
                t.terminatedAt(),
                t.mostRecentSegmentTime(),
                t.durationMs()
        );
    }

}
