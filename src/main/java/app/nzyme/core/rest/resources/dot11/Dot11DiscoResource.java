package app.nzyme.core.rest.resources.dot11;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.db.Dot11MacFrameCount;
import app.nzyme.core.dot11.db.BSSIDPairFrameCount;
import app.nzyme.core.dot11.db.DiscoHistogramEntry;
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
import app.nzyme.core.rest.responses.dot11.disco.DiscoHistogramValueResponse;
import app.nzyme.core.rest.responses.dot11.disco.DiscoMonitorMethodConfigurationResponse;
import app.nzyme.core.rest.responses.dot11.disco.Dot11DiscoMonitorAnomalyDetailsResponse;
import app.nzyme.core.rest.responses.dot11.disco.Dot11DiscoMonitorAnomalyListResponse;
import app.nzyme.core.rest.responses.shared.*;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import tools.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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

@Path("/api/dot11/disco")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class Dot11DiscoResource extends TapDataHandlingResource {

    private static final Logger LOG = LogManager.getLogger(TapDataHandlingResource.class);

    private enum ListType {
        SENDERS,
        RECEIVERS,
        PAIRS
    }

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/histogram")
    public Response histogram(@Context SecurityContext sc,
                              @QueryParam("disco_type") String type,
                              @QueryParam("time_range") @Valid String timeRangeParameter,
                              @QueryParam("taps") String taps,
                              @QueryParam("bssids") @Nullable List<String> bssids, // or monitoredNetworkId
                              @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        Dot11.DiscoType discoType;
        try {
            discoType = Dot11.DiscoType.valueOf(type.toUpperCase());
        } catch(IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        List<String> selectedBssids = null;
        if (bssids != null && !bssids.isEmpty()) {
            selectedBssids = bssids;
        }

        if (monitoredNetworkId != null) {
            Optional<MonitoredSSID> monitoredNetwork = nzyme.getDot11().findMonitoredSSID(monitoredNetworkId);
            if (monitoredNetwork.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            if (!entityAccessible(authenticatedUser, monitoredNetwork.get())) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }

            selectedBssids = nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(monitoredNetwork.get().id())
                    .stream()
                    .map(MonitoredBSSID::bssid)
                    .collect(Collectors.toList());
        }

        Map<DateTime, DiscoHistogramValueResponse> response = Maps.newTreeMap();
        for (DiscoHistogramEntry h : nzyme.getDot11()
                .getDiscoHistogram(discoType, timeRange, bucketing, tapUuids, selectedBssids)) {
            response.put(h.bucket(), DiscoHistogramValueResponse.create(h.bucket(), h.frameCount()));
        }

        return Response.ok(response).build();
    }

    @GET
    @Path("/lists/{list_type}")
    public Response list(@Context SecurityContext sc,
                         @QueryParam("organization_id") UUID organizationId,
                         @QueryParam("tenant_id") UUID tenantId,
                         @PathParam("list_type") @NotEmpty String listTypeParam,
                         @QueryParam("time_range") @Valid String timeRangeParameter,
                         @QueryParam("taps") String taps,
                         @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId,
                         @QueryParam("bssids") @Nullable String bssidsParam,
                         @QueryParam("limit") int limit,
                         @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        ListType listType;
        try {
            listType = ListType.valueOf(listTypeParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (monitoredNetworkId != null && bssidsParam != null) {
            // Not allowed to filter by both monitored network and specific BSSIDs.
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);

        List<String> selectedBssids = null;
        if (monitoredNetworkId != null) {
            Optional<MonitoredSSID> monitoredNetwork = nzyme.getDot11().findMonitoredSSID(monitoredNetworkId);
            if (monitoredNetwork.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            if (!entityAccessible(authenticatedUser, monitoredNetwork.get())) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }

            selectedBssids = nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(monitoredNetwork.get().id())
                    .stream()
                    .map(MonitoredBSSID::bssid)
                    .collect(Collectors.toList());
        }

        if (bssidsParam != null) {
            selectedBssids = Splitter.on(",").splitToList(bssidsParam);
        }

        long total;
        switch (listType) {
            case SENDERS:
                List<TwoColumnTableHistogramValueResponse> sendersValues = Lists.newArrayList();
                total = nzyme.getDot11().countDiscoTopSenders(timeRange, tapUuids, selectedBssids);
                for (Dot11MacFrameCount s : nzyme.getDot11().getDiscoTopSenders(timeRange, limit, offset, tapUuids, selectedBssids)) {
                    Optional<MacAddressContextEntry> macContext = nzyme.getContextService().findMacAddressContext(
                            s.mac(), organizationId, tenantId
                    );

                    sendersValues.add(TwoColumnTableHistogramValueResponse.create(
                            HistogramValueStructureResponse.create(
                                    s.mac(),
                                    HistogramValueType.DOT11_MAC,
                                    Dot11MacLinkMetadataResponse.create(
                                            nzyme.getDot11().getMacAddressMetadata(s.mac(), tapUuids).type(),
                                            Dot11MacAddressResponse.create(
                                                    s.mac(),
                                                    nzyme.getOuiService().lookup(s.mac()).orElse(null),
                                                    null,
                                                    macContext.map(macAddressContextEntry ->
                                                                    Dot11MacAddressContextResponse.create(
                                                                            macAddressContextEntry.name(),
                                                                            macAddressContextEntry.description(),
                                                                            macAddressContextEntry.notes()
                                                                    ))
                                                            .orElse(null)
                                            )
                                    )
                            ),
                            HistogramValueStructureResponse.create(
                                    s.frameCount(),
                                    HistogramValueType.INTEGER,
                                    null
                            )
                    ));
                }
                return Response.ok(TwoColumnTableHistogramResponse.create(total, true, sendersValues)).build();
            case RECEIVERS:
                List<TwoColumnTableHistogramValueResponse> receiversValues = Lists.newArrayList();
                total = nzyme.getDot11().countDiscoTopReceivers(timeRange, tapUuids, selectedBssids);
                for (Dot11MacFrameCount s : nzyme.getDot11().getDiscoTopReceivers(timeRange, limit, offset, tapUuids, selectedBssids)) {
                    Optional<MacAddressContextEntry> macContext = nzyme.getContextService().findMacAddressContext(
                            s.mac(), organizationId, tenantId
                    );

                    receiversValues.add(TwoColumnTableHistogramValueResponse.create(
                            HistogramValueStructureResponse.create(
                                    s.mac(),
                                    HistogramValueType.DOT11_MAC,
                                    Dot11MacLinkMetadataResponse.create(
                                            nzyme.getDot11().getMacAddressMetadata(s.mac(), tapUuids).type(),
                                            Dot11MacAddressResponse.create(
                                                    s.mac(),
                                                    nzyme.getOuiService().lookup(s.mac()).orElse(null),
                                                    null,
                                                    macContext.map(macAddressContextEntry ->
                                                                    Dot11MacAddressContextResponse.create(
                                                                            macAddressContextEntry.name(),
                                                                            macAddressContextEntry.description(),
                                                                            macAddressContextEntry.notes()
                                                                    ))
                                                            .orElse(null)
                                            )
                                    )
                            ),
                            HistogramValueStructureResponse.create(
                                    s.frameCount(),
                                    HistogramValueType.INTEGER,
                                    null
                            )
                    ));
                }
                return Response.ok(TwoColumnTableHistogramResponse.create(total, true, receiversValues)).build();
            case PAIRS:
                List<ThreeColumnTableHistogramValueResponse> pairsValues = Lists.newArrayList();
                total = nzyme.getDot11().countDiscoTopPairs(timeRange, tapUuids, selectedBssids);
                for (BSSIDPairFrameCount s : nzyme.getDot11().getDiscoTopPairs(timeRange, limit, offset, tapUuids, selectedBssids)) {
                    Optional<MacAddressContextEntry> senderMacContext = nzyme.getContextService().findMacAddressContext(
                            s.sender(), organizationId, tenantId
                    );

                    Optional<MacAddressContextEntry> receiverMacContext = nzyme.getContextService().findMacAddressContext(
                            s.receiver(), organizationId, tenantId
                    );

                    pairsValues.add(ThreeColumnTableHistogramValueResponse.create(
                            HistogramValueStructureResponse.create(
                                    s.sender(),
                                    HistogramValueType.DOT11_MAC,
                                    Dot11MacLinkMetadataResponse.create(
                                            nzyme.getDot11().getMacAddressMetadata(s.sender(), tapUuids).type(),
                                            Dot11MacAddressResponse.create(
                                                    s.sender(),
                                                    nzyme.getOuiService().lookup(s.sender()).orElse(null),
                                                    null,
                                                    senderMacContext.map(macAddressContextEntry ->
                                                                    Dot11MacAddressContextResponse.create(
                                                                            macAddressContextEntry.name(),
                                                                            macAddressContextEntry.description(),
                                                                            macAddressContextEntry.notes()
                                                                    ))
                                                            .orElse(null)
                                            )
                                    )
                            ),
                            HistogramValueStructureResponse.create(
                                    s.receiver(),
                                    HistogramValueType.DOT11_MAC,
                                    Dot11MacLinkMetadataResponse.create(
                                            nzyme.getDot11().getMacAddressMetadata(s.receiver(), tapUuids).type(),
                                            Dot11MacAddressResponse.create(
                                                    s.receiver(),
                                                    nzyme.getOuiService().lookup(s.receiver()).orElse(null),
                                                    null,
                                                    receiverMacContext.map(macAddressContextEntry ->
                                                                    Dot11MacAddressContextResponse.create(
                                                                            macAddressContextEntry.name(),
                                                                            macAddressContextEntry.description(),
                                                                            macAddressContextEntry.notes()
                                                                    ))
                                                            .orElse(null)
                                            )
                                    )
                            ),
                            HistogramValueStructureResponse.create(
                                    s.frameCount(),
                                    HistogramValueType.INTEGER,
                                    null
                            ),
                            s.sender() + " ⇨ " + s.receiver()
                    ));
                }

                return Response.ok(ThreeColumnTableHistogramResponse.create(total, true, pairsValues)).build();
            default:
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }


    @GET
    @Path("/config/detection")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    public Response getDetectionConfig(@Context SecurityContext sc,
                                       @QueryParam("monitored_network_id") @NotNull UUID monitoredNetworkId) {
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
    public Response setDetectionConfig(@Context SecurityContext sc,
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
    public Response simulateDetectionConfig(@Context SecurityContext sc,
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
