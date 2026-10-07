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
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Organizations", description = "Organizations are the top level of the Nzyme tenancy model. They hold "
        + "tenants, users, taps and their own configuration and quotas.")
public class OrganizationsResource extends UserAuthenticatedResource {

    private static final Logger LOG = LogManager.getLogger(OrganizationsResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Operation(operationId = "findOrganizations", summary = "List all organizations",
            description = "Returns all organizations of this Nzyme installation with their tenant, user and tap "
                    + "counts. The page size is limited to 250. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Organizations found.",
            content = @Content(schema = @Schema(implementation = OrganizationsListResponse.class)))
    @ApiResponse(responseCode = "401", description = "The requested page size is larger than 250.", content = @Content)
    public Response findAll(@Parameter(description = "Page size.") @QueryParam("limit") int limit, @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
    @Operation(operationId = "findOrganization", summary = "Get an organization",
            description = "Organization administrators can only request their own organization. Requires "
                    + "organization administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Organization found.",
            content = @Content(schema = @Schema(implementation = OrganizationDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization not found or not accessible by the calling user.", content = @Content)
    public Response find(@Parameter(hidden = true) @Context SecurityContext sc, @Parameter(description = "Organization UUID.") @PathParam("id") UUID id) {
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
    @Operation(operationId = "createOrganization", summary = "Create an organization",
            description = "Requires super administrator permissions.")
    @ApiResponse(responseCode = "201", description = "Organization created.", content = @Content)
    public Response create(@RequestBody(description = "Name and description of the new organization.", required = true, content = @Content(mediaType = "application/json")) @Valid CreateOrganizationRequest req) {
        nzyme.getAuthenticationService().createOrganization(
                req.name(),
                req.description()
        );

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/show/{id}")
    @Operation(operationId = "updateOrganization", summary = "Update an organization",
            description = "Changes name and description of an organization. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Organization updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization not found.", content = @Content)
    public Response update(@Parameter(description = "Organization UUID.") @PathParam("id") UUID id,
                           @RequestBody(description = "New name and description of the organization.", required = true, content = @Content(mediaType = "application/json")) @Valid UpdateOrganizationRequest req) {
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
    @Operation(operationId = "deleteOrganization", summary = "Delete an organization",
            description = "An organization can only be deleted after all of its tenants, users and taps are gone. "
                    + "Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Organization deleted.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The organization still has tenants, users or taps and cannot be deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization not found.", content = @Content)
    public Response delete(@Parameter(description = "Organization UUID.") @PathParam("id") UUID id) {
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
    @Operation(operationId = "findOrganizationSubsystemsConfiguration",
            summary = "Get subsystem configuration of an organization",
            description = "Returns which subsystems, Ethernet, WiFi, Bluetooth and UAV, are enabled for this "
                    + "organization and whether they are available system-wide. Requires super administrator "
                    + "permissions.",
            externalDocs = @ExternalDocumentation(description = "Subsystems in the Nzyme documentation",
                    url = "https://go.nzyme.org/subsystems"))
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = SubsystemsConfigurationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization not found.", content = @Content)
    public Response getOrganizationSubsystemConfiguration(@Parameter(description = "Organization UUID.") @PathParam("id") UUID id) {
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
    @Operation(operationId = "updateOrganizationSubsystemsConfiguration",
            summary = "Update subsystem configuration of an organization",
            description = "Enables or disables subsystems for this organization. A subsystem that is disabled "
                    + "system-wide cannot be enabled here. Enabling a subsystem here does not enable it for the "
                    + "tenants of the organization. Requires super administrator permissions.",
            externalDocs = @ExternalDocumentation(description = "Subsystems in the Nzyme documentation",
                    url = "https://go.nzyme.org/subsystems"))
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The subsystem is disabled system-wide.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization not found.", content = @Content)
    @ApiResponse(responseCode = "422", description = "No configuration values were passed or a value failed validation.", content = @Content)
    public Response updateOrganizationSubsystemConfiguration(@Parameter(description = "Organization UUID.") @PathParam("id") UUID id, @RequestBody(description = "Map of subsystem configuration keys and their new values.", required = true, content = @Content(mediaType = "application/json")) UpdateConfigurationRequest req) {
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
    @Operation(operationId = "findOrganizationQuotas", summary = "List quotas of an organization",
            description = "Returns every quota type with its configured limit and the current use. A limit of null "
                    + "means the quota is unlimited, which is the default for every quota. A quota can be exceeded "
                    + "if it was lowered below the current use. Requires organization administrator permissions.",
            externalDocs = @ExternalDocumentation(description = "Quotas in the Nzyme documentation",
                    url = "https://go.nzyme.org/quotas"))
    @ApiResponse(responseCode = "200", description = "Quotas found.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = QuotaDetailsResponse.class))))
    @ApiResponse(responseCode = "404", description = "Organization not found or not accessible by the calling user.", content = @Content)
    public Response getOrganizationQuotas(@Parameter(hidden = true) @Context SecurityContext sc, @Parameter(description = "Organization UUID.") @PathParam("id") UUID id) {
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
    @Operation(operationId = "updateOrganizationQuota", summary = "Set a quota of an organization",
            description = "Sets the limit of a single quota type. Pass a null quota to reset it back to unlimited. "
                    + "Lowering a quota below the current use does not delete anything, but the organization cannot "
                    + "create new entities of that type until it is back under the limit. Requires super "
                    + "administrator permissions.",
            externalDocs = @ExternalDocumentation(description = "Quotas in the Nzyme documentation",
                    url = "https://go.nzyme.org/quotas"))
    @ApiResponse(responseCode = "200", description = "Quota updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Unknown quota type.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization not found.", content = @Content)
    public Response setOrganizationQuota(@Parameter(description = "Organization UUID.") @PathParam("id") UUID id,
                                         @Parameter(description = "Quota type name.") @PathParam("quota_type") String quotaTypeParam,
                                         @RequestBody(description = "The new quota limit. Pass null to reset the quota back to unlimited.", required = true, content = @Content(mediaType = "application/json")) @Valid ConfigureQuotaRequest req) {
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
    @Operation(operationId = "findTenants", summary = "List tenants of an organization",
            description = "Returns all tenants of an organization with their user and tap counts. The page size is "
                    + "limited to 250. Requires organization administrator permissions.", tags = {"Tenants"})
    @ApiResponse(responseCode = "200", description = "Tenants found.",
            content = @Content(schema = @Schema(implementation = TenantsListResponse.class)))
    @ApiResponse(responseCode = "401", description = "The requested page size is larger than 250.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization not found or not accessible by the calling user.", content = @Content)
    public Response findTenantsOfOrganization(@Parameter(hidden = true) @Context SecurityContext sc,
                                              @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                              @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                              @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
    @Operation(operationId = "findOrganizationAdministrators", summary = "List organization administrators",
            description = "Returns all administrators of an organization. The page size is limited to 250. Requires "
                    + "organization administrator permissions.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Organization administrators found.",
            content = @Content(schema = @Schema(implementation = UsersListResponse.class)))
    @ApiResponse(responseCode = "401", description = "The requested page size is larger than 250.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization not found or not accessible by the calling user.", content = @Content)
    public Response findAllOrganizationAdministrators(@Parameter(hidden = true) @Context SecurityContext sc,
                                                      @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                      @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                                      @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
    @Operation(operationId = "findOrganizationAdministrator", summary = "Get an organization administrator",
            description = "Returns the administrator together with the API keys they own. Requires organization "
                    + "administrator permissions.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Organization administrator found.",
            content = @Content(schema = @Schema(implementation = OrganizationAdministratorDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization administrator not found or not accessible by the calling user.", content = @Content)
    public Response findOrganizationAdministrator(@Parameter(hidden = true) @Context SecurityContext sc,
                                                  @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                  @Parameter(description = "User UUID.") @PathParam("id") UUID userId) {
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
    @Operation(operationId = "createOrganizationAdministrator", summary = "Create an organization administrator",
            description = "The email address must be unique across the whole installation and the password must be "
                    + "between 12 and 128 characters long. Requires organization administrator permissions. Requires "
                    + "an interactive session; API keys are rejected.", tags = {"Users"})
    @ApiResponse(responseCode = "201", description = "Organization administrator created.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The request failed validation or the email address is already in use.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization not found or not accessible by the calling user.", content = @Content)
    public Response createOrganizationAdministrator(@Parameter(hidden = true) @Context SecurityContext sc,
                                                    @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                    @RequestBody(description = "Name, email address, password and MFA setting of the new organization administrator.", required = true, content = @Content(mediaType = "application/json")) @Valid CreateUserRequest req) {
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
    @Operation(operationId = "updateOrganizationAdministrator", summary = "Update an organization administrator",
            description = "Changes name, email address and the multi-factor authentication requirement. Turning "
                    + "multi-factor authentication off records a system event. Requires organization administrator "
                    + "permissions.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Organization administrator updated.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The request failed validation or the email address is already in use.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization administrator not found or not accessible by the calling user.", content = @Content)
    public Response editOrganizationAdministrator(@Parameter(hidden = true) @Context SecurityContext sc,
                                                  @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                  @Parameter(description = "User UUID.") @PathParam("id") UUID userId,
                                                  @RequestBody(description = "New name, email address and MFA setting of the organization administrator.", required = true, content = @Content(mediaType = "application/json")) @Valid UpdateUserRequest req) {
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
    @Operation(operationId = "updateOrganizationAdministratorPassword",
            summary = "Set password of an organization administrator",
            description = "All sessions of the administrator are invalidated and a system event is recorded. The "
                    + "password must be between 12 and 128 characters long. Requires organization administrator "
                    + "permissions. Requires an interactive session; API keys are rejected.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Password changed.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The password failed validation.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization administrator not found or not accessible by the calling user.", content = @Content)
    public Response editOrganizationAdministratorPassword(@Parameter(hidden = true) @Context SecurityContext sc,
                                                          @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                          @Parameter(description = "User UUID.") @PathParam("id") UUID userId,
                                                          @RequestBody(description = "The new password.", required = true, content = @Content(mediaType = "application/json")) @Valid UpdatePasswordRequest req) {
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
    @Operation(operationId = "deleteOrganizationAdministrator", summary = "Delete an organization administrator",
            description = "Administrators cannot delete themselves. Requires organization administrator "
                    + "permissions.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Organization administrator deleted.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The calling user tried to delete themselves.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization administrator not found or not accessible by the calling user.", content = @Content)
    public Response deleteOrganizationAdministrator(@Parameter(hidden = true) @Context SecurityContext sc,
                                                    @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                    @Parameter(description = "User UUID.") @PathParam("id") UUID userId) {
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
    @Operation(operationId = "resetOrganizationAdministratorMfa", summary = "Reset MFA of an organization administrator",
            description = "Removes the multi-factor authentication credentials so the administrator can enroll a "
                    + "new method on their next login. Records a system event. Requires organization administrator "
                    + "permissions. Requires an interactive session; API keys are rejected.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "MFA credentials reset.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization administrator not found or not accessible by the calling user.", content = @Content)
    public Response resetOrganizationAdministratorMFA(@Parameter(hidden = true) @Context SecurityContext sc,
                                                      @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                      @Parameter(description = "User UUID.") @PathParam("id") UUID userId) {
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
    @Operation(operationId = "findTenant", summary = "Get a tenant",
            description = "Returns the tenant with its user and tap counts and its timeout settings. Requires "
                    + "organization administrator permissions.", tags = {"Tenants"})
    @ApiResponse(responseCode = "200", description = "Tenant found.",
            content = @Content(schema = @Schema(implementation = TenantDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Tenant not found or not accessible by the calling user.", content = @Content)
    public Response findTenantOfOrganization(@Parameter(hidden = true) @Context SecurityContext sc,
                                             @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                             @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId) {
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
    @Operation(operationId = "createTenant", summary = "Create a tenant",
            description = "Session and multi-factor authentication timeouts are configured per tenant. Requires "
                    + "organization administrator permissions.", tags = {"Tenants"})
    @ApiResponse(responseCode = "201", description = "Tenant created.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization not found or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The tenant quota of the organization is exhausted.", content = @Content)
    public Response createTenant(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                 @RequestBody(description = "Name, description and timeout settings of the new tenant.", required = true, content = @Content(mediaType = "application/json")) @Valid CreateTenantRequest req) {
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
    @Operation(operationId = "updateTenant", summary = "Update a tenant",
            description = "Changes name, description and the session and multi-factor authentication timeouts. "
                    + "Requires organization administrator permissions.", tags = {"Tenants"})
    @ApiResponse(responseCode = "200", description = "Tenant updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response updateTenant(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                 @RequestBody(description = "New name, description and timeout settings of the tenant.", required = true, content = @Content(mediaType = "application/json")) @Valid UpdateTenantRequest req) {
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
    @Operation(operationId = "deleteTenant", summary = "Delete a tenant",
            description = "Requires organization administrator permissions.", tags = {"Tenants"})
    @ApiResponse(responseCode = "200", description = "Tenant deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteTenant(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId) {
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
    @Operation(operationId = "findTenantQuotas", summary = "List quotas of a tenant",
            description = "Returns every quota type with its configured limit and the current use. The tenants "
                    + "quota is left out because a tenant cannot have tenants. A limit of null means no tenant "
                    + "quota is configured, in which case the quota of the organization applies. Requires "
                    + "organization administrator permissions.", tags = {"Tenants"},
            externalDocs = @ExternalDocumentation(description = "Quotas in the Nzyme documentation",
                    url = "https://go.nzyme.org/quotas"))
    @ApiResponse(responseCode = "200", description = "Quotas found.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = QuotaDetailsResponse.class))))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response getTenantQuotas(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId) {
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
    @Operation(operationId = "updateTenantQuota", summary = "Set a quota of a tenant",
            description = "Sets the limit of a single quota type. Pass a null quota to remove the tenant quota, "
                    + "after which the quota of the organization applies. The quotas of all tenants of a type "
                    + "together cannot exceed the organization quota. Requires organization administrator "
                    + "permissions.", tags = {"Tenants"},
            externalDocs = @ExternalDocumentation(description = "Quotas in the Nzyme documentation",
                    url = "https://go.nzyme.org/quotas"))
    @ApiResponse(responseCode = "200", description = "Quota updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Unknown quota type.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The requested quota would exceed the organization quota.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response setTenantQuota(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                   @Parameter(description = "Quota type name.") @PathParam("quota_type") String quotaTypeParam,
                                   @RequestBody(description = "The new quota limit. Pass null to reset the quota back to unlimited.", required = true, content = @Content(mediaType = "application/json")) @Valid ConfigureQuotaRequest req) {
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
    @Operation(operationId = "findTenantSubsystemsConfiguration", summary = "Get subsystem configuration of a tenant",
            description = "Returns which subsystems, Ethernet, WiFi, Bluetooth and UAV, are enabled for this tenant "
                    + "and whether they are available in the organization. Requires organization administrator "
                    + "permissions.", tags = {"Tenants"},
            externalDocs = @ExternalDocumentation(description = "Subsystems in the Nzyme documentation",
                    url = "https://go.nzyme.org/subsystems"))
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = SubsystemsConfigurationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response getTenantSubsystemConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                                    @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                    @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId) {
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
    @Operation(operationId = "updateTenantSubsystemsConfiguration",
            summary = "Update subsystem configuration of a tenant",
            description = "Enables or disables subsystems for this tenant. A subsystem that is disabled for the "
                    + "organization cannot be enabled here. Disabling a subsystem hides its pages in the web "
                    + "interface and turns off its API resources for this tenant. Requires organization "
                    + "administrator permissions.", tags = {"Tenants"},
            externalDocs = @ExternalDocumentation(description = "Subsystems in the Nzyme documentation",
                    url = "https://go.nzyme.org/subsystems"))
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The subsystem is disabled for the organization.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "No configuration values were passed or a value failed validation.", content = @Content)
    public Response updateTenantSubsystemConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                                       @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                       @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                                       @RequestBody(description = "Map of subsystem configuration keys and their new values.", required = true, content = @Content(mediaType = "application/json")) UpdateConfigurationRequest req) {
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
    @Operation(operationId = "findTenantUsers", summary = "List users of a tenant",
            description = "Returns all users of a tenant with their feature and tap permissions. The page size is "
                    + "limited to 250. Requires organization administrator permissions.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Users found.",
            content = @Content(schema = @Schema(implementation = UsersListResponse.class)))
    @ApiResponse(responseCode = "401", description = "The requested page size is larger than 250, or organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization not accessible by the calling user.", content = @Content)
    public Response findAllUsersOfTenant(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                         @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                         @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                         @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
    @Operation(operationId = "findTenantUser", summary = "Get a user of a tenant",
            description = "Returns the user with their feature and tap permissions and the API keys they own. "
                    + "Requires organization administrator permissions.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "User found.",
            content = @Content(schema = @Schema(implementation = UserOfTenantDetailsResponse.class)))
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "User not found or not accessible by the calling user.", content = @Content)
    public Response findUserOfTenant(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                     @Parameter(description = "User UUID.") @PathParam("userId") UUID userId) {
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
    @Operation(operationId = "createTenantUser", summary = "Create a user of a tenant",
            description = "The email address must be unique across the whole installation and the password must be "
                    + "between 12 and 128 characters long. A new user has no permissions until you grant them. "
                    + "Requires organization administrator permissions. Requires an interactive session; API keys "
                    + "are rejected.", tags = {"Users"})
    @ApiResponse(responseCode = "201", description = "User created.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The request failed validation.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The email address is already in use.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The user quota of the tenant is exhausted.", content = @Content)
    public Response createUserOfTenant(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                       @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                       @RequestBody(description = "Name, email address, password and MFA setting of the new user.", required = true, content = @Content(mediaType = "application/json")) @Valid CreateUserRequest req) {
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
    @Operation(operationId = "updateTenantUser", summary = "Update a user of a tenant",
            description = "Changes name, email address and the multi-factor authentication requirement. Turning "
                    + "multi-factor authentication off records a system event. Requires organization administrator "
                    + "permissions.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "User updated.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found, the request failed validation, or the email address is already in use.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "User not found or not accessible by the calling user.", content = @Content)
    public Response editUserOfTenant(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                     @Parameter(description = "User UUID.") @PathParam("userId") UUID userId,
                                     @RequestBody(description = "New name, email address and MFA setting of the user.", required = true, content = @Content(mediaType = "application/json")) @Valid UpdateUserRequest req) {
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
    @Operation(operationId = "updateTenantUserTapPermissions", summary = "Set tap permissions of a user",
            description = "Replaces the list of taps this user can access. Tap UUIDs that do not belong to the "
                    + "tenant are ignored. Set the allow all flag to grant access to every current and future tap "
                    + "of the tenant. Requires organization administrator permissions.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Tap permissions updated.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "User not found or not accessible by the calling user.", content = @Content)
    public Response editUserOfTenantTapPermissions(@Parameter(hidden = true) @Context SecurityContext sc,
                                                   @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                   @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                                   @Parameter(description = "User UUID.") @PathParam("userId") UUID userId,
                                                   @RequestBody(description = "List of tap UUIDs the user may access and the flag that grants access to all taps of the tenant.", required = true, content = @Content(mediaType = "application/json")) UpdateUserTapPermissionsRequest req) {
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
    @Operation(operationId = "updateTenantUserPermissions", summary = "Set feature permissions of a user",
            description = "Replaces the feature permissions of this user with the passed permission ids. List the "
                    + "available ids with the permissions endpoint. Requires organization administrator "
                    + "permissions.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Permissions updated.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "User not found or not accessible by the calling user.", content = @Content)
    public Response editUserOfTenantPermissions(@Parameter(hidden = true) @Context SecurityContext sc,
                                                @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                                @Parameter(description = "User UUID.") @PathParam("userId") UUID userId,
                                                @RequestBody(description = "List of feature permission ids the user should have.", required = true, content = @Content(mediaType = "application/json")) UpdateUserPermissionsRequest req) {
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
    @Operation(operationId = "deleteTenantUser", summary = "Delete a user of a tenant",
            description = "Users cannot delete themselves. Requires organization administrator permissions.",
            tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "User deleted.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The calling user tried to delete themselves.", content = @Content)
    @ApiResponse(responseCode = "404", description = "User not found or not accessible by the calling user.", content = @Content)
    public Response deleteUserOfTenant(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                       @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                       @Parameter(description = "User UUID.") @PathParam("userId") UUID userId) {
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
    @Operation(operationId = "updateTenantUserPassword", summary = "Set password of a user of a tenant",
            description = "All sessions of the user are invalidated and a system event is recorded. The password "
                    + "must be between 12 and 128 characters long. Requires organization administrator permissions. "
                    + "Requires an interactive session; API keys are rejected.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Password changed.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found, or the password failed validation.", content = @Content)
    @ApiResponse(responseCode = "404", description = "User not found or not accessible by the calling user.", content = @Content)
    public Response editUserOfTenantPassword(@Parameter(hidden = true) @Context SecurityContext sc,
                                             @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                             @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                             @Parameter(description = "User UUID.") @PathParam("userId") UUID userId,
                                             @RequestBody(description = "The new password.", required = true, content = @Content(mediaType = "application/json")) UpdatePasswordRequest req) {
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
    @Operation(operationId = "resetTenantUserMfa", summary = "Reset MFA of a user of a tenant",
            description = "Removes the multi-factor authentication credentials so the user can enroll a new method "
                    + "on their next login. Records a system event. Requires organization administrator "
                    + "permissions. Requires an interactive session; API keys are rejected.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "MFA credentials reset.", content = @Content)
    @ApiResponse(responseCode = "404", description = "User not found or not accessible by the calling user.", content = @Content)
    public Response resetMFAOfUserOfTenant(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                           @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                           @Parameter(description = "User UUID.") @PathParam("userId") UUID userId) {
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
    @Operation(operationId = "findSessions", summary = "List all sessions",
            description = "Returns every session of this Nzyme installation, including sessions that have not "
                    + "passed multi-factor authentication yet. The page size is limited to 250. Requires super "
                    + "administrator permissions.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Sessions found.",
            content = @Content(schema = @Schema(implementation = SessionsListResponse.class)))
    @ApiResponse(responseCode = "401", description = "The requested page size is larger than 250.", content = @Content)
    public Response findAllSessions(@Parameter(description = "Page size.") @QueryParam("limit") int limit, @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
    @Operation(operationId = "findOrganizationSessions", summary = "List sessions of an organization",
            description = "The page size is limited to 250. Requires organization administrator permissions.",
            tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Sessions found.",
            content = @Content(schema = @Schema(implementation = SessionsListResponse.class)))
    @ApiResponse(responseCode = "401", description = "The requested page size is larger than 250.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization not found or not accessible by the calling user.", content = @Content)
    public Response findSessionsOfOrganization(@Parameter(hidden = true) @Context SecurityContext sc,
                                               @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                               @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                               @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
    @Operation(operationId = "findTenantSessions", summary = "List sessions of a tenant",
            description = "The page size is limited to 250. Requires organization administrator permissions.",
            tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Sessions found.",
            content = @Content(schema = @Schema(implementation = SessionsListResponse.class)))
    @ApiResponse(responseCode = "401", description = "The requested page size is larger than 250, or organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization not accessible by the calling user.", content = @Content)
    public Response findSessionsOfTenant(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                         @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                         @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                         @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
    @Operation(operationId = "invalidateSession", summary = "Invalidate a session",
            description = "Logs the user of this session out right away. Requires organization administrator "
                    + "permissions.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Session invalidated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Session not found or not accessible by the calling user.", content = @Content)
    public Response invalidateSession(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Session ID.") @PathParam("sessionId") long sessionId) {
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
    @Operation(operationId = "findTenantTaps", summary = "List taps of a tenant for management",
            description = "Returns all taps of a tenant with their secret and their location and floor assignment. "
                    + "The page size is limited to 250. Requires organization administrator permissions.",
            tags = {"Taps"})
    @ApiResponse(responseCode = "200", description = "Taps found.",
            content = @Content(schema = @Schema(implementation = TapPermissionsListResponse.class)))
    @ApiResponse(responseCode = "401", description = "The requested page size is larger than 250, or organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization not accessible by the calling user.", content = @Content)
    public Response findAllTapsOfTenant(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
    @Operation(operationId = "findTenantTap", summary = "Get a tap of a tenant for management",
            description = "Returns the tap with its secret and its location and floor assignment. Requires "
                    + "organization administrator permissions.", tags = {"Taps"})
    @ApiResponse(responseCode = "200", description = "Tap found.",
            content = @Content(schema = @Schema(implementation = TapPermissionDetailsResponse.class)))
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Tap not found or not accessible by the calling user.", content = @Content)
    public Response findTap(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                            @Parameter(description = "Tap UUID.") @PathParam("tapUuid") UUID tapId) {
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
    @Operation(operationId = "createTap", summary = "Create a tap",
            description = "Registers a new tap for a tenant and generates its secret. Configure the nzyme-tap "
                    + "process with that secret to connect the tap. Requires organization administrator "
                    + "permissions.", tags = {"Taps"})
    @ApiResponse(responseCode = "201", description = "Tap created.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The tap quota of the tenant is exhausted.", content = @Content)
    public Response createTap(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                              @RequestBody(description = "Name, description, location, floor and coordinates of the new tap.", required = true, content = @Content(mediaType = "application/json")) @Valid CreateTapRequest req) {
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
    @Operation(operationId = "updateTap", summary = "Update a tap",
            description = "Changes name, description, location, floor and coordinates of a tap. Assigning a tap to a "
                    + "location lets Nzyme group it with the other taps at the same site. Requires organization "
                    + "administrator permissions.", tags = {"Taps"},
            externalDocs = @ExternalDocumentation(description = "Locations in the Nzyme documentation",
                    url = "https://go.nzyme.org/locations"))
    @ApiResponse(responseCode = "200", description = "Tap updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Tap not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response editTap(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                            @Parameter(description = "Tap UUID.") @PathParam("tapUuid") UUID tapId,
                            @RequestBody(description = "New name, description, location, floor and coordinates of the tap.", required = true, content = @Content(mediaType = "application/json")) @Valid UpdateTapRequest req) {
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
    @Operation(operationId = "deleteTap", summary = "Delete a tap",
            description = "Deletes the tap and removes it from all monitors. Data the tap already reported stays. "
                    + "Requires organization administrator permissions.", tags = {"Taps"})
    @ApiResponse(responseCode = "200", description = "Tap deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Tap not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response deleteTap(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                              @Parameter(description = "Tap UUID.") @PathParam("tapUuid") UUID tapId) {
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
    @Operation(operationId = "cycleTapSecret", summary = "Cycle the secret of a tap",
            description = "Generates a new secret for the tap. The tap cannot connect again until you configure the "
                    + "nzyme-tap process with the new secret. Requires organization administrator permissions.",
            tags = {"Taps"})
    @ApiResponse(responseCode = "200", description = "Secret cycled.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Tap not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response cycleTapSecret(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                   @Parameter(description = "Tap UUID.") @PathParam("tapUuid") UUID tapId) {
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
    @Operation(operationId = "findTenantLocation", summary = "Get a location of a tenant",
            description = "Returns the location with its floor and tap counts. Requires organization administrator "
                    + "permissions.", tags = {"Locations"})
    @ApiResponse(responseCode = "200", description = "Location found.",
            content = @Content(schema = @Schema(implementation = TenantLocationDetailsResponse.class)))
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Location not found or not accessible by the calling user.", content = @Content)
    public Response findTenantLocation(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                       @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                       @Parameter(description = "Location UUID.") @PathParam("locationId") UUID locationId) {
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
    @Operation(operationId = "findTenantLocations", summary = "List locations of a tenant",
            description = "A location is a site such as a campus or a building. It groups the floors of that site "
                    + "and the taps placed on them, which makes data easier to read when a tenant has taps spread "
                    + "over several sites. Requires organization administrator permissions.", tags = {"Locations"},
            externalDocs = @ExternalDocumentation(description = "Locations in the Nzyme documentation",
                    url = "https://go.nzyme.org/locations"))
    @ApiResponse(responseCode = "200", description = "Locations found.",
            content = @Content(schema = @Schema(implementation = TenantLocationListResponse.class)))
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization not accessible by the calling user.", content = @Content)
    public Response findAllTenantLocations(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                           @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                           @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                           @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
    @Operation(operationId = "createTenantLocation", summary = "Create a location of a tenant",
            description = "Creates a location for a tenant and invalidates the environment data cache on all "
                    + "cluster nodes. Assign taps to the location afterwards to make use of it. Requires "
                    + "organization administrator permissions.", tags = {"Locations"},
            externalDocs = @ExternalDocumentation(description = "Locations in the Nzyme documentation",
                    url = "https://go.nzyme.org/locations"))
    @ApiResponse(responseCode = "201", description = "Location created.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization not accessible by the calling user.", content = @Content)
    public Response createTenantLocation(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @RequestBody(description = "Name, description and coordinates of the new location.", required = true, content = @Content(mediaType = "application/json")) @Valid CreateTenantLocationRequest req,
                                         @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                         @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId) {
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
    @Operation(operationId = "updateTenantLocation", summary = "Update a location of a tenant",
            description = "Invalidates the environment data cache on all cluster nodes. Requires organization "
                    + "administrator permissions.", tags = {"Locations"})
    @ApiResponse(responseCode = "200", description = "Location updated.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Location not found or not accessible by the calling user.", content = @Content)
    public Response updateTenantLocation(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @RequestBody(description = "New name, description and coordinates of the location.", required = true, content = @Content(mediaType = "application/json")) @Valid UpdateTenantLocationRequest req,
                                         @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                         @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                         @Parameter(description = "Location UUID.") @PathParam("locationId") UUID locationId) {
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

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{organizationId}/tenants/show/{tenantId}/locations/show/{locationId}")
    @Operation(operationId = "deleteTenantLocation", summary = "Delete a location of a tenant",
            description = "A location can only be deleted after all of its floors are gone. Requires organization "
                    + "administrator permissions.", tags = {"Locations"})
    @ApiResponse(responseCode = "200", description = "Location deleted.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The location still has floors and cannot be deleted.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Location not found or not accessible by the calling user.", content = @Content)
    public Response deleteTenantLocation(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                         @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                         @Parameter(description = "Location UUID.") @PathParam("locationId") UUID locationId) {
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
    @Operation(operationId = "updateTenantLocationEnvironmentalAlertEventing",
            summary = "Toggle environmental alert eventing of a location",
            description = "Controls whether severe environmental alerts of this location raise detection events "
                    + "and trigger the event actions subscribed to them. Environmental monitoring needs Nzyme "
                    + "Connect and a location with latitude and longitude. Requires organization administrator "
                    + "permissions.", tags = {"Locations"},
            externalDocs = @ExternalDocumentation(description = "Environmental monitoring in the Nzyme documentation",
                    url = "https://go.nzyme.org/environmental-monitoring"))
    @ApiResponse(responseCode = "200", description = "Setting updated.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Location not found or not accessible by the calling user.", content = @Content)
    public Response setEnvironmentalAlertEventingOfTenantLocation(@Parameter(hidden = true) @Context SecurityContext sc,
                                                                  @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                                  @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                                                  @Parameter(description = "Location UUID.") @PathParam("locationId") UUID locationId,
                                                                  @Parameter(description = "Set to true to enable eventing, false to disable it.") @PathParam("enabled") boolean enabled) {
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
    @Operation(operationId = "findTenantLocationFloors", summary = "List floors of a location",
            description = "Returns all floors with the taps placed on them and whether a floor plan has been "
                    + "uploaded. Requires organization administrator permissions.", tags = {"Locations"})
    @ApiResponse(responseCode = "200", description = "Floors found.",
            content = @Content(schema = @Schema(implementation = TenantLocationFloorListResponse.class)))
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Location not found or not accessible by the calling user.", content = @Content)
    public Response findAllFloorsOfTenantLocation(@Parameter(hidden = true) @Context SecurityContext sc,
                                                  @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                  @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                                  @Parameter(description = "Location UUID.") @PathParam("locationId") UUID locationId,
                                                  @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                                  @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
    @Operation(operationId = "findTenantLocationFloor", summary = "Get a floor of a location",
            description = "Returns the floor with the taps placed on it. Requires organization administrator "
                    + "permissions.", tags = {"Locations"})
    @ApiResponse(responseCode = "200", description = "Floor found.",
            content = @Content(schema = @Schema(implementation = TenantLocationFloorDetailsResponse.class)))
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Location or floor not found, or not accessible by the calling user.", content = @Content)
    public Response findFloorOfTenantLocation(@Parameter(hidden = true) @Context SecurityContext sc,
                                              @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                              @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                              @Parameter(description = "Location UUID.") @PathParam("locationId") UUID locationId,
                                              @Parameter(description = "Floor UUID.") @PathParam("floorId") UUID floorId) {
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
    @Operation(operationId = "createTenantLocationFloor", summary = "Create a floor of a location",
            description = "The floor number must be unique within the location. The path loss exponent describes "
                    + "how quickly signal strength drops on this floor and is used for trilateration. It is rounded "
                    + "to one decimal. Requires organization administrator permissions.", tags = {"Locations"},
            externalDocs = @ExternalDocumentation(description = "Trilateration in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-trilateration"))
    @ApiResponse(responseCode = "201", description = "Floor created.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found, or the location already has a floor with that number.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Location not found or not accessible by the calling user.", content = @Content)
    public Response createFloorOfTenantLocation(@Parameter(hidden = true) @Context SecurityContext sc,
                                                @RequestBody(description = "Number, name and path loss exponent of the new floor.", required = true, content = @Content(mediaType = "application/json")) @Valid CreateFloorOfTenantLocationRequest req,
                                                @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                                @Parameter(description = "Location UUID.") @PathParam("locationId") UUID locationId) {
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
    @Operation(operationId = "updateTenantLocationFloor", summary = "Update a floor of a location",
            description = "The floor number must stay unique within the location. Requires organization "
                    + "administrator permissions.", tags = {"Locations"})
    @ApiResponse(responseCode = "200", description = "Floor updated.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found, or the location already has a floor with that number.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Location or floor not found, or not accessible by the calling user.", content = @Content)
    public Response updateFloorOfTenantLocation(@Parameter(hidden = true) @Context SecurityContext sc,
                                                @RequestBody(description = "New number, name and path loss exponent of the floor.", required = true, content = @Content(mediaType = "application/json")) @Valid UpdateFloorOfTenantLocationRequest req,
                                                @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                                @Parameter(description = "Location UUID.") @PathParam("locationId") UUID locationId,
                                                @Parameter(description = "Floor UUID.") @PathParam("floorId") UUID floorId) {
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
    @Operation(operationId = "deleteTenantLocationFloor", summary = "Delete a floor of a location",
            description = "All taps placed on the floor are removed from it first. Requires organization "
                    + "administrator permissions.", tags = {"Locations"})
    @ApiResponse(responseCode = "200", description = "Floor deleted.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Location or floor not found, or not accessible by the calling user.", content = @Content)
    public Response deleteFloorOfTenantLocation(@Parameter(hidden = true) @Context SecurityContext sc,
                                                @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                                @Parameter(description = "Location UUID.") @PathParam("locationId") UUID locationId,
                                                @Parameter(description = "Floor UUID.") @PathParam("floorId") UUID floorId) {
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
    @Operation(operationId = "findTenantLocationFloorPlan", summary = "Get the floor plan of a floor",
            description = "Returns the floor plan image Base64 encoded, with its pixel dimensions and its real "
                    + "world width and length in meters. Requires organization administrator permissions.",
            tags = {"Locations"})
    @ApiResponse(responseCode = "200", description = "Floor plan found.",
            content = @Content(schema = @Schema(implementation = FloorPlanResponse.class)))
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Location or floor not found, no floor plan has been uploaded, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The stored floor plan image could not be read.", content = @Content)
    public Response findFloorPlan(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                  @Parameter(description = "Location UUID.") @PathParam("locationId") UUID locationId,
                                  @Parameter(description = "Floor UUID.") @PathParam("floorId") UUID floorId) {
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
    @Operation(operationId = "uploadTenantLocationFloorPlan", summary = "Upload a floor plan",
            description = "Takes a JPG or PNG file of up to 5MB as multipart form data and converts it to PNG. "
                    + "Pass the real world width and length of the floor in meters so Nzyme can translate pixels "
                    + "into distances. A floor plan with taps placed on it is what enables trilateration for the "
                    + "subsystems that support it. Requires organization administrator permissions.",
            tags = {"Locations"},
            externalDocs = @ExternalDocumentation(description = "Trilateration in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-trilateration"))
    @ApiResponse(responseCode = "201", description = "Floor plan uploaded.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The dimensions are not positive, or the file is empty, too large or not a readable JPG or PNG file.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Location or floor not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The uploaded file could not be read.", content = @Content)
    @RequestBody(description = "Multipart form with the floor plan image file.", required = true,
            content = @Content(mediaType = "multipart/form-data",
                    schemaProperties = @SchemaProperty(name = "plan",
                            schema = @Schema(type = "string", format = "binary", description = "The file to upload."))))
    public Response uploadFloorPlan(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(hidden = true) @FormDataParam("plan") InputStream planFile,
                                    @Parameter(hidden = true) @FormDataParam("width_meters") int widthMeters,
                                    @Parameter(hidden = true) @FormDataParam("length_meters") int lengthMeters,
                                    @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                    @Parameter(description = "Location UUID.") @PathParam("locationId") UUID locationId,
                                    @Parameter(description = "Floor UUID.") @PathParam("floorId") UUID floorId) {
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
    @Operation(operationId = "deleteTenantLocationFloorPlan", summary = "Delete the floor plan of a floor",
            description = "All taps placed on the floor are removed from it. Requires organization administrator "
                    + "permissions.", tags = {"Locations"})
    @ApiResponse(responseCode = "200", description = "Floor plan deleted.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Location or floor not found, or not accessible by the calling user.", content = @Content)
    public Response deleteFloorPlan(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                    @Parameter(description = "Location UUID.") @PathParam("locationId") UUID locationId,
                                    @Parameter(description = "Floor UUID.") @PathParam("floorId") UUID floorId) {
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
    @Operation(operationId = "placeTapOnFloor", summary = "Place a tap on a floor plan",
            description = "Sets the pixel coordinates of the tap on the floor plan. Nzyme uses the positions of the "
                    + "taps on a floor to trilaterate the position of signal sources. Requires organization "
                    + "administrator permissions.", tags = {"Locations"},
            externalDocs = @ExternalDocumentation(description = "Trilateration in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-trilateration"))
    @ApiResponse(responseCode = "200", description = "Tap placed on the floor.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Location, floor or tap not found, or not accessible by the calling user.", content = @Content)
    public Response placeTapOnFloor(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @RequestBody(description = "Pixel coordinates of the tap on the floor plan.", required = true, content = @Content(mediaType = "application/json")) @Valid PlaceTapRequest req,
                                    @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                    @Parameter(description = "Location UUID.") @PathParam("locationId") UUID locationId,
                                    @Parameter(description = "Floor UUID.") @PathParam("floorId") UUID floorId,
                                    @Parameter(description = "Tap UUID.") @PathParam("tapId") UUID tapId) {
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
    @Operation(operationId = "removeTapFromFloor", summary = "Remove a tap from a floor plan",
            description = "Requires organization administrator permissions.", tags = {"Locations"})
    @ApiResponse(responseCode = "200", description = "Tap removed from the floor.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Organization or tenant not found.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Location, floor or tap not found, or not accessible by the calling user.", content = @Content)
    public Response deleteTapFromFloor(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @PathParam("tenantId") UUID tenantId,
                                    @Parameter(description = "Location UUID.") @PathParam("locationId") UUID locationId,
                                    @Parameter(description = "Floor UUID.") @PathParam("floorId") UUID floorId,
                                    @Parameter(description = "Tap UUID.") @PathParam("tapId") UUID tapId) {
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
    @Operation(operationId = "findOrganizationEventActions", summary = "List event actions of an organization",
            description = "Returns all event actions of an organization with the system event types and detection "
                    + "types each action is subscribed to. Actions created in an organization can be subscribed to "
                    + "detection events and to the system events of that organization. Requires organization "
                    + "administrator permissions.", tags = {"Event Actions"},
            externalDocs = @ExternalDocumentation(description = "Subscriptions and actions in the Nzyme documentation",
                    url = "https://go.nzyme.org/detection-alerts-subscriptions"))
    @ApiResponse(responseCode = "200", description = "Event actions found.",
            content = @Content(schema = @Schema(implementation = EventActionsListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization not found or not accessible by the calling user.", content = @Content)
    public Response findAllEventActionsOfOrganization(@Parameter(hidden = true) @Context SecurityContext sc,
                                                      @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                      @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                                      @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
    @Operation(operationId = "findOrganizationEventAction", summary = "Get an event action of an organization",
            description = "Returns the event action with the system event types and detection types it is "
                    + "subscribed to. Requires organization administrator permissions.", tags = {"Event Actions"})
    @ApiResponse(responseCode = "200", description = "Event action found.",
            content = @Content(schema = @Schema(implementation = EventActionDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or event action not found, or not accessible by the calling user.", content = @Content)
    public Response findEventActionOfOrganization(@Parameter(hidden = true) @Context SecurityContext sc,
                                                  @Parameter(description = "Organization UUID.") @PathParam("organizationId") UUID organizationId,
                                                  @Parameter(description = "Event action UUID.") @PathParam("actionId") UUID actionId) {
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
    @Operation(operationId = "findGlobalAuthenticationConfiguration",
            summary = "Get global authentication configuration",
            description = "Returns the session, session inactivity and multi-factor authentication timeouts that "
                    + "apply to super administrators. Requires super administrator permissions.", tags = {"System"})
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = SuperadminSettingsResponse.class)))
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
    @Operation(operationId = "updateGlobalAuthenticationConfiguration",
            summary = "Update global authentication configuration",
            description = "Requires super administrator permissions.", tags = {"System"})
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "422", description = "A configuration value failed validation.", content = @Content)
    public Response setGlobalSuperAdministratorConfiguration(@RequestBody(description = "Map of authentication configuration keys and their new values.", required = true, content = @Content(mediaType = "application/json")) @Valid SuperadminSettingsUpdateRequest ur) {
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
    @Operation(operationId = "findSuperAdministrators", summary = "List super administrators",
            description = "The page size is limited to 250. Requires super administrator permissions.",
            tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Super administrators found.",
            content = @Content(schema = @Schema(implementation = UsersListResponse.class)))
    @ApiResponse(responseCode = "401", description = "The requested page size is larger than 250.", content = @Content)
    public Response findAllSuperAdministrators(@Parameter(description = "Page size.") @QueryParam("limit") int limit, @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
    @Operation(operationId = "findSuperAdministrator", summary = "Get a super administrator",
            description = "Returns the super administrator with the API keys they own. The last remaining super "
                    + "administrator and the calling user themselves are reported as not deletable. Requires super "
                    + "administrator permissions.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Super administrator found.",
            content = @Content(schema = @Schema(implementation = SuperAdministratorDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Super administrator not found.", content = @Content)
    public Response findSuperAdministrator(@Parameter(hidden = true) @Context SecurityContext sc, @Parameter(description = "User UUID.") @PathParam("id") UUID userId) {
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
    @Operation(operationId = "createSuperAdministrator", summary = "Create a super administrator",
            description = "The email address must be unique across the whole installation and the password must be "
                    + "between 12 and 128 characters long. Records a system event. Requires super administrator "
                    + "permissions. Requires an interactive session; API keys are rejected.", tags = {"Users"})
    @ApiResponse(responseCode = "201", description = "Super administrator created.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The request failed validation or the email address is already in use.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response createSuperAdministrator(@RequestBody(description = "Name, email address, password and MFA setting of the new super administrator.", required = true, content = @Content(mediaType = "application/json")) @Valid CreateUserRequest req) {
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
    @Operation(operationId = "updateSuperAdministrator", summary = "Update a super administrator",
            description = "Changes name, email address and the multi-factor authentication requirement. Turning "
                    + "multi-factor authentication off records a system event. Requires super administrator "
                    + "permissions.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Super administrator updated.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The request failed validation or the email address is already in use.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Super administrator not found.", content = @Content)
    public Response editSuperAdministrator(@Parameter(description = "User UUID.") @PathParam("userId") UUID userId,
                                           @RequestBody(description = "New name, email address and MFA setting of the super administrator.", required = true, content = @Content(mediaType = "application/json")) @Valid UpdateUserRequest req) {
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
    @Operation(operationId = "updateSuperAdministratorPassword", summary = "Set password of a super administrator",
            description = "All sessions of the super administrator are invalidated and a system event is recorded. "
                    + "The password must be between 12 and 128 characters long. Requires super administrator "
                    + "permissions. Requires an interactive session; API keys are rejected.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Password changed.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The password failed validation.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Super administrator not found.", content = @Content)
    public Response editSuperAdministratorPassword(@Parameter(hidden = true) @Context SecurityContext sc,
                                                   @Parameter(description = "User UUID.") @PathParam("userId") UUID userId,
                                                   @RequestBody(description = "The new password.", required = true, content = @Content(mediaType = "application/json")) UpdatePasswordRequest req) {
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
    @Operation(operationId = "deleteSuperAdministrator", summary = "Delete a super administrator",
            description = "Super administrators cannot delete themselves and the last remaining super "
                    + "administrator cannot be deleted. Records a system event. Requires super administrator "
                    + "permissions.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Super administrator deleted.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The calling user tried to delete themselves or the last remaining super administrator.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Super administrator not found.", content = @Content)
    public Response deleteSuperAdministrator(@Parameter(hidden = true) @Context SecurityContext sc, @Parameter(description = "User UUID.") @PathParam("id") UUID userId) {
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
    @Operation(operationId = "resetSuperAdministratorMfa", summary = "Reset MFA of a super administrator",
            description = "Removes the multi-factor authentication credentials so the super administrator can "
                    + "enroll a new method on their next login. Records a system event. Requires super "
                    + "administrator permissions. Requires an interactive session; API keys are rejected.",
            tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "MFA credentials reset.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Super administrator not found.", content = @Content)
    public Response resetSuperAdministratorMFA(@Parameter(hidden = true) @Context SecurityContext sc, @Parameter(description = "User UUID.") @PathParam("id") UUID userId) {
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
    @Operation(operationId = "findPermissions", summary = "List all assignable feature permissions",
            description = "Returns every feature permission with its id, name, description and whether it respects "
                    + "the tap scope of a user. Use these ids when you set the permissions of a user. Requires "
                    + "organization administrator permissions.", tags = {"Users"})
    @ApiResponse(responseCode = "200", description = "Permissions found.",
            content = @Content(schema = @Schema(implementation = PermissionListResponse.class)))
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
