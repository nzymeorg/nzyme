package app.nzyme.core.rest.resources.system.authentication.mgmt;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.crypto.Crypto;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.events.EventEngineImpl;
import app.nzyme.core.events.actions.EventActionUtilities;
import app.nzyme.core.events.db.EventActionEntry;
import app.nzyme.core.events.types.SystemEvent;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.floorplans.db.TenantLocationEntry;
import app.nzyme.core.floorplans.db.TenantLocationFloorEntry;
import app.nzyme.core.quota.QuotaType;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.authentication.SessionOnly;
import app.nzyme.core.rest.requests.*;
import app.nzyme.core.rest.responses.authentication.SessionDetailsResponse;
import app.nzyme.core.rest.responses.authentication.SessionsListResponse;
import app.nzyme.core.rest.responses.authentication.apikeys.ApiKeyDetailsResponse;
import app.nzyme.core.rest.responses.authentication.mgmt.*;
import app.nzyme.core.rest.responses.events.EventActionDetailsResponse;
import app.nzyme.core.rest.responses.events.EventActionsListResponse;
import app.nzyme.core.rest.responses.floorplans.*;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
import app.nzyme.core.rest.responses.subsystems.SubsystemsConfigurationResponse;
import app.nzyme.core.security.authentication.AuthenticationRegistryKeys;
import app.nzyme.core.security.authentication.PasswordHasher;
import app.nzyme.core.security.authentication.db.*;
import app.nzyme.core.security.authentication.roles.Permission;
import app.nzyme.core.security.authentication.roles.Permissions;
import app.nzyme.core.security.sessions.db.SessionEntry;
import app.nzyme.core.security.sessions.db.SessionEntryWithUserDetails;
import app.nzyme.core.subsystems.SubsystemRegistryKeys;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.Subsystem;
import app.nzyme.plugin.distributed.messaging.ClusterMessage;
import app.nzyme.plugin.distributed.messaging.MessageType;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraintValidator;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import jakarta.validation.Valid;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Base64;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.joda.time.DateTime;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

@Path("/api/system/authentication/mgmt/organizations")
@Produces(MediaType.APPLICATION_JSON)
public class OrganizationsResource extends UserAuthenticatedResource {

    private static final Logger LOG = LogManager.getLogger(OrganizationsResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    public Response findAll(@QueryParam("limit") int limit, @QueryParam("offset") int offset) {
        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<OrganizationDetailsResponse> organizations = Lists.newArrayList();

        for (OrganizationEntry org : nzyme.getAuthenticationService().findAllOrganizations(limit, offset)) {
            organizations.add(organizationEntryToResponse(org));
        }

        long organizationCount = nzyme.getAuthenticationService().countAllOrganizations();

        return Response.ok(OrganizationsListResponse.create(organizationCount, organizations)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{id}")
    public Response find(@Context SecurityContext sc, @PathParam("id") UUID id) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<OrganizationEntry> org = nzyme.getAuthenticationService().findOrganization(id);

        if (org.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(org.get().uuid())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(organizationEntryToResponse(org.get())).build();
    }

    @POST
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    public Response create(@Valid CreateOrganizationRequest req) {
        nzyme.getAuthenticationService().createOrganization(
                req.name(),
                req.description()
        );

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/show/{id}")
    public Response update(@PathParam("id") UUID id,
                           @Valid UpdateOrganizationRequest req) {
        Optional<OrganizationEntry> org = nzyme.getAuthenticationService().findOrganization(id);

        if (org.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAuthenticationService().updateOrganization(
                id, req.name(), req.description()
        );

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/show/{id}")
    public Response delete(@PathParam("id") UUID id) {
        Optional<OrganizationEntry> org = nzyme.getAuthenticationService().findOrganization(id);

        if (org.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getAuthenticationService().isOrganizationDeletable(org.get())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        nzyme.getAuthenticationService().deleteOrganization(id);

        return Response.ok().build();
    }

    @GET
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/show/{id}/subsystems/configuration")
    public Response getOrganizationSubsystemConfiguration(@PathParam("id") UUID id) {
        Optional<OrganizationEntry> org = nzyme.getAuthenticationService().findOrganization(id);

        if (org.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if subsystems are enabled system-wide.
        boolean ethernetAvailable = nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, null, null);
        boolean dot11Available = nzyme.getSubsystems().isEnabled(Subsystem.DOT11, null, null);
        boolean bluetoothAvailable = nzyme.getSubsystems().isEnabled(Subsystem.BLUETOOTH, null, null);
        boolean uavAvailable = nzyme.getSubsystems().isEnabled(Subsystem.UAV, null, null);

        SubsystemsConfigurationResponse response = SubsystemsConfigurationResponse.create(
                ethernetAvailable,
                dot11Available,
                bluetoothAvailable,
                uavAvailable,
                ConfigurationEntryResponse.create(
                        SubsystemRegistryKeys.ETHERNET_ENABLED.key(),
                        "Ethernet is enabled",
                        nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, org.get().uuid(), null),
                        ConfigurationEntryValueType.BOOLEAN,
                        SubsystemRegistryKeys.ETHERNET_ENABLED.defaultValue().orElse(null),
                        SubsystemRegistryKeys.ETHERNET_ENABLED.requiresRestart(),
                        SubsystemRegistryKeys.ETHERNET_ENABLED.constraints().orElse(Collections.emptyList()),
                        "subsystems"
                ),
                ConfigurationEntryResponse.create(
                        SubsystemRegistryKeys.DOT11_ENABLED.key(),
                        "WiFi/802.11 is enabled",
                        nzyme.getSubsystems().isEnabled(Subsystem.DOT11,  org.get().uuid(), null),
                        ConfigurationEntryValueType.BOOLEAN,
                        SubsystemRegistryKeys.DOT11_ENABLED.defaultValue().orElse(null),
                        SubsystemRegistryKeys.DOT11_ENABLED.requiresRestart(),
                        SubsystemRegistryKeys.DOT11_ENABLED.constraints().orElse(Collections.emptyList()),
                        "subsystems"
                ),
                ConfigurationEntryResponse.create(
                        SubsystemRegistryKeys.BLUETOOTH_ENABLED.key(),
                        "Bluetooth is enabled",
                        nzyme.getSubsystems().isEnabled(Subsystem.BLUETOOTH,  org.get().uuid(), null),
                        ConfigurationEntryValueType.BOOLEAN,
                        SubsystemRegistryKeys.BLUETOOTH_ENABLED.defaultValue().orElse(null),
                        SubsystemRegistryKeys.BLUETOOTH_ENABLED.requiresRestart(),
                        SubsystemRegistryKeys.BLUETOOTH_ENABLED.constraints().orElse(Collections.emptyList()),
                        "subsystems"
                ),
                ConfigurationEntryResponse.create(
                        SubsystemRegistryKeys.UAV_ENABLED.key(),
                        "UAV is enabled",
                        nzyme.getSubsystems().isEnabled(Subsystem.UAV,  org.get().uuid(), null),
                        ConfigurationEntryValueType.BOOLEAN,
                        SubsystemRegistryKeys.UAV_ENABLED.defaultValue().orElse(null),
                        SubsystemRegistryKeys.UAV_ENABLED.requiresRestart(),
                        SubsystemRegistryKeys.UAV_ENABLED.constraints().orElse(Collections.emptyList()),
                        "subsystems"
                )
        );

        return Response.ok(response).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/show/{id}/subsystems/configuration")
    public Response updateOrganizationSubsystemConfiguration(@PathParam("id") UUID id, UpdateConfigurationRequest req) {
        if (req.change().isEmpty()) {
            LOG.info("Empty configuration parameters.");
            return Response.status(422).build();
        }

        Optional<OrganizationEntry> org = nzyme.getAuthenticationService().findOrganization(id);

        if (org.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        for (Map.Entry<String, Object> c : req.change().entrySet()) {
            switch (c.getKey()) {
                case "subsystem_ethernet_enabled":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(SubsystemRegistryKeys.ETHERNET_ENABLED, c)) {
                        return Response.status(422).build();
                    }

                    if (!nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, null, null)) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                    }

                    break;
                case "subsystem_dot11_enabled":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(SubsystemRegistryKeys.DOT11_ENABLED, c)) {
                        return Response.status(422).build();
                    }

                    if (!nzyme.getSubsystems().isEnabled(Subsystem.DOT11, null, null)) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                    }

                    break;
                case "subsystem_bluetooth_enabled":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(SubsystemRegistryKeys.BLUETOOTH_ENABLED, c)) {
                        return Response.status(422).build();
                    }

                    if (!nzyme.getSubsystems().isEnabled(Subsystem.BLUETOOTH, null, null)) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                    }

                    break;
                case "subsystem_uav_enabled":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(SubsystemRegistryKeys.UAV_ENABLED, c)) {
                        return Response.status(422).build();
                    }

                    if (!nzyme.getSubsystems().isEnabled(Subsystem.UAV, null, null)) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                    }

                    break;
            }

            nzyme.getDatabaseCoreRegistry().setValue(c.getKey(), c.getValue().toString(), org.get().uuid());
        }

        return Response.ok().build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{id}/quotas")
    public Response getOrganizationQuotas(@Context SecurityContext sc, @PathParam("id") UUID id) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<OrganizationEntry> org = nzyme.getAuthenticationService().findOrganization(id);

