/*
 * This file is part of nzyme.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */

package app.nzyme.core.rest.resources.system;

import java.util.Optional;
import java.util.List;
import app.nzyme.core.registry.RegistryChangeValidator;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.branding.BrandingRegistryKeys;
import app.nzyme.core.distributed.NodeRegistryKeys;
import app.nzyme.core.rest.requests.UpdateConfigurationRequest;
import app.nzyme.core.rest.requests.UpdateSidebarTitleRequest;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
import app.nzyme.core.rest.responses.subsystems.SubsystemsConfigurationResponse;
import app.nzyme.core.rest.responses.system.SidebarTitleResponse;
import app.nzyme.core.subsystems.SubsystemRegistryKeys;
import app.nzyme.plugin.Subsystem;
import app.nzyme.plugin.rest.configuration.*;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import app.nzyme.core.rest.responses.system.VersionResponse;

import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

@Path("/api/system")
@RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "System", description = "System-wide settings of an Nzyme cluster: version information, subsystems and "
        + "the look and feel of the web interface.")
public class SystemResource {

    private static final Logger LOG = LogManager.getLogger(SystemResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/status")
    @Operation(operationId = "findSystemStatus", summary = "Check if the node is responding",
            description = "Always returns an empty 200 response if the node is up and the request was authenticated. "
                    + "Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "The node is responding.", content = @Content)
    public Response getStatus() {
        return Response.ok().build();
    }

    @GET
    @Path("/version")
    @Operation(operationId = "findSystemVersion", summary = "Get version information",
            description = "Returns the version of this Nzyme node, whether a newer version is available and whether "
                    + "version checks are enabled in the node configuration. Requires super administrator "
                    + "permissions.")
    @ApiResponse(responseCode = "200", description = "Version information found.",
            content = @Content(schema = @Schema(implementation = VersionResponse.class)))
    public Response getVersion() {
        boolean newVersionAvailable = Boolean.valueOf(nzyme.getDatabaseCoreRegistry()
                .getValue(NodeRegistryKeys.VERSIONCHECK_STATUS.key())
                .orElse("false")
        );

        return Response.ok(VersionResponse.create(
                nzyme.getVersion().getVersionString(),
                newVersionAvailable,
                nzyme.getConfiguration().versionchecksEnabled()
        )).build();
    }

    @GET
    @Path("/lookandfeel/sidebartitle")
    @Operation(operationId = "findSidebarTitle", summary = "Get the sidebar title",
            description = "Returns the title and subtitle shown in the sidebar of the web interface. The title falls "
                    + "back to the built-in default if it was never set. The subtitle is null if it was never set. "
                    + "Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Sidebar title found.",
            content = @Content(schema = @Schema(implementation = SidebarTitleResponse.class)))
    public Response getSidebarTitle() {
        //noinspection OptionalGetWithoutIsPresent
        String title = nzyme.getDatabaseCoreRegistry()
                .getValue(BrandingRegistryKeys.SIDEBAR_TITLE_TEXT.key())
                .orElse(BrandingRegistryKeys.SIDEBAR_TITLE_TEXT.defaultValue().get());
        String subtitle = nzyme.getDatabaseCoreRegistry()
                .getValueOrNull(BrandingRegistryKeys.SIDEBAR_SUBTITLE_TEXT.key());

        return Response.ok(SidebarTitleResponse.create(title, subtitle)).build();
    }

    @PUT
    @Path("/lookandfeel/sidebartitle")
    @Operation(operationId = "updateSidebarTitle", summary = "Update the sidebar title",
            description = "Sets the title and subtitle shown in the sidebar of the web interface. An empty subtitle "
                    + "removes the stored subtitle. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Sidebar title updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The title or subtitle did not pass validation. The response "
            + "body explains why.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response setSidebarTitle(@RequestBody(description = "New sidebar title and optional subtitle.",
            required = true, content = @Content(mediaType = "application/json")) UpdateSidebarTitleRequest req) {
        for (ConfigurationEntryConstraint c : BrandingRegistryKeys.SIDEBAR_TITLE_TEXT.constraints().get()) {
            ConstraintValidationResult result = ConstraintValidator.validate(req.title(), c);
            if (!result.isOk()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ErrorResponse.create(result.getReason()))
                        .build();
            }
        }

        if (!Strings.isNullOrEmpty(req.subtitle())) {
            for (ConfigurationEntryConstraint c : BrandingRegistryKeys.SIDEBAR_SUBTITLE_TEXT.constraints().get()) {
                ConstraintValidationResult result = ConstraintValidator.validate(req.subtitle(), c);
                if (!result.isOk()) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(ErrorResponse.create(result.getReason()))
                            .build();
                }
            }
        }

