package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.StringNumberNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.ssh.SSH;
import app.nzyme.core.ethernet.ssh.db.SSHSessionEntry;
import app.nzyme.core.ethernet.l4.tcp.db.TcpSessionEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import app.nzyme.core.rest.responses.ethernet.ssh.SSHSessionDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.ssh.SSHSessionsListResponse;
import app.nzyme.core.rest.responses.ethernet.ssh.SSHVersionResponse;
import app.nzyme.core.rest.responses.shared.*;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/ssh")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class SSHResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/sessions")
    public Response sessions(@Context SecurityContext sc,
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

        SSH.OrderColumn orderColumn = SSH.OrderColumn.ESTABLISHED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = SSH.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().ssh().countAllSessions(timeRange, filters, taps);

        List<SSHSessionDetailsResponse> sessions = Lists.newArrayList();
        for (SSHSessionEntry s : nzyme.getEthernet().ssh()
                .findAllSessions(timeRange, filters, orderColumn, orderDirection, limit, offset, taps)) {
            sessions.add(buildSessionDetails(s, organizationId, tenantId, taps));
        }

        return Response.ok(SSHSessionsListResponse.create(total, sessions)).build();
    }


    @GET
    @Path("/sessions/show/{session_key}")
    public Response session(@Context SecurityContext sc,
                            @PathParam("session_key") String sessionKey,
                            @QueryParam("organization_id") UUID organizationId,
                            @QueryParam("tenant_id") UUID tenantId,
                            @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<SSHSessionEntry> session = nzyme.getEthernet().ssh().findSession(sessionKey, taps);

        if (session.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(buildSessionDetails(session.get(), organizationId, tenantId, taps)).build();
    }

    @GET
    @Path("/sessions/active/histogram")
    public Response activeSessionsHistogram(@Context SecurityContext sc,
                                            @QueryParam("time_range") @Valid String timeRangeParameter,
                                            @QueryParam("filters") String filtersParameter,
                                            @QueryParam("taps") String taps) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Map<DateTime, Integer> buckets = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().ssh()
                .getActiveSessionsHistogram(timeRange, bucketing, filters, tapUUIDs)) {
            buckets.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(NumericHistogramResponse.create(buckets, bucketing.bucketSizeMs())).build();
    }

    @GET
    @Path("/sessions/clients/top/histogram")
    public Response topClientsHistogram(@Context SecurityContext sc,
                                        @QueryParam("organization_id") UUID organizationId,
                                        @QueryParam("tenant_id") UUID tenantId,
                                        @QueryParam("time_range") @Valid String timeRangeParameter,
                                        @QueryParam("filters") String filtersParameter,
                                        @QueryParam("taps") String taps,
                                        @QueryParam("order_column") @Nullable String orderColumnParam,
                                        @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                        @QueryParam("limit") int limit,
                                        @QueryParam("offset") int offset) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ThreeColumnWithKeyHistogramOrderColumn orderColumn = ThreeColumnWithKeyHistogramOrderColumn.VALUE1;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ThreeColumnWithKeyHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long count = nzyme.getEthernet().ssh().getTopClientsCount(timeRange, filters, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (L4AddressDataAddressNumberNumberAggregationResult x : nzyme.getEthernet().ssh()
                .getTopClients(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.UDP, x.key()),
                            HistogramValueType.L4_ADDRESS,
                            null),
                    HistogramValueStructureResponse.create(x.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(x.value2(), HistogramValueType.BYTES, null),
                    x.key().address()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, true, values)).build();
    }

    @GET
    @Path("/sessions/servers/top/histogram")
    public Response topServersHistogram(@Context SecurityContext sc,
                                        @QueryParam("organization_id") UUID organizationId,
                                        @QueryParam("tenant_id") UUID tenantId,
                                        @QueryParam("time_range") @Valid String timeRangeParameter,
                                        @QueryParam("filters") String filtersParameter,
                                        @QueryParam("taps") String taps,
                                        @QueryParam("order_column") @Nullable String orderColumnParam,
                                        @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                        @QueryParam("limit") int limit,
                                        @QueryParam("offset") int offset) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ThreeColumnWithKeyHistogramOrderColumn orderColumn = ThreeColumnWithKeyHistogramOrderColumn.VALUE1;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ThreeColumnWithKeyHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long count = nzyme.getEthernet().ssh().getTopServersCount(timeRange, filters, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (L4AddressDataAddressNumberNumberAggregationResult x : nzyme.getEthernet().ssh()
                .getTopServers(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.UDP, x.key()),
                            HistogramValueType.L4_ADDRESS,
                            null),
                    HistogramValueStructureResponse.create(x.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(x.value2(), HistogramValueType.BYTES, null),
                    x.key().address()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, true, values)).build();
    }

    @GET
    @Path("/sessions/clients/types/top/histogram")
    public Response topClientTypesHistogram(@Context SecurityContext sc,
                                            @QueryParam("organization_id") UUID organizationId,
                                            @QueryParam("tenant_id") UUID tenantId,
                                            @QueryParam("time_range") @Valid String timeRangeParameter,
                                            @QueryParam("filters") String filtersParameter,
                                            @QueryParam("taps") String taps,
                                            @QueryParam("order_column") @Nullable String orderColumnParam,
                                            @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                            @QueryParam("limit") int limit,
                                            @QueryParam("offset") int offset) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ThreeColumnWithKeyHistogramOrderColumn orderColumn = ThreeColumnWithKeyHistogramOrderColumn.VALUE1;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ThreeColumnWithKeyHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long count = nzyme.getEthernet().ssh().getTopClientTypesCount(timeRange, filters, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (StringNumberNumberAggregationResult x : nzyme.getEthernet().ssh()
                .getTopClientTypes(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            x.key(),
                            HistogramValueType.GENERIC,
                            null),
                    HistogramValueStructureResponse.create(x.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(x.value2(), HistogramValueType.BYTES, null),
                    x.key()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, true, values)).build();
    }

    @GET
    @Path("/sessions/servers/types/top/histogram")
    public Response topServerTypesHistogram(@Context SecurityContext sc,
                                            @QueryParam("organization_id") UUID organizationId,
                                            @QueryParam("tenant_id") UUID tenantId,
                                            @QueryParam("time_range") @Valid String timeRangeParameter,
                                            @QueryParam("filters") String filtersParameter,
                                            @QueryParam("taps") String taps,
                                            @QueryParam("order_column") @Nullable String orderColumnParam,
                                            @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                            @QueryParam("limit") int limit,
                                            @QueryParam("offset") int offset) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ThreeColumnWithKeyHistogramOrderColumn orderColumn = ThreeColumnWithKeyHistogramOrderColumn.VALUE1;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ThreeColumnWithKeyHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long count = nzyme.getEthernet().ssh().getTopServerTypesCount(timeRange, filters, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (StringNumberNumberAggregationResult x : nzyme.getEthernet().ssh()
                .getTopServerTypes(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            x.key(),
                            HistogramValueType.GENERIC,
                            null),
                    HistogramValueStructureResponse.create(x.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(x.value2(), HistogramValueType.BYTES, null),
                    x.key()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, true, values)).build();
    }

    private SSHSessionDetailsResponse buildSessionDetails(SSHSessionEntry s,
                                                          UUID organizationId,
                                                          UUID tenantId,
                                                          List<UUID> taps) {
        // Get underlying TCP session. (Can be NULL)
        Optional<TcpSessionEntry> tcpSession = nzyme.getEthernet().tcp()
                .findSessionBySessionKey(s.tcpSessionKey(), s.establishedAt(), taps);

        L4AddressResponse client = null;
        L4AddressResponse server = null;
        if (tcpSession.isPresent()) {
            client = RestHelpers.L4AddressDataToResponse(
                    nzyme, organizationId, tenantId, L4Type.TCP, tcpSession.get().source()
            );
            server = RestHelpers.L4AddressDataToResponse(
                    nzyme, organizationId, tenantId, L4Type.TCP, tcpSession.get().destination()
            );
        }

        return SSHSessionDetailsResponse.create(
                s.tcpSessionKey(),
                client,
                server,
                SSHVersionResponse.create(
                        s.clientVersionVersion(),
                        s.clientVersionSoftware(),
                        s.clientVersionComments()
                ),
                SSHVersionResponse.create(
                        s.serverVersionVersion(),
                        s.serverVersionSoftware(),
                        s.serverVersionComments()
                ),
                tcpSession.map(tcpSessionEntry -> RestHelpers.tcpSessionStateToGeneric(tcpSessionEntry.state()))
                        .orElse("Invalid"),
                s.tunneledBytes(),
                s.establishedAt(),
                s.terminatedAt(),
                s.mostRecentSegmentTime(),
                s.durationMs()
        );
    }

}