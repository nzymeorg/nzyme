package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.ndp.NDP;
import app.nzyme.core.ethernet.ndp.NdpPrefix;
import app.nzyme.core.ethernet.ndp.NdpRoute;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.ndp.NdpMessageResponse;
import app.nzyme.core.rest.responses.ethernet.ndp.NdpMessagesListResponse;
import app.nzyme.core.rest.responses.ethernet.ndp.NdpRouterAdvertisementResponse;
import app.nzyme.core.rest.responses.ethernet.ndp.NdpRouterAdvertisementsListResponse;
import app.nzyme.core.rest.responses.ethernet.ndp.NdpStatisticsBucketResponse;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;


@Path("/api/ethernet/ndp")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "NDP", description = "IPv6 neighbor discovery that taps recorded on the Ethernet network: neighbor and "
        + "router solicitations, neighbor advertisements and router advertisements. It is what ARP is for IPv4. "
        + "Identical messages are counted together per report of a tap.")
public class NdpResource extends TapDataHandlingResource {

    private static final ObjectMapper OM = new ObjectMapper();

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/messages")
    @Operation(operationId = "findNdpMessages", summary = "List NDP solicitations and neighbor advertisements",
            description = "Returns neighbor solicitations, neighbor advertisements and router solicitations in the "
                    + "time range, most recently seen first by default. Identical messages that a tap recorded "
                    + "within one report are one entry with a packet count. The source and target addresses are "
                    + "enriched with asset, OUI and context information. A hop limit other than 255 means the "
                    + "message was not sent from the local link. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Messages found.",
            content = @Content(schema = @Schema(implementation = NdpMessagesListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response messages(@Parameter(hidden = true) @Context SecurityContext sc,
                             @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                             @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                             @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                             @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                             @Parameter(description = "Sorting column. Defaults to the time a message was last seen.") @QueryParam("order_column") @Nullable String orderColumnParam,
                             @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                             @Parameter(description = "Page size. 25 if not set, 250 at most.") @QueryParam("limit") int limit,
                             @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                             @Parameter(description = "Comma separated list of tap UUIDs to include. Use * for all taps the user can access. Without the parameter, no taps are selected and the result is empty.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        NDP.MessageOrderColumn orderColumn = NDP.MessageOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = NDP.MessageOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        NDP ndp = nzyme.getEthernet().ndp();
        long total = ndp.countAllMessages(timeRange, filters, taps);

        List<NdpMessageResponse> messages = Lists.newArrayList();
        for (NDP.Message m : ndp.findAllMessages(timeRange, filters, RestHelpers.pageSize(limit), Math.max(offset, 0), orderColumn, orderDirection, taps)) {
            // An advertisement names the MAC address of its target itself.
            String targetMac = m.targetLinkLayerAddress() != null ? m.targetLinkLayerAddress()
                    : NDP.NEIGHBOR_ADVERTISEMENT.equals(m.messageType()) ? m.sourceMac() : null;

            messages.add(NdpMessageResponse.create(
                    m.tapUuid(),
                    m.messageType(),
                    RestHelpers.internalAddressDataToResponse(nzyme, m.sourceMac(), m.sourceAddress(), organizationId, tenantId),
                    m.destinationAddress(),
                    m.destinationMac(),
                    m.hopLimit(),
                    RestHelpers.internalAddressDataToResponse(nzyme, targetMac, m.targetAddress(), organizationId, tenantId),
                    m.sourceLinkLayerAddress(),
                    m.targetLinkLayerAddress(),
                    m.router(),
                    m.solicited(),
                    m.overrides(),
                    m.packetCount(),
                    m.firstSeen(),
                    m.lastSeen()
            ));
        }

        return Response.ok(NdpMessagesListResponse.create(total, messages)).build();
    }

    @GET
    @Path("/messages/statistics")
    @Operation(operationId = "findNdpStatistics", summary = "Get NDP message statistics",
            description = "Returns packet counts per time bucket for neighbor solicitations, neighbor advertisements "
                    + "and router solicitations, limited to the messages matching the filters. The response is an "
                    + "object that maps each bucket timestamp to its values. The bucket size is chosen automatically "
                    + "from the time range.")
    @ApiResponse(responseCode = "200", description = "Statistics found.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    public Response statistics(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                               @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                               @Parameter(description = "Comma separated list of tap UUIDs to include. Use * for all taps the user can access. Without the parameter, no taps are selected and the result is empty.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        Filters filters = parseFilters(filtersParameter, sc);

        Map<DateTime, NdpStatisticsBucketResponse> statistics = Maps.newHashMap();
        for (NDP.StatisticsBucket s : nzyme.getEthernet().ndp().getStatistics(timeRange, bucketing, filters, taps)) {
            statistics.put(s.bucket(), NdpStatisticsBucketResponse.create(
                    s.neighborSolicitations(), s.neighborAdvertisements(), s.routerSolicitations()
            ));
        }

        return Response.ok(statistics).build();
    }

    @GET
    @Path("/router-advertisements")
    @Operation(operationId = "findNdpRouterAdvertisements", summary = "List NDP router advertisements",
            description = "Returns router advertisements in the time range, most recently seen first by default, "
                    + "with the prefixes, routes and DNS servers they announce. Identical advertisements that a tap "
                    + "recorded within one report are one entry with a packet count. Each list of an advertisement "
                    + "holds at most 32 entries. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Router advertisements found.",
            content = @Content(schema = @Schema(implementation = NdpRouterAdvertisementsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response routerAdvertisements(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                         @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                         @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                         @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                         @Parameter(description = "Sorting column. Defaults to the time an advertisement was last seen.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                         @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                         @Parameter(description = "Page size. 25 if not set, 250 at most.") @QueryParam("limit") int limit,
                                         @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                         @Parameter(description = "Comma separated list of tap UUIDs to include. Use * for all taps the user can access. Without the parameter, no taps are selected and the result is empty.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        NDP.RouterAdvertisementOrderColumn orderColumn = NDP.RouterAdvertisementOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = NDP.RouterAdvertisementOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        NDP ndp = nzyme.getEthernet().ndp();
        long total = ndp.countAllRouterAdvertisements(timeRange, filters, taps);

        List<NdpRouterAdvertisementResponse> advertisements = Lists.newArrayList();
        for (NDP.RouterAdvertisement ra : ndp
                .findAllRouterAdvertisements(timeRange, filters, RestHelpers.pageSize(limit), Math.max(offset, 0), orderColumn, orderDirection, taps)) {
            String routerMac = ra.sourceLinkLayerAddress() != null ? ra.sourceLinkLayerAddress() : ra.sourceMac();

            advertisements.add(NdpRouterAdvertisementResponse.create(
                    ra.tapUuid(),
                    RestHelpers.internalAddressDataToResponse(nzyme, routerMac, ra.sourceAddress(), organizationId, tenantId),
                    ra.destinationAddress(),
                    ra.hopLimit(),
                    ra.sourceLinkLayerAddress(),
                    ra.currentHopLimit(),
                    ra.managed(),
                    ra.otherConfiguration(),
                    ra.preference(),
                    ra.routerLifetime(),
                    ra.reachableTime(),
                    ra.retransmitTimer(),
                    ra.mtu(),
                    list(ra.prefixes(), new TypeReference<List<NdpPrefix>>() { }),
                    list(ra.routes(), new TypeReference<List<NdpRoute>>() { }),
                    list(ra.dnsServers(), new TypeReference<List<String>>() { }),
                    ra.dnsServersLifetime(),
                    list(ra.dnsSearchList(), new TypeReference<List<String>>() { }),
                    ra.packetCount(),
                    ra.firstSeen(),
                    ra.lastSeen()
            ));
        }

        return Response.ok(NdpRouterAdvertisementsListResponse.create(total, advertisements)).build();
    }

    private static <T> List<T> list(@Nullable String json, TypeReference<List<T>> type) {
        if (json == null) {
            return List.of();
        }

        try {
            return OM.readValue(json, type);
        } catch (JacksonException e) {
            return List.of();
        }
    }

}
