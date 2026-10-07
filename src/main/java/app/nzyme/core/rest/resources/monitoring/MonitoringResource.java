package app.nzyme.core.rest.resources.monitoring;

import app.nzyme.plugin.RegistryCryptoException;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Maps;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.monitoring.exporters.prometheus.PrometheusRegistryKeys;
import app.nzyme.core.rest.responses.monitoring.MonitoringSummaryResponse;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/api/system/monitoring")
@RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Metrics Exporters", description = "Metrics exporters make internal Nzyme metrics available to third "
        + "party monitoring systems. Exported metrics are always specific to the node you talk to.",
        externalDocs = @ExternalDocumentation(description = "Metrics exporters in the Nzyme documentation",
                url = "https://go.nzyme.org/metrics-exporters"))
public class MonitoringResource {

    @Inject
    private NzymeNode nzyme;

    private static final Logger LOG = LogManager.getLogger(MonitoringResource.class);

    @GET
    @Path("/summary")
    @Operation(operationId = "findMonitoringSummary", summary = "Get monitoring exporter summary",
            description = "Returns a map of exporter names and whether each one is fully configured and enabled. The "
                    + "Prometheus exporter counts as enabled only if it is turned on and has a username and a "
                    + "password. Requires super administrator permissions.",
            externalDocs = @ExternalDocumentation(description = "Prometheus exporter configuration in the Nzyme documentation",
                    url = "https://go.nzyme.org/prometheus-exporter-config"))
    @ApiResponse(responseCode = "200", description = "Summary found.",
            content = @Content(schema = @Schema(implementation = MonitoringSummaryResponse.class)))
    @ApiResponse(responseCode = "500", description = "A stored exporter password could not be decrypted.", content = @Content)
    public Response summary() {
        boolean prometheusReportEnabled;
        try {
            prometheusReportEnabled = nzyme.getDatabaseCoreRegistry()
                    .getValue(PrometheusRegistryKeys.REST_REPORT_ENABLED.key())
                    .filter(Boolean::parseBoolean)
                    .isPresent()
                    && nzyme.getDatabaseCoreRegistry().getValue(PrometheusRegistryKeys.REST_REPORT_USERNAME.key())
                    .isPresent()
                    && nzyme.getDatabaseCoreRegistry().getEncryptedValue(PrometheusRegistryKeys.REST_REPORT_PASSWORD.key())
                    .isPresent();
        } catch(RegistryCryptoException e) {
            LOG.error("Could not decrypt encrypted registry value", e);
            return Response.serverError().build();
        }

        Map<String, Boolean> exporters = Maps.newHashMap();
        exporters.put("prometheus", prometheusReportEnabled);

        return Response.ok(MonitoringSummaryResponse.create(exporters)).build();
    }

}
