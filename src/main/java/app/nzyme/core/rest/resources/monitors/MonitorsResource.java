package app.nzyme.core.rest.resources.monitors;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.detection.alerts.db.DetectionAlertEntry;
import app.nzyme.core.detection.alerts.db.DetectionAlertTimelineEntry;
import app.nzyme.core.monitors.MonitorType;
import app.nzyme.core.monitors.db.MonitorEntry;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.CreateMonitorRequest;
import app.nzyme.core.rest.requests.UpdateMonitorRequest;
import app.nzyme.core.rest.responses.alerts.DetectionAlertTimelineDetailsResponse;
import app.nzyme.core.rest.responses.alerts.DetectionAlertTimelineListResponse;
import app.nzyme.core.rest.responses.monitors.MonitorDetailsResponse;
import app.nzyme.core.rest.responses.monitors.MonitorListResponse;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
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
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.Duration;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/monitors")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Monitors", description = "Monitors turn a saved search filter into a persistent alert condition. "
        + "Nzyme runs each monitor in its configured interval and raises a MONITOR_TRIGGERED detection event when "
        + "the number of results in the lookback window is larger than the trigger condition. Monitors exist for "
        + "several subsystems, for example WiFi and Bluetooth.",
        externalDocs = @ExternalDocumentation(description = "Monitors in the Nzyme documentation",
                url = "https://go.nzyme.org/monitors"))
public class MonitorsResource extends TapDataHandlingResource {

    private static final Logger LOG = LogManager.getLogger(MonitorsResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/show/{id}")
    @Operation(operationId = "findMonitor", summary = "Get a monitor",
            description = "Returns the configuration and the current state of the monitor. The tap list is reduced "
                    + "to the taps the calling user can access and the response flags partial data when taps were "
                    + "removed this way.")
    @ApiResponse(responseCode = "200", description = "Monitor found.",
            content = @Content(schema = @Schema(implementation = MonitorDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Monitor not found or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Monitor UUID.") @PathParam("id") UUID uuid) {
        AuthenticatedUser user = getAuthenticatedUser(sc);
        Optional<MonitorEntry> monitor = nzyme.getMonitors().find(uuid);

        if (monitor.isEmpty() || !entityAccessible(user, monitor.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<UUID> filteredTapUUIDs;
        boolean partialData;
        if (monitor.get().taps() == null) {
            filteredTapUUIDs = null;
            partialData = !user.accessAllTenantTaps;
        } else {
            filteredTapUUIDs = parseAndValidateTapIdsDirect(
                    user,
                    nzyme,
                    monitor.get().taps()
            );
            partialData = filteredTapUUIDs.size() != monitor.get().taps().size();
        }

        return Response.ok(MonitorDetailsResponse.create(
                monitor.get().uuid(),
                monitor.get().organizationId(),
                monitor.get().tenantId(),
                monitor.get().enabled(),
                monitor.get().type(),
                monitor.get().name(),
                monitor.get().description(),
                filteredTapUUIDs,
                monitor.get().triggerCondition(),
                monitor.get().interval(),
                monitor.get().lookback(),
                monitor.get().filters(),
                monitor.get().alerted(),
                monitor.get().status(),
                monitor.get().lastRun(),
                monitor.get().lastEvent(),
                monitor.get().createdAt(),
                monitor.get().updatedAt(),
                partialData
        )).build();
    }

    @GET
    @Path("/show/{id}/detections/timeline")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "alerts_view" })
    @Operation(operationId = "findMonitorAlertTimeline", summary = "List alert timeline of a monitor",
            description = "Returns the timeline of the MONITOR_TRIGGERED alert of this monitor, most recent period "
                    + "first. The timeline is empty if the monitor never triggered. Requires the "
                    + "alerts_view feature permission.")
    @ApiResponse(responseCode = "200", description = "Timeline found, or the monitor never triggered.",
            content = @Content(schema = @Schema(implementation = DetectionAlertTimelineListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Monitor not found or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The monitor triggered more than one alert, which should never happen.", content = @Content)
    public Response findDetectionsTimelineOfMonitor(@Parameter(hidden = true) @Context SecurityContext sc,
                                                    @Parameter(description = "Monitor UUID.") @PathParam("id") UUID uuid,
                                                    @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                                    @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser user = getAuthenticatedUser(sc);
        Optional<MonitorEntry> monitor = nzyme.getMonitors().find(uuid);

        if (monitor.isEmpty() || !entityAccessible(user, monitor.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<DetectionAlertEntry> alerts = nzyme.getDetectionAlertService().findAllAlertsByTypeAndAttribute(
                monitor.get().organizationId(),
                monitor.get().tenantId(),
                DetectionType.MONITOR_TRIGGERED.name(),
                "monitor_uuid",
                monitor.get().uuid().toString(),
                Integer.MAX_VALUE,
                0
        );

        // Due to deduplication, we should always see 0 or 1 alerts.
        if (alerts.isEmpty()) {
            return Response.ok(DetectionAlertTimelineListResponse.create(0, Collections.emptyList())).build();
        }

        if (alerts.size() != 1) {
            LOG.error("Monitor [{}] has triggered multiple ({}) alerts.",
                    monitor.get().uuid(), alerts.size());
            return Response.serverError().build();
        }

        DetectionAlertEntry alert = alerts.getFirst();

        List<DetectionAlertTimelineDetailsResponse> entries = Lists.newArrayList();
        for (DetectionAlertTimelineEntry timelineEntry : nzyme.getDetectionAlertService()
                .findAlertTimeline(alert.id(), limit, offset)) {
            Duration duration = new Duration(timelineEntry.seenFrom(), timelineEntry.seenTo());

            entries.add(DetectionAlertTimelineDetailsResponse.create(
                    timelineEntry.seenFrom(),
                    timelineEntry.seenTo(),
                    duration.getStandardSeconds(),
                    Tools.durationToHumanReadable(duration)
            ));
        }

        long total = nzyme.getDetectionAlertService().countAlertTimelineEntries(alert.id());

        return Response.ok(DetectionAlertTimelineListResponse.create(total, entries)).build();
    }

    @GET
    @Path("/type/{monitor_type}")
    @Operation(operationId = "findMonitors", summary = "List monitors of a type",
            description = "Returns all monitors of the requested type that belong to the tenant, ordered by name. "
                    + "The tap list of each monitor is reduced to the taps the calling user can access and partial "
                    + "data is flagged accordingly. The page size cannot be larger than 200.")
    @ApiResponse(responseCode = "200", description = "Monitors found.",
            content = @Content(schema = @Schema(implementation = MonitorListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The page size is larger than 200 or the page offset is negative.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Organization or tenant not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Type of monitor to list.") @PathParam("monitor_type") MonitorType monitorType,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") @NotNull UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") @NotNull UUID tenantId,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser user = getAuthenticatedUser(sc);

        if (limit > 200 || offset < 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        long total = nzyme.getMonitors().countAllMonitorsOfType(monitorType, organizationId, tenantId);

        List<MonitorDetailsResponse> monitors = Lists.newArrayList();
        nzyme.getDatabase().useHandle(handle -> {
            for (MonitorEntry m : nzyme.getMonitors()
                    .findAllMonitorsOfType(monitorType, organizationId, tenantId, offset, limit)) {
                List<UUID> filteredTapUUIDs;
                boolean partialData;
                if (m.taps() == null) {
                    filteredTapUUIDs = null;
                    partialData = !user.accessAllTenantTaps;
                } else {
                    filteredTapUUIDs = parseAndValidateTapIdsDirect(
                            user,
                            nzyme,
                            m.taps()
                    );
                    partialData = filteredTapUUIDs.size() != m.taps().size();
                }

                monitors.add(MonitorDetailsResponse.create(
                        m.uuid(),
                        m.organizationId(),
                        m.tenantId(),
                        m.enabled(),
                        m.type(),
                        m.name(),
                        m.description(),
                        filteredTapUUIDs,
                        m.triggerCondition(),
                        m.interval(),
                        m.lookback(),
                        m.filters(),
                        m.alerted(),
                        m.status(),
                        m.lastRun(),
                        m.lastEvent(),
                        m.createdAt(),
                        m.updatedAt(),
                        partialData
                ));
            }
        });

        return Response.ok(MonitorListResponse.create(total, monitors)).build();
    }

    @POST
    @Path("/type/{monitor_type}")
    @Operation(operationId = "createMonitor", summary = "Create a monitor",
            description = "Creates an enabled monitor for the organization and tenant in the request body. Interval "
                    + "and lookback are in minutes, and the monitor triggers when the result count is larger than "
                    + "the trigger condition. Omit the tap list to monitor all taps of the tenant. Requires the "
                    + "manage permission of the subsystem the monitor type belongs to, for example "
                    + "dot11_monitoring_manage or bluetooth_monitoring_manage.")
    @ApiResponse(responseCode = "201", description = "Monitor created.", content = @Content)
    @ApiResponse(responseCode = "403", description = "A referenced tap is not accessible, the tenant is not accessible, or the calling user cannot manage this monitor type.", content = @Content)
    public Response create(@Parameter(hidden = true) @Context SecurityContext sc,
                           @RequestBody(description = "Name, filters, interval, lookback, trigger condition, taps and the owning tenant of the new monitor.", required = true, content = @Content(mediaType = "application/json")) @Valid CreateMonitorRequest req,
                           @Parameter(description = "Type of monitor to create.") @PathParam("monitor_type") MonitorType monitorType) {
        AuthenticatedUser user = getAuthenticatedUser(sc);

        List<UUID> tapUuids;
        if (req.taps() == null) {
            // All taps selected.
            tapUuids = null;
        } else {
            tapUuids = Lists.newArrayList();
            List<UUID> userAccessibleTaps = nzyme.getTapManager().allTapUUIDsAccessibleByUser(user);
            for (String tapId : req.taps()) {
                UUID tapUuid = UUID.fromString(tapId);
                if (!userAccessibleTaps.contains(tapUuid)) {
                    return Response.status(Response.Status.FORBIDDEN).build();
                } else {
                    tapUuids.add(tapUuid);
                }
            }
        }

        // Check permissions.
        if (!userHasWritePermissionsForMonitorType(user, req.organizationId(), monitorType)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        if (!passedTenantDataAccessible(sc, req.organizationId(), req.tenantId())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        nzyme.getMonitors().createMonitor(
                monitorType,
                req.name(),
                req.description(),
                tapUuids,
                req.triggerCondition(),
                req.interval(),
                req.lookback(),
                parseFiltersQueryParameter(req.filters()),
                req.organizationId(),
                req.tenantId()
        );

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @Path("/show/{id}")
    @Operation(operationId = "updateMonitor", summary = "Update a monitor",
            description = "Updates the monitor in two independent parts: the meta information is only written when "
                    + "name, description, trigger condition, interval and lookback are all present, and the taps "
                    + "and filters are only written when filters are present. Interval and lookback are in "
                    + "minutes. Requires the manage permission of the subsystem the monitor type belongs to.")
    @ApiResponse(responseCode = "200", description = "Monitor updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Trigger condition, interval or lookback are out of range.", content = @Content)
    @ApiResponse(responseCode = "403", description = "A referenced tap is not accessible or the calling user cannot manage this monitor type.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitor not found or not accessible by the calling user.", content = @Content)
    public Response update(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Monitor UUID.") @PathParam("id") UUID uuid,
                           @RequestBody(description = "The monitor fields to change. All fields are optional.", required = true, content = @Content(mediaType = "application/json")) UpdateMonitorRequest req) {
        AuthenticatedUser user = getAuthenticatedUser(sc);
        Optional<MonitorEntry> monitor = nzyme.getMonitors().find(uuid);

        if (monitor.isEmpty() || !entityAccessible(user, monitor.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check permissions.
        if (!userHasWritePermissionsForMonitorType(
                user, monitor.get().organizationId(), MonitorType.valueOf(monitor.get().type()))) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        if (req.name() != null && !req.name().isBlank() && req.description() != null
                && req.triggerCondition() != null && req.interval() != null && req.lookback() != null) {

            if (req.triggerCondition() < 0 || req.interval() <= 0 || req.lookback() <= 0) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            nzyme.getMonitors().updateMonitorMetaInformation(
                    monitor.get().uuid(),
                    req.name(),
                    req.description(),
                    req.triggerCondition(),
                    req.interval(),
                    req.lookback()
            );
        }

        if (req.filters() != null && !req.filters().isBlank()) {
            List<UUID> tapUuids;
            if (req.taps() == null) {
                // All taps selected.
                tapUuids = null;
            } else {
                tapUuids = Lists.newArrayList();
                List<UUID> userAccessibleTaps = nzyme.getTapManager().allTapUUIDsAccessibleByUser(user);
                for (String tapId : req.taps()) {
                    UUID tapUuid = UUID.fromString(tapId);
                    if (!userAccessibleTaps.contains(tapUuid)) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                    } else {
                        tapUuids.add(tapUuid);
                    }
                }
            }

            nzyme.getMonitors().updateMonitorFilterInformation(
                    monitor.get().uuid(), tapUuids, parseFiltersQueryParameter(req.filters())
            );
        }

        return Response.ok().build();
    }

    @DELETE
    @Path("/show/{id}")
    @Operation(operationId = "deleteMonitor", summary = "Delete a monitor",
            description = "Requires the manage permission of the subsystem the monitor type belongs to.")
    @ApiResponse(responseCode = "200", description = "Monitor deleted.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The calling user cannot manage this monitor type.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitor not found or not accessible by the calling user.", content = @Content)
    public Response delete(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Monitor UUID.") @PathParam("id") UUID uuid) {
        AuthenticatedUser user = getAuthenticatedUser(sc);
        Optional<MonitorEntry> monitor = nzyme.getMonitors().find(uuid);

        if (monitor.isEmpty() || !entityAccessible(user, monitor.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check permissions.
        if (!userHasWritePermissionsForMonitorType(
                user, monitor.get().organizationId(), MonitorType.valueOf(monitor.get().type()))) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        nzyme.getMonitors().deleteMonitor(uuid);

        return Response.ok().build();
    }

    private boolean userHasWritePermissionsForMonitorType(AuthenticatedUser user, UUID organizationId, MonitorType monitorType) {
        if (!user.isSuperAdministrator()
                && !(user.isOrganizationAdministrator() && organizationId.equals(user.getOrganizationId()))) {
            // User is not a super admin or admin of the passed org. Check user permissions.
            List<String> userPermissions = nzyme.getAuthenticationService().findPermissionsOfUser(user.getUserId());
            String requiredPermission;
            switch (monitorType) {
                case DOT11_BSSID:
                case DOT11_CLIENT_CONNECTED:
                case DOT11_CLIENT_DISCONNECTED:
                    requiredPermission = "dot11_monitoring_manage";
                    break;
                case BLUETOOTH_DEVICE:
                    requiredPermission = "bluetooth_monitoring_manage";
                    break;
                default:
                    return false;
            }

            if (!userPermissions.contains(requiredPermission)) {
                return false;
            }
        }

        return true;
    }

}
