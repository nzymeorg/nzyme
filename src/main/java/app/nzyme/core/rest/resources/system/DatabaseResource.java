package app.nzyme.core.rest.resources.system;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.DataCategory;
import app.nzyme.core.database.DataTableInformation;
import app.nzyme.core.database.DatabaseImpl;
import app.nzyme.core.database.DatabaseTools;
import app.nzyme.core.database.tasks.GlobalPurgeCategoryTask;
import app.nzyme.core.database.tasks.OrganizationPurgeCategoryTask;
import app.nzyme.core.database.tasks.TenantPurgeCategoryTask;
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
import app.nzyme.core.util.Tools;
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
            description = "Returns the total size on disk and the row count of every data category, broken down by "
                    + "organization and tenant. Only the global entries carry a size on disk; the organization and "
                    + "tenant entries carry row counts and retention times. Requires super administrator "
                    + "permissions.")
    @ApiResponse(responseCode = "200", description = "Database sizes found.",
            content = @Content(schema = @Schema(implementation = GlobalDatabaseCategoriesResponse.class)))
    public Response globalDatabaseSizes() {
        // Global/Total database size.
        Map<String, DataCategorySizesResponse> globalSizes = Maps.newHashMap();
        for (DataCategory category : DataCategory.values()) {
            globalSizes.put(category.name(), DataCategorySizesResponse.create(
                    calculateDataCategorySize(category),
                    countDataCategoryRows(category, null, null))
            );
        }

        // Organizations.
        List<OrganizationDataCategoriesResponse> organizations = Lists.newArrayList();
        for (OrganizationEntry org : nzyme.getAuthenticationService().findAllOrganizations()) {
            Map<String, DataCategorySizesResponse> orgSizes = Maps.newHashMap();
            for (DataCategory category : DataCategory.values()) {
                orgSizes.put(category.name(), DataCategorySizesResponse.create(
                        null, countDataCategoryRows(category, org.uuid(), null)
                ));
            }

            // Tenants.
            List<TenantDataCategoriesResponse> tenants = Lists.newArrayList();
            for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfOrganization(org.uuid())) {
                Map<String, DataCategorySizesAndConfigurationResponse> tenantSizes = Maps.newHashMap();

                for (DataCategory category : DataCategory.values()) {
                    tenantSizes.put(category.name(), DataCategorySizesAndConfigurationResponse.create(
                            null,
                            countDataCategoryRows(category, org.uuid(), tenant.uuid()),
                            DatabaseTools.getDataCategoryRetentionTimeDays(nzyme, category, org.uuid(), tenant.uuid())
                    ));
                }

                tenants.add(TenantDataCategoriesResponse.create(
                        tenant.uuid(),
                        tenant.name(),
                        org.uuid(),
                        org.name(),
                        tenantSizes
                ));
            }

            organizations.add(OrganizationDataCategoriesResponse.create(
                    org.uuid(),
                    org.name(),
                    orgSizes,
                    tenants
            ));
        }

        return Response.ok(GlobalDatabaseCategoriesResponse.create(globalSizes, organizations)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/sizes/organization/{organization_id}")
    @Operation(operationId = "findOrganizationDatabaseSizes", summary = "Get database sizes of an organization",
            description = "Returns the row count of every data category of an organization, broken down by tenant. "
                    + "Sizes on disk are not reported at this level. Requires organization administrator "
                    + "permissions.")
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

        Map<String, DataCategorySizesResponse> orgSizes = Maps.newHashMap();
        for (DataCategory category : DataCategory.values()) {
            orgSizes.put(category.name(), DataCategorySizesResponse.create(
                    null, countDataCategoryRows(category, org.get().uuid(), null)
            ));
        }

        // Tenants.
        List<TenantDataCategoriesResponse> tenants = Lists.newArrayList();
        for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfOrganization(org.get().uuid())) {
            Map<String, DataCategorySizesAndConfigurationResponse> tenantSizes = Maps.newHashMap();

            for (DataCategory category : DataCategory.values()) {
                tenantSizes.put(category.name(), DataCategorySizesAndConfigurationResponse.create(
                        null,
                        countDataCategoryRows(category, org.get().uuid(), tenant.uuid()),
                        DatabaseTools.getDataCategoryRetentionTimeDays(nzyme, category, org.get().uuid(), tenant.uuid())
                ));
            }

            tenants.add(TenantDataCategoriesResponse.create(
                    tenant.uuid(),
                    tenant.name(),
                    org.get().uuid(),
                    org.get().name(),
                    tenantSizes
            ));
        }

        return Response.ok(
                OrganizationDataCategoriesResponse.create(org.get().uuid(), org.get().name(), orgSizes, tenants)
        ).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/sizes/organization/{organization_id}/tenants/{tenant_id}")
    @Operation(operationId = "findTenantDatabaseSizes", summary = "Get database sizes of a tenant",
            description = "Returns the row count and the configured retention time of every data category of a "
                    + "tenant. Sizes on disk are not reported at this level. Requires organization administrator "
                    + "permissions.")
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

        Map<String, DataCategorySizesAndConfigurationResponse> tenantSizes = Maps.newHashMap();
        for (DataCategory category : DataCategory.values()) {
            tenantSizes.put(category.name(), DataCategorySizesAndConfigurationResponse.create(
                    null,
                    countDataCategoryRows(category, org.get().uuid(), tenant.get().uuid()),
                    DatabaseTools.getDataCategoryRetentionTimeDays(nzyme, category, org.get().uuid(), tenant.get().uuid())
            ));
        }

        return Response.ok(TenantDataCategoriesResponse.create(
                tenant.get().uuid(), tenant.get().name(), org.get().uuid(), org.get().name(), tenantSizes
        )).build();
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
            case UAV -> key = UavRegistryKeys.UAV_RETENTION_TIME_DAYS.key();
            case TIMELINE_EVENTS_DOT11 -> key = TimelinesRegistryKeys.DOT11_EVENTS_RETENTION_TIME_DAYS.key();
        }

        nzyme.getDatabaseCoreRegistry()
                .setValue(key, String.valueOf(req.retentionTimeDays()), organizationId, tenantId);

        return Response.status(Response.Status.ACCEPTED).build();
    }

    private long countDataCategoryRows(DataCategory category,
                                       @Nullable UUID organizationId,
                                       @Nullable UUID tenantId) {
        List<DataTableInformation> tables = ((DatabaseImpl) nzyme.getDatabase()).getTablesOfDataCategory(category);

        long rows = 0;
        for (DataTableInformation table : tables) {
            List<UUID> tapUuids = Tools.getTapUuids(nzyme, organizationId, tenantId);

            if (tapUuids.isEmpty()) {
                continue;
            }

            rows += nzyme.getDatabase().withHandle(handle ->
                    handle.createQuery(table.getRowCountQuery())
                            .bindList("taps", tapUuids)
                            .mapTo(Long.class)
                            .one()
            );
        }

        return rows;
    }

    public long calculateDataCategorySize(DataCategory category) {
        List<DataTableInformation> tables = ((DatabaseImpl) nzyme.getDatabase()).getTablesOfDataCategory(category);

        long size = 0;
        DatabaseImpl database = (DatabaseImpl) nzyme.getDatabase();
        for (DataTableInformation table : tables) {
            size += database.getTableSize(table.getTableName());
        }

        return size;
    }

}
