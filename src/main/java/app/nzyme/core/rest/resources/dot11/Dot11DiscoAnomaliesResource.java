package app.nzyme.core.rest.resources.dot11;

import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.dot11.Dot11ReasonCodes;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.dot11.disco.ConnectionFrameType;
import app.nzyme.core.dot11.disco.DiscoAnomalies;
import app.nzyme.core.dot11.disco.DiscoAnomalyDetector;
import app.nzyme.core.dot11.disco.DiscoDetectionRegistryKeys;
import app.nzyme.core.registry.RegistryChangeValidator;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.GenericConfigurationUpdateRequest;
import app.nzyme.core.rest.responses.dot11.disco.*;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.joda.time.DateTime;

import java.util.*;

@Path("/api/dot11/disco/anomalies")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Disconnection Anomalies", description = "Nzyme looks for the patterns that forced disconnections "
        + "leave behind: disconnection frames that a real access point or client would not send that way, "
        + "frames that arrive with another signal strength than the access point they name, authentication "
        + "floods and unexpected EAPOL frames. Each "
        + "pattern a tap observed around one access point, and one client where it concerns a single client, is "
        + "recorded as an anomaly that stays open while the pattern continues. Anomalies are recorded per tap and "
        + "are retention cleaned with the other WiFi data. Detection runs every minute and records at most 200 "
        + "new anomalies per run and tenant, those of monitored networks first.")
public class Dot11DiscoAnomaliesResource extends Dot11BssidScopedResource {

    private static final String MANAGE_PERMISSION = "dot11_monitoring_manage";

    private static final int MAX_PAGE_SIZE = 250;
    private static final int EVIDENCE_MARGIN_SECONDS = 60;

    private static final String FILTERS_DESCRIPTION = "JSON encoded filter definition as produced by the web interface filter builder. Fields: anomaly_type, bssid, client, frame_count.";
    private static final String TAPS_DESCRIPTION = "Comma separated list of tap UUIDs to include, or * for all taps the user can access. Without it, the result is empty.";
    private static final String BSSIDS_DESCRIPTION = "Comma separated list of BSSIDs. Narrows the result to anomalies of these access points. Ignored if a monitored network is passed.";
    private static final String MONITORED_NETWORK_DESCRIPTION = "UUID of a monitored network. Narrows the result to anomalies of all BSSIDs of that network.";

