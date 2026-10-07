package app.nzyme.core.rest.resources.system;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;
import app.nzyme.core.rest.responses.system.HealthIndicatorResponse;
import app.nzyme.core.rest.responses.system.HealthResponse;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.PUT;
import org.joda.time.DateTime;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Path("/api/system/health")
@RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Health", description = "Health indicators continuously check parts of an Nzyme cluster and report "
        + "problems that need attention. An indicator raises a system event every time its result level changes, so "
        + "you can subscribe event actions to it.")
public class HealthResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("indicators")
    @Operation(operationId = "findHealthIndicators", summary = "List health indicators",
            description = "Returns the most recent result of every health indicator, keyed by indicator ID. An "
                    + "indicator that was last checked more than two minutes ago is flagged as stale. Requires "
                    + "super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Health indicators found.",
            content = @Content(schema = @Schema(implementation = HealthResponse.class)))
    public Response indicators() {
        List<IndicatorStatus> status = nzyme.getHealthMonitor().getIndicatorStatus();

        Map<String, HealthIndicatorResponse> indicators = Maps.newHashMap();
        for (IndicatorStatus s : status) {
            indicators.put(s.indicatorId(), HealthIndicatorResponse.create(
                    s.indicatorId(),
                    s.indicatorName(),
                    s.resultLevel().toString().toUpperCase(),
                    s.lastChecked(),
                    s.lastChecked().isBefore(DateTime.now().minusMinutes(2)),
                    s.active()
            ));
        }

        return Response.ok(HealthResponse.create(indicators)).build();
    }

    @PUT
    @Path("/indicators/configuration")
    @Operation(operationId = "updateHealthIndicatorConfiguration", summary = "Update health indicator configuration",
            description = "Enables or disables health indicators. The body maps an indicator ID to a map of settings. "
                    + "Only the active setting is read; all other settings are ignored. Unknown indicator IDs are "
                    + "ignored as well. A disabled indicator is skipped on the next health monitor run and keeps its "
                    + "last recorded result. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    public Response updateIndicatorConfig(@RequestBody(description = "Indicator IDs mapped to their settings.",
            required = true, content = @Content(mediaType = "application/json")) Map<String, Map<String, String>> config) {
        for (Map.Entry<String, Map<String, String>> c : config.entrySet()) {
            if (c.getValue().containsKey("active")) {
                boolean active = Boolean.parseBoolean(c.getValue().get("active"));
                nzyme.getHealthMonitor().updateIndicatorActivationState(c.getKey(), active);
            }
        }

        return Response.ok().build();
    }

}
