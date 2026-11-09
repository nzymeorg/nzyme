package app.nzyme.core.rest.resources.system;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.DataCategory;
import app.nzyme.core.database.DatabaseTools;
import app.nzyme.core.database.tasks.GlobalPurgeCategoryTask;
import app.nzyme.core.database.tasks.OrganizationPurgeCategoryTask;
import app.nzyme.core.database.tasks.TenantPurgeCategoryTask;
import app.nzyme.core.database.maintenance.DatabaseMaintenance;
import app.nzyme.core.database.maintenance.db.MaintenanceLogEntry;
import app.nzyme.core.database.usage.DatabaseUsage;
import app.nzyme.core.dot11.Dot11RegistryKeys;
import app.nzyme.core.ethernet.EthernetRegistryKeys;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.SetDatabaseCategoryRetentionTimeRequest;
import app.nzyme.core.rest.responses.bluetooth.BluetoothRegistryKeys;
import app.nzyme.core.rest.responses.system.database.*;
import app.nzyme.core.security.authentication.db.OrganizationEntry;
import app.nzyme.core.security.authentication.db.TenantEntry;
import app.nzyme.core.timelines.TimelinesRegistryKeys;
import app.nzyme.core.uav.UavRegistryKeys;
import app.nzyme.core.assets.AssetRegistryKeys;
import app.nzyme.core.assets.identity.AssetIdentityRegistryKeys;
import app.nzyme.plugin.Subsystem;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/api/system/database")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Database", description = "Shows how much data Nzyme stores per data category, and lets you purge data "
        + "or change its retention time.",
        externalDocs = @ExternalDocumentation(description = "Retention times in the Nzyme documentation",
                url = "https://go.nzyme.org/retention-time"))
public class DatabaseResource extends UserAuthenticatedResource {

