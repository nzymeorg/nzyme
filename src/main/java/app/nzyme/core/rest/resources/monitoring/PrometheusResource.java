package app.nzyme.core.rest.resources.monitoring;

import java.util.List;
import app.nzyme.core.registry.RegistryChangeValidator;
import io.swagger.v3.oas.annotations.Hidden;
import app.nzyme.plugin.RegistryCryptoException;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.configuration.EncryptedConfigurationEntryResponse;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.monitoring.exporters.prometheus.PrometheusFormatter;
import app.nzyme.core.monitoring.exporters.prometheus.PrometheusRegistryKeys;
import app.nzyme.core.rest.authentication.PrometheusBasicAuthSecured;
import app.nzyme.core.rest.requests.PrometheusConfigurationUpdateRequest;
import app.nzyme.core.rest.responses.monitoring.prometheus.PrometheusConfigurationResponse;
import jakarta.ws.rs.PUT;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;


@Produces(MediaType.APPLICATION_JSON)
@Hidden // Not part of the user-facing REST API.
@Path("/api/system/monitoring/prometheus")
public class PrometheusResource {

    private static final Logger LOG = LogManager.getLogger(PrometheusResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @PrometheusBasicAuthSecured
    @Path("/metrics")
    public Response metrics() {
        Optional<String> v = nzyme.getDatabaseCoreRegistry().getValue(PrometheusRegistryKeys.REST_REPORT_ENABLED.key());

        if (v.isPresent() && v.get().equals("true")) {
            PrometheusFormatter f = new PrometheusFormatter(nzyme.getMetrics());
            return Response.ok(f.format()).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/configuration")
    public Response configuration() {
        boolean reportEnabled = nzyme.getDatabaseCoreRegistry()
                .getValue(PrometheusRegistryKeys.REST_REPORT_ENABLED.key())
                .filter(Boolean::parseBoolean).isPresent();

        String username = nzyme.getDatabaseCoreRegistry().getValue(PrometheusRegistryKeys.REST_REPORT_USERNAME.key())
                .orElse(null);

        boolean passwordIsSet;
        try {
            passwordIsSet = nzyme.getDatabaseCoreRegistry()
                    .getEncryptedValue(PrometheusRegistryKeys.REST_REPORT_PASSWORD.key())
                    .isPresent();
        } catch(RegistryCryptoException e) {
            LOG.error("Could not decrypt encrypted registry value.", e);
            return Response.serverError().build();
        }

        PrometheusConfigurationResponse response = PrometheusConfigurationResponse.create(
                ConfigurationEntryResponse.create(
                        PrometheusRegistryKeys.REST_REPORT_ENABLED.key(),
                        "REST Report enabled",
                        reportEnabled,
                        ConfigurationEntryValueType.BOOLEAN,
                        PrometheusRegistryKeys.REST_REPORT_ENABLED.defaultValue().orElse(null),
                        PrometheusRegistryKeys.REST_REPORT_ENABLED.requiresRestart(),
                        PrometheusRegistryKeys.REST_REPORT_ENABLED.constraints().orElse(Collections.emptyList()),
                        "prometheus-exporter-config"
                ),
                ConfigurationEntryResponse.create(
                        PrometheusRegistryKeys.REST_REPORT_USERNAME.key(),
                        "Basic authentication username",
                        username,
                        ConfigurationEntryValueType.STRING,
                        PrometheusRegistryKeys.REST_REPORT_USERNAME.defaultValue().orElse(null),
                        PrometheusRegistryKeys.REST_REPORT_USERNAME.requiresRestart(),
                        PrometheusRegistryKeys.REST_REPORT_USERNAME.constraints().orElse(Collections.emptyList()),
                        "prometheus-exporter-config"
                ),
                EncryptedConfigurationEntryResponse.create(
                        PrometheusRegistryKeys.REST_REPORT_PASSWORD.key(),
                        "Basic authentication password",
                        passwordIsSet,
                        ConfigurationEntryValueType.STRING_ENCRYPTED,
                        PrometheusRegistryKeys.REST_REPORT_PASSWORD.requiresRestart(),
                        PrometheusRegistryKeys.REST_REPORT_PASSWORD.constraints().orElse(Collections.emptyList()),
                        "prometheus-exporter-config"
                )
        );

        return Response.ok(response).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/configuration")
    public Response update(PrometheusConfigurationUpdateRequest ur) {
        if (ur.change().isEmpty()) {
            LOG.info("Empty configuration parameters.");
            return Response.status(422).build();
        }

        Optional<List<RegistryChangeValidator.Change>> changes = RegistryChangeValidator
                .allowing(PrometheusRegistryKeys.REST_REPORT_ENABLED,
                        PrometheusRegistryKeys.REST_REPORT_USERNAME)
                .allowingEncrypted(PrometheusRegistryKeys.REST_REPORT_PASSWORD)
                .validate(ur.change());

        if (changes.isEmpty()) {
            return Response.status(422).build();
        }

        for (RegistryChangeValidator.Change c : changes.get()) {
            if (c.encrypted()) {
                try {
                    nzyme.getDatabaseCoreRegistry().setEncryptedValue(c.key(), c.value());
                } catch (RegistryCryptoException e) {
                    return Response.serverError().build();
                }
            } else {
                nzyme.getDatabaseCoreRegistry().setValue(c.key(), c.value());
            }
        }

        return Response.ok().build();
    }

}
