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

    private enum ListType {
        SENDERS,
        RECEIVERS,
        PAIRS
    }

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/histogram")
    @Operation(operationId = "findDiscoHistogram", summary = "Get disconnection frame histogram",
            description = "Returns an object keyed by bucket timestamp with the number of disconnection frames in each "
                    + "bucket. Narrow the result to a set of BSSIDs, or to the BSSIDs of a monitored network. If you "
                    + "pass a monitored network, it takes precedence over the BSSID list.")
    @ApiResponse(responseCode = "200", description = "Histogram found. The response is an object keyed by bucket timestamp.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "400", description = "Unknown disconnection frame type.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The monitored network is not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found.", content = @Content)
    public Response histogram(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Type of disconnection frames to count, for example DISCONNECTION.") @QueryParam("disco_type") String type,
                              @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                              @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                              @Parameter(description = "BSSIDs to narrow the result to. Ignored if a monitored network is passed.") @QueryParam("bssids") @Nullable List<String> bssids, // or monitoredNetworkId
                              @Parameter(description = "UUID of a monitored network. Narrows the result to all BSSIDs of that network.") @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId) {
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
    @Operation(operationId = "findDiscoTopList", summary = "Get top senders, receivers or pairs of disco frames",
            description = "Returns a table of the most active sources, targets or sender and receiver pairs of "
                    + "disconnection frames. The senders and receivers lists return a two column table, the pairs list "
                    + "returns a three column table. You can narrow the result by monitored network or by BSSIDs, but "
                    + "not by both. The page size cannot exceed 250.")
    @ApiResponse(responseCode = "200", description = "List found. The pairs list type returns a three column table instead.",
            content = @Content(schema = @Schema(implementation = TwoColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Both a monitored network and a BSSID list were passed.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The requested page size is larger than 250, or the monitored "
            + "network is not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Unknown list type, monitored network not found, or organization "
            + "or tenant not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The list type could not be handled.", content = @Content)
    public Response list(@Parameter(hidden = true) @Context SecurityContext sc,
                         @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                         @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                         @Parameter(description = "List to return: senders, receivers or pairs.") @PathParam("list_type") @NotEmpty String listTypeParam,
                         @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                         @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                         @Parameter(description = "UUID of a monitored network. Narrows the result to all BSSIDs of that network.") @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId,
                         @Parameter(description = "Comma separated list of BSSIDs to narrow the result to.") @QueryParam("bssids") @Nullable String bssidsParam,
                         @Parameter(description = "Page size. Cannot exceed 250.") @QueryParam("limit") int limit,
                         @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
