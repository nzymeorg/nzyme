package app.nzyme.core.rest.resources.dot11;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.shared.db.TapBasedSignalStrengthResult;
import app.nzyme.core.dot11.db.TapBasedSignalStrengthResultHistogramEntry;
import app.nzyme.core.dot11.trilateration.FloorSelectionResult;
import app.nzyme.core.dot11.trilateration.LocationSolver;
import app.nzyme.core.floorplans.db.TenantLocationEntry;
import app.nzyme.core.floorplans.db.TenantLocationFloorEntry;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.constraints.MacAddress;
import app.nzyme.core.rest.responses.floorplans.*;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.stream.Collectors;

@Path("/api/dot11/locations")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class Dot11LocationsResource extends TapDataHandlingResource {

    private static final Logger LOG = LogManager.getLogger(Dot11LocationsResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/show/{locationId}/floors")
    public Response findAllFloorsOfLocation(@Context SecurityContext sc,
                                            @PathParam("locationId") UUID locationId,
                                            @QueryParam("limit") int limit,
                                            @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        // Find location.
        Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, authenticatedUser.getOrganizationId(), authenticatedUser.getTenantId());

        if (location.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long floorCount = nzyme.getAuthenticationService().countAllFloorsOfTenantLocation(location.get().uuid());
        List<TenantLocationFloorDetailsResponse> floors = Lists.newArrayList();
        for (TenantLocationFloorEntry floor : nzyme.getAuthenticationService()
                .findAllFloorsOfTenantLocation(location.get().uuid(), limit, offset)) {
            List<TapPositionResponse> tapPositions = Lists.newArrayList();
            for (Tap t : nzyme.getTapManager()
                    .findAllTapsOnFloor(
                            authenticatedUser.getOrganizationId(),
                            authenticatedUser.getTenantId(),
                            locationId,
                            floor.uuid())) {
                //noinspection DataFlowIssue
                tapPositions.add(TapPositionResponse.create(
                        t.uuid(), t.name(), t.x(), t.y(), t.lastReport(), Tools.isTapActive(t.lastReport())
                ));
            }

            floors.add(TenantLocationFloorDetailsResponse.create(
                    floor.uuid(),
                    floor.locationId(),
                    floor.number(),
                    floor.name() == null ? "Floor " + floor.number() : floor.name(),
                    floor.plan() != null,
                    tapPositions.size(),
                    tapPositions,
                    Tools.round(floor.pathLossExponent(), 1),
                    floor.createdAt(),
                    floor.updatedAt()
            ));
        }

        return Response.ok(TenantLocationFloorListResponse.create(floorCount, floors)).build();
    }

    @GET
    @Path("/locate/bssid/{bssid}")
    public Response bssidLocation(@Context SecurityContext sc,
                                  @MacAddress @PathParam("bssid") @NotEmpty String bssidParam,
                                  @QueryParam("floor_uuid") @Nullable UUID floorUuid,
                                  @QueryParam("location_uuid") @Nullable UUID locationUuid,
                                  @QueryParam("time_range") @Valid String timeRangeParameter) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        List<TapBasedSignalStrengthResult> instantSignalStrengths = null;
        if (floorUuid == null && locationUuid == null) {
            instantSignalStrengths = nzyme.getDot11()
                    .findBSSIDSignalStrengthPerTap(
                            bssidParam,
                            timeRange,
                            nzyme.getTapManager().allTapUUIDsAccessibleByUser(authenticatedUser)
                    );
        }

        FloorSelectionResult fsr = selectFloor(authenticatedUser, floorUuid, locationUuid, instantSignalStrengths);

        if (fsr.errorResponse() != null) {
            return fsr.errorResponse();
        }

        // Get location heatmap data.
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        List<UUID> tapUuids = fsr.taps().stream().map(Tap::uuid).collect(Collectors.toList());
        List<TapBasedSignalStrengthResultHistogramEntry> signals = nzyme.getDot11()
                .getBSSIDSignalStrengthPerTapHistogram(bssidParam, timeRange, bucketing, tapUuids);

        if (!validateSignalsForTrilateration(signals)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.create("The determined/selected floor has three placed taps but less than " +
                            "three taps recorded this BSSID during the selected timeframe. Trilateration cannot be " +
                            "performed."))
                    .build();
        }

        // Calculate location.
        LocationSolver solver = new LocationSolver();
        LocationSolver.TrilaterationResult bssidLocation;
        try {
            bssidLocation = solver.solve(signals, fsr.taps(), fsr.floor());
        } catch (LocationSolver.InvalidTapsException e) {
            LOG.error("Could not calculate BSSID location.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        Map<DateTime, TrilaterationLocationResponse> locations = Maps.newTreeMap();
        for (Map.Entry<DateTime, LocationSolver.TrilaterationLocation> loc : bssidLocation.locations().entrySet()) {
            locations.put(loc.getKey(), TrilaterationLocationResponse.create(loc.getValue().x(), loc.getValue().y()));
        }

        //noinspection DataFlowIssue
        return Response.ok(TrilaterationResponse.create(
                locations,
                bssidLocation.outsideOfPlanBoundariesPercentage(),
                bssidLocation.isOutsideOfFloorPlanBoundaries(),
                bssidLocation.outsideOfPlanBoundariesTapStrengths(),
                FloorPlanResponse.create(
                        BaseEncoding.base64().encode(fsr.floor().plan()),
                        fsr.floorPlanImage().getWidth(),
                        fsr.floorPlanImage().getHeight(),
                        fsr.floor().planWidthMeters(),
                        fsr.floor().planLengthMeters()
                ),
                TenantLocationDetailsResponse.create(
                        fsr.location().uuid(),
                        fsr.location().name(),
                        fsr.location().description(),
                        fsr.location().longitude(),
                        fsr.location().latitude(),
                        fsr.locationFloorCount(),
                        fsr.locationTapCount(),
                        fsr.location().environmentalAlertEventingEnabled(),
                        fsr.location().createdAt(),
                        fsr.location().updatedAt()
                ),
                TenantLocationFloorDetailsResponse.create(
                        fsr.floor().uuid(),
                        fsr.floor().locationId(),
                        fsr.floor().number(),
                        fsr.floor().name() == null ? "Floor #" + fsr.floor().number() : fsr.floor().name(),
                        true, // It has if we reached here.
                        fsr.tapPositions().size(),
                        fsr.tapPositions(),
                        Tools.round(fsr.floor().pathLossExponent(), 1),
                        fsr.floor().createdAt(),
                        fsr.floor().updatedAt()
                ),
                DateTime.now(),
                "BSSID " + bssidParam
        )).build();
    }

    @GET
    @Path("/locate/client/{mac}")
    public Response clientLocation(@Context SecurityContext sc,
                                   @MacAddress @PathParam("mac") @NotEmpty String macParam,
                                   @QueryParam("floor_uuid") @Nullable UUID floorUuid,
                                   @QueryParam("location_uuid") @Nullable UUID locationUuid,
                                   @QueryParam("time_range") @Valid String timeRangeParameter) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        List<UUID> accessibleTaps = nzyme.getTapManager().allTapUUIDsAccessibleByUser(authenticatedUser);

        List<TapBasedSignalStrengthResult> mergedSignalStrengthsByTap = null;
        if (floorUuid == null && locationUuid == null) {
            Map<UUID, TapBasedSignalStrengthResult> connectedSignalStrengthsByTap = Maps.newHashMap();
            for (TapBasedSignalStrengthResult tsr : nzyme.getDot11()
                    .findBssidClientSignalStrengthPerTap(macParam, timeRange, accessibleTaps)) {
                connectedSignalStrengthsByTap.put(tsr.tapUuid(), tsr);
            }

            Map<UUID, TapBasedSignalStrengthResult> disconnectedSignalStrengthsByTap = Maps.newHashMap();
            for (TapBasedSignalStrengthResult tsr : nzyme.getDot11()
                    .findDisconnectedClientSignalStrengthPerTap(macParam, timeRange, accessibleTaps)) {
                disconnectedSignalStrengthsByTap.put(tsr.tapUuid(), tsr);
            }

            mergedSignalStrengthsByTap = Lists.newArrayList();
            for (UUID tap : accessibleTaps) {
                if (connectedSignalStrengthsByTap.containsKey(tap) && disconnectedSignalStrengthsByTap.containsKey(tap)) {
                    // Merge.
                    TapBasedSignalStrengthResult connected = connectedSignalStrengthsByTap.get(tap);
                    TapBasedSignalStrengthResult disconnected = disconnectedSignalStrengthsByTap.get(tap);
                    mergedSignalStrengthsByTap.add(TapBasedSignalStrengthResult.create(
                            connected.tapUuid(),
                            connected.tapName(),
                            (connected.signalStrength() + disconnected.signalStrength()) / 2
                    ));
                } else if (connectedSignalStrengthsByTap.containsKey(tap)) {
                    // Only in connected.
                    mergedSignalStrengthsByTap.add(connectedSignalStrengthsByTap.get(tap));
                } else if (disconnectedSignalStrengthsByTap.containsKey(tap)) {
                    // Only in disconnected.
                    mergedSignalStrengthsByTap.add(disconnectedSignalStrengthsByTap.get(tap));
                }
            }
        }

        FloorSelectionResult fsr = selectFloor(authenticatedUser, floorUuid, locationUuid, mergedSignalStrengthsByTap);

        if (fsr.errorResponse() != null) {
            return fsr.errorResponse();
        }

        // Get location heatmap data.
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        List<UUID> tapUuids = fsr.taps().stream().map(Tap::uuid).collect(Collectors.toList());
        List<TapBasedSignalStrengthResultHistogramEntry> signals = Lists.newArrayList();

        signals.addAll(nzyme.getDot11()
                .getConnectedClientSignalStrengthPerTapHistogram(macParam, timeRange, bucketing, tapUuids));
        signals.addAll(nzyme.getDot11()
                .getDisconnectedClientSignalStrengthPerTapHistogram(macParam, timeRange, bucketing, tapUuids));

        if (!validateSignalsForTrilateration(signals)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.create("The determined/selected floor has three placed taps but less than " +
                            "three taps recorded this client during the selected timeframe. Trilateration cannot be " +
                            "performed."))
                    .build();
        }

        // Calculate location.
        LocationSolver solver = new LocationSolver();
        LocationSolver.TrilaterationResult clientLocation;
        try {
            clientLocation = solver.solve(signals, fsr.taps(), fsr.floor());
        } catch (LocationSolver.InvalidTapsException e) {
            LOG.error("Could not calculate client location.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        Map<DateTime, TrilaterationLocationResponse> locations = Maps.newTreeMap();
        for (Map.Entry<DateTime, LocationSolver.TrilaterationLocation> loc : clientLocation.locations().entrySet()) {
            locations.put(loc.getKey(), TrilaterationLocationResponse.create(loc.getValue().x(), loc.getValue().y()));
        }

        //noinspection DataFlowIssue
        return Response.ok(TrilaterationResponse.create(
                locations,
                clientLocation.outsideOfPlanBoundariesPercentage(),
                clientLocation.isOutsideOfFloorPlanBoundaries(),
                clientLocation.outsideOfPlanBoundariesTapStrengths(),
                FloorPlanResponse.create(
                        BaseEncoding.base64().encode(fsr.floor().plan()),
                        fsr.floorPlanImage().getWidth(),
                        fsr.floorPlanImage().getHeight(),
                        fsr.floor().planWidthMeters(),
                        fsr.floor().planLengthMeters()
                ),
                TenantLocationDetailsResponse.create(
                        fsr.location().uuid(),
                        fsr.location().name(),
                        fsr.location().description(),
                        fsr.location().longitude(),
                        fsr.location().latitude(),
                        fsr.locationFloorCount(),
                        fsr.locationTapCount(),
                        fsr.location().environmentalAlertEventingEnabled(),
                        fsr.location().createdAt(),
                        fsr.location().updatedAt()
                ),
                TenantLocationFloorDetailsResponse.create(
                        fsr.floor().uuid(),
                        fsr.floor().locationId(),
                        fsr.floor().number(),
                        fsr.floor().name() == null ? "Floor #" + fsr.floor().number() : fsr.floor().name(),
                        true, // It has if we reached here.
                        fsr.tapPositions().size(),
                        fsr.tapPositions(),
                        Tools.round(fsr.floor().pathLossExponent(), 1),
                        fsr.floor().createdAt(),
                        fsr.floor().updatedAt()
                ),
                DateTime.now(),
                "Client " + macParam
        )).build();
    }

    private FloorSelectionResult selectFloor(AuthenticatedUser authenticatedUser,
                                             @Nullable UUID passedFloorUuid,
                                             @Nullable UUID passedLocationUuid,
                                             @Nullable List<TapBasedSignalStrengthResult> instantSignalStrengths) {
        TenantLocationEntry location;
        TenantLocationFloorEntry floor;

        // Was a floor/location selected or do we guess?
        if (passedFloorUuid == null || passedLocationUuid == null) {
            // We have to guess.
            if (instantSignalStrengths.size() < 3) {
                return buildFloorSelectionError(Response.status(Response.Status.BAD_REQUEST)
                        .entity(ErrorResponse.create("Less than three taps recorded this " +
                                "signal source during the selected timeframe and trilateration cannot be performed."))
                        .build());
            }

            Optional<TenantLocationFloorEntry> guessedFloor = nzyme.getTapManager()
                    .guessFloorOfSignalSource(instantSignalStrengths);

            if (guessedFloor.isEmpty()) {
                return buildFloorSelectionError(Response.status(Response.Status.BAD_REQUEST)
                        .entity(ErrorResponse.create("The system could not determine a floor " +
                                "with at least three placed taps that have recorded a signal of the source during the " +
                                "selected timeframe. Trilateration cannot be performed."))
                        .build());
            }

            floor = guessedFloor.get();

            Optional<TenantLocationEntry> locationResult = nzyme.getAuthenticationService().findTenantLocation(
                    floor.locationId(), authenticatedUser.getOrganizationId(), authenticatedUser.getTenantId()
            );

            if (locationResult.isEmpty()) {
                return buildFloorSelectionError(Response.status(Response.Status.NOT_FOUND).build());
            }

            location = locationResult.get();
        } else {
            // Floor/location was passed.
            Optional<TenantLocationEntry> locationResult = nzyme.getAuthenticationService().findTenantLocation(
                    passedLocationUuid, authenticatedUser.getOrganizationId(), authenticatedUser.getTenantId()
            );

            if (locationResult.isEmpty()) {
                return buildFloorSelectionError(Response.status(Response.Status.NOT_FOUND).build());
            }

            location = locationResult.get();

            Optional<TenantLocationFloorEntry> floorResult = nzyme.getAuthenticationService()
                    .findFloorOfTenantLocation(location.uuid(), passedFloorUuid);

            if (floorResult.isEmpty()) {
                return buildFloorSelectionError(Response.status(Response.Status.NOT_FOUND).build());
            }

            floor = floorResult.get();
        }

        // Get all taps of floor.
        List<Tap> taps = nzyme.getTapManager().findAllTapsOnFloor(
                authenticatedUser.getOrganizationId(),
                authenticatedUser.getTenantId(),
                location.uuid(),
                floor.uuid()
        );

        if (taps.size() < 3) {
            /*
             * Selected floor does not have at least three taps. We would not have guessed this one. Was manual
             * selection, which UI prevents.
             */
            return buildFloorSelectionError(Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.create("Selected floor does not have at least three placed taps."))
                    .build());
        }

        long locationFloorCount = nzyme.getAuthenticationService().countFloorsOfTenantLocation(location.uuid());
        long locationTapCount = nzyme.getAuthenticationService().countTapsOfTenantLocation(location.uuid());

        List<TapPositionResponse> tapPositions = Lists.newArrayList();
        for (Tap t : nzyme.getTapManager()
                .findAllTapsOnFloor(location.organizationId(), location.tenantId(), location.uuid(), floor.uuid())) {
            //noinspection DataFlowIssue
            tapPositions.add(TapPositionResponse.create(
                    t.uuid(), t.name(), t.x(), t.y(), t.lastReport(), Tools.isTapActive(t.lastReport())
            ));
        }

        // Get floor plan.
        BufferedImage floorPlanImage;
        try {
            if (floor.plan() == null) {
                LOG.error("Floor plan of floor [{}] is null. This is a database inconsistency.", floor.uuid());
                return buildFloorSelectionError(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
            }

            floorPlanImage = ImageIO.read(new ByteArrayInputStream(floor.plan()));
        } catch (Exception e) {
            LOG.error("Could not read floor plan image data from database. Floor: {}", floor, e);
            return buildFloorSelectionError(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
        }

        return FloorSelectionResult.create(
                null,
                taps,
                tapPositions,
                locationFloorCount,
                locationTapCount,
                location,
                floor,
                floorPlanImage
        );
    }

    private FloorSelectionResult buildFloorSelectionError(Response response) {
        return FloorSelectionResult.create(
                response,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private boolean validateSignalsForTrilateration(List<TapBasedSignalStrengthResultHistogramEntry> signals) {
        Map<DateTime, List<TapBasedSignalStrengthResultHistogramEntry>> histo = Maps.newHashMap();
        for (TapBasedSignalStrengthResultHistogramEntry signal : signals) {
            List<TapBasedSignalStrengthResultHistogramEntry> entry = histo.get(signal.bucket());

            if (entry != null) {
                entry.add(signal);
            } else {
                histo.put(signal.bucket(), new ArrayList<>(){{ add(signal); }});
            }
        }

        int validBuckets = 0;

        for (Map.Entry<DateTime, List<TapBasedSignalStrengthResultHistogramEntry>> entry : histo.entrySet()) {
            if (entry.getValue().size() >= 3) {
                validBuckets+=1;
            }
        }

        return validBuckets > 0;
    }

}
