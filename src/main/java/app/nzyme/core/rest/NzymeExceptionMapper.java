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

package app.nzyme.core.rest;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.server.ParamException;

import jakarta.ws.rs.core.Response;
import tools.jackson.databind.exc.ValueInstantiationException;

import java.sql.SQLException;
import java.util.Set;

@Provider
public class NzymeExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = LogManager.getLogger(NzymeExceptionMapper.class);

    // What PostgreSQL reports for a value it cannot read as a regular expression, or as the type it is cast to.
    // Such values come from the request, like the regular expression or the address of a filter.
    private static final Set<String> INVALID_VALUE_SQL_STATES = Set.of("2201B", "22P02");

    @Override
    public Response toResponse(Throwable t) {
        if (t instanceof NotFoundException) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (t instanceof ParamException || t instanceof IllegalArgumentException) {
            LOG.debug(t);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (causedByInvalidValue(t)) {
            LOG.debug(t);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        // AutoValue JSON creation error. Swallow but also log because this is likely a programming problem.
        if (t instanceof ValueInstantiationException) {
            LOG.error(t);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        LOG.error("Error while handling REST call.", t);
        return Response.serverError().build();
    }

    private static boolean causedByInvalidValue(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof SQLException) {
                String state = ((SQLException) cause).getSQLState();
                if (state != null && INVALID_VALUE_SQL_STATES.contains(state)) {
                    return true;
                }
            }
        }

        return false;
    }

}