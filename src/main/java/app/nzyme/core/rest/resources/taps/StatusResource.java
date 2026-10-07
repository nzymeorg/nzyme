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

package app.nzyme.core.rest.resources.taps;

import io.swagger.v3.oas.annotations.Hidden;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.rest.authentication.AuthenticatedTap;
import app.nzyme.core.rest.authentication.TapSecured;
import app.nzyme.core.rest.resources.taps.reports.HelloReport;
import app.nzyme.core.rest.resources.taps.reports.StatusReport;
import app.nzyme.core.rest.resources.taps.reports.context.TapContextReport;
import jakarta.ws.rs.POST;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.glassfish.grizzly.http.server.Request;

import java.util.UUID;

@Hidden // Not part of the user-facing REST API.
@Path("/api/taps")
@TapSecured
@Produces(MediaType.APPLICATION_JSON)
public class StatusResource {

    private static final Logger LOG = LogManager.getLogger(StatusResource.class);

    @Inject
    private NzymeNode nzyme;

    @Path("/hello")
    @POST
    public Response hello(@Context SecurityContext sc, HelloReport report) {
        UUID tapId = ((AuthenticatedTap) sc.getUserPrincipal()).getUuid();

        LOG.debug("Received hello from tap [{}]: {}", tapId, report);
        nzyme.getTapManager().registerTapHello(report, tapId);

        return Response.status(Response.Status.CREATED).build();
    }

    @Path("/status")
    @POST
    public Response status(@Context SecurityContext sc, @Context Request request, StatusReport report) {
        UUID tapId = ((AuthenticatedTap) sc.getUserPrincipal()).getUuid();

        LOG.debug("Received status from tap [{}]: {}", tapId, report);
        nzyme.getTapManager().registerTapStatus(report, request.getRemoteAddr(), tapId);

        return Response.status(Response.Status.CREATED).build();
    }

    @Path("/context")
    @POST
    public Response context(@Context SecurityContext sc, TapContextReport report) {
        UUID tapId = ((AuthenticatedTap) sc.getUserPrincipal()).getUuid();

        LOG.debug("Received context report from tap [{}]: {}", tapId, report);
        nzyme.getTapManager().registerTapContext(report, tapId);

        return Response.status(Response.Status.CREATED).build();
    }

}
