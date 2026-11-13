package app.nzyme.core.rest.resources.dashboards;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.dashboards.DashboardMetric;
import app.nzyme.core.dashboards.DashboardSettings;
import app.nzyme.core.dashboards.DashboardTopology;
import app.nzyme.core.dashboards.Dashboards;
import app.nzyme.core.dashboards.DisplaySettings;
import app.nzyme.core.dashboards.DisplayTokens;
import app.nzyme.core.dashboards.db.DashboardEntry;
import app.nzyme.core.dashboards.db.DisplayDashboard;
import app.nzyme.core.dashboards.db.DisplayEntry;
import app.nzyme.core.events.types.SystemEvent;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.quota.QuotaType;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.DashboardRequest;
import app.nzyme.core.rest.requests.DisplayRequest;
import app.nzyme.core.floorplans.db.TenantLocationEntry;
import app.nzyme.core.rest.responses.dashboards.DashboardContentResponse;
import app.nzyme.core.rest.responses.dashboards.DashboardMetricOptionResponse;
import app.nzyme.core.rest.responses.dashboards.DashboardResponse;
import app.nzyme.core.rest.responses.dashboards.DashboardScopeItemResponse;
import app.nzyme.core.rest.responses.dashboards.DashboardScopeOptionsResponse;
import app.nzyme.core.rest.responses.dashboards.DashboardTopologyResponse;
import app.nzyme.core.rest.responses.dashboards.DashboardsListResponse;
import app.nzyme.core.rest.responses.dashboards.DisplayDashboardResponse;
import app.nzyme.core.rest.responses.dashboards.DisplayResponse;
import app.nzyme.core.rest.responses.dashboards.DisplayTokenResponse;
import app.nzyme.core.rest.responses.dashboards.DisplaysListResponse;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
import app.nzyme.core.security.authentication.db.TenantEntry;
import app.nzyme.plugin.Subsystem;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.joda.time.DateTime;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/dashboards")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Dashboards", description = "Dashboards are pages made to run permanently on a screen. A display is "
        + "such a screen: it authenticates with its own token that can only read the dashboards assigned to it, "
        + "holds the presentation settings of the screen and can rotate through several dashboards.")
public class DashboardsResource extends UserAuthenticatedResource {

    private static final String VIEW_PERMISSION = "dashboards_view";
    private static final String MANAGE_PERMISSION = "dashboards_manage";
    private static final String ALERTS_PERMISSION = "alerts_view";

