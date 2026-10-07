package app.nzyme.core.rest.resources.uav;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.geo.GeoCenter;
import app.nzyme.core.geo.HaversineDistance;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.requests.CreateUavCustomTypeRequest;
import app.nzyme.core.rest.requests.UavMonitoringConfigurationRequest;
import app.nzyme.core.rest.requests.UpdateUavCustomTypeRequest;
import app.nzyme.core.rest.responses.shared.ClassificationResponse;
import app.nzyme.core.rest.responses.taps.TapHighLevelInformationDetailsResponse;
import app.nzyme.core.rest.responses.uav.*;
import app.nzyme.core.rest.responses.uav.enums.*;
import app.nzyme.core.rest.responses.uav.monitoring.UavMonitoringSettingsResponse;
import app.nzyme.core.rest.responses.uav.types.UavConnectTypeDetailsResponse;
import app.nzyme.core.rest.responses.uav.types.UavConnectTypeListResponse;
import app.nzyme.core.rest.responses.uav.types.UavCustomTypeDetailsResponse;
import app.nzyme.core.rest.responses.uav.types.UavCustomTypeListResponse;
import app.nzyme.core.shared.Classification;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.uav.UavRegistryKeys;
import app.nzyme.core.uav.db.UavEntry;
import app.nzyme.core.uav.db.UavTimelineEntry;
import app.nzyme.core.uav.db.UavTypeEntry;
import app.nzyme.core.uav.db.UavVectorEntry;
import app.nzyme.core.uav.types.ConnectUavModel;
import app.nzyme.core.uav.types.UavTypeMatch;
import app.nzyme.core.uav.types.UavTypeMatchType;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.Tools;
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
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/api/uav")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "UAV", description = "Unmanned aerial vehicles that your taps detected by decoding the Remote ID "
        + "broadcasts of the UAV. These endpoints return detected UAVs with their flight vectors and timelines, and "
        + "manage classifications, custom UAV types and the monitoring configuration. Nzyme is not a Counter UAS "
        + "platform and only sees UAVs that broadcast Remote ID.")
