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
public class AuthenticationResource extends UserAuthenticatedResource {

    private static final Logger LOG = LogManager.getLogger(AuthenticationResource.class);

    @Inject
    private NzymeNode nzyme;

    @POST
    @Path("/session")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createSession(@Context org.glassfish.grizzly.http.server.Request rc,
                                  @NotNull CreateSessionRequest request) {
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
    public Response getSessionInformation(@Context SecurityContext sc,
                                          @QueryParam("organization_id") @Nullable UUID organizationId,
                                          @QueryParam("tenant_id") @Nullable UUID tenantId) {
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
    public Response touchSession(@Context SecurityContext sc) {
        // The filter updates last user activity. We may add more actions here in the future.

        return Response.ok().build();
    }

    @GET
    @PreMFASecured
    @Path("/mfa/setup/initialize")
    public Response initializeMfaSetup(@Context SecurityContext sc) {
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
    public Response verifyMfaSetup(@Context SecurityContext sc, MFAVerificationRequest req) {
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
    public Response completeMfaSetup(@Context SecurityContext sc) {
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
    public Response verifyMfa(@Context SecurityContext sc, MFAVerificationRequest req) {
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
    public Response mfaRecoveryCodeValidation(@Context SecurityContext sc, MFARecoveryCodeRequest req) {
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
    @Path("/session")
    public Response deleteSessionOfOwnUser(@Context SecurityContext sc) {
        AuthenticatedUser user = getAuthenticatedUser(sc);
        nzyme.getAuthenticationService().deleteAllSessionsOfUser(user.getUserId());

        LOG.info("Deleting session of user [{}].", user.getEmail());

        return Response.ok().build();
    }

}