    private static final Logger LOG = LogManager.getLogger(DatabaseResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/sizes/global")
    @Operation(operationId = "findGlobalDatabaseSizes", summary = "Get database sizes of the whole cluster",
            description = "Returns the size on disk and the row count of every data category and of every "
                    + "subsystem, broken down by organization and tenant, from the last usage snapshot. Global sizes "
                    + "are measured. Organization and tenant sizes are apportioned by row share and flagged as "
                    + "estimates. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Database sizes found.",
            content = @Content(schema = @Schema(implementation = GlobalDatabaseCategoriesResponse.class)))
    public Response globalDatabaseSizes() {
        DatabaseUsage.Snapshot snapshot = nzyme.getDatabaseMaintenance().usage().getSnapshot();
        boolean calculating = nzyme.getDatabaseMaintenance().isUsageCalculationPending();

        Map<String, DataCategorySizesResponse> globalSizes = Maps.newHashMap();
        for (DataCategory category : DataCategory.values()) {
            globalSizes.put(category.name(), sizes(snapshot, DatabaseUsage.Scope.GLOBAL, null, null, category));
        }

        List<OrganizationDataCategoriesResponse> organizations = Lists.newArrayList();
        for (OrganizationEntry org : nzyme.getAuthenticationService().findAllOrganizations()) {
            organizations.add(organizationResponse(snapshot, org, calculating));
        }

        return Response.ok(GlobalDatabaseCategoriesResponse.create(
                globalSizes, bySubsystem(globalSizes), organizations, snapshot.estimated() ? null : snapshot.calculatedAt(),
                snapshot.estimated(), calculating, calculationError()
        )).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/sizes/organization/{organization_id}")
    @Operation(operationId = "findOrganizationDatabaseSizes", summary = "Get database sizes of an organization",
            description = "Returns the row count and apportioned size on disk of every data category and subsystem "
                    + "of an organization, broken down by tenant, from the last usage snapshot. Requires organization "
                    + "administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Database sizes found.",
            content = @Content(schema = @Schema(implementation = OrganizationDataCategoriesResponse.class)))
    @ApiResponse(responseCode = "403", description = "The calling user cannot administer this organization.",
            content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization not found.", content = @Content)
    public Response organizationDatabaseSizes(@Parameter(hidden = true) @Context SecurityContext sc,
                                              @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (authenticatedUser.getOrganizationId() != null
                && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Optional<OrganizationEntry> org = nzyme.getAuthenticationService().findOrganization(organizationId);

        if (org.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DatabaseUsage.Snapshot snapshot = nzyme.getDatabaseMaintenance().usage().getSnapshot();
        boolean calculating = nzyme.getDatabaseMaintenance().isUsageCalculationPending();

        return Response.ok(organizationResponse(snapshot, org.get(), calculating)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/sizes/organization/{organization_id}/tenants/{tenant_id}")
    @Operation(operationId = "findTenantDatabaseSizes", summary = "Get database sizes of a tenant",
            description = "Returns the row count, apportioned size on disk and configured retention time of every "
                    + "data category of a tenant, plus sums per subsystem, from the last usage snapshot. Requires "
                    + "organization administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Database sizes found.",
            content = @Content(schema = @Schema(implementation = TenantDataCategoriesResponse.class)))
    @ApiResponse(responseCode = "403", description = "Organization or tenant not accessible by the calling user.",
            content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found.", content = @Content)
    public Response tenantDatabaseSizes(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                        @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId){
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Optional<TenantEntry> tenant = nzyme.getAuthenticationService().findTenant(tenantId);
        Optional<OrganizationEntry> org = nzyme.getAuthenticationService().findOrganization(organizationId);

        if (tenant.isEmpty() || org.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DatabaseUsage.Snapshot snapshot = nzyme.getDatabaseMaintenance().usage().getSnapshot();
        boolean calculating = nzyme.getDatabaseMaintenance().isUsageCalculationPending();

        return Response.ok(tenantResponse(snapshot, org.get(), tenant.get(), calculating)).build();
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/usage/recalculate")
    @Operation(operationId = "recalculateDatabaseUsage", summary = "Recalculate the database usage snapshot",
            description = "Queues a fresh count of all data categories for every organization and tenant. The "
                    + "count runs in the background as a database maintenance task and takes a few seconds on "
                    + "large databases. It also runs once an hour and after every purge. Manual requests are "
                    + "accepted at most once every " + DatabaseMaintenance.MANUAL_USAGE_COOLDOWN_MINUTES + " minutes "
                    + "across the cluster. Requires organization administrator permissions.")
    @ApiResponse(responseCode = "202", description = "Calculation queued.", content = @Content)
    @ApiResponse(responseCode = "409", description = "A calculation is already queued or running.", content = @Content)
    @ApiResponse(responseCode = "429", description = "The last calculation finished less than "
            + DatabaseMaintenance.MANUAL_USAGE_COOLDOWN_MINUTES + " minutes ago.", content = @Content)
    public Response recalculateUsage() {
        return switch (nzyme.getDatabaseMaintenance().requestUsageCalculation(true)) {
            case QUEUED -> Response.status(Response.Status.ACCEPTED).build();
            case TOO_SOON -> Response.status(429).build();
            default -> Response.status(Response.Status.CONFLICT).build();
        };
    }

    @POST
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/purge/category/{category}")
    @Operation(operationId = "purgeGlobalDataCategory", summary = "Purge a data category cluster-wide",
            description = "Submits a task that deletes all data of a category across every organization and tenant. "
                    + "The task runs in the background, so a successful response only means the request was queued. "
                    + "This cannot be undone. Requires super administrator permissions.")
    @ApiResponse(responseCode = "202", description = "Purge task queued.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Unknown data category.", content = @Content)
    public Response purgeGlobalCategory(@Parameter(description = "Data category name, for example DOT11 or "
            + "ETHERNET_DNS. Not case sensitive.") @PathParam("category") String categoryParam) {
        DataCategory category;
        try {
            category = DataCategory.valueOf(categoryParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.info("Submitting tasks to globally purge data category [{}] on API request.", category);
        nzyme.getTasksQueue().publish(new GlobalPurgeCategoryTask(category, DateTime.now()));
        return Response.status(Response.Status.ACCEPTED).build();
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/purge/organization/{organization_id}/category/{category}")
    @Operation(operationId = "purgeOrganizationDataCategory", summary = "Purge a data category of an organization",
            description = "Submits a task that deletes all data of a category for every tenant of an organization. "
                    + "The task runs in the background, so a successful response only means the request was queued. "
                    + "This cannot be undone. Requires organization administrator permissions.")
    @ApiResponse(responseCode = "202", description = "Purge task queued.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The calling user cannot administer this organization.",
            content = @Content)
    @ApiResponse(responseCode = "404", description = "Unknown data category.", content = @Content)
    public Response purgeOrganizationCategory(@Parameter(hidden = true) @Context SecurityContext sc,
                                              @Parameter(description = "Data category name, for example DOT11 or ETHERNET_DNS. Not case sensitive.") @PathParam("category") String categoryParam,
                                              @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (authenticatedUser.getOrganizationId() != null
                && !authenticatedUser.getOrganizationId().equals(organizationId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        DataCategory category;
        try {
            category = DataCategory.valueOf(categoryParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.info("Submitting tasks to purge data category [{}] of organization [{}] on API request.",
                category, organizationId);

        nzyme.getTasksQueue().publish(new OrganizationPurgeCategoryTask(category, organizationId, DateTime.now()));
        return Response.status(Response.Status.ACCEPTED).build();
    }

    @POST
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/purge/organization/{organization_id}/tenant/{tenant_id}/category/{category}")
    @Operation(operationId = "purgeTenantDataCategory", summary = "Purge a data category of a tenant",
            description = "Submits a task that deletes all data of a category for a single tenant. The task runs in "
                    + "the background, so a successful response only means the request was queued. This cannot be "
                    + "undone. Requires organization administrator permissions.")
    @ApiResponse(responseCode = "202", description = "Purge task queued.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Organization or tenant not accessible by the calling user.",
            content = @Content)
    @ApiResponse(responseCode = "404", description = "Unknown data category.", content = @Content)
    public Response purgeTenantCategory(@Parameter(hidden = true) @Context SecurityContext sc,
                                              @Parameter(description = "Data category name, for example DOT11 or ETHERNET_DNS. Not case sensitive.") @PathParam("category") String categoryParam,
                                              @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                              @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        DataCategory category;
        try {
            category = DataCategory.valueOf(categoryParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.info("Submitting tasks to purge data category [{}] of tenant [{}] on API request.",
                category, tenantId);

        nzyme.getTasksQueue().publish(new TenantPurgeCategoryTask(category, organizationId, tenantId, DateTime.now()));
        return Response.status(Response.Status.ACCEPTED).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/configuration/organization/{organization_id}/tenant/{tenant_id}/category/{category}/retention")
    @Operation(operationId = "updateTenantDataCategoryRetentionTime",
            summary = "Set the retention time of a data category",
            description = "Sets how many days Nzyme keeps data of a category for a tenant. The new retention time "
                    + "applies immediately and needs no restart, but a background task only purges expired data "
                    + "shortly after node startup and every 60 minutes after that. Requires organization "
                    + "administrator permissions.",
            externalDocs = @ExternalDocumentation(description = "Retention times in the Nzyme documentation",
                    url = "https://go.nzyme.org/retention-time"))
    @ApiResponse(responseCode = "202", description = "Retention time updated.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Organization or tenant not accessible by the calling user.",
            content = @Content)
    @ApiResponse(responseCode = "404", description = "Unknown data category.", content = @Content)
    public Response setCategoryRetentionTime(@Parameter(hidden = true) @Context SecurityContext sc,
                                             @RequestBody(description = "New retention time in days.", required = true, content = @Content(mediaType = "application/json"))
                                             @Valid SetDatabaseCategoryRetentionTimeRequest req,
                                             @Parameter(description = "Data category name, for example DOT11 or ETHERNET_DNS. Not case sensitive.") @PathParam("category") String categoryParam,
                                             @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                             @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        DataCategory category;
        try {
            category = DataCategory.valueOf(categoryParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.info("Setting retention time of data category [{}] of tenant [{}] to [{}] on API request.",
                category, tenantId, req.retentionTimeDays());

        String key = null;
        switch (category) {
            case DOT11 -> key = Dot11RegistryKeys.DOT11_RETENTION_TIME_DAYS.key();
            case BLUETOOTH -> key = BluetoothRegistryKeys.BLUETOOTH_RETENTION_TIME_DAYS.key();
            case ETHERNET_L4 -> key = EthernetRegistryKeys.L4_RETENTION_TIME_DAYS.key();
            case ETHERNET_DNS -> key = EthernetRegistryKeys.DNS_RETENTION_TIME_DAYS.key();
            case PORTAL_INTEGRITY -> key = EthernetRegistryKeys.PORTAL_INTEGRITY_RETENTION_TIME_DAYS.key();
            case TLS_CERTIFICATES -> key = EthernetRegistryKeys.TLS_CERTIFICATES_RETENTION_TIME_DAYS.key();
            case UAV -> key = UavRegistryKeys.UAV_RETENTION_TIME_DAYS.key();
            case TIMELINE_EVENTS_DOT11 -> key = TimelinesRegistryKeys.DOT11_EVENTS_RETENTION_TIME_DAYS.key();
            case ASSET_ACTIVITY -> key = AssetRegistryKeys.ASSETS_ACTIVITY_RETENTION_TIME_DAYS.key();
            case ASSET_ATTRIBUTES -> key = AssetRegistryKeys.ASSETS_ATTRIBUTES_RETENTION_TIME_DAYS.key();
            case ASSET_COMMUNICATION -> key = AssetRegistryKeys.ASSETS_COMMUNICATION_RETENTION_TIME_DAYS.key();
            case ASSET_STATISTICS -> key = AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS.key();
            case ASSET_IDENTITY -> key = AssetIdentityRegistryKeys.RETENTION_TIME_DAYS.key();
        }

        nzyme.getDatabaseCoreRegistry()
                .setValue(key, String.valueOf(req.retentionTimeDays()), organizationId, tenantId);

        return Response.status(Response.Status.ACCEPTED).build();
    }

    private OrganizationDataCategoriesResponse organizationResponse(DatabaseUsage.Snapshot snapshot,
                                                                    OrganizationEntry org,
                                                                    boolean calculating) {
        Map<String, DataCategorySizesResponse> orgSizes = Maps.newHashMap();
        for (DataCategory category : DataCategory.values()) {
            orgSizes.put(category.name(), sizes(snapshot, DatabaseUsage.Scope.ORGANIZATION, org.uuid(), null, category));
        }

        List<TenantDataCategoriesResponse> tenants = Lists.newArrayList();
        for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfOrganization(org.uuid())) {
            tenants.add(tenantResponse(snapshot, org, tenant, calculating));
        }

        return OrganizationDataCategoriesResponse.create(
                org.uuid(), org.name(), orgSizes, bySubsystem(orgSizes), tenants,
                snapshot.estimated() ? null : snapshot.calculatedAt(), calculating, calculationError()
        );
    }

    private TenantDataCategoriesResponse tenantResponse(DatabaseUsage.Snapshot snapshot,
                                                        OrganizationEntry org,
                                                        TenantEntry tenant,
                                                        boolean calculating) {
        Map<String, DataCategorySizesAndConfigurationResponse> tenantSizes = Maps.newHashMap();
        Map<String, DataCategorySizesResponse> plainSizes = Maps.newHashMap();
        for (DataCategory category : DataCategory.values()) {
            DataCategorySizesResponse sizes = sizes(snapshot, DatabaseUsage.Scope.TENANT, org.uuid(), tenant.uuid(), category);
            plainSizes.put(category.name(), sizes);
            tenantSizes.put(category.name(), DataCategorySizesAndConfigurationResponse.create(
                    sizes.bytes(),
                    sizes.rows(),
                    sizes.rowsEstimated(),
                    sizes.bytesEstimated(),
                    DatabaseTools.getDataCategoryRetentionTimeDays(nzyme, category, org.uuid(), tenant.uuid())
            ));
        }

        return TenantDataCategoriesResponse.create(
                tenant.uuid(), tenant.name(), org.uuid(), org.name(), tenantSizes, bySubsystem(plainSizes),
                snapshot.estimated() ? null : snapshot.calculatedAt(), calculating, calculationError()
        );
    }

    // A sum is null as soon as one category has no figure yet, so a partial sum never looks like a complete one.
    // The failure message of the most recent usage calculation, if that one failed. Null otherwise.
    @Nullable
    private String calculationError() {
        return nzyme.getDatabaseMaintenance().findLastLogEntry(DatabaseMaintenance.ActionType.USAGE_STATISTICS)
                .filter(e -> e.success() != null && !e.success())
                .map(MaintenanceLogEntry::details)
                .orElse(null);
    }

    private Map<String, DataCategorySizesResponse> bySubsystem(Map<String, DataCategorySizesResponse> categories) {
        Map<String, DataCategorySizesResponse> result = Maps.newHashMap();

        for (Subsystem subsystem : Subsystem.values()) {
            Long bytes = 0L;
            Long rows = 0L;
            boolean rowsEstimated = false;
            boolean bytesEstimated = false;
            boolean any = false;

            for (DataCategory category : DataCategory.values()) {
                if (category.getSubsystem() != subsystem) {
                    continue;
                }

                any = true;
                DataCategorySizesResponse c = categories.get(category.name());

                bytes = (bytes == null || c.bytes() == null) ? null : bytes + c.bytes();
                rows = (rows == null || c.rows() == null) ? null : rows + c.rows();
                rowsEstimated |= c.rowsEstimated();
                bytesEstimated |= c.bytesEstimated();
            }

            if (any) {
                result.put(subsystem.name(), DataCategorySizesResponse.create(bytes, rows, rowsEstimated, bytesEstimated));
            }
        }

        return result;
    }

    private DataCategorySizesResponse sizes(DatabaseUsage.Snapshot snapshot,
                                            DatabaseUsage.Scope scope,
                                            @Nullable UUID organizationId,
                                            @Nullable UUID tenantId,
                                            DataCategory category) {
        Optional<DatabaseUsage.Entry> entry = snapshot.find(scope, organizationId, tenantId, category);

        return DataCategorySizesResponse.create(
                entry.map(DatabaseUsage.Entry::bytes).orElse(null),
                entry.map(DatabaseUsage.Entry::rows).orElse(null),
                entry.map(DatabaseUsage.Entry::rowsEstimated).orElse(false),
                entry.map(DatabaseUsage.Entry::bytesEstimated).orElse(false)
        );
    }

}
