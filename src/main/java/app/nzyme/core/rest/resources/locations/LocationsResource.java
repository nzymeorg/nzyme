package app.nzyme.core.rest.resources.locations;

/*
 * This is the generic locations resource used for reading high-level location information from across subsystems. It
 * is for all users and not to be confused wit the location management APIs in the OrganizationsResource, which is
 * limited to org admins.
 */

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.db.DetectionAlertAttributeEntry;
import app.nzyme.core.detection.alerts.db.DetectionAlertEntry;
import app.nzyme.core.environment.dto.EnvironmentData;
import app.nzyme.core.environment.dto.LocationEnvironmentAlertDetails;
import app.nzyme.core.floorplans.db.TenantLocationEntry;
import app.nzyme.core.floorplans.db.TenantLocationFloorEntry;
import app.nzyme.core.rest.RestTools;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.responses.alerts.DetectionAlertDetailsResponse;
import app.nzyme.core.rest.responses.locations.*;
import app.nzyme.core.rest.responses.taps.TapHighLevelInformationDetailsResponse;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static app.nzyme.core.environment.EnvironmentService.*;

@Path("/api/locations/organizations/{organization_id}/tenants/{tenant_id}")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Locations", description = "A location groups the taps of a tenant that are deployed at the same "
        + "physical site, for example a campus or a building, and can have floors with floor plans. These endpoints "
        + "return the high-level status of a location: its floors, its taps, active detection alerts and "
        + "environmental data. Creating and changing locations is part of the tenant settings.",
        externalDocs = @ExternalDocumentation(description = "Locations in the Nzyme documentation",
                url = "https://go.nzyme.org/locations"))