        if (org.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(org.get().uuid())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<QuotaDetailsResponse> quotas = new ArrayList<>();
        for (Map.Entry<QuotaType, Optional<Integer>> q : nzyme.getQuotaService()
                .getAllOrganizationQuotas(org.get().uuid()).entrySet()) {

            int quotaUse = nzyme.getQuotaService().calculateOrganizationQuotaUse(org.get().uuid(), q.getKey());

            quotas.add(QuotaDetailsResponse.create(
                    q.getKey().name(),
                    q.getKey().getHumanReadable(),
                    q.getValue().orElse(null),
                    quotaUse
            ));
        }

        return Response.ok(quotas).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/show/{id}/quotas/show/{quota_type}")
    public Response setOrganizationQuota(@PathParam("id") UUID id,
                                         @PathParam("quota_type") String quotaTypeParam,
                                         @Valid ConfigureQuotaRequest req) {
        Optional<OrganizationEntry> org = nzyme.getAuthenticationService().findOrganization(id);

        if (org.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        QuotaType quotaType;
        try {
            quotaType = QuotaType.valueOf(quotaTypeParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (req.quota() == null) {
            // Reset quota.
            nzyme.getQuotaService().eraseOrganizationQuota(id, quotaType);
        } else {
            // Set quota.
            nzyme.getQuotaService().setOrganizationQuota(org.get().uuid(), quotaType, req.quota());
        }

        return Response.ok().build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants")
    public Response findTenantsOfOrganization(@Context SecurityContext sc,
                                              @PathParam("organizationId") UUID organizationId,
                                              @QueryParam("limit") int limit,
                                              @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<OrganizationEntry> org = nzyme.getAuthenticationService().findOrganization(organizationId);

        if (org.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(org.get().uuid())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<TenantDetailsResponse> response = Lists.newArrayList();
        for (TenantEntry tenant : nzyme.getAuthenticationService()
                .findAllTenantsOfOrganization(org.get().uuid(), limit, offset)) {
            response.add(tenantEntryToResponse(tenant));
        }

        long tenantsCount = nzyme.getAuthenticationService().countTenantsOfOrganization(org.get());

        return Response.ok(TenantsListResponse.create(tenantsCount, response)).build();
    }


    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/administrators")
    public Response findAllOrganizationAdministrators(@Context SecurityContext sc,
                                                      @PathParam("organizationId") UUID organizationId,
                                                      @QueryParam("limit") int limit,
                                                      @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<OrganizationEntry> org = nzyme.getAuthenticationService().findOrganization(organizationId);

        if (org.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(org.get().uuid())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<UserDetailsResponse> users = Lists.newArrayList();
        for (UserEntry user : nzyme.getAuthenticationService().findAllOrganizationAdministrators(
                org.get().uuid(), limit, offset)) {
            users.add(userEntryToResponse(user, Collections.emptyList(), Collections.emptyList()));
        }

        long orgAdminCount = nzyme.getAuthenticationService().countOrganizationAdministrators(org.get().uuid());

        return Response.ok(UsersListResponse.create(orgAdminCount, users)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/administrators/show/{id}")
    public Response findOrganizationAdministrator(@Context SecurityContext sc,
                                                  @PathParam("organizationId") UUID organizationId,
                                                  @PathParam("id") UUID userId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        Optional<UserEntry> orgAdmin = nzyme.getAuthenticationService().findOrganizationAdministrator(
                organizationId, userId);

        if (orgAdmin.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        boolean isDeletable = !authenticatedUser.getUserId().equals(userId);

        // Find API keys of this user.
        List<ApiKeyDetailsResponse> apiKeys = Lists.newArrayList();
        for (ApiKeyEntry key : nzyme.getAuthenticationService().findAllApiKeys(orgAdmin.get().uuid())) {
            apiKeys.add(ApiKeyDetailsResponse.create(
                    key.uuid(),
                    key.userId(),
                    key.name(),
                    key.lastActivity(),
                    key.expiresAt(),
                    key.createdAt()
            ));
        }

        return Response.ok(OrganizationAdministratorDetailsResponse.create(
                userEntryToResponse(orgAdmin.get(), Collections.emptyList(), Collections.emptyList()), isDeletable, apiKeys
        )).build();
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/administrators")
    @SessionOnly
    public Response createOrganizationAdministrator(@Context SecurityContext sc,
                                                    @PathParam("organizationId") UUID organizationId,
                                                    @Valid CreateUserRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!validateCreateUserRequest(req)) {
            LOG.info("Invalid parameters in create user request.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (nzyme.getAuthenticationService().userWithEmailExists(req.email().toLowerCase())) {
            LOG.info("User with email address already exists.");
            return Response.status(Response.Status.UNAUTHORIZED).entity(
                    ErrorResponse.create("Email address already in use.")
            ).build();
        }

        PasswordHasher hasher = new PasswordHasher(nzyme.getMetrics());
        PasswordHasher.GeneratedHashAndSalt hash = hasher.createHash(req.password());

        nzyme.getAuthenticationService().createOrganizationAdministrator(
                organizationId,
                req.name(),
                req.email().toLowerCase(),
                req.disableMfa(),
                hash
        );

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/administrators/show/{id}")
    public Response editOrganizationAdministrator(@Context SecurityContext sc,
                                                  @PathParam("organizationId") UUID organizationId,
                                                  @PathParam("id") UUID userId,
                                                  @Valid UpdateUserRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<UserEntry> orgAdmin = nzyme.getAuthenticationService().findOrganizationAdministrator(
                organizationId, userId);

        if (orgAdmin.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!validateUpdateUserRequest(req)) {
            LOG.info("Invalid parameters in update user request.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (!orgAdmin.get().email().equals(req.email()) && nzyme.getAuthenticationService().userWithEmailExists(
                req.email().toLowerCase())) {
            LOG.info("User with email address already exists.");
            return Response.status(Response.Status.UNAUTHORIZED).entity(
                    ErrorResponse.create("Email address already in use.")
            ).build();
        }

        if (!orgAdmin.get().hasMfaDisabled() && req.disableMfa()) {
            // System event.
            nzyme.getEventEngine().processEvent(SystemEvent.create(
                    SystemEventType.AUTHENTICATION_MFA_DISABLED,
                    DateTime.now(),
                    "MFA of organization administrator [" + orgAdmin.get().email() + "] was disabled."
            ), organizationId, null);
        }

        nzyme.getAuthenticationService().editUser(
                userId,
                req.name(),
                req.email().toLowerCase(),
                req.disableMfa()
        );

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/administrators/show/{id}/password")
    @SessionOnly
    public Response editOrganizationAdministratorPassword(@Context SecurityContext sc,
                                                          @PathParam("organizationId") UUID organizationId,
                                                          @PathParam("id") UUID userId,
                                                          @Valid UpdatePasswordRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<UserEntry> orgAdmin = nzyme.getAuthenticationService().findOrganizationAdministrator(
                organizationId, userId);

        if (orgAdmin.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!validateUpdatePasswordRequest(req)) {
            LOG.info("Invalid password in update password request.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        PasswordHasher hasher = new PasswordHasher(nzyme.getMetrics());
        PasswordHasher.GeneratedHashAndSalt hash = hasher.createHash(req.password());

        nzyme.getAuthenticationService().editUserPassword(
                userId,
                hash
        );

        // Invalidate session of user.
        nzyme.getAuthenticationService().deleteAllSessionsOfUser(userId);

        // System event.
        nzyme.getEventEngine().processEvent(SystemEvent.create(
                SystemEventType.AUTHENTICATION_PASSWORD_CHANGED,
                DateTime.now(),
                "Password of organization administrator [" + orgAdmin.get().email() + "] was changed by administrator."
        ), organizationId, null);

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/administrators/show/{id}")
    public Response deleteOrganizationAdministrator(@Context SecurityContext sc,
                                                    @PathParam("organizationId") UUID organizationId,
                                                    @PathParam("id") UUID userId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (authenticatedUser.getUserId().equals(userId)) {
            LOG.warn("Organization administrators cannot delete themselves.");
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<UserEntry> orgAdmin = nzyme.getAuthenticationService().findOrganizationAdministrator(
                organizationId, userId);

        if (orgAdmin.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAuthenticationService().deleteOrganizationAdministrator(organizationId, userId);

        return Response.ok().build();
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/administrators/show/{id}/mfa/reset")
    @SessionOnly
    public Response resetOrganizationAdministratorMFA(@Context SecurityContext sc,
                                                      @PathParam("organizationId") UUID organizationId,
                                                      @PathParam("id") UUID userId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<UserEntry> orgAdmin = nzyme.getAuthenticationService().findOrganizationAdministrator(
                organizationId, userId);

        if (orgAdmin.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAuthenticationService().resetMFAOfUser(userId);

        LOG.info("Reset MFA credentials of organization administrator [{}] on admin request.",
                orgAdmin.get().email());

        // System event.
        nzyme.getEventEngine().processEvent(SystemEvent.create(
                SystemEventType.AUTHENTICATION_MFA_RESET,
                DateTime.now(),
                "MFA method of organization administrator [" + orgAdmin.get().email() + "] was reset by administrator."
        ), organizationId, null);

        return Response.ok().build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}")
    public Response findTenantOfOrganization(@Context SecurityContext sc,
                                             @PathParam("organizationId") UUID organizationId,
                                             @PathParam("tenantId") UUID tenantId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TenantEntry> tenant = nzyme.getAuthenticationService().findTenant(tenantId);

        if (tenant.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(tenantEntryToResponse(tenant.get())).build();
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/")
    public Response createTenant(@Context SecurityContext sc,
                                 @PathParam("organizationId") UUID organizationId,
                                 @Valid CreateTenantRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if there is room in the quota.
        if (!nzyme.getQuotaService().isOrganizationQuotaAvailable(organizationId, QuotaType.TENANTS)) {
            return Response.status(422).build();
        }

        nzyme.getAuthenticationService().createTenant(
                organizationId,
                req.name(),
                req.description(),
                req.sessionTimeoutMinutes(),
                req.sessionInactivityTimeoutMinutes(),
                req.mfaTimeoutMinutes()
        );

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}")
    public Response updateTenant(@Context SecurityContext sc,
                                 @PathParam("organizationId") UUID organizationId,
                                 @PathParam("tenantId") UUID tenantId,
                                 @Valid UpdateTenantRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAuthenticationService().updateTenant(
                tenantId,
                req.name(),
                req.description(),
                req.sessionTimeoutMinutes(),
                req.sessionInactivityTimeoutMinutes(),
                req.mfaTimeoutMinutes()
        );

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}")
    public Response deleteTenant(@Context SecurityContext sc,
                                 @PathParam("organizationId") UUID organizationId,
                                 @PathParam("tenantId") UUID tenantId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAuthenticationService().deleteTenant(tenantId);

        return Response.ok().build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/quotas")
    public Response getTenantQuotas(@Context SecurityContext sc,
                                    @PathParam("organizationId") UUID organizationId,
                                    @PathParam("tenantId") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<QuotaDetailsResponse> quotas = new ArrayList<>();
        for (Map.Entry<QuotaType, Optional<Integer>> q : nzyme.getQuotaService()
                .getAllTenantQuotas(organizationId, tenantId).entrySet()) {

            if (q.getKey().equals(QuotaType.TENANTS)) {
                // A tenant has no tenant quota.
                continue;
            }

            int quotaUse = nzyme.getQuotaService().calculateTenantQuotaUse(organizationId, tenantId, q.getKey());

            quotas.add(QuotaDetailsResponse.create(
                    q.getKey().name(),
                    q.getKey().getHumanReadable(),
                    q.getValue().orElse(null),
                    quotaUse
            ));
        }

        return Response.ok(quotas).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/quotas/show/{quota_type}")
    public Response setTenantQuota(@Context SecurityContext sc,
                                   @PathParam("organizationId") UUID organizationId,
                                   @PathParam("tenantId") UUID tenantId,
                                   @PathParam("quota_type") String quotaTypeParam,
                                   @Valid ConfigureQuotaRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        QuotaType quotaType;
        try {
            quotaType = QuotaType.valueOf(quotaTypeParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        // Get organization quota settings to decide if there is room left.
        Integer organizationQuota = nzyme.getQuotaService()
                .getOrganizationQuota(organizationId, quotaType)
                .orElse(null);

        if (organizationQuota != null && req.quota() != null) {
            // Add up all tenant quotas of this type if organization quota is not unlimited.
            int orgWideQuotaUse = nzyme.getAuthenticationService()
                    .findAllTenantsOfOrganization(organizationId)
                    .stream()
                    .filter(t -> !t.uuid().equals(tenantId)) // Skip our own tenant.
                    .mapToInt(t -> nzyme.getQuotaService()
                            .getTenantQuota(organizationId, t.uuid(), quotaType)
                            .orElse(0))
                    .sum();

            if (orgWideQuotaUse+req.quota() > organizationQuota) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(ErrorResponse.create("Cannot set quota because it would exceed the " +
                                "organization quota."))
                        .build();
            }
        }

        if (req.quota() == null) {
            nzyme.getQuotaService().eraseTenantQuota(organizationId, tenantId, quotaType);
        } else {
            nzyme.getQuotaService().setTenantQuota(organizationId, tenantId, quotaType, req.quota());
        }

        return Response.ok().build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/subsystems/configuration")
    public Response getTenantSubsystemConfiguration(@Context SecurityContext sc,
                                                    @PathParam("organizationId") UUID organizationId,
                                                    @PathParam("tenantId") UUID tenantId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if subsystems are enabled for organization.
        boolean ethernetAvailable = nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, organizationId, null);
        boolean dot11Available = nzyme.getSubsystems().isEnabled(Subsystem.DOT11, organizationId, null);
        boolean bluetoothAvailable = nzyme.getSubsystems().isEnabled(Subsystem.BLUETOOTH, organizationId, null);
        boolean uavAvailable = nzyme.getSubsystems().isEnabled(Subsystem.UAV, organizationId, null);

        SubsystemsConfigurationResponse response = SubsystemsConfigurationResponse.create(
                ethernetAvailable,
                dot11Available,
                bluetoothAvailable,
                uavAvailable,
                ConfigurationEntryResponse.create(
                        SubsystemRegistryKeys.ETHERNET_ENABLED.key(),
                        "Ethernet is enabled",
                        nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, organizationId, tenantId),
                        ConfigurationEntryValueType.BOOLEAN,
                        SubsystemRegistryKeys.ETHERNET_ENABLED.defaultValue().orElse(null),
                        SubsystemRegistryKeys.ETHERNET_ENABLED.requiresRestart(),
                        SubsystemRegistryKeys.ETHERNET_ENABLED.constraints().orElse(Collections.emptyList()),
                        "subsystems"
                ),
                ConfigurationEntryResponse.create(
                        SubsystemRegistryKeys.DOT11_ENABLED.key(),
                        "WiFi/802.11 is enabled",
                        nzyme.getSubsystems().isEnabled(Subsystem.DOT11,  organizationId, tenantId),
                        ConfigurationEntryValueType.BOOLEAN,
                        SubsystemRegistryKeys.DOT11_ENABLED.defaultValue().orElse(null),
                        SubsystemRegistryKeys.DOT11_ENABLED.requiresRestart(),
                        SubsystemRegistryKeys.DOT11_ENABLED.constraints().orElse(Collections.emptyList()),
                        "subsystems"
                ),
                ConfigurationEntryResponse.create(
                        SubsystemRegistryKeys.BLUETOOTH_ENABLED.key(),
                        "Bluetooth is enabled",
                        nzyme.getSubsystems().isEnabled(Subsystem.BLUETOOTH,  organizationId, tenantId),
                        ConfigurationEntryValueType.BOOLEAN,
                        SubsystemRegistryKeys.BLUETOOTH_ENABLED.defaultValue().orElse(null),
                        SubsystemRegistryKeys.BLUETOOTH_ENABLED.requiresRestart(),
                        SubsystemRegistryKeys.BLUETOOTH_ENABLED.constraints().orElse(Collections.emptyList()),
                        "subsystems"
                ),
                ConfigurationEntryResponse.create(
                        SubsystemRegistryKeys.UAV_ENABLED.key(),
                        "UAV is enabled",
                        nzyme.getSubsystems().isEnabled(Subsystem.UAV,  organizationId, tenantId),
                        ConfigurationEntryValueType.BOOLEAN,
                        SubsystemRegistryKeys.UAV_ENABLED.defaultValue().orElse(null),
                        SubsystemRegistryKeys.UAV_ENABLED.requiresRestart(),
                        SubsystemRegistryKeys.UAV_ENABLED.constraints().orElse(Collections.emptyList()),
                        "subsystems"
                )
        );

        return Response.ok(response).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/subsystems/configuration")
    public Response updateTenantSubsystemConfiguration(@Context SecurityContext sc,
                                                       @PathParam("organizationId") UUID organizationId,
                                                       @PathParam("tenantId") UUID tenantId,
                                                       UpdateConfigurationRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (req.change().isEmpty()) {
            LOG.info("Empty configuration parameters.");
            return Response.status(422).build();
        }

        for (Map.Entry<String, Object> c : req.change().entrySet()) {
            switch (c.getKey()) {
                case "subsystem_ethernet_enabled":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(SubsystemRegistryKeys.ETHERNET_ENABLED, c)) {
                        return Response.status(422).build();
                    }

                    if (!nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, organizationId, null)) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                    }

                    break;
                case "subsystem_dot11_enabled":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(SubsystemRegistryKeys.DOT11_ENABLED, c)) {
                        return Response.status(422).build();
                    }

                    if (!nzyme.getSubsystems().isEnabled(Subsystem.DOT11, organizationId, null)) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                    }

                    break;
                case "subsystem_bluetooth_enabled":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(SubsystemRegistryKeys.BLUETOOTH_ENABLED, c)) {
                        return Response.status(422).build();
                    }

                    if (!nzyme.getSubsystems().isEnabled(Subsystem.BLUETOOTH, organizationId, null)) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                    }

                    break;
                case "subsystem_uav_enabled":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(SubsystemRegistryKeys.UAV_ENABLED, c)) {
                        return Response.status(422).build();
                    }

                    if (!nzyme.getSubsystems().isEnabled(Subsystem.UAV, organizationId, null)) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                    }

                    break;
            }

            nzyme.getDatabaseCoreRegistry().setValue(c.getKey(), c.getValue().toString(), organizationId, tenantId);
        }

        return Response.ok().build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/users")
    public Response findAllUsersOfTenant(@Context SecurityContext sc,
                                         @PathParam("organizationId") UUID organizationId,
                                         @PathParam("tenantId") UUID tenantId,
                                         @QueryParam("limit") int limit,
                                         @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<UserDetailsResponse> users = Lists.newArrayList();
        for (UserEntry user : nzyme.getAuthenticationService().findAllUsersOfTenant(
                organizationId, tenantId, limit, offset)) {
            users.add(userEntryToResponse(
                    user,
                    nzyme.getAuthenticationService().findPermissionsOfUser(user.uuid()),
                    nzyme.getAuthenticationService().findTapPermissionsOfUser(user.uuid())
            ));
        }

        long userCount = nzyme.getAuthenticationService().countUsersOfTenant(
                nzyme.getAuthenticationService().findTenant(tenantId).get()
        );

        return Response.ok(UsersListResponse.create(userCount, users)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/users/show/{userId}")
    public Response findUserOfTenant(@Context SecurityContext sc,
                                     @PathParam("organizationId") UUID organizationId,
                                     @PathParam("tenantId") UUID tenantId,
                                     @PathParam("userId") UUID userId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserOfTenant(organizationId, tenantId, userId);

        if (user.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Users cannot delete themselves.
        boolean isDeletable = !authenticatedUser.getUserId().equals(userId);

        // Find API keys of this user.
        List<ApiKeyDetailsResponse> apiKeys = Lists.newArrayList();
        for (ApiKeyEntry key : nzyme.getAuthenticationService().findAllApiKeys(user.get().uuid())) {
            apiKeys.add(ApiKeyDetailsResponse.create(
                    key.uuid(),
                    key.userId(),
                    key.name(),
                    key.lastActivity(),
                    key.expiresAt(),
                    key.createdAt()
            ));
        }

        return Response.ok(UserOfTenantDetailsResponse.create(
                userEntryToResponse(
                        user.get(),
                        nzyme.getAuthenticationService().findPermissionsOfUser(user.get().uuid()),
                        nzyme.getAuthenticationService().findTapPermissionsOfUser(user.get().uuid())
                ),
                isDeletable,
                apiKeys)
        ).build();
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/users")
    @SessionOnly
    public Response createUserOfTenant(@Context SecurityContext sc,
                                       @PathParam("organizationId") UUID organizationId,
                                       @PathParam("tenantId") UUID tenantId,
                                       @Valid CreateUserRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!validateCreateUserRequest(req)) {
            LOG.info("Invalid parameters in create user request.");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        // Check if there is room in the quota.
        if (!nzyme.getQuotaService().isTenantQuotaAvailable(organizationId, tenantId, QuotaType.TENANT_USERS)) {
            return Response.status(422).build();
        }

        if (nzyme.getAuthenticationService().userWithEmailExists(req.email().toLowerCase())) {
            LOG.info("User with email address already exists.");
            return Response.status(Response.Status.UNAUTHORIZED).entity(
                    ErrorResponse.create("Email address already in use.")
            ).build();
        }

        PasswordHasher hasher = new PasswordHasher(nzyme.getMetrics());
        PasswordHasher.GeneratedHashAndSalt hash = hasher.createHash(req.password());

        nzyme.getAuthenticationService().createUserOfTenant(
                organizationId,
                tenantId,
                req.name(),
                req.email().toLowerCase(),
                req.disableMfa(),
                hash
        );

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/users/show/{userId}")
    public Response editUserOfTenant(@Context SecurityContext sc,
                                     @PathParam("organizationId") UUID organizationId,
                                     @PathParam("tenantId") UUID tenantId,
                                     @PathParam("userId") UUID userId,
                                     @Valid UpdateUserRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserOfTenant(organizationId, tenantId, userId);

        if (user.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!validateUpdateUserRequest(req)) {
            LOG.info("Invalid parameters in update user request.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!user.get().email().equals(req.email()) && nzyme.getAuthenticationService().userWithEmailExists(
                req.email().toLowerCase())) {
            LOG.info("User with email address already exists.");
            return Response.status(Response.Status.UNAUTHORIZED).entity(
                    ErrorResponse.create("Email address already in use.")
            ).build();
        }

        if (!user.get().hasMfaDisabled() && req.disableMfa()) {
            // System event.
            nzyme.getEventEngine().processEvent(SystemEvent.create(
                    SystemEventType.AUTHENTICATION_MFA_DISABLED,
                    DateTime.now(),
                    "MFA of user [" + user.get().email() + "] was disabled."
            ), organizationId, null);
        }

        nzyme.getAuthenticationService().editUser(
                userId,
                req.name(),
                req.email().toLowerCase(),
                req.disableMfa()
        );

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/users/show/{userId}/taps")
    public Response editUserOfTenantTapPermissions(@Context SecurityContext sc,
                                                   @PathParam("organizationId") UUID organizationId,
                                                   @PathParam("tenantId") UUID tenantId,
                                                   @PathParam("userId") UUID userId,
                                                   UpdateUserTapPermissionsRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserOfTenant(organizationId, tenantId, userId);

        if (user.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Update tap permissions.
        List<UUID> requestedPermissions = Lists.newArrayList();

        for (String tap : req.taps()) {
            requestedPermissions.add(UUID.fromString(tap));
        }

        List<UUID> newPermissions = Lists.newArrayList();

        // Make sure tap belongs to tenant and exists.
        for (TapPermissionEntry tap : nzyme.getAuthenticationService().findAllTapsOfTenant(organizationId, tenantId)) {
            if (requestedPermissions.contains(tap.uuid())) {
                newPermissions.add(tap.uuid());
            }
        }

        nzyme.getAuthenticationService().setUserTapPermissions(user.get().uuid(), newPermissions);

        // Update access all flag.
        nzyme.getAuthenticationService().setUserTapPermissionsAllowAll(user.get().uuid(), req.allowAccessAllTenantTaps());

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/users/show/{userId}/permissions")
    public Response editUserOfTenantPermissions(@Context SecurityContext sc,
                                                @PathParam("organizationId") UUID organizationId,
                                                @PathParam("tenantId") UUID tenantId,
                                                @PathParam("userId") UUID userId,
                                                UpdateUserPermissionsRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserOfTenant(organizationId, tenantId, userId);

        if (user.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAuthenticationService().setUserPermissions(user.get().uuid(), req.permissions());

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/users/show/{userId}")
    public Response deleteUserOfTenant(@Context SecurityContext sc,
                                       @PathParam("organizationId") UUID organizationId,
                                       @PathParam("tenantId") UUID tenantId,
                                       @PathParam("userId") UUID userId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserOfTenant(organizationId, tenantId, userId);

        if (user.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (authenticatedUser.getUserId().equals(userId)) {
            LOG.warn("User [{}] cannot delete themselves.", user.get().email());
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        nzyme.getAuthenticationService().deleteUserOfTenant(organizationId, tenantId, userId);

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/users/show/{userId}/password")
    @SessionOnly
    public Response editUserOfTenantPassword(@Context SecurityContext sc,
                                             @PathParam("organizationId") UUID organizationId,
                                             @PathParam("tenantId") UUID tenantId,
                                             @PathParam("userId") UUID userId,
                                             UpdatePasswordRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserOfTenant(organizationId, tenantId, userId);

        if (user.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!validateUpdatePasswordRequest(req)) {
            LOG.info("Invalid password in update password request.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        PasswordHasher hasher = new PasswordHasher(nzyme.getMetrics());
        PasswordHasher.GeneratedHashAndSalt hash = hasher.createHash(req.password());

        nzyme.getAuthenticationService().editUserPassword(
                userId,
                hash
        );

        // Invalidate session of user.
        nzyme.getAuthenticationService().deleteAllSessionsOfUser(user.get().uuid());

        // System event.
        nzyme.getEventEngine().processEvent(SystemEvent.create(
                SystemEventType.AUTHENTICATION_PASSWORD_CHANGED,
                DateTime.now(),
                "Password of user [" + user.get().email() + "] was changed by administrator."
        ), organizationId, tenantId);

        return Response.ok().build();
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/users/show/{userId}/mfa/reset")
    @SessionOnly
    public Response resetMFAOfUserOfTenant(@Context SecurityContext sc,
                                           @PathParam("organizationId") UUID organizationId,
                                           @PathParam("tenantId") UUID tenantId,
                                           @PathParam("userId") UUID userId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserOfTenant(organizationId, tenantId, userId);

        if (user.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAuthenticationService().resetMFAOfUser(user.get().uuid());

        LOG.info("Reset MFA credentials of user [{}] on admin request.", user.get().email());

        // System event.
        nzyme.getEventEngine().processEvent(SystemEvent.create(
                SystemEventType.AUTHENTICATION_MFA_RESET,
                DateTime.now(),
                "MFA method of user [" + user.get().email() + "] was reset by administrator."
        ), organizationId, tenantId);

        return Response.ok().build();
    }

    @GET
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/sessions")
    public Response findAllSessions(@QueryParam("limit") int limit, @QueryParam("offset") int offset) {
        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<SessionDetailsResponse> sessions = Lists.newArrayList();
        for (SessionEntryWithUserDetails session : nzyme.getAuthenticationService().findAllSessions(limit, offset)) {
            sessions.add(SessionDetailsResponse.create(
                    session.id(),
                    session.organizationId(),
                    session.tenantId(),
                    session.userId(),
                    session.userEmail(),
                    session.userName(),
                    session.isSuperadmin(),
                    session.isOrgadmin(),
                    session.remoteIp(),
                    session.createdAt(),
                    session.lastActivity(),
                    session.mfaValid(),
                    session.mfaRequestedAt(),
                    session.mfaDisabled()
            ));
        }

        long sessionCount = nzyme.getAuthenticationService().countAllSessions();

        return Response.ok(SessionsListResponse.create(sessionCount, sessions)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/sessions")
    public Response findSessionsOfOrganization(@Context SecurityContext sc,
                                               @PathParam("organizationId") UUID organizationId,
                                               @QueryParam("limit") int limit,
                                               @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if(!organizationExists(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<SessionDetailsResponse> sessions = Lists.newArrayList();
        for (SessionEntryWithUserDetails session : nzyme.getAuthenticationService().findSessionsOfOrganization(
                organizationId, limit, offset)) {
            sessions.add(SessionDetailsResponse.create(
                    session.id(),
                    session.organizationId(),
                    session.tenantId(),
                    session.userId(),
                    session.userEmail(),
                    session.userName(),
                    session.isSuperadmin(),
                    session.isOrgadmin(),
                    session.remoteIp(),
                    session.createdAt(),
                    session.lastActivity(),
                    session.mfaValid(),
                    session.mfaRequestedAt(),
                    session.mfaDisabled()
            ));
        }

        long sessionCount = nzyme.getAuthenticationService().countSessionsOfOrganization(organizationId);

        return Response.ok(SessionsListResponse.create(sessionCount, sessions)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/sessions")
    public Response findSessionsOfTenant(@Context SecurityContext sc,
                                         @PathParam("organizationId") UUID organizationId,
                                         @PathParam("tenantId") UUID tenantId,
                                         @QueryParam("limit") int limit,
                                         @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<SessionDetailsResponse> sessions = Lists.newArrayList();
        for (SessionEntryWithUserDetails session : nzyme.getAuthenticationService().findSessionsOfTenant(
                organizationId, tenantId, limit, offset)) {
            sessions.add(SessionDetailsResponse.create(
                    session.id(),
                    session.organizationId(),
                    session.tenantId(),
                    session.userId(),
                    session.userEmail(),
                    session.userName(),
                    session.isSuperadmin(),
                    session.isOrgadmin(),
                    session.remoteIp(),
                    session.createdAt(),
                    session.lastActivity(),
                    session.mfaValid(),
                    session.mfaRequestedAt(),
                    session.mfaDisabled()
            ));
        }

        long sessionCount = nzyme.getAuthenticationService().countSessionsOfTenant(organizationId, tenantId);

        return Response.ok(SessionsListResponse.create(sessionCount, sessions)).build();
    }


    @DELETE
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/sessions/show/{sessionId}")
    public Response invalidateSession(@Context SecurityContext sc,
                                      @PathParam("sessionId") long sessionId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<SessionEntry> session = nzyme.getAuthenticationService()
                .findSessionWithOrWithoutPassedMFAById(sessionId);

        if (session.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<UserEntry> user = nzyme.getAuthenticationService().findUserById(session.get().userId());

        if (user.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(user.get().organizationId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAuthenticationService().deleteSession(sessionId);

        return Response.ok().build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/taps")
    public Response findAllTapsOfTenant(@Context SecurityContext sc,
                                @PathParam("organizationId") UUID organizationId,
                                @PathParam("tenantId") UUID tenantId,
                                @QueryParam("limit") int limit,
                                @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<TapPermissionDetailsResponse> taps = Lists.newArrayList();
        for (TapPermissionEntry tap : nzyme.getAuthenticationService()
                .findAllTapsOfTenant(organizationId, tenantId, limit, offset)) {
            Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                    .findTenantLocation(organizationId, tenantId, tap.locationId());
            Optional<TenantLocationFloorEntry> floor;
            if (location.isPresent()) {
                floor = nzyme.getAuthenticationService()
                        .findFloorOfTenantLocation(location.get().uuid(), tap.floorId());
            } else {
                floor = Optional.empty();
            }

            taps.add(tapPermissionEntryToResponse(tap, location, floor));
        }

        long tapCount = nzyme.getAuthenticationService().countTapsOfTenant(
                nzyme.getAuthenticationService().findTenant(tenantId).get()
        );

        return Response.ok(TapPermissionsListResponse.create(tapCount, taps)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/taps/show/{tapUuid}")
    public Response findTap(@Context SecurityContext sc,
                            @PathParam("organizationId") UUID organizationId,
                            @PathParam("tenantId") UUID tenantId,
                            @PathParam("tapUuid") UUID tapId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TapPermissionEntry> tap = nzyme.getAuthenticationService().findTap(organizationId, tenantId, tapId);

        if (tap.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                .findTenantLocation(tap.get().locationId(), organizationId, tenantId);
        Optional<TenantLocationFloorEntry> floor;
        if (location.isPresent()) {
            floor = nzyme.getAuthenticationService()
                    .findFloorOfTenantLocation(location.get().uuid(), tap.get().floorId());
        } else {
            floor = Optional.empty();
        }

        return Response.ok(tapPermissionEntryToResponse(tap.get(), location, floor)).build();
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/taps")
    public Response createTap(@Context SecurityContext sc,
                              @PathParam("organizationId") UUID organizationId,
                              @PathParam("tenantId") UUID tenantId,
                              @Valid CreateTapRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if there is room in the quota.
        if (!nzyme.getQuotaService().isTenantQuotaAvailable(organizationId, tenantId, QuotaType.TAPS)) {
            return Response.status(422).build();
        }

        String secret = RandomStringUtils.random(64, true, true);

        nzyme.getAuthenticationService().createTap(
                organizationId,
                tenantId,
                secret,
                req.name(),
                req.description(),
                req.location(),
                req.floor(),
                req.latitude(),
                req.longitude()
        );

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/taps/show/{tapUuid}")
    public Response editTap(@Context SecurityContext sc,
                            @PathParam("organizationId") UUID organizationId,
                            @PathParam("tenantId") UUID tenantId,
                            @PathParam("tapUuid") UUID tapId,
                            @Valid UpdateTapRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TapPermissionEntry> tap = nzyme.getAuthenticationService().findTap(organizationId, tenantId, tapId);

        if (tap.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAuthenticationService().editTap(
                organizationId,
                tenantId,
                tapId,
                req.name(),
                req.description(),
                req.location(),
                req.floor(),
                req.latitude(),
                req.longitude()
        );

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/taps/show/{tapUuid}")
    public Response deleteTap(@Context SecurityContext sc,
                              @PathParam("organizationId") UUID organizationId,
                              @PathParam("tenantId") UUID tenantId,
                              @PathParam("tapUuid") UUID tapId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TapPermissionEntry> tap = nzyme.getAuthenticationService().findTap(organizationId, tenantId, tapId);

        if (tap.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Remove tap.
        nzyme.getAuthenticationService().deleteTap(organizationId, tenantId, tapId);

        // Remove tap from all monitors.
        nzyme.getMonitors().onTapDeleted(tapId);

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/taps/show/{tapUuid}/secret/cycle")
    public Response cycleTapSecret(@Context SecurityContext sc,
                                   @PathParam("organizationId") UUID organizationId,
                                   @PathParam("tenantId") UUID tenantId,
                                   @PathParam("tapUuid") UUID tapId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TapPermissionEntry> tap = nzyme.getAuthenticationService().findTap(organizationId, tenantId, tapId);

        if (tap.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String newSecret = RandomStringUtils.random(64, true, true);
        nzyme.getAuthenticationService().cycleTapSecret(organizationId, tenantId, tapId, newSecret);

        return Response.ok().build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations/show/{locationId}")
    public Response findTenantLocation(@Context SecurityContext sc,
                                       @PathParam("organizationId") UUID organizationId,
                                       @PathParam("tenantId") UUID tenantId,
                                       @PathParam("locationId") UUID locationId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TenantLocationEntry> result = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, organizationId, tenantId);

        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TenantLocationEntry tl = result.get();
        long floorCount = nzyme.getAuthenticationService().countFloorsOfTenantLocation(tl.uuid());
        long tapCount = nzyme.getAuthenticationService().countTapsOfTenantLocation(tl.uuid());
        return Response.ok(TenantLocationDetailsResponse.create(
                tl.uuid(),
                tl.name(),
                tl.description(),
                tl.longitude(),
                tl.latitude(),
                floorCount,
                tapCount,
                tl.environmentalAlertEventingEnabled(),
                tl.createdAt(),
                tl.updatedAt()
        )).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations")
    public Response findAllTenantLocations(@Context SecurityContext sc,
                                           @PathParam("organizationId") UUID organizationId,
                                           @PathParam("tenantId") UUID tenantId,
                                           @QueryParam("limit") int limit,
                                           @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long locationCount = nzyme.getAuthenticationService().countAllTenantLocations(organizationId, tenantId);

        List<TenantLocationDetailsResponse> locations = Lists.newArrayList();
        for (TenantLocationEntry tl :
                nzyme.getAuthenticationService().findAllTenantLocations(organizationId, tenantId, limit, offset)) {
            long floorCount = nzyme.getAuthenticationService().countFloorsOfTenantLocation(tl.uuid());
            long tapCount = nzyme.getAuthenticationService().countTapsOfTenantLocation(tl.uuid());

            locations.add(TenantLocationDetailsResponse.create(
                    tl.uuid(),
                    tl.name(),
                    tl.description(),
                    tl.longitude(),
                    tl.latitude(),
                    floorCount,
                    tapCount,
                    tl.environmentalAlertEventingEnabled(),
                    tl.createdAt(),
                    tl.updatedAt()
            ));
        }

        return Response.ok(TenantLocationListResponse.create(locationCount, locations)).build();
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations")
    public Response createTenantLocation(@Context SecurityContext sc,
                                         @Valid CreateTenantLocationRequest req,
                                         @PathParam("organizationId") UUID organizationId,
                                         @PathParam("tenantId") UUID tenantId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String description = Strings.isNullOrEmpty(req.description()) ? null : req.description();

        nzyme.getAuthenticationService().createTenantLocation(
                organizationId, tenantId, req.name(), description, req.latitude(), req.longitude()
        );

        // Re-fetch environment data.
        nzyme.getMessageBus().sendToAllOnlineNodes(ClusterMessage.create(
                MessageType.INVALIDATE_CACHE,
                Map.of("cache_type", "environment_data"),
                true
        ));

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations/show/{locationId}")
    public Response updateTenantLocation(@Context SecurityContext sc,
                                         @Valid UpdateTenantLocationRequest req,
                                         @PathParam("organizationId") UUID organizationId,
                                         @PathParam("tenantId") UUID tenantId,
                                         @PathParam("locationId") UUID locationId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Check if the location exists.
        Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, organizationId, tenantId);

        if (location.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String description = Strings.isNullOrEmpty(req.description()) ? null : req.description();

        nzyme.getAuthenticationService().updateTenantLocation(
                location.get().id(), req.name(), description, req.latitude(), req.longitude()
        );

        // Re-fetch environment data.
        nzyme.getMessageBus().sendToAllOnlineNodes(ClusterMessage.create(
                MessageType.INVALIDATE_CACHE,
                Map.of("cache_type", "environment_data"),
                true
        ));

        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations/show/{locationId}")
    public Response deleteTenantLocation(@Context SecurityContext sc,
                                         @PathParam("organizationId") UUID organizationId,
                                         @PathParam("tenantId") UUID tenantId,
                                         @PathParam("locationId") UUID locationId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TenantLocationEntry> result = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, organizationId, tenantId);

        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TenantLocationEntry tl = result.get();
        long floorCount = nzyme.getAuthenticationService().countFloorsOfTenantLocation(tl.uuid());

        if (floorCount > 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        nzyme.getAuthenticationService().deleteTenantLocation(tl.id());

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations/show/{locationId}/environment/alerts/eventing/enabled/{enabled}")
    public Response setEnvironmentalAlertEventingOfTenantLocation(@Context SecurityContext sc,
                                                                  @PathParam("organizationId") UUID organizationId,
                                                                  @PathParam("tenantId") UUID tenantId,
                                                                  @PathParam("locationId") UUID locationId,
                                                                  @PathParam("enabled") boolean enabled) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TenantLocationEntry> result = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, organizationId, tenantId);

        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAuthenticationService().setEnvironmentalAlertEventingOfTenantLocation(
                result.get().id(), enabled
        );

        nzyme.getAuthenticationService().updateUpdatedAtOfTenantLocation(result.get().id());

        return Response.ok().build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations/show/{locationId}/floors")
    public Response findAllFloorsOfTenantLocation(@Context SecurityContext sc,
                                                  @PathParam("organizationId") UUID organizationId,
                                                  @PathParam("tenantId") UUID tenantId,
                                                  @PathParam("locationId") UUID locationId,
                                                  @QueryParam("limit") int limit,
                                                  @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Find location.
        Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, organizationId, tenantId);

        if (location.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long floorCount = nzyme.getAuthenticationService().countAllFloorsOfTenantLocation(location.get().uuid());
        List<TenantLocationFloorDetailsResponse> floors = Lists.newArrayList();
        for (TenantLocationFloorEntry floor : nzyme.getAuthenticationService()
                .findAllFloorsOfTenantLocation(location.get().uuid(), limit, offset)) {
            List<TapPositionResponse> tapPositions = Lists.newArrayList();
            for (Tap t : nzyme.getTapManager().findAllTapsOnFloor(organizationId, tenantId, locationId, floor.uuid())) {
                //noinspection DataFlowIssue
                tapPositions.add(TapPositionResponse.create(
                        t.uuid(), t.name(), t.x(), t.y(), t.lastReport(), Tools.isTapActive(t.lastReport())
                ));
            }

            floors.add(TenantLocationFloorDetailsResponse.create(
                    floor.uuid(),
                    floor.locationId(),
                    floor.number(),
                    Tools.buildFloorName(floor),
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
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations/show/{locationId}/floors/show/{floorId}")
    public Response findFloorOfTenantLocation(@Context SecurityContext sc,
                                              @PathParam("organizationId") UUID organizationId,
                                              @PathParam("tenantId") UUID tenantId,
                                              @PathParam("locationId") UUID locationId,
                                              @PathParam("floorId") UUID floorId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Find location.
        Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, organizationId, tenantId);

        if (location.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TenantLocationFloorEntry> result = nzyme.getAuthenticationService()
                .findFloorOfTenantLocation(location.get().uuid(), floorId);

        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TenantLocationFloorEntry floor = result.get();

        List<TapPositionResponse> tapPositions = Lists.newArrayList();
        for (Tap t : nzyme.getTapManager().findAllTapsOnFloor(organizationId, tenantId, locationId, floor.uuid())) {
            //noinspection DataFlowIssue
            tapPositions.add(TapPositionResponse.create(
                    t.uuid(), t.name(), t.x(), t.y(), t.lastReport(), Tools.isTapActive(t.lastReport())
            ));
        }

        return Response.ok(TenantLocationFloorDetailsResponse.create(
                floor.uuid(),
                floor.locationId(),
                floor.number(),
                Tools.buildFloorName(floor),
                floor.plan() != null,
                tapPositions.size(),
                tapPositions,
                Tools.round(floor.pathLossExponent(), 1),
                floor.createdAt(),
                floor.updatedAt()
        )).build();
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations/show/{locationId}/floors")
    public Response createFloorOfTenantLocation(@Context SecurityContext sc,
                                                @Valid CreateFloorOfTenantLocationRequest req,
                                                @PathParam("organizationId") UUID organizationId,
                                                @PathParam("tenantId") UUID tenantId,
                                                @PathParam("locationId") UUID locationId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Find location.
        Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, organizationId, tenantId);

        if (location.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (nzyme.getAuthenticationService().tenantLocationHasFloorWithNumber(location.get().uuid(), req.number())) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(
                    ErrorResponse.create("This location already has a floor with that number.")
            ).build();
        }

        String name = Strings.isNullOrEmpty(req.name()) ? null : req.name();
        nzyme.getAuthenticationService().createFloorOfTenantLocation(
                location.get().uuid(), req.number(), name, Tools.round(req.pathLossExponent(), 1)
        );
        nzyme.getAuthenticationService().updateUpdatedAtOfTenantLocation(location.get().id());

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations/show/{locationId}/floors/show/{floorId}")
    public Response updateFloorOfTenantLocation(@Context SecurityContext sc,
                                                @Valid UpdateFloorOfTenantLocationRequest req,
                                                @PathParam("organizationId") UUID organizationId,
                                                @PathParam("tenantId") UUID tenantId,
                                                @PathParam("locationId") UUID locationId,
                                                @PathParam("floorId") UUID floorId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Find location.
        Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, organizationId, tenantId);

        if (location.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TenantLocationFloorEntry> result = nzyme.getAuthenticationService()
                .findFloorOfTenantLocation(location.get().uuid(), floorId);

        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TenantLocationFloorEntry floor = result.get();

        // Check if floor number already exists, but only if it's changing in this request.
        if (floor.number() != req.number()
                && nzyme.getAuthenticationService()
                .tenantLocationHasFloorWithNumber(location.get().uuid(), req.number())) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(
                    ErrorResponse.create("This location already has a floor with that number.")
            ).build();
        }

        String name = Strings.isNullOrEmpty(req.name()) ? null : req.name();
        nzyme.getAuthenticationService().updateFloorOfTenantLocation(
                floor.id(), req.number(), name, Tools.round(req.pathLossExponent(), 1)
        );
        nzyme.getAuthenticationService().updateUpdatedAtOfTenantLocation(location.get().id());

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations/show/{locationId}/floors/show/{floorId}")
    public Response deleteFloorOfTenantLocation(@Context SecurityContext sc,
                                                @Valid UpdateFloorOfTenantLocationRequest req,
                                                @PathParam("organizationId") UUID organizationId,
                                                @PathParam("tenantId") UUID tenantId,
                                                @PathParam("locationId") UUID locationId,
                                                @PathParam("floorId") UUID floorId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Find location.
        Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, organizationId, tenantId);

        if (location.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TenantLocationFloorEntry> result = nzyme.getAuthenticationService()
                .findFloorOfTenantLocation(location.get().uuid(), floorId);

        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TenantLocationFloorEntry floor = result.get();

        // Remove all taps from floor.
        for (Tap tap : nzyme.getTapManager().findAllTapsOnFloor(organizationId, tenantId, locationId, floorId)) {
            nzyme.getAuthenticationService().removeTapFromFloor(tap.id(), locationId, floorId);
        }

        nzyme.getAuthenticationService().deleteFloorOfTenantLocation(floor.id());
        nzyme.getAuthenticationService().updateUpdatedAtOfTFloor(floor.id());
        nzyme.getAuthenticationService().updateUpdatedAtOfTenantLocation(location.get().id());

        return Response.ok().build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations/show/{locationId}/floors/show/{floorId}/plan")
    public Response findFloorPlan(@Context SecurityContext sc,
                                  @PathParam("organizationId") UUID organizationId,
                                  @PathParam("tenantId") UUID tenantId,
                                  @PathParam("locationId") UUID locationId,
                                  @PathParam("floorId") UUID floorId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Find location.
        Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, organizationId, tenantId);

        if (location.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TenantLocationFloorEntry> floor = nzyme.getAuthenticationService()
                .findFloorOfTenantLocation(location.get().uuid(), floorId);

        if (floor.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (floor.get().plan() == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ByteArrayInputStream imageBytes = new ByteArrayInputStream(floor.get().plan());

        try {
            BufferedImage image = ImageIO.read(imageBytes);

            //noinspection DataFlowIssue
            return Response.ok(FloorPlanResponse.create(
                    BaseEncoding.base64().encode(
                            floor.get().plan()), image.getWidth(), image.getHeight(), floor.get().planWidthMeters(), floor.get().planLengthMeters()
                    )
            ).build();
        } catch (Exception e) {
            LOG.error("Could not read floor plan image data from database. Floor: {}", floor.get(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations/show/{locationId}/floors/show/{floorId}/plan")
    public Response uploadFloorPlan(@Context SecurityContext sc,
                                    @FormDataParam("plan") InputStream planFile,
                                    @FormDataParam("width_meters") int widthMeters,
                                    @FormDataParam("length_meters") int lengthMeters,
                                    @PathParam("organizationId") UUID organizationId,
                                    @PathParam("tenantId") UUID tenantId,
                                    @PathParam("locationId") UUID locationId,
                                    @PathParam("floorId") UUID floorId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (widthMeters <= 0 || lengthMeters <= 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Find location.
        Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, organizationId, tenantId);

        if (location.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TenantLocationFloorEntry> floor = nzyme.getAuthenticationService()
                .findFloorOfTenantLocation(location.get().uuid(), floorId);

        if (floor.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        byte[] planBytes;
        try {
            /*
             * Set the limit to 5MB plus 1 byte. If it fills this entirely, a file too large was uploaded.
             * Another, larger limit is handled by our HTTP server itself.
             */
            planBytes = ByteStreams.limit(planFile, 5242881).readAllBytes();
        } catch (IOException e) {
            LOG.error(e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        if (planBytes.length == 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.create("Uploaded floor plan file is empty."))
                    .build();
        }

        if (planBytes.length == 5242881) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.create("Uploaded floor plan file is too large. Maximum file size is 5MB."))
                    .build();
        }

        try {
            // New input stream because the previous one had been consumed when checking the length.
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(planBytes));

            if (image == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ErrorResponse.create("Could not read image file. Make sure it is a JPG or PNG file."))
                        .build();
            }

            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            ImageIO.write(image, "png", pngOut);

            nzyme.getAuthenticationService().writeFloorPlan(
                    floor.get().id(),
                    pngOut.toByteArray(),
                    image.getWidth(),
                    image.getHeight(),
                    widthMeters,
                    lengthMeters
            );
        } catch (Exception e) {
            LOG.warn("Could not process uploaded floor plan file.", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.create("Could not process uploaded floor plan file. Make sure it is a JPG or " +
                            "PNG file."))
                    .build();
        }

        nzyme.getAuthenticationService().updateUpdatedAtOfTenantLocation(location.get().id());

        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations/show/{locationId}/floors/show/{floorId}/plan")
    public Response deleteFloorPlan(@Context SecurityContext sc,
                                    @PathParam("organizationId") UUID organizationId,
                                    @PathParam("tenantId") UUID tenantId,
                                    @PathParam("locationId") UUID locationId,
                                    @PathParam("floorId") UUID floorId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Find location.
        Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, organizationId, tenantId);

        if (location.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TenantLocationFloorEntry> floor = nzyme.getAuthenticationService()
                .findFloorOfTenantLocation(location.get().uuid(), floorId);

        if (floor.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Remove all taps from floor.
        for (Tap tap : nzyme.getTapManager().findAllTapsOnFloor(organizationId, tenantId, locationId, floorId)) {
            nzyme.getAuthenticationService().removeTapFromFloor(tap.id(), locationId, floorId);
        }

        nzyme.getAuthenticationService().deleteFloorPlan(floor.get().id());
        nzyme.getAuthenticationService().updateUpdatedAtOfTenantLocation(location.get().id());

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations/show/{locationId}/floors/show/{floorId}/plan/taps/show/{tapId}/coords")
    public Response placeTapOnFloor(@Context SecurityContext sc,
                                    @Valid PlaceTapRequest req,
                                    @PathParam("organizationId") UUID organizationId,
                                    @PathParam("tenantId") UUID tenantId,
                                    @PathParam("locationId") UUID locationId,
                                    @PathParam("floorId") UUID floorId,
                                    @PathParam("tapId") UUID tapId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Find location.
        Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, organizationId, tenantId);

        if (location.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TenantLocationFloorEntry> floor = nzyme.getAuthenticationService()
                .findFloorOfTenantLocation(location.get().uuid(), floorId);

        if (floor.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TapPermissionEntry> tap = nzyme.getAuthenticationService().findTap(organizationId, tenantId, tapId);
        if (tap.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAuthenticationService().placeTapOnFloor(tap.get().id(), locationId, floorId, req.x(), req.y());
        nzyme.getAuthenticationService().updateUpdatedAtOfTFloor(floor.get().id());
        nzyme.getAuthenticationService().updateUpdatedAtOfTenantLocation(location.get().id());

        return Response.status(Response.Status.OK).build();
    }

    @DELETE
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations/show/{locationId}/floors/show/{floorId}/plan/taps/show/{tapId}")
    public Response deleteTapFromFloor(@Context SecurityContext sc,
                                    @PathParam("organizationId") UUID organizationId,
                                    @PathParam("tenantId") UUID tenantId,
                                    @PathParam("locationId") UUID locationId,
                                    @PathParam("floorId") UUID floorId,
                                    @PathParam("tapId") UUID tapId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationAndTenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Check if user is org admin for this org.
        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Find location.
        Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                .findTenantLocation(locationId, organizationId, tenantId);

        if (location.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TenantLocationFloorEntry> floor = nzyme.getAuthenticationService()
                .findFloorOfTenantLocation(location.get().uuid(), floorId);

        if (floor.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TapPermissionEntry> tap = nzyme.getAuthenticationService().findTap(organizationId, tenantId, tapId);
        if (tap.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAuthenticationService().removeTapFromFloor(tap.get().id(), locationId, floorId);
        nzyme.getAuthenticationService().updateUpdatedAtOfTFloor(floor.get().id());
        nzyme.getAuthenticationService().updateUpdatedAtOfTenantLocation(location.get().id());

        return Response.status(Response.Status.OK).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/events/actions")
    public Response findAllEventActionsOfOrganization(@Context SecurityContext sc,
                                                      @PathParam("organizationId") UUID organizationId,
                                                      @QueryParam("limit") int limit,
                                                      @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationExists(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        EventEngineImpl eventEngine = (EventEngineImpl) nzyme.getEventEngine();

        long total = eventEngine.countAllEventActionsOfOrganization(organizationId);
        List<EventActionDetailsResponse> events = Lists.newArrayList();
        for (EventActionEntry ea : eventEngine.findAllEventActionsOfOrganization(organizationId, limit, offset)) {
            List<SystemEventType> subscribedSystemEvents = eventEngine
                    .findAllSystemEventTypesActionIsSubscribedTo(ea.uuid());
            List<DetectionType> subscribedDetectionEvents = eventEngine
                    .findAllDetectionEventTypesActionIsSubscribedTo(ea.uuid());

            events.add(EventActionUtilities.eventActionEntryToResponse(
                    ea,
                    subscribedSystemEvents,
                    subscribedDetectionEvents
            ));
        }

        return Response.ok(EventActionsListResponse.create(total, events)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/events/actions/show/{actionId}")
    public Response findEventActionOfOrganization(@Context SecurityContext sc,
                                                  @PathParam("organizationId") UUID organizationId,
                                                  @PathParam("actionId") UUID actionId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!organizationExists(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!authenticatedUser.isSuperAdministrator() && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        EventEngineImpl eventEngine = (EventEngineImpl) nzyme.getEventEngine();
        Optional<EventActionEntry> ea = eventEngine.findEventActionOfOrganization(organizationId, actionId);

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

    @GET
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/global/configuration")
    public Response getGlobalSuperAdministratorConfiguration() {

        int sessionTimeoutMinutes = Integer.parseInt(nzyme.getDatabaseCoreRegistry()
                .getValue(AuthenticationRegistryKeys.SESSION_TIMEOUT_MINUTES.key())
                .orElse(AuthenticationRegistryKeys.SESSION_TIMEOUT_MINUTES.defaultValue().get()));

        int sessionInactivityTimeoutMinutes =  Integer.parseInt(nzyme.getDatabaseCoreRegistry()
                .getValue(AuthenticationRegistryKeys.SESSION_INACTIVITY_TIMEOUT_MINUTES.key())
                .orElse(AuthenticationRegistryKeys.SESSION_INACTIVITY_TIMEOUT_MINUTES.defaultValue().get()));

        int mfaTimeoutMinutes =  Integer.parseInt(nzyme.getDatabaseCoreRegistry()
                .getValue(AuthenticationRegistryKeys.MFA_TIMEOUT_MINUTES.key())
                .orElse(AuthenticationRegistryKeys.MFA_TIMEOUT_MINUTES.defaultValue().get()));

        SuperadminSettingsResponse response = SuperadminSettingsResponse.create(
                ConfigurationEntryResponse.create(
                        AuthenticationRegistryKeys.SESSION_TIMEOUT_MINUTES.key(),
                        "Session Timeout (minutes)",
                        sessionTimeoutMinutes,
                        ConfigurationEntryValueType.NUMBER,
                        AuthenticationRegistryKeys.SESSION_TIMEOUT_MINUTES.defaultValue().orElse(null),
                        AuthenticationRegistryKeys.SESSION_TIMEOUT_MINUTES.requiresRestart(),
                        AuthenticationRegistryKeys.SESSION_TIMEOUT_MINUTES.constraints().orElse(Collections.emptyList()),
                        "authentication-settings"
                ),
                ConfigurationEntryResponse.create(
                        AuthenticationRegistryKeys.SESSION_INACTIVITY_TIMEOUT_MINUTES.key(),
                        "Session Inactivity Timeout (minutes)",
                        sessionInactivityTimeoutMinutes,
                        ConfigurationEntryValueType.NUMBER,
                        AuthenticationRegistryKeys.SESSION_INACTIVITY_TIMEOUT_MINUTES.defaultValue().orElse(null),
                        AuthenticationRegistryKeys.SESSION_INACTIVITY_TIMEOUT_MINUTES.requiresRestart(),
                        AuthenticationRegistryKeys.SESSION_INACTIVITY_TIMEOUT_MINUTES.constraints().orElse(Collections.emptyList()),
                        "authentication-settings"
                ),
                ConfigurationEntryResponse.create(
                        AuthenticationRegistryKeys.MFA_TIMEOUT_MINUTES.key(),
                        "MFA Timeout (minutes)",
                        mfaTimeoutMinutes,
                        ConfigurationEntryValueType.NUMBER,
                        AuthenticationRegistryKeys.MFA_TIMEOUT_MINUTES.defaultValue().orElse(null),
                        AuthenticationRegistryKeys.MFA_TIMEOUT_MINUTES.requiresRestart(),
                        AuthenticationRegistryKeys.MFA_TIMEOUT_MINUTES.constraints().orElse(Collections.emptyList()),
                        "authentication-settings"
                )
        );

        return Response.ok(response).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/global/configuration")
    public Response setGlobalSuperAdministratorConfiguration(@Valid SuperadminSettingsUpdateRequest ur) {
        for (Map.Entry<String, Object> c : ur.change().entrySet()) {
            switch (c.getKey()) {
                case "session_timeout_minutes":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(AuthenticationRegistryKeys.SESSION_TIMEOUT_MINUTES, c)) {
                        return Response.status(422).build();
                    }
                    break;
                case "session_inactivity_timeout_minutes":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(AuthenticationRegistryKeys.SESSION_INACTIVITY_TIMEOUT_MINUTES, c)) {
                        return Response.status(422).build();
                    }
                    break;
                case "mfa_timeout_minutes":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(AuthenticationRegistryKeys.MFA_TIMEOUT_MINUTES, c)) {
                        return Response.status(422).build();
                    }
                    break;
            }

            nzyme.getDatabaseCoreRegistry().setValue(c.getKey(), c.getValue().toString());
        }

         return Response.ok().build();
    }

    @GET
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/superadmins")
    public Response findAllSuperAdministrators(@QueryParam("limit") int limit, @QueryParam("offset") int offset) {
        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<UserDetailsResponse> users = Lists.newArrayList();
        for (UserEntry user : nzyme.getAuthenticationService().findAllSuperAdministrators(limit, offset)) {
            users.add(userEntryToResponse(
                    user,
                    Collections.emptyList(),
                    Collections.emptyList())
            );
        }

        long superadminCount = nzyme.getAuthenticationService().countSuperAdministrators();

        return Response.ok(UsersListResponse.create(superadminCount, users)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/superadmins/show/{id}")
    public Response findSuperAdministrator(@Context SecurityContext sc, @PathParam("id") UUID userId) {
        AuthenticatedUser sessionUser = getAuthenticatedUser(sc);
        Optional<UserEntry> superAdmin = nzyme.getAuthenticationService().findSuperAdministrator(userId);

        if (superAdmin.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        boolean isDeletable = nzyme.getAuthenticationService().countSuperAdministrators() != 1
                && !sessionUser.getUserId().equals(userId);

        // Find API keys of this user.
        List<ApiKeyDetailsResponse> apiKeys = Lists.newArrayList();
        for (ApiKeyEntry key : nzyme.getAuthenticationService().findAllApiKeys(superAdmin.get().uuid())) {
            apiKeys.add(ApiKeyDetailsResponse.create(
                    key.uuid(),
                    key.userId(),
                    key.name(),
                    key.lastActivity(),
                    key.expiresAt(),
                    key.createdAt()
            ));
        }

        return Response.ok(SuperAdministratorDetailsResponse.create(
                userEntryToResponse(
                        superAdmin.get(),
                        Collections.emptyList(),
                        Collections.emptyList()
                ), isDeletable, apiKeys
        )).build();
    }

    @POST
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/superadmins")
    @SessionOnly
    public Response createSuperAdministrator(@Valid CreateUserRequest req) {
        if (!validateCreateUserRequest(req)) {
            LOG.info("Invalid parameters in create user request.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (nzyme.getAuthenticationService().userWithEmailExists(req.email().toLowerCase())) {
            LOG.info("User with email address already exists.");
            return Response.status(Response.Status.UNAUTHORIZED).entity(
                    ErrorResponse.create("Email address already in use.")
            ).build();
        }

        PasswordHasher hasher = new PasswordHasher(nzyme.getMetrics());
        PasswordHasher.GeneratedHashAndSalt hash = hasher.createHash(req.password());

        nzyme.getAuthenticationService().createSuperAdministrator(
                req.name(),
                req.email().toLowerCase(),
                req.disableMfa(),
                hash
        );

        // System event.
        nzyme.getEventEngine().processEvent(SystemEvent.create(
                SystemEventType.AUTHENTICATION_SUPERADMIN_CREATED,
                DateTime.now(),
                "A new super administrator [" + req.email() + "] was created."
        ), null, null);

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/superadmins/show/{userId}")
    public Response editSuperAdministrator(@PathParam("userId") UUID userId,
                                           @Valid UpdateUserRequest req) {
        Optional<UserEntry> superAdmin = nzyme.getAuthenticationService().findSuperAdministrator(userId);

        if (superAdmin.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!validateUpdateUserRequest(req)) {
            LOG.info("Invalid parameters in update user request.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (!superAdmin.get().email().equals(req.email()) && nzyme.getAuthenticationService().userWithEmailExists(
                req.email().toLowerCase())) {
            LOG.info("User with email address already exists.");
            return Response.status(Response.Status.UNAUTHORIZED).entity(
                    ErrorResponse.create("Email address already in use.")
            ).build();
        }

        if (!superAdmin.get().hasMfaDisabled() && req.disableMfa()) {
            // System event.
            nzyme.getEventEngine().processEvent(SystemEvent.create(
                    SystemEventType.AUTHENTICATION_SUPERADMIN_MFA_DISABLED,
                    DateTime.now(),
                    "MFA for super administrator [" + req.email() + "] was disabled."
            ), null, null);
        }

        nzyme.getAuthenticationService().editUser(
                userId,
                req.name(),
                req.email().toLowerCase(),
                req.disableMfa()
        );

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/superadmins/show/{userId}/password")
    @SessionOnly
    public Response editSuperAdministratorPassword(@Context SecurityContext sc,
                                                   @PathParam("userId") UUID userId,
                                                   UpdatePasswordRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        Optional<UserEntry> superAdmin = nzyme.getAuthenticationService().findSuperAdministrator(userId);

        if (superAdmin.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!validateUpdatePasswordRequest(req)) {
            LOG.info("Invalid password in update password request.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        PasswordHasher hasher = new PasswordHasher(nzyme.getMetrics());
        PasswordHasher.GeneratedHashAndSalt hash = hasher.createHash(req.password());

        nzyme.getAuthenticationService().editUserPassword(
                userId,
                hash
        );

        // Invalidate session of user.
        nzyme.getAuthenticationService().deleteAllSessionsOfUser(superAdmin.get().uuid());

        // System event.
        nzyme.getEventEngine().processEvent(SystemEvent.create(
                SystemEventType.AUTHENTICATION_SUPERADMIN_PASSWORD_CHANGED,
                DateTime.now(),
                "Password of super administrator [" + superAdmin.get().email() + "] was changed by [" +
                        authenticatedUser.getEmail() + "]."
        ), null, null);

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/superadmins/show/{id}")
    public Response deleteSuperAdministrator(@Context SecurityContext sc, @PathParam("id") UUID userId) {
        AuthenticatedUser sessionUser = getAuthenticatedUser(sc);

        if (sessionUser.getUserId().equals(userId)) {
            LOG.warn("Super administrators cannot delete themselves.");
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        if (nzyme.getAuthenticationService().countSuperAdministrators() == 1) {
            LOG.warn("Last remaining super administrator cannot be deleted.");
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Optional<UserEntry> superAdmin = nzyme.getAuthenticationService().findSuperAdministrator(userId);

        if (superAdmin.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAuthenticationService().deleteSuperAdministrator(userId);

        // System event.
        nzyme.getEventEngine().processEvent(SystemEvent.create(
                SystemEventType.AUTHENTICATION_SUPERADMIN_DELETED,
                DateTime.now(),
                "Super administrator [" + superAdmin.get().email() + "] was deleted."
        ), null, null);

        return Response.ok().build();
    }

    @POST
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/superadmins/show/{id}/mfa/reset")
    @SessionOnly
    public Response resetSuperAdministratorMFA(@Context SecurityContext sc, @PathParam("id") UUID userId) {
        AuthenticatedUser sessionUser = getAuthenticatedUser(sc);

        Optional<UserEntry> superAdmin = nzyme.getAuthenticationService().findSuperAdministrator(userId);

        if (superAdmin.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAuthenticationService().resetMFAOfUser(superAdmin.get().uuid());

        LOG.info("Reset MFA credentials of super administrator [{}] on admin request.", superAdmin.get().email());

        // System event.
        nzyme.getEventEngine().processEvent(SystemEvent.create(
                SystemEventType.AUTHENTICATION_SUPERADMIN_MFA_RESET,
                DateTime.now(),
                "MFA method of super administrator [" + superAdmin.get().email() + "] was reset by ["
                        + sessionUser.getEmail() + "]"
        ), null, null);

        return Response.ok().build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/permissions/all")
    public Response getAllPermissions() {
        List<PermissionDetailsResponse> permissions = Lists.newArrayList();

        for (Permission permission : Permissions.ALL.values()) {
            permissions.add(PermissionDetailsResponse.create(
                    permission.id(),
                    permission.name(),
                    permission.description(),
                    permission.respectsTapScope()
            ));
        }

        return Response.ok(PermissionListResponse.create(permissions)).build();
    }

    private OrganizationDetailsResponse organizationEntryToResponse(OrganizationEntry org) {
        return OrganizationDetailsResponse.create(
                org.uuid(),
                org.name(),
                org.description(),
                org.createdAt(),
                org.updatedAt(),
                nzyme.getAuthenticationService().countTenantsOfOrganization(org),
                nzyme.getAuthenticationService().countUsersOfOrganization(org),
                nzyme.getAuthenticationService().countTapsOfOrganization(org),
                nzyme.getAuthenticationService().isOrganizationDeletable(org)
        );
    }

    private TenantDetailsResponse tenantEntryToResponse(TenantEntry t) {
        return TenantDetailsResponse.create(
                t.uuid(),
                t.organizationUuid(),
                t.name(),
                t.description(),
                t.createdAt(),
                t.updatedAt(),
                nzyme.getAuthenticationService().countUsersOfTenant(t),
                nzyme.getAuthenticationService().countTapsOfTenant(t),
                t.sessionTimeoutMinutes(),
                t.sessionInactivityTimeoutMinutes(),
                t.mfaTimeoutMinutes(),
                nzyme.getAuthenticationService().isTenantDeletable(t)
        );
    }

    private UserDetailsResponse userEntryToResponse(UserEntry u, List<String> permissions, List<UUID> tapPermissions) {
        long apiKeys = nzyme.getAuthenticationService().countAllApiKeys(u.uuid());

        return UserDetailsResponse.create(
                u.uuid(),
                u.organizationId(),
                u.tenantId(),
                u.email(),
                u.name(),
                u.createdAt(),
                u.updatedAt(),
                u.lastActivity(),
                u.lastRemoteIp(),
                u.lastGeoCity(),
                u.lastGeoCountry(),
                u.lastGeoAsn(),
                permissions,
                u.accessAllTenantTaps(),
                tapPermissions,
                u.isLoginThrottled(),
                u.hasMfaDisabled(),
                apiKeys
        );
    }

    private TapPermissionDetailsResponse tapPermissionEntryToResponse(TapPermissionEntry tpe,
                                                                      Optional<TenantLocationEntry> location,
                                                                      Optional<TenantLocationFloorEntry> floor) {
        String decryptedSecret;
        try {
            decryptedSecret = new String(nzyme.getCrypto().decryptWithClusterKey(Base64.decode(tpe.secret())));
        } catch (Crypto.CryptoOperationException e) {
            throw new RuntimeException("Could not decrypt tap secret.", e);
        }

        return TapPermissionDetailsResponse.create(
                tpe.uuid(),
                tpe.organizationId(),
                tpe.tenantId(),
                tpe.name(),
                tpe.description(),
                tpe.latitude(),
                tpe.longitude(),
                decryptedSecret,
                tpe.floorId() != null && tpe.locationId() != null,
                tpe.locationId(),
                location.map(TenantLocationEntry::name).orElse(null),
                tpe.floorId(),
                floor.map(Tools::buildFloorName).orElse(null),
                tpe.floorLocationX(),
                tpe.floorLocationY(),
                tpe.createdAt(),
                tpe.updatedAt(),
                tpe.lastReport(),
                Tools.isTapActive(tpe.lastReport())
        );
    }

    private boolean organizationExists(UUID organizationId) {
        return nzyme.getAuthenticationService().findOrganization(organizationId).isPresent();
    }

    private boolean organizationAndTenantExists(UUID organizationId, UUID tenantId) {
        Optional<OrganizationEntry> org = nzyme.getAuthenticationService().findOrganization(organizationId);

        if (org.isEmpty()) {
            return false;
        }

        Optional<TenantEntry> tenant = nzyme.getAuthenticationService().findTenant(tenantId);

        if (tenant.isEmpty()) {
            return false;
        }

        if (!tenant.get().organizationUuid().equals(organizationId)) {
            return false;
        }

        return true;
    }

    public static boolean validateCreateUserRequest(CreateUserRequest req) {
        if (req == null) {
            return false;
        }

        if (req.name() == null || req.name().trim().isEmpty()) {
            return false;
        }

        if (req.email() == null || !req.email().toLowerCase().matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$")) {
            return false;
        }

        if (req.password() == null || req.password().length() < 12 || req.password().length() > 128) {
            return false;
        }

        return true;
    }

    private boolean validateUpdateUserRequest(UpdateUserRequest req) {
        if (req == null) {
            return false;
        }

        if (req.name() == null || req.name().trim().isEmpty()) {
            return false;
        }

        if (req.email() == null || !req.email().toLowerCase().matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$")) {
            return false;
        }

        return true;
    }

    private boolean validateUpdatePasswordRequest(UpdatePasswordRequest req) {
        if (req == null) {
            return false;
        }

        if (req.password() == null || req.password().length() < 12 || req.password().length() > 128) {
            return false;
        }

        return true;
    }



}
