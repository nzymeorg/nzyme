/*
 * This file is part of nzyme.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */

package app.nzyme.core.rest.resources.system.authentication;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.branding.BrandingRegistryKeys;
import app.nzyme.core.crypto.Crypto;
import app.nzyme.core.events.types.SystemEvent;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.monitoring.health.IndicatorStatusLevel;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.authentication.PreMFASecured;
import app.nzyme.core.rest.authentication.SessionOnly;
import app.nzyme.core.rest.requests.MFARecoveryCodeRequest;
import app.nzyme.core.rest.requests.MFAVerificationRequest;
import app.nzyme.core.rest.responses.authentication.MFAInitResponse;
import app.nzyme.core.rest.responses.authentication.SessionInformationResponse;
import app.nzyme.core.rest.responses.authentication.SessionTokenResponse;
import app.nzyme.core.rest.responses.authentication.SessionUserInformationDetailsResponse;
import app.nzyme.core.rest.responses.authentication.branding.BrandingResponse;
import app.nzyme.core.security.authentication.AuthenticationRegistryKeys;
import app.nzyme.core.security.authentication.PasswordHasher;
import app.nzyme.core.security.authentication.RecoveryCodes;
import app.nzyme.core.security.authentication.db.UserEntry;
import app.nzyme.core.security.sessions.SessionId;
import app.nzyme.core.security.sessions.db.SessionEntry;
import app.nzyme.plugin.Subsystem;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import app.nzyme.core.rest.requests.CreateSessionRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;


import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.*;

@Path("/api/system/authentication")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication", description = "Logging in and out of Nzyme, and setting up or passing the multi-factor "
        + "authentication challenge of a session. Nzyme requires multi-factor authentication for every user unless "
        + "an administrator disabled it for that account.")
public class AuthenticationResource extends UserAuthenticatedResource {

    private static final Logger LOG = LogManager.getLogger(AuthenticationResource.class);

    @Inject
    private NzymeNode nzyme;