public class LocationsResource extends UserAuthenticatedResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findLocations", summary = "List locations of a tenant",
            description = "Returns a summary of every location of the tenant with its floors, its taps and the "
                    + "current environmental data. Environmental data is null for locations without latitude and "
                    + "longitude. Up to 15 active detection alerts of the taps at a location are included, and only "
                    + "if the calling user holds the alerts_view feature permission.",
            externalDocs = @ExternalDocumentation(description = "Environmental monitoring in the Nzyme documentation",
                    url = "https://go.nzyme.org/environmental-monitoring"))
    @ApiResponse(responseCode = "200", description = "Locations found.",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = LocationSummaryResponse.class))))
    @ApiResponse(responseCode = "401", description = "Organization or tenant not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        AuthenticatedUser user = getAuthenticatedUser(sc);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<LocationSummaryResponse> locations = Lists.newArrayList();
        for (TenantLocationEntry location : nzyme.getAuthenticationService()
                .findAllTenantLocations(organizationId, tenantId, Integer.MAX_VALUE, 0)) {

            locations.add(buildLocationSummary(location, user, organizationId, tenantId));
        }

        return Response.ok(locations).build();
    }

    @GET
    @Path("/show/{location_id}")
    @Operation(operationId = "findLocation", summary = "Get a location of a tenant",
            description = "Returns the floors, the taps, the environmental data and the timezone of the location. "
                    + "The timezone and the environmental data are only set if the location has a latitude and a "
                    + "longitude, and only severe environmental alerts that are currently relevant are included. Up "
                    + "to 15 active detection alerts of the taps at the location are included, and only if the "
                    + "calling user holds the alerts_view feature permission.",
            externalDocs = @ExternalDocumentation(description = "Environmental monitoring in the Nzyme documentation",
                    url = "https://go.nzyme.org/environmental-monitoring"))
    @ApiResponse(responseCode = "200", description = "Location found.",
            content = @Content(schema = @Schema(implementation = LocationSummaryResponse.class)))
    @ApiResponse(responseCode = "401", description = "Organization or tenant not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Location not found.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Location UUID.") @PathParam("location_id") UUID locationId,
                            @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        AuthenticatedUser user = getAuthenticatedUser(sc);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, organizationId, tenantId);

        if (location.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(buildLocationSummary(location.get(), user, organizationId, tenantId)).build();
    }

    private LocationSummaryResponse buildLocationSummary(TenantLocationEntry location,
                                                         AuthenticatedUser user,
                                                         UUID organizationId,
                                                         UUID tenantId) {
        List<Tap> taps = nzyme.getTapManager().findAllTapsAtLocation(location.uuid());

        // Taps and alerts.
        int detectionAlertCount = 0;
        List<TapHighLevelInformationDetailsResponse> tapsList = Lists.newArrayList();
        for (Tap tap : taps) {
            detectionAlertCount += (int) nzyme.getDetectionAlertService()
                    .countActiveAlertsOfTap(organizationId, tenantId, tap.uuid());

            tapsList.add(TapHighLevelInformationDetailsResponse.create(
                    tap.uuid(), tap.name(), null, null, Tools.isTapActive(tap.lastReport())
            ));
        }

        // Environment data.
        Optional<EnvironmentData> ed = nzyme.getEnvironmentService().getEnvironmentData(location.uuid());
        LocationEnvironmentDataResponse environmentDataResponse;
        if (ed.isPresent()) {
            EnvironmentData x = ed.get();
            List<LocationEnvironmentAlertDetailsResponse> eAlerts = Lists.newArrayList();
            for (LocationEnvironmentAlertDetails a : x.alerts()) {
                if (a.severity() != null && severityOrdinal(a.severity()) < MIN_ALERT_SEVERITY) {
                    continue;
                }

                if (!alertIsCurrentlyRelevant(a)) {
                    continue;
                }

                eAlerts.add(LocationEnvironmentAlertDetailsResponse.create(
                        a.event(),
                        a.severity(),
                        a.certainty(),
                        a.urgency(),
                        a.headline(),
                        a.description(),
                        a.senderName(),
                        a.effective(),
                        a.expires(),
                        a.ends()
                ));
            }

            LocationEnvironmentConditionDetailsResponse condition;
            if (x.condition() != null) {
                condition = LocationEnvironmentConditionDetailsResponse.create(
                        x.condition().displayName(), x.condition().severity()
                );
            } else {
                condition = null;
            }

            environmentDataResponse = LocationEnvironmentDataResponse.create(
                    x.stationId(),
                    x.metar(),
                    condition,
                    x.temperature(),
                    x.windDirection(),
                    x.windSpeed(),
                    x.windGust(),
                    x.visibility(),
                    eAlerts
            );
        } else {
            environmentDataResponse = null;
        }

        // Timezone.
        String timezone;
        if (location.longitude() != null && location.latitude() != null
                && location.longitude() != 0 && location.latitude() != 0) {
            Optional<ZoneId> tz = nzyme.getEnvironmentService()
                    .getTimezoneAtCoordinates(location.longitude(), location.latitude());

            timezone = tz.map(ZoneId::getId).orElse(null);
        } else {
            timezone = null;
        }

        // Detection alerts.
        List<DetectionAlertDetailsResponse> detectionAlerts = Lists.newArrayList();
        if (userHasPermission(user, "alerts_view")) {
            List<UUID> tapUUIDs = taps.stream()
                    .map(Tap::uuid)
                    .collect(Collectors.toList());

            for (DetectionAlertEntry alert : nzyme.getDetectionAlertService()
                    .findActiveAlertsOfTaps(organizationId, tenantId, tapUUIDs, 15)) {
                List<DetectionAlertAttributeEntry> attributes = nzyme.getDetectionAlertService()
                        .findAlertAttributes(alert.id());

                detectionAlerts.add(RestTools.buildAlertDetailsResponse(alert, attributes));
            }
        }

        // Floors.
        List<LocationFloorDetailsResponse> floors = Lists.newArrayList();
        for (TenantLocationFloorEntry floor : nzyme.getAuthenticationService()
                .findAllFloorsOfTenantLocation(location.uuid())) {
            boolean hasFloorPlan = floor.plan() != null;
            int tapCount = nzyme.getTapManager()
                    .findAllTapsOnFloor(organizationId, tenantId, location.uuid(), floor.uuid()).size();

            floors.add(LocationFloorDetailsResponse.create(
                    floor.uuid(), floor.number(), Tools.buildFloorName(floor), hasFloorPlan, tapCount
            ));
        }


        return LocationSummaryResponse.create(
                location.uuid(),
                location.name(),
                location.description(),
                taps.size(),
                detectionAlertCount,
                detectionAlerts,
                timezone,
                environmentDataResponse,
                location.longitude(),
                location.latitude(),
                tapsList,
                floors
        );
    }

}
