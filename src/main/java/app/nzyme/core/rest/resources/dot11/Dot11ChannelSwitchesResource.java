package app.nzyme.core.rest.resources.dot11;

import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.TwoColumnHistogramOrderColumn;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.channelswitches.ChannelSwitchMonitorRule;
import app.nzyme.core.dot11.channelswitches.ChannelSwitchRules;
import app.nzyme.core.dot11.db.ChannelSwitchChannelCount;
import app.nzyme.core.dot11.db.ChannelSwitchEntry;
import app.nzyme.core.dot11.db.ChannelSwitchHistogramEntry;
import app.nzyme.core.dot11.db.Dot11MacFrameCount;
import app.nzyme.core.dot11.db.monitoring.MonitoredBSSID;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.responses.dot11.Dot11MacLinkMetadataResponse;
import app.nzyme.core.rest.responses.dot11.channelswitches.ChannelSwitchDetailsResponse;
import app.nzyme.core.rest.responses.dot11.channelswitches.ChannelSwitchHistogramValueResponse;
import app.nzyme.core.rest.responses.dot11.channelswitches.ChannelSwitchListResponse;
import app.nzyme.core.rest.responses.dot11.channelswitches.ChannelSwitchMonitorHistogramResponse;
import app.nzyme.core.rest.responses.dot11.channelswitches.ChannelSwitchMonitorHistogramValueResponse;
import app.nzyme.core.rest.responses.shared.HistogramValueStructureResponse;
import app.nzyme.core.rest.responses.shared.HistogramValueType;
import app.nzyme.core.rest.responses.shared.TwoColumnTableHistogramResponse;
import app.nzyme.core.rest.responses.shared.TwoColumnTableHistogramValueResponse;
import app.nzyme.core.taps.Tap;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/api/dot11/channelswitches")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Channel Switch Announcements", description = "Access points announce a change of channel to their "
        + "clients in beacons, probe responses and action frames. Forged announcements move or disconnect clients "
        + "of networks that protect their management frames. These endpoints show the announcements your taps "
        + "recorded, the anomalies Nzyme found in them and whether the access point followed its announcement.")
public class Dot11ChannelSwitchesResource extends Dot11BssidScopedResource {

    private static final String TIME_RANGE_DESCRIPTION = "Time range selector. Accepts the same format as the web interface time range picker.";
    private static final String FILTERS_DESCRIPTION = "JSON encoded filter definition as produced by the web interface filter builder.";
    private static final String TAPS_DESCRIPTION = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.";
    private static final String BSSIDS_DESCRIPTION = "Comma separated list of BSSIDs. Narrows the result to announcements of these BSSIDs. Ignored if a monitored network is passed.";
    private static final String MONITORED_NETWORK_DESCRIPTION = "UUID of a monitored network. Narrows the result to announcements of all BSSIDs of that network.";

    private static final String NETWORK_TAPS_DESCRIPTION = "Comma separated list of tap UUIDs to include. Omit for all taps of the network the user can access.";

    private static final int MAX_PAGE_SIZE = 250;

    @GET
    @Path("/histogram")
    @Operation(operationId = "findChannelSwitchHistogram", summary = "Get channel switch announcement histogram",
            description = "Returns an object keyed by bucket timestamp with the number of frames that announced a "
                    + "channel switch in each bucket and how many of them were part of an announcement with "
                    + "anomalies. Narrow the result with filters, a set of BSSIDs, or the BSSIDs of a monitored "
                    + "network. A monitored network takes precedence over the BSSID list.")
    @ApiResponse(responseCode = "200", description = "Histogram found. The response is an object keyed by bucket timestamp.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "401", description = "The monitored network is not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found.", content = @Content)
    public Response histogram(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = TIME_RANGE_DESCRIPTION) @QueryParam("time_range") @Valid String timeRangeParameter,
                              @Parameter(description = FILTERS_DESCRIPTION) @QueryParam("filters") String filtersParameter,
                              @Parameter(description = TAPS_DESCRIPTION) @QueryParam("taps") String taps,
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

        Map<DateTime, ChannelSwitchHistogramValueResponse> response = Maps.newTreeMap();
        for (ChannelSwitchHistogramEntry h : nzyme.getDot11()
                .getChannelSwitchHistogram(timeRange, bucketing, tapUuids, filters, bssids.bssids())) {
            response.put(h.bucket(), ChannelSwitchHistogramValueResponse.create(
                    h.bucket(), h.frameCount(), h.anomalousFrameCount()
            ));
        }

        return Response.ok(response).build();
    }

