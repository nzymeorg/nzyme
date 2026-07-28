package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.rtsp.RTSP;
import app.nzyme.core.ethernet.rtsp.db.RTSPStreamEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
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
import org.joda.time.DateTime;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static app.nzyme.core.rest.RestHelpers.tcpSessionStateToGeneric;
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

        ObjectMapper om = new ObjectMapper();

        long total = nzyme.getEthernet().rtsp().countAllStreams(timeRange, filters, taps);

        List<RTSPStreamDetailsResponse> streams = Lists.newArrayList();
        for (RTSPStreamEntry stream : nzyme.getEthernet().rtsp()
                .findAllStreams(timeRange, filters, orderColumn, orderDirection, limit, offset, taps)) {
            Map<String, Object> mediaLocatorResponse = null;
            if (stream.mediaLocator() != null && !stream.mediaLocator().isEmpty()) {
                mediaLocatorResponse = om.readValue(stream.mediaLocator(), new TypeReference<>() {});
            }

            L4AddressResponse setupSource = null;
            L4AddressResponse setupDestination = null;
            L4AddressResponse streamSource = null;
            L4AddressResponse streamDestination = null;

            if (stream.setupSource() != null) {
                setupSource = RestHelpers.L4AddressDataToResponse(
                        nzyme, organizationId, tenantId, L4Type.TCP, stream.setupSource()
                );
            }

            if (stream.setupDestination() != null) {
                setupDestination = RestHelpers.L4AddressDataToResponse(
                        nzyme, organizationId, tenantId, L4Type.TCP, stream.setupDestination()
                );
            }

            if (stream.streamSource() != null) {
                streamSource = RestHelpers.L4AddressDataToResponse(
                        nzyme, organizationId, tenantId, L4Type.TCP, stream.streamSource()
                );
            }

            if (stream.streamDestination() != null) {
                streamDestination = RestHelpers.L4AddressDataToResponse(
                        nzyme, organizationId, tenantId, L4Type.TCP, stream.streamDestination()
                );
            }

            DateTime lastActivity = null;
            Boolean isActive = null;
            if (stream.setupMostRecentSegmentTime() != null && stream.streamMostRecentSegmentTime() != null) {
                if (stream.setupMostRecentSegmentTime().isAfter(stream.streamMostRecentSegmentTime())) {
                    lastActivity = stream.setupMostRecentSegmentTime();
                } else {
                    lastActivity = stream.streamMostRecentSegmentTime();
                }

                isActive = lastActivity.isAfter(DateTime.now().minusMinutes(1));
            }

            streams.add(RTSPStreamDetailsResponse.create(
                    stream.setupTcpSessionKey(),
                    isActive,
                    stream.state(),
                    mediaLocatorResponse,
                    stream.requestUri(),
                    stream.clientAgent(),
                    stream.serverInfo(),
                    stream.authentication(),
                    stream.flags(),
                    lastActivity,
                    stream.setupConnectionStatus(),
                    stream.setupEstablishedAt(),
                    stream.setupTerminatedAt(),
                    stream.setupMostRecentSegmentTime(),
                    setupSource,
                    setupDestination,
                    stream.setupBytesExchanged(),
                    stream.streamL4Type(),
                    streamSource,
                    streamDestination,
                    stream.streamBytesRx(),
                    stream.streamBytesTx()
            ));
        }

        return Response.ok(RTSPStreamsListResponse.create(total, streams)).build();
    }

}