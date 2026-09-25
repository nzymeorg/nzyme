package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.StringStringNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnHistogramOrderColumn;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import app.nzyme.core.ethernet.nat.NAT;
import app.nzyme.core.ethernet.nat.db.NATTraversalDiscoveryEntry;
import app.nzyme.core.ethernet.nat.db.NATTraversalDiscoveryHistogramBucket;
import app.nzyme.core.ethernet.nat.db.STUNNegotiationEntry;
import app.nzyme.core.ethernet.webrtc.db.WebRTCSessionEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.*;
import app.nzyme.core.rest.responses.ethernet.nat.NATSTUNNegotiationDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.nat.NATSTUNNegotiationsListResponse;
import app.nzyme.core.rest.responses.ethernet.nat.NATTraversalDiscoveryDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.nat.NATTraversalDiscoveryListResponse;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.rest.misc.NATHelper.buildNegotiationDetailsResponse;
import static app.nzyme.core.rest.misc.WebRTCHelper.buildWebRTCSessionDetailsResponse;
import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/nat")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class NATResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/traversal/stun/discoveries/show/{id}")
    public Response oneSTUNDiscovery(@Context SecurityContext sc,
                                     @PathParam("id") String id,
                                     @QueryParam("organization_id") UUID organizationId,
                                     @QueryParam("tenant_id") UUID tenantId,
                                     @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<NATTraversalDiscoveryEntry> discovery = nzyme.getEthernet().nat().findOneDiscovery(id, taps);

        if (discovery.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(buildDiscoveryDetailsResponse(discovery.get(), organizationId, tenantId)).build();
    }

    @GET
    @Path("/traversal/stun/discoveries")
    public Response allSTUNDiscoveries(@Context SecurityContext sc,
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

        NAT.DiscoveryOrderColumn orderColumn = NAT.DiscoveryOrderColumn.INITIATED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = NAT.DiscoveryOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().nat().countAllDiscoveries(timeRange, filters, taps);

        List<NATTraversalDiscoveryDetailsResponse> discoveries = Lists.newArrayList();
        for (NATTraversalDiscoveryEntry discovery : nzyme.getEthernet().nat()
                .findAllDiscoveries(timeRange, filters, orderColumn, orderDirection, limit, offset, taps)) {

            discoveries.add(buildDiscoveryDetailsResponse(discovery, organizationId, tenantId));
        }

        return Response.ok(NATTraversalDiscoveryListResponse.create(total, discoveries)).build();
    }

    @GET
    @Path("/traversal/stun/discoveries/histogram")
    public Response stunDiscoveriesHistogram(@Context SecurityContext sc,
                                             @QueryParam("time_range") @Valid String timeRangeParameter,
                                             @QueryParam("filters") String filtersParameter,
                                             @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Map<DateTime, Map<String, Long>> response = Maps.newHashMap();
        for (NATTraversalDiscoveryHistogramBucket bucket : nzyme.getEthernet().nat()
                .getTraversalDiscoveryHistogram(timeRange, bucketing, filters, taps)) {
            response.put(bucket.bucket(), Map.of(
                    "complete", bucket.completeCount(),
                    "incomplete", bucket.incompleteCount(),
                    "error", bucket.errorCount()
            ));
        }

        return Response.ok(response).build();
    }

    @GET
    @Path("/traversal/stun/clients/histogram")
    public Response stunDiscoveriesTopClientsHistogram(@Context SecurityContext sc,
                                                       @QueryParam("organization_id") UUID organizationId,
                                                       @QueryParam("tenant_id") UUID tenantId,
                                                       @QueryParam("time_range") String timeRangeParameter,
                                                       @QueryParam("filters") String filtersParameter,
                                                       @QueryParam("limit") int limit,
                                                       @QueryParam("offset") int offset,
                                                       @QueryParam("order_column") @Nullable String orderColumnParam,
                                                       @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                                       @QueryParam("taps") String taps) {
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

        long count = nzyme.getEthernet().nat().countTraversalDiscoveryTopClientsHistogram(timeRange, filters, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (L4AddressDataAddressNumberNumberAggregationResult x : nzyme.getEthernet().nat()
                .getTraversalDiscoveryTopClientsHistogram(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.UDP, x.key()),
                            HistogramValueType.L4_ADDRESS_NO_PORT,
                            null),
                    HistogramValueStructureResponse.create(x.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(x.value2(), HistogramValueType.BYTES, null),
                    x.key().address()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, false, values)).build();
    }

    @GET
    @Path("/traversal/stun/servers/histogram")
    public Response stunDiscoveriesTopServersHistogram(@Context SecurityContext sc,
                                                       @QueryParam("organization_id") UUID organizationId,
                                                       @QueryParam("tenant_id") UUID tenantId,
                                                       @QueryParam("time_range") String timeRangeParameter,
                                                       @QueryParam("filters") String filtersParameter,
                                                       @QueryParam("limit") int limit,
                                                       @QueryParam("offset") int offset,
                                                       @QueryParam("order_column") @Nullable String orderColumnParam,
                                                       @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                                       @QueryParam("taps") String taps) {
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

        long count = nzyme.getEthernet().nat().countTraversalDiscoveryTopServersHistogram(timeRange, filters, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (L4AddressDataAddressNumberNumberAggregationResult x : nzyme.getEthernet().nat()
                .getTraversalDiscoveryTopServersHistogram(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
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

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, false, values)).build();
    }

    @GET
    @Path("/traversal/stun/connections")
    public Response allSTUNConnections(@Context SecurityContext sc,
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

        NAT.NegotiationOrderColumn orderColumn = NAT.NegotiationOrderColumn.INITIATED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = NAT.NegotiationOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().nat().countAllNegotiations(timeRange, filters, taps);

        List<NATSTUNNegotiationDetailsResponse> negotiations = Lists.newArrayList();
        for (STUNNegotiationEntry negotiation : nzyme.getEthernet().nat()
                .findAllNegotiations(timeRange, filters, orderColumn, orderDirection, limit, offset, taps)) {
            negotiations.add(buildNegotiationDetailsResponse(negotiation, null, null, nzyme, organizationId, tenantId));
        }

        return Response.ok(NATSTUNNegotiationsListResponse.create(total, negotiations)).build();
    }

    @GET
    @Path("/traversal/stun/connections/show/{key_sha256}")
    public Response oneSTUNConnection(@Context SecurityContext sc,
                                      @PathParam("key_sha256") String negotiationKeySha256,
                                      @QueryParam("organization_id") UUID organizationId,
                                      @QueryParam("tenant_id") UUID tenantId,
                                      @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<STUNNegotiationEntry> negotiation = nzyme.getEthernet().nat().findOneNegotiation(negotiationKeySha256, taps);

        if (negotiation.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<NATSTUNNegotiationDetailsResponse> flows = Lists.newArrayList();
        for (STUNNegotiationEntry flow : nzyme.getEthernet().nat().findFlowsOfNegotiation(negotiation.get().negotiationKeySha256(), taps)) {
            flows.add(buildNegotiationDetailsResponse(flow, null, null, nzyme, organizationId, tenantId));
        }

        // Find related connections if there are any.
        Map<String, Object> relatedConnections = Maps.newHashMap();

        // WebRTC.
        Optional<WebRTCSessionEntry> webRtcSession = nzyme.getEthernet()
                .webRtc()
                .findOneSession(negotiation.get().negotiationKeySha256(), taps);

        if (webRtcSession.isPresent()) {
            relatedConnections.put(
                    "webrtc",
                    buildWebRTCSessionDetailsResponse(webRtcSession.get(), null, null, nzyme, organizationId, tenantId)
            );
        }

        return Response.ok(buildNegotiationDetailsResponse(negotiation.get(), flows, relatedConnections, nzyme, organizationId, tenantId)).build();
    }

    @GET
    @Path("/traversal/stun/connections/active/histogram")
    public Response stunConnectionsActiveHistogram(@Context SecurityContext sc,
                                                   @QueryParam("time_range") @Valid String timeRangeParameter,
                                                   @QueryParam("filters") String filtersParameter,
                                                   @QueryParam("taps") String taps) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Map<DateTime, Integer> buckets = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().nat()
                .getActiveNegotiationsHistogram(timeRange, bucketing, filters, tapUUIDs)) {
            buckets.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(NumericHistogramResponse.create(buckets, bucketing.bucketSizeMs())).build();
    }

    @GET
    @Path("/traversal/stun/connections/clients/histogram")
    public Response stunConnectionsTopClientsHistogram(@Context SecurityContext sc,
                                                       @QueryParam("organization_id") UUID organizationId,
                                                       @QueryParam("tenant_id") UUID tenantId,
                                                       @QueryParam("time_range") String timeRangeParameter,
                                                       @QueryParam("filters") String filtersParameter,
                                                       @QueryParam("limit") int limit,
                                                       @QueryParam("offset") int offset,
                                                       @QueryParam("order_column") @Nullable String orderColumnParam,
                                                       @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                                       @QueryParam("taps") String taps) {
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

        long count = nzyme.getEthernet().nat().getNegotiationTopClientsCount(timeRange, filters, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (L4AddressDataAddressNumberNumberAggregationResult x : nzyme.getEthernet().nat()
                .getNegotiationTopClients(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.UDP, x.key()),
                            HistogramValueType.L4_ADDRESS_NO_PORT,
                            null),
                    HistogramValueStructureResponse.create(x.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(x.value2(), HistogramValueType.BYTES, null),
                    x.key().address()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, false, values)).build();
    }

    @GET
    @Path("/traversal/stun/connections/servers/histogram")
    public Response stunConnectionsTopServersHistogram(@Context SecurityContext sc,
                                                       @QueryParam("organization_id") UUID organizationId,
                                                       @QueryParam("tenant_id") UUID tenantId,
                                                       @QueryParam("time_range") String timeRangeParameter,
                                                       @QueryParam("filters") String filtersParameter,
                                                       @QueryParam("limit") int limit,
                                                       @QueryParam("offset") int offset,
                                                       @QueryParam("order_column") @Nullable String orderColumnParam,
                                                       @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                                       @QueryParam("taps") String taps) {
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

        long count = nzyme.getEthernet().nat().getNegotiationTopServersCount(timeRange, filters, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (L4AddressDataAddressNumberNumberAggregationResult x : nzyme.getEthernet().nat()
                .getNegotiationTopServers(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
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

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, false, values)).build();
    }

    private NATTraversalDiscoveryDetailsResponse buildDiscoveryDetailsResponse(NATTraversalDiscoveryEntry discovery,
                                                                               UUID organizationId,
                                                                               UUID tenantId) {
        List<L4AddressResponse> mappedAddresses = discovery.mappedAddresses()
                .stream()
                .map(ma -> RestHelpers.L4AddressDataToResponse(
                        nzyme,
                        organizationId,
                        tenantId,
                        L4Type.valueOf(discovery.transport().toUpperCase()), ma))
                .toList();

        L4AddressResponse source = null;
        if (discovery.source() != null) {
            source = RestHelpers.L4AddressDataToResponse(
                    nzyme,
                    organizationId,
                    tenantId,
                    L4Type.valueOf(discovery.transport().toUpperCase()),
                    discovery.source()
            );
        }

        L4AddressResponse destination = null;
        if (discovery.destination() != null) {
            destination = RestHelpers.L4AddressDataToResponse(
                    nzyme,
                    organizationId,
                    tenantId,
                    L4Type.valueOf(discovery.transport().toUpperCase()),
                    discovery.destination()
            );
        }

        return NATTraversalDiscoveryDetailsResponse.create(
                discovery.sessionKey(),
                discovery.transport(),
                discovery.status(),
                mappedAddresses,
                discovery.mostRecentSegmentTime(),
                discovery.firstSeen(),
                discovery.terminatedAt(),
                source,
                destination
        );
    }


}
