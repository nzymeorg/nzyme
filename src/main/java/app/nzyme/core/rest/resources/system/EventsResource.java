package app.nzyme.core.rest.resources.system;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.events.EventEngineImpl;
import app.nzyme.core.events.db.EventActionEntry;
import app.nzyme.core.events.db.EventEntry;
import app.nzyme.core.events.db.SubscriptionEntry;
import app.nzyme.core.events.types.EventActionType;
import app.nzyme.core.events.types.EventType;
import app.nzyme.core.events.types.SystemEventScope;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.SystemEventSubscriptionRequest;
import app.nzyme.core.rest.responses.events.*;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/system/events")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Events", description = "System events record what happened in Nzyme itself, for example a user signing "
        + "in or an organization being changed. They are either super administrator events, which affect the whole "
        + "cluster, or organization events. Event actions can be subscribed to every event type.",
        externalDocs = @ExternalDocumentation(description = "Alerting in the Nzyme documentation",
                url = "https://go.nzyme.org/alerting"))
public class EventsResource extends UserAuthenticatedResource {

    private static final Logger LOG = LogManager.getLogger(UserAuthenticatedResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Operation(operationId = "findEvents", summary = "List events",
            description = "Returns recorded events, newest first. You have to pass at least one event type: an empty "
                    + "event_types parameter returns an empty list. Omit organization_id as a super administrator to "
                    + "get the events of all organizations. The total count in the response counts the events that "
                    + "match the event type filter. Requires organization administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Events found.",
            content = @Content(schema = @Schema(implementation = EventsListResponse.class)))
    @ApiResponse(responseCode = "403", description = "The requested organization is not administered by the calling user.", content = @Content)
    public Response findAllEvents(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                  @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                  @Parameter(description = "Comma separated list of event type names to include. An empty value returns no events.") @QueryParam("event_types")String eventTypes,
                                  @Parameter(description = "Organization UUID. Super administrators can omit this to query all organizations.") @QueryParam("organization_id") @Nullable UUID organizationId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (Strings.isNullOrEmpty(eventTypes)) {
            return Response.ok(EventsListResponse.create(0, Collections.emptyList())).build();
        }

        // Check if user is allowed to access the requested org.
        if (!authenticatedUser.isSuperAdministrator()) {
            if (organizationId == null || !organizationId.equals(authenticatedUser.getOrganizationId())) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }

        List<String> types = Splitter.on(",").splitToList(eventTypes);

        List<EventEntry> events;
        long totalEvents;
        if (organizationId == null) {
            // Superadmin.
            events = ((EventEngineImpl) nzyme.getEventEngine()).findAllEventsOfAllOrganizations(types, limit, offset);
            totalEvents = ((EventEngineImpl) nzyme.getEventEngine()).countAllEventsOfAllOrganizations(types);
        } else {
            // Organization admin.
            events = ((EventEngineImpl) nzyme.getEventEngine())
                    .findAllEventsOfOrganization(types, organizationId, limit, offset);
            totalEvents = ((EventEngineImpl) nzyme.getEventEngine()).countAllEventsOfOrganization(types, organizationId);
        }

        List<EventDetailsResponse> result = Lists.newArrayList();
        for (EventEntry event : events) {
            result.add(EventDetailsResponse.create(
                    event.uuid(),
                    event.eventType(),
                    event.reference(),
                    event.details(),
                    event.createdAt()
            ));
        }

        return Response.ok(EventsListResponse.create(totalEvents, result)).build();
    }

    @GET
    @Path("/types")
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Operation(operationId = "findEventTypes", summary = "List event types and their subscriptions",
            description = "Returns the available event types with the event actions subscribed to each of them. You "
                    + "have to pass at least one category: an empty categories parameter returns an empty list. "
                    + "Passing organization_id returns the organization scoped types, omitting it returns the "
                    + "remaining types and is reserved for super administrators. Requires organization administrator "
                    + "permissions.")
    @ApiResponse(responseCode = "200", description = "Event types found.",
            content = @Content(schema = @Schema(implementation = EventTypesListResponse.class)))
    @ApiResponse(responseCode = "403", description = "The requested organization is not administered by the calling user.", content = @Content)
    public Response findAllEventTypes(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                      @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                      @Parameter(description = "Comma separated list of event category names to include. An empty value returns no event types.") @QueryParam("categories") String eventCategories,
                                      @Parameter(description = "Organization UUID. Super administrators can omit this to query the types that are not organization scoped.") @QueryParam("organization_id") @Nullable UUID organizationId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        // Org admins can only request data for own org. (types are always the same, but subs are not)
        if (!authenticatedUser.isSuperAdministrator()) {
            if (organizationId == null || !organizationId.equals(authenticatedUser.getOrganizationId())) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }

        if (Strings.isNullOrEmpty(eventCategories)) {
            return Response.ok(EventTypesListResponse.create(0, Collections.emptyList())).build();
        }

        List<String> categories = Splitter.on(",").splitToList(eventCategories);

        List<SystemEventType> types = Lists.newArrayList();
        int totalEvents = 0;
        for (SystemEventType type : SystemEventType.values()) {
            if (organizationId == null && type.getScope().equals(SystemEventScope.ORGANIZATION)) {
                // Skip organization event types for system/superadmin view.
                continue;
            }

            if (organizationId != null && !type.getScope().equals(SystemEventScope.ORGANIZATION)) {
                // Skip all non-org event types for org request.
                continue;
            }

            if (categories.contains(type.getCategory().toString())) {
                types.add(type);
                totalEvents++;
            }
        }

        List<SystemEventType> page = types.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());

        EventEngineImpl eventEngine = ((EventEngineImpl) nzyme.getEventEngine());

        List<SystemEventTypeDetailsResponse> result = Lists.newArrayList();
        for (SystemEventType entry : page) {
            List<SubscriptionDetailsResponse> subscriptions = Lists.newArrayList();
            for (SubscriptionEntry sub : eventEngine.findAllActionsOfSubscription(organizationId, entry.name())) {
                eventEngine.findEventAction(sub.actionId()).ifPresent(eventActionEntry ->
                    subscriptions.add(buildSubscriptionDetailsResponse(sub, eventActionEntry))
                );
            }

            result.add(SystemEventTypeDetailsResponse.create(
                    entry.name(),
                    entry.getCategory().name(),
                    entry.getCategory().getHumanReadableName(),
                    entry.getHumanReadableName(),
                    entry.getDescription(),
                    subscriptions
            ));
        }

        return Response.ok(EventTypesListResponse.create(totalEvents, result)).build();
    }

