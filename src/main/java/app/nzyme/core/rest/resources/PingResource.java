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

package app.nzyme.core.rest.resources;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.branding.BrandingRegistryKeys;
import app.nzyme.core.rest.responses.system.PingResponse;
import io.swagger.v3.oas.annotations.Operation;
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

@Path("/api/ping")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Ping", description = "Unauthenticated liveness check of a Nzyme node.")
public class PingResource {

    /*
     * DANGER: This resource is entirely unauthenticated. Be careful with what you expose.
     */

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "ping", summary = "Check if the node is alive",
            description = "Responds as soon as the node is up and the database is reachable. This endpoint requires "
                    + "no authentication and returns only information needed by the login page, including whether "
                    + "initial setup is still required and the configured login image.")
    @ApiResponse(responseCode = "200", description = "The node is alive.",
            content = @Content(schema = @Schema(implementation = PingResponse.class)))
    public Response ping() {
        String loginImage = nzyme.getDatabaseCoreRegistry()
                .getValue(BrandingRegistryKeys.LOGIN_IMAGE.key())
                .orElse(null);

        return Response.ok(PingResponse.create(
                nzyme.getAuthenticationService().countSuperAdministrators() == 0,
                loginImage
        )).build();
    }

}
