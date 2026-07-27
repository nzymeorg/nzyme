package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.rtsp.RTSP;
import app.nzyme.core.ethernet.rtsp.db.RTSPStreamEntry;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.rtsp.RTSPStreamDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.rtsp.RTSPStreamsListResponse;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/rtsp")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class RTSPResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/streams")
    public Response streams(@Context SecurityContext sc,
                            @QueryParam("organization_id") UUID organizationId,
                            @QueryParam("tenant_id") UUID tenantId,
                            @QueryParam("time_range") @Valid String timeRangeParameter,
                            @QueryParam("filters") String filtersParameter,
                            @QueryParam("order_column") @Nullable String orderColumnParam,
                            @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @QueryParam("limit") int limit,
                            @QueryParam("offset") int offset,
                            @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        RTSP.OrderColumn orderColumn = RTSP.OrderColumn.SETUP_ESTABLISHED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = RTSP.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().rtsp().countAllStreams(timeRange, filters, taps);

        List<RTSPStreamDetailsResponse> sessions = Lists.newArrayList();
        for (RTSPStreamEntry session : nzyme.getEthernet().rtsp()
                .findAllStreams(timeRange, filters, orderColumn, orderDirection, limit, offset, taps)) {
            sessions.add(RTSPStreamDetailsResponse.create(session.setupTcpSessionKey(), session.state()));
        }

        return Response.ok(RTSPStreamsListResponse.create(total, sessions)).build();
    }

}