package app.nzyme.core.rest.resources.system;

import app.nzyme.core.NzymeNode;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/system/plugins")
@RESTSecured(PermissionLevel.ANY)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Plugins", description = "Plugins extend Nzyme with additional functionality. These endpoints report "
        + "which plugins a node loaded.")
public class PluginResource {

    @Inject
    NzymeNode nzyme;

    @GET
    @Path("/names")
    @Operation(operationId = "findPluginNames", summary = "List loaded plugins",
            description = "Returns the names of all plugins this Nzyme node initialized during startup. The list is "
                    + "empty if no plugins are installed. Available to any user.")
    @ApiResponse(responseCode = "200", description = "Plugin names found.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class))))
    public Response getNames() {
        return Response.ok(nzyme.getInitializedPlugins()).build();
    }

}
