package app.nzyme.core.rest.resources.system;

import app.nzyme.core.NzymeNode;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/system/registry")
@RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Registry", description = "The registry holds the configuration values of an Nzyme cluster.")
public class RegistryResource {

    @Inject
    private NzymeNode nzyme;

    @DELETE
    @Path("/show/{key}")
    @Operation(operationId = "deleteRegistryValue", summary = "Delete a registry value",
            description = "Deletes the cluster-wide value stored under a registry key. The key falls back to its "
                    + "default value afterwards. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Registry value deleted.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The key was empty.", content = @Content)
    public Response indicators(@Parameter(description = "Registry key to delete.") @PathParam("key") String key) {
        if (key == null || key.trim().isEmpty()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        nzyme.getDatabaseCoreRegistry().deleteValue(key);

        return Response.ok().build();
    }

}
