package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.topology.Topology;
import app.nzyme.core.ethernet.topology.TopologyBuckets;
import app.nzyme.core.ethernet.topology.TopologyNodeSelector;
import app.nzyme.core.floorplans.db.TenantLocationEntry;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.topology.*;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.TimeRange;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.base.Strings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/topology")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Topology", description = "Who talks to whom on the Ethernet network, aggregated from TCP and UDP "
        + "sessions at request time. Session endpoints are grouped into nodes: internal ones (site-local "
        + "addresses) by address, network or tap, external ones by address, autonomous system or country. Sessions "
        + "to multicast, broadcast and loopback addresses are not part of the topology. All endpoints take the "
        + "filters of the TCP/UDP sessions list, a scope, optional nodes to narrow the selection to and nodes "
        + "to exclude. Traffic that several of the selected taps recorded is counted once: per pair of addresses "
        + "and port, the tap that saw the most is used. Grouped by tap and in geo flows, every tap keeps what it "
        + "recorded.")
public class TopologyResource extends TapDataHandlingResource {

    private static final int DEFAULT_LINKS = 250;
    private static final int MAX_LINKS = 10000;
    private static final int DEFAULT_FLOWS = 500;
    private static final int MAX_FLOWS = 2000;
    private static final int DEFAULT_RANKING_ROWS = 10;
    private static final int MAX_RANKING_ROWS = 500;
    private static final int MAX_NODES = 4;
    private static final int MAX_EXCLUDED_NODES = 25;

    private static final String TIME_RANGE = "Time range selector. Accepts the same format as the web interface "
            + "time range picker.";
    private static final String FILTERS = "JSON encoded filter definition as produced by the web interface filter "
            + "builder. Takes the fields of the TCP/UDP sessions list.";
    private static final String TAPS = "Comma separated list of tap UUIDs to include, or * for all taps the user "
            + "can access. Only taps of the passed tenant are used.";
    private static final String SCOPE = "Which sessions to include by where their endpoints are: ALL (default), "
            + "INTERNAL (both internal), EXTERNAL (at least one external), OUTBOUND (internal to external) or "
            + "INBOUND (external to internal).";
    private static final String NODES = "JSON array of up to " + MAX_NODES + " node IDs to narrow the selection "
            + "to. Only sessions that have every one of the nodes on either end are included, so two nodes select "
            + "the sessions between them. Takes the IDs the topology endpoints return, and "
            + "loc:<latitude>,<longitude> for the external addresses at the coordinates of a geo flow.";

    private static final String EXCLUDED_NODES = "JSON array of up to " + MAX_EXCLUDED_NODES + " node IDs to "
            + "exclude. Sessions that have one of the nodes on either end are left out. Takes the same IDs as the "
            + "nodes parameter.";

    private static final ObjectMapper OM = JsonMapper.builder().build();

    @Inject
    private NzymeNode nzyme;

    private static class InvalidParameterException extends Exception {
        InvalidParameterException(String message) {
            super(message);
        }
    }

    private record Request(Topology.Selection selection, List<Tap> taps) {}

