package app.nzyme.core.rest.resources.system.connect;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.connect.ConnectRegistryKeys;
import app.nzyme.core.rest.requests.ConnectConfigurationUpdateRequest;
import app.nzyme.core.rest.responses.connect.ConnectConfigurationResponse;
import app.nzyme.core.rest.responses.connect.ConnectStatusResponse;
import app.nzyme.plugin.RegistryCryptoException;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraintValidator;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.configuration.EncryptedConfigurationEntryResponse;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.*;

@Produces(MediaType.APPLICATION_JSON)
@Path("/api/system/connect")
@Tag(name = "Nzyme Connect", description = "Nzyme Connect is an optional cloud service that your cluster reports to. "
        + "In return it provides curated operational data, for example GeoIP, ASN, UAV and MAC address vendor "
        + "information. All it needs is an API key.")
public class ConnectResource {

    private static final Logger LOG = LogManager.getLogger(ConnectResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/status")
    @Operation(operationId = "findConnectStatus", summary = "Get Nzyme Connect status",
            description = "The connection summary is disabled when Connect is turned off, never_connected when no "
                    + "report has ever been accepted, fail when the last accepted report is older than two minutes, "
                    + "and ok otherwise. The response also lists the services Connect currently provides to this "
                    + "cluster. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Status found.",
            content = @Content(schema = @Schema(implementation = ConnectStatusResponse.class)))
    public Response status() {
        Optional<String> lastReport = nzyme.getDatabaseCoreRegistry()
                .getValue(ConnectRegistryKeys.LAST_SUCCESSFUL_REPORT_SUBMISSION.key());

        Optional<String> providedServicesInfo = nzyme.getDatabaseCoreRegistry()
                .getValue(ConnectRegistryKeys.PROVIDED_SERVICES.key());

        List<String> providedServices;
        if (providedServicesInfo.isPresent()) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                providedServices = objectMapper.readValue(providedServicesInfo.get(), new TypeReference<>() {});
            } catch (JacksonException e) {
                LOG.error("Could not parse Connect provided services info.", e);
                providedServices = Collections.emptyList();
            }
        } else {
            providedServices = Collections.emptyList();
        }

        ConnectStatusResponse status;
        if (nzyme.getConnect().isEnabled()) {
            if (lastReport.isEmpty()) {
                status = ConnectStatusResponse.create(
                        "never_connected",
                        null,
                        providedServices
                );
            } else {
                DateTime ts = DateTime.parse(lastReport.get());

                String summary;
                if (ts.isBefore(DateTime.now().minusMinutes(2))) {
                    summary = "fail";
                } else {
                    summary = "ok";
                }

                status = ConnectStatusResponse.create(summary, ts, providedServices);
            }
        } else {
            status = ConnectStatusResponse.create(
                    "disabled",
                    null,
                    providedServices
            );
        }

        return Response.ok(status).build();
    }

    @GET
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/configuration")
    @Operation(operationId = "findConnectConfiguration", summary = "Get Nzyme Connect configuration",
            description = "The API key is never returned. The response only tells you whether a key is stored. "
                    + "Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = ConnectConfigurationResponse.class)))
    @ApiResponse(responseCode = "500", description = "The stored API key could not be decrypted.", content = @Content)
    public Response configuration() {
        boolean connectEnabled = nzyme.getDatabaseCoreRegistry()
                .getValue(ConnectRegistryKeys.CONNECT_ENABLED.key())
                .filter(Boolean::parseBoolean).isPresent();

        boolean apiKeyIsSet;
        try {
            apiKeyIsSet = nzyme.getDatabaseCoreRegistry()
                    .getEncryptedValue(ConnectRegistryKeys.CONNECT_API_KEY.key())
                    .isPresent();
        } catch(RegistryCryptoException e) {
            LOG.error("Could not decrypt encrypted registry value.", e);
            return Response.serverError().build();
        }

        ConnectConfigurationResponse response = ConnectConfigurationResponse.create(
                ConfigurationEntryResponse.create(
                        ConnectRegistryKeys.CONNECT_ENABLED.key(),
                        "Connect Enabled",
                        connectEnabled,
                        ConfigurationEntryValueType.BOOLEAN,
                        ConnectRegistryKeys.CONNECT_ENABLED.defaultValue().orElse(null),
                        ConnectRegistryKeys.CONNECT_ENABLED.requiresRestart(),
                        ConnectRegistryKeys.CONNECT_ENABLED.constraints().orElse(Collections.emptyList()),
                        "nzyme-connect"
                ),
                EncryptedConfigurationEntryResponse.create(
                        ConnectRegistryKeys.CONNECT_API_KEY.key(),
                        "API Key",
                        apiKeyIsSet,
                        ConfigurationEntryValueType.STRING_ENCRYPTED,
                        ConnectRegistryKeys.CONNECT_API_KEY.requiresRestart(),
                        ConnectRegistryKeys.CONNECT_API_KEY.constraints().orElse(Collections.emptyList()),
                        "nzyme-connect"
                )
        );

        return Response.ok(response).build();
    }

    @PUT
    @RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
    @Path("/configuration")
    @Operation(operationId = "updateConnectConfiguration", summary = "Update Nzyme Connect configuration",
            description = "Each submitted value is validated against the constraints of its configuration key before "
                    + "it is stored. The API key is stored encrypted. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "422", description = "No changes were submitted, a value failed validation, or an "
            + "unknown configuration key was submitted.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The API key could not be encrypted.", content = @Content)
    public Response update(@RequestBody(description = "Configuration keys and their new values.", required = true, content = @Content(mediaType = "application/json"))
                           ConnectConfigurationUpdateRequest ur) {
        if (ur.change().isEmpty()) {
            LOG.info("Empty configuration parameters.");
            return Response.status(422).build();
        }

        for (Map.Entry<String, Object> c : ur.change().entrySet()) {
            boolean encrypted;
            switch (c.getKey()) {
                case "connect_enabled":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(ConnectRegistryKeys.CONNECT_ENABLED, c)) {
                        return Response.status(422).build();
                    }
                    encrypted = false;
                    break;
                case "connect_api_key":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(ConnectRegistryKeys.CONNECT_API_KEY, c)) {
                        return Response.status(422).build();
                    }
                    encrypted = true;
                    break;
                default:
                    LOG.info("Unknown configuration parameter [{}].", c.getKey());
                    return Response.status(422).build();
            }

            if (encrypted) {
                try {
                    nzyme.getDatabaseCoreRegistry().setEncryptedValue(c.getKey(), c.getValue().toString());
                } catch (RegistryCryptoException e) {
                    return Response.serverError().build();
                }
            } else {
                nzyme.getDatabaseCoreRegistry().setValue(c.getKey(), c.getValue().toString());
            }
        }

        return Response.ok().build();
    }

}
