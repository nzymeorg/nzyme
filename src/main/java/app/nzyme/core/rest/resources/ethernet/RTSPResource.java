package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.StringNumberNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.rtsp.RTSP;
import app.nzyme.core.ethernet.rtsp.db.RTSPStreamEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import app.nzyme.core.rest.responses.ethernet.rtsp.RTSPStreamDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.rtsp.RTSPStreamsListResponse;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/rtsp")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "RTSP", description = "RTSP streams that Nzyme taps observed on the network. RTSP is what cameras, "
        + "network video recorders and streaming appliances use to negotiate live audio and video. Nzyme reads the "
        + "clear text control connection and links the media flow back to it, so a stream reports its negotiation "
        + "state, its authentication posture and the devices on both ends.")
public class RTSPResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    private static ObjectMapper OM = new ObjectMapper();

    @GET
    @Path("/streams")
    @Operation(operationId = "findRtspStreams", summary = "List RTSP streams",
            description = "Returns all RTSP streams observed in the time range, newest setup first by default. Each "
                    + "stream carries the control connection, the media transport addresses and the exchanged bytes, "
                    + "plus the negotiation state, the requested URI, the client and server software, the "
                    + "authentication posture and flags such as an unauthenticated stream. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Streams found.",
            content = @Content(schema = @Schema(implementation = RTSPStreamsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response allStreams(@Parameter(hidden = true) @Context SecurityContext sc,
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

        RTSP.OrderColumn orderColumn = RTSP.OrderColumn.SETUP_ESTABLISHED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = RTSP.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().rtsp().countAllStreams(timeRange, filters, taps);

        List<RTSPStreamDetailsResponse> streams = Lists.newArrayList();
        for (RTSPStreamEntry stream : nzyme.getEthernet().rtsp()
                .findAllStreams(timeRange, filters, orderColumn, orderDirection, limit, offset, taps)) {
            streams.add(buildDetailsResponse(stream, organizationId, tenantId));
        }

        return Response.ok(RTSPStreamsListResponse.create(total, streams)).build();
    }

    @GET
    @Path("/streams/show/{session_id}")
    @Operation(operationId = "findRtspStream", summary = "Get an RTSP stream",
            description = "Returns a single RTSP stream by the session key of the TCP control connection that set "
                    + "it up.")
    @ApiResponse(responseCode = "200", description = "Stream found.",
            content = @Content(schema = @Schema(implementation = RTSPStreamDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Stream not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response oneStream(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Session key of the TCP control connection of the stream.") @PathParam("session_id") String sessionId,
                              @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                              @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<RTSPStreamEntry> stream = nzyme.getEthernet().rtsp().findOneStream(sessionId, taps);

        if (stream.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(buildDetailsResponse(stream.get(), organizationId, tenantId)).build();
    }

    @GET
    @Path("/streams/active/histogram")
    @Operation(operationId = "findRtspActiveStreamsHistogram", summary = "Get histogram of active RTSP streams",
            description = "Returns the number of RTSP streams that were active in each bucket of the time range. "
                    + "The bucket size is chosen automatically based on the length of the time range.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = NumericHistogramResponse.class)))
    public Response activeStreamsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                           @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                           @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Map<DateTime, Integer> buckets = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().rtsp()
                .getActiveStreamsHistogram(timeRange, bucketing, filters, tapUUIDs)) {
            buckets.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(NumericHistogramResponse.create(buckets, bucketing.bucketSizeMs())).build();
    }

    @GET
    @Path("/streams/servers/top/histogram")
    @Operation(operationId = "findRtspTopServers", summary = "List top RTSP servers",
            description = "Returns the server addresses and ports with the most RTSP streams in the time range, "
                    + "together with the stream count and the number of exchanged bytes.")
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

        long count = nzyme.getEthernet().rtsp().getTopServersCount(timeRange, filters, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (L4AddressDataAddressNumberNumberAggregationResult x : nzyme.getEthernet().rtsp()
                .getTopServers(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.TCP, x.key()),
                            HistogramValueType.L4_ADDRESS,
                            null),
                    HistogramValueStructureResponse.create(x.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(x.value2(), HistogramValueType.BYTES, null),
                    x.key().address()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, false, values)).build();
    }

    @GET
    @Path("/streams/clients/top/histogram")
    @Operation(operationId = "findRtspTopClients", summary = "List top RTSP clients",
            description = "Returns the client addresses with the most RTSP streams in the time range, together with "
                    + "the stream count and the number of exchanged bytes.")
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

        long count = nzyme.getEthernet().rtsp().getTopClientsCount(timeRange, filters, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (L4AddressDataAddressNumberNumberAggregationResult x : nzyme.getEthernet().rtsp()
                .getTopClients(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.TCP, x.key()),
                            HistogramValueType.L4_ADDRESS_NO_PORT,
                            null),
                    HistogramValueStructureResponse.create(x.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(x.value2(), HistogramValueType.BYTES, null),
                    x.key().address()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, false, values)).build();
    }

    private RTSPStreamDetailsResponse buildDetailsResponse(RTSPStreamEntry stream, UUID organizationId, UUID tenantId) {
        Map<String, Object> mediaLocatorResponse = null;
        if (stream.mediaLocator() != null && !stream.mediaLocator().isEmpty()) {
            mediaLocatorResponse = OM.readValue(stream.mediaLocator(), new TypeReference<>() {});
        }

        L4AddressResponse setupSource = null;
        L4AddressResponse setupDestination = null;
        L4AddressResponse streamSource = null;
        L4AddressResponse streamDestination = null;

        if (stream.setupSource() != null) {
            setupSource = RestHelpers.L4AddressDataToResponse(
                    nzyme, organizationId, tenantId, L4Type.TCP, stream.setupSource()
            );
        }

        if (stream.setupDestination() != null) {
            setupDestination = RestHelpers.L4AddressDataToResponse(
                    nzyme, organizationId, tenantId, L4Type.TCP, stream.setupDestination()
            );
        }

        if (stream.streamSource() != null) {
            streamSource = RestHelpers.L4AddressDataToResponse(
                    nzyme, organizationId, tenantId, L4Type.TCP, stream.streamSource()
            );
        }

        if (stream.streamDestination() != null) {
            streamDestination = RestHelpers.L4AddressDataToResponse(
                    nzyme, organizationId, tenantId, L4Type.TCP, stream.streamDestination()
            );
        }
        return RTSPStreamDetailsResponse.create(
                stream.setupTcpSessionKey(),
                stream.isActive(),
                stream.state(),
                mediaLocatorResponse,
                stream.requestUri(),
                stream.clientAgent(),
                stream.serverInfo(),
                stream.authentication(),
                stream.flags(),
                stream.lastActivity(),
                stream.durationMs(),
                stream.setupConnectionStatus(),
                stream.setupEstablishedAt(),
                stream.setupTerminatedAt(),
                stream.setupMostRecentSegmentTime(),
                setupSource,
                setupDestination,
                stream.setupBytesExchanged(),
                stream.streamL4Type(),
                streamSource,
                streamDestination,
                stream.streamBytesRx(),
                stream.streamBytesTx()
        );
    }

}