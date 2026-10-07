package app.nzyme.core.rest.resources.alerts;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.detection.alerts.db.DetectionAlertAttributeEntry;
import app.nzyme.core.detection.alerts.db.DetectionAlertEntry;
import app.nzyme.core.detection.alerts.db.DetectionAlertTimelineEntry;
import app.nzyme.core.events.EventEngineImpl;
import app.nzyme.core.events.db.EventActionEntry;
import app.nzyme.core.events.db.SubscriptionEntry;
import app.nzyme.core.events.types.EventActionType;
import app.nzyme.core.events.types.EventType;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.DetectionEventSubscriptionRequest;
import app.nzyme.core.rest.requests.UUIDListRequest;
import app.nzyme.core.rest.responses.alerts.*;
import app.nzyme.core.rest.responses.events.SubscriptionDetailsResponse;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.Subsystem;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.joda.time.Duration;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.rest.RestTools.buildAlertDetailsResponse;

@Path("/api/alerts")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Alerts", description = "Detection alerts are raised by the detection engines of Nzyme. Repeated "
        + "observations of the same condition update one alert instead of creating a new one, and an alert that was "
        + "last seen within the past five minutes counts as active. This group of endpoints lists, resolves and "
        + "deletes alerts and manages which event actions are subscribed to which detection type.",
        externalDocs = @ExternalDocumentation(description = "Alerting in the Nzyme documentation",
                url = "https://go.nzyme.org/detection-alerts"))
public class AlertsResource extends UserAuthenticatedResource {

