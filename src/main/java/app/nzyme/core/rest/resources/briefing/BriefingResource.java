package app.nzyme.core.rest.resources.briefing;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.briefing.*;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.responses.briefing.BriefingResponse;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.TimeRange;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Sets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Path("/api/briefing")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Briefing", description = "A briefing summarizes what happened since the calling user last looked: "
        + "what needs attention, whether the taps and detections are actually watching, and what changed compared "
        + "to the window before. There is a main briefing across all subsystems and one each for WiFi, Ethernet and Bluetooth.")
public class BriefingResource extends TapDataHandlingResource {

    private static final Logger LOG = LogManager.getLogger(BriefingResource.class);

    @Inject
    private NzymeNode nzyme;

    private BriefingService service;

    private BriefingService service() {
        if (service == null) {
            service = new BriefingService(nzyme);
        }
        return service;
    }

    @GET
    @Path("/{type}")
    @RESTSecured(PermissionLevel.ANY)
    @Operation(operationId = "getBriefing", summary = "Get a briefing",
            description = "Builds the briefing of the given type (MAIN, DOT11, ETHERNET or BLUETOOTH) for a tenant and a set "
                    + "of taps. The window starts at the user's last visit of this briefing, at the end of the user's "
                    + "previous session, or at the start of the passed time range. Opening the briefing records the visit; "
                    + "reloads within ten minutes keep the same window. Sections the user has no permission for "
                    + "are left out. Alerts need the alerts_view feature permission and monitoring configuration "
                    + "checks need the dot11_monitoring_manage feature permission, Ethernet detection "
                    + "method checks the ethernet_monitoring_manage feature permission. Links to pages the user "
                    + "cannot open are left out.")
    @ApiResponse(responseCode = "200", description = "Briefing built.",
            content = @Content(schema = @Schema(implementation = BriefingResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown briefing type or since mode.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response get(@Parameter(hidden = true) @Context SecurityContext sc,
                        @Parameter(description = "Briefing type. One of MAIN, DOT11, ETHERNET, BLUETOOTH.") @PathParam("type") String typeParam,
                        @Parameter(description = "Organization UUID.") @QueryParam("organization_id") @NotNull UUID organizationId,
                        @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") @NotNull UUID tenantId,
                        @Parameter(description = "Comma separated list of tap UUIDs to include. Omit or pass * for all taps of the tenant the user can access, which is what the web interface does.") @QueryParam("taps") String tapsParam,
                        @Parameter(description = "Where the window starts. One of LAST_VISIT, LAST_SESSION, TIME_RANGE. Defaults to LAST_VISIT.") @QueryParam("since") @Nullable String sinceParam,
                        @Parameter(description = "Time range selector, used with since=TIME_RANGE. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid @Nullable String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        BriefingType type;
        BriefingSinceMode since;
        try {
            type = BriefingType.valueOf(typeParam.toUpperCase());
            since = sinceParam == null || sinceParam.isEmpty()
                    ? BriefingSinceMode.LAST_VISIT : BriefingSinceMode.valueOf(sinceParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        TimeRange timeRange = null;
        if (since == BriefingSinceMode.TIME_RANGE && timeRangeParameter != null && !timeRangeParameter.isEmpty()) {
            timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        }

        AuthenticatedUser user = getAuthenticatedUser(sc);

        Set<UUID> tenantTaps = Sets.newHashSet();
        for (Tap tap : nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)) {
            tenantTaps.add(tap.uuid());
        }
        String tapsSelector = tapsParam == null || tapsParam.isEmpty() ? "*" : tapsParam;
        List<UUID> taps = parseAndValidateTapIds(user, nzyme, tapsSelector).stream()
                .filter(tenantTaps::contains)
                .toList();

        BriefingWindow window = service().resolveWindow(type, user, since, timeRange);
        BriefingContext ctx = service().buildContext(user, organizationId, tenantId, taps, window,
                userHasPermission(user, "alerts_view"), userHasPermission(user, "dot11_monitoring_manage"),
                userHasPermission(user, "ethernet_monitoring_manage"),
                userHasPermission(user, "bluetooth_monitoring_manage"));

        long started = System.currentTimeMillis();
        BriefingResponse briefing = service().build(type, ctx);
        LOG.debug("Built {} briefing in {}ms.", type, System.currentTimeMillis() - started);

        return Response.ok(briefing).build();
    }

    @PUT
    @Path("/{type}/seen")
    @RESTSecured(PermissionLevel.ANY)
    @Operation(operationId = "markBriefingSeen", summary = "Mark a briefing as seen",
            description = "Moves the calling user's last visit of this briefing to now, regardless of the reload "
                    + "grace period. The next briefing in last-visit mode starts a fresh window.")
    @ApiResponse(responseCode = "200", description = "Marked as seen.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Unknown briefing type.", content = @Content)
    public Response markSeen(@Parameter(hidden = true) @Context SecurityContext sc,
                             @Parameter(description = "Briefing type. One of MAIN, DOT11, ETHERNET, BLUETOOTH.") @PathParam("type") String typeParam) {
        BriefingType type;
        try {
            type = BriefingType.valueOf(typeParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        service().visits().markSeen(getAuthenticatedUser(sc).getUserId(), type);

        return Response.ok().build();
    }

}
