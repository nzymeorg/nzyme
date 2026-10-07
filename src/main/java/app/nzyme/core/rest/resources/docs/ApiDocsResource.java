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
package app.nzyme.core.rest.resources.docs;

import com.google.common.io.ByteStreams;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;

@Hidden // Not part of the user-facing REST API.
@Path("/api/docs")
public class ApiDocsResource {

    private static final Logger LOG = LogManager.getLogger(ApiDocsResource.class);

    private static final String SPEC_RESOURCE = "openapi/openapi.json";

    @GET
    @Path("/openapi.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response spec() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(SPEC_RESOURCE)) {
            if (in == null) {
                LOG.error("OpenAPI spec [{}] not found on classpath. Was the build run with the swagger plugin?",
                        SPEC_RESOURCE);
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            return Response.ok(ByteStreams.toByteArray(in)).build();
        } catch (IOException e) {
            LOG.error("Could not read OpenAPI spec.", e);
            return Response.serverError().build();
        }
    }

}
