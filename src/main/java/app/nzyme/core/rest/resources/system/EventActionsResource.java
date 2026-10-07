package app.nzyme.core.rest.resources.system;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.crypto.Crypto;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.events.EventEngineImpl;
import app.nzyme.core.events.actions.EventActionUtilities;
import app.nzyme.core.events.actions.email.EmailActionConfiguration;
import app.nzyme.core.events.actions.syslog.SyslogActionConfiguration;
import app.nzyme.core.events.actions.webhook.WebhookActionConfiguration;
import app.nzyme.core.events.db.EventActionEntry;
import app.nzyme.core.events.types.EventActionType;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.*;
import app.nzyme.core.rest.responses.events.EventActionDetailsResponse;
import app.nzyme.core.rest.responses.events.EventActionsListResponse;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Path("/api/system/events/actions")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Event Actions", description = "An event action is something Nzyme does when an event fires, for example "
        + "sending an email, posting to a webhook or sending a syslog message. An action only runs once it is "
        + "subscribed to a system event type or a detection type. Actions created in the system scope belong to no "
        + "organization and can only be subscribed to system events.",
        externalDocs = @ExternalDocumentation(description = "Action types in the Nzyme documentation",
                url = "https://go.nzyme.org/alerting-action-types"))
public class EventActionsResource extends UserAuthenticatedResource {

    private static final Logger LOG = LogManager.getLogger(EventActionsResource.class);

    @Inject
    private NzymeNode nzyme;

    private final ObjectMapper om = new ObjectMapper();