    private static final int MAXIMUM_PAGE_SIZE = 250;

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findDashboards", summary = "List dashboards of a tenant",
            description = "Returns the dashboards of the tenant, sorted by name. The page size is limited to 250. "
                    + "Requires the dashboards_view or the dashboards_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Dashboards found.",
            content = @Content(schema = @Schema(implementation = DashboardsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The page size is not between 1 and 250.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The calling user has neither of the required feature permissions.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Dashboards are disabled for the tenant.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAllDashboards(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                      @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                      @Parameter(description = "Page size. Up to 250.") @QueryParam("limit") int limit,
                                      @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!canView(sc)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getDashboards().isEnabled(organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        if (invalidPage(limit, offset)) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Dashboards dashboards = nzyme.getDashboards();

        ScopeNames names = new ScopeNames(organizationId, tenantId);
        List<DashboardResponse> result = Lists.newArrayList();
        for (DashboardEntry dashboard : dashboards.findAllDashboards(organizationId, tenantId, limit, offset)) {
            result.add(toResponse(dashboard, names));
        }

        return Response.ok(DashboardsListResponse.create(
                dashboards.countDashboards(organizationId, tenantId), result
        )).build();
    }

    @GET
    @Path("/show/{dashboard_uuid}")
    @Operation(operationId = "findDashboard", summary = "Get a dashboard",
            description = "Requires the dashboards_view or the dashboards_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Dashboard found.",
            content = @Content(schema = @Schema(implementation = DashboardResponse.class)))
    @ApiResponse(responseCode = "401", description = "The calling user has neither of the required feature permissions.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Dashboards are disabled for the tenant.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Dashboard, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findDashboard(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                  @Parameter(description = "Dashboard UUID.") @PathParam("dashboard_uuid") UUID dashboardUuid) {
        if (!canView(sc)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getDashboards().isEnabled(organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Optional<DashboardEntry> dashboard = nzyme.getDashboards().findDashboard(dashboardUuid, organizationId, tenantId);
        if (dashboard.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(toResponse(dashboard.get(), new ScopeNames(organizationId, tenantId))).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "createDashboard", summary = "Create a dashboard",
            description = "Creates a dashboard with its scope, time window and widgets. The calling user can only "
                    + "choose taps they can access, locations where they can access every tap, and the scope ALL if "
                    + "they can access all taps. Widgets that show alerts need the alerts_view feature permission. "
                    + "Everyone who can view the dashboard sees the data of its whole scope. Requires the "
                    + "dashboards_manage feature permission.")
    @ApiResponse(responseCode = "201", description = "Dashboard created.",
            content = @Content(schema = @Schema(implementation = DashboardResponse.class)))
    @ApiResponse(responseCode = "403", description = "Dashboards are disabled for the tenant.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid settings, a scope or widget the calling user may not use, or the dashboards quota is exceeded. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response createDashboard(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                    @RequestBody(description = "The dashboard.", required = true, content = @Content(mediaType = "application/json"))
                                    DashboardRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId) || !tenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getDashboards().isEnabled(organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        DashboardSettings settings;
        try {
            settings = settings(req);
            requireScopeAccess(getAuthenticatedUser(sc), organizationId, tenantId, settings, null);
        } catch (IllegalArgumentException e) {
            return unprocessable(e.getMessage());
        }

        if (!nzyme.getQuotaService().isTenantQuotaAvailable(organizationId, tenantId, QuotaType.DASHBOARDS)) {
            return unprocessable("Dashboard quota exceeded. Please contact your administrator.");
        }

        DashboardEntry created = nzyme.getDashboards().createDashboard(organizationId, tenantId, settings);

        return Response.status(Response.Status.CREATED)
                .entity(toResponse(created, new ScopeNames(organizationId, tenantId)))
                .build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{dashboard_uuid}")
    @Operation(operationId = "updateDashboard", summary = "Update a dashboard",
            description = "Replaces the settings, the scope and the widgets of the dashboard. A user who cannot "
                    + "access every tap of the scope can keep or narrow the scope and change the name, description "
                    + "and layout, but cannot add widgets, change what a widget shows or change the time window. "
                    + "Adding a widget that shows alerts, or changing what it shows, needs the alerts_view feature "
                    + "permission; such widgets that are left as they are can be kept. Requires the "
                    + "dashboards_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Dashboard updated.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Dashboards are disabled for the tenant.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Dashboard, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid settings, or a scope or widget the calling user may not use. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response updateDashboard(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                    @Parameter(description = "Dashboard UUID.") @PathParam("dashboard_uuid") UUID dashboardUuid,
                                    @RequestBody(description = "The new dashboard data.", required = true, content = @Content(mediaType = "application/json"))
                                    DashboardRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getDashboards().isEnabled(organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Dashboards dashboards = nzyme.getDashboards();

        Optional<DashboardEntry> existing = dashboards.findDashboard(dashboardUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DashboardSettings settings;
        try {
            settings = settings(req);
            requireScopeAccess(getAuthenticatedUser(sc), organizationId, tenantId, settings, existing.get().settings());
        } catch (IllegalArgumentException e) {
            return unprocessable(e.getMessage());
        }

        dashboards.updateDashboard(existing.get().id(), settings);

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{dashboard_uuid}")
    @Operation(operationId = "deleteDashboard", summary = "Delete a dashboard",
            description = "Deletes the dashboard and removes it from all displays that show it. The displays "
                    + "themselves are kept. Requires the dashboards_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Dashboard deleted.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Dashboards are disabled for the tenant.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Dashboard, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteDashboard(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                    @Parameter(description = "Dashboard UUID.") @PathParam("dashboard_uuid") UUID dashboardUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getDashboards().isEnabled(organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Dashboards dashboards = nzyme.getDashboards();

        Optional<DashboardEntry> existing = dashboards.findDashboard(dashboardUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        dashboards.deleteDashboard(existing.get().id());

        return Response.ok().build();
    }

    @GET
    @Path("/show/{dashboard_uuid}/content")
    @Operation(operationId = "findDashboardContent", summary = "Get the content of a dashboard",
            description = "Returns the widgets of the dashboard and the current data of each widget, keyed by "
                    + "widget ID. The data covers the scope and the time window of the dashboard, independent of the "
                    + "taps the calling user can access. A result is reused for up to 10 seconds. A widget whose "
                    + "subsystem is disabled has no data, and neither has a clock, a text or a topology, which has a feed of its own. The throughput metric has no data for a time window of more than a day, because tap metrics are kept for a day. A metric has no previous value if nothing is kept of the time before the window. The alerts widget returns up to 25 alerts with the "
                    + "total. The taps widget returns the state of every tap as one character per tap and "
                    + "location (X offline, A alert, D degraded, O fine; A only on a dashboard with widgets that show alerts), the names of all taps if there are at "
                    + "most 250, and the details of up to 50 taps that are not fine. The locations widget returns up to 100 "
                    + "locations with the total, those with alerts or offline taps first. Requires the dashboards_view or the "
                    + "dashboards_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Content built.",
            content = @Content(schema = @Schema(implementation = DashboardContentResponse.class)))
    @ApiResponse(responseCode = "401", description = "The calling user has neither of the required feature permissions.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Dashboards are disabled for the tenant.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Dashboard, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findDashboardContent(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                         @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                         @Parameter(description = "Dashboard UUID.") @PathParam("dashboard_uuid") UUID dashboardUuid) {
        if (!canView(sc)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getDashboards().isEnabled(organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Optional<DashboardEntry> dashboard = nzyme.getDashboards().findDashboard(dashboardUuid, organizationId, tenantId);
        if (dashboard.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(nzyme.getDashboards().content().get(dashboard.get(), false)).build();
    }

    @GET
    @Path("/show/{dashboard_uuid}/widgets/{widget_id}/topology")
    @Operation(operationId = "findDashboardTopology", summary = "Get the live feed of a topology widget",
            description = "Returns the TCP and UDP sessions with activity in the last " + DashboardTopology.SECONDS
                    + " seconds that match the filters of the widget, each with the nodes its endpoints belong to at "
                    + "the node grouping of the widget, in the shape of the live sessions of the topology. A client "
                    + "that asks every few seconds follows the topology as it happens. The feed covers the scope of "
                    + "the dashboard, independent of the taps the calling user can access, and holds the "
                    + DashboardTopology.MAXIMUM_SESSIONS + " most recently active sessions. A result is reused for "
                    + "up to 2 seconds. Requires the dashboards_view or the dashboards_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Feed built.",
            content = @Content(schema = @Schema(implementation = DashboardTopologyResponse.class)))
    @ApiResponse(responseCode = "401", description = "The calling user has neither of the required feature permissions.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Dashboards are disabled for the tenant.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Dashboard, topology widget, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findDashboardTopology(@Parameter(hidden = true) @Context SecurityContext sc,
                                          @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                          @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                          @Parameter(description = "Dashboard UUID.") @PathParam("dashboard_uuid") UUID dashboardUuid,
                                          @Parameter(description = "ID of a topology widget of the dashboard.") @PathParam("widget_id") String widgetId) {
        if (!canView(sc)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getDashboards().isEnabled(organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Optional<DashboardEntry> dashboard = nzyme.getDashboards().findDashboard(dashboardUuid, organizationId, tenantId);
        if (dashboard.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<DashboardTopologyResponse> topology = nzyme.getDashboards().topology()
                .get(dashboard.get(), widgetId, false);
        if (topology.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(topology.get()).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/scope/options")
    @Operation(operationId = "findDashboardScopeOptions", summary = "List what the calling user can put on a dashboard",
            description = "Returns the taps and locations the calling user can choose as the scope of a dashboard, "
                    + "whether they can choose all taps, the metrics of the metric widget, whether the user may "
                    + "add widgets that show alerts and which of the subsystems with dashboard content (Ethernet, WiFi, "
                    + "Bluetooth) are enabled. The topology widget needs the "
                    + "Ethernet subsystem. A location is listed if the user can access every tap at it. "
                    + "A metric of a disabled subsystem is marked as not available. Requires the dashboards_manage "
                    + "feature permission.")
    @ApiResponse(responseCode = "200", description = "Options found.",
            content = @Content(schema = @Schema(implementation = DashboardScopeOptionsResponse.class)))
    @ApiResponse(responseCode = "403", description = "Dashboards are disabled for the tenant.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findScopeOptions(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getDashboards().isEnabled(organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        AuthenticatedUser user = getAuthenticatedUser(sc);
        ScopeAccess access = scopeAccess(user, organizationId, tenantId);

        List<DashboardMetricOptionResponse> metrics = Lists.newArrayList();
        for (DashboardMetric metric : DashboardMetric.values()) {
            metrics.add(DashboardMetricOptionResponse.create(metric.name(), metric.title(), metric.subsystem() == null
                    || nzyme.getSubsystems().isEnabled(metric.subsystem(), organizationId, tenantId)));
        }

        return Response.ok(DashboardScopeOptionsResponse.create(
                access.allTaps(),
                access.locations().stream()
                        .map(l -> DashboardScopeItemResponse.create(l.uuid(), l.name(), null))
                        .collect(Collectors.toList()),
                access.taps().stream()
                        .map(t -> DashboardScopeItemResponse.create(t.uuid(), t.name(), null))
                        .collect(Collectors.toList()),
                metrics,
                userHasPermission(user, ALERTS_PERMISSION),
                DashboardSettings.ALERT_SUBSYSTEMS.stream()
                        .filter(subsystem -> nzyme.getSubsystems().isEnabled(subsystem, organizationId, tenantId))
                        .map(Subsystem::name)
                        .collect(Collectors.toList())
        )).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/displays")
    @Operation(operationId = "findDashboardDisplays", summary = "List displays of a tenant",
            description = "Returns the displays of the tenant, sorted by name, optionally only those that show a "
                    + "certain dashboard or are at a certain location. The page size is limited to 250. Requires "
                    + "the dashboards_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Displays found.",
            content = @Content(schema = @Schema(implementation = DisplaysListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The page size is not between 1 and 250.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Dashboards are disabled for the tenant.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Dashboard, location, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAllDisplays(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                    @Parameter(description = "Only return displays that show the dashboard with this UUID.") @QueryParam("dashboard_id") @Nullable UUID dashboardUuid,
                                    @Parameter(description = "Only return displays at the location with this UUID.") @QueryParam("location_id") @Nullable UUID locationId,
                                    @Parameter(description = "Page size. Up to 250.") @QueryParam("limit") int limit,
                                    @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getDashboards().isEnabled(organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        if (invalidPage(limit, offset)) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Dashboards dashboards = nzyme.getDashboards();

        Long dashboardId = null;
        if (dashboardUuid != null) {
            Optional<DashboardEntry> dashboard = dashboards.findDashboard(dashboardUuid, organizationId, tenantId);
            if (dashboard.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            dashboardId = dashboard.get().id();
        }

        if (locationId != null && !locationExists(locationId, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<DisplayEntry> displays = dashboards.findAllDisplays(
                organizationId, tenantId, dashboardId, locationId, limit, offset
        );
        Map<Long, List<DisplayDashboard>> assigned = dashboards.findDashboardsOfDisplays(
                displays.stream().map(DisplayEntry::id).collect(Collectors.toList())
        );

        List<DisplayResponse> result = Lists.newArrayList();
        for (DisplayEntry display : displays) {
            result.add(toResponse(display, assigned.getOrDefault(display.id(), Lists.newArrayList())));
        }

        return Response.ok(DisplaysListResponse.create(
                dashboards.countDisplays(organizationId, tenantId, dashboardId, locationId), result
        )).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/displays/show/{display_uuid}")
    @Operation(operationId = "findDashboardDisplay", summary = "Get a display",
            description = "Returns the settings of the display, the dashboards it shows and when it last "
                    + "connected. The token of the display is never returned here. Requires the dashboards_manage "
                    + "feature permission.")
    @ApiResponse(responseCode = "200", description = "Display found.",
            content = @Content(schema = @Schema(implementation = DisplayResponse.class)))
    @ApiResponse(responseCode = "403", description = "Dashboards are disabled for the tenant.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Display, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findDisplay(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                @Parameter(description = "Display UUID.") @PathParam("display_uuid") UUID displayUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getDashboards().isEnabled(organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Dashboards dashboards = nzyme.getDashboards();

        Optional<DisplayEntry> display = dashboards.findDisplay(displayUuid, organizationId, tenantId);
        if (display.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(toResponse(display.get(), dashboards.findDashboardsOfDisplay(display.get().id()))).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/displays/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "createDashboardDisplay", summary = "Create a display",
            description = "Creates a display and its token. The plaintext token is returned exactly once in this "
                    + "response and cannot be retrieved later, because Nzyme only stores a hash of it. The token "
                    + "can only read the dashboards assigned to this display. Requires the dashboards_manage "
                    + "feature permission.")
    @ApiResponse(responseCode = "201", description = "Display created. The response contains the plaintext token.",
            content = @Content(schema = @Schema(implementation = DisplayTokenResponse.class)))
    @ApiResponse(responseCode = "403", description = "Dashboards are disabled for the tenant.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid settings, unknown dashboard, unknown location, or the dashboard displays quota is exceeded. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response createDisplay(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                  @RequestBody(description = "The display.", required = true, content = @Content(mediaType = "application/json"))
                                  DisplayRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId) || !tenantExists(organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getDashboards().isEnabled(organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Dashboards dashboards = nzyme.getDashboards();

        DisplaySettings settings;
        List<Long> dashboardIds;
        try {
            settings = settings(req, organizationId, tenantId, null);
            dashboardIds = dashboardIds(req.dashboards(), organizationId, tenantId);
        } catch (IllegalArgumentException e) {
            return unprocessable(e.getMessage());
        }

        if (!nzyme.getQuotaService().isTenantQuotaAvailable(organizationId, tenantId, QuotaType.DASHBOARD_DISPLAYS)) {
            return unprocessable("Display quota exceeded. Please contact your administrator.");
        }

        String token = DisplayTokens.generate();
        DisplayEntry created = dashboards.createDisplay(
                organizationId, tenantId, settings, DisplayTokens.hash(token), dashboardIds
        );

        AuthenticatedUser user = getAuthenticatedUser(sc);
        nzyme.getEventEngine().processEvent(SystemEvent.create(
                SystemEventType.DASHBOARD_DISPLAY_TOKEN_CREATED,
                DateTime.now(),
                "User [" + user.getEmail() + "] created dashboard display [" + settings.name() + "]."
        ), organizationId, tenantId);

        return Response.status(Response.Status.CREATED)
                .entity(DisplayTokenResponse.create(created.uuid(), token))
                .build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/displays/organization/{organization_id}/tenant/{tenant_id}/show/{display_uuid}")
    @Operation(operationId = "updateDashboardDisplay", summary = "Update a display",
            description = "Replaces all settings of the display and the dashboards it shows. The token is not "
                    + "changed and the screen applies the new settings with its next request. Requires the "
                    + "dashboards_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Display updated.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Dashboards are disabled for the tenant.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Display, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid settings, unknown dashboard or unknown location. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response updateDisplay(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                  @Parameter(description = "Display UUID.") @PathParam("display_uuid") UUID displayUuid,
                                  @RequestBody(description = "The new display data.", required = true, content = @Content(mediaType = "application/json"))
                                  DisplayRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getDashboards().isEnabled(organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Dashboards dashboards = nzyme.getDashboards();

        Optional<DisplayEntry> existing = dashboards.findDisplay(displayUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DisplaySettings settings;
        List<Long> dashboardIds;
        try {
            settings = settings(req, organizationId, tenantId, existing.get().settings());
            dashboardIds = dashboardIds(req.dashboards(), organizationId, tenantId);
        } catch (IllegalArgumentException e) {
            return unprocessable(e.getMessage());
        }

        dashboards.updateDisplay(existing.get().id(), settings, dashboardIds);

        return Response.ok().build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/displays/organization/{organization_id}/tenant/{tenant_id}/show/{display_uuid}/token")
    @Operation(operationId = "replaceDashboardDisplayToken", summary = "Replace the token of a display",
            description = "Creates a new token for the display and immediately rejects the previous one. The "
                    + "plaintext token is returned exactly once in this response. Requires the dashboards_manage "
                    + "feature permission.")
    @ApiResponse(responseCode = "200", description = "Token replaced. The response contains the plaintext token.",
            content = @Content(schema = @Schema(implementation = DisplayTokenResponse.class)))
    @ApiResponse(responseCode = "403", description = "Dashboards are disabled for the tenant.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Display, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response replaceDisplayToken(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                        @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                        @Parameter(description = "Display UUID.") @PathParam("display_uuid") UUID displayUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getDashboards().isEnabled(organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Dashboards dashboards = nzyme.getDashboards();

        Optional<DisplayEntry> existing = dashboards.findDisplay(displayUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String token = DisplayTokens.generate();
        dashboards.replaceDisplayToken(existing.get().id(), DisplayTokens.hash(token));

        AuthenticatedUser user = getAuthenticatedUser(sc);
        nzyme.getEventEngine().processEvent(SystemEvent.create(
                SystemEventType.DASHBOARD_DISPLAY_TOKEN_CREATED,
                DateTime.now(),
                "User [" + user.getEmail() + "] replaced the token of dashboard display ["
                        + existing.get().settings().name() + "]."
        ), organizationId, tenantId);

        return Response.ok(DisplayTokenResponse.create(existing.get().uuid(), token)).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/displays/organization/{organization_id}/tenant/{tenant_id}/show/{display_uuid}")
    @Operation(operationId = "deleteDashboardDisplay", summary = "Delete a display",
            description = "Deletes the display and immediately rejects any further request that uses its token. "
                    + "The dashboards it showed are kept. Requires the dashboards_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Display deleted.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Dashboards are disabled for the tenant.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Display, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteDisplay(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                  @Parameter(description = "Display UUID.") @PathParam("display_uuid") UUID displayUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nzyme.getDashboards().isEnabled(organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Dashboards dashboards = nzyme.getDashboards();

        Optional<DisplayEntry> existing = dashboards.findDisplay(displayUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        dashboards.deleteDisplay(existing.get().id());

        return Response.ok().build();
    }

    private boolean canView(SecurityContext sc) {
        AuthenticatedUser user = getAuthenticatedUser(sc);
        return userHasPermission(user, VIEW_PERMISSION) || userHasPermission(user, MANAGE_PERMISSION);
    }

    // Super administrators pass the tenant access check for any ID.
    private boolean tenantExists(UUID organizationId, UUID tenantId) {
        Optional<TenantEntry> tenant = nzyme.getAuthenticationService().findTenant(tenantId);
        return tenant.isPresent() && tenant.get().organizationUuid().equals(organizationId);
    }

    private static boolean invalidPage(int limit, int offset) {
        return limit < 1 || limit > MAXIMUM_PAGE_SIZE || offset < 0;
    }

    private static DashboardSettings settings(@Nullable DashboardRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("A name is required.");
        }

        return DashboardSettings.validated(req.name(), req.description(), req.scopeType(), req.scopeIds(),
                req.windowMinutes(), req.widgets());
    }

    private record ScopeAccess(boolean allTaps, List<TenantLocationEntry> locations, List<Tap> taps) { }

    // What the user may choose as the scope of a dashboard. Viewers see the whole scope, so a manager can only
    // hand out what they can access themselves.
    private ScopeAccess scopeAccess(AuthenticatedUser user, UUID organizationId, UUID tenantId) {
        List<Tap> tenantTaps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId);
        List<UUID> accessible = nzyme.getTapManager().allTapUUIDsAccessibleByUser(user);

        // Explicit tap permissions take precedence over the flag, like in the tap manager.
        boolean allTaps = user.isSuperAdministrator() || user.isOrganizationAdministrator()
                || (user.isAccessAllTenantTaps()
                && nzyme.getAuthenticationService().findTapPermissionsOfUser(user.getUserId()).isEmpty());

        // A location without taps is not one where the user can access every tap: taps placed there later would
        // be covered without anyone checking.
        List<TenantLocationEntry> locations = nzyme.getAuthenticationService()
                .findAllTenantLocations(organizationId, tenantId, Integer.MAX_VALUE, 0).stream()
                .filter(l -> {
                    List<Tap> placed = tenantTaps.stream().filter(t -> l.uuid().equals(t.locationId())).toList();
                    return allTaps || (!placed.isEmpty() && placed.stream().allMatch(t -> accessible.contains(t.uuid())));
                })
                .collect(Collectors.toList());

        List<Tap> taps = tenantTaps.stream()
                .filter(t -> accessible.contains(t.uuid()))
                .sorted(Comparator.comparing(t -> t.name().toLowerCase()))
                .collect(Collectors.toList());

        return new ScopeAccess(allTaps, locations, taps);
    }

    private void requireScopeAccess(AuthenticatedUser user,
                                    UUID organizationId,
                                    UUID tenantId,
                                    DashboardSettings settings,
                                    @Nullable DashboardSettings existing) {
        settings.requireChangeAllowed(existing, () -> {
            ScopeAccess access = scopeAccess(user, organizationId, tenantId);

            return new DashboardSettings.Access(
                    access.allTaps(),
                    access.locations().stream().map(TenantLocationEntry::uuid).collect(Collectors.toSet()),
                    access.taps().stream().map(Tap::uuid).collect(Collectors.toSet()),
                    userHasPermission(user, ALERTS_PERMISSION)
            );
        });
    }

    // The locations and taps of a tenant, loaded once for all dashboards of a response and only if one needs them.
    private class ScopeNames {

        private final UUID organizationId;
        private final UUID tenantId;

        private List<TenantLocationEntry> locations;
        private List<Tap> taps;

        private ScopeNames(UUID organizationId, UUID tenantId) {
            this.organizationId = organizationId;
            this.tenantId = tenantId;
        }

        private List<DashboardScopeItemResponse> of(DashboardSettings settings) {
            switch (settings.scope()) {
                case LOCATIONS:
                    if (locations == null) {
                        locations = nzyme.getAuthenticationService()
                                .findAllTenantLocations(organizationId, tenantId, Integer.MAX_VALUE, 0);
                    }

                    return locations.stream()
                            .filter(l -> settings.scopeIds().contains(l.uuid()))
                            .map(l -> DashboardScopeItemResponse.create(l.uuid(), l.name(), null))
                            .collect(Collectors.toList());
                case TAPS:
                    if (taps == null) {
                        taps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId);
                    }

                    return taps.stream()
                            .filter(t -> settings.scopeIds().contains(t.uuid()))
                            .sorted(Comparator.comparing(t -> t.name().toLowerCase()))
                            .map(t -> DashboardScopeItemResponse.create(t.uuid(), t.name(), null))
                            .collect(Collectors.toList());
                default:
                    return Lists.newArrayList();
            }
        }

    }

    private boolean locationExists(UUID locationId, UUID organizationId, UUID tenantId) {
        return nzyme.getAuthenticationService().findTenantLocation(locationId, organizationId, tenantId).isPresent();
    }

    private DisplaySettings settings(@Nullable DisplayRequest req,
                                     UUID organizationId,
                                     UUID tenantId,
                                     @Nullable DisplaySettings existing) {
        if (req == null) {
            throw new IllegalArgumentException("A name is required.");
        }

        if (req.locationId() != null && !locationExists(req.locationId(), organizationId, tenantId)) {
            throw new IllegalArgumentException("The location does not exist.");
        }

        return DisplaySettings.validated(req.name(), req.locationId(), req.theme(), req.lightFrom(), req.lightUntil(),
                req.timeZone(), req.clockFormat(), req.scalePercent(), req.rotationSeconds(), req.refreshSeconds(),
                req.allowedNetworks(),
                // An update that does not mention it must not make a screen show what it hid before.
                req.redacted() == null && existing != null ? Boolean.valueOf(existing.redacted()) : req.redacted(),
                req.hideCursor(), req.expiresAt());
    }

    private List<Long> dashboardIds(@Nullable List<UUID> requested, UUID organizationId, UUID tenantId) {
        List<UUID> uuids = requested == null
                ? Lists.newArrayList() : requested.stream().distinct().collect(Collectors.toList());

        if (uuids.size() > Dashboards.MAXIMUM_DASHBOARDS_PER_DISPLAY) {
            throw new IllegalArgumentException("A display cannot show more than "
                    + Dashboards.MAXIMUM_DASHBOARDS_PER_DISPLAY + " dashboards.");
        }

        Map<UUID, Long> ids = nzyme.getDashboards().findDashboards(uuids, organizationId, tenantId).stream()
                .collect(Collectors.toMap(DashboardEntry::uuid, DashboardEntry::id));

        List<Long> ordered = Lists.newArrayList();
        for (UUID uuid : uuids) {
            if (uuid == null || !ids.containsKey(uuid)) {
                throw new IllegalArgumentException("One of the dashboards does not exist.");
            }

            ordered.add(ids.get(uuid));
        }

        return ordered;
    }

    private static Response unprocessable(String message) {
        return Response.status(422).entity(ErrorResponse.create(message)).build();
    }

    private DashboardResponse toResponse(DashboardEntry dashboard, ScopeNames names) {
        DashboardSettings settings = dashboard.settings();

        return DashboardResponse.create(
                dashboard.uuid(),
                settings.name(),
                settings.description(),
                settings.scope().name(),
                names.of(settings),
                settings.windowMinutes(),
                settings.widgets(),
                dashboard.displayCount(),
                dashboard.createdAt(),
                dashboard.updatedAt()
        );
    }

    private DisplayResponse toResponse(DisplayEntry display, List<DisplayDashboard> assigned) {
        DisplaySettings settings = display.settings();

        // A display that missed three refreshes counts as offline.
        int offlineAfterSeconds = Math.max(settings.refreshSeconds() * 3, 60);

        return DisplayResponse.create(
                display.uuid(),
                settings.name(),
                settings.locationId(),
                display.locationName(),
                assigned.stream()
                        .map(d -> DisplayDashboardResponse.create(d.uuid(), d.name()))
                        .collect(Collectors.toList()),
                settings.theme().name(),
                settings.lightFrom(),
                settings.lightUntil(),
                settings.timeZone(),
                settings.clockFormat().name(),
                settings.scalePercent(),
                settings.rotationSeconds(),
                settings.refreshSeconds(),
                settings.allowedNetworks(),
                settings.redacted(),
                settings.hideCursor(),
                settings.expiresAt(),
                settings.expiresAt() != null && settings.expiresAt().isBeforeNow(),
                display.lastSeenAt() != null
                        && display.lastSeenAt().isAfter(DateTime.now().minusSeconds(offlineAfterSeconds)),
                display.lastSeenAt(),
                display.lastRemoteAddress(),
                display.lastReportedVersion(),
                nzyme.getVersion().getVersionString(),
                display.createdAt(),
                display.updatedAt()
        );
    }

}
