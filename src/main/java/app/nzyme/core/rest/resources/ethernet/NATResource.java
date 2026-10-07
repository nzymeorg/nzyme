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
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "NAT", description = "NAT traversal activity that taps recorded on the Ethernet network. Nzyme "
        + "reports it in two views: STUN discoveries, where a device asks a public STUN server what its own public "
        + "address looks like, and STUN connections, where two peers negotiate a direct or TURN relayed path to "
        + "each other.")
public class NATResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/traversal/stun/discoveries/show/{id}")
    @Operation(operationId = "findNatStunDiscovery", summary = "Get a STUN discovery",
            description = "A STUN discovery is one exchange in which a client asked a STUN server for its public "
                    + "address. The response holds the client, the server, the status of the exchange and all mapped "
                    + "addresses the server returned.")
    @ApiResponse(responseCode = "200", description = "Discovery found.",
            content = @Content(schema = @Schema(implementation = NATTraversalDiscoveryDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Discovery not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response oneSTUNDiscovery(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Session key of the discovery.") @PathParam("id") String id,
                                     @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                     @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
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
    @Operation(operationId = "findNatStunDiscoveries", summary = "List STUN discoveries",
            description = "Returns all STUN discoveries in the time range, newest first by default. A discovery is "
                    + "COMPLETE when the client received a successful response, ERROR when the server answered with "
                    + "an error and INCOMPLETE when no matching response was observed. Results are paginated.",
            externalDocs = @ExternalDocumentation(description = "STUN discoveries in the Nzyme documentation",
                    url = "https://go.nzyme.org/stun-discoveries"))
    @ApiResponse(responseCode = "200", description = "Discoveries found.",
            content = @Content(schema = @Schema(implementation = NATTraversalDiscoveryListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response allSTUNDiscoveries(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                       @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                       @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                       @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                       @Parameter(description = "Sorting column. Defaults to the time the discovery was initiated.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                       @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                       @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                       @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                       @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
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
    @Operation(operationId = "findNatStunDiscoveryHistogram", summary = "Get the STUN discovery histogram",
            description = "Returns the number of complete, incomplete and error STUN discoveries per time bucket. "
                    + "The response is an object that maps each bucket timestamp to those three counts. The bucket "
                    + "size is chosen automatically from the time range.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    public Response stunDiscoveriesHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                             @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                             @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                             @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
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
    @Operation(operationId = "findNatStunDiscoveryTopClients", summary = "List top STUN discovery clients",
            description = "Returns the clients that ran the most STUN discoveries, with the number of complete and "
                    + "the number of incomplete discoveries per client. Most complete discoveries first by default. "
                    + "Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response stunDiscoveriesTopClientsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                                       @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                                       @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                                       @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") String timeRangeParameter,
                                                       @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                                       @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                                       @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                                       @Parameter(description = "Sorting column. Defaults to the number of complete discoveries.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                                       @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                                       @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
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
    @Operation(operationId = "findNatStunDiscoveryTopServers", summary = "List top STUN discovery servers",
            description = "Returns the STUN servers that clients queried the most, with the number of complete and "
                    + "the number of incomplete discoveries per server. Most complete discoveries first by default. "
                    + "Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response stunDiscoveriesTopServersHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                                       @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                                       @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                                       @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") String timeRangeParameter,
                                                       @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                                       @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                                       @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                                       @Parameter(description = "Sorting column. Defaults to the number of complete discoveries.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                                       @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                                       @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
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
    @Operation(operationId = "findNatStunConnections", summary = "List STUN connections",
            description = "Returns all STUN negotiated connections in the time range, newest first by default. A "
                    + "connection groups all flows that share one negotiation key and is marked successful only if "
                    + "Nzyme saw evidence of a working path. Each entry is the summary of one negotiation; the "
                    + "individual flows and related connections are only returned when you fetch a single "
                    + "connection. Results are paginated.",
            externalDocs = @ExternalDocumentation(description = "STUN connections in the Nzyme documentation",
                    url = "https://go.nzyme.org/stun-connections"))
    @ApiResponse(responseCode = "200", description = "Connections found.",
            content = @Content(schema = @Schema(implementation = NATSTUNNegotiationsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response allSTUNConnections(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                       @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                       @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                       @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                       @Parameter(description = "Sorting column. Defaults to the time the negotiation was initiated.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                       @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                       @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                       @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                       @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
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
    @Operation(operationId = "findNatStunConnection", summary = "Get a STUN connection",
            description = "Returns one STUN negotiated connection with all of its flows, including the mapped, peer "
                    + "and relayed addresses observed in the STUN and TURN messages. If Nzyme matched the "
                    + "negotiation to a WebRTC session, that session is included in the related connections.")
    @ApiResponse(responseCode = "200", description = "Connection found.",
            content = @Content(schema = @Schema(implementation = NATSTUNNegotiationDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Connection not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response oneSTUNConnection(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "SHA256 of the negotiation key.") @PathParam("key_sha256") String negotiationKeySha256,
                                      @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                      @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                      @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
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
    @Operation(operationId = "findNatStunActiveConnectionsHistogram",
            summary = "Get the active STUN connections histogram",
            description = "Returns the number of STUN connections that were active in each time bucket. The "
                    + "bucket size is chosen automatically from the time range and is reported with the response.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = NumericHistogramResponse.class)))
    public Response stunConnectionsActiveHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                                   @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                                   @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                                   @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
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
    @Operation(operationId = "findNatStunConnectionTopClients", summary = "List top STUN connection clients",
            description = "Returns the peers on the source side of the most STUN connections, with the connection "
                    + "count and the total bytes. ICE is peer to peer, so the source side is the peer that opened "
                    + "the underlying flow, not a client in the traditional sense. Most connections first by "
                    + "default. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response stunConnectionsTopClientsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                                       @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                                       @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                                       @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") String timeRangeParameter,
                                                       @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                                       @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                                       @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                                       @Parameter(description = "Sorting column. Defaults to the connection count.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                                       @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                                       @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
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
    @Operation(operationId = "findNatStunConnectionTopServers", summary = "List top STUN connection servers",
            description = "Returns the peers on the destination side of the most STUN connections, including the "
                    + "connection count and the total bytes. ICE is peer to peer, so the destination side is the "
                    + "peer the underlying flow was opened to, not a server in the traditional sense. Most "
                    + "connections first by default. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response stunConnectionsTopServersHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                                       @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                                       @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                                       @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") String timeRangeParameter,
                                                       @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                                       @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                                       @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                                       @Parameter(description = "Sorting column. Defaults to the connection count.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                                       @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                                       @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
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
