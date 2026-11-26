package app.nzyme.core.rest.resources.dot11;

import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.db.Dot11MacFrameCount;
import app.nzyme.core.dot11.db.BSSIDPairFrameCount;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.ThreeColumnHistogramOrderColumn;
import app.nzyme.core.database.generic.TwoColumnHistogramOrderColumn;
import app.nzyme.core.dot11.Dot11ReasonCodes;
import app.nzyme.core.dot11.db.DiscoFrameEntry;
import app.nzyme.core.dot11.db.DiscoFrequencyCount;
import app.nzyme.core.dot11.db.DiscoHistogramEntry;
import app.nzyme.core.dot11.db.DiscoReasonCodeCount;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.dot11.monitoring.disco.db.Dot11DiscoMonitorMethodConfiguration;
import app.nzyme.core.dot11.monitoring.disco.monitormethods.DiscoMonitorFactory;
import app.nzyme.core.dot11.monitoring.disco.monitormethods.DiscoMonitorMethodType;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.SimulateDiscoDetectionConfigRequest;
import app.nzyme.core.rest.requests.UpdateDiscoDetectionConfigRequest;
import app.nzyme.core.dot11.signal.FrameSource;
import app.nzyme.core.rest.responses.dot11.disco.DiscoFrameDetailsResponse;
import app.nzyme.core.rest.responses.dot11.disco.DiscoFrameListResponse;
import app.nzyme.core.rest.responses.dot11.signal.FrameSignalAnalysisResponse;
import app.nzyme.core.rest.responses.dot11.signal.FrameSignalChartsResponse;
import app.nzyme.core.rest.responses.dot11.signal.FrameSignalTransmitterListResponse;
import app.nzyme.core.rest.responses.dot11.disco.DiscoHistogramValueResponse;
import app.nzyme.core.rest.responses.dot11.disco.DiscoMonitorMethodConfigurationResponse;
import app.nzyme.core.rest.responses.dot11.disco.Dot11DiscoMonitorAnomalyDetailsResponse;
import app.nzyme.core.rest.responses.dot11.disco.Dot11DiscoMonitorAnomalyListResponse;
import app.nzyme.core.rest.responses.shared.*;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import tools.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;


@Path("/api/dot11/disco")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Disconnection Monitoring", description = "Nzyme calls deauthentication and disassociation frames "
        + "disconnection frames, or disco frames. They are a common part of WiFi attacks that kick clients off a "
        + "network. These endpoints show the disconnection activity your taps recorded and manage the disconnection "
        + "monitor of a monitored network.",
        externalDocs = @ExternalDocumentation(description = "Disconnection activity in the Nzyme documentation",
                url = "https://go.nzyme.org/disco"))
public class Dot11DiscoResource extends Dot11BssidScopedResource {

    private static final Logger LOG = LogManager.getLogger(TapDataHandlingResource.class);

    private enum MacHistogramType {
        TRANSMITTERS(Dot11.DiscoMacDimension.TRANSMITTER),
        RECEIVERS(Dot11.DiscoMacDimension.RECEIVER),
        BSSIDS(Dot11.DiscoMacDimension.BSSID);

        private final Dot11.DiscoMacDimension dimension;

        MacHistogramType(Dot11.DiscoMacDimension dimension) {
            this.dimension = dimension;
        }
    }

    private static final String FILTERS_DESCRIPTION = "JSON encoded filter definition as produced by the web interface filter builder.";
    private static final String BSSIDS_DESCRIPTION = "Comma separated list of BSSIDs. Narrows the result to frames one of them transmitted, received or refers to as BSSID. Ignored if a monitored network is passed.";
    private static final String MONITORED_NETWORK_DESCRIPTION = "UUID of a monitored network. Narrows the result to frames of all BSSIDs of that network.";