        nzyme.getDatabaseCoreRegistry().setValue(BrandingRegistryKeys.SIDEBAR_TITLE_TEXT.key(), req.title());

        if (!Strings.isNullOrEmpty(req.subtitle())) {
            nzyme.getDatabaseCoreRegistry().setValue(BrandingRegistryKeys.SIDEBAR_SUBTITLE_TEXT.key(), req.subtitle());
        } else {
            nzyme.getDatabaseCoreRegistry().deleteValue(BrandingRegistryKeys.SIDEBAR_SUBTITLE_TEXT.key());
        }

        return Response.ok().build();
    }

    @POST
    @Path("/lookandfeel/loginimage")
    @Operation(operationId = "uploadLoginImage", summary = "Upload the login page image",
            description = "Uploads the image shown on the login page of the web interface. The file is sent as "
                    + "multipart form data. It must be a JPG or PNG file, no larger than 1MB, and exactly 700x600 "
                    + "pixels. Nzyme converts it to PNG before storing it. Requires super administrator permissions.")
    @ApiResponse(responseCode = "201", description = "Login image stored.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The file was empty, too large, not a readable image or had the "
            + "wrong dimensions. The response body explains why.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "500", description = "The uploaded file could not be read.", content = @Content)
    @RequestBody(description = "Multipart form with the image file to show on the login page.", required = true,
            content = @Content(mediaType = "multipart/form-data",
                    schemaProperties = @SchemaProperty(name = "image",
                            schema = @Schema(type = "string", format = "binary", description = "The file to upload."))))
    public Response uploadLoginImage(@Parameter(hidden = true)
                                     @FormDataParam("image") InputStream imageFile) {
        byte[] imageBytes;
        try {
            /*
             * Set the limit to 1MB plus 1 byte. If it fills this entirely, a file too large was uploaded.
             * There is also a larger limit in the HTTP server itself.
             */
            imageBytes = ByteStreams.limit(imageFile, 1048576).readAllBytes();
        } catch (IOException e) {
            LOG.error(e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        if (imageBytes.length == 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.create("Uploaded file is empty."))
                    .build();
        }

        if (imageBytes.length == 5242881) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.create("Uploaded  file is too large. Maximum file size is 1MB."))
                    .build();
        }

        try {
            // New input stream because the previous one had been consumed when checking the length.
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));

            if (image == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ErrorResponse.create("Could not read image file. Make sure it is a JPG or PNG file."))
                        .build();
            }

            if (image.getHeight() != 600 && image.getWidth() != 700) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ErrorResponse.create("Image must be 700x600px."))
                        .build();
            }

            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            ImageIO.write(image, "png", pngOut);


            // Convert to Base64 and store in registry.
            nzyme.getDatabaseCoreRegistry().setValue(
                    BrandingRegistryKeys.LOGIN_IMAGE.key(),
                    BaseEncoding.base64().encode(pngOut.toByteArray())
            );
        } catch (Exception e) {
            LOG.warn("Could not process uploaded file.", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.create("Could not process uploaded file. Make sure it is a JPG or " +
                            "PNG file."))
                    .build();
        }

        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @Path("/lookandfeel/loginimage")
    @Operation(operationId = "resetLoginImage", summary = "Reset the login page image",
            description = "Removes a previously uploaded login page image. The login page falls back to the default "
                    + "Nzyme image. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Login image reset.", content = @Content)
    public Response resetLoginImage() {
        nzyme.getDatabaseCoreRegistry().deleteValue(BrandingRegistryKeys.LOGIN_IMAGE.key());
        return Response.ok().build();
    }

    @GET
    @Path("/subsystems/configuration")
    @Operation(operationId = "findSubsystemsConfiguration", summary = "Get subsystem configuration",
            description = "Returns the cluster-wide activation state of the Ethernet, WiFi, Bluetooth and UAV "
                    + "subsystems. Organizations and tenants can disable subsystems further down, but they can never "
                    + "enable a subsystem that is disabled here. Requires super administrator permissions.",
            externalDocs = @ExternalDocumentation(description = "Subsystems in the Nzyme documentation",
                    url = "https://go.nzyme.org/subsystems"))
    @ApiResponse(responseCode = "200", description = "Subsystem configuration found.",
            content = @Content(schema = @Schema(implementation = SubsystemsConfigurationResponse.class)))
    public Response getSubsystemsConfiguration() {
        SubsystemsConfigurationResponse response = SubsystemsConfigurationResponse.create(
                true,
                true,
                true,
                true,
                ConfigurationEntryResponse.create(
                        SubsystemRegistryKeys.ETHERNET_ENABLED.key(),
                        "Ethernet is enabled",
                        nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, null, null),
                        ConfigurationEntryValueType.BOOLEAN,
                        SubsystemRegistryKeys.ETHERNET_ENABLED.defaultValue().orElse(null),
                        SubsystemRegistryKeys.ETHERNET_ENABLED.requiresRestart(),
                        SubsystemRegistryKeys.ETHERNET_ENABLED.constraints().orElse(Collections.emptyList()),
                        "subsystems"
                ),
                ConfigurationEntryResponse.create(
                        SubsystemRegistryKeys.DOT11_ENABLED.key(),
                        "WiFi/802.11 is enabled",
                        nzyme.getSubsystems().isEnabled(Subsystem.DOT11, null, null),
                        ConfigurationEntryValueType.BOOLEAN,
                        SubsystemRegistryKeys.DOT11_ENABLED.defaultValue().orElse(null),
                        SubsystemRegistryKeys.DOT11_ENABLED.requiresRestart(),
                        SubsystemRegistryKeys.DOT11_ENABLED.constraints().orElse(Collections.emptyList()),
                        "subsystems"
                ),
                ConfigurationEntryResponse.create(
                        SubsystemRegistryKeys.BLUETOOTH_ENABLED.key(),
                        "Bluetooth is enabled",
                        nzyme.getSubsystems().isEnabled(Subsystem.BLUETOOTH, null, null),
                        ConfigurationEntryValueType.BOOLEAN,
                        SubsystemRegistryKeys.BLUETOOTH_ENABLED.defaultValue().orElse(null),
                        SubsystemRegistryKeys.BLUETOOTH_ENABLED.requiresRestart(),
                        SubsystemRegistryKeys.BLUETOOTH_ENABLED.constraints().orElse(Collections.emptyList()),
                        "subsystems"
                ),
                ConfigurationEntryResponse.create(
                        SubsystemRegistryKeys.UAV_ENABLED.key(),
                        "UAV is enabled",
                        nzyme.getSubsystems().isEnabled(Subsystem.UAV, null, null),
                        ConfigurationEntryValueType.BOOLEAN,
                        SubsystemRegistryKeys.UAV_ENABLED.defaultValue().orElse(null),
                        SubsystemRegistryKeys.UAV_ENABLED.requiresRestart(),
                        SubsystemRegistryKeys.UAV_ENABLED.constraints().orElse(Collections.emptyList()),
                        "subsystems"
                )
        );

        return Response.ok(response).build();
    }

    @PUT
    @Path("/subsystems/configuration")
    @Operation(operationId = "updateSubsystemsConfiguration", summary = "Update subsystem configuration",
            description = "Enables or disables subsystems cluster-wide. The body carries a change map of registry "
                    + "keys and their new values. Disabling a subsystem hides its pages in the web interface and "
                    + "turns off its API resources. It does not change the configuration of organizations and "
                    + "tenants, which takes effect again once you re-enable the subsystem. Requires super "
                    + "administrator permissions.",
            externalDocs = @ExternalDocumentation(description = "Subsystems in the Nzyme documentation",
                    url = "https://go.nzyme.org/subsystems"))
    @ApiResponse(responseCode = "200", description = "Subsystem configuration updated.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The change map was empty or a value did not pass the "
            + "constraints of its configuration key.", content = @Content)
    public Response updateSubsystemsConfiguration(@RequestBody(description = "Map of subsystem configuration keys "
            + "and their new values.", required = true, content = @Content(mediaType = "application/json")) UpdateConfigurationRequest req) {
        if (req.change().isEmpty()) {
            LOG.info("Empty configuration parameters.");
            return Response.status(422).build();
        }

        Optional<List<RegistryChangeValidator.Change>> changes = RegistryChangeValidator
                .allowing(SubsystemRegistryKeys.ETHERNET_ENABLED,
                        SubsystemRegistryKeys.DOT11_ENABLED,
                        SubsystemRegistryKeys.BLUETOOTH_ENABLED,
                        SubsystemRegistryKeys.UAV_ENABLED)
                .validate(req.change());

        if (changes.isEmpty()) {
            return Response.status(422).build();
        }

        for (RegistryChangeValidator.Change c : changes.get()) {
            nzyme.getDatabaseCoreRegistry().setValue(c.key(), c.value());
        }

        return Response.ok().build();
    }

}