    @POST
    @Path("/session")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createSession", summary = "Log in and create a session",
            description = "Exchanges an email address and a password for a session token. This endpoint needs no "
                    + "authentication. The returned session still has to pass the multi-factor authentication "
                    + "challenge before it can be used for anything else, unless multi-factor authentication is "
                    + "disabled for the user. Creating a session invalidates all other sessions of the same user. "
                    + "After five failed logins the user is throttled, which delays every further response by five "
                    + "seconds and raises a system event.")
    @ApiResponse(responseCode = "201", description = "Session created.",
            content = @Content(schema = @Schema(implementation = SessionTokenResponse.class)))
    @ApiResponse(responseCode = "400", description = "The remote address of the request or the X-Forwarded-For "
            + "header was not a valid IP address.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Wrong email address or password, or the password did not meet "
            + "the password preconditions.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The login throttling delay was interrupted.",
            content = @Content)
    public Response createSession(@Parameter(hidden = true) @Context org.glassfish.grizzly.http.server.Request rc,
                                  @RequestBody(description = "Email address and password of the user.",
                                          required = true, content = @Content(mediaType = "application/json")) @NotNull CreateSessionRequest request) {
        String remoteIp = rc.getHeader("X-Forwarded-For") == null
                ? rc.getRemoteAddr() : rc.getHeader("X-Forwarded-For").split(",")[0];

        InetAddressValidator inetValidator = new InetAddressValidator();
        if (!inetValidator.isValid(remoteIp)) {
            LOG.warn("Invalid remote IP or X-Forwarded-For header in session request: [{}]. Aborting.", remoteIp);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        String username = request.username();
        String password = request.password();

        // Pull user this login impersonates.
        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserByEmail(request.username());

        // Verify hash.
        PasswordHasher hasher = new PasswordHasher(nzyme.getMetrics());

        if (!hasher.runPasswordPreconditions(request.password())) {
            LOG.warn("Failed login attempt for user [{}]. (Password preconditions not met.)", username);
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        String hash;
        String salt;
        if (user.isPresent()) {
            // User found.
            hash = user.get().passwordHash();
            salt = user.get().passwordSalt();
        } else {
            /*
             * No such user. Instead of returning immediately, create a new hash/salt that will not match to make
             * timing attacks harder.
             */
            PasswordHasher.GeneratedHashAndSalt generated = hasher.createHash(
                    RandomStringUtils.random(18, true, true)
            );

            hash = generated.hash();
            salt = generated.salt();
        }

        // Delay if user is throttled.
        if (user.isPresent() && user.get().isLoginThrottled()) {
            // If this is the initial throttle, create system event.
            if (user.get().failedLoginCount() == 5) {
                if (user.get().isSuperAdmin()) {
                    nzyme.getEventEngine().processEvent(SystemEvent.create(
                            SystemEventType.AUTHENTICATION_SUPERADMIN_LOGIN_THROTTLED,
                            DateTime.now(),
                            "Login attempts of super administrator [" + user.get().email() + "] were throttled."
                    ), null, null);
                } else {
                    nzyme.getEventEngine().processEvent(SystemEvent.create(
                            SystemEventType.AUTHENTICATION_USER_LOGIN_THROTTLED,
                            DateTime.now(),
                            "Login attempts of user [" + user.get().email() + "] were throttled."
                    ), user.get().organizationId(), user.get().tenantId());
                }
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }
        }

        if (hasher.compareHash(password, hash, salt)) {
            // Correct password. Create session.
            String sessionId = SessionId.createSessionId();

            nzyme.getAuthenticationService().deleteAllSessionsOfUser(user.get().uuid());
            nzyme.getAuthenticationService().createSession(sessionId, user.get().uuid(), remoteIp);

            nzyme.getAuthenticationService().markUserSuccessfulLogin(user.get());

            LOG.info("Creating session for user [{}]", username);
            return Response.status(Response.Status.CREATED).entity(SessionTokenResponse.create(sessionId)).build();
        } else {
            // Failed login for an existing user.
            user.ifPresent(u -> nzyme.getAuthenticationService().markUserFailedLogin(u));

            LOG.warn("Failed login attempt for user [{}].", username);
            return Response.status(Response.Status.FORBIDDEN).build();
        }
    }

    @GET
    @PreMFASecured
    @Path("/session")
    @Operation(operationId = "findSession", summary = "Get information about the current session",
            description = "Returns the user behind the session, their permissions, the enabled subsystems, the "
                    + "multi-factor authentication state of the session and the branding of the web interface. The "
                    + "organization and tenant parameters are optional because the web interface calls this endpoint "
                    + "before the user has selected a tenant. Pass both to also receive the active alert status. "
                    + "Available while multi-factor authentication is still pending for the session.")
    @ApiResponse(responseCode = "200", description = "Session information found.",
            content = @Content(schema = @Schema(implementation = SessionInformationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Session or user not found, or the passed organization and "
            + "tenant are not accessible by the calling user.", content = @Content)
    public Response getSessionInformation(@Parameter(hidden = true) @Context SecurityContext sc,
                                          @Parameter(description = "Organization UUID. Optional, but required together with the tenant UUID to receive the alert status.") @QueryParam("organization_id") @Nullable UUID organizationId,
                                          @Parameter(description = "Tenant UUID. Optional, but required together with the organization UUID to receive the alert status.") @QueryParam("tenant_id") @Nullable UUID tenantId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        /*
         * The selected tenant is optional because there is a phase where the user is logged in, but the tenant
         * selection has not been made yet. In that case, we let the request pass, but not use the selected tenant
         * in this logic.
         */
        if (organizationId != null && tenantId != null && !passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<SessionEntry> session = nzyme.getAuthenticationService().findSessionWithOrWithoutPassedMFABySessionId(
                authenticatedUser.getSessionId()
        );

        if (session.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserById(session.get().userId());

        if (user.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        UserEntry u = user.get();

        int mfaTimeoutMinutes;
        if (authenticatedUser.isSuperAdministrator() || authenticatedUser.isOrganizationAdministrator()) {
            mfaTimeoutMinutes = Integer.parseInt(nzyme.getDatabaseCoreRegistry()
                    .getValue(AuthenticationRegistryKeys.MFA_TIMEOUT_MINUTES.key())
                    .orElse(AuthenticationRegistryKeys.MFA_TIMEOUT_MINUTES.defaultValue().get()));
        } else {
            mfaTimeoutMinutes = nzyme.getAuthenticationService().findTenant(user.get().tenantId()).get()
                    .mfaTimeoutMinutes();
        }

        List<String> featurePermissions = nzyme.getAuthenticationService().findPermissionsOfUser(u.uuid());
        DateTime mfaExpiresAt = session.get().mfaRequestedAt() == null
                ? null : session.get().mfaRequestedAt().plusMinutes(mfaTimeoutMinutes);

        //noinspection OptionalGetWithoutIsPresent
        String sidebarTitleText = nzyme.getDatabaseCoreRegistry()
                .getValue(BrandingRegistryKeys.SIDEBAR_TITLE_TEXT.key())
                .orElse(BrandingRegistryKeys.SIDEBAR_TITLE_TEXT.defaultValue().get());
        String sidebarSubtitleText = nzyme.getDatabaseCoreRegistry()
                .getValueOrNull(BrandingRegistryKeys.SIDEBAR_SUBTITLE_TEXT.key());


        // Fetch current alert info if user has permission to see alert.
        boolean hasActiveAlerts = false;
        if (tenantId != null && organizationId != null) {
            List<String> userPermissions = nzyme.getAuthenticationService().findPermissionsOfUser(user.get().uuid());
            if (user.get().isSuperAdmin() || user.get().isOrganizationAdmin() || userPermissions.contains("alerts_view")) {
                hasActiveAlerts = nzyme.getDetectionAlertService().countActiveAlerts(
                        organizationId,
                        tenantId,
                        null
                ) > 0;
            }
        }
        
        List<String> subsystems = Lists.newArrayList();
        if (nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, user.get().organizationId(), user.get().tenantId())) {
            subsystems.add("ethernet");
        }
        if (nzyme.getSubsystems().isEnabled(Subsystem.DOT11, user.get().organizationId(), user.get().tenantId())) {
            subsystems.add("dot11");
        }
        if (nzyme.getSubsystems().isEnabled(Subsystem.BLUETOOTH, user.get().organizationId(), user.get().tenantId())) {
            subsystems.add("bluetooth");
        }
        if (nzyme.getSubsystems().isEnabled(Subsystem.UAV, user.get().organizationId(), user.get().tenantId())) {
            subsystems.add("uav");
        }

        String healthIndicatorLevel = null;
        if (user.get().isSuperAdmin()) {
            boolean hasRed = false;
            boolean hasOrange = false;

            List<IndicatorStatus> indicators = nzyme.getHealthMonitor().getIndicatorStatus();
            for (IndicatorStatus status : indicators) {
                if (status.active()) {
                    if (status.resultLevel().equals(IndicatorStatusLevel.RED)) {
                        hasRed = true;
                    }

                    if (status.resultLevel().equals(IndicatorStatusLevel.ORANGE)) {
                        hasOrange = true;
                    }
                }
            }

            if (hasRed) {
                healthIndicatorLevel = "RED";
            } else if (hasOrange) {
                healthIndicatorLevel = "ORANGE";
            } else {
                healthIndicatorLevel = "GREEN";
            }
        }

        return Response.ok(SessionInformationResponse.create(
                SessionUserInformationDetailsResponse.create(
                        u.uuid(),
                        u.email(),
                        u.name(),
                        u.isSuperAdmin(),
                        u.isOrganizationAdmin(),
                        u.organizationId(),
                        u.tenantId(),
                        featurePermissions,
                        subsystems,
                        u.hasMfaDisabled(),
                        u.unitSystem()
                ),
                session.get().mfaValid(),
                user.get().mfaComplete(),
                mfaExpiresAt,
                BrandingResponse.create(sidebarTitleText, sidebarSubtitleText),
                hasActiveAlerts,
                healthIndicatorLevel
        )).build();
    }

    @POST
    @PreMFASecured
    @Path("/session/touch")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "touchSession", summary = "Keep the current session alive",
            description = "Records user activity on the session so that it is not considered inactive. The web "
                    + "interface calls this while a user is working. Available while multi-factor authentication is "
                    + "still pending for the session.")
    @ApiResponse(responseCode = "200", description = "Session activity recorded.", content = @Content)
    public Response touchSession(@Parameter(hidden = true) @Context SecurityContext sc) {
        // The filter updates last user activity. We may add more actions here in the future.

        return Response.ok().build();
    }

    @GET
    @PreMFASecured
    @Path("/mfa/setup/initialize")
    @Operation(operationId = "initializeMfaSetup", summary = "Start multi-factor authentication setup",
            description = "Returns the TOTP secret and the recovery codes a user needs to set up multi-factor "
                    + "authentication. Both are generated and stored encrypted on the first call. A later call "
                    + "returns the existing secret and codes, so an aborted setup can be resumed. Users should store "
                    + "the recovery codes in a safe place, because they are the only way back in without the "
                    + "authenticator app. Available while multi-factor authentication is still pending for the "
                    + "session.")
    @ApiResponse(responseCode = "200", description = "Setup data created or restored.",
            content = @Content(schema = @Schema(implementation = MFAInitResponse.class)))
    @ApiResponse(responseCode = "404", description = "Session or user not found, the session already passed "
            + "multi-factor authentication, or the user already completed the setup.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The secret or the recovery codes could not be encrypted, "
            + "decrypted or serialized.", content = @Content)
    public Response initializeMfaSetup(@Parameter(hidden = true) @Context SecurityContext sc) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<SessionEntry> session = nzyme.getAuthenticationService().findSessionWithOrWithoutPassedMFABySessionId(
                authenticatedUser.getSessionId()
        );

        if (session.isEmpty() || session.get().mfaValid()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserById(session.get().userId());

        if (user.isEmpty() || user.get().mfaComplete()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String userSecret;
        Map<String, Boolean> recoveryCodes;
        if (Strings.isNullOrEmpty(user.get().totpSecret())) {
            // Store secret and recovery codes with user.
            SecretGenerator secretGenerator = new DefaultSecretGenerator();
            RecoveryCodes recoveryCodeGenerator = new RecoveryCodes();

            userSecret = secretGenerator.generate();
            recoveryCodes = Maps.newHashMap();

            for (String code : recoveryCodeGenerator.generateCodes(8)) {
                recoveryCodes.put(code, false);
            }

            String recoveryCodesJson;
            try {
                recoveryCodesJson = new ObjectMapper().writeValueAsString(recoveryCodes);
            } catch (JacksonException e) {
                LOG.error("Could not serialize MFA recovery codes.", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }

            // Encrypt.
            String encryptedUserSecret;
            String encryptedRecoveryCodesJson;
            try {
                encryptedUserSecret = BaseEncoding.base64().encode(
                        nzyme.getCrypto().encryptWithClusterKey(userSecret.getBytes())
                );
                encryptedRecoveryCodesJson = BaseEncoding.base64().encode(
                        nzyme.getCrypto().encryptWithClusterKey(recoveryCodesJson.getBytes())
                );
            } catch (Crypto.CryptoOperationException e) {
                LOG.error("Could not encrypt MFA data codes.", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }

            // Store encrypted data in database.
            nzyme.getAuthenticationService().setUserTOTPSecret(user.get().uuid(), encryptedUserSecret);
            nzyme.getAuthenticationService().setUserMFARecoveryCodes(user.get().uuid(), encryptedRecoveryCodesJson);
        } else {
            // User already has a secret (but MFA setup not complete. Aborted wizard?) Use existing secret.
            try {
                userSecret = new String(nzyme.getCrypto().decryptWithClusterKey(
                        BaseEncoding.base64().decode(user.get().totpSecret())
                ));
            } catch (Crypto.CryptoOperationException e) {
                LOG.error("Could not decrypt MFA data codes.", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }

            try {
                String recoveryCodesDecryptedJson;
                try {
                    recoveryCodesDecryptedJson = new String(nzyme.getCrypto().decryptWithClusterKey(
                            BaseEncoding.base64().decode(user.get().mfaRecoveryCodes())
                    ));
                } catch (Crypto.CryptoOperationException e) {
                    LOG.error("Could not decrypt MFA data codes.", e);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
                }

                recoveryCodes = new ObjectMapper().readValue(recoveryCodesDecryptedJson, new TypeReference<>() {});
            } catch (JacksonException e) {
                LOG.error("Could not deserialize MFA recovery codes.", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }
        }

        return Response.ok(
                MFAInitResponse.create(userSecret, user.get().email(), new ArrayList<>(recoveryCodes.keySet()))
        ).build();
    }

    @POST
    @PreMFASecured
    @Path("/mfa/setup/verify")
    @Operation(operationId = "verifyMfaSetup", summary = "Verify a code during multi-factor setup",
            description = "Checks a TOTP code against the secret created during setup. This confirms that the "
                    + "authenticator app of the user is configured correctly. It does not complete the setup and it "
                    + "does not mark the session as passed. Available while multi-factor authentication is still "
                    + "pending for the session.")
    @ApiResponse(responseCode = "200", description = "The code was correct.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The code was wrong or expired.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Session or user not found, the session already passed "
            + "multi-factor authentication, or the user already completed the setup.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The stored secret could not be decrypted.",
            content = @Content)
    public Response verifyMfaSetup(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @RequestBody(description = "The TOTP code from the authenticator app.",
                                           required = true, content = @Content(mediaType = "application/json")) MFAVerificationRequest req) {
        // THIS IS THE RESOURCE THAT VERIFIES THE INITIAL MFA SETUP, NOT THE LOGIN FLOW.
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<SessionEntry> session = nzyme.getAuthenticationService().findSessionWithOrWithoutPassedMFABySessionId(
                authenticatedUser.getSessionId()
        );

        if (session.isEmpty() || session.get().mfaValid()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserById(session.get().userId());

        if (user.isEmpty() || user.get().mfaComplete()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Compare codes.
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

        String userSecret;
        try {
            userSecret = new String(nzyme.getCrypto().decryptWithClusterKey(
                    BaseEncoding.base64().decode(user.get().totpSecret())
            ));
        } catch (Crypto.CryptoOperationException e) {
            LOG.error("Could not decrypt MFA data codes for initial verification.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        if (!verifier.isValidCode(userSecret, req.code())) {
            LOG.info("User <{}> failed MFA challenge for initial verification.", user.get().email());
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        // We have a valid TOTP.
        LOG.info("User <{}> passed MFA challenge for initial verification.", user.get().email());

        return Response.ok().build();
    }

    @POST
    @PreMFASecured
    @Path("/mfa/setup/complete")
    @Operation(operationId = "completeMfaSetup", summary = "Complete multi-factor authentication setup",
            description = "Marks multi-factor authentication as fully set up for the user. From now on, every login "
                    + "requires a TOTP code or a recovery code. Available while multi-factor authentication is still "
                    + "pending for the session.")
    @ApiResponse(responseCode = "200", description = "Setup completed.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Session or user not found, the session already passed "
            + "multi-factor authentication, or the user already completed the setup.", content = @Content)
    public Response completeMfaSetup(@Parameter(hidden = true) @Context SecurityContext sc) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<SessionEntry> session = nzyme.getAuthenticationService().findSessionWithOrWithoutPassedMFABySessionId(
                authenticatedUser.getSessionId()
        );

        if (session.isEmpty() || session.get().mfaValid()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserById(session.get().userId());

        if (user.isEmpty() || user.get().mfaComplete()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAuthenticationService().setUserMFAComplete(user.get().uuid(), true);

        return Response.ok().build();
    }


    @POST
    @PreMFASecured
    @Path("/mfa/verify")
    @Operation(operationId = "verifyMfa", summary = "Pass the multi-factor authentication challenge",
            description = "Checks a TOTP code and marks the session as having passed multi-factor authentication. "
                    + "This is the second step of the login flow. TOTP codes rely on local time, so the clocks of "
                    + "the Nzyme node and the authenticator app have to be accurate. Available while multi-factor "
                    + "authentication is still pending for the session.")
    @ApiResponse(responseCode = "200", description = "The code was correct and the session is now fully "
            + "authenticated.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The code was wrong or expired.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Session or user not found, the session already passed "
            + "multi-factor authentication, or the user has not completed the setup yet.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The stored secret could not be decrypted.",
            content = @Content)
    public Response verifyMfa(@Parameter(hidden = true) @Context SecurityContext sc,
                              @RequestBody(description = "The TOTP code from the authenticator app.",
                                      required = true, content = @Content(mediaType = "application/json")) MFAVerificationRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<SessionEntry> session = nzyme.getAuthenticationService().findSessionWithOrWithoutPassedMFABySessionId(
                authenticatedUser.getSessionId()
        );

        if (session.isEmpty() || session.get().mfaValid()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserById(session.get().userId());

        if (user.isEmpty() || !user.get().mfaComplete()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Compare codes.
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

        String userSecret;
        try {
            userSecret = new String(nzyme.getCrypto().decryptWithClusterKey(
                    BaseEncoding.base64().decode(user.get().totpSecret())
            ));
        } catch (Crypto.CryptoOperationException e) {
            LOG.error("Could not decrypt MFA data codes.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        if (!verifier.isValidCode(userSecret, req.code())) {
            LOG.info("User <{}> failed MFA challenge.", user.get().email());
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        // We have a valid TOTP. Mark session as MFA'd.
        LOG.info("User <{}> passed MFA challenge.", user.get().email());
        nzyme.getAuthenticationService().markSessionAsMFAValid(session.get().sessionId());

        return Response.ok().build();
    }

    @POST
    @PreMFASecured
    @Path("/mfa/recovery")
    @Operation(operationId = "useMfaRecoveryCode", summary = "Pass multi-factor authentication with a recovery code",
            description = "Uses one of the recovery codes of a user to pass the multi-factor authentication "
                    + "challenge. Each code works only once and is marked as used. Nzyme records a system event for "
                    + "every used code and for every attempt to reuse one. Available while multi-factor "
                    + "authentication is still pending for the session.")
    @ApiResponse(responseCode = "200", description = "The recovery code was valid and the session is now fully "
            + "authenticated.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The recovery code is unknown or has been used before.",
            content = @Content)
    @ApiResponse(responseCode = "404", description = "Session or user not found, the session already passed "
            + "multi-factor authentication, the user has not completed the setup yet, or the user has no recovery "
            + "codes.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The recovery codes could not be serialized or encrypted.",
            content = @Content)
    public Response mfaRecoveryCodeValidation(@Parameter(hidden = true) @Context SecurityContext sc,
                                              @RequestBody(description = "One of the recovery codes of the user.",
                                                      required = true, content = @Content(mediaType = "application/json")) MFARecoveryCodeRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<SessionEntry> session = nzyme.getAuthenticationService().findSessionWithOrWithoutPassedMFABySessionId(
                authenticatedUser.getSessionId()
        );

        if (session.isEmpty() || session.get().mfaValid()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserById(session.get().userId());

        if (user.isEmpty() || !user.get().mfaComplete()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Map<String, Boolean>> codes = nzyme.getAuthenticationService()
                .getUserMFARecoveryCodes(user.get().uuid());

        if (codes.isEmpty()) {
            LOG.warn("No MFA recovery codes found for user [{}].", user.get().email());
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<String> unusedCodes = Lists.newArrayList();
        List<String> usedCodes = Lists.newArrayList();
        for (Map.Entry<String, Boolean> code : codes.get().entrySet()) {
            if (!code.getValue()) {
                unusedCodes.add(code.getKey());
            } else {
                usedCodes.add(code.getKey());
            }
        }

        // Check if the code is valid.
        if (!unusedCodes.contains(req.code())) {
            if (usedCodes.contains(req.code())) {
                // This recovery code has been used before. Alert!
                LOG.warn("User [{}] attempted to use previously used MFA recovery code.", user.get().email());

                // System event.
                if (user.get().isSuperAdmin()) {
                    nzyme.getEventEngine().processEvent(SystemEvent.create(
                            SystemEventType.AUTHENTICATION_SUPERADMIN_MFA_RECOVERY_CODE_REUSED,
                            DateTime.now(),
                            "Super administrator [" + user.get().email() + "] attempted to reuse one of their " +
                                    "previously utilized MFA recovery codes for login, which was unsuccessful."
                    ), null, null);
                } else {
                    nzyme.getEventEngine().processEvent(SystemEvent.create(
                            SystemEventType.AUTHENTICATION_MFA_RECOVERY_CODE_REUSED,
                            DateTime.now(),
                            "User [" + user.get().email() + "] attempted to reuse one of their previously utilized " +
                                    "MFA recovery codes for login, which was unsuccessful."
                    ), user.get().organizationId(), user.get().tenantId());
                }

                return Response.status(Response.Status.UNAUTHORIZED).build();
            } else {
                LOG.warn("User [{}] attempted to use invalid MFA recovery code.", user.get().email());
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
        }

        // Write remaining codes back to DB.
        Map<String, Boolean> newCodes = Maps.newHashMap();
        for (Map.Entry<String, Boolean> code : codes.get().entrySet()) {
            newCodes.put(code.getKey(), code.getKey().equals(req.code()) || code.getValue());
        }

        String recoveryCodesJson;
        try {
            recoveryCodesJson = new ObjectMapper().writeValueAsString(newCodes);
        } catch (JacksonException e) {
            LOG.error("Could not serialize MFA recovery codes.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        String encryptedRecoveryCodesJson;
        try {
            encryptedRecoveryCodesJson = BaseEncoding.base64().encode(
                    nzyme.getCrypto().encryptWithClusterKey(recoveryCodesJson.getBytes())
            );
        } catch (Crypto.CryptoOperationException e) {
            LOG.error("Could not encrypt MFA data codes.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        nzyme.getAuthenticationService().setUserMFARecoveryCodes(user.get().uuid(), encryptedRecoveryCodesJson);

        LOG.info("User [{}] passed MFA challenge with recovery code.", user.get().email());
        nzyme.getAuthenticationService().markSessionAsMFAValid(session.get().sessionId());

        // System event.
        if (user.get().isSuperAdmin()) {
            nzyme.getEventEngine().processEvent(SystemEvent.create(
                    SystemEventType.AUTHENTICATION_SUPERADMIN_MFA_RECOVERY_CODE_USED,
                    DateTime.now(),
                    "Super administrator [" + user.get().email() + "] used a MFA recovery code to log in."
            ), null, null);
        } else {
            nzyme.getEventEngine().processEvent(SystemEvent.create(
                    SystemEventType.AUTHENTICATION_MFA_RECOVERY_CODE_USED,
                    DateTime.now(),
                    "User [" + user.get().email() + "] used a MFA recovery code to log in."
            ), user.get().organizationId(), user.get().tenantId());
        }

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(PermissionLevel.ANY)
    @SessionOnly
    @Path("/session")
    @Operation(operationId = "deleteSession", summary = "Log out of Nzyme",
            description = "Deletes all sessions of the calling user, which logs them out everywhere. Available to "
                    + "any user. Requires an interactive session; API keys are rejected.")
    @ApiResponse(responseCode = "200", description = "Sessions deleted.", content = @Content)
    public Response deleteSessionOfOwnUser(@Parameter(hidden = true) @Context SecurityContext sc) {
        AuthenticatedUser user = getAuthenticatedUser(sc);
        nzyme.getAuthenticationService().deleteAllSessionsOfUser(user.getUserId());

        LOG.info("Deleting session of user [{}].", user.getEmail());

        return Response.ok().build();
    }

}
