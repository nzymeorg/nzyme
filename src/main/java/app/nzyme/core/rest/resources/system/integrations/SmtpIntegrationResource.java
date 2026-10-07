package app.nzyme.core.rest.resources.system.integrations;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.integrations.smtp.SMTPConfigurationRegistryKeys;
import app.nzyme.core.rest.requests.SmtpIntegrationConfigurationUpdateRequest;
import app.nzyme.core.rest.responses.system.configuration.SmtpConfigurationResponse;
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

@Path("/api/system/integrations/smtp")
@RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Integrations", description = "Configuration of the services Nzyme talks to, for example the SMTP server "
        + "it uses to send system and alert emails. Integrations are configured for the whole cluster and not per "
        + "tenant.")
public class SmtpIntegrationResource {

    private static final Logger LOG = LogManager.getLogger(SmtpIntegrationResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/configuration")
    @Operation(operationId = "findSmtpConfiguration", summary = "Get SMTP configuration",
            description = "Returns the SMTP settings Nzyme uses to send emails, including the current value, the "
                    + "default value and the validation constraints of every setting. The password is never returned. "
                    + "The response only tells you whether a password is stored. Requires super administrator "
                    + "permissions.")
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = SmtpConfigurationResponse.class)))
    @ApiResponse(responseCode = "500", description = "The stored password could not be decrypted.", content = @Content)
    public Response getSmtpConfiguration() {
        String transportStrategy = nzyme.getDatabaseCoreRegistry()
                .getValueOrNull(SMTPConfigurationRegistryKeys.TRANSPORT_STRATEGY.key());

        String hostname = nzyme.getDatabaseCoreRegistry()
                .getValueOrNull(SMTPConfigurationRegistryKeys.HOST.key());

        Integer port = nzyme.getDatabaseCoreRegistry()
                .getValue(SMTPConfigurationRegistryKeys.PORT.key())
                .map(Integer::parseInt)
                .orElse(null);

        String username = nzyme.getDatabaseCoreRegistry()
                .getValueOrNull(SMTPConfigurationRegistryKeys.USERNAME.key());

        String fromAddress = nzyme.getDatabaseCoreRegistry()
                .getValueOrNull(SMTPConfigurationRegistryKeys.FROM_ADDRESS.key());

        String webInterfaceUrl = nzyme.getDatabaseCoreRegistry()
                .getValueOrNull(SMTPConfigurationRegistryKeys.WEB_INTERFACE_URL.key());

        boolean passwordIsSet;
        try {
            passwordIsSet = nzyme.getDatabaseCoreRegistry()
                    .getEncryptedValue(SMTPConfigurationRegistryKeys.PASSWORD.key())
                    .isPresent();
        } catch(RegistryCryptoException e) {
            LOG.error("Could not decrypt encrypted registry value.", e);
            return Response.serverError().build();
        }

        SmtpConfigurationResponse config = SmtpConfigurationResponse.create(
                ConfigurationEntryResponse.create(
                        SMTPConfigurationRegistryKeys.TRANSPORT_STRATEGY.key(),
                        "Transport Strategy",
                        transportStrategy,
                        ConfigurationEntryValueType.ENUM_STRINGS,
                        SMTPConfigurationRegistryKeys.TRANSPORT_STRATEGY.defaultValue().orElse(null),
                        SMTPConfigurationRegistryKeys.TRANSPORT_STRATEGY.requiresRestart(),
                        SMTPConfigurationRegistryKeys.TRANSPORT_STRATEGY.constraints().orElse(Collections.emptyList()),
                        "smtp-config"
                ),
                ConfigurationEntryResponse.create(
                        SMTPConfigurationRegistryKeys.HOST.key(),
                        "Hostname",
                        hostname,
                        ConfigurationEntryValueType.STRING,
                        SMTPConfigurationRegistryKeys.HOST.defaultValue().orElse(null),
                        SMTPConfigurationRegistryKeys.HOST.requiresRestart(),
                        SMTPConfigurationRegistryKeys.HOST.constraints().orElse(Collections.emptyList()),
                        "smtp-config"
                ),
                ConfigurationEntryResponse.create(
                        SMTPConfigurationRegistryKeys.PORT.key(),
                        "Port",
                        port,
                        ConfigurationEntryValueType.NUMBER,
                        SMTPConfigurationRegistryKeys.PORT.defaultValue().orElse(null),
                        SMTPConfigurationRegistryKeys.PORT.requiresRestart(),
                        SMTPConfigurationRegistryKeys.PORT.constraints().orElse(Collections.emptyList()),
                        "smtp-config"
                ),
                ConfigurationEntryResponse.create(
                        SMTPConfigurationRegistryKeys.USERNAME.key(),
                        "Username",
                        username,
                        ConfigurationEntryValueType.STRING,
                        SMTPConfigurationRegistryKeys.USERNAME.defaultValue().orElse(null),
                        SMTPConfigurationRegistryKeys.USERNAME.requiresRestart(),
                        SMTPConfigurationRegistryKeys.USERNAME.constraints().orElse(Collections.emptyList()),
                        "smtp-config"
                ),
                EncryptedConfigurationEntryResponse.create(
                        SMTPConfigurationRegistryKeys.PASSWORD.key(),
                        "Password",
                        passwordIsSet,
                        ConfigurationEntryValueType.STRING_ENCRYPTED,
                        SMTPConfigurationRegistryKeys.PASSWORD.requiresRestart(),
                        SMTPConfigurationRegistryKeys.PASSWORD.constraints().orElse(Collections.emptyList()),
                        "smtp-config"
                ),
                ConfigurationEntryResponse.create(
                        SMTPConfigurationRegistryKeys.FROM_ADDRESS.key(),
                        "From Address",
                        fromAddress,
                        ConfigurationEntryValueType.STRING,
                        SMTPConfigurationRegistryKeys.FROM_ADDRESS.defaultValue().orElse(null),
                        SMTPConfigurationRegistryKeys.FROM_ADDRESS.requiresRestart(),
                        SMTPConfigurationRegistryKeys.FROM_ADDRESS.constraints().orElse(Collections.emptyList()),
                        "smtp-config"
                ),
                ConfigurationEntryResponse.create(
                        SMTPConfigurationRegistryKeys.WEB_INTERFACE_URL.key(),
                        "URL of Nzyme Web Interface",
                        webInterfaceUrl,
                        ConfigurationEntryValueType.STRING,
                        SMTPConfigurationRegistryKeys.WEB_INTERFACE_URL.defaultValue().orElse(null),
                        SMTPConfigurationRegistryKeys.WEB_INTERFACE_URL.requiresRestart(),
                        SMTPConfigurationRegistryKeys.WEB_INTERFACE_URL.constraints().orElse(Collections.emptyList()),
                        "smtp-config"
                )
        );

        return Response.ok(config).build();
    }

    @PUT
    @Path("/configuration")
    @Operation(operationId = "updateSmtpConfiguration", summary = "Update SMTP configuration",
            description = "Each submitted value is validated against the constraints of its configuration key before "
                    + "it is stored. The password is stored encrypted. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "422", description = "No changes were submitted, or a value failed validation.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The password could not be encrypted.", content = @Content)
    public Response updateSmtpConfiguration(@RequestBody(description = "Configuration keys and their new values.", required = true, content = @Content(mediaType = "application/json"))
                                            SmtpIntegrationConfigurationUpdateRequest ur) {
        if (ur.change().isEmpty()) {
            LOG.info("Empty configuration parameters.");
            return Response.status(422).build();
        }

        for (Map.Entry<String, Object> c : ur.change().entrySet()) {
            boolean encrypted = false;
            switch (c.getKey()) {
                case "smtp_transport_strategy":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(SMTPConfigurationRegistryKeys.TRANSPORT_STRATEGY, c)) {
                        return Response.status(422).build();
                    }
                    break;
                case "smtp_host":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(SMTPConfigurationRegistryKeys.HOST, c)) {
                        return Response.status(422).build();
                    }
                    break;
                case "smtp_port":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(SMTPConfigurationRegistryKeys.PORT, c)) {
                        return Response.status(422).build();
                    }
                    break;
                case "smtp_username":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(SMTPConfigurationRegistryKeys.USERNAME, c)) {
                        return Response.status(422).build();
                    }
                    break;
                case "smtp_password":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(SMTPConfigurationRegistryKeys.PASSWORD, c)) {
                        return Response.status(422).build();
                    }
                    encrypted = true;
                    break;
                case "smtp_from_address":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(SMTPConfigurationRegistryKeys.FROM_ADDRESS, c)) {
                        return Response.status(422).build();
                    }
                    break;
                case "smtp_web_interface_url":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(SMTPConfigurationRegistryKeys.WEB_INTERFACE_URL, c)) {
                        return Response.status(422).build();
                    }
                    break;
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
