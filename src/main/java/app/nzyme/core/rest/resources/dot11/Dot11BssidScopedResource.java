package app.nzyme.core.rest.resources.dot11;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.channelswitches.ChannelSwitchAlertConfiguration;
import app.nzyme.core.dot11.disco.DiscoDetectionRegistryKeys;
import app.nzyme.core.dot11.signal.FrameSignalAnalysis;
import app.nzyme.core.dot11.signal.FrameSource;
import app.nzyme.core.rest.responses.dot11.signal.FrameSignalAnalysisResponse;
import app.nzyme.core.rest.responses.dot11.signal.FrameSignalChartResponse;
import app.nzyme.core.rest.responses.dot11.signal.FrameSignalChartsResponse;
import app.nzyme.core.rest.responses.dot11.signal.FrameSignalFindingResponse;
import app.nzyme.core.rest.responses.dot11.signal.FrameSignalFrameResponse;
import app.nzyme.core.rest.responses.dot11.signal.FrameSignalReferenceBucketResponse;
import app.nzyme.core.rest.responses.dot11.signal.FrameSignalReferenceResponse;
import app.nzyme.core.rest.responses.dot11.signal.FrameSignalSourceResponse;
import app.nzyme.core.rest.responses.dot11.signal.FrameSignalTransmitterListResponse;
import app.nzyme.core.rest.responses.dot11.signal.FrameSignalTransmitterResponse;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.ws.rs.core.SecurityContext;
import java.util.Map;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.dot11.db.monitoring.MonitoredBSSID;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.responses.dot11.Dot11MacAddressContextResponse;
import app.nzyme.core.rest.responses.dot11.Dot11MacAddressResponse;
import app.nzyme.core.rest.responses.dot11.Dot11MacLinkMetadataResponse;
import com.google.common.base.Splitter;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class Dot11BssidScopedResource extends TapDataHandlingResource {

    protected static final String SIGNAL_TAPS_DESCRIPTION = "Comma separated list of tap UUIDs to include, or * for all taps of the tenant the user can access. Without it, the result is empty.";
    protected static final String SIGNAL_TOO_MUCH_DATA = "The comparison did not finish within 30 seconds per statement. Select a shorter time range or fewer taps.";

    @Inject
    protected NzymeNode nzyme;

    protected record BssidSelection(@Nullable List<String> bssids, @Nullable Response error) { }

    protected BssidSelection resolveBssids(AuthenticatedUser authenticatedUser,
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

    protected Dot11MacLinkMetadataResponse buildMacResponse(String mac,
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

    private int signalThreshold(FrameSource source, UUID organizationId, UUID tenantId) {
        return switch (source) {
            case DISCONNECTION -> nzyme.getDot11().discoAnomalies().intValue(
                    DiscoDetectionRegistryKeys.SIGNAL_MISMATCH_MINIMUM_DIFFERENCE, organizationId, tenantId);
            case CHANNEL_SWITCH -> ChannelSwitchAlertConfiguration.load(nzyme, organizationId, tenantId)
                    .signalMismatchMinimumDifference();
        };
    }

    protected Response signalAnalysis(FrameSource source,
                                      SecurityContext sc,
                                      UUID organizationId,
                                      UUID tenantId,
                                      String timeRangeParameter,
                                      String filtersParameter,
                                      String taps,
                                      int limit,
                                      int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (limit > FrameSignalAnalysis.MAX_FINDINGS_PER_PAGE) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        List<UUID> tapUuids = tapsOfTenant(getAuthenticatedUser(sc), taps, organizationId, tenantId);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);
        int threshold = signalThreshold(source, organizationId, tenantId);

        FrameSignalAnalysis.Result result;
        try {
            result = nzyme.getDot11().frameSignalAnalysis()
                    .analyze(source, timeRange, filters, tapUuids, threshold, limit <= 0 ? 25 : limit, offset);
        } catch (FrameSignalAnalysis.TooMuchDataException e) {
            return Response.status(422).build();
        }

        Map<UUID, String> tapNames = Maps.newHashMap();
        List<FrameSignalFindingResponse> findings = Lists.newArrayList();
        for (FrameSignalAnalysis.Finding f : result.findings()) {
            findings.add(FrameSignalFindingResponse.create(
                    f.tapUuid(),
                    tapNames.computeIfAbsent(f.tapUuid(), uuid ->
                            nzyme.getTapManager().findTap(uuid).map(Tap::name).orElse(null)),
                    f.frequency(),
                    f.transmitter(),
                    f.frameCount(),
                    f.signal(),
                    f.signalMin(),
                    f.signalMax(),
                    f.comparedFrameCount(),
                    f.comparedWith(),
                    f.difference(),
                    f.mismatchFrameCount(),
                    f.largestDifference()
            ));
        }

        return Response.ok(FrameSignalAnalysisResponse.create(
                result.sourceCount(),
                FrameSignalAnalysis.MAX_SOURCES,
                signalSources(result.sources(), tapUuids, organizationId, tenantId),
                threshold,
                result.findingCount(),
                findings,
                result.truncated(),
                FrameSignalAnalysis.MAX_FRAME_RECORDS,
                FrameSignalAnalysis.MAX_CHARTS,
                FrameSignalAnalysis.REFERENCE_MINUTES,
                FrameSignalAnalysis.CLIENT_COMPARISON_MINUTES,
                result.firstFrame(),
                result.lastFrame()
        )).build();
    }

    protected Response signalCharts(FrameSource source,
                                    SecurityContext sc,
                                    UUID organizationId,
                                    UUID tenantId,
                                    String timeRangeParameter,
                                    String filtersParameter,
                                    String taps,
                                    @Nullable String chartsParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<FrameSignalAnalysis.ChartKey> selection = null;
        if (chartsParameter != null && !chartsParameter.isBlank()) {
            selection = Lists.newArrayList();
            for (String chart : Splitter.on(",").trimResults().omitEmptyStrings().split(chartsParameter)) {
                List<String> parts = Splitter.on(":").splitToList(chart);

                try {
                    if (parts.size() != 2) {
                        throw new IllegalArgumentException();
                    }

                    selection.add(new FrameSignalAnalysis.ChartKey(
                            UUID.fromString(parts.get(0)), Integer.parseInt(parts.get(1))));
                } catch (IllegalArgumentException e) {
                    return Response.status(Response.Status.BAD_REQUEST).build();
                }
            }

            if (selection.isEmpty() || selection.size() > FrameSignalAnalysis.MAX_CHARTS) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        List<UUID> tapUuids = tapsOfTenant(getAuthenticatedUser(sc), taps, organizationId, tenantId);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);
        int threshold = signalThreshold(source, organizationId, tenantId);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        FrameSignalAnalysis.Charts result;
        try {
            result = nzyme.getDot11().frameSignalAnalysis()
                    .charts(source, timeRange, bucketing, filters, tapUuids, threshold, selection);
        } catch (FrameSignalAnalysis.TooMuchDataException e) {
            return Response.status(422).build();
        }

        Map<UUID, String> tapNames = Maps.newHashMap();
        List<FrameSignalChartResponse> charts = Lists.newArrayList();
        for (FrameSignalAnalysis.Chart chart : result.charts()) {
            List<FrameSignalReferenceResponse> references = Lists.newArrayList();
            for (FrameSignalAnalysis.Reference reference : chart.references()) {
                List<FrameSignalReferenceBucketResponse> buckets = Lists.newArrayList();
                for (FrameSignalAnalysis.ReferenceBucket bucket : reference.buckets()) {
                    buckets.add(FrameSignalReferenceBucketResponse.create(
                            bucket.bucket(), bucket.average(), bucket.min(), bucket.max(), bucket.samples()
                    ));
                }

                references.add(FrameSignalReferenceResponse.create(reference.source(), buckets));
            }

            List<FrameSignalFrameResponse> frames = Lists.newArrayList();
            for (FrameSignalAnalysis.Frame f : chart.frames()) {
                frames.add(FrameSignalFrameResponse.create(
                        f.timestamp(),
                        f.transmitter(),
                        f.receiver(),
                        Dot11.BROADCAST_MAC.equals(f.receiver()),
                        f.frameType(),
                        f.reasonCode(),
                        f.newChannel(),
                        f.isProtected(),
                        f.frameCount(),
                        f.signal(),
                        f.signalMin(),
                        f.signalMax(),
                        f.signalHistogram(),
                        f.reference(),
                        f.reference() == null ? null : f.signal() - f.reference()
                ));
            }

            charts.add(FrameSignalChartResponse.create(
                    chart.tapUuid(),
                    tapNames.computeIfAbsent(chart.tapUuid(), uuid ->
                            nzyme.getTapManager().findTap(uuid).map(Tap::name).orElse(null)),
                    chart.frequency(),
                    chart.frameCount(),
                    chart.truncated(),
                    references,
                    frames
            ));
        }

        return Response.ok(FrameSignalChartsResponse.create(
                result.sourceCount(),
                FrameSignalAnalysis.MAX_SOURCES,
                signalSources(result.sources(), tapUuids, organizationId, tenantId),
                threshold,
                charts,
                bucketing.bucketSizeMs() / 1000,
                FrameSignalAnalysis.DEFAULT_CHARTS,
                FrameSignalAnalysis.MAX_FRAME_RECORDS_PER_CHART,
                FrameSignalAnalysis.MAX_FRAME_RECORDS,
                result.truncated()
        )).build();
    }

    // Administrators can reach taps of other tenants. What is compared for a tenant comes from its own taps.
    protected List<UUID> tapsOfTenant(AuthenticatedUser user, String taps, UUID organizationId, UUID tenantId) {
        List<UUID> tapUuids = Lists.newArrayList(parseAndValidateTapIds(user, nzyme, taps));
        tapUuids.retainAll(nzyme.getTapManager().allTapUUIDsAccessibleByUser(user, organizationId, tenantId));

        return tapUuids;
    }

    private List<FrameSignalSourceResponse> signalSources(List<FrameSignalAnalysis.Source> sources,
                                                          List<UUID> tapUuids,
                                                          UUID organizationId,
                                                          UUID tenantId) {
        List<FrameSignalSourceResponse> responses = Lists.newArrayList();
        for (FrameSignalAnalysis.Source source : sources) {
            responses.add(FrameSignalSourceResponse.create(
                    source.address(),
                    source.isAccessPoint() ? "ACCESS_POINT" : "CLIENT",
                    buildMacResponse(source.address(), tapUuids, organizationId, tenantId),
                    source.frameCount(),
                    source.comparedFrameCount(),
                    source.mismatchFrameCount(),
                    source.difference(),
                    source.largestDifference()
            ));
        }

        return responses;
    }

    protected Response signalTransmitters(FrameSource source,
                                          SecurityContext sc,
                                          UUID organizationId,
                                          UUID tenantId,
                                          String timeRangeParameter,
                                          String taps,
                                          int limit,
                                          int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (limit > FrameSignalAnalysis.MAX_TRANSMITTERS_PER_PAGE) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        List<UUID> tapUuids = tapsOfTenant(getAuthenticatedUser(sc), taps, organizationId, tenantId);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        FrameSignalAnalysis.Transmitters result;
        try {
            result = nzyme.getDot11().frameSignalAnalysis().transmitters(source, timeRange, tapUuids, limit, offset);
        } catch (FrameSignalAnalysis.TooMuchDataException e) {
            return Response.status(422).build();
        }

        List<FrameSignalTransmitterResponse> transmitters = Lists.newArrayList();
        for (FrameSignalAnalysis.Transmitter transmitter : result.transmitters()) {
            transmitters.add(FrameSignalTransmitterResponse.create(
                    buildMacResponse(transmitter.address(), tapUuids, organizationId, tenantId),
                    transmitter.isAccessPoint() ? "ACCESS_POINT" : "CLIENT",
                    transmitter.frameCount(),
                    transmitter.receiverCount(),
                    transmitter.comparisonFrameCount(),
                    transmitter.lastSeen()
            ));
        }

        return Response.ok(FrameSignalTransmitterListResponse.create(result.total(), transmitters)).build();
    }

}