    @GET
    @Operation(operationId = "findDiscoAnomalies", summary = "List disconnection anomalies",
            description = "Returns the anomalies that were going on at any point inside the time range, with the "
                    + "number of anomalies per type. Sorts by the time an anomaly was last seen, descending, unless "
                    + "you pass a sorting column and direction. An anomaly lists at most 32 reason codes and 25 "
                    + "sample clients. The detection keeps the 10 pairs of stations with the most frames per tap, "
                    + "access point and detection in one run, and records at most 200 new anomalies per run.")
    @ApiResponse(responseCode = "200", description = "Anomalies found.",
            content = @Content(schema = @Schema(implementation = DiscoAnomalyListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction, or page size above 250.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The monitored network is not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization, tenant or monitored network not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = FILTERS_DESCRIPTION) @QueryParam("filters") String filtersParameter,
                            @Parameter(description = TAPS_DESCRIPTION) @QueryParam("taps") String taps,
                            @Parameter(description = BSSIDS_DESCRIPTION) @QueryParam("bssids") @Nullable String bssidsParam,
                            @Parameter(description = MONITORED_NETWORK_DESCRIPTION) @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId,
                            @Parameter(description = "Sorting column: ANOMALY_TYPE, BSSID, CLIENT, FRAME_COUNT, FIRST_SEEN or LAST_SEEN. Omit to sort by LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size. Cannot exceed 250.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = tapsOfTenant(authenticatedUser, taps, organizationId, tenantId);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

        if (limit > MAX_PAGE_SIZE || limit < 0 || offset < 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        DiscoAnomalies.OrderColumn orderColumn = DiscoAnomalies.OrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = DiscoAnomalies.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        BssidSelection bssids = resolveBssids(authenticatedUser, bssidsParam, monitoredNetworkId);
        if (bssids.error() != null) {
            return bssids.error();
        }

        DiscoAnomalies anomalies = nzyme.getDot11().discoAnomalies();
        long total = anomalies.count(timeRange, filters, bssids.bssids(), tapUuids);

        List<DiscoAnomalyTypeCountResponse> types = Lists.newArrayList();
        for (DiscoAnomalies.TypeCount count : anomalies.countByType(timeRange, filters, bssids.bssids(), tapUuids)) {
            types.add(DiscoAnomalyTypeCountResponse.create(
                    count.type().name(), count.type().getTitle(), count.anomalies()
            ));
        }

        Map<String, List<MonitoredSSID>> monitored = nzyme.getDot11().discoAnomalies()
                .monitoredNetworksByBssid(organizationId, tenantId, false);
        Map<UUID, String> tapNames = Maps.newHashMap();
        List<DiscoAnomalyDetailsResponse> result = Lists.newArrayList();
        for (DiscoAnomalies.Anomaly anomaly : anomalies.findAll(
                timeRange, filters, bssids.bssids(), tapUuids, orderColumn, orderDirection, limit, offset)) {
            result.add(toResponse(anomaly, tapUuids, tapNames, monitored, organizationId, tenantId));
        }

        return Response.ok(DiscoAnomalyListResponse.create(total, types, result)).build();
    }

    @GET
    @Path("/histogram")
    @Operation(operationId = "findDiscoAnomaliesHistogram", summary = "Get disconnection anomaly histogram",
            description = "Returns an object keyed by bucket timestamp with the number of anomalies that were first "
                    + "seen in each bucket, in total and per type. The bucket size is chosen automatically from "
                    + "the time range.")
    @ApiResponse(responseCode = "200", description = "Histogram found. The response is an object keyed by bucket timestamp.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "401", description = "The monitored network is not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found.", content = @Content)
    public Response histogram(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                              @Parameter(description = FILTERS_DESCRIPTION) @QueryParam("filters") String filtersParameter,
                              @Parameter(description = TAPS_DESCRIPTION) @QueryParam("taps") String taps,
                              @Parameter(description = BSSIDS_DESCRIPTION) @QueryParam("bssids") @Nullable String bssidsParam,
                              @Parameter(description = MONITORED_NETWORK_DESCRIPTION) @QueryParam("monitored_network_id") @Nullable UUID monitoredNetworkId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

        BssidSelection bssids = resolveBssids(authenticatedUser, bssidsParam, monitoredNetworkId);
        if (bssids.error() != null) {
            return bssids.error();
        }

        Map<DateTime, Map<String, Long>> buckets = Maps.newTreeMap();
        for (DiscoAnomalies.HistogramBucket bucket : nzyme.getDot11().discoAnomalies().histogram(
                timeRange, Bucketing.getConfig(timeRange), filters, bssids.bssids(), tapUuids)) {
            buckets.computeIfAbsent(bucket.bucket(), b -> Maps.newTreeMap())
                    .put(bucket.type().name(), bucket.anomalies());
        }

        Map<DateTime, DiscoAnomalyHistogramValueResponse> response = Maps.newTreeMap();
        buckets.forEach((bucket, types) -> response.put(bucket, DiscoAnomalyHistogramValueResponse.create(
                bucket, types.values().stream().mapToLong(Long::longValue).sum(), types
        )));

        return Response.ok(response).build();
    }

    @GET
    @Path("/show/{uuid}")
    @Operation(operationId = "findDiscoAnomaly", summary = "Get a disconnection anomaly",
            description = "Returns one anomaly. It is only found if a tap of the passed tenant that the calling "
                    + "user can access recorded it.")
    @ApiResponse(responseCode = "200", description = "Anomaly found.",
            content = @Content(schema = @Schema(implementation = DiscoAnomalyDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Anomaly, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Anomaly UUID.") @PathParam("uuid") UUID uuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<UUID> tapUuids = nzyme.getTapManager()
                .allTapUUIDsAccessibleByUser(getAuthenticatedUser(sc), organizationId, tenantId);

        Optional<DiscoAnomalies.Anomaly> anomaly = nzyme.getDot11().discoAnomalies().findOne(uuid, tapUuids);
        if (anomaly.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(toResponse(anomaly.get(), tapUuids, Maps.newHashMap(),
                nzyme.getDot11().discoAnomalies().monitoredNetworksByBssid(organizationId, tenantId, false),
                organizationId, tenantId)).build();
    }

    @GET
    @Path("/show/{uuid}/frames/connection")
    @Operation(operationId = "findDiscoAnomalyConnectionFrames", summary = "List the connection frames around a disconnection anomaly",
            description = "Returns the authentication, association and EAPOL frames the tap of the anomaly recorded "
                    + "for its access point, and its client if it has one, from one minute before the anomaly was "
                    + "first seen until one minute after it was last seen, oldest first. Taps combine frames that "
                    + "share type, addresses, status and channel within a report interval into one record with a "
                    + "frame count.")
    @ApiResponse(responseCode = "200", description = "Frames found.",
            content = @Content(schema = @Schema(implementation = ConnectionFrameListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Page size above 250.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Anomaly, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response connectionFrames(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                     @Parameter(description = "Anomaly UUID.") @PathParam("uuid") UUID uuid,
                                     @Parameter(description = "Page size. Cannot exceed 250.") @QueryParam("limit") int limit,
                                     @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (limit > MAX_PAGE_SIZE || limit < 0 || offset < 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        List<UUID> tapUuids = nzyme.getTapManager()
                .allTapUUIDsAccessibleByUser(getAuthenticatedUser(sc), organizationId, tenantId);

        DiscoAnomalies anomalies = nzyme.getDot11().discoAnomalies();
        Optional<DiscoAnomalies.Anomaly> anomaly = anomalies.findOne(uuid, tapUuids);
        if (anomaly.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DiscoAnomalies.Anomaly a = anomaly.get();
        List<UUID> anomalyTap = List.of(a.tapUuid());
        List<String> frameTypes = Arrays.stream(ConnectionFrameType.values()).map(Enum::name).toList();
        DateTime from = a.firstSeen().minusSeconds(EVIDENCE_MARGIN_SECONDS);
        DateTime to = a.lastSeen().plusSeconds(EVIDENCE_MARGIN_SECONDS);

        long total = anomalies.countConnectionFrames(a.tapUuid(), a.bssid(), a.client(), frameTypes, from, to);

        List<ConnectionFrameDetailsResponse> frames = Lists.newArrayList();
        for (DiscoAnomalies.ConnectionFrame f : anomalies.findConnectionFrames(
                a.tapUuid(), a.bssid(), a.client(), frameTypes, from, to, limit, offset)) {
            frames.add(ConnectionFrameDetailsResponse.create(
                    f.id(),
                    f.frameType(),
                    buildMacResponse(f.transmitter(), anomalyTap, organizationId, tenantId),
                    buildMacResponse(f.receiver(), anomalyTap, organizationId, tenantId),
                    buildMacResponse(f.bssid(), anomalyTap, organizationId, tenantId),
                    f.algorithm(),
                    f.transaction(),
                    f.statusCode(),
                    f.frequency(),
                    f.signalStrengthAverage(),
                    f.frameCount(),
                    f.retryFrames(),
                    f.firstSeen(),
                    f.lastSeen()
            ));
        }

        return Response.ok(ConnectionFrameListResponse.create(total, frames)).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "findDiscoAnomalyDetectionConfiguration", summary = "Get the disconnection anomaly detection configuration of a tenant",
            description = "Returns the detection settings of the tenant in three groups: general settings, "
                    + "detections on disconnection frames, and detections on the authentication and EAPOL frames "
                    + "of clients that connect. Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = DiscoDetectionConfigurationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response getConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<ConfigurationEntryResponse> general = Lists.newArrayList();
        general.add(booleanEntry(DiscoDetectionRegistryKeys.MONITORED_NETWORKS_ONLY, "Only record anomalies of monitored networks", organizationId, tenantId));
        general.add(booleanEntry(DiscoDetectionRegistryKeys.FRAME_OVERFLOW_ENABLED, "Too many different frames", organizationId, tenantId));

        List<ConfigurationEntryResponse> frames = Lists.newArrayList();
        frames.add(booleanEntry(DiscoDetectionRegistryKeys.UNPROTECTED_FRAME_ENABLED, "Unprotected frame on protected network", organizationId, tenantId));
        frames.add(booleanEntry(DiscoDetectionRegistryKeys.UNPROTECTED_FRAME_IGNORE_MISSING_STATE, "Unprotected frame: Ignore reason codes 6 and 7", organizationId, tenantId));
        frames.add(booleanEntry(DiscoDetectionRegistryKeys.MIRRORED_FRAMES_ENABLED, "Frames in both directions", organizationId, tenantId));
        frames.add(booleanEntry(DiscoDetectionRegistryKeys.REPEATED_FRAMES_ENABLED, "Repeated transmissions", organizationId, tenantId));
        frames.add(numberEntry(DiscoDetectionRegistryKeys.REPEATED_FRAMES_MINIMUM_SEQUENCE_NUMBERS, "Repeated transmissions: Minimum sequence numbers within ten seconds", organizationId, tenantId));
        frames.add(numberEntry(DiscoDetectionRegistryKeys.REPEATED_FRAMES_MINIMUM_FRAMES, "Repeated transmissions: Minimum frames within ten seconds", organizationId, tenantId));
        frames.add(booleanEntry(DiscoDetectionRegistryKeys.SIGNAL_MISMATCH_ENABLED, "Signal strength mismatch", organizationId, tenantId));
        frames.add(numberEntry(DiscoDetectionRegistryKeys.SIGNAL_MISMATCH_MINIMUM_DIFFERENCE, "Signal strength mismatch: Minimum difference to the beacons of the access point (dB)", organizationId, tenantId));

        List<ConfigurationEntryResponse> connections = Lists.newArrayList();
        connections.add(booleanEntry(DiscoDetectionRegistryKeys.AUTHENTICATION_FLOOD_ENABLED, "Authentication flood", organizationId, tenantId));
        connections.add(numberEntry(DiscoDetectionRegistryKeys.AUTHENTICATION_FLOOD_MINIMUM_TRANSMITTERS, "Authentication flood: Minimum transmitters per minute", organizationId, tenantId));
        connections.add(booleanEntry(DiscoDetectionRegistryKeys.EAPOL_FLOOD_ENABLED, "EAPOL flood", organizationId, tenantId));
        connections.add(numberEntry(DiscoDetectionRegistryKeys.EAPOL_FLOOD_MINIMUM_FRAMES, "EAPOL flood: Minimum frames per minute", organizationId, tenantId));
        connections.add(booleanEntry(DiscoDetectionRegistryKeys.EAP_FAILURE_ENABLED, "Unexpected EAP failure", organizationId, tenantId));
        connections.add(numberEntry(DiscoDetectionRegistryKeys.EAP_FAILURE_MINIMUM_FAILURES, "Unexpected EAP failure: Minimum failures per minute on networks that use EAP", organizationId, tenantId));

        return Response.ok(DiscoDetectionConfigurationResponse.create(general, frames, connections)).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "updateDiscoAnomalyDetectionConfiguration", summary = "Update the disconnection anomaly detection configuration of a tenant",
            description = "Updates one or more detection settings of the tenant. Accepted keys are the keys "
                    + "returned by the configuration endpoint. A change applies from the next detection run, "
                    + "which looks at the last five minutes again, and does not touch recorded anomalies. Requires "
                    + "the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The change is empty, contains a key that is not part of the detection configuration, or a value violates the constraints of its configuration key.", content = @Content)
    public Response setConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                     @RequestBody(description = "Map of configuration keys and their new values, wrapped in a change field.", required = true, content = @Content(mediaType = "application/json"))
                                     @Valid GenericConfigurationUpdateRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (req.change().isEmpty()) {
            return Response.status(422).build();
        }

        Optional<List<RegistryChangeValidator.Change>> changes = RegistryChangeValidator
                .allowing(DiscoDetectionRegistryKeys.ALL)
                .validate(req.change());

        if (changes.isEmpty()) {
            return Response.status(422).build();
        }

        // The constraints take any number, but the thresholds are read as whole numbers.
        for (RegistryChangeValidator.Change c : changes.get()) {
            boolean isThreshold = Arrays.stream(DiscoDetectionRegistryKeys.ALL)
                    .anyMatch(key -> key.key().equals(c.key()) && key.defaultValue().orElse("").matches("-?\\d+"));

            if (isThreshold && !c.value().matches("\\d{1,6}")) {
                return Response.status(422).build();
            }
        }

        for (RegistryChangeValidator.Change c : changes.get()) {
            nzyme.getDatabaseCoreRegistry().setValue(c.key(), c.value(), organizationId, tenantId);
        }

        return Response.ok().build();
    }

    private DiscoAnomalyDetailsResponse toResponse(DiscoAnomalies.Anomaly anomaly,
                                                   List<UUID> tapUuids,
                                                   Map<UUID, String> tapNames,
                                                   Map<String, List<MonitoredSSID>> monitored,
                                                   UUID organizationId,
                                                   UUID tenantId) {
        String tapName = tapNames.computeIfAbsent(anomaly.tapUuid(), uuid ->
                nzyme.getTapManager().findTap(uuid).map(Tap::name).orElse(null));

        List<DiscoAnomalyReasonCodeResponse> reasonCodes = Lists.newArrayList();
        for (Integer code : anomaly.reasonCodes()) {
            reasonCodes.add(DiscoAnomalyReasonCodeResponse.create(code, Dot11ReasonCodes.name(code)));
        }

        List<MonitoredSSID> networks = monitored.get(anomaly.bssid());
        MonitoredSSID network = networks == null ? null : networks.get(0);

        return DiscoAnomalyDetailsResponse.create(
                anomaly.uuid(),
                anomaly.tapUuid(),
                tapName,
                anomaly.type().name(),
                anomaly.type().getTitle(),
                buildMacResponse(anomaly.bssid(), tapUuids, organizationId, tenantId),
                anomaly.client() == null ? null
                        : buildMacResponse(anomaly.client(), tapUuids, organizationId, tenantId),
                anomaly.firstSeen(),
                anomaly.lastSeen(),
                anomaly.lastSeen().isAfter(DateTime.now().minusMinutes(DiscoAnomalyDetector.MERGE_GAP_MINUTES)),
                anomaly.frameCount(),
                anomaly.framesFromBssid(),
                anomaly.peak(),
                anomaly.threshold(),
                anomaly.signalStrength(),
                anomaly.referenceSignalStrength(),
                reasonCodes,
                anomaly.frameTypes(),
                anomaly.sampleClients(),
                network == null ? null : network.uuid(),
                network == null ? null : network.ssid()
        );
    }

    private ConfigurationEntryResponse booleanEntry(RegistryKey key, String title, UUID organizationId, UUID tenantId) {
        return ConfigurationEntryResponse.create(
                key.key(),
                title,
                nzyme.getDot11().discoAnomalies().booleanValue(key, organizationId, tenantId),
                ConfigurationEntryValueType.BOOLEAN,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                null
        );
    }

    private ConfigurationEntryResponse numberEntry(RegistryKey key, String title, UUID organizationId, UUID tenantId) {
        return ConfigurationEntryResponse.create(
                key.key(),
                title,
                nzyme.getDot11().discoAnomalies().intValue(key, organizationId, tenantId),
                ConfigurationEntryValueType.NUMBER,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                null
        );
    }

}
