package app.nzyme.core.rest.resources.dot11;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.db.*;
import app.nzyme.core.dot11.tracks.Track;
import app.nzyme.core.dot11.tracks.TrackDetector;
import app.nzyme.core.dot11.tracks.db.TrackDetectorConfig;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.UpdateTrackDetectorConfigurationRequest;
import app.nzyme.core.rest.responses.dot11.*;
import app.nzyme.core.rest.responses.shared.TapBasedSignalStrengthResponse;
import app.nzyme.core.shared.db.TapBasedSignalStrengthResult;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.TimeRangeFactory;
import app.nzyme.core.util.Tools;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.joda.time.DateTime;

import java.util.*;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/dot11/networks")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Networks", description = "Access points, BSSIDs and SSIDs that taps recorded on the air. "
        + "Use these endpoints to explore the WiFi networks around your taps and their signal behavior.")
public class Dot11NetworksResource extends TapDataHandlingResource {

    private final static List<Integer> DEFAULT_X_VALUES = Lists.newArrayList();

    static {
        for (int cnt = -100; cnt < 0; cnt++) {
            DEFAULT_X_VALUES.add(cnt);
        }
    }

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/bssids")
    @Operation(operationId = "findDot11Bssids", summary = "List recorded BSSIDs",
            description = "Returns all BSSIDs that the selected taps recorded in the time range, with a summary of "
                    + "their SSIDs, security protocols, fingerprints and client counts. Sorts by average signal "
                    + "strength descending unless you pass a sorting column and direction.")
    @ApiResponse(responseCode = "200", description = "BSSIDs found.",
            content = @Content(schema = @Schema(implementation = BSSIDListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    public Response bssids(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                           @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                           @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                           @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                           @Parameter(description = "Sorting column. Omit to sort by average signal strength.") @QueryParam("order_column") @Nullable String orderColumnParam,
                           @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                           @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Dot11.BssidOrderColumn orderColumn = Dot11.BssidOrderColumn.SIGNAL_STRENGTH_AVERAGE;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Dot11.BssidOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        List<BSSIDSummaryDetailsResponse> bssids = Lists.newArrayList();
        long total = nzyme.getDot11().countBSSIDs(timeRange, filters, tapUuids);
        for (BSSIDSummary bssid : nzyme.getDot11().findBSSIDs(
                timeRange,
                filters,
                orderColumn,
                orderDirection,
                limit,
                offset,
                tapUuids)) {
            Optional<MacAddressContextEntry> bssidContext = nzyme.getContextService().findMacAddressContext(
                    bssid.bssid(),
                    authenticatedUser.getOrganizationId(),
                    authenticatedUser.getTenantId()
            );

            bssids.add(BSSIDSummaryDetailsResponse.create(
                    Dot11MacAddressResponse.create(
                            bssid.bssid(),
                            nzyme.getOuiService().lookup(bssid.bssid()).orElse(null),
                            null,
                            bssidContext.map(macAddressContextEntry ->
                                    Dot11MacAddressContextResponse.create(
                                            macAddressContextEntry.name(),
                                            macAddressContextEntry.description(),
                                            macAddressContextEntry.notes()
                                    ))
                                    .orElse(null)
                    ),
                    bssid.securityProtocols(),
                    bssid.signalStrengthAverage(),
                    bssid.firstSeen(),
                    bssid.lastSeen(),
                    bssid.clientCount(),
                    bssid.fingerprints(),
                    bssid.ssids(),
                    bssid.hiddenSSIDFrames() > 0,
                    bssid.infrastructureTypes()
            ));
        }

        return Response.ok(BSSIDListResponse.create(total, bssids)).build();
    }

    @GET
    @Path("/bssids/show/{bssid}")
    @Operation(operationId = "findDot11Bssid", summary = "Get details of a BSSID",
            description = "Looks the BSSID up over all time, not a selected time range. Includes the clients that "
                    + "connected to it in the last 24 hours and the signal strength each tap recorded in the last "
                    + "15 minutes.")
    @ApiResponse(responseCode = "200", description = "BSSID found.",
            content = @Content(schema = @Schema(implementation = BSSIDDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "No tap the user can access recorded this BSSID.", content = @Content)
    public Response bssid(@Parameter(hidden = true) @Context SecurityContext sc,
                          @Parameter(description = "BSSID MAC address.") @PathParam("bssid") @NotEmpty String bssidParam,
                          @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);

        Optional<BSSIDSummary> bssidResult = nzyme.getDot11()
                .findBSSID(bssidParam, TimeRangeFactory.allTime(), tapUuids);

        if (bssidResult.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        BSSIDSummary bssid = bssidResult.get();

        Optional<MacAddressContextEntry> bssidContext = nzyme.getContextService().findMacAddressContext(
                bssid.bssid(),
                authenticatedUser.getOrganizationId(),
                authenticatedUser.getTenantId()
        );

        BSSIDSummaryDetailsResponse summary = BSSIDSummaryDetailsResponse.create(
                Dot11MacAddressResponse.create(
                        bssid.bssid(),
                        nzyme.getOuiService().lookup(bssid.bssid()).orElse(null),
                        null,
                        bssidContext.map(macAddressContextEntry ->
                                        Dot11MacAddressContextResponse.create(
                                                macAddressContextEntry.name(),
                                                macAddressContextEntry.description(),
                                                macAddressContextEntry.notes()
                                        ))
                                .orElse(null)
                ),
                bssid.securityProtocols(),
                bssid.signalStrengthAverage(),
                bssid.firstSeen(),
                bssid.lastSeen(),
                bssid.clientCount(),
                bssid.fingerprints(),
                bssid.ssids(),
                bssid.hiddenSSIDFrames() > 0,
                bssid.infrastructureTypes()
        );

        List<BSSIDClientDetails> clients = Lists.newArrayList();
        for (ConnectedClientDetails client : nzyme.getDot11().findClientsOfBSSID(bssid.bssid(), 24*60, tapUuids)) {
            Optional<MacAddressContextEntry> clientContext = nzyme.getContextService().findMacAddressContext(
                    client.clientMac(),
                    authenticatedUser.getOrganizationId(),
                    authenticatedUser.getTenantId()
            );

            clients.add(BSSIDClientDetails.create(Dot11MacAddressResponse.create(
                    client.clientMac(),
                    nzyme.getOuiService().lookup(client.clientMac()).orElse(null),
                    Tools.macAddressIsRandomized(bssid.bssid()),
                    clientContext.map(macAddressContextEntry ->
                                    Dot11MacAddressContextResponse.create(
                                            macAddressContextEntry.name(),
                                            macAddressContextEntry.description(),
                                            macAddressContextEntry.notes()
                                    ))
                            .orElse(null)
            )));
        }

        List<TapBasedSignalStrengthResponse> signalStrength = Lists.newArrayList();
        for (TapBasedSignalStrengthResult ss : nzyme.getDot11()
                .findBSSIDSignalStrengthPerTap(bssid.bssid(), TimeRange.create(DateTime.now().minusMinutes(15), DateTime.now(), false), tapUuids)) {
            signalStrength.add(TapBasedSignalStrengthResponse.create(ss.tapUuid(), ss.tapName(), ss.signalStrength()));
        }

        return Response.ok(BSSIDDetailsResponse.create(
                summary,
                clients,
                signalStrength,
                bssid.frequencies()
        )).build();
    }

    @GET
    @Path("/bssids/show/{bssid}/signal/waterfall")
    @Operation(operationId = "findDot11BssidSignalWaterfall", summary = "Get signal waterfall of a BSSID",
            description = "Returns a signal strength heatmap of the BSSID on one frequency. Exactly one tap must be "
                    + "selected because signal strength is only comparable within a single tap. Track detection is "
                    + "not performed here and no tracks are returned.",
            externalDocs = @ExternalDocumentation(description = "Signal tracks in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-network-monitoring-signal-tracks"))
    @ApiResponse(responseCode = "200", description = "Waterfall data found.",
            content = @Content(schema = @Schema(implementation = SignalWaterfallResponse.class)))
    @ApiResponse(responseCode = "400", description = "Not exactly one tap was selected.", content = @Content)
    public Response bssidSignalWaterfall(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @Parameter(description = "BSSID MAC address.") @PathParam("bssid") String bssid,
                                         @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                         @Parameter(description = "Frequency in MHz.") @QueryParam("frequency") int frequency,
                                         @Parameter(description = "UUID of exactly one tap to include.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        if (tapUuids.size() != 1) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        // Track detector config. Pull related org from tap (access was checked above and count is 1)
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        Tap tap = nzyme.getTapManager().findTap(tapUuids.get(0)).get();

        List<SignalTrackHistogramEntry> signals = nzyme.getDot11().getBSSIDSignalStrengthWaterfall(
                bssid, frequency, timeRange, tap.uuid()
        );

        TrackDetector.TrackDetectorHeatmapData heatmap = TrackDetector.toChartAxisMaps(signals);

        return Response.ok(
                SignalWaterfallResponse.create(
                        heatmap.z(),
                        DEFAULT_X_VALUES,
                        heatmap.y(),
                        null,
                        null
                )
        ).build();
    }

    @GET
    @Path("/bssids/show/{bssid}/advertisements/histogram")
    @Operation(operationId = "findDot11BssidAdvertisementHistogram", summary = "Get advertisement histogram of a BSSID",
            description = "Returns the number of beacons and probe responses the BSSID sent per time bucket. The "
                    + "bucket size is derived from the time range. Buckets without data are missing from the result.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = AdvertisementHistogramResponse.class)))
    public Response bssidAdvertisementHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                                @Parameter(description = "BSSID MAC address.") @PathParam("bssid") String bssid,
                                                @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                                @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        Map<DateTime, AdvertisementHistogramValueResponse> response = Maps.newTreeMap();
        for (Dot11AdvertisementHistogramEntry entry : nzyme.getDot11()
                .getBSSIDAdvertisementHistogram(bssid, timeRange, bucketing, tapUuids)) {
            response.put(
                    entry.bucket(),
                    AdvertisementHistogramValueResponse.create(
                            entry.bucket(),
                            entry.beacons(),
                            entry.probeResponses()
                    )
            );
        }

        return Response.ok(AdvertisementHistogramResponse.create(response)).build();
    }

    @GET
    @Path("/bssids/show/{bssid}/frequencies/histogram")
    @Operation(operationId = "findDot11BssidChannelHistogram", summary = "List active channels of a BSSID",
            description = "Returns every channel the BSSID was active on in the time range, with the number of frames "
                    + "and bytes recorded on each.")
    @ApiResponse(responseCode = "200", description = "Channels found.",
            content = @Content(schema = @Schema(implementation = ActiveChannelListResponse.class)))
    public Response bssidActiveChannelHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                                @Parameter(description = "BSSID MAC address.") @PathParam("bssid") String bssid,
                                                @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                                @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        List<ActiveChannelDetailsResponse> channels = Lists.newArrayList();
        for (ActiveChannel c : nzyme.getDot11().getBSSIDChannelUsageHistogram(bssid, timeRange, tapUuids)) {
            channels.add(ActiveChannelDetailsResponse.create(
                    Dot11.frequencyToChannel(c.frequency()),
                    c.frequency(),
                    c.frames(),
                    c.bytes()
            ));
        }

        return Response.ok(ActiveChannelListResponse.create(channels)).build();
    }

    @GET
    @Path("/bssids/show/{bssid}/ssids")
    @Operation(operationId = "findDot11BssidSsids", summary = "List SSIDs advertised by a BSSID",
            description = "Returns one entry per SSID and channel combination. Each entry is flagged if its channel "
                    + "is the most active one for that SSID.")
    @ApiResponse(responseCode = "200", description = "SSIDs found.",
            content = @Content(schema = @Schema(implementation = SSIDChannelListResponse.class)))
    @ApiResponse(responseCode = "404", description = "The BSSID was not recorded in the time range.", content = @Content)
    public Response bssidSSIDs(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "BSSID MAC address.") @PathParam("bssid") String bssid,
                               @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                               @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        if (!nzyme.getDot11().bssidExist(bssid, timeRange, tapUuids)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<SSIDChannelDetails> ssids = nzyme.getDot11().findSSIDPerChannelsOfBSSID(timeRange, bssid, tapUuids);

        // Find main active channel per SSID.
        Map<String, Map<Integer, Long>> activeChannels = Maps.newHashMap();
        for (SSIDChannelDetails ssid : ssids) {
            if (!activeChannels.containsKey(ssid.ssid())) {
                activeChannels.put(ssid.ssid(), Maps.newHashMap());
            }
            Map<Integer, Long> ssidChannels = activeChannels.get(ssid.ssid());
            ssidChannels.put(ssid.frequency(), ssid.totalFrames());
        }
        Map<String, Integer> mostActiveChannels = Maps.newHashMap();
        for (Map.Entry<String, Map<Integer, Long>> channel : activeChannels.entrySet()) {
            String ssid = channel.getKey();

            int mostActiveChannel = 0;
            long highestCount = 0;
            for (Map.Entry<Integer, Long> count : channel.getValue().entrySet()) {
                if (count.getValue() > highestCount) {
                    highestCount = count.getValue();
                    mostActiveChannel = count.getKey();
                }
            }

            mostActiveChannels.put(ssid, mostActiveChannel);
        }

        List<SSIDChannelDetailsResponse> ssidsResult = Lists.newArrayList();
        for (SSIDChannelDetails ssid : ssids) {
            ssidsResult.add(SSIDChannelDetailsResponse.create(
                    ssid.ssid(),
                    ssid.frequency(),
                    Dot11.frequencyToChannel(ssid.frequency()),
                    ssid.signalStrengthAverage(),
                    ssid.totalFrames(),
                    ssid.totalBytes(),
                    mostActiveChannels.get(ssid.ssid()).equals(ssid.frequency()),
                    ssid.securityProtocols(),
                    ssid.infrastructureTypes(),
                    ssid.isWps(),
                    ssid.lastSeen()
            ));
        }

        return Response.ok(SSIDChannelListResponse.create(ssidsResult)).build();
    }

    @GET
    @Path("/bssids/histogram")
    @Operation(operationId = "findDot11NetworksHistogram", summary = "Get BSSID and SSID count histogram",
            description = "Returns how many distinct BSSIDs and SSIDs the selected taps recorded per time bucket. The "
                    + "response also carries the bucket size in milliseconds.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = BSSIDAndSSIDHistogramResponse.class)))
    public Response histogram(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                              @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                              @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        Map<DateTime, BSSIDAndSSIDHistogramValueResponse> response = Maps.newTreeMap();
        for (BSSIDAndSSIDCountHistogramEntry h : nzyme.getDot11()
                .getBSSIDAndSSIDCountHistogram(timeRange, bucketing, filters, tapUuids)) {
            response.put(
                    h.bucket(),
                    BSSIDAndSSIDHistogramValueResponse.create(
                            h.bucket(),
                            h.bssidCount(),
                            h.ssidCount()
                    )
            );
        }

        return Response.ok(BSSIDAndSSIDHistogramResponse.create(response, bucketing.bucketSizeMs())).build();
    }

    @GET
    @Path("/bssids/show/{bssid}/ssids/show/{ssid}")
    @Operation(operationId = "findDot11Ssid", summary = "Get details of an SSID of a BSSID",
            description = "Returns frequencies, frame and byte counts, security suites, fingerprints and connected "
                    + "clients of one SSID as advertised by one BSSID. The signal strength per tap covers the last "
                    + "15 minutes regardless of the time range.")
    @ApiResponse(responseCode = "200", description = "SSID found.",
            content = @Content(schema = @Schema(implementation = SSIDDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "This BSSID did not advertise this SSID in the time range.", content = @Content)
    public Response ssidOfBSSID(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "BSSID MAC address.") @PathParam("bssid") String bssid,
                                @Parameter(description = "SSID name.") @PathParam("ssid") String ssid,
                                @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        Optional<SSIDDetails> dbResult = nzyme.getDot11().findSSIDDetails(timeRange, bssid, ssid, tapUuids);

        if (dbResult.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        SSIDDetails ssidDetails = dbResult.get();

        List<SecuritySuitesResponse> securitySuites = Lists.newArrayList();
        for (Dot11SecuritySuiteJson suite : ssidDetails.securitySuites()) {
            if (suite == null) {
                continue;
            }

            securitySuites.add(SecuritySuitesResponse.create(
                    suite.pairwiseCiphers(),
                    suite.groupCipher(),
                    suite.keyManagementModes(),
                    suite.pmfMode(),
                    Dot11.securitySuitesToIdentifier(suite)
            ));
        }

        List<BSSIDClientDetails> accessPointClients = Lists.newArrayList();
        for (String mac : ssidDetails.accessPointClients()) {
            if (mac != null) {
                Optional<MacAddressContextEntry> clientContext = nzyme.getContextService().findMacAddressContext(
                        mac,
                        authenticatedUser.getOrganizationId(),
                        authenticatedUser.getTenantId()
                );

                accessPointClients.add(BSSIDClientDetails.create(Dot11MacAddressResponse.create(
                        mac,
                        nzyme.getOuiService().lookup(mac).orElse(null),
                        Tools.macAddressIsRandomized(mac),
                        clientContext.map(macAddressContextEntry ->
                                        Dot11MacAddressContextResponse.create(
                                                macAddressContextEntry.name(),
                                                macAddressContextEntry.description(),
                                                macAddressContextEntry.notes()
                                        ))
                                .orElse(null)
                )));
            }
        }

        List<TapBasedSignalStrengthResponse> signalStrength = Lists.newArrayList();
        for (TapBasedSignalStrengthResult ss : nzyme.getDot11()
                .findBSSIDSignalStrengthPerTap(bssid, TimeRange.create(DateTime.now().minusMinutes(15), DateTime.now(), false), tapUuids)) {
            signalStrength.add(TapBasedSignalStrengthResponse.create(ss.tapUuid(), ss.tapName(), ss.signalStrength()));
        }

        Optional<MacAddressContextEntry> bssidContext = nzyme.getContextService().findMacAddressContext(
                bssid,
                authenticatedUser.getOrganizationId(),
                authenticatedUser.getTenantId()
        );

        SSIDDetailsResponse response = SSIDDetailsResponse.create(
                Dot11MacAddressResponse.create(
                        bssid,
                        nzyme.getOuiService().lookup(bssid).orElse(null),
                        null,
                        bssidContext.map(macAddressContextEntry ->
                                        Dot11MacAddressContextResponse.create(
                                                macAddressContextEntry.name(),
                                                macAddressContextEntry.description(),
                                                macAddressContextEntry.notes()
                                        ))
                                .orElse(null)
                ),
                ssidDetails.ssid(),
                ssidDetails.frequencies(),
                ssidDetails.signalStrengthAverage(),
                ssidDetails.totalFrames(),
                ssidDetails.totalBytes(),
                ssidDetails.securityProtocols(),
                ssidDetails.fingerprints(),
                accessPointClients,
                ssidDetails.rates(),
                ssidDetails.infrastructureTypes(),
                securitySuites,
                ssidDetails.isWps(),
                signalStrength,
                ssidDetails.lastSeen()
        );

        return Response.ok(response).build();
    }

    @GET
    @Path("/bssids/show/{bssid}/ssids/show/{ssid}/advertisements/histogram")
    @Operation(operationId = "findDot11SsidAdvertisementHistogram", summary = "Get advertisement histogram of an SSID",
            description = "Returns the number of beacons and probe responses that carried this SSID per time bucket. "
                    + "Buckets without data are missing from the result.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = AdvertisementHistogramResponse.class)))
    public Response ssidOfBSSIDAdvertisementHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                                      @Parameter(description = "BSSID MAC address.") @PathParam("bssid") String bssid,
                                                      @Parameter(description = "SSID name.") @PathParam("ssid") String ssid,
                                                      @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                                      @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        Map<DateTime, AdvertisementHistogramValueResponse> response = Maps.newTreeMap();
        for (Dot11AdvertisementHistogramEntry entry : nzyme.getDot11()
                .getSSIDAdvertisementHistogram(bssid, ssid, timeRange, bucketing, tapUuids)) {
            response.put(
                    entry.bucket(),
                    AdvertisementHistogramValueResponse.create(
                            entry.bucket(),
                            entry.beacons(),
                            entry.probeResponses()
                    )
            );
        }

        return Response.ok(AdvertisementHistogramResponse.create(response)).build();
    }

    @GET
    @Path("/bssids/show/{bssid}/ssids/show/{ssid}/frequencies/histogram")
    @Operation(operationId = "findDot11SsidChannelHistogram", summary = "List active channels of an SSID",
            description = "Returns every channel this SSID of this BSSID was active on in the time range, with the "
                    + "number of frames and bytes recorded on each.")
    @ApiResponse(responseCode = "200", description = "Channels found.",
            content = @Content(schema = @Schema(implementation = ActiveChannelListResponse.class)))
    public Response ssidOfBSSIDActiveChannelHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                                      @Parameter(description = "BSSID MAC address.") @PathParam("bssid") String bssid,
                                                      @Parameter(description = "SSID name.") @PathParam("ssid") String ssid,
                                                      @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                                      @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        List<ActiveChannelDetailsResponse> channels = Lists.newArrayList();
        for (ActiveChannel c : nzyme.getDot11().getSSIDChannelUsageHistogram(bssid, ssid, timeRange, tapUuids)) {
            channels.add(ActiveChannelDetailsResponse.create(
                    Dot11.frequencyToChannel(c.frequency()),
                    c.frequency(),
                    c.frames(),
                    c.bytes()
            ));
        }

        return Response.ok(ActiveChannelListResponse.create(channels)).build();
    }

    @GET
    @Path("/bssids/show/{bssid}/ssids/show/{ssid}/frequencies/show/{frequency}/signal/waterfall")
    @Operation(operationId = "findDot11SsidSignalWaterfall", summary = "Get signal waterfall of an SSID",
            description = "Returns a signal strength heatmap of this SSID on one frequency, together with the signal "
                    + "tracks the track detector found and the track detector configuration that was used. Exactly "
                    + "one tap must be selected because signal strength is only comparable within a single tap.",
            externalDocs = @ExternalDocumentation(description = "Signal tracks in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-network-monitoring-signal-tracks"))
    @ApiResponse(responseCode = "200", description = "Waterfall data found.",
            content = @Content(schema = @Schema(implementation = SignalWaterfallResponse.class)))
    @ApiResponse(responseCode = "400", description = "Not exactly one tap was selected.", content = @Content)
    public Response ssidOfBSSIDSignalWaterfall(@Parameter(hidden = true) @Context SecurityContext sc,
                                               @Parameter(description = "BSSID MAC address.") @PathParam("bssid") String bssid,
                                               @Parameter(description = "SSID name.") @PathParam("ssid") String ssid,
                                               @Parameter(description = "Frequency in MHz.") @PathParam("frequency") int frequency,
                                               @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                               @Parameter(description = "UUID of exactly one tap to include.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        if (tapUuids.size() != 1) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        // Track detector config. Pull related org from tap (access was checked above and count is 1)
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        Tap tap = nzyme.getTapManager().findTap(tapUuids.get(0)).get();
        TrackDetectorConfig config = nzyme.getDot11()
                .findCustomTrackDetectorConfiguration(tap.organizationId(), tap.uuid(), bssid, ssid, frequency)
                .orElse(TrackDetector.DEFAULT_CONFIG);

        List<SignalTrackHistogramEntry> signals = nzyme.getDot11().getSSIDSignalStrengthWaterfall(
                bssid, ssid, frequency, timeRange, tap.uuid());

        TrackDetector.TrackDetectorHeatmapData heatmap = TrackDetector.toChartAxisMaps(signals);

        TrackDetector td = new TrackDetector();
        List<SignalWaterfallTrackResponse> tracks = Lists.newArrayList();
        for (Track track : td.detect(heatmap.z(), heatmap.y(), config)) {
            tracks.add(SignalWaterfallTrackResponse.create(
                    track.start(),
                    track.end(),
                    track.centerline(),
                    track.minSignal(),
                    track.maxSignal()
            ));
        }

        return Response.ok(
                SignalWaterfallResponse.create(
                        heatmap.z(),
                        DEFAULT_X_VALUES,
                        heatmap.y(),
                        tracks,
                        SignalWaterfallConfigurationResponse.create(
                                config.frameThreshold(),
                                TrackDetector.DEFAULT_CONFIG.frameThreshold(),
                                config.gapThreshold(),
                                TrackDetector.DEFAULT_CONFIG.gapThreshold(),
                                config.signalCenterlineJitter(),
                                TrackDetector.DEFAULT_CONFIG.signalCenterlineJitter()
                        )
                )
        ).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/bssids/show/{bssid}/ssids/show/{ssid}/frequencies/show/{frequency}/signal/trackdetector/configuration")
    @Operation(operationId = "updateDot11TrackDetectorConfiguration", summary = "Update track detector configuration",
            description = "Stores a custom track detector configuration for this BSSID, SSID and frequency on the tap "
                    + "referenced in the request body. The configuration replaces the built in defaults for signal "
                    + "waterfall track detection. Requires organization administrator permissions.",
            externalDocs = @ExternalDocumentation(description = "Signal tracks in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-network-monitoring-signal-tracks"))
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The referenced tap is not accessible by the calling user.", content = @Content)
    public Response updateTrackDetectorConfig(@Parameter(hidden = true) @Context SecurityContext sc,
                                              @Parameter(description = "BSSID MAC address.") @PathParam("bssid") String bssid,
                                              @Parameter(description = "SSID name.") @PathParam("ssid") String ssid,
                                              @Parameter(description = "Frequency in MHz.") @PathParam("frequency") int frequency,
                                              @RequestBody(description = "Tap UUID and the frame threshold, gap "
                                                      + "threshold and signal centerline jitter to use.", required = true, content = @Content(mediaType = "application/json"))
                                              UpdateTrackDetectorConfigurationRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        // Check if user has access to this tap.
        if (!nzyme.getTapManager().allTapUUIDsAccessibleByUser(authenticatedUser).contains(req.tapId())) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Known to exist because of permission check.
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        Tap tap = nzyme.getTapManager().findTap(req.tapId()).get();

        nzyme.getDot11().updateCustomTrackDetectorConfiguration(
                tap.organizationId(),
                tap.uuid(),
                bssid,
                ssid,
                frequency,
                (int) req.frameThreshold(),
                (int)  req.gapThreshold(),
                (int) req.signalCenterlineJitter()
        );

        return Response.ok().build();
    }

    @GET
    @Path("/ssids/names")
    @Operation(operationId = "findDot11SsidNames", summary = "List all recorded SSID names",
            description = "Returns the distinct names of all SSIDs that the taps of a tenant ever recorded. The list "
                    + "is empty if the user cannot access any tap of the tenant.")
    @ApiResponse(responseCode = "200", description = "SSID names found.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class))))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response allSSIDNames(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @QueryParam("organization_id") @NotNull UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") @NotNull UUID tenantId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!passedTenantDataAccessible(sc,  organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }


        List<UUID> tapUuids = parseAndValidateTapIdsDirect(
                authenticatedUser,
                nzyme,
                nzyme.getTapManager().allTapUUIDsAccessibleByScope(organizationId, tenantId)
        );

        return Response.ok(nzyme.getDot11().findAllSSIDNames(tapUuids)).build();
    }
}
