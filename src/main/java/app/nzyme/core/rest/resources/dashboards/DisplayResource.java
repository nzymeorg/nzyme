package app.nzyme.core.rest.resources.dashboards;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.dashboards.DisplaySettings;
import app.nzyme.core.dashboards.db.DashboardEntry;
import app.nzyme.core.dashboards.db.DisplayEntry;
import app.nzyme.core.rest.authentication.AuthenticatedDisplay;
import app.nzyme.core.rest.authentication.DisplaySecured;
import app.nzyme.core.rest.responses.dashboards.DashboardTopologyResponse;
import app.nzyme.core.rest.responses.dashboards.DisplayDashboardResponse;
import app.nzyme.core.rest.responses.dashboards.DisplayStateResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.joda.time.DateTime;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Hidden // Not part of the user-facing REST API.
@Path("/api/display")
@DisplaySecured
@Produces(MediaType.APPLICATION_JSON)
public class DisplayResource {

    private static final int MAXIMUM_VERSION_LENGTH = 128;

    @Inject
    private NzymeNode nzyme;

    @GET
    public Response state(@Context SecurityContext sc, @QueryParam("version") @Nullable String version) {
        AuthenticatedDisplay authenticated = (AuthenticatedDisplay) sc.getUserPrincipal();
        DisplayEntry display = authenticated.getDisplay();
        DisplaySettings settings = display.settings();

        String reportedVersion = version == null || version.isEmpty() ? null
                : version.substring(0, Math.min(version.length(), MAXIMUM_VERSION_LENGTH));

        nzyme.getDashboards().recordDisplayContact(display.id(), authenticated.getRemoteAddress(), reportedVersion);

        return Response.ok(DisplayStateResponse.create(
                settings.name(),
                display.locationName(),
                nzyme.getDashboards().findDashboardsOfDisplay(display.id()).stream()
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
                settings.hideCursor(),
                nzyme.getVersion().getVersionString(),
                DateTime.now()
        )).build();
    }

    @GET
    @Path("/dashboards/{dashboard_uuid}")
    public Response content(@Context SecurityContext sc, @PathParam("dashboard_uuid") UUID dashboardUuid) {
        DisplayEntry display = ((AuthenticatedDisplay) sc.getUserPrincipal()).getDisplay();

        Optional<DashboardEntry> dashboard = assignedDashboard(display, dashboardUuid);
        if (dashboard.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(nzyme.getDashboards().content().get(dashboard.get(), display.settings().redacted())).build();
    }

    @GET
    @Path("/dashboards/{dashboard_uuid}/widgets/{widget_id}/topology")
    public Response topology(@Context SecurityContext sc,
                             @PathParam("dashboard_uuid") UUID dashboardUuid,
                             @PathParam("widget_id") String widgetId) {
        DisplayEntry display = ((AuthenticatedDisplay) sc.getUserPrincipal()).getDisplay();

        Optional<DashboardEntry> dashboard = assignedDashboard(display, dashboardUuid);
        if (dashboard.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<DashboardTopologyResponse> topology = nzyme.getDashboards().topology()
                .get(dashboard.get(), widgetId, display.settings().redacted());
        if (topology.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(topology.get()).build();
    }

    // A display token only reads the dashboards assigned to its display.
    private Optional<DashboardEntry> assignedDashboard(DisplayEntry display, UUID dashboardUuid) {
        boolean assigned = nzyme.getDashboards().findDashboardsOfDisplay(display.id()).stream()
                .anyMatch(d -> d.uuid().equals(dashboardUuid));
        if (!assigned) {
            return Optional.empty();
        }

        return nzyme.getDashboards().findDashboard(dashboardUuid, display.organizationId(), display.tenantId());
    }

}
