package app.nzyme.core.rest.resources.dot11;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.context.db.MacAddressContextEntry;
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
import app.nzyme.core.dot11.db.monitoring.MonitoredBSSID;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.dot11.monitoring.disco.db.Dot11DiscoMonitorMethodConfiguration;
import app.nzyme.core.dot11.monitoring.disco.monitormethods.DiscoMonitorFactory;
import app.nzyme.core.dot11.monitoring.disco.monitormethods.DiscoMonitorMethodType;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.SimulateDiscoDetectionConfigRequest;
import app.nzyme.core.rest.requests.UpdateDiscoDetectionConfigRequest;
import app.nzyme.core.rest.responses.dot11.Dot11MacAddressContextResponse;
import app.nzyme.core.rest.responses.dot11.Dot11MacAddressResponse;
import app.nzyme.core.rest.responses.dot11.Dot11MacLinkMetadataResponse;
import app.nzyme.core.rest.responses.dot11.disco.DiscoFrameDetailsResponse;
import app.nzyme.core.rest.responses.dot11.disco.DiscoFrameListResponse;
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
import com.google.common.base.Splitter;
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
import jakarta.inject.Inject;
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

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/dot11/disco")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Disconnection Monitoring", description = "Nzyme calls deauthentication and disassociation frames "
        + "disconnection frames, or disco frames. They are a common part of WiFi attacks that kick clients off a "
        + "network. These endpoints show the disconnection activity your taps recorded and manage the disconnection "
        + "monitor of a monitored network.",
        externalDocs = @ExternalDocumentation(description = "Disconnection activity in the Nzyme documentation",
                url = "https://go.nzyme.org/disco"))
public class Dot11DiscoResource extends TapDataHandlingResource {

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

    @Inject
    private NzymeNode nzyme;

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
                              @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                              @Parameter(description = BSSIDS_DESCRIPTION) @QueryParam("bssids") @Nullable String bssidsParam,
                              @Parameter(description = MONITORED_NETWORK_DESCRIPTION) @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);
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
                           @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                           @Parameter(description = BSSIDS_DESCRIPTION) @QueryParam("bssids") @Nullable String bssidsParam,
                           @Parameter(description = MONITORED_NETWORK_DESCRIPTION) @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId,
                           @Parameter(description = "Sorting column. Omit to sort by time.") @QueryParam("order_column") @Nullable String orderColumnParam,
                           @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                           @Parameter(description = "Page size. Cannot exceed 250.") @QueryParam("limit") int limit,
                           @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

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
                    Dot11ReasonCodes.name(f.reasonCode()),
                    f.frequency(),
                    f.signalStrengthMin(),
                    f.signalStrengthMax(),
                    f.signalStrengthAverage(),
                    f.frameCount()
            ));
        }

        return Response.ok(DiscoFrameListResponse.create(total, frames)).build();
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
                                 @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                 @Parameter(description = BSSIDS_DESCRIPTION) @QueryParam("bssids") @Nullable String bssidsParam,
                                 @Parameter(description = MONITORED_NETWORK_DESCRIPTION) @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId,
                                 @Parameter(description = "Sorting column, KEY or VALUE. Defaults to the frame count.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                 @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                 @Parameter(description = "Page size. Cannot exceed 250.") @QueryParam("limit") int limit,
                                 @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

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
                          @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                          @Parameter(description = BSSIDS_DESCRIPTION) @QueryParam("bssids") @Nullable String bssidsParam,
                          @Parameter(description = MONITORED_NETWORK_DESCRIPTION) @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId,
                          @Parameter(description = "Sorting column, VALUE1, VALUE2 or VALUE3. Defaults to the frame count.") @QueryParam("order_column") @Nullable String orderColumnParam,
                          @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                          @Parameter(description = "Page size. Cannot exceed 250.") @QueryParam("limit") int limit,
                          @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

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
                                @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                @Parameter(description = BSSIDS_DESCRIPTION) @QueryParam("bssids") @Nullable String bssidsParam,
                                @Parameter(description = MONITORED_NETWORK_DESCRIPTION) @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId,
                                @Parameter(description = "Sorting column, KEY or VALUE. Defaults to the frame count.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                @Parameter(description = "Page size. Cannot exceed 250.") @QueryParam("limit") int limit,
                                @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

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
                                @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                @Parameter(description = BSSIDS_DESCRIPTION) @QueryParam("bssids") @Nullable String bssidsParam,
                                @Parameter(description = MONITORED_NETWORK_DESCRIPTION) @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId,
                                @Parameter(description = "Sorting column, KEY or VALUE. Defaults to the frame count.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                @Parameter(description = "Page size. Cannot exceed 250.") @QueryParam("limit") int limit,
                                @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

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

    private record BssidSelection(@Nullable List<String> bssids, @Nullable Response error) { }

    private BssidSelection resolveBssids(AuthenticatedUser authenticatedUser,
                                         @Nullable String bssidsParam,
                                         @Nullable UUID monitoredNetworkId) {
        List<String> bssids = null;

        if (bssidsParam != null && !bssidsParam.isEmpty()) {
            bssids = Splitter.on(",").omitEmptyStrings().trimResults().splitToList(bssidsParam)
                    .stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.toList());
        }

        if (monitoredNetworkId != null) {
            Optional<MonitoredSSID> monitoredNetwork = nzyme.getDot11().findMonitoredSSID(monitoredNetworkId);
            if (monitoredNetwork.isEmpty()) {
                return new BssidSelection(null, Response.status(Response.Status.NOT_FOUND).build());
            }

            if (!entityAccessible(authenticatedUser, monitoredNetwork.get())) {
                return new BssidSelection(null, Response.status(Response.Status.UNAUTHORIZED).build());
            }

            bssids = nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(monitoredNetwork.get().id())
                    .stream()
                    .map(MonitoredBSSID::bssid)
                    .collect(Collectors.toList());
        }

        return new BssidSelection(bssids, null);
    }

    private Dot11MacLinkMetadataResponse buildMacResponse(String mac,
                                                          List<UUID> tapUuids,
                                                          UUID organizationId,
                                                          UUID tenantId) {
        Optional<MacAddressContextEntry> macContext = nzyme.getContextService()
                .findMacAddressContext(mac, organizationId, tenantId);

        return Dot11MacLinkMetadataResponse.create(
                nzyme.getDot11().getMacAddressMetadata(mac, tapUuids).type(),
                Dot11MacAddressResponse.create(
                        mac,
                        nzyme.getOuiService().lookup(mac).orElse(null),
                        null,
                        macContext.map(c -> Dot11MacAddressContextResponse.create(c.name(), c.description(), c.notes()))
                                .orElse(null)
                )
        );
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