public class UavResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/uavs/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "findUavs", summary = "List UAVs of a tenant",
            description = "Returns all UAVs that the selected taps detected in the time range, including their "
                    + "designation, last known position, classification and the distance in feet to each tap. The "
                    + "response also carries a suggested map center, which is the geographic center of all UAV "
                    + "positions.")
    @ApiResponse(responseCode = "200", description = "UAVs found.",
            content = @Content(schema = @Schema(implementation = UavListResponse.class)))
    @ApiResponse(responseCode = "401", description = "Organization or tenant not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                            @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        long total = nzyme.getUav().countAllUavs(timeRange, taps);
        List<UavSummaryResponse> uavs = Lists.newArrayList();

        List<UavTypeEntry> customTypes = nzyme.getUav().findAllCustomTypes(organizationId, tenantId);
        List<GeoCenter.LatLon> uavPositions = Lists.newArrayList();

        for (UavEntry uav : nzyme.getUav().findAllUavsOfTenant(timeRange, limit, offset, organizationId, tenantId, taps)) {
            uavs.add(uavEntryToSummaryResponse(uav, nzyme.getUav().matchUavType(customTypes, uav.idSerial()), taps));

            if (uav.latitude() != null && uav.longitude() != null) {
                uavPositions.add(new GeoCenter.LatLon(uav.latitude(), uav.longitude()));
            }
        }

        UavMapCenterResponse mapCenterResponse;

        if (!uavPositions.isEmpty()) {
            GeoCenter.LatLon geoCenter = GeoCenter.center(uavPositions);
            mapCenterResponse = UavMapCenterResponse.create(
                    13, geoCenter.latDeg, geoCenter.lonDeg
            );
        } else {
            mapCenterResponse = UavMapCenterResponse.create(0, 0, 0);
        }

        return Response.ok(UavListResponse.create(total, mapCenterResponse, uavs)).build();
    }

    @GET
    @Path("/uavs/organization/{organization_id}/tenant/{tenant_id}/show/{identifier}")
    @Operation(operationId = "findUav", summary = "Get a UAV",
            description = "Returns the last known state of the UAV, the matching custom or Nzyme Connect type and "
                    + "the distance in feet to each of the selected taps. The designation is three words derived "
                    + "from the UAV identifier and stays the same for the same UAV.")
    @ApiResponse(responseCode = "200", description = "UAV found.",
            content = @Content(schema = @Schema(implementation = UavDetailsResponse.class)))
    @ApiResponse(responseCode = "401", description = "Organization or tenant not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "UAV not found, or not seen by any of the selected taps.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Identifier of the UAV as reported by the taps.") @PathParam("identifier") String uavIdentifier,
                            @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        Optional<UavEntry> uav = nzyme.getUav().findUav(uavIdentifier, organizationId, tenantId, taps);

        if (uav.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<UavTypeMatch> uavType = nzyme.getUav().matchUavType(uav.get().idSerial(), tenantId, organizationId);

        return Response.ok(UavDetailsResponse.create(
                uavEntryToSummaryResponse(uav.get(), uavType, taps)
        )).build();
    }

    @GET
    @Path("/uavs/organization/{organization_id}/tenant/{tenant_id}/show/{identifier}/timelines")
    @Operation(operationId = "findUavTimelines", summary = "List timelines of a UAV",
            description = "A timeline is one continuous period in which the UAV was observed. For each timeline "
                    + "the response includes the duration, whether it is still active, and the shortest and longest "
                    + "distance in feet between the UAV and any of the selected taps.")
    @ApiResponse(responseCode = "200", description = "Timelines found.",
            content = @Content(schema = @Schema(implementation = UavTimelineListResponse.class)))
    @ApiResponse(responseCode = "401", description = "Organization or tenant not accessible by the calling user.", content = @Content)
    public Response findTimelines(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Identifier of the UAV as reported by the taps.") @PathParam("identifier") String uavIdentifier,
                                  @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                  @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds,
                                  @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                  @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                  @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<UUID> tapUuids = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        List<Tap> taps = Lists.newArrayList();
        for (UUID uuid : tapUuids) {
            Optional<Tap> tap = nzyme.getTapManager().findTap(uuid);
            tap.ifPresent(taps::add);
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        long count = nzyme.getUav().countTimelines(uavIdentifier, timeRange, organizationId, tenantId);
        List<UavTimelineDetailsResponse> timelines = Lists.newArrayList();
        for (UavTimelineEntry timeline : nzyme.getUav()
                .findUavTimelines(uavIdentifier, timeRange, organizationId, tenantId, limit, offset)) {
            // Fetch all vectors of this timeline and calculate max/min distance from selected taps.
            Double minDistance = null;
            Double maxDistance = null;

            for (UavVectorEntry vector : nzyme.getUav()
                    .findVectorsOfTimeline(uavIdentifier, organizationId, tenantId,
                            timeline.seenFrom(), timeline.seenTo())) {
                if (vector.latitude() != null && vector.longitude() != null) {
                    for (Tap tap : taps) {
                        if (tap.latitude() == null || tap.longitude() == null) {
                            continue;
                        }

                        double distanceFeet = HaversineDistance.haversine(
                                tap.latitude(), tap.longitude(), vector.latitude(), vector.longitude()
                        )*3.28084;

                        if (minDistance == null || distanceFeet < minDistance) {
                            minDistance = distanceFeet;
                        }

                        if (maxDistance == null || distanceFeet > maxDistance) {
                            maxDistance = distanceFeet;
                        }
                    }
                }
            }


            Duration duration = new Duration(timeline.seenFrom(), timeline.seenTo());

            timelines.add(UavTimelineDetailsResponse.create(
                    timeline.seenTo().isAfter(DateTime.now().minusMinutes(5)),
                    timeline.uuid(),
                    timeline.seenFrom(),
                    timeline.seenTo(),
                    maxDistance,
                    minDistance,
                    duration.getStandardSeconds(),
                    Tools.durationToHumanReadable(duration)
            ));
        }

        return Response.ok(UavTimelineListResponse.create(count, timelines)).build();
    }

    @GET
    @Path("/uavs/organization/{organization_id}/tenant/{tenant_id}/show/{identifier}/timelines/show/{timeline_id}")
    @Operation(operationId = "findUavTimelineVectors", summary = "List flight vectors of a UAV timeline",
            description = "Returns every position the UAV reported during this timeline, with speed, altitude and "
                    + "accuracy values. Altitude is reported as pressure altitude and geodetic altitude, plus a "
                    + "height with the reference it was measured against. Vectors without a latitude or longitude "
                    + "are left out.")
    @ApiResponse(responseCode = "200", description = "Vectors found.",
            content = @Content(schema = @Schema(implementation = UavVectorListResponse.class)))
    @ApiResponse(responseCode = "401", description = "Organization or tenant not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Timeline not found.", content = @Content)
    public Response getTimelineVectors(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @Parameter(description = "Identifier of the UAV as reported by the taps.") @PathParam("identifier") String uavIdentifier,
                                       @Parameter(description = "Timeline UUID.") @PathParam("timeline_id") UUID timelineId,
                                       @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                       @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                       @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Get Timeline
        Optional<UavTimelineEntry> timeline = nzyme.getUav()
                .findUavTimeline(uavIdentifier, timelineId, organizationId, tenantId);

        // Does the timeline exist?
        if (timeline.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Query vectors for UAV during timeline duration.
        List<UavVectorDetailsResponse> vectors =  Lists.newArrayList();
        for (UavVectorEntry v : nzyme.getUav()
                .findVectorsOfTimeline(uavIdentifier, organizationId, tenantId,
                        timeline.get().seenFrom(), timeline.get().seenTo())) {
            if (v.latitude() == null || v.longitude() == null) {
                continue;
            }

            vectors.add(UavVectorDetailsResponse.create(
                    v.timestamp(),
                    v.latitude(),
                    v.longitude(),
                    v.operationalStatus(),
                    v.groundTrack(),
                    v.speed(),
                    v.verticalSpeed(),
                    v.altitudePressure(),
                    v.altitudeGeodetic(),
                    v.heightType(),
                    v.height(),
                    v.accuracyHorizontal(),
                    v.accuracyVertical(),
                    v.accuracyBarometer(),
                    v.accuracySpeed()
            ));
        }

        return Response.ok(UavVectorListResponse.create(vectors.size(), vectors)).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "uav_monitoring_manage" })
    @Path("/uavs/organization/{organization_id}/tenant/{tenant_id}/show/{identifier}/classify/{classification}")
    @Operation(operationId = "updateUavClassification", summary = "Classify a UAV",
            description = "Sets the manual classification of the UAV to FRIENDLY, NEUTRAL, HOSTILE or UNKNOWN. The "
                    + "default classification of a matching UAV type, your own or one from Nzyme Connect, always "
                    + "takes precedence over the manual classification. Requires the uav_monitoring_manage feature "
                    + "permission.")
    @ApiResponse(responseCode = "200", description = "Classification updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The classification is unknown.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response classifyUav(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                @Parameter(description = "Identifier of the UAV as reported by the taps.") @PathParam("identifier") String uavIdentifier,
                                @Parameter(description = "Classification to set. One of FRIENDLY, NEUTRAL, HOSTILE or UNKNOWN.") @PathParam("classification") String c) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Update classification.
        Classification classification;
        try {
            classification = Classification.valueOf(c);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        nzyme.getUav().setUavClassification(uavIdentifier, organizationId, tenantId, classification);

        return Response.ok().build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY)
    @Path("/uavs/organization/{organization_id}/tenant/{tenant_id}/types/custom")
    @Operation(operationId = "findUavTypes", summary = "List custom UAV types of a tenant",
            description = "A UAV type matches a detected UAV by its serial number and enriches it with a make, a "
                    + "model, a name and a default classification. Your own types are matched before the types "
                    + "Nzyme Connect provides, so they take priority.")
    @ApiResponse(responseCode = "200", description = "Custom UAV types found.",
            content = @Content(schema = @Schema(implementation = UavCustomTypeListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAllCustomTypes(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                       @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                       @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                       @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long count = nzyme.getUav().countAllCustomTypes(organizationId, tenantId);
        List<UavCustomTypeDetailsResponse> types = Lists.newArrayList();
        for (UavTypeEntry type : nzyme.getUav().findAllCustomTypes(organizationId, tenantId, limit, offset)) {
            types.add(UavCustomTypeDetailsResponse.create(
                    type.uuid(),
                    type.organizationId(),
                    type.tenantId(),
                    type.matchType(),
                    type.matchValue(),
                    type.defaultClassification(),
                    type.type(),
                    type.name(),
                    type.model(),
                    type.createdAt(),
                    type.updatedAt()
            ));
        }

        return Response.ok(UavCustomTypeListResponse.create(count, types)).build();
    }

    // Organization and tenant ID are not used and only here to keep API paths consistent.
    @GET
    @RESTSecured(value = PermissionLevel.ANY)
    @Path("/uavs/organization/{organization_id}/tenant/{tenant_id}/types/connect")
    @Operation(operationId = "findUavConnectTypes", summary = "List UAV types from Nzyme Connect",
            description = "Returns the UAV models that Nzyme Connect knows about. Responds with an empty list if "
                    + "this node is not connected to Nzyme Connect or no models are available. The organization "
                    + "and tenant in the path are ignored and only exist to keep the API paths consistent.")
    @ApiResponse(responseCode = "200", description = "UAV types found, or Nzyme Connect is not available.",
            content = @Content(schema = @Schema(implementation = UavConnectTypeListResponse.class)))
    public Response findAllConnectTypes(@Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                        @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        Optional<Integer> count = nzyme.getUav().countAllConnectUavModels();

        if (count.isEmpty()) {
            return Response.ok(UavConnectTypeListResponse.create(0, Lists.newArrayList())).build();
        }

        List<UavConnectTypeDetailsResponse> types = Lists.newArrayList();

        Optional<List<ConnectUavModel>> models = nzyme.getUav().findAllConnectUavModels(limit, offset);

        if (models.isEmpty()) {
            return Response.ok(UavConnectTypeListResponse.create(0, Lists.newArrayList())).build();
        }

        for (ConnectUavModel model : models.get()) {
            types.add(UavConnectTypeDetailsResponse.create(
                    model.classification() == null ? "Unknown" : model.classification(),
                    model.make() + " " + model.model(),
                    model.serialType().toString(),
                    model.serial()
            ));
        }

        return Response.ok(UavConnectTypeListResponse.create(count.get(), types)).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY)
    @Path("/uavs/organization/{organization_id}/tenant/{tenant_id}/types/custom/show/{uuid}")
    @Operation(operationId = "findUavType", summary = "Get a custom UAV type")
    @ApiResponse(responseCode = "200", description = "Custom UAV type found.",
            content = @Content(schema = @Schema(implementation = UavCustomTypeDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Custom UAV type not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response findCustomType(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Custom UAV type UUID.") @PathParam("uuid") UUID uuid,
                                   @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<UavTypeEntry> result = nzyme.getUav().findCustomType(uuid, organizationId, tenantId);

        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        UavTypeEntry type = result.get();

        return Response.ok(UavCustomTypeDetailsResponse.create(
                type.uuid(),
                type.organizationId(),
                type.tenantId(),
                type.matchType(),
                type.matchValue(),
                type.defaultClassification(),
                type.type(),
                type.name(),
                type.model(),
                type.createdAt(),
                type.updatedAt()
        )).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "uav_monitoring_manage" })
    @Path("/uavs/organization/{organization_id}/tenant/{tenant_id}/types/custom")
    @Operation(operationId = "createUavType", summary = "Create a custom UAV type",
            description = "Creates a custom UAV type for the tenant. The match value is always compared against the "
                    + "serial number of a detected UAV, and the match type decides whether that is an EXACT or a "
                    + "PREFIX comparison. Use this to describe your own fleet, for example with a default "
                    + "classification of FRIENDLY. Requires the uav_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "201", description = "Custom UAV type created.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The match type or the default classification is unknown.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response createCustomType(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                     @RequestBody(description = "Match type, match value, name, type, model and default classification of the new UAV type.", required = true, content = @Content(mediaType = "application/json")) @Valid CreateUavCustomTypeRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        UavTypeMatchType matchType;
        Classification defaultClassification;
        try {
            matchType = UavTypeMatchType.valueOf(req.matchType().toUpperCase());

            if (req.defaultClassification() != null && !req.defaultClassification().isEmpty()) {
                defaultClassification = Classification.valueOf(req.defaultClassification().toUpperCase());
            } else {
                defaultClassification = null;
            }
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        nzyme.getUav().createCustomType(
                organizationId,
                tenantId,
                matchType,
                req.matchValue(),
                defaultClassification,
                req.type(),
                req.name(),
                req.model() == null || req.model().trim().isEmpty() ? null : req.model()
        );

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "uav_monitoring_manage" })
    @Path("/uavs/organization/{organization_id}/tenant/{tenant_id}/types/custom/show/{uuid}")
    @Operation(operationId = "updateUavType", summary = "Update a custom UAV type",
            description = "Replaces all fields of the custom UAV type. The match type is EXACT or PREFIX and the "
                    + "match value is compared against the serial number of a detected UAV. Requires the "
                    + "uav_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Custom UAV type updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The match type or the default classification is unknown.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Custom UAV type not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response updateCustomType(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Custom UAV type UUID.") @PathParam("uuid") UUID uuid,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                     @RequestBody(description = "The new match type, match value, name, type, model and default classification.", required = true, content = @Content(mediaType = "application/json")) @Valid UpdateUavCustomTypeRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<UavTypeEntry> type = nzyme.getUav().findCustomType(uuid, organizationId, tenantId);

        if (type.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        UavTypeMatchType matchType;
        Classification defaultClassification;
        try {
            matchType = UavTypeMatchType.valueOf(req.matchType().toUpperCase());

            if (req.defaultClassification() != null && !req.defaultClassification().isEmpty()) {
                defaultClassification = Classification.valueOf(req.defaultClassification().toUpperCase());
            } else {
                defaultClassification = null;
            }
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }


        nzyme.getUav().updateCustomType(
                type.get().id(),
                matchType,
                req.matchValue(),
                defaultClassification,
                req.type(),
                req.name(),
                req.model() == null || req.model().trim().isEmpty() ? null : req.model()
        );

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "uav_monitoring_manage" })
    @Path("/uavs/organization/{organization_id}/tenant/{tenant_id}/types/custom/show/{uuid}")
    @Operation(operationId = "deleteUavType", summary = "Delete a custom UAV type",
            description = "Detected UAVs that matched this type fall back to a matching Nzyme Connect type, or to "
                    + "their manual classification if there is none. Requires the uav_monitoring_manage feature "
                    + "permission.")
    @ApiResponse(responseCode = "200", description = "Custom UAV type deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Custom UAV type not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response deleteCustomType(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                     @Parameter(description = "Custom UAV type UUID.") @PathParam("uuid") UUID uuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<UavTypeEntry> type = nzyme.getUav().findCustomType(uuid, organizationId, tenantId);

        if (type.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getUav().deleteCustomType(type.get().id());

        return Response.ok().build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "uav_monitoring_manage" })
    @Path("/uavs/organization/{organization_id}/tenant/{tenant_id}/monitoring")
    @Operation(operationId = "findUavMonitoringConfiguration", summary = "Get UAV monitoring configuration",
            description = "Returns which of the four UAV classifications raise a detection alert for this tenant. "
                    + "Every setting that was never configured is returned as false. Requires the "
                    + "uav_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = UavMonitoringSettingsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response getMonitoringConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                               @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                               @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        boolean alertOnUnknown = nzyme.getDatabaseCoreRegistry()
                .getValue(UavRegistryKeys.MONITORING_ALERT_ON_UNKNOWN.key(), organizationId, tenantId)
                .map(Boolean::parseBoolean).orElse(false);

        boolean alertOnFriendly = nzyme.getDatabaseCoreRegistry()
                .getValue(UavRegistryKeys.MONITORING_ALERT_ON_FRIENDLY.key(), organizationId, tenantId)
                .map(Boolean::parseBoolean).orElse(false);

        boolean alertOnNeutral = nzyme.getDatabaseCoreRegistry()
                .getValue(UavRegistryKeys.MONITORING_ALERT_ON_NEUTRAL.key(), organizationId, tenantId)
                .map(Boolean::parseBoolean).orElse(false);

        boolean alertOnHostile = nzyme.getDatabaseCoreRegistry()
                .getValue(UavRegistryKeys.MONITORING_ALERT_ON_HOSTILE.key(), organizationId, tenantId)
                .map(Boolean::parseBoolean).orElse(false);

        return Response.ok(UavMonitoringSettingsResponse.create(
                alertOnUnknown,
                alertOnFriendly,
                alertOnNeutral,
                alertOnHostile
        )).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "uav_monitoring_manage" })
    @Path("/uavs/organization/{organization_id}/tenant/{tenant_id}/monitoring")
    @Operation(operationId = "updateUavMonitoringConfiguration", summary = "Update UAV monitoring configuration",
            description = "Sets which UAV classifications raise a detection alert for this tenant, so you can for "
                    + "example alert on every UAV except known friendly ones. All four settings are written on "
                    + "every call. Requires the uav_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response setMonitoringConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                               @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                               @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                               @RequestBody(description = "The classifications that should raise an alert.", required = true, content = @Content(mediaType = "application/json")) UavMonitoringConfigurationRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }


        nzyme.getDatabaseCoreRegistry().setValue(
                UavRegistryKeys.MONITORING_ALERT_ON_UNKNOWN.key(),
                String.valueOf(req.alertOnUnknown()),
                organizationId,
                tenantId
        );

        nzyme.getDatabaseCoreRegistry().setValue(
                UavRegistryKeys.MONITORING_ALERT_ON_FRIENDLY.key(),
                String.valueOf(req.alertOnFriendly()),
                organizationId,
                tenantId
        );

        nzyme.getDatabaseCoreRegistry().setValue(
                UavRegistryKeys.MONITORING_ALERT_ON_NEUTRAL.key(),
                String.valueOf(req.alertOnNeutral()),
                organizationId,
                tenantId
        );

        nzyme.getDatabaseCoreRegistry().setValue(
                UavRegistryKeys.MONITORING_ALERT_ON_HOSTILE.key(),
                String.valueOf(req.alertOnHostile()),
                organizationId,
                tenantId
        );

        return Response.ok().build();
    }

    private UavSummaryResponse uavEntryToSummaryResponse(UavEntry uav,
                                                         Optional<UavTypeMatch> uavType,
                                                         List<UUID> taps) {
        Double operatorDistanceToUav = null;

        if (uav.latitude() != null && uav.longitude() != null
                && uav.operatorLatitude() != null && uav.operatorLongitude() != null) {
            operatorDistanceToUav = HaversineDistance.haversine(
                    uav.latitude(), uav.longitude(), uav.operatorLatitude(), uav.operatorLongitude()
            );
        }

        ClassificationResponse classification;
        if (uavType.isPresent() && uavType.get().defaultClassification() != null) {
            classification = ClassificationResponse.valueOf(uavType.get().defaultClassification());
        } else {
            // No custom classification. Leave it at the manual classification.
            classification = ClassificationResponse.valueOf(uav.classification());
        }

        // Calculate tap distances.
        Map<UUID, UavToTapDistanceResponse> tapDistances = Maps.newHashMap();
        Double closestTapDistance = null;
        if (uav.latitude() != null && uav.longitude() != null) {
            for (UUID uuid : taps) {
                Optional<Tap> tap = nzyme.getTapManager().findTap(uuid);
                if (tap.isEmpty() || tap.get().latitude() == null || tap.get().longitude() == null) {
                    continue;
                }

                double distanceFeet = HaversineDistance.haversine(
                        tap.get().latitude(), tap.get().longitude(), uav.latitude(), uav.longitude()
                )*3.28084;

                if (closestTapDistance == null || distanceFeet < closestTapDistance) {
                    closestTapDistance = distanceFeet;
                }

                tapDistances.put(uuid, UavToTapDistanceResponse.create(
                        TapHighLevelInformationDetailsResponse.create(
                                uuid, tap.get().name(), null, null, Tools.isTapActive(tap.get().lastReport())
                        ),
                        distanceFeet
                ));
            }
        }

        return UavSummaryResponse.create(
                uav.lastSeen().isAfter(DateTime.now().minusMinutes(5)),
                uav.identifier(),
                uav.designation(),
                classification,
                UavTypeResponse.fromString(uav.uavType()),
                uavType.map(UavTypeMatch::type).orElse(null),
                uavType.map(UavTypeMatch::model).orElse(null),
                uavType.map(UavTypeMatch::name).orElse(null),
                UavDetectionSourceResponse.fromString(uav.detectionSource()),
                uav.idSerial(),
                uav.idRegistration(),
                uav.idUtm(),
                uav.idSession(),
                uav.operatorId(),
                uav.rssiAverage(),
                UavOperationalStatusResponse.fromString(uav.operationalStatus()),
                uav.latitude(),
                uav.longitude(),
                uav.groundTrack(),
                uav.speed(),
                uav.verticalSpeed(),
                uav.altitudePressure(),
                uav.altitudeGeodetic(),
                UavHeightTypeResponse.fromString(uav.heightType()),
                uav.height(),
                uav.accuracyHorizontal(),
                uav.accuracyVertical(),
                uav.accuracyBarometer(),
                uav.accuracySpeed(),
                UavOperatorLocationTypeResponse.fromString(uav.operatorLocationType()),
                uav.operatorLatitude(),
                uav.operatorLongitude(),
                uav.operatorAltitude(),
                operatorDistanceToUav,
                uav.latestVectorTimestamp(),
                uav.latestOperatorLocationTimestamp(),
                tapDistances,
                closestTapDistance,
                uav.firstSeen(),
                uav.lastSeen()
        );
    }

}
