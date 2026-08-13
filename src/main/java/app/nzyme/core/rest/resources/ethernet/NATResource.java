package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.StringStringNumberAggregationResult;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import app.nzyme.core.ethernet.nat.NAT;
import app.nzyme.core.ethernet.nat.NATTraversalDiscoveryStatus;
import app.nzyme.core.ethernet.nat.db.NATTraversalDiscoveryEntry;
import app.nzyme.core.ethernet.nat.db.NATTraversalDiscoveryHistogramBucket;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.*;
import app.nzyme.core.rest.responses.ethernet.nat.NATTraversalDiscoveryDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.nat.NATTraversalDiscoveryListResponse;
import app.nzyme.core.rest.responses.shared.HistogramValueStructureResponse;
import app.nzyme.core.rest.responses.shared.HistogramValueType;
import app.nzyme.core.rest.responses.shared.ThreeColumnTableHistogramResponse;
import app.nzyme.core.rest.responses.shared.ThreeColumnTableHistogramValueResponse;
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

@Path("/api/ethernet/nat")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class NATResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/traversal/discoveries/show/{id}")
    public Response oneTraversalDiscovery(@Context SecurityContext sc,
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

        return Response.ok(buildDetailsResponse(discovery.get(), organizationId, tenantId)).build();
    }

    @GET
    @Path("/traversal/discoveries")
    public Response allTraversalDiscoveries(@Context SecurityContext sc,
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

            discoveries.add(buildDetailsResponse(discovery, organizationId, tenantId));
        }

        return Response.ok(NATTraversalDiscoveryListResponse.create(total, discoveries)).build();
    }

    @GET
    @Path("/traversal/discoveries/histogram")
    public Response traversalHistogram(@Context SecurityContext sc,
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
    @Path("/traversal/clients/histogram")
    public Response traversalTopClientsHistogram(@Context SecurityContext sc,
                                                 @QueryParam("organization_id") UUID organizationId,
                                                 @QueryParam("tenant_id") UUID tenantId,
                                                 @QueryParam("time_range") String timeRangeParameter,
                                                 @QueryParam("filters") String filtersParameter,
                                                 @QueryParam("limit") int limit,
                                                 @QueryParam("offset") int offset,
                                                 @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long total = nzyme.getEthernet().nat()
                .countTraversalDiscoveryTopClientsHistogram(timeRange, filters, taps);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (StringStringNumberAggregationResult s : nzyme.getEthernet().nat()
                .getTraversalDiscoveryTopClientsHistogram(timeRange, filters, limit, offset, taps)) {

            Optional<MacAddressContextEntry> clientContext = nzyme.getContextService().findMacAddressContext(
                    s.value1(),
                    organizationId,
                    tenantId
            );

            Optional<AssetEntry> clientAsset = nzyme.getAssetsManager()
                    .findAssetByMac(s.value1(), organizationId, tenantId);

            // Pull the most recent address data of this asset.
            Optional<L4AddressData> clientAddressData = nzyme.getEthernet().l4()
                    .findMostRecentSourceAddressData(taps, s.key());

            EthernetMacAddressResponse client;
            L4AddressResponse l4AddressResponse;
            if (clientAddressData.isPresent() && s.value1() != null) {
                if (clientAddressData.get().attributes() != null && clientAddressData.get().attributes().isSiteLocal()) {
                    client = EthernetMacAddressResponse.create(
                            s.value1(),
                            nzyme.getOuiService().lookup(s.value1()).orElse(null),
                            clientAsset.map(AssetEntry::uuid).orElse(null),
                            clientAsset.map(AssetEntry::isActive).orElse(null),
                            clientContext.map(ctx ->
                                    EthernetMacAddressContextResponse.create(
                                            ctx.name(),
                                            ctx.description()
                                    )
                            ).orElse(null)
                    );
                } else {
                    client = null;
                }
                l4AddressResponse = RestHelpers.L4AddressDataToResponse(
                        nzyme, organizationId, tenantId, L4Type.NONE, clientAddressData.get()
                );
            } else {
                client = null;
                l4AddressResponse = L4AddressResponse.create(
                        L4AddressTypeResponse.UDP,
                        null,
                        s.key(),
                        null,
                        null,
                        null,
                        L4AddressContextResponse.create()
                );
            }

            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            l4AddressResponse,
                            HistogramValueType.L4_ADDRESS,
                            null),
                    HistogramValueStructureResponse.create(s.value1(),
                            HistogramValueType.ETHERNET_MAC_NO_INTERNAL,
                            client
                    ),
                    HistogramValueStructureResponse.create(s.value2(), HistogramValueType.INTEGER, null),
                    s.key()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(total, true, values)).build();
    }


    @GET
    @Path("/traversal/servers/histogram")
    public Response traversalTopServersHistogram(@Context SecurityContext sc,
                                                 @QueryParam("organization_id") UUID organizationId,
                                                 @QueryParam("tenant_id") UUID tenantId,
                                                 @QueryParam("time_range") String timeRangeParameter,
                                                 @QueryParam("filters") String filtersParameter,
                                                 @QueryParam("limit") int limit,
                                                 @QueryParam("offset") int offset,
                                                 @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long total = nzyme.getEthernet().nat()
                .countTraversalDiscoveryTopServersHistogram(timeRange, filters, taps);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (StringStringNumberAggregationResult s : nzyme.getEthernet().nat()
                .getTraversalDiscoveryTopServersHistogram(timeRange, filters, limit, offset, taps)) {

            Optional<MacAddressContextEntry> serverContext = nzyme.getContextService().findMacAddressContext(
                    s.value1(),
                    organizationId,
                    tenantId
            );

            Optional<AssetEntry> serverAsset = nzyme.getAssetsManager()
                    .findAssetByMac(s.value1(), organizationId, tenantId);

            // Pull the most recent address data of this asset.
            Optional<L4AddressData> serverAddressData = nzyme.getEthernet().l4()
                    .findMostRecentDestinationAddressData(taps, s.key());

            EthernetMacAddressResponse server;
            L4AddressResponse l4AddressResponse;
            if (serverAddressData.isPresent() && s.value1() != null) {
                if (serverAddressData.get().attributes() != null && serverAddressData.get().attributes().isSiteLocal()) {
                    server = EthernetMacAddressResponse.create(
                            s.value1(),
                            nzyme.getOuiService().lookup(s.value1()).orElse(null),
                            serverAsset.map(AssetEntry::uuid).orElse(null),
                            serverAsset.map(AssetEntry::isActive).orElse(null),
                            serverContext.map(ctx ->
                                    EthernetMacAddressContextResponse.create(
                                            ctx.name(),
                                            ctx.description()
                                    )
                            ).orElse(null)
                    );
                } else {
                    server = null;
                }
                l4AddressResponse = RestHelpers.L4AddressDataToResponse(
                        nzyme, organizationId, tenantId, L4Type.NONE, serverAddressData.get()
                );
            } else {
                server = null;
                l4AddressResponse = L4AddressResponse.create(
                        L4AddressTypeResponse.UDP,
                        null,
                        s.key(),
                        null,
                        null,
                        null,
                        L4AddressContextResponse.create()
                );
            }

            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            l4AddressResponse,
                            HistogramValueType.L4_ADDRESS,
                            null),
                    HistogramValueStructureResponse.create(s.value1(),
                            HistogramValueType.ETHERNET_MAC_NO_INTERNAL,
                            server
                    ),
                    HistogramValueStructureResponse.create(s.value2(), HistogramValueType.INTEGER, null),
                    s.key()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(total, true, values)).build();
    }

    private NATTraversalDiscoveryDetailsResponse buildDetailsResponse(NATTraversalDiscoveryEntry discovery,
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