    private static final Logger LOG = LogManager.getLogger(AlertsResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "alerts_view" })
    @Operation(operationId = "findAlerts", summary = "List alerts of a tenant",
            description = "Returns all alerts of a tenant, most recently seen first. Resolved alerts are included "
                    + "and the response also carries the total number of alerts and the number of active alerts. "
                    + "Pass a subsystem to limit the result to the alerts of one subsystem. The page size cannot be "
                    + "larger than 250. Requires the alerts_view feature permission.")
    @ApiResponse(responseCode = "200", description = "Alerts found.",
            content = @Content(schema = @Schema(implementation = DetectionAlertListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The subsystem is unknown.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The page size is larger than 250.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") @NotNull UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") @NotNull UUID tenantId,
                            @Parameter(description = "Name of a subsystem to filter by. Omit for alerts of all subsystems.") @QueryParam("subsystem") @Nullable String subsystemParam,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Subsystem subsystem = null;
        if (subsystemParam != null) {
            try {
                subsystem = Subsystem.valueOf(subsystemParam.toUpperCase());
            } catch(IllegalArgumentException e) {
                LOG.warn("Unknown subsystem.");
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        List<DetectionAlertEntry> alerts = nzyme.getDetectionAlertService().findAllAlerts(
                organizationId,
                tenantId,
                subsystem,
                limit,
                offset
        );

        long total = nzyme.getDetectionAlertService().countAlerts(
                organizationId,
                tenantId,
                subsystem
        );

        long totalActive = nzyme.getDetectionAlertService().countActiveAlerts(
                organizationId,
                tenantId,
                subsystem
        );

        List<DetectionAlertDetailsResponse> responsesList = Lists.newArrayList();
        for (DetectionAlertEntry alert : alerts) {
            List<DetectionAlertAttributeEntry> attributes = nzyme.getDetectionAlertService()
                    .findAlertAttributes(alert.id());

            responsesList.add(buildAlertDetailsResponse(alert, attributes));
        }

        return Response.ok(DetectionAlertListResponse.create(total, totalActive, responsesList)).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "alerts_view" })
    @Path("/show/{uuid}")
    @Operation(operationId = "findAlert", summary = "Get an alert",
            description = "Returns the alert with all attributes the detection method recorded. Only alerts of "
                    + "the tenant of the calling user are visible. Requires the alerts_view feature permission.")
    @ApiResponse(responseCode = "200", description = "Alert found.",
            content = @Content(schema = @Schema(implementation = DetectionAlertDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Alert not found or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Alert UUID.") @PathParam("uuid") UUID uuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<DetectionAlertEntry> alert = nzyme.getDetectionAlertService().findAlert(uuid,
                authenticatedUser.getOrganizationId(), authenticatedUser.getTenantId());

        if (alert.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<DetectionAlertAttributeEntry> attributes = nzyme.getDetectionAlertService()
                .findAlertAttributes(alert.get().id());

        return Response.ok(buildAlertDetailsResponse(alert.get(), attributes)).build();
    }


    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "alerts_view" })
    @Path("/show/{uuid}/timeline")
    @Operation(operationId = "findAlertTimeline", summary = "List timeline of an alert",
            description = "Returns the periods in which this alert was seen, most recent period first, with the "
                    + "duration of each period. A new timeline entry is added whenever the alert is re-triggered "
                    + "after a pause. Requires the alerts_view feature permission.")
    @ApiResponse(responseCode = "200", description = "Timeline found.",
            content = @Content(schema = @Schema(implementation = DetectionAlertTimelineListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Alert not found or not accessible by the calling user.", content = @Content)
    public Response findTimeline(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Alert UUID.") @PathParam("uuid") UUID uuid,
                                 @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                 @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<DetectionAlertEntry> alert = nzyme.getDetectionAlertService().findAlert(uuid,
                authenticatedUser.getOrganizationId(), authenticatedUser.getTenantId());

        if (alert.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<DetectionAlertTimelineDetailsResponse> entries = Lists.newArrayList();
        for (DetectionAlertTimelineEntry timelineEntry : nzyme.getDetectionAlertService()
                .findAlertTimeline(alert.get().id(), limit, offset)) {
            Duration duration = new Duration(timelineEntry.seenFrom(), timelineEntry.seenTo());

            entries.add(DetectionAlertTimelineDetailsResponse.create(
                    timelineEntry.seenFrom(),
                    timelineEntry.seenTo(),
                    duration.getStandardSeconds(),
                    Tools.durationToHumanReadable(duration)
            ));
        }

        long total = nzyme.getDetectionAlertService().countAlertTimelineEntries(alert.get().id());

        return Response.ok(DetectionAlertTimelineListResponse.create(total, entries)).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "alerts_manage" })
    @Path("/show/{uuid}")
    @Operation(operationId = "deleteAlert", summary = "Delete an alert",
            description = "Deletes the alert and its timeline from the database. If the underlying condition still "
                    + "exists, the next detection run creates a new alert, which triggers a new event and all "
                    + "subscribed event actions. Requires the alerts_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Alert deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Alert not found or not accessible by the calling user.", content = @Content)
    public Response delete(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Alert UUID.") @PathParam("uuid") UUID uuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<DetectionAlertEntry> alert = nzyme.getDetectionAlertService().findAlert(uuid,
                authenticatedUser.getOrganizationId(), authenticatedUser.getTenantId());

        if (alert.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDetectionAlertService().delete(uuid);

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "alerts_manage" })
    @Path("/show/{uuid}/resolve")
    @Operation(operationId = "resolveAlert", summary = "Mark an alert as resolved",
            description = "A resolved alert stays in the list but immediately stops counting as active. If the "
                    + "underlying condition still exists, the next detection run re-triggers the alert. Requires "
                    + "the alerts_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Alert marked as resolved.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Alert not found or not accessible by the calling user.", content = @Content)
    public Response markAsResolved(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Alert UUID.") @PathParam("uuid") UUID uuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<DetectionAlertEntry> alert = nzyme.getDetectionAlertService().findAlert(uuid,
                authenticatedUser.getOrganizationId(), authenticatedUser.getTenantId());

        if (alert.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDetectionAlertService().markAlertAsResolved(uuid);

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "alerts_manage" })
    @Path("/many/resolve")
    @Operation(operationId = "resolveAlerts", summary = "Mark multiple alerts as resolved",
            description = "Resolves every alert in the list. Processing stops at the first alert that does not "
                    + "exist or is not accessible, so earlier alerts in the list may already be resolved when this "
                    + "happens. Requires the alerts_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "All alerts marked as resolved.", content = @Content)
    @ApiResponse(responseCode = "404", description = "One of the alerts was not found or is not accessible by the calling user.", content = @Content)
    public Response markListAsResolved(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @RequestBody(description = "The UUIDs of the alerts to resolve.", required = true, content = @Content(mediaType = "application/json")) UUIDListRequest uuids) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        for (UUID uuid : uuids.uuids()) {
            Optional<DetectionAlertEntry> alert = nzyme.getDetectionAlertService().findAlert(uuid,
                    authenticatedUser.getOrganizationId(), authenticatedUser.getTenantId());

            if (alert.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            nzyme.getDetectionAlertService().markAlertAsResolved(uuid);
        }

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "alerts_manage" })
    @Path("/many/delete")
    @Operation(operationId = "deleteAlerts", summary = "Delete multiple alerts",
            description = "Deletes every alert in the list. Processing stops at the first alert that does not "
                    + "exist or is not accessible, so earlier alerts in the list may already be deleted when this "
                    + "happens. Requires the alerts_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "All alerts deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "One of the alerts was not found or is not accessible by the calling user.", content = @Content)
    public Response deleteList(@Parameter(hidden = true) @Context SecurityContext sc,
                               @RequestBody(description = "The UUIDs of the alerts to delete.", required = true, content = @Content(mediaType = "application/json")) UUIDListRequest uuids) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        for (UUID uuid : uuids.uuids()) {
            Optional<DetectionAlertEntry> alert = nzyme.getDetectionAlertService().findAlert(uuid,
                    authenticatedUser.getOrganizationId(), authenticatedUser.getTenantId());

            if (alert.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            nzyme.getDetectionAlertService().delete(uuid);
        }

        return Response.ok().build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ORGADMINISTRATOR)
    @Path("/detections/types")
    @Operation(operationId = "findAlertTypes", summary = "List detection types",
            description = "Returns all detection types Nzyme can raise alerts for, together with the event action "
                    + "subscriptions of the organization for each type. The wildcard type is left out of the list "
                    + "and has its own endpoints. Super administrators have to pass an organization, for all other "
                    + "users the organization of the calling user is used. Requires organization administrator "
                    + "permissions.")
    @ApiResponse(responseCode = "200", description = "Detection types found.",
            content = @Content(schema = @Schema(implementation = DetectionAlertTypeListResponse.class)))
    @ApiResponse(responseCode = "400", description = "A super administrator did not pass an organization, or the subsystem is unknown.", content = @Content)
    public Response findAllDetectionTypes(@Parameter(hidden = true) @Context SecurityContext sc,
                                          @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                          @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                          @Parameter(description = "Organization UUID. Required for super administrators.") @QueryParam("organization_uuid") @NotNull UUID filterOrganizationId,
                                          @Parameter(description = "Name of a subsystem to filter by. Omit for all subsystems.") @QueryParam("filter_subsystem") @Nullable String filterSubsystem) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        UUID organizationId;
        if (authenticatedUser.isSuperAdministrator()) {
            if (filterOrganizationId != null) {
                organizationId = filterOrganizationId;
            } else {
                // Super admins have to select an org.
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        } else {
            organizationId = authenticatedUser.getOrganizationId();
        }

        // Filters.
        Subsystem subsystem = null;
        if (!Strings.isNullOrEmpty(filterSubsystem)) {
            try {
                subsystem = Subsystem.valueOf(filterSubsystem.toUpperCase());
            } catch(IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        // Find all detection types.
        List<DetectionType> types = nzyme.getDetectionAlertService().findAllDetectionTypes(subsystem, limit, offset);
        long totalCount = nzyme.getDetectionAlertService().countAllDetectionTypes(subsystem);

        EventEngineImpl eventEngine = (EventEngineImpl) nzyme.getEventEngine();
        List<DetectionAlertTypeDetailsResponse> typesList = Lists.newArrayList();
        for (DetectionType type : types) {
            if (type.equals(DetectionType.WILDCARD)) {
                continue;
            }

            List<SubscriptionDetailsResponse> subscriptions = Lists.newArrayList();
            for (SubscriptionEntry sub : eventEngine.findAllActionsOfSubscription(organizationId, type.name())) {
                eventEngine.findEventAction(sub.actionId()).ifPresent(eventActionEntry ->
                        subscriptions.add(buildSubscriptionDetailsResponse(sub, eventActionEntry))
                );
            }

            typesList.add(DetectionAlertTypeDetailsResponse.create(
                    type.name(),
                    type.getTitle(),
                    type.getSubsystem().name(),
                    subscriptions
            ));
        }

        // Build response.
        return Response.ok(DetectionAlertTypeListResponse.create(totalCount, typesList)).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ORGADMINISTRATOR)
    @Path("/detections/subscriptions/wildcard")
    @Operation(operationId = "findAlertWildcardSubscriptions", summary = "List wildcard subscriptions",
            description = "Returns the event actions that are subscribed to the wildcard detection type of an "
                    + "organization, which runs them for every type of detection event. Super administrators have "
                    + "to pass an organization, for all other users the organization of the calling user is used. "
                    + "Requires organization administrator permissions.",
            externalDocs = @ExternalDocumentation(description = "Subscriptions and actions in the Nzyme documentation",
                    url = "https://go.nzyme.org/detection-alerts-subscriptions"))
    @ApiResponse(responseCode = "200", description = "Subscriptions found.",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = SubscriptionDetailsResponse.class))))
    @ApiResponse(responseCode = "400", description = "A super administrator did not pass an organization.", content = @Content)
    public Response findAllWildcardSubscription(@Parameter(hidden = true) @Context SecurityContext sc,
                                                @Parameter(description = "Organization UUID. Required for super administrators.") @QueryParam("organization_uuid") @NotNull UUID filterOrganizationId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        UUID organizationId;
        if (authenticatedUser.isSuperAdministrator()) {
            if (filterOrganizationId != null) {
                organizationId = filterOrganizationId;
            } else {
                // Super admins have to select an org.
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        } else {
            organizationId = authenticatedUser.getOrganizationId();
        }

        EventEngineImpl eventEngine = (EventEngineImpl) nzyme.getEventEngine();
        List<SubscriptionDetailsResponse> subscriptions = Lists.newArrayList();
        for (SubscriptionEntry sub : eventEngine.findAllActionsOfSubscription(organizationId, "*")) {
            eventEngine.findEventAction(sub.actionId()).ifPresent(eventActionEntry ->
                    subscriptions.add(buildSubscriptionDetailsResponse(sub, eventActionEntry))
            );
        }

        return Response.ok(subscriptions).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ORGADMINISTRATOR)
    @Path("/detections/subscriptions/wildcard")
    @Operation(operationId = "createAlertWildcardSubscription", summary = "Subscribe an action to all detection types",
            description = "Subscribes an event action to the wildcard detection type, so the action runs for every "
                    + "type of detection event of the organization. The same action can only be subscribed once. "
                    + "Requires organization administrator permissions.",
            externalDocs = @ExternalDocumentation(description = "Subscriptions and actions in the Nzyme documentation",
                    url = "https://go.nzyme.org/detection-alerts-subscriptions"))
    @ApiResponse(responseCode = "200", description = "Action subscribed.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The action is already subscribed.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The calling user cannot administer the organization in the request, "
            + "or the action is a system action or belongs to another organization. The latter case carries an error message.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Event action not found.", content = @Content)
    public Response subscribeWildcardAction(@Parameter(hidden = true) @Context SecurityContext sc,
                                            @RequestBody(description = "The event action and the organization to subscribe it for.", required = true, content = @Content(mediaType = "application/json")) @Valid DetectionEventSubscriptionRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!authenticatedUser.isSuperAdministrator()
                && !req.organizationId().equals(authenticatedUser.getOrganizationId())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        EventEngineImpl eventEngine = ((EventEngineImpl) nzyme.getEventEngine());

        // Pull action.
        Optional<EventActionEntry> action = eventEngine.findEventAction(req.actionId());

        if (action.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Only actions of the same organization can be subscribed to detection events. System actions cannot.
        if (action.get().organizationId() == null || !action.get().organizationId().equals(req.organizationId())) {
            return Response
                    .status(Response.Status.FORBIDDEN)
                    .entity(ErrorResponse.create("Only event actions of the same organization can be subscribed to detection events."))
                    .build();
        }

        // Check if this event already has this action ID subscribed to it.
        for (SubscriptionEntry sub : eventEngine
                .findAllActionsOfSubscription(req.organizationId(), "*")) {
            if (sub.actionId().equals(action.get().uuid())) {
                return Response
                        .status(Response.Status.UNAUTHORIZED)
                        .entity(ErrorResponse.create("Action is already subscribed."))
                        .build();
            }
        }

        eventEngine.subscribeActionToEvent(req.organizationId(), EventType.DETECTION, "*", action.get().uuid());

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ORGADMINISTRATOR)
    @Path("/detections/subscriptions/wildcard/show/{subscriptionId}")
    @Operation(operationId = "deleteAlertWildcardSubscription", summary = "Delete a wildcard subscription",
            description = "Removes the subscription of an event action to the wildcard detection type of the "
                    + "organization. Requires organization administrator permissions.",
            externalDocs = @ExternalDocumentation(description = "Subscriptions and actions in the Nzyme documentation",
                    url = "https://go.nzyme.org/detection-alerts-subscriptions"))
    @ApiResponse(responseCode = "200", description = "Subscription deleted.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The subscribed action does not belong to an organization the calling user can administer.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Subscription or the event action behind it not found.", content = @Content)
    public Response unsubscribeWildcardAction(@Parameter(hidden = true) @Context SecurityContext sc,
                                              @Parameter(description = "Subscription UUID.") @PathParam("subscriptionId") @NotNull UUID subscriptionId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        EventEngineImpl eventEngine = ((EventEngineImpl) nzyme.getEventEngine());

        // Fetch action UUID from subscription.
        Optional<UUID> actionUuid = eventEngine.findActionOfSubscription(subscriptionId);

        if (actionUuid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Fetch action.
        Optional<EventActionEntry> action = eventEngine.findEventAction(actionUuid.get());

        if (action.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if superadmin or event is subscription of org.
        if (!authenticatedUser.isSuperAdministrator()) {
            if (action.get().organizationId() == null
                    || !(action.get().organizationId().equals(authenticatedUser.getOrganizationId()))) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }

        // Delete subscription.
        eventEngine.unsubscribeActionFromEvent(subscriptionId);

        return Response.ok().build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ORGADMINISTRATOR)
    @Path("/detections/types/show/{name}")
    @Operation(operationId = "findAlertType", summary = "Get a detection type",
            description = "Returns the detection type with the event action subscriptions of the organization for "
                    + "it. Super administrators have to pass an organization, for all other users the organization "
                    + "of the calling user is used. Requires organization administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Detection type found.",
            content = @Content(schema = @Schema(implementation = DetectionAlertTypeDetailsResponse.class)))
    @ApiResponse(responseCode = "400", description = "A super administrator did not pass an organization.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Detection type not found.", content = @Content)
    public Response findDetectionType(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Organization UUID. Required for super administrators.") @QueryParam("organization_uuid") @NotNull UUID filterOrganizationId,
                                      @Parameter(description = "Name of the detection type.") @PathParam("name") @NotEmpty String name) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        UUID organizationId;
        if (authenticatedUser.isSuperAdministrator()) {
            if (filterOrganizationId != null) {
                organizationId = filterOrganizationId;
            } else {
                // Super admins have to select an org.
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        } else {
            organizationId = authenticatedUser.getOrganizationId();
        }

        DetectionType requestedType = null;
        for (DetectionType type : nzyme.getDetectionAlertService().findAllDetectionTypes(null, Integer.MAX_VALUE, 0)) {
            if (type.name().equals(name.toUpperCase())) {
                requestedType = type;
            }
        }

        if (requestedType == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        EventEngineImpl eventEngine = (EventEngineImpl) nzyme.getEventEngine();
        List<SubscriptionDetailsResponse> subscriptions = Lists.newArrayList();
        for (SubscriptionEntry sub : eventEngine.findAllActionsOfSubscription(organizationId, requestedType.name())) {
            eventEngine.findEventAction(sub.actionId()).ifPresent(eventActionEntry ->
                    subscriptions.add(buildSubscriptionDetailsResponse(sub, eventActionEntry))
            );
        }

        return Response.ok(
                DetectionAlertTypeDetailsResponse.create(
                    requestedType.name(),
                    requestedType.getTitle(),
                    requestedType.getSubsystem().name(),
                    subscriptions
                )
        ).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ORGADMINISTRATOR)
    @Path("/detections/types/show/{detectionTypeName}/subscriptions")
    @Operation(operationId = "createAlertTypeSubscription", summary = "Subscribe an action to a detection type",
            description = "Subscribes an event action to this detection type, so the action runs whenever a "
                    + "detection event of this type is raised for the organization. The same action can only be "
                    + "subscribed once per type. Requires organization administrator permissions.",
            externalDocs = @ExternalDocumentation(description = "Subscriptions and actions in the Nzyme documentation",
                    url = "https://go.nzyme.org/detection-alerts-subscriptions"))
    @ApiResponse(responseCode = "200", description = "Action subscribed.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The action is already subscribed to this detection type.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The calling user cannot administer the organization in the request, "
            + "or the action is a system action or belongs to another organization. The latter case carries an error message.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Detection type or event action not found.", content = @Content)
    public Response subscribeActionToDetectionEvent(@Parameter(hidden = true) @Context SecurityContext sc,
                                                    @Parameter(description = "Name of the detection type.") @PathParam("detectionTypeName") @NotEmpty String detectionTypeName,
                                                    @RequestBody(description = "The event action and the organization to subscribe it for.", required = true, content = @Content(mediaType = "application/json")) @Valid DetectionEventSubscriptionRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!authenticatedUser.isSuperAdministrator()
                && !req.organizationId().equals(authenticatedUser.getOrganizationId())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        DetectionType detectionType;
        try {
            detectionType = DetectionType.valueOf(detectionTypeName.toUpperCase());
        } catch(IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        EventEngineImpl eventEngine = ((EventEngineImpl) nzyme.getEventEngine());

        // Pull action.
        Optional<EventActionEntry> action = eventEngine.findEventAction(req.actionId());

        if (action.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Only actions of the same organization can be subscribed to detection events. System actions cannot.
        if (action.get().organizationId() == null || !action.get().organizationId().equals(req.organizationId())) {
            return Response
                    .status(Response.Status.FORBIDDEN)
                    .entity(ErrorResponse.create("Only event actions of the same organization can be subscribed to detection events."))
                    .build();
        }

        // Check if this event already has this action ID subscribed to it.
        for (SubscriptionEntry sub : eventEngine
                .findAllActionsOfSubscription(req.organizationId(), detectionType.name())) {
            if (sub.actionId().equals(action.get().uuid())) {
                return Response
                        .status(Response.Status.UNAUTHORIZED)
                        .entity(ErrorResponse.create("Action is already subscribed to this event."))
                        .build();
            }
        }

        eventEngine.subscribeActionToEvent(
                req.organizationId(), EventType.DETECTION, detectionType.name(), action.get().uuid());

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ORGADMINISTRATOR)
    @Path("/detections/types/show/{detectionTypeName}/subscriptions/show/{subscriptionId}")
    @Operation(operationId = "deleteAlertTypeSubscription", summary = "Delete a subscription of a detection type",
            description = "Removes the subscription of an event action to this detection type. Requires "
                    + "organization administrator permissions.",
            externalDocs = @ExternalDocumentation(description = "Subscriptions and actions in the Nzyme documentation",
                    url = "https://go.nzyme.org/detection-alerts-subscriptions"))
    @ApiResponse(responseCode = "200", description = "Subscription deleted.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The subscribed action does not belong to an organization the calling user can administer.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Subscription or the event action behind it not found.", content = @Content)
    public Response unsubscribeActionFromDetectionEvent(@Parameter(hidden = true) @Context SecurityContext sc,
                                                        @Parameter(description = "Subscription UUID.") @PathParam("subscriptionId") @NotNull UUID subscriptionId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        EventEngineImpl eventEngine = ((EventEngineImpl) nzyme.getEventEngine());

        // Fetch action UUID from subscription.
        Optional<UUID> actionUuid = eventEngine.findActionOfSubscription(subscriptionId);

        if (actionUuid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Fetch action.
        Optional<EventActionEntry> action = eventEngine.findEventAction(actionUuid.get());

        if (action.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if superadmin or event is subscription of org.
        if (!authenticatedUser.isSuperAdministrator()) {
            if (action.get().organizationId() == null
                    || !(action.get().organizationId().equals(authenticatedUser.getOrganizationId()))) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
       }

        // Delete subscription.
        eventEngine.unsubscribeActionFromEvent(subscriptionId);

        return Response.ok().build();
    }


    private static SubscriptionDetailsResponse buildSubscriptionDetailsResponse(SubscriptionEntry subscriptionEntry,
                                                                                EventActionEntry eventActionEntry) {
        EventActionType eventActionType = EventActionType.valueOf(eventActionEntry.actionType());

        return SubscriptionDetailsResponse.create(
                subscriptionEntry.uuid(),
                eventActionEntry.uuid(),
                eventActionType.name(),
                eventActionType.getHumanReadable(),
                eventActionEntry.name()
        );
    }

}
