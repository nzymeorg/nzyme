package app.nzyme.core.rest.resources.system.authentication.mgmt;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.rest.requests.CreateUserRequest;
import app.nzyme.core.security.authentication.PasswordHasher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/system/authentication/mgmt/initialuser")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Initial Setup", description = "Creates the first super administrator of a fresh Nzyme installation.")
public class InitialUserResource {

    private static final Logger LOG = LogManager.getLogger(InitialUserResource.class);

    @Inject
    private NzymeNode nzyme;

    @POST
    @Operation(operationId = "createInitialUser", summary = "Create the initial super administrator",
            description = "Creates the first super administrator of an Nzyme installation. This endpoint needs no "
                    + "authentication, but it only works while no super administrator exists. It is rejected as soon "
                    + "as the first user has been created.")
    @ApiResponse(responseCode = "201", description = "Initial super administrator created.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The email address, password or name did not pass validation.",
            content = @Content)
    @ApiResponse(responseCode = "403", description = "A super administrator already exists, so initial setup is "
            + "no longer available.", content = @Content)
    public Response createInitialUser(@RequestBody(description = "Email address, password, name and whether "
            + "multi-factor authentication is disabled for this user.", required = true, content = @Content(mediaType = "application/json")) CreateUserRequest req) {
        if (nzyme.getAuthenticationService().countSuperAdministrators() > 0) {
            LOG.warn("Attempt to access initial user creation but users already exist.");
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        if (!OrganizationsResource.validateCreateUserRequest(req)) {
            LOG.info("Invalid parameters in create initial user request.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        PasswordHasher hasher = new PasswordHasher(nzyme.getMetrics());
        PasswordHasher.GeneratedHashAndSalt hash = hasher.createHash(req.password());

        LOG.info("Creating initial user [{}].", req.email());

        nzyme.getAuthenticationService().createSuperAdministrator(
                req.name(),
                req.email().toLowerCase(),
                req.disableMfa(),
                hash
        );

        return Response.status(Response.Status.CREATED).build();
    }

}