    @GET
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Operation(operationId = "findEventActions", summary = "List event actions of the system scope",
            description = "Returns the event actions that are not owned by an organization and are available to super "
                    + "administrators only. Every action includes the system event types and detection types it is "
                    + "subscribed to. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Event actions found.",
            content = @Content(schema = @Schema(implementation = EventActionsListResponse.class)))
    public Response findAllActionsOfSuperAdministrators(@Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                                        @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        EventEngineImpl eventEngine = ((EventEngineImpl) nzyme.getEventEngine());
        long total = eventEngine.countAllEventActionsOfSuperadministrators();
        List<EventActionDetailsResponse> events = Lists.newArrayList();
        for (EventActionEntry ea : eventEngine.findAllEventActionsOfSuperadministrators(limit, offset)) {

            List<SystemEventType> subscribedSystemEvents = eventEngine
                    .findAllSystemEventTypesActionIsSubscribedTo(ea.uuid());
            List<DetectionType> subscribedDetectionEvents = eventEngine
                    .findAllDetectionEventTypesActionIsSubscribedTo(ea.uuid());
            events.add(EventActionUtilities.eventActionEntryToResponse(
                    ea, subscribedSystemEvents, subscribedDetectionEvents
            ));
        }

        return Response.ok(EventActionsListResponse.create(total, events)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/show/{actionId}")
    @Operation(operationId = "findEventAction", summary = "Get details of an event action",
            description = "Includes the system event types and detection types the action is subscribed to. Requires "
                    + "super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Event action found.",
            content = @Content(schema = @Schema(implementation = EventActionDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Event action not found.", content = @Content)
    public Response findAction(@Parameter(description = "Event action UUID.") @PathParam("actionId") UUID actionId) {
        EventEngineImpl eventEngine = ((EventEngineImpl) nzyme.getEventEngine());

        Optional<EventActionEntry> ea = eventEngine.findEventAction(actionId);

        if (ea.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<SystemEventType> subscribedSystemEvents = eventEngine
                .findAllSystemEventTypesActionIsSubscribedTo(ea.get().uuid());
        List<DetectionType> subscribedDetectionEvents = eventEngine
                .findAllDetectionEventTypesActionIsSubscribedTo(ea.get().uuid());

        return Response.ok(EventActionUtilities.eventActionEntryToResponse(
                ea.get(),
                subscribedSystemEvents,
                subscribedDetectionEvents
        )).build();
    }

    @DELETE
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{actionId}")
    @Operation(operationId = "deleteEventAction", summary = "Delete an event action",
            description = "An action that is still subscribed to an event type cannot be deleted. Remove all of its "
                    + "subscriptions first. Requires organization administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Event action deleted.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The event action still has subscriptions.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The event action belongs to another organization.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Event action not found.", content = @Content)
    public Response deleteAction(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Event action UUID.") @PathParam("actionId") UUID actionId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        EventEngineImpl eventEngine = (EventEngineImpl) nzyme.getEventEngine();

        // Find action.
        Optional<EventActionEntry> action = eventEngine.findEventAction(actionId);

        if (action.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check permissions.
        if (!authenticatedUser.isSuperAdministrator()) {
            if (!action.get().organizationId().equals(authenticatedUser.getOrganizationId())) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }

        // Check if action has active subscriptions.
        List<SystemEventType> subscribedSystemEvents = eventEngine
                .findAllSystemEventTypesActionIsSubscribedTo(action.get().uuid());
        List<DetectionType> subscribedDetectionEvents = eventEngine
                .findAllDetectionEventTypesActionIsSubscribedTo(action.get().uuid());

        if (!subscribedSystemEvents.isEmpty() || !subscribedDetectionEvents.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        eventEngine.deleteEventAction(actionId);

        return Response.ok().build();
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/email")
    @Operation(operationId = "createEmailEventAction", summary = "Create an email event action",
            description = "Creates an action that sends a formatted notification email to a list of receivers. The "
                    + "email goes out over the cluster wide SMTP server a super administrator configures on the "
                    + "integrations page, and the subject prefix is prepended to the generated subject. Organization "
                    + "administrators can only create actions in their own organization. Requires organization "
                    + "administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Event action created.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The requested organization is not administered by the calling user.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The action configuration could not be serialized.", content = @Content)
    public Response createEmailAction(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @RequestBody(description = "Name, description, subject prefix, receivers and owning organization of the action.", required = true, content = @Content(mediaType = "application/json"))
                                      @Valid CreateEmailEventActionRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        // Check permissions.
        if (!authenticatedUser.isSuperAdministrator()) {
            if (req.organizationId() == null || !req.organizationId().equals(authenticatedUser.getOrganizationId())) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }

        String config;
        try {
            config = om.writeValueAsString(EmailActionConfiguration.create(req.subjectPrefix(), req.receivers()));
        } catch (JacksonException e) {
            LOG.error("Could not create action configuration.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        ((EventEngineImpl) nzyme.getEventEngine()).createEventAction(
                req.organizationId(),
                EventActionType.EMAIL,
                req.name(),
                req.description(),
                config
        );

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/email/{actionId}")
    @Operation(operationId = "updateEmailEventAction", summary = "Update an email event action",
            description = "Replaces the name, description and configuration of the action. The owning organization "
                    + "cannot be changed. Requires organization administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Event action updated.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The event action belongs to another organization.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Event action not found.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The action configuration could not be serialized.", content = @Content)
    public Response updateEmailAction(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @RequestBody(description = "New name, description, subject prefix and receivers of the action.", required = true, content = @Content(mediaType = "application/json"))
                                      @Valid UpdateEmailEventActionRequest req,
                                      @Parameter(description = "Event action UUID.") @PathParam("actionId") UUID actionId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        // Find action.
        Optional<EventActionEntry> action = ((EventEngineImpl) nzyme.getEventEngine()).findEventAction(actionId);

        if (action.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check permissions.
        if (!authenticatedUser.isSuperAdministrator()) {
            if (!action.get().organizationId().equals(authenticatedUser.getOrganizationId())) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }

        String config;
        try {
            config = om.writeValueAsString(EmailActionConfiguration.create(req.subjectPrefix(), req.receivers()));
        } catch (JacksonException e) {
            LOG.error("Could not create action configuration.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        ((EventEngineImpl) nzyme.getEventEngine()).updateAction(
                action.get().uuid(),
                req.name(),
                req.description(),
                config
        );

        return Response.ok().build();
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/webhook")
    @Operation(operationId = "createWebhookEventAction", summary = "Create a webhook event action",
            description = "Creates an action that posts one event per request as JSON to an HTTP endpoint. An "
                    + "optional bearer token is stored encrypted with the cluster key and sent in the Authorization "
                    + "header. Allowing insecure connections turns off TLS certificate and hostname verification. "
                    + "Organization administrators can only create actions in their own organization. Requires "
                    + "organization administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Event action created.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The requested organization is not administered by the calling user.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The bearer token could not be encrypted, or the action configuration could not be serialized.", content = @Content)
    public Response createWebhookAction(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @RequestBody(description = "Name, description, target URL, bearer token and owning organization of the action.", required = true, content = @Content(mediaType = "application/json"))
                                        @Valid CreateWebhookEventActionRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        // Check permissions.
        if (!authenticatedUser.isSuperAdministrator()) {
            if (req.organizationId() == null || !req.organizationId().equals(authenticatedUser.getOrganizationId())) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }

        String encryptedBearerToken = "";
        if (req.bearerToken() != null && !req.bearerToken().trim().isEmpty()) {
            try {
                encryptedBearerToken = BaseEncoding.base64()
                        .encode(nzyme.getCrypto().encryptWithClusterKey(req.bearerToken().getBytes()));
            } catch (Crypto.CryptoOperationException e) {
                LOG.error("Could not encrypt bearer token.", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }
        }

        String config;
        try {
            config = om.writeValueAsString(WebhookActionConfiguration.create(
                    req.url(), req.allowInsecure(), encryptedBearerToken

            ));
        } catch (JacksonException e) {
            LOG.error("Could not create action configuration.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        ((EventEngineImpl) nzyme.getEventEngine()).createEventAction(
                req.organizationId(),
                EventActionType.WEBHOOK,
                req.name(),
                req.description(),
                config
        );

        return Response.ok().build();
    }


    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/webhook/{actionId}")
    @Operation(operationId = "updateWebhookEventAction", summary = "Update a webhook event action",
            description = "Replaces the name, description and configuration of the action. Send an empty bearer token "
                    + "to remove the stored one. Requires organization administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Event action updated.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The event action belongs to another organization.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Event action not found.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The bearer token could not be encrypted, or the action configuration could not be serialized.", content = @Content)
    public Response updateWebhookAction(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @RequestBody(description = "New name, description, target URL and bearer token of the action.", required = true, content = @Content(mediaType = "application/json"))
                                        @Valid UpdateWebhookEventActionRequest req,
                                        @Parameter(description = "Event action UUID.") @PathParam("actionId") UUID actionId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        // Find action.
        Optional<EventActionEntry> action = ((EventEngineImpl) nzyme.getEventEngine()).findEventAction(actionId);

        if (action.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check permissions.
        if (!authenticatedUser.isSuperAdministrator()) {
            if (!action.get().organizationId().equals(authenticatedUser.getOrganizationId())) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }

        String encryptedBearerToken = "";
        if (req.bearerToken() != null && !req.bearerToken().isEmpty()) {
            try {
                encryptedBearerToken = BaseEncoding.base64()
                        .encode(nzyme.getCrypto().encryptWithClusterKey(req.bearerToken().getBytes()));
            } catch (Crypto.CryptoOperationException e) {
                LOG.error("Could not encrypt bearer token.", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }
        }

        String config;
        try {
            config = om.writeValueAsString(WebhookActionConfiguration.create(
                    req.url(), req.allowInsecure(), encryptedBearerToken
            ));
        } catch (JacksonException e) {
            LOG.error("Could not create action configuration.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        ((EventEngineImpl) nzyme.getEventEngine()).updateAction(
                action.get().uuid(),
                req.name(),
                req.description(),
                config
        );

        return Response.ok().build();
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/syslog")
    @Operation(operationId = "createSyslogEventAction", summary = "Create a syslog event action",
            description = "Creates an action that sends one RFC 5424 message per event to a syslog server. The only "
                    + "supported protocol is UDP_RFC5424 and the syslog hostname is used as the hostname field of "
                    + "the message. Organization administrators can only create actions in their own organization. "
                    + "Requires organization administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Event action created.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The requested organization is not administered by the calling user.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The action configuration could not be serialized.", content = @Content)
    public Response createSyslogAction(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @RequestBody(description = "Name, description, protocol, syslog hostname, target host and port, and owning organization of the action.", required = true, content = @Content(mediaType = "application/json"))
                                       @Valid CreateSyslogEventActionRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        // Check permissions.
        if (!authenticatedUser.isSuperAdministrator()) {
            if (req.organizationId() == null || !req.organizationId().equals(authenticatedUser.getOrganizationId())) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }

        String config;
        try {
            config = om.writeValueAsString(SyslogActionConfiguration.create(
                    req.protocol(), req.syslogHostname(), req.host(), req.port()
            ));
        } catch (JacksonException e) {
            LOG.error("Could not create action configuration.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        ((EventEngineImpl) nzyme.getEventEngine()).createEventAction(
                req.organizationId(),
                EventActionType.SYSLOG,
                req.name(),
                req.description(),
                config
        );

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/syslog/{actionId}")
    @Operation(operationId = "updateSyslogEventAction", summary = "Update a syslog event action",
            description = "Replaces the name, description and configuration of the action. The owning organization "
                    + "cannot be changed. Requires organization administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Event action updated.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The event action belongs to another organization.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Event action not found.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The action configuration could not be serialized.", content = @Content)
    public Response updateSyslogAction(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @RequestBody(description = "New name, description, protocol, syslog hostname, target host and port of the action.", required = true, content = @Content(mediaType = "application/json"))
                                       @Valid UpdateSyslogEventActionRequest req,
                                       @Parameter(description = "Event action UUID.") @PathParam("actionId") UUID actionId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        // Find action.
        Optional<EventActionEntry> action = ((EventEngineImpl) nzyme.getEventEngine()).findEventAction(actionId);

        if (action.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check permissions.
        if (!authenticatedUser.isSuperAdministrator()) {
            if (!action.get().organizationId().equals(authenticatedUser.getOrganizationId())) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }

        String config;
        try {
            config = om.writeValueAsString(SyslogActionConfiguration.create(
                    req.protocol(), req.syslogHostname(), req.host(), req.port()
            ));
        } catch (JacksonException e) {
            LOG.error("Could not create action configuration.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        ((EventEngineImpl) nzyme.getEventEngine()).updateAction(
                action.get().uuid(),
                req.name(),
                req.description(),
                config
        );

        return Response.ok().build();
    }











}
