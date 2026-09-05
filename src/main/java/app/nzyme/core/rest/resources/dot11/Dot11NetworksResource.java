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
    public Response bssids(@Context SecurityContext sc,
                           @QueryParam("time_range") @Valid String timeRangeParameter,
                           @QueryParam("filters") String filtersParameter,
                           @QueryParam("limit") int limit,
                           @QueryParam("offset") int offset,
                           @QueryParam("order_column") @Nullable String orderColumnParam,
                           @QueryParam("order_direction") @Nullable String orderDirectionParam,
                           @QueryParam("taps") String taps) {
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
                                            macAddressContextEntry.description()
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
    public Response bssid(@Context SecurityContext sc,
                          @PathParam("bssid") @NotEmpty String bssidParam,
                          @QueryParam("taps") String taps) {
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
                                                macAddressContextEntry.description()
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
                                            macAddressContextEntry.description()
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
    public Response bssidSignalWaterfall(@Context SecurityContext sc,
                                         @PathParam("bssid") String bssid,
                                         @QueryParam("time_range") @Valid String timeRangeParameter,
                                         @QueryParam("frequency") int frequency,
                                         @QueryParam("taps") String taps) {
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
    public Response bssidAdvertisementHistogram(@Context SecurityContext sc,
                                                @PathParam("bssid") String bssid,
                                                @QueryParam("time_range") @Valid String timeRangeParameter,
                                                @QueryParam("taps") String taps) {
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
    public Response bssidActiveChannelHistogram(@Context SecurityContext sc,
                                                @PathParam("bssid") String bssid,
                                                @QueryParam("time_range") @Valid String timeRangeParameter,
                                                @QueryParam("taps") String taps) {
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
    public Response bssidSSIDs(@Context SecurityContext sc,
                               @PathParam("bssid") String bssid,
                               @QueryParam("time_range") @Valid String timeRangeParameter,
                               @QueryParam("taps") String taps) {
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
    public Response histogram(@Context SecurityContext sc,
                              @QueryParam("time_range") @Valid String timeRangeParameter,
                              @QueryParam("filters") String filtersParameter,
                              @QueryParam("taps") String taps) {
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
    public Response ssidOfBSSID(@Context SecurityContext sc,
                                @PathParam("bssid") String bssid,
                                @PathParam("ssid") String ssid,
                                @QueryParam("time_range") @Valid String timeRangeParameter,
                                @QueryParam("taps") String taps) {
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
                                                macAddressContextEntry.description()
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
                                                macAddressContextEntry.description()
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
    public Response ssidOfBSSIDAdvertisementHistogram(@Context SecurityContext sc,
                                                      @PathParam("bssid") String bssid,
                                                      @PathParam("ssid") String ssid,
                                                      @QueryParam("time_range") @Valid String timeRangeParameter,
                                                      @QueryParam("taps") String taps) {
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
    public Response ssidOfBSSIDActiveChannelHistogram(@Context SecurityContext sc,
                                                      @PathParam("bssid") String bssid,
                                                      @PathParam("ssid") String ssid,
                                                      @QueryParam("time_range") @Valid String timeRangeParameter,
                                                      @QueryParam("taps") String taps) {
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
    public Response ssidOfBSSIDSignalWaterfall(@Context SecurityContext sc,
                                               @PathParam("bssid") String bssid,
                                               @PathParam("ssid") String ssid,
                                               @PathParam("frequency") int frequency,
                                               @QueryParam("time_range") @Valid String timeRangeParameter,
                                               @QueryParam("taps") String taps) {
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
    public Response updateTrackDetectorConfig(@Context SecurityContext sc,
                                              @PathParam("bssid") String bssid,
                                              @PathParam("ssid") String ssid,
                                              @PathParam("frequency") int frequency,
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
    public Response allSSIDNames(@Context SecurityContext sc,
                                 @QueryParam("organization_id") @NotNull UUID organizationId,
                                 @QueryParam("tenant_id") @NotNull UUID tenantId) {
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