    @GET
    @Path("/histogram")
    @Operation(operationId = "findDiscoHistogram", summary = "Get disconnection frame histogram",
            description = "Returns an object keyed by bucket timestamp with the number of disconnection frames in each "
                    + "bucket, split into deauthentication and disassociation frames and with the number of frames "
                    + "sent to the broadcast address. Narrow the result with filters, a set of BSSIDs, or the "
                    + "BSSIDs of a monitored network. A monitored network takes precedence over the BSSID list.")
    @ApiResponse(responseCode = "200", description = "Histogram found. The response is an object keyed by bucket timestamp.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "401", description = "The monitored network is not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found.", content = @Content)
    public Response histogram(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                              @Parameter(description = FILTERS_DESCRIPTION) @QueryParam("filters") String filtersParameter,
                              @Parameter(description = "Comma separated list of tap UUIDs to include. Use * for all taps the user can access. Without the parameter, no taps are selected and the result is empty.") @QueryParam("taps") String taps,
                              @Parameter(description = BSSIDS_DESCRIPTION) @QueryParam("bssids") @Nullable String bssidsParam,
                              @Parameter(description = MONITORED_NETWORK_DESCRIPTION) @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        BssidSelection bssids = resolveBssids(authenticatedUser, bssidsParam, monitoredNetworkId);
        if (bssids.error() != null) {
            return bssids.error();
        }

        Map<DateTime, DiscoHistogramValueResponse> response = Maps.newTreeMap();
        for (DiscoHistogramEntry h : nzyme.getDot11()
                .getDiscoHistogram(timeRange, bucketing, tapUuids, filters, bssids.bssids())) {
            response.put(h.bucket(), DiscoHistogramValueResponse.create(
                    h.bucket(),
                    h.frameCount(),
                    h.deauthenticationCount(),
                    h.disassociationCount(),
                    h.broadcastCount()
            ));
        }

        return Response.ok(response).build();
    }

    @GET
    @Path("/frames")
    @Operation(operationId = "findDiscoFrames", summary = "List disconnection frames",
            description = "Returns the recorded disconnection frames. Taps aggregate frames that share type, "
                    + "transmitter, receiver, BSSID, reason code and channel within a report interval into one record "
                    + "with a frame count. Sorts by time descending unless you pass a sorting column and direction.")
    @ApiResponse(responseCode = "200", description = "Frames found.",
            content = @Content(schema = @Schema(implementation = DiscoFrameListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction, or page size above 250.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The monitored network is not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization, tenant or monitored network not found, or not accessible by the calling user.", content = @Content)
    public Response frames(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                           @Parameter(description = FILTERS_DESCRIPTION) @QueryParam("filters") String filtersParameter,
                           @Parameter(description = "Comma separated list of tap UUIDs to include. Use * for all taps the user can access. Without the parameter, no taps are selected and the result is empty.") @QueryParam("taps") String taps,
                           @Parameter(description = BSSIDS_DESCRIPTION) @QueryParam("bssids") @Nullable String bssidsParam,
                           @Parameter(description = MONITORED_NETWORK_DESCRIPTION) @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId,
                           @Parameter(description = "Sorting column. Omit to sort by time.") @QueryParam("order_column") @Nullable String orderColumnParam,
                           @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                           @Parameter(description = "Page size. Cannot exceed 250.") @QueryParam("limit") int limit,
                           @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (limit > 250) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Dot11.DiscoFrameOrderColumn orderColumn = Dot11.DiscoFrameOrderColumn.CREATED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Dot11.DiscoFrameOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        BssidSelection bssids = resolveBssids(authenticatedUser, bssidsParam, monitoredNetworkId);
        if (bssids.error() != null) {
            return bssids.error();
        }

        long total = nzyme.getDot11().countDiscoFrames(timeRange, filters, bssids.bssids(), tapUuids);

        Map<UUID, String> tapNames = Maps.newHashMap();
        List<DiscoFrameDetailsResponse> frames = Lists.newArrayList();
        for (DiscoFrameEntry f : nzyme.getDot11().findDiscoFrames(
                timeRange, filters, bssids.bssids(), limit, offset, orderColumn, orderDirection, tapUuids)) {
            String tapName = tapNames.computeIfAbsent(f.tapUuid(), uuid ->
                    nzyme.getTapManager().findTap(uuid).map(Tap::name).orElse(null));

            frames.add(DiscoFrameDetailsResponse.create(
                    f.id(),
                    f.tapUuid(),
                    tapName,
                    f.createdAt(),
                    f.discoType(),
                    buildMacResponse(f.transmitter(), tapUuids, organizationId, tenantId),
                    buildMacResponse(f.receiver(), tapUuids, organizationId, tenantId),
                    buildMacResponse(f.bssid(), tapUuids, organizationId, tenantId),
                    Dot11.BROADCAST_MAC.equals(f.receiver()),
                    f.transmitter().equals(f.bssid()),
                    f.receiver().equals(f.bssid()),
                    f.reasonCode(),
                    f.reasonCode() == null ? null : Dot11ReasonCodes.name(f.reasonCode()),
                    f.isProtected(),
                    f.frequency(),
                    f.signalStrengthMin(),
                    f.signalStrengthMax(),
                    f.signalStrengthAverage(),
                    f.frameCount(),
                    f.retryFrames(),
                    f.pairSequenceNumbers()
            ));
        }

        return Response.ok(DiscoFrameListResponse.create(total, frames)).build();
    }

    @GET
    @Path("/signal")
    @Operation(operationId = "findFrameSignalAnalysis", summary = "Compare the signal strength of disconnection frames with their transmitters",
            description = "Compares the signal strength of the recorded disconnection frames with the signal "
                    + "strength of the stations they name as transmitter. The filters have to leave one or two "
                    + "transmitters; with more, only their number is returned. A tap records a station at "
                    + "another strength on every frequency it tunes to, so there is one finding per tap, "
                    + "frequency and transmitter. Findings are sorted by importance: those with mismatches "
                    + "first, then those that were compared by their largest difference, then those that could "
                    + "not be compared. They are paginated, while the totals of the transmitters cover all of "
                    + "them. An access point is compared with its beacons and probe responses on the same tap "
                    + "and frequency, in the minute a frame was reported in and the five minutes before, which "
                    + "is what the signal strength mismatch detection uses. A client is compared with the "
                    + "nearest data frames, probe requests and connection frames it sent on the same tap and "
                    + "frequency, at most 15 minutes away. A frame is a mismatch when it differs by the "
                    + "configured threshold or more. The latest 25,000 frame records are evaluated.")
    @ApiResponse(responseCode = "200", description = "Analysis found.",
            content = @Content(schema = @Schema(implementation = FrameSignalAnalysisResponse.class)))
    @ApiResponse(responseCode = "400", description = "Page size too large.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = SIGNAL_TOO_MUCH_DATA, content = @Content)
    public Response signal(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                           @Parameter(description = FILTERS_DESCRIPTION) @QueryParam("filters") String filtersParameter,
                           @Parameter(description = SIGNAL_TAPS_DESCRIPTION) @QueryParam("taps") String taps,
                           @Parameter(description = "Page size of the findings, 25 if omitted. Cannot exceed 100.") @QueryParam("limit") int limit,
                           @Parameter(description = "Page offset of the findings.") @QueryParam("offset") int offset) {
        return signalAnalysis(FrameSource.DISCONNECTION, sc, organizationId, tenantId, timeRangeParameter,
                filtersParameter, taps, limit, offset);
    }

    @GET
    @Path("/signal/charts")
    @Operation(operationId = "findFrameSignalCharts", summary = "Chart the signal strength of disconnection frames next to their transmitters",
            description = "Returns the disconnection frames of up to 10 combinations of tap and frequency with "
                    + "their signal strength, next to the signal strength of what their transmitters are "
                    + "compared with, for charting both over time. Without a selection, the 6 combinations of "
                    + "the most important findings are returned. Each chart has at most 1,000 frame records, "
                    + "and every record carries the signal strength it was compared with and a histogram of "
                    + "the signal strength of every single frame if the tap reported one. The filters have to "
                    + "leave one or two transmitters, as for the findings. The latest 25,000 frame records of "
                    + "the selected charts are looked at, and a chart keeps the oldest 1,000 of its records.")
    @ApiResponse(responseCode = "200", description = "Charts found.",
            content = @Content(schema = @Schema(implementation = FrameSignalChartsResponse.class)))
    @ApiResponse(responseCode = "400", description = "Malformed selection, or more than 10 charts selected.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = SIGNAL_TOO_MUCH_DATA, content = @Content)
    public Response signalCharts(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                 @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                 @Parameter(description = FILTERS_DESCRIPTION) @QueryParam("filters") String filtersParameter,
                                 @Parameter(description = SIGNAL_TAPS_DESCRIPTION) @QueryParam("taps") String taps,
                                 @Parameter(description = "Comma separated selection of up to 10 charts, each a tap UUID and a frequency in MHz joined by a colon. Omit for the charts of the most important findings.") @QueryParam("charts") @Nullable String chartsParameter) {
        return signalCharts(FrameSource.DISCONNECTION, sc, organizationId, tenantId, timeRangeParameter,
                filtersParameter, taps, chartsParameter);
    }

    @GET
    @Path("/signal/transmitters")
    @Operation(operationId = "findFrameSignalTransmitters", summary = "List transmitters of disconnection frames for a signal strength comparison",
            description = "Returns the addresses named as transmitter of the most disconnection frames that were "
                    + "recorded with a signal strength. Every address comes with the number of its receivers, "
                    + "the time of its last frame and the number of frames its disconnection frames can be "
                    + "compared with: beacons and probe responses for an access point, data frames, probe "
                    + "requests and connection frames for a client, counted on the taps and frequencies its "
                    + "disconnection frames were recorded on. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Transmitters found.",
            content = @Content(schema = @Schema(implementation = FrameSignalTransmitterListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Page size too large.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = SIGNAL_TOO_MUCH_DATA, content = @Content)
    public Response signalTransmitters(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                       @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                       @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                       @Parameter(description = SIGNAL_TAPS_DESCRIPTION) @QueryParam("taps") String taps,
                                       @Parameter(description = "Page size, 10 if omitted. Cannot exceed 25.") @QueryParam("limit") int limit,
                                       @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        return signalTransmitters(FrameSource.DISCONNECTION, sc, organizationId, tenantId, timeRangeParameter, taps,
                limit, offset);
    }

    @GET
    @Path("/histograms/macs/{histogram_type}")
    @Operation(operationId = "findDiscoMacHistogram", summary = "List top disconnection frame addresses",
            description = "Returns the MAC addresses with the most disconnection frames in one of three roles: "
                    + "transmitters, receivers or BSSIDs. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = TwoColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown histogram type, sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The monitored network is not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization, tenant or monitored network not found, or not accessible by the calling user.", content = @Content)
    public Response macHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                 @Parameter(description = "Address role to count: transmitters, receivers or bssids.") @PathParam("histogram_type") @NotEmpty String histogramTypeParam,
                                 @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                 @Parameter(description = FILTERS_DESCRIPTION) @QueryParam("filters") String filtersParameter,
                                 @Parameter(description = "Comma separated list of tap UUIDs to include. Use * for all taps the user can access. Without the parameter, no taps are selected and the result is empty.") @QueryParam("taps") String taps,
                                 @Parameter(description = BSSIDS_DESCRIPTION) @QueryParam("bssids") @Nullable String bssidsParam,
                                 @Parameter(description = MONITORED_NETWORK_DESCRIPTION) @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId,
                                 @Parameter(description = "Sorting column, KEY or VALUE. Defaults to the frame count.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                 @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                 @Parameter(description = "Page size. Cannot exceed 250.") @QueryParam("limit") int limit,
                                 @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (limit > 250) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        MacHistogramType histogramType;
        try {
            histogramType = MacHistogramType.valueOf(histogramTypeParam.toUpperCase());
        } catch(IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        TwoColumnHistogramOrderColumn orderColumn = TwoColumnHistogramOrderColumn.VALUE;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = TwoColumnHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        BssidSelection bssids = resolveBssids(authenticatedUser, bssidsParam, monitoredNetworkId);
        if (bssids.error() != null) {
            return bssids.error();
        }

        long total = nzyme.getDot11()
                .countDiscoMacHistogram(histogramType.dimension, timeRange, filters, bssids.bssids(), tapUuids);

        List<TwoColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (Dot11MacFrameCount s : nzyme.getDot11().getDiscoMacHistogram(histogramType.dimension, timeRange,
                filters, bssids.bssids(), limit, offset, orderColumn, orderDirection, tapUuids)) {
            values.add(TwoColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            s.mac(),
                            HistogramValueType.DOT11_MAC,
                            buildMacResponse(s.mac(), tapUuids, organizationId, tenantId)
                    ),
                    HistogramValueStructureResponse.create(s.frameCount(), HistogramValueType.INTEGER, null)
            ));
        }

        return Response.ok(TwoColumnTableHistogramResponse.create(total, true, values)).build();
    }

    @GET
    @Path("/histograms/pairs")
    @Operation(operationId = "findDiscoPairs", summary = "List top disconnection frame pairs",
            description = "Returns transmitter and receiver pairs with the number of disconnection frames exchanged "
                    + "between them, most active pair first by default. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Pairs found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The monitored network is not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization, tenant or monitored network not found, or not accessible by the calling user.", content = @Content)
    public Response pairs(@Parameter(hidden = true) @Context SecurityContext sc,
                          @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                          @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                          @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                          @Parameter(description = FILTERS_DESCRIPTION) @QueryParam("filters") String filtersParameter,
                          @Parameter(description = "Comma separated list of tap UUIDs to include. Use * for all taps the user can access. Without the parameter, no taps are selected and the result is empty.") @QueryParam("taps") String taps,
                          @Parameter(description = BSSIDS_DESCRIPTION) @QueryParam("bssids") @Nullable String bssidsParam,
                          @Parameter(description = MONITORED_NETWORK_DESCRIPTION) @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId,
                          @Parameter(description = "Sorting column, VALUE1, VALUE2 or VALUE3. Defaults to the frame count.") @QueryParam("order_column") @Nullable String orderColumnParam,
                          @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                          @Parameter(description = "Page size. Cannot exceed 250.") @QueryParam("limit") int limit,
                          @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (limit > 250) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        ThreeColumnHistogramOrderColumn orderColumn = ThreeColumnHistogramOrderColumn.VALUE3;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ThreeColumnHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        BssidSelection bssids = resolveBssids(authenticatedUser, bssidsParam, monitoredNetworkId);
        if (bssids.error() != null) {
            return bssids.error();
        }

        long total = nzyme.getDot11().countDiscoPairs(timeRange, filters, bssids.bssids(), tapUuids);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (BSSIDPairFrameCount s : nzyme.getDot11().getDiscoPairs(
                timeRange, filters, bssids.bssids(), limit, offset, orderColumn, orderDirection, tapUuids)) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            s.sender(),
                            HistogramValueType.DOT11_MAC,
                            buildMacResponse(s.sender(), tapUuids, organizationId, tenantId)
                    ),
                    HistogramValueStructureResponse.create(
                            s.receiver(),
                            HistogramValueType.DOT11_MAC,
                            buildMacResponse(s.receiver(), tapUuids, organizationId, tenantId)
                    ),
                    HistogramValueStructureResponse.create(s.frameCount(), HistogramValueType.INTEGER, null),
                    s.sender() + " ⇨ " + s.receiver()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(total, true, values)).build();
    }

    @GET
    @Path("/histograms/reason_codes")
    @Operation(operationId = "findDiscoReasonCodes", summary = "List top disconnection frame reason codes",
            description = "Returns the reason codes of the recorded disconnection frames with their names from the "
                    + "IEEE 802.11 standard and the number of frames that carried each code. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = TwoColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The monitored network is not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found.", content = @Content)
    public Response reasonCodes(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                @Parameter(description = FILTERS_DESCRIPTION) @QueryParam("filters") String filtersParameter,
                                @Parameter(description = "Comma separated list of tap UUIDs to include. Use * for all taps the user can access. Without the parameter, no taps are selected and the result is empty.") @QueryParam("taps") String taps,
                                @Parameter(description = BSSIDS_DESCRIPTION) @QueryParam("bssids") @Nullable String bssidsParam,
                                @Parameter(description = MONITORED_NETWORK_DESCRIPTION) @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId,
                                @Parameter(description = "Sorting column, KEY or VALUE. Defaults to the frame count.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                @Parameter(description = "Page size. Cannot exceed 250.") @QueryParam("limit") int limit,
                                @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

        if (limit > 250) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        TwoColumnHistogramOrderColumn orderColumn = TwoColumnHistogramOrderColumn.VALUE;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = TwoColumnHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        BssidSelection bssids = resolveBssids(authenticatedUser, bssidsParam, monitoredNetworkId);
        if (bssids.error() != null) {
            return bssids.error();
        }

        long total = nzyme.getDot11().countDiscoReasonCodes(timeRange, filters, bssids.bssids(), tapUuids);

        List<TwoColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (DiscoReasonCodeCount s : nzyme.getDot11().getDiscoReasonCodeHistogram(
                timeRange, filters, bssids.bssids(), limit, offset, orderColumn, orderDirection, tapUuids)) {
            Map<String, Object> metadata = Maps.newHashMap();
            metadata.put("reason_code", s.reasonCode());

            values.add(TwoColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            s.reasonCode() + " (" + Dot11ReasonCodes.name(s.reasonCode()) + ")",
                            HistogramValueType.GENERIC,
                            metadata
                    ),
                    HistogramValueStructureResponse.create(s.frameCount(), HistogramValueType.INTEGER, null)
            ));
        }

        return Response.ok(TwoColumnTableHistogramResponse.create(total, true, values)).build();
    }

    @GET
    @Path("/histograms/frequencies")
    @Operation(operationId = "findDiscoFrequencies", summary = "List top disconnection frame frequencies",
            description = "Returns the frequencies the recorded disconnection frames were seen on, in MHz, with the "
                    + "number of frames on each. Frames without channel information are grouped under a null "
                    + "frequency. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = TwoColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The monitored network is not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found.", content = @Content)
    public Response frequencies(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                @Parameter(description = FILTERS_DESCRIPTION) @QueryParam("filters") String filtersParameter,
                                @Parameter(description = "Comma separated list of tap UUIDs to include. Use * for all taps the user can access. Without the parameter, no taps are selected and the result is empty.") @QueryParam("taps") String taps,
                                @Parameter(description = BSSIDS_DESCRIPTION) @QueryParam("bssids") @Nullable String bssidsParam,
                                @Parameter(description = MONITORED_NETWORK_DESCRIPTION) @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId,
                                @Parameter(description = "Sorting column, KEY or VALUE. Defaults to the frame count.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                @Parameter(description = "Page size. Cannot exceed 250.") @QueryParam("limit") int limit,
                                @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

        if (limit > 250) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        TwoColumnHistogramOrderColumn orderColumn = TwoColumnHistogramOrderColumn.VALUE;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = TwoColumnHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        BssidSelection bssids = resolveBssids(authenticatedUser, bssidsParam, monitoredNetworkId);
        if (bssids.error() != null) {
            return bssids.error();
        }

        long total = nzyme.getDot11().countDiscoFrequencies(timeRange, filters, bssids.bssids(), tapUuids);

        List<TwoColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (DiscoFrequencyCount s : nzyme.getDot11().getDiscoFrequencyHistogram(
                timeRange, filters, bssids.bssids(), limit, offset, orderColumn, orderDirection, tapUuids)) {
            Map<String, Object> metadata = Maps.newHashMap();
            metadata.put("frequency", s.frequency());

            values.add(TwoColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            s.frequency() == null ? "Unknown" : s.frequency() + " MHz",
                            HistogramValueType.GENERIC,
                            metadata
                    ),
                    HistogramValueStructureResponse.create(s.frameCount(), HistogramValueType.INTEGER, null)
            ));
        }

        return Response.ok(TwoColumnTableHistogramResponse.create(total, true, values)).build();
    }

    @GET
    @Path("/config/detection")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Operation(operationId = "findDiscoDetectionConfiguration", summary = "Get disconnection monitor configuration",
            description = "Returns the detection method and its configuration that the disconnection monitor of a "
                    + "monitored network uses. The monitor counts disconnection frames sent to or from the expected "
                    + "BSSIDs of the network and alerts on anomalies. Requires the dot11_monitoring_manage feature "
                    + "permission.",
            externalDocs = @ExternalDocumentation(description = "Disconnection anomalies in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-network-monitoring-disco-anomalies"))
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = DiscoMonitorMethodConfigurationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response getDetectionConfig(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @Parameter(description = "Monitored network UUID.") @QueryParam("monitored_network_id") @NotNull UUID monitoredNetworkId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> monitoredNetwork = nzyme.getDot11().findMonitoredSSID(monitoredNetworkId);

        if (monitoredNetwork.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, monitoredNetwork.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Dot11DiscoMonitorMethodConfiguration config = nzyme.getDot11().getDiscoMonitorMethodConfiguration(
                monitoredNetwork.get().id());

        return Response.ok(DiscoMonitorMethodConfigurationResponse.create(config.type(), config.configuration()))
                .build();
    }

    @PUT
    @Path("/config/detection")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Operation(operationId = "updateDiscoDetectionConfiguration", summary = "Update disconnection monitor configuration",
            description = "Sets the detection method and its configuration for the disconnection monitor of a monitored "
                    + "network. The configuration is stored as JSON and its fields depend on the method type, for "
                    + "example a frame count threshold for the static threshold method. Requires the "
                    + "dot11_monitoring_manage feature permission.",
            externalDocs = @ExternalDocumentation(description = "Disconnection anomalies in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-network-monitoring-disco-anomalies"))
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The configuration could not be serialized to JSON.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response setDetectionConfig(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @RequestBody(description = "Monitored network UUID, detection method type and "
                                               + "the configuration of that method.", required = true, content = @Content(mediaType = "application/json"))
                                       @Valid UpdateDiscoDetectionConfigRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> monitoredNetwork = nzyme.getDot11().findMonitoredSSID(req.monitoredNetworkId());

        if (monitoredNetwork.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, monitoredNetwork.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String configurationJson;
        try {
            ObjectMapper om = new ObjectMapper();
            configurationJson = om.writeValueAsString(req.configuration());
        } catch(Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        nzyme.getDot11().setDiscoMonitorMethodConfiguration(
                req.methodType(), configurationJson, monitoredNetwork.get().id()
        );

        return Response.ok().build();
    }

    @POST
    @Path("/config/detection/simulate")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Operation(operationId = "simulateDiscoDetectionConfiguration", summary = "Simulate disconnection monitor configuration",
            description = "Runs a detection method with the passed configuration against the recorded data of one tap "
                    + "and returns the anomalies it would have raised. Nothing is stored and no alerts are created. "
                    + "Use it to find a threshold that fits your environment. Requires the dot11_monitoring_manage "
                    + "feature permission.",
            externalDocs = @ExternalDocumentation(description = "Disconnection anomalies in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-network-monitoring-disco-anomalies"))
    @ApiResponse(responseCode = "200", description = "Simulation completed.",
            content = @Content(schema = @Schema(implementation = Dot11DiscoMonitorAnomalyListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown detection method type.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network or tap not found, or not accessible by the calling user.", content = @Content)
    public Response simulateDetectionConfig(@Parameter(hidden = true) @Context SecurityContext sc,
                                            @RequestBody(description = "Monitored network UUID, tap UUID, detection "
                                                    + "method type and the configuration to simulate.", required = true, content = @Content(mediaType = "application/json"))
                                            @Valid SimulateDiscoDetectionConfigRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> monitoredNetwork = nzyme.getDot11().findMonitoredSSID(req.monitoredNetworkId());

        if (monitoredNetwork.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, monitoredNetwork.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Tap> tap = nzyme.getTapManager().findTap(req.tapId());
        if (tap.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getTapManager().allTapUUIDsAccessibleByUser(authenticatedUser).contains(tap.get().uuid())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DiscoMonitorMethodType type;
        try {
            type = DiscoMonitorMethodType.valueOf(req.methodType());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        List<Dot11DiscoMonitorAnomalyDetailsResponse> anomalies = DiscoMonitorFactory
                .build(nzyme, type, monitoredNetwork.get(), req.configuration())
                .execute(tap.get())
                .stream()
                .map(a -> Dot11DiscoMonitorAnomalyDetailsResponse.create(a.timestamp(), a.frameCount()))
                .collect(Collectors.toList());

        return Response.ok(Dot11DiscoMonitorAnomalyListResponse.create(anomalies.size(), anomalies)).build();
    }

}
