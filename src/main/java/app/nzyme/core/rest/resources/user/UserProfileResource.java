package app.nzyme.core.rest.resources.user;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.events.types.SystemEvent;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.CreateApiKeyRequest;
import app.nzyme.core.rest.requests.UpdateUserOwnPasswordRequest;
import app.nzyme.core.rest.responses.authentication.apikeys.ApiKeyCreatedResponse;
import app.nzyme.core.rest.responses.authentication.apikeys.ApiKeyDetailsResponse;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
import app.nzyme.core.rest.responses.userprofile.UserProfileDetailsResponse;
import app.nzyme.core.rest.authentication.SessionOnly;
import app.nzyme.core.security.authentication.ApiKeys;
import app.nzyme.core.security.authentication.PasswordHasher;
import app.nzyme.core.security.authentication.db.ApiKeyEntry;
import app.nzyme.core.security.authentication.db.UserEntry;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/api/user")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "User Profile", description = "Endpoints the calling user can use to manage their own profile, "
        + "password, multi-factor authentication and API keys.")
public class UserProfileResource extends UserAuthenticatedResource {

    private static final Logger LOG = LogManager.getLogger(UserProfileResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/profile")
    @Operation(operationId = "findOwnProfile", summary = "Get your own profile",
            description = "Returns email address, name and unit system preference of the calling user.")
    @ApiResponse(responseCode = "200", description = "Profile found.",
            content = @Content(schema = @Schema(implementation = UserProfileDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "The user referenced in the session no longer exists.", content = @Content)
    public Response findOwnProfile(@Parameter(hidden = true) @Context SecurityContext sc) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserById(authenticatedUser.getUserId());

        if (user.isEmpty()) {
            LOG.error("User <{}> referenced in session does not exist.", authenticatedUser.getUserId());
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(UserProfileDetailsResponse.create(
                user.get().email(), user.get().name(), user.get().unitSystem())
        ).build();
    }

    @PUT
    @Path("/password")
    @SessionOnly
    @Operation(operationId = "updateOwnPassword", summary = "Change your own password",
            description = "Changes the password of the calling user after verifying the current password. All "
                    + "sessions of the user are invalidated, so you have to log in again. Requires an interactive "
                    + "session; API keys are rejected.")
    @ApiResponse(responseCode = "200", description = "Password changed.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The current password is incorrect or one of the passwords is empty.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The user referenced in the session no longer exists.", content = @Content)
    public Response changeOwnPassword(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @RequestBody(description = "The current and the new password.", required = true, content = @Content(mediaType = "application/json")) UpdateUserOwnPasswordRequest r) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (Strings.isNullOrEmpty(r.currentPassword()) || Strings.isNullOrEmpty(r.newPassword())) {
             return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserById(authenticatedUser.getUserId());

        if (user.isEmpty()) {
            LOG.error("User <{}> referenced in session does not exist.", authenticatedUser.getUserId());
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        PasswordHasher hasher = new PasswordHasher(nzyme.getMetrics());

        // Compare old password.
        try {
            if (!hasher.compareHash(r.currentPassword(), user.get().passwordHash(), user.get().passwordSalt())) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ErrorResponse.create("Incorrect current password.")).build();
            }
        } catch(Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ErrorResponse.create("Incorrect current password.")).build();
        }

        PasswordHasher.GeneratedHashAndSalt newPasswordHash = hasher.createHash(r.newPassword());

        nzyme.getAuthenticationService().editUserPassword(
                user.get().uuid(),
                newPasswordHash
        );

        // Invalidate session of user.
        nzyme.getAuthenticationService().deleteAllSessionsOfUser(user.get().uuid());

        // System event.
        nzyme.getEventEngine().processEvent(SystemEvent.create(
                SystemEventType.AUTHENTICATION_PASSWORD_CHANGED,
                DateTime.now(),
                "Password of user [" + user.get().email() + "] was changed by same user."
        ), user.get().organizationId(), user.get().tenantId());

        return Response.ok().build();
    }

    @GET
    @Path("/mfa/recoverycodes")
    @SessionOnly
    @Operation(operationId = "findOwnMfaRecoveryCodes", summary = "List your own MFA recovery codes",
            description = "Returns an object that maps each multi-factor authentication recovery code of the "
                    + "calling user to a boolean that is true as long as the code has not been used. Requires an "
                    + "interactive session; API keys are rejected.")
    @ApiResponse(responseCode = "200", description = "Recovery codes found.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "404", description = "No recovery codes exist for this user.", content = @Content)
    public Response findOwnMfaRecoveryCodes(@Parameter(hidden = true) @Context SecurityContext sc) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<Map<String, Boolean>> recoveryCodes = nzyme.getAuthenticationService()
                .getUserMFARecoveryCodes(authenticatedUser.getUserId());

        if (recoveryCodes.isEmpty()) {
            LOG.error("No recovery codes for user <{}> found.", authenticatedUser.getUserId());
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(recoveryCodes.get()).build();
    }

    @PUT
    @Path("/mfa/reset")
    @SessionOnly
    @Operation(operationId = "resetOwnMfa", summary = "Reset your own MFA method",
            description = "Removes the multi-factor authentication setup of the calling user. You have to enroll a "
                    + "new method the next time you log in. Requires an interactive session; API keys are rejected.")
    @ApiResponse(responseCode = "200", description = "Multi-factor authentication reset.", content = @Content)
    public Response resetOwnMfa(@Parameter(hidden = true) @Context SecurityContext sc) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        nzyme.getAuthenticationService().resetMFAOfUser(authenticatedUser.getUserId());

        // System event.
        nzyme.getEventEngine().processEvent(SystemEvent.create(
                SystemEventType.AUTHENTICATION_MFA_RESET,
                DateTime.now(),
                "MFA method of user [" + authenticatedUser.getEmail() + "] was reset by same user."
        ), authenticatedUser.getOrganizationId(), authenticatedUser.getTenantId());

        return Response.ok().build();
    }

    @PUT
    @Path("/unitsystem/{unit_system}")
    @Operation(operationId = "updateOwnUnitSystem", summary = "Set your own unit system",
            description = "Sets the unit system the web interface uses to display distances, speeds and "
                    + "temperatures for the calling user.")
    @ApiResponse(responseCode = "200", description = "Unit system changed.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The unit system is not metric or imperial.", content = @Content)
    public Response setOwnUnitSystem(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Unit system to use. Either metric or imperial.") @PathParam("unit_system") String unitSystem) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!unitSystem.equals("metric") && !unitSystem.equals("imperial")) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        nzyme.getAuthenticationService().setUnitSystemOfUser(authenticatedUser.getUserId(), unitSystem);

        return Response.ok().build();
    }

    @GET
    @Path("/apikeys")
    @SessionOnly
    @Operation(operationId = "findOwnApiKeys", summary = "List your own API keys",
            description = "Returns the metadata of all API keys of the calling user, including name, expiry and "
                    + "last activity. Nzyme only stores a hash of each key, so the key itself is never returned "
                    + "here. Requires an interactive session; API keys are rejected.")
    @ApiResponse(responseCode = "200", description = "API keys found.",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = ApiKeyDetailsResponse.class))))
    public Response listApiKeys(@Parameter(hidden = true) @Context SecurityContext sc) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        List<ApiKeyDetailsResponse> keys = Lists.newArrayList();
        for (ApiKeyEntry key : nzyme.getAuthenticationService().findAllApiKeys(authenticatedUser.getUserId())) {
            keys.add(ApiKeyDetailsResponse.create(
                    key.uuid(),
                    key.userId(),
                    key.name(),
                    key.lastActivity(),
                    key.expiresAt(),
                    key.createdAt()
            ));
        }


        return Response.ok(keys).build();
    }

    @POST
    @Path("/apikeys")
    @SessionOnly
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "api_keys_manage_own" })
    @Operation(operationId = "createOwnApiKey", summary = "Create an API key for yourself",
            description = "Creates a new API key for the calling user. The plaintext key is returned exactly once "
                    + "in this response and cannot be retrieved later, because Nzyme only stores a hash of it. "
                    + "Requires an interactive session; API keys are rejected. Requires the api_keys_manage_own "
                    + "feature permission.")
    @ApiResponse(responseCode = "201", description = "API key created. The response contains the plaintext key.",
            content = @Content(schema = @Schema(implementation = ApiKeyCreatedResponse.class)))
    public Response createApiKey(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @RequestBody(description = "Name of the key and an optional expiry in days.", required = true, content = @Content(mediaType = "application/json")) @Valid CreateApiKeyRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        DateTime expiresAt = req.expiryDays() != null ?  DateTime.now().plusDays(req.expiryDays()) : null;

        String plaintextKey = ApiKeys.generate();

        nzyme.getAuthenticationService().createApiKey(
                authenticatedUser.getUserId(), req.name(), ApiKeys.hash(plaintextKey), expiresAt
        );

        // System event.
        nzyme.getEventEngine().processEvent(SystemEvent.create(
                SystemEventType.AUTHENTICATION_USER_API_KEY_CREATED,
                DateTime.now(),
                "User [" + authenticatedUser.getEmail() + "] created an API key for themselves."
        ), authenticatedUser.getOrganizationId(), authenticatedUser.getTenantId());

        return Response.status(Response.Status.CREATED)
                .entity(ApiKeyCreatedResponse.create(plaintextKey, expiresAt))
                .build();
    }

    @DELETE
    @Path("/apikeys/show/{uuid}")
    @SessionOnly
    @Operation(operationId = "deleteOwnApiKey", summary = "Delete one of your own API keys",
            description = "Deletes the API key and immediately rejects any further request that uses it. Keys of "
                    + "other users are not affected. Requires an interactive session; API keys are rejected.")
    @ApiResponse(responseCode = "200", description = "API key deleted, or it did not exist for this user.", content = @Content)
    public Response deleteApiKey(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "API key UUID.") @PathParam("uuid") UUID keyId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        nzyme.getAuthenticationService().deleteApiKey(authenticatedUser.getUserId(), keyId);

        return Response.ok().build();
    }

}