    @GET
    @Path("/summary")
    @Operation(operationId = "findTopologySummary", summary = "Count the endpoints of the topology",
            description = "Returns how many distinct internal addresses, external addresses, external autonomous "
                    + "systems and external countries take part in the selected sessions.")
    @ApiResponse(responseCode = "200", description = "Summary found.",
            content = @Content(schema = @Schema(implementation = TopologySummaryResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid scope or node IDs.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response summary(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = TIME_RANGE) @QueryParam("time_range") String timeRangeParameter,
                            @Parameter(description = FILTERS) @QueryParam("filters") String filtersParameter,
                            @Parameter(description = SCOPE) @QueryParam("scope") @Nullable String scope,
                            @Parameter(description = NODES) @QueryParam("nodes") @Nullable String nodes,
                            @Parameter(description = EXCLUDED_NODES) @QueryParam("exclude_nodes") @Nullable String excludedNodes,
                            @Parameter(description = TAPS) @QueryParam("taps") String tapIds) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Request request;
        try {
            request = request(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, scope, nodes,
                    excludedNodes, tapIds);
        } catch (InvalidParameterException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Topology.Summary summary = nzyme.getEthernet().topology().summary(request.selection());

        return Response.ok(TopologySummaryResponse.create(summary.internalHosts(), summary.externalHosts(),
                summary.externalNetworks(), summary.externalCountries())).build();
    }

    @GET
    @Path("/timeline")
    @Operation(operationId = "findTopologyTimeline", summary = "Get topology sessions and traffic over time",
            description = "Returns the sessions and bytes of the selection per time bucket, split by where the "
                    + "endpoints are: internal (both internal), outbound (internal to external), inbound (external "
                    + "to internal) and external (both external). A session counts in every bucket it was open "
                    + "in, and its bytes are split over them by the time it was open in each. The totals count "
                    + "every session once. The bucket size follows the time range so that at most "
                    + TopologyBuckets.MAX_BUCKETS + " buckets are returned. The series of the links and geo flows "
                    + "use the same buckets.")
    @ApiResponse(responseCode = "200", description = "Timeline found.",
            content = @Content(schema = @Schema(implementation = TopologyTimelineResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid scope or node IDs.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response timeline(@Parameter(hidden = true) @Context SecurityContext sc,
                             @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                             @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                             @Parameter(description = TIME_RANGE) @QueryParam("time_range") String timeRangeParameter,
                             @Parameter(description = FILTERS) @QueryParam("filters") String filtersParameter,
                             @Parameter(description = SCOPE) @QueryParam("scope") @Nullable String scope,
                             @Parameter(description = NODES) @QueryParam("nodes") @Nullable String nodes,
                             @Parameter(description = EXCLUDED_NODES) @QueryParam("exclude_nodes") @Nullable String excludedNodes,
                             @Parameter(description = TAPS) @QueryParam("taps") String tapIds) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Request request;
        try {
            request = request(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, scope, nodes,
                    excludedNodes, tapIds);
        } catch (InvalidParameterException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Topology.Timeline timeline = nzyme.getEthernet().topology().timeline(request.selection());

        List<TopologyTimelineBucketResponse> buckets = new ArrayList<>();
        for (Topology.TimelineBucket b : timeline.buckets()) {
            buckets.add(TopologyTimelineBucketResponse.create(b.bucket(), b.sessionsInternal(), b.sessionsOutbound(),
                    b.sessionsInbound(), b.sessionsExternal(), b.bytesInternal(), b.bytesOutbound(),
                    b.bytesInbound(), b.bytesExternal()));
        }

        Topology.TimelineTotals totals = timeline.totals();

        return Response.ok(TopologyTimelineResponse.create(
                buckets,
                TopologyTimelineTotalsResponse.create(totals.sessionsInternal(), totals.sessionsOutbound(),
                        totals.sessionsInbound(), totals.sessionsExternal(), totals.bytesInternal(),
                        totals.bytesOutbound(), totals.bytesInbound(), totals.bytesExternal()),
                timeline.configuration().sizeMs()
        )).build();
    }

    @GET
    @Path("/links")
    @Operation(operationId = "findTopologyLinks", summary = "List the links between topology nodes",
            description = "Returns the links with the most traffic and the nodes they connect. A link is directed "
                    + "from the node that initiated sessions to the node that accepted them, so two nodes that "
                    + "both initiate sessions to each other have two links. Internal nodes that are addresses "
                    + "carry the asset that used the address in the time range, unless more than one did. With a "
                    + "service level, every link is "
                    + "additionally split by service, which is what a flow diagram from source over service to "
                    + "destination needs. Each link carries its sessions and bytes over time in the buckets of the "
                    + "timeline. The totals cover all links of the selection, also those past the limit.")
    @ApiResponse(responseCode = "200", description = "Links found.",
            content = @Content(schema = @Schema(implementation = TopologyLinksResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid scope, level or node IDs.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response links(@Parameter(hidden = true) @Context SecurityContext sc,
                          @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                          @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                          @Parameter(description = TIME_RANGE) @QueryParam("time_range") String timeRangeParameter,
                          @Parameter(description = FILTERS) @QueryParam("filters") String filtersParameter,
                          @Parameter(description = SCOPE) @QueryParam("scope") @Nullable String scope,
                          @Parameter(description = NODES) @QueryParam("nodes") @Nullable String nodes,
                          @Parameter(description = EXCLUDED_NODES) @QueryParam("exclude_nodes") @Nullable String excludedNodes,
                          @Parameter(description = "How internal endpoints are grouped into nodes: HOST (default), SUBNET (/24 for IPv4, /64 for IPv6) or TAP.") @QueryParam("internal_level") @Nullable String internalLevelParam,
                          @Parameter(description = "How external endpoints are grouped into nodes: HOST, NETWORK (autonomous system, default) or COUNTRY.") @QueryParam("external_level") @Nullable String externalLevelParam,
                          @Parameter(description = "Splits links by service: PROTOCOL (the protocol tags of a session, destination port for untagged sessions) or PORT (transport and destination port). Omit for links that are not split.") @QueryParam("service_level") @Nullable String serviceLevelParam,
                          @Parameter(description = "Maximum number of links. Defaults to " + DEFAULT_LINKS + ", maximum is " + MAX_LINKS + ".") @QueryParam("limit") @Nullable Integer limitParam,
                          @Parameter(description = TAPS) @QueryParam("taps") String tapIds) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Request request;
        Topology.InternalLevel internalLevel;
        Topology.ExternalLevel externalLevel;
        Topology.ServiceLevel serviceLevel;
        try {
            request = request(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, scope, nodes,
                    excludedNodes, tapIds);
            internalLevel = enumParameter(Topology.InternalLevel.class, internalLevelParam, Topology.InternalLevel.HOST);
            externalLevel = enumParameter(Topology.ExternalLevel.class, externalLevelParam, Topology.ExternalLevel.NETWORK);
            serviceLevel = enumParameter(Topology.ServiceLevel.class, serviceLevelParam, null);
        } catch (InvalidParameterException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        int limit = limitParam == null ? DEFAULT_LINKS : Math.max(1, Math.min(limitParam, MAX_LINKS));

        Topology.Links result = nzyme.getEthernet().topology()
                .links(request.selection(), internalLevel, externalLevel, serviceLevel, limit);

        List<TopologyServiceResponse> services = result.services().stream()
                .map(TopologyResource::serviceResponse)
                .collect(Collectors.toList());

        List<TopologyLinkResponse> links = new ArrayList<>();
        for (Topology.Link link : result.links()) {
            links.add(TopologyLinkResponse.create(link.source(), link.target(), link.service(), link.sessions(),
                    link.sessionsTcp(), link.sessions() - link.sessionsTcp(), link.bytesTx(), link.bytesRx(),
                    link.firstSeen(), link.lastSeen(), series(link.series())));
        }

        return Response.ok(TopologyLinksResponse.create(
                nodeResponses(result.nodes(), request.taps(), request.selection().timeRange(), organizationId, tenantId),
                services, links, result.totalLinks(), result.totalSessions(), result.totalBytes(), limit,
                seriesConfiguration(result.buckets())
        )).build();
    }

    @GET
    @Path("/geo")
    @Operation(operationId = "findTopologyGeoFlows", summary = "List traffic flows between sites and the world",
            description = "Returns the flows with the most traffic between the sites of the selected taps and the "
                    + "places external addresses are located at. A site is a location with coordinates and all "
                    + "selected taps assigned to it, or a tap with its own coordinates that has no such location. "
                    + "A place is a pair of GeoIP coordinates rounded to two decimals, or a country for addresses "
                    + "without coordinates. Only sessions with exactly one internal endpoint have a flow. Flows "
                    + "are reported per tap and direction, so two taps of one site can both report a flow to the "
                    + "same place. Each flow carries its sessions and bytes over time in the buckets of the "
                    + "timeline.")
    @ApiResponse(responseCode = "200", description = "Flows found.",
            content = @Content(schema = @Schema(implementation = TopologyGeoResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid scope or node IDs.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response geo(@Parameter(hidden = true) @Context SecurityContext sc,
                        @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                        @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                        @Parameter(description = TIME_RANGE) @QueryParam("time_range") String timeRangeParameter,
                        @Parameter(description = FILTERS) @QueryParam("filters") String filtersParameter,
                        @Parameter(description = SCOPE) @QueryParam("scope") @Nullable String scope,
                        @Parameter(description = NODES) @QueryParam("nodes") @Nullable String nodes,
                        @Parameter(description = EXCLUDED_NODES) @QueryParam("exclude_nodes") @Nullable String excludedNodes,
                        @Parameter(description = "Maximum number of flows. Defaults to " + DEFAULT_FLOWS + ", maximum is " + MAX_FLOWS + ".") @QueryParam("limit") @Nullable Integer limitParam,
                        @Parameter(description = TAPS) @QueryParam("taps") String tapIds) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Request request;
        try {
            request = request(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, scope, nodes,
                    excludedNodes, tapIds);
        } catch (InvalidParameterException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        int limit = limitParam == null ? DEFAULT_FLOWS : Math.max(1, Math.min(limitParam, MAX_FLOWS));

        Map<String, TopologySiteResponse.Builder> sites = new LinkedHashMap<>();
        Map<String, List<TopologyTapResponse>> siteTaps = new HashMap<>();
        Map<UUID, String> siteOfTap = new HashMap<>();
        List<TopologyTapResponse> unplaced = new ArrayList<>();
        Map<UUID, Optional<TenantLocationEntry>> locations = new HashMap<>();

        for (Tap tap : request.taps()) {
            TopologyTapResponse tapResponse = TopologyTapResponse.create(tap.uuid(), tap.name());

            Optional<TenantLocationEntry> location = tap.locationId() == null
                    ? Optional.empty()
                    : locations.computeIfAbsent(tap.locationId(), id -> nzyme.getAuthenticationService()
                            .findTenantLocation(id, organizationId, tenantId));

            String siteId;
            if (location.isPresent() && location.get().latitude() != null && location.get().longitude() != null) {
                siteId = "location:" + location.get().uuid();
                sites.computeIfAbsent(siteId, id -> TopologySiteResponse.builder()
                        .id(id)
                        .name(location.get().name())
                        .latitude(location.get().latitude())
                        .longitude(location.get().longitude())
                        .locationUuid(location.get().uuid()));
            } else if (tap.latitude() != null && tap.longitude() != null) {
                siteId = "tap:" + tap.uuid();
                sites.put(siteId, TopologySiteResponse.builder()
                        .id(siteId)
                        .name(tap.name())
                        .latitude(tap.latitude())
                        .longitude(tap.longitude())
                        .locationUuid(location.map(TenantLocationEntry::uuid).orElse(null)));
            } else {
                unplaced.add(tapResponse);
                continue;
            }

            siteOfTap.put(tap.uuid(), siteId);
            siteTaps.computeIfAbsent(siteId, id -> new ArrayList<>()).add(tapResponse);
        }

        List<TopologySiteResponse> siteResponses = new ArrayList<>();
        for (Map.Entry<String, TopologySiteResponse.Builder> site : sites.entrySet()) {
            siteResponses.add(site.getValue().taps(siteTaps.get(site.getKey())).build());
        }

        Topology.GeoFlows result = nzyme.getEthernet().topology().geo(request.selection(), limit);

        List<TopologyGeoFlowResponse> flows = new ArrayList<>();
        for (Topology.GeoFlow flow : result.flows()) {
            flows.add(TopologyGeoFlowResponse.create(siteOfTap.get(flow.tapUuid()), flow.tapUuid(),
                    flow.outbound() ? "OUTBOUND" : "INBOUND", flow.latitude(), flow.longitude(), flow.city(),
                    flow.countryCode(), flow.sessions(), flow.bytesTx(), flow.bytesRx(), flow.firstSeen(),
                    flow.lastSeen(), series(flow.series())));
        }

        return Response.ok(TopologyGeoResponse.create(siteResponses, unplaced, flows, result.totalFlows(),
                result.totalSessions(), result.totalBytes(), limit, seriesConfiguration(result.buckets()))).build();
    }

    @GET
    @Path("/ranking")
    @Operation(operationId = "findTopologyRanking", summary = "Rank topology nodes or services",
            description = "Returns the nodes of one kind, or the services, of the selection with their sessions, "
                    + "bytes and first and last activity. A node ranks with the sessions it initiated and the "
                    + "ones it accepted, and carries the number of distinct addresses on the other end. A service "
                    + "carries its distinct client and server addresses. Ordering node rankings by PEERS ascending "
                    + "finds the external networks the fewest internal addresses talk to, ordering by FIRST_SEEN "
                    + "descending the ones that appeared last. Narrowed to one or two nodes, the service rankings "
                    + "break down the traffic of a node or of a link. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Ranking found.",
            content = @Content(schema = @Schema(implementation = TopologyRankingResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid scope, dimension, sorting or node IDs.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response ranking(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = TIME_RANGE) @QueryParam("time_range") String timeRangeParameter,
                            @Parameter(description = FILTERS) @QueryParam("filters") String filtersParameter,
                            @Parameter(description = SCOPE) @QueryParam("scope") @Nullable String scope,
                            @Parameter(description = NODES) @QueryParam("nodes") @Nullable String nodes,
                            @Parameter(description = EXCLUDED_NODES) @QueryParam("exclude_nodes") @Nullable String excludedNodes,
                            @Parameter(description = "What to rank: INTERNAL_HOST, INTERNAL_SUBNET, INTERNAL_TAP, EXTERNAL_HOST, EXTERNAL_NETWORK, EXTERNAL_COUNTRY, SERVICE_PROTOCOL or SERVICE_PORT.") @QueryParam("dimension") String dimensionParam,
                            @Parameter(description = "Sorting column: BYTES (default), SESSIONS, PEERS (distinct clients for services), FIRST_SEEN or LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC. Defaults to DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size. Defaults to " + DEFAULT_RANKING_ROWS + ", maximum is " + MAX_RANKING_ROWS + ".") @QueryParam("limit") @Nullable Integer limitParam,
                            @Parameter(description = "Page offset.") @QueryParam("offset") @Nullable Integer offsetParam,
                            @Parameter(description = TAPS) @QueryParam("taps") String tapIds) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Request request;
        Topology.RankingDimension dimension;
        Topology.RankingOrder order;
        OrderDirection direction;
        try {
            request = request(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, scope, nodes,
                    excludedNodes, tapIds);
            dimension = enumParameter(Topology.RankingDimension.class, dimensionParam, null);
            order = enumParameter(Topology.RankingOrder.class, orderColumnParam, Topology.RankingOrder.BYTES);
            direction = enumParameter(OrderDirection.class, orderDirectionParam, OrderDirection.DESC);
        } catch (InvalidParameterException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (dimension == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        int limit = limitParam == null ? DEFAULT_RANKING_ROWS : Math.max(1, Math.min(limitParam, MAX_RANKING_ROWS));
        int offset = offsetParam == null ? 0 : Math.max(0, offsetParam);

        Topology.Ranking ranking = nzyme.getEthernet().topology()
                .ranking(request.selection(), dimension, order, direction, limit, offset);

        List<Topology.Node> rankedNodes = ranking.rows().stream()
                .map(Topology.RankingRow::node)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<String, TopologyNodeResponse> nodeResponses = new HashMap<>();
        for (TopologyNodeResponse response : nodeResponses(rankedNodes, request.taps(),
                request.selection().timeRange(), organizationId, tenantId)) {
            nodeResponses.put(response.id(), response);
        }

        List<TopologyRankingRowResponse> rows = new ArrayList<>();
        for (Topology.RankingRow row : ranking.rows()) {
            rows.add(TopologyRankingRowResponse.create(
                    row.node() == null ? null : nodeResponses.get(row.node().id()),
                    row.service() == null ? null : serviceResponse(row.service()),
                    row.sessions(), row.sessionsInitiated(), row.bytesSent(), row.bytesReceived(), row.peers(),
                    row.clients(), row.servers(), row.firstSeen(), row.lastSeen()
            ));
        }

        return Response.ok(TopologyRankingResponse.create(rows, ranking.total())).build();
    }

    // Taps are cut down to the passed tenant: address spaces of tenants overlap.
    private Request request(SecurityContext sc, UUID organizationId, UUID tenantId, String timeRangeParameter,
                            String filtersParameter, @Nullable String scopeParam, @Nullable String nodesParam,
                            @Nullable String excludedNodesParam, String tapIds) throws InvalidParameterException {
        List<Tap> taps = nzyme.getTapManager()
                .findAllTapsByUUIDs(parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds))
                .stream()
                .filter(tap -> organizationId.equals(tap.organizationId()) && tenantId.equals(tap.tenantId()))
                .collect(Collectors.toList());

        Topology.Scope scope = enumParameter(Topology.Scope.class, scopeParam, Topology.Scope.ALL);

        return new Request(new Topology.Selection(
                parseTimeRangeQueryParameter(timeRangeParameter),
                parseFiltersQueryParameter(filtersParameter),
                taps.stream().map(Tap::uuid).collect(Collectors.toList()),
                scope, nodeSelectors(nodesParam, MAX_NODES), nodeSelectors(excludedNodesParam, MAX_EXCLUDED_NODES)
        ), taps);
    }

    private static List<TopologyNodeSelector> nodeSelectors(@Nullable String parameter, int maximum)
            throws InvalidParameterException {
        List<TopologyNodeSelector> nodes = new ArrayList<>();
        if (Strings.isNullOrEmpty(parameter)) {
            return nodes;
        }

        try {
            List<String> ids = OM.readValue(parameter, new TypeReference<>() {});
            if (ids == null || ids.size() > maximum) {
                throw new InvalidParameterException("Invalid number of node IDs.");
            }
            for (String id : ids) {
                nodes.add(TopologyNodeSelector.parse(id));
            }
        } catch (JacksonException | IllegalArgumentException e) {
            throw new InvalidParameterException("Invalid node ID.");
        }

        return nodes;
    }

    private static <E extends Enum<E>> E enumParameter(Class<E> type, @Nullable String value, @Nullable E fallback)
            throws InvalidParameterException {
        if (Strings.isNullOrEmpty(value)) {
            return fallback;
        }

        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterException("Invalid value [" + value + "].");
        }
    }

    private List<TopologyNodeResponse> nodeResponses(List<Topology.Node> nodes, List<Tap> taps, TimeRange timeRange,
                                                     UUID organizationId, UUID tenantId) {
        Set<String> internalAddresses = nodes.stream()
                .filter(node -> node.internal() && node.address() != null)
                .map(Topology.Node::address)
                .collect(Collectors.toSet());
        Map<String, Topology.AddressAsset> assets = nzyme.getEthernet().topology()
                .findAssetsOfAddresses(organizationId, tenantId, timeRange, internalAddresses);

        Map<UUID, String> tapNames = new HashMap<>();
        for (Tap tap : taps) {
            tapNames.put(tap.uuid(), tap.name());
        }

        List<TopologyNodeResponse> responses = new ArrayList<>();
        for (Topology.Node node : nodes) {
            Topology.AddressAsset asset = node.internal() && node.address() != null
                    ? assets.get(node.address()) : null;

            responses.add(TopologyNodeResponse.create(
                    node.id(),
                    node.internal() ? "INTERNAL" : "EXTERNAL",
                    node.level().name(),
                    node.address(),
                    node.network(),
                    node.tapUuid(),
                    node.tapUuid() == null ? null : tapNames.get(node.tapUuid()),
                    asset == null ? null : TopologyNodeAssetResponse.create(asset.uuid(), asset.mac(),
                            nzyme.getContextService().findMacAddressContext(asset.mac(), organizationId, tenantId)
                                    .map(MacAddressContextEntry::name).orElse(null)),
                    asset == null ? null : asset.hostname(),
                    node.asnNumber(),
                    node.asnName(),
                    node.countryCode()
            ));
        }

        return responses;
    }

    private static TopologyServiceResponse serviceResponse(Topology.Service service) {
        return TopologyServiceResponse.create(service.id(), service.l4Type(), service.port(), service.protocol());
    }

    private static List<long[]> series(List<Topology.SeriesPoint> points) {
        List<long[]> series = new ArrayList<>(points.size());
        for (Topology.SeriesPoint point : points) {
            series.add(new long[]{point.bucket(), point.sessions(), point.bytesTx(), point.bytesRx(),
                    point.sessionsNew()});
        }
        return series;
    }

    private static TopologySeriesConfigurationResponse seriesConfiguration(TopologyBuckets buckets) {
        return TopologySeriesConfigurationResponse.create(buckets.from(), buckets.sizeMs(), buckets.count());
    }

}