    @GET
    @Path("/announcements")
    @Operation(operationId = "findChannelSwitches", summary = "List channel switch announcements",
            description = "Returns the recorded channel switch announcements. Taps aggregate frames that share "
                    + "BSSID, frame type, switch mode, announced channel and the channel they were recorded on within "
                    + "a report interval into one record with a frame count. The outcome is empty until Nzyme judged "
                    + "it, which takes about six minutes. Sorts by time descending unless you pass a sorting column "
                    + "and direction.")
    @ApiResponse(responseCode = "200", description = "Announcements found.",
            content = @Content(schema = @Schema(implementation = ChannelSwitchListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction, or page size above 250.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The monitored network is not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization, tenant or monitored network not found, or not accessible by the calling user.", content = @Content)
    public Response announcements(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                  @Parameter(description = TIME_RANGE_DESCRIPTION) @QueryParam("time_range") @Valid String timeRangeParameter,
                                  @Parameter(description = FILTERS_DESCRIPTION) @QueryParam("filters") String filtersParameter,
                                  @Parameter(description = TAPS_DESCRIPTION) @QueryParam("taps") String taps,
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

        ChannelSwitchOrder order = parseOrder(orderColumnParam, orderDirectionParam);
        if (order == null || limit < 0 || limit > MAX_PAGE_SIZE || offset < 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        BssidSelection bssids = resolveBssids(authenticatedUser, bssidsParam, monitoredNetworkId);
        if (bssids.error() != null) {
            return bssids.error();
        }

        long total = nzyme.getDot11().countChannelSwitches(timeRange, filters, bssids.bssids(), tapUuids);

        List<ChannelSwitchEntry> announcements = nzyme.getDot11().findChannelSwitches(
                timeRange, filters, bssids.bssids(), limit, offset, order.column(), order.direction(), tapUuids);

        return Response.ok(ChannelSwitchListResponse.create(
                total, buildResponses(announcements, tapUuids, organizationId, tenantId)
        )).build();
    }

    @GET
    @Path("/simulation")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Operation(operationId = "simulateChannelSwitchMonitor", summary = "Simulate the channel switch monitor",
            description = "Returns the recorded announcements in beacons of BSSIDs of a monitored network that "
                    + "are at or above the passed burst threshold. The channel switch monitor also alerts on "
                    + "anomalies and on announcements that were not followed, which no threshold changes and "
                    + "which are not part of the simulation. Reads the passed taps, or all taps of the network "
                    + "that the calling user can access. Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Simulation completed.",
            content = @Content(schema = @Schema(implementation = ChannelSwitchListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Burst threshold outside of 10 to 1000, unknown sorting column or direction, or page size above 250.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response simulation(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "Monitored network UUID.") @QueryParam("monitored_network_id") @NotNull UUID monitoredNetworkId,
                               @Parameter(description = "Burst threshold in frames per second, 10 to 1000.") @QueryParam("burst_threshold") int burstThreshold,
                               @Parameter(description = TIME_RANGE_DESCRIPTION) @QueryParam("time_range") @Valid String timeRangeParameter,
                               @Parameter(description = NETWORK_TAPS_DESCRIPTION) @QueryParam("taps") @Nullable String taps,
                               @Parameter(description = "Sorting column. Omit to sort by time.") @QueryParam("order_column") @Nullable String orderColumnParam,
                               @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                               @Parameter(description = "Page size. Cannot exceed 250.") @QueryParam("limit") int limit,
                               @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        Optional<MonitoredSSID> network = nzyme.getDot11().findMonitoredSSID(monitoredNetworkId);
        if (network.isEmpty() || !entityAccessible(authenticatedUser, network.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ChannelSwitchOrder order = parseOrder(orderColumnParam, orderDirectionParam);
        if (order == null || limit < 0 || limit > MAX_PAGE_SIZE || offset < 0
                || burstThreshold < ChannelSwitchRules.BURST_THRESHOLD_MINIMUM
                || burstThreshold > ChannelSwitchRules.BURST_THRESHOLD_MAXIMUM) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        List<UUID> tapUuids = networkTaps(authenticatedUser, network.get(), taps);
        List<String> bssids = monitoredBssids(network.get());

        long total = nzyme.getDot11().countChannelSwitchesAtBurstThreshold(timeRange, bssids, burstThreshold, tapUuids);
        List<ChannelSwitchEntry> announcements = nzyme.getDot11().findChannelSwitchesAtBurstThreshold(
                timeRange, bssids, burstThreshold, limit, offset, order.column(), order.direction(), tapUuids);

        return Response.ok(ChannelSwitchListResponse.create(
                total,
                buildResponses(announcements, tapUuids, network.get().organizationId(), network.get().tenantId())
        )).build();
    }

    @GET
    @Path("/simulation/histogram")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Operation(operationId = "simulateChannelSwitchMonitorHistogram", summary = "Get histogram of a channel switch monitor simulation",
            description = "Returns the number of frames that announced a channel switch for BSSIDs of a monitored "
                    + "network per bucket, and how many of them belong to announcements in beacons at or above the "
                    + "passed burst threshold. Reads the passed taps, or all taps of the network that the calling "
                    + "user can access. Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Simulation completed.",
            content = @Content(schema = @Schema(implementation = ChannelSwitchMonitorHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Burst threshold outside of 10 to 1000.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response simulationHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "Monitored network UUID.") @QueryParam("monitored_network_id") @NotNull UUID monitoredNetworkId,
                                        @Parameter(description = "Burst threshold in frames per second, 10 to 1000.") @QueryParam("burst_threshold") int burstThreshold,
                                        @Parameter(description = TIME_RANGE_DESCRIPTION) @QueryParam("time_range") @Valid String timeRangeParameter,
                                        @Parameter(description = NETWORK_TAPS_DESCRIPTION) @QueryParam("taps") @Nullable String taps) {
        if (burstThreshold < ChannelSwitchRules.BURST_THRESHOLD_MINIMUM
                || burstThreshold > ChannelSwitchRules.BURST_THRESHOLD_MAXIMUM) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        return highlightedHistogram(sc, monitoredNetworkId, burstThreshold, timeRangeParameter, taps);
    }

    @GET
    @Path("/monitor/histogram")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Operation(operationId = "findChannelSwitchMonitorHistogram", summary = "Get histogram of the channel switch monitor",
            description = "Returns the number of frames that announced a channel switch for BSSIDs of a monitored "
                    + "network per bucket, and how many of them belong to announcements the channel switch monitor "
                    + "of the network alerts on: with an alerting anomaly, in beacons at or above the burst "
                    + "threshold of the network, or not followed by the access point. Detection methods that are "
                    + "disabled for the network do not count. Reads the passed taps, or all taps of the network that "
                    + "the calling user can access. Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ChannelSwitchMonitorHistogramResponse.class)))
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response monitorHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Monitored network UUID.") @QueryParam("monitored_network_id") @NotNull UUID monitoredNetworkId,
                                     @Parameter(description = TIME_RANGE_DESCRIPTION) @QueryParam("time_range") @Valid String timeRangeParameter,
                                     @Parameter(description = NETWORK_TAPS_DESCRIPTION) @QueryParam("taps") @Nullable String taps) {
        return highlightedHistogram(sc, monitoredNetworkId, null, timeRangeParameter, taps);
    }

    // Without a simulated threshold, the monitor of the network applies as it is configured.
    private Response highlightedHistogram(SecurityContext sc,
                                          UUID monitoredNetworkId,
                                          @Nullable Integer simulatedBurstThreshold,
                                          String timeRangeParameter,
                                          @Nullable String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        Optional<MonitoredSSID> network = nzyme.getDot11().findMonitoredSSID(monitoredNetworkId);
        if (network.isEmpty() || !entityAccessible(authenticatedUser, network.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<String> bssids = monitoredBssids(network.get());
        List<UUID> tapUuids = networkTaps(authenticatedUser, network.get(), taps);

        List<Dot11.HighlightedChannelSwitchBucket> histogram = simulatedBurstThreshold == null
                ? nzyme.getDot11().getChannelSwitchMonitorHistogram(timeRange, bucketing, bssids,
                        network.get().detectionConfigChannelSwitchBurstThreshold(),
                        ChannelSwitchMonitorRule.parse(network.get().detectionConfigChannelSwitchDisabledRules()),
                        tapUuids)
                : nzyme.getDot11().getChannelSwitchSimulationHistogram(
                        timeRange, bucketing, bssids, simulatedBurstThreshold, tapUuids);

        Map<DateTime, ChannelSwitchMonitorHistogramValueResponse> buckets = Maps.newTreeMap();
        for (Dot11.HighlightedChannelSwitchBucket b : histogram) {
            buckets.put(b.bucket(), ChannelSwitchMonitorHistogramValueResponse.create(
                    b.bucket(), b.frameCount(), b.highlightedFrameCount()
            ));
        }

        return Response.ok(ChannelSwitchMonitorHistogramResponse.create(bucketing.bucketSizeMs(), buckets)).build();
    }

    // Never more than the taps of the network's tenant, which is what the monitor reads.
    private List<UUID> networkTaps(AuthenticatedUser authenticatedUser, MonitoredSSID network, @Nullable String taps) {
        List<UUID> networkTaps = nzyme.getTapManager().allTapUUIDsAccessibleByUser(
                authenticatedUser, network.organizationId(), network.tenantId());

        if (taps == null || taps.isEmpty() || taps.equals("*")) {
            return networkTaps;
        }

        return parseAndValidateTapIds(authenticatedUser, nzyme, taps)
                .stream()
                .filter(networkTaps::contains)
                .toList();
    }

    private List<String> monitoredBssids(MonitoredSSID network) {
        return nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(network.id())
                .stream()
                .map(MonitoredBSSID::bssid)
                .toList();
    }

    private List<ChannelSwitchDetailsResponse> buildResponses(List<ChannelSwitchEntry> entries,
                                                              List<UUID> tapUuids,
                                                              UUID organizationId,
                                                              UUID tenantId) {
        Map<UUID, String> tapNames = Maps.newHashMap();
        Map<String, Dot11MacLinkMetadataResponse> macs = Maps.newHashMap();
        List<ChannelSwitchDetailsResponse> announcements = Lists.newArrayList();
        for (ChannelSwitchEntry cs : entries) {
            String tapName = tapNames.computeIfAbsent(cs.tapUuid(), uuid ->
                    nzyme.getTapManager().findTap(uuid).map(Tap::name).orElse(null));

            announcements.add(ChannelSwitchDetailsResponse.create(
                    cs.id(),
                    cs.tapUuid(),
                    tapName,
                    cs.createdAt(),
                    macs.computeIfAbsent(cs.bssid(), mac -> buildMacResponse(mac, tapUuids, organizationId, tenantId)),
                    cs.frameType(),
                    cs.announcementType(),
                    cs.switchMode(),
                    cs.frequency(),
                    cs.newOperatingClass(),
                    cs.newChannel(),
                    cs.newFrequency(),
                    cs.countMin(),
                    cs.countMax(),
                    cs.frameCount(),
                    cs.peakFramesPerSecond(),
                    cs.anomalies(),
                    cs.outcome(),
                    cs.signalStrengthMin(),
                    cs.signalStrengthMax(),
                    cs.signalStrengthAverage()
            ));
        }

        return announcements;
    }

    private record ChannelSwitchOrder(Dot11.ChannelSwitchOrderColumn column, OrderDirection direction) { }

    @Nullable
    private static ChannelSwitchOrder parseOrder(@Nullable String orderColumn, @Nullable String orderDirection) {
        if (orderColumn == null || orderDirection == null) {
            return new ChannelSwitchOrder(Dot11.ChannelSwitchOrderColumn.CREATED_AT, OrderDirection.DESC);
        }

        try {
            return new ChannelSwitchOrder(
                    Dot11.ChannelSwitchOrderColumn.valueOf(orderColumn.toUpperCase()),
                    OrderDirection.valueOf(orderDirection.toUpperCase())
            );
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @GET
    @Path("/histograms/bssids")
    @Operation(operationId = "findChannelSwitchBssids", summary = "List top announcing BSSIDs",
            description = "Returns the BSSIDs with the most frames that announced a channel switch. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = TwoColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction, or page size above 250.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The monitored network is not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization, tenant or monitored network not found, or not accessible by the calling user.", content = @Content)
    public Response bssids(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                           @Parameter(description = TIME_RANGE_DESCRIPTION) @QueryParam("time_range") @Valid String timeRangeParameter,
                           @Parameter(description = FILTERS_DESCRIPTION) @QueryParam("filters") String filtersParameter,
                           @Parameter(description = TAPS_DESCRIPTION) @QueryParam("taps") String taps,
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

        HistogramOrder order = parseHistogramOrder(orderColumnParam, orderDirectionParam);
        if (order == null || limit < 0 || limit > MAX_PAGE_SIZE || offset < 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        BssidSelection bssids = resolveBssids(authenticatedUser, bssidsParam, monitoredNetworkId);
        if (bssids.error() != null) {
            return bssids.error();
        }

        long total = nzyme.getDot11().countChannelSwitchBssids(timeRange, filters, bssids.bssids(), tapUuids);

        List<TwoColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (Dot11MacFrameCount s : nzyme.getDot11().getChannelSwitchBssidHistogram(timeRange, filters,
                bssids.bssids(), limit, offset, order.column(), order.direction(), tapUuids)) {
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
    @Path("/histograms/channels")
    @Operation(operationId = "findChannelSwitchChannels", summary = "List top announced channels",
            description = "Returns the channels that were announced as the channel to switch to, with the number of "
                    + "frames that announced each. A channel that does not exist in its band has no frequency. "
                    + "Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = TwoColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction, or page size above 250.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The monitored network is not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found.", content = @Content)
    public Response channels(@Parameter(hidden = true) @Context SecurityContext sc,
                             @Parameter(description = TIME_RANGE_DESCRIPTION) @QueryParam("time_range") @Valid String timeRangeParameter,
                             @Parameter(description = FILTERS_DESCRIPTION) @QueryParam("filters") String filtersParameter,
                             @Parameter(description = TAPS_DESCRIPTION) @QueryParam("taps") String taps,
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

        HistogramOrder order = parseHistogramOrder(orderColumnParam, orderDirectionParam);
        if (order == null || limit < 0 || limit > MAX_PAGE_SIZE || offset < 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        BssidSelection bssids = resolveBssids(authenticatedUser, bssidsParam, monitoredNetworkId);
        if (bssids.error() != null) {
            return bssids.error();
        }

        long total = nzyme.getDot11().countChannelSwitchChannels(timeRange, filters, bssids.bssids(), tapUuids);

        List<TwoColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (ChannelSwitchChannelCount s : nzyme.getDot11().getChannelSwitchChannelHistogram(timeRange, filters,
                bssids.bssids(), limit, offset, order.column(), order.direction(), tapUuids)) {
            Map<String, Object> metadata = Maps.newHashMap();
            metadata.put("new_channel", s.newChannel());
            metadata.put("new_frequency", s.newFrequency());

            values.add(TwoColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            s.newFrequency() == null
                                    ? String.valueOf(s.newChannel())
                                    : s.newChannel() + " (" + s.newFrequency() + " MHz)",
                            HistogramValueType.GENERIC,
                            metadata
                    ),
                    HistogramValueStructureResponse.create(s.frameCount(), HistogramValueType.INTEGER, null)
            ));
        }

        return Response.ok(TwoColumnTableHistogramResponse.create(total, true, values)).build();
    }

    private record HistogramOrder(TwoColumnHistogramOrderColumn column, OrderDirection direction) { }

    @Nullable
    private static HistogramOrder parseHistogramOrder(@Nullable String orderColumn, @Nullable String orderDirection) {
        if (orderColumn == null || orderDirection == null) {
            return new HistogramOrder(TwoColumnHistogramOrderColumn.VALUE, OrderDirection.DESC);
        }

        try {
            return new HistogramOrder(
                    TwoColumnHistogramOrderColumn.valueOf(orderColumn.toUpperCase()),
                    OrderDirection.valueOf(orderDirection.toUpperCase())
            );
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