    @GET
    @Path("/types/system/show/{eventTypeName}")
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Operation(operationId = "findSystemEventType", summary = "Get a system event type",
            description = "Returns the description of a system event type and the event actions subscribed to it. "
                    + "Requires organization administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Event type found.",
            content = @Content(schema = @Schema(implementation = SystemEventTypeDetailsResponse.class)))
    @ApiResponse(responseCode = "403", description = "The requested organization is not administered by the calling user, or the event type is not organization scoped.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Event type not found.", content = @Content)
    public Response findSystemEventType(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "System event type name.") @PathParam("eventTypeName") @NotEmpty String eventTypeName,
                                        @Parameter(description = "Organization UUID. Super administrators can omit this for the types that are not organization scoped.") @QueryParam("organization_id") @Nullable UUID organizationId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        // Org admins can only request data for own org. (types are always the same, but subs are not)
        if (!authenticatedUser.isSuperAdministrator()) {
            if (organizationId == null || !organizationId.equals(authenticatedUser.getOrganizationId())) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }

        SystemEventType eventType;
        try {
            eventType = SystemEventType.valueOf(eventTypeName.toUpperCase());
        } catch(IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!authenticatedUser.isSuperAdministrator() && !eventType.getScope().equals(SystemEventScope.ORGANIZATION)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        EventEngineImpl eventEngine = ((EventEngineImpl) nzyme.getEventEngine());
        List<SubscriptionDetailsResponse> subscriptions = Lists.newArrayList();
        for (SubscriptionEntry sub : eventEngine.findAllActionsOfSubscription(organizationId, eventType.name())) {
            eventEngine.findEventAction(sub.actionId()).ifPresent(eventActionEntry ->
                    subscriptions.add(buildSubscriptionDetailsResponse(sub, eventActionEntry))
            );
        }

        return Response.ok(SystemEventTypeDetailsResponse.create(
                eventType.name(),
                eventType.getCategory().name(),
                eventType.getCategory().getHumanReadableName(),
                eventType.getHumanReadableName(),
                eventType.getDescription(),
                subscriptions
        )).build();
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/types/system/show/{eventTypeName}/subscriptions")
    @Operation(operationId = "createSystemEventSubscription", summary = "Subscribe an action to a system event type",
            description = "The action runs every time the event type fires. An action can only be subscribed once per "
                    + "event type, but the same action can be subscribed to as many event types as you want. "
                    + "Organization administrators can only subscribe actions of their own organization and only to "
                    + "organization scoped event types. Requires organization administrator permissions.",
            externalDocs = @ExternalDocumentation(description = "Subscriptions and actions in the Nzyme documentation",
                    url = "https://go.nzyme.org/detection-alerts-subscriptions"))
    @ApiResponse(responseCode = "200", description = "Action subscribed.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The action is already subscribed to this event type.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The event type or the event action is not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Event type or event action not found.", content = @Content)
    public Response subscribeActionToSystemEvent(@Parameter(hidden = true) @Context SecurityContext sc,
                                                 @Parameter(description = "System event type name.") @PathParam("eventTypeName") @NotEmpty String eventTypeName,
                                                 @RequestBody(description = "UUID of the event action to subscribe and the organization it belongs to.", required = true, content = @Content(mediaType = "application/json"))
                                                 @Valid SystemEventSubscriptionRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        SystemEventType eventType;
        try {
            eventType = SystemEventType.valueOf(eventTypeName.toUpperCase());
        } catch(IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        EventEngineImpl eventEngine = ((EventEngineImpl) nzyme.getEventEngine());

        // Pull action.
        Optional<EventActionEntry> action = eventEngine.findEventAction(req.actionId());

        if (action.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if orgadmin has access to event type.
        if (!authenticatedUser.isSuperAdministrator() && !eventType.getScope().equals(SystemEventScope.ORGANIZATION)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        // Only superadmins can subscribe system/superadmin actions.
        if (!authenticatedUser.isSuperAdministrator() && action.get().organizationId() == null) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        // Check if orgadmin has access to this action and can subscribe it.
        if (!authenticatedUser.isSuperAdministrator() && !action.get().organizationId()
                .equals(authenticatedUser.getOrganizationId())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        // Check if this event already has this action ID subscribed to it.
        for (SubscriptionEntry sub : eventEngine
                .findAllActionsOfSubscription(req.organizationId(), eventType.name())) {
            if (sub.actionId().equals(action.get().uuid())) {
                return Response
                        .status(Response.Status.UNAUTHORIZED)
                        .entity(ErrorResponse.create("Action is already subscribed to this event."))
                        .build();
            }
        }

        eventEngine.subscribeActionToEvent(
                action.get().organizationId(), EventType.SYSTEM, eventType.name(), action.get().uuid());

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/types/system/show/{eventTypeName}/subscriptions/show/{subscriptionId}")
    @Operation(operationId = "deleteSystemEventSubscription", summary = "Unsubscribe an action from a system event type",
            description = "Deletes the subscription. The event action itself is kept. Requires organization "
                    + "administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Action unsubscribed.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The subscribed event action belongs to another organization.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Subscription or event action not found.", content = @Content)
    public Response unsubscribeActionFromSystemEvent(@Parameter(hidden = true) @Context SecurityContext sc,
                                                     @Parameter(description = "System event type name.") @PathParam("eventTypeName") @NotEmpty String eventTypeName,
                                                     @Parameter(description = "Subscription UUID.") @PathParam("subscriptionId") UUID subscriptionId) {
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
