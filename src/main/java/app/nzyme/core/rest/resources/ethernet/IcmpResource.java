package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.GeoData;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.beacons.BeaconDetector;
import app.nzyme.core.ethernet.icmp.ICMP;
import app.nzyme.core.ethernet.icmp.IcmpRegistryKeys;
import app.nzyme.core.ethernet.icmp.IcmpExclusionMatcher;
import app.nzyme.core.ethernet.icmp.IcmpExclusions;
import app.nzyme.core.ethernet.icmp.IcmpType;
import app.nzyme.core.ethernet.icmp.db.IcmpExclusionEntry;
import app.nzyme.core.ethernet.icmp.db.IcmpPacketEntry;
import app.nzyme.core.ethernet.icmp.db.IcmpRedirectEntry;
import app.nzyme.core.ethernet.icmp.db.IcmpTracerouteEntry;
import app.nzyme.core.ethernet.icmp.db.IcmpTunnelEntry;
import app.nzyme.core.ethernet.l4.db.L4AddressAttributes;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import app.nzyme.core.integrations.geoip.GeoIpLookupResult;
import app.nzyme.core.registry.RegistryChangeValidator;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.GenericConfigurationUpdateRequest;
import app.nzyme.core.rest.requests.IcmpExclusionRequest;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import app.nzyme.core.rest.responses.ethernet.icmp.*;
import app.nzyme.core.rest.responses.shared.HistogramValueStructureResponse;
import app.nzyme.core.rest.responses.shared.HistogramValueType;
import app.nzyme.core.rest.responses.shared.TwoColumnTableHistogramResponse;
import app.nzyme.core.rest.responses.shared.TwoColumnTableHistogramValueResponse;
import app.nzyme.core.database.generic.TwoColumnHistogramOrderColumn;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.rest.responses.shared.ThreeColumnTableHistogramResponse;
import app.nzyme.core.rest.responses.shared.ThreeColumnTableHistogramValueResponse;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/icmp")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "ICMP", description = "ICMP traffic the taps recorded. Queries and replies like echo (ping) are "
        + "aggregated per source, destination and message type within each tap report period. Error messages "
        + "like destination unreachable, time exceeded and redirect are kept per packet together with the header "
        + "of the packet that caused them. Redirects and traceroutes are detected from the error messages.")
public class IcmpResource extends TapDataHandlingResource {

    private static final Logger LOG = LogManager.getLogger(IcmpResource.class);

    private static final String MANAGE_PERMISSION = "ethernet_monitoring_manage";

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/packets")
    @Operation(operationId = "findIcmpPackets", summary = "List ICMP traffic",
            description = "Returns ICMP traffic in the time range, newest first by default. Queries and replies "
                    + "are aggregated rows with a packet count, error messages are single packets with the quoted "
                    + "original packet header. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Packets found.",
            content = @Content(schema = @Schema(implementation = IcmpPacketsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response packets(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Sorting column. Defaults to the time the traffic was last seen.") @QueryParam("order_column") @Nullable String orderColumnParam,
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

        ICMP.OrderColumn orderColumn = ICMP.OrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ICMP.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        ICMP icmp = nzyme.getEthernet().icmp();

        long total = icmp.countAllPackets(timeRange, filters, taps);

        List<IcmpPacketResponse> packets = Lists.newArrayList();
        for (IcmpPacketEntry p : icmp.findAllPackets(timeRange, filters, limit, offset, orderColumn, orderDirection, taps)) {
            packets.add(IcmpPacketResponse.create(
                    p.tapUuid(),
                    p.sourceAddress(),
                    addressResponse(p.sourceAddress(), p.sourceMac(), organizationId, tenantId),
                    macResponse(p.sourceMac(), organizationId, tenantId),
                    p.destinationAddress(),
                    addressResponse(p.destinationAddress(), p.destinationMac(), organizationId, tenantId),
                    macResponse(p.destinationMac(), organizationId, tenantId),
                    p.icmpType(),
                    IcmpType.name(p.icmpType()),
                    p.icmpCode(),
                    IcmpType.isError(p.icmpType()),
                    p.packetCount(),
                    p.bytesMin(),
                    p.bytesMax(),
                    p.ipTtl(),
                    p.ipTos(),
                    p.ipDf(),
                    p.redirectGateway(),
                    p.quotedProtocol(),
                    p.quotedSourceAddress(),
                    p.quotedDestinationAddress(),
                    p.quotedSourcePort(),
                    p.quotedDestinationPort(),
                    p.firstSeen(),
                    p.lastSeen()
            ));
        }

        return Response.ok(IcmpPacketsListResponse.create(total, packets)).build();
    }

    @GET
    @Path("/statistics")
    @Operation(operationId = "findIcmpStatistics", summary = "Get ICMP statistics",
            description = "Returns packet counts per time bucket, broken down into echo requests, echo replies, "
                    + "destination unreachable, time exceeded, redirect and other messages. The response is an "
                    + "object that maps each bucket timestamp to its values. The bucket size is chosen "
                    + "automatically from the time range.")
    @ApiResponse(responseCode = "200", description = "Statistics found.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    public Response statistics(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                               @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                               @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Map<DateTime, IcmpStatisticsBucketResponse> statistics = Maps.newHashMap();
        for (ICMP.StatisticsBucket b : nzyme.getEthernet().icmp().getStatistics(timeRange, bucketing, filters, taps)) {
            statistics.put(b.bucket(), IcmpStatisticsBucketResponse.create(
                    b.total(), b.echoRequests(), b.echoReplies(), b.unreachable(), b.timeExceeded(), b.redirects(), b.other()
            ));
        }

        return Response.ok(statistics).build();
    }

    @GET
    @Path("/histograms/addresses/top")
    @Operation(operationId = "findIcmpTopAddresses", summary = "List the most active ICMP sources or destinations",
            description = "Returns the addresses with the most ICMP packets in the time range, as a two column "
                    + "histogram of address and packet count. Paginated and sortable.")
    @ApiResponse(responseCode = "200", description = "Addresses found.",
            content = @Content(schema = @Schema(implementation = TwoColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown column, sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response topAddresses(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                 @Parameter(description = "Which end to rank: source or destination.") @QueryParam("column") @Nullable String columnParam,
                                 @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                 @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                 @Parameter(description = "Sorting column: KEY (address) or VALUE (packet count). Defaults to VALUE.") @QueryParam("order_column") @Nullable String orderColumnParam,
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

        String column = "destination".equalsIgnoreCase(columnParam) ? "destination" : "source";
        TwoColumnHistogramOrderColumn orderColumn = TwoColumnHistogramOrderColumn.VALUE;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = TwoColumnHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        ICMP icmp = nzyme.getEthernet().icmp();

        long total = icmp.countAddresses(column, timeRange, filters, taps);
        List<TwoColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (ICMP.TopAddress a : icmp.topAddresses(column, timeRange, filters, taps, orderColumn, orderDirection, limit, offset)) {
            values.add(TwoColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            addressResponse(a.address(), a.mac(), organizationId, tenantId),
                            HistogramValueType.L4_ADDRESS_NO_PORT,
                            null
                    ),
                    HistogramValueStructureResponse.create(a.packetCount(), HistogramValueType.INTEGER, null)
            ));
        }

        return Response.ok(TwoColumnTableHistogramResponse.create(total, true, values)).build();
    }

    @GET
    @Path("/histograms/types")
    @Operation(operationId = "findIcmpTypes", summary = "List ICMP message types by volume",
            description = "Returns the ICMP message types seen in the time range, as a two column histogram of "
                    + "message type and packet count. Paginated and sortable.")
    @ApiResponse(responseCode = "200", description = "Types found.",
            content = @Content(schema = @Schema(implementation = TwoColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response types(@Parameter(hidden = true) @Context SecurityContext sc,
                          @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                          @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                          @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                          @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                          @Parameter(description = "Sorting column: KEY (type) or VALUE (packet count). Defaults to VALUE.") @QueryParam("order_column") @Nullable String orderColumnParam,
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

        TwoColumnHistogramOrderColumn orderColumn = TwoColumnHistogramOrderColumn.VALUE;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = TwoColumnHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        ICMP icmp = nzyme.getEthernet().icmp();

        long total = icmp.countTypes(timeRange, filters, taps);
        List<TwoColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (ICMP.TypeCount t : icmp.typeCounts(timeRange, filters, taps, orderColumn, orderDirection, limit, offset)) {
            values.add(TwoColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            IcmpType.name(t.type()) + " (" + t.type() + ")",
                            HistogramValueType.GENERIC,
                            Map.of("icmp_type", String.valueOf(t.type()))
                    ),
                    HistogramValueStructureResponse.create(t.packetCount(), HistogramValueType.INTEGER, null)
            ));
        }

        return Response.ok(TwoColumnTableHistogramResponse.create(total, true, values)).build();
    }

    @GET
    @Path("/redirects")
    @Operation(operationId = "findIcmpRedirects", summary = "List detected ICMP redirects",
            description = "Returns every detected sender, advertised gateway and target host combination, most "
                    + "recently seen first by default. Supports the web interface filter builder. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Redirects found.",
            content = @Content(schema = @Schema(implementation = IcmpRedirectsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response redirects(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                              @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                              @Parameter(description = "Comma separated list of tap UUIDs. Only redirects observed by at least one of these taps are returned. Omit or pass * for all taps the user can access.") @QueryParam("taps") @Nullable String tapIds,
                              @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                              @Parameter(description = "Sorting column: SENDER_ADDRESS, GATEWAY_ADDRESS, TARGET_ADDRESS, PACKET_COUNT, FIRST_SEEN or LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                              @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                              @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                              @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        ICMP.RedirectOrderColumn orderColumn = ICMP.RedirectOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ICMP.RedirectOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        ICMP icmp = nzyme.getEthernet().icmp();

        List<IcmpRedirectResponse> redirects = Lists.newArrayList();
        for (IcmpRedirectEntry r : icmp.findAllRedirects(filters, taps, timeRange, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            redirects.add(IcmpRedirectResponse.create(
                    r.uuid(),
                    r.senderAddress(),
                    addressResponseByIp(r.senderAddress(), organizationId, tenantId),
                    macResponse(r.senderMac(), organizationId, tenantId),
                    r.gatewayAddress(),
                    addressResponse(r.gatewayAddress(), null, organizationId, tenantId),
                    r.targetAddress(),
                    addressResponse(r.targetAddress(), r.targetMac(), organizationId, tenantId),
                    macResponse(r.targetMac(), organizationId, tenantId),
                    redirectedDestinationResponses(r.redirectedDestinations(), organizationId, tenantId),
                    r.packetCount(),
                    r.tapUuids(),
                    r.firstSeen(),
                    r.lastSeen()
            ));
        }

        return Response.ok(IcmpRedirectsListResponse.create(icmp.countRedirects(filters, taps, timeRange, organizationId, tenantId), redirects)).build();
    }

    @GET
    @Path("/redirects/show/{redirect_uuid}")
    @Operation(operationId = "findIcmpRedirect", summary = "Get one detected ICMP redirect",
            description = "Returns a single redirect finding by its UUID, for sharing by link.")
    @ApiResponse(responseCode = "200", description = "Redirect found.",
            content = @Content(schema = @Schema(implementation = IcmpRedirectResponse.class)))
    @ApiResponse(responseCode = "404", description = "Redirect, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response redirect(@Parameter(hidden = true) @Context SecurityContext sc,
                             @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                             @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                             @Parameter(description = "Redirect UUID.") @PathParam("redirect_uuid") UUID redirectUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<IcmpRedirectEntry> r = nzyme.getEthernet().icmp().findRedirect(redirectUuid, organizationId, tenantId);
        if (r.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(IcmpRedirectResponse.create(
                r.get().uuid(),
                r.get().senderAddress(),
                addressResponseByIp(r.get().senderAddress(), organizationId, tenantId),
                macResponse(r.get().senderMac(), organizationId, tenantId),
                r.get().gatewayAddress(),
                addressResponse(r.get().gatewayAddress(), null, organizationId, tenantId),
                r.get().targetAddress(),
                addressResponse(r.get().targetAddress(), r.get().targetMac(), organizationId, tenantId),
                macResponse(r.get().targetMac(), organizationId, tenantId),
                redirectedDestinationResponses(r.get().redirectedDestinations(), organizationId, tenantId),
                r.get().packetCount(),
                r.get().tapUuids(),
                r.get().firstSeen(),
                r.get().lastSeen()
        )).build();
    }

    @GET
    @Path("/traceroutes")
    @Operation(operationId = "findIcmpTraceroutes", summary = "List detected traceroutes",
            description = "Returns detected traceroutes, most recently seen first by default. Each one names the "
                    + "tracing host, the traced destination and the routers that answered. Supports the web "
                    + "interface filter builder. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Traceroutes found.",
            content = @Content(schema = @Schema(implementation = IcmpTraceroutesListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response traceroutes(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                @Parameter(description = "Comma separated list of tap UUIDs. Only traceroutes observed by at least one of these taps are returned. Omit or pass * for all taps the user can access.") @QueryParam("taps") @Nullable String tapIds,
                                @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                @Parameter(description = "Sorting column: SOURCE_ADDRESS, DESTINATION_ADDRESS, PROTOCOL, ROUTER_COUNT, DESTINATION_REACHED, PACKET_COUNT, FIRST_SEEN or LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        ICMP.TracerouteOrderColumn orderColumn = ICMP.TracerouteOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ICMP.TracerouteOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        ICMP icmp = nzyme.getEthernet().icmp();

        List<IcmpTracerouteResponse> traceroutes = Lists.newArrayList();
        for (IcmpTracerouteEntry t : icmp.findAllTraceroutes(filters, taps, timeRange, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            traceroutes.add(IcmpTracerouteResponse.create(
                    t.uuid(),
                    t.sourceAddress(),
                    addressResponse(t.sourceAddress(), t.sourceMac(), organizationId, tenantId),
                    macResponse(t.sourceMac(), organizationId, tenantId),
                    t.destinationAddress(),
                    addressResponse(t.destinationAddress(), null, organizationId, tenantId),
                    t.protocol(),
                    protocolName(t.protocol()),
                    t.routers(),
                    t.packetCount(),
                    t.destinationReached(),
                    t.tapUuids(),
                    t.firstSeen(),
                    t.lastSeen()
            ));
        }

        return Response.ok(IcmpTraceroutesListResponse.create(icmp.countTraceroutes(filters, taps, timeRange, organizationId, tenantId), traceroutes)).build();
    }

    @GET
    @Path("/traceroutes/show/{traceroute_uuid}")
    @Operation(operationId = "findIcmpTraceroute", summary = "Get one detected traceroute",
            description = "Returns a single traceroute finding by its UUID, for sharing by link.")
    @ApiResponse(responseCode = "200", description = "Traceroute found.",
            content = @Content(schema = @Schema(implementation = IcmpTracerouteResponse.class)))
    @ApiResponse(responseCode = "404", description = "Traceroute, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response traceroute(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                               @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                               @Parameter(description = "Traceroute UUID.") @PathParam("traceroute_uuid") UUID tracerouteUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<IcmpTracerouteEntry> t = nzyme.getEthernet().icmp().findTraceroute(tracerouteUuid, organizationId, tenantId);
        if (t.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(IcmpTracerouteResponse.create(
                t.get().uuid(),
                t.get().sourceAddress(),
                addressResponse(t.get().sourceAddress(), t.get().sourceMac(), organizationId, tenantId),
                macResponse(t.get().sourceMac(), organizationId, tenantId),
                t.get().destinationAddress(),
                addressResponse(t.get().destinationAddress(), null, organizationId, tenantId),
                t.get().protocol(),
                protocolName(t.get().protocol()),
                t.get().routers(),
                t.get().packetCount(),
                t.get().destinationReached(),
                t.get().tapUuids(),
                t.get().firstSeen(),
                t.get().lastSeen()
        )).build();
    }

    @GET
    @Path("/traceroutes/histogram")
    @Operation(operationId = "findIcmpTraceroutesHistogram", summary = "Get the number of traceroutes over time",
            description = "Returns the number of traceroutes active in each time bucket, honoring the filter builder.")
    @ApiResponse(responseCode = "200", description = "Histogram found.")
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response traceroutesHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                         @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                         @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                         @Parameter(description = "Comma separated list of tap UUIDs. Only traceroutes observed by at least one of these taps are counted. Omit or pass * for all taps the user can access.") @QueryParam("taps") @Nullable String tapIds,
                                         @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        Map<DateTime, Integer> histogram = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().icmp()
                .tracerouteHistogram(filters, taps, organizationId, tenantId, timeRange, bucketing)) {
            histogram.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(histogram).build();
    }

    @GET
    @Path("/traceroutes/histograms")
    @Operation(operationId = "findIcmpTraceroutesBreakdown", summary = "Break traceroutes down by sender, destination or protocol",
            description = "Returns the traced hosts, destinations or probe protocols with the most traceroutes, as a "
                    + "two column histogram. Honors the filter builder. Paginated and sortable.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = TwoColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown column, sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response traceroutesBreakdown(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                         @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                         @Parameter(description = "What to break down by: SENDER, DESTINATION or PROTOCOL.") @QueryParam("by") @Nullable String byParam,
                                         @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                         @Parameter(description = "Comma separated list of tap UUIDs. Only traceroutes observed by at least one of these taps are counted. Omit or pass * for all taps the user can access.") @QueryParam("taps") @Nullable String tapIds,
                                         @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                         @Parameter(description = "Sorting column: KEY or VALUE (count). Defaults to VALUE.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                         @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                         @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                         @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        String by = byParam == null ? "" : byParam.toUpperCase();
        if (!by.equals("SENDER") && !by.equals("DESTINATION") && !by.equals("PROTOCOL")) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        TwoColumnHistogramOrderColumn orderColumn = TwoColumnHistogramOrderColumn.VALUE;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = TwoColumnHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        ICMP icmp = nzyme.getEthernet().icmp();

        long total = icmp.countTracerouteGroups(by, filters, taps, timeRange, organizationId, tenantId);
        List<TwoColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (ICMP.TracerouteGroup g : icmp.tracerouteGroups(by, filters, taps, timeRange, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            HistogramValueStructureResponse keyValue;
            if (by.equals("PROTOCOL")) {
                Integer protocol = g.value() == null ? null : Integer.valueOf(g.value());
                keyValue = HistogramValueStructureResponse.create(
                        protocol == null ? "Unknown" : protocolName(protocol) + " (" + protocol + ")",
                        HistogramValueType.GENERIC,
                        protocol == null ? null : Map.of("protocol", String.valueOf(protocol))
                );
            } else {
                keyValue = HistogramValueStructureResponse.create(
                        addressResponse(g.value(), g.mac(), organizationId, tenantId),
                        HistogramValueType.L4_ADDRESS_NO_PORT,
                        null
                );
            }
            values.add(TwoColumnTableHistogramValueResponse.create(
                    keyValue,
                    HistogramValueStructureResponse.create(g.count(), HistogramValueType.INTEGER, null)
            ));
        }

        return Response.ok(TwoColumnTableHistogramResponse.create(total, true, values)).build();
    }

    private static final int MAXIMUM_NAME_LENGTH = 128;
    private static final int MAXIMUM_DESCRIPTION_LENGTH = 10000;

    @GET
    @Path("/exclusions")
    @Operation(operationId = "findIcmpExclusions", summary = "List ICMP exclusions of a tenant",
            description = "Returns the exclusions of the tenant, sorted by name by default. Exclusions keep known "
                    + "good sources, like a legitimate second router or a monitoring host, out of redirect and "
                    + "traceroute detection. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Exclusions found.",
            content = @Content(schema = @Schema(implementation = IcmpExclusionsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findExclusions(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                   @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                   @Parameter(description = "Sorting column: NAME, ENABLED, LAST_MATCHED_AT, MATCH_COUNT, CREATED_AT or UPDATED_AT. Defaults to NAME.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                   @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                   @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                   @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);

        IcmpExclusions.OrderColumn orderColumn = IcmpExclusions.OrderColumn.NAME;
        OrderDirection orderDirection = OrderDirection.ASC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = IcmpExclusions.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        IcmpExclusions exclusions = nzyme.getEthernet().icmp().exclusions();

        long total = exclusions.count(filters, organizationId, tenantId);
        List<IcmpExclusionResponse> result = Lists.newArrayList();
        for (IcmpExclusionEntry exclusion : exclusions.findAll(filters, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            result.add(exclusionResponse(exclusion));
        }

        return Response.ok(IcmpExclusionsListResponse.create(total, result)).build();
    }

    @GET
    @Path("/exclusions/show/{exclusion_uuid}")
    @Operation(operationId = "findIcmpExclusion", summary = "Get an ICMP exclusion")
    @ApiResponse(responseCode = "200", description = "Exclusion found.",
            content = @Content(schema = @Schema(implementation = IcmpExclusionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Exclusion, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findExclusion(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                  @Parameter(description = "Exclusion UUID.") @PathParam("exclusion_uuid") UUID exclusionUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<IcmpExclusionEntry> exclusion = nzyme.getEthernet().icmp().exclusions()
                .find(exclusionUuid, organizationId, tenantId);
        if (exclusion.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(exclusionResponse(exclusion.get())).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/exclusions/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "createIcmpExclusion", summary = "Create an ICMP exclusion",
            description = "Creates an exclusion. It takes effect with the next detection run. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "201", description = "Exclusion created.",
            content = @Content(schema = @Schema(implementation = IcmpExclusionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid name or criteria. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response createExclusion(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                    @RequestBody(description = "The exclusion.", required = true, content = @Content(mediaType = "application/json"))
                                    IcmpExclusionRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String name;
        String description;
        IcmpExclusions.Criteria criteria;
        try {
            name = validateName(req.name());
            description = validateDescription(req.description());
            criteria = criteria(req).normalized();
        } catch (IllegalArgumentException e) {
            return unprocessable(e.getMessage());
        }

        AuthenticatedUser user = getAuthenticatedUser(sc);
        IcmpExclusionEntry created = nzyme.getEthernet().icmp().exclusions().create(
                organizationId, tenantId, name, description, req.enabled() == null || req.enabled(), criteria,
                user.getUserId(), user.getName(), user.getEmail()
        );

        return Response.status(Response.Status.CREATED).entity(exclusionResponse(created)).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/exclusions/organization/{organization_id}/tenant/{tenant_id}/show/{exclusion_uuid}")
    @Operation(operationId = "updateIcmpExclusion", summary = "Update an ICMP exclusion",
            description = "Replaces name, description, enabled state and all criteria. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Exclusion updated.",
            content = @Content(schema = @Schema(implementation = IcmpExclusionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Exclusion, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid name or criteria. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response updateExclusion(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                    @Parameter(description = "Exclusion UUID.") @PathParam("exclusion_uuid") UUID exclusionUuid,
                                    @RequestBody(description = "The new exclusion data.", required = true, content = @Content(mediaType = "application/json"))
                                    IcmpExclusionRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        IcmpExclusions exclusions = nzyme.getEthernet().icmp().exclusions();
        Optional<IcmpExclusionEntry> existing = exclusions.find(exclusionUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String name;
        String description;
        IcmpExclusions.Criteria criteria;
        try {
            name = validateName(req.name());
            description = validateDescription(req.description());
            criteria = criteria(req).normalized();
        } catch (IllegalArgumentException e) {
            return unprocessable(e.getMessage());
        }

        exclusions.update(existing.get().id(), name, description,
                req.enabled() == null ? existing.get().enabled() : req.enabled(), criteria);

        return Response.ok(exclusionResponse(exclusions.find(exclusionUuid, organizationId, tenantId).orElseThrow())).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/exclusions/organization/{organization_id}/tenant/{tenant_id}/show/{exclusion_uuid}/enabled/{enabled}")
    @Operation(operationId = "setIcmpExclusionEnabled", summary = "Enable or disable an ICMP exclusion",
            description = "A disabled exclusion is kept but no longer applied. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Exclusion updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Exclusion, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response setExclusionEnabled(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                        @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                        @Parameter(description = "Exclusion UUID.") @PathParam("exclusion_uuid") UUID exclusionUuid,
                                        @Parameter(description = "true or false.") @PathParam("enabled") boolean enabled) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        IcmpExclusions exclusions = nzyme.getEthernet().icmp().exclusions();
        Optional<IcmpExclusionEntry> existing = exclusions.find(exclusionUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        exclusions.setEnabled(existing.get().id(), enabled);

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/exclusions/organization/{organization_id}/tenant/{tenant_id}/show/{exclusion_uuid}")
    @Operation(operationId = "deleteIcmpExclusion", summary = "Delete an ICMP exclusion",
            description = "Deletes an exclusion. Suppressed detections are detected again with the next detection "
                    + "run. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Exclusion deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Exclusion, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteExclusion(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                    @Parameter(description = "Exclusion UUID.") @PathParam("exclusion_uuid") UUID exclusionUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        IcmpExclusions exclusions = nzyme.getEthernet().icmp().exclusions();
        Optional<IcmpExclusionEntry> existing = exclusions.find(exclusionUuid, organizationId, tenantId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        exclusions.delete(existing.get().id());

        return Response.ok().build();
    }

    private static IcmpExclusions.Criteria criteria(IcmpExclusionRequest req) {
        return new IcmpExclusions.Criteria(req.detectionTypes(), req.sourceCidr(), req.sourceMac(), req.gatewayCidr());
    }

    private static String validateName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("The name cannot be empty.");
        }
        if (trimmed.length() > MAXIMUM_NAME_LENGTH) {
            throw new IllegalArgumentException("The name cannot be longer than " + MAXIMUM_NAME_LENGTH + " characters.");
        }
        return trimmed;
    }

    private static String validateDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return null;
        }
        if (description.length() > MAXIMUM_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("The description is too long.");
        }
        return description.trim();
    }

    private static Response unprocessable(String message) {
        return Response.status(422).entity(ErrorResponse.create(message)).build();
    }

    private static IcmpExclusionResponse exclusionResponse(IcmpExclusionEntry e) {
        return IcmpExclusionResponse.create(
                e.uuid(), e.name(), e.description(), e.enabled(), e.detectionTypes(), e.sourceCidr(), e.sourceMac(),
                e.gatewayCidr(), e.matchCount(), e.lastMatchedAt(), e.createdByName(), e.createdByEmail(),
                e.createdAt(), e.updatedAt()
        );
    }

    private static final RegistryKey[] CONFIGURATION_KEYS = {
            IcmpRegistryKeys.REDIRECT_DETECTION_ENABLED,
            IcmpRegistryKeys.TRACEROUTE_DETECTION_ENABLED,
            IcmpRegistryKeys.DETECTION_WINDOW_MINUTES,
            IcmpRegistryKeys.TRACEROUTE_MINIMUM_ROUTERS,
            IcmpRegistryKeys.DETECTION_RETENTION_TIME_DAYS,
            IcmpRegistryKeys.ALERT_ON_REDIRECT,
            IcmpRegistryKeys.ALERT_ON_TRACEROUTE,
            IcmpRegistryKeys.TUNNEL_DETECTION_ENABLED,
            IcmpRegistryKeys.TUNNEL_MINIMUM_PAYLOAD_BYTES,
            IcmpRegistryKeys.TUNNEL_MINIMUM_PACKETS,
            IcmpRegistryKeys.ALERT_ON_TUNNEL
    };

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "findIcmpConfiguration", summary = "Get the ICMP detection configuration of a tenant",
            description = "Returns the redirect and traceroute detection settings and alerting of the tenant. "
                    + "Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = IcmpConfigurationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response getConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<ConfigurationEntryResponse> detection = Lists.newArrayList();
        detection.add(booleanEntry(IcmpRegistryKeys.REDIRECT_DETECTION_ENABLED, "Redirect detection enabled", organizationId, tenantId));
        detection.add(booleanEntry(IcmpRegistryKeys.TRACEROUTE_DETECTION_ENABLED, "Traceroute detection enabled", organizationId, tenantId));
        detection.add(numberEntry(IcmpRegistryKeys.DETECTION_WINDOW_MINUTES, "Detection window (Minutes)", organizationId, tenantId));
        detection.add(numberEntry(IcmpRegistryKeys.TRACEROUTE_MINIMUM_ROUTERS, "Traceroute: Minimum routers answering", organizationId, tenantId));
        detection.add(booleanEntry(IcmpRegistryKeys.TUNNEL_DETECTION_ENABLED, "Tunnel detection enabled", organizationId, tenantId));
        detection.add(numberEntry(IcmpRegistryKeys.TUNNEL_MINIMUM_PAYLOAD_BYTES, "Tunnel: Minimum echo payload (Bytes)", organizationId, tenantId));
        detection.add(numberEntry(IcmpRegistryKeys.TUNNEL_MINIMUM_PACKETS, "Tunnel: Minimum packets", organizationId, tenantId));
        detection.add(numberEntry(IcmpRegistryKeys.DETECTION_RETENTION_TIME_DAYS, "Detection retention time (Days)", organizationId, tenantId));

        List<ConfigurationEntryResponse> alerting = Lists.newArrayList();
        alerting.add(booleanEntry(IcmpRegistryKeys.ALERT_ON_REDIRECT, "Alert on redirect", organizationId, tenantId));
        alerting.add(booleanEntry(IcmpRegistryKeys.ALERT_ON_TRACEROUTE, "Alert on traceroute", organizationId, tenantId));
        alerting.add(booleanEntry(IcmpRegistryKeys.ALERT_ON_TUNNEL, "Alert on tunnel", organizationId, tenantId));

        return Response.ok(IcmpConfigurationResponse.create(detection, alerting)).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "updateIcmpConfiguration", summary = "Update the ICMP detection configuration of a tenant",
            description = "Updates one or more settings. Accepted keys are the keys returned by the configuration "
                    + "endpoint. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The change contains a key that is not part of the ICMP configuration.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The change is empty or a value violates the constraints of its configuration key.", content = @Content)
    public Response setConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                     @RequestBody(description = "Map of configuration keys and their new values, wrapped in a change field.", required = true, content = @Content(mediaType = "application/json"))
                                     @Valid GenericConfigurationUpdateRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (req.change().isEmpty()) {
            return Response.status(422).build();
        }

        Optional<List<RegistryChangeValidator.Change>> changes = RegistryChangeValidator
                .allowing(CONFIGURATION_KEYS)
                .validate(req.change());

        if (changes.isEmpty()) {
            return Response.status(422).build();
        }

        for (RegistryChangeValidator.Change c : changes.get()) {
            nzyme.getDatabaseCoreRegistry().setValue(c.key(), c.value(), organizationId, tenantId);
        }

        return Response.ok().build();
    }

    /*
     * Helpers.
     */

    private ConfigurationEntryResponse booleanEntry(RegistryKey key, String title, UUID organizationId, UUID tenantId) {
        return ConfigurationEntryResponse.create(
                key.key(),
                title,
                nzyme.getEthernet().icmp().booleanValue(key, organizationId, tenantId),
                ConfigurationEntryValueType.BOOLEAN,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                "ethernet-icmp-config"
        );
    }

    private ConfigurationEntryResponse numberEntry(RegistryKey key, String title, UUID organizationId, UUID tenantId) {
        return ConfigurationEntryResponse.create(
                key.key(),
                title,
                nzyme.getEthernet().icmp().intValue(key, organizationId, tenantId),
                ConfigurationEntryValueType.NUMBER,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                "ethernet-icmp-config"
        );
    }

    @Nullable
    @GET
    @Path("/tunnels")
    @Operation(operationId = "findIcmpTunnels", summary = "List suspected ICMP tunnels",
            description = "Returns echo flows with large or sustained payloads that may carry a covert channel, "
                    + "grouped by source and destination. Honors the filter builder, tap selection and time "
                    + "range. Paginated and sortable.")
    @ApiResponse(responseCode = "200", description = "Tunnels found.",
            content = @Content(schema = @Schema(implementation = IcmpTunnelsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response tunnels(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Comma separated list of tap UUIDs. Only tunnels observed by at least one of these taps are returned. Omit or pass * for all taps the user can access.") @QueryParam("taps") @Nullable String tapIds,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "Sorting column: SOURCE_ADDRESS, DESTINATION_ADDRESS, MAX_PAYLOAD_BYTES, PACKET_COUNT, FIRST_SEEN or LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        ICMP.TunnelOrderColumn orderColumn = ICMP.TunnelOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ICMP.TunnelOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        ICMP icmp = nzyme.getEthernet().icmp();

        List<IcmpTunnelResponse> tunnels = Lists.newArrayList();
        for (IcmpTunnelEntry t : icmp.findAllTunnels(filters, taps, timeRange, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            tunnels.add(IcmpTunnelResponse.create(
                    t.uuid(),
                    t.sourceAddress(),
                    addressResponse(t.sourceAddress(), t.sourceMac(), organizationId, tenantId),
                    macResponse(t.sourceMac(), organizationId, tenantId),
                    t.destinationAddress(),
                    addressResponse(t.destinationAddress(), t.destinationMac(), organizationId, tenantId),
                    macResponse(t.destinationMac(), organizationId, tenantId),
                    t.maxPayloadBytes(),
                    t.packetCount(),
                    t.tapUuids(),
                    t.firstSeen(),
                    t.lastSeen()
            ));
        }

        return Response.ok(IcmpTunnelsListResponse.create(
                icmp.countTunnels(filters, taps, timeRange, organizationId, tenantId), tunnels)).build();
    }

    @GET
    @Path("/tunnels/histogram")
    @Operation(operationId = "findIcmpTunnelsHistogram", summary = "Get the number of ICMP tunnels over time",
            description = "Returns the number of suspected ICMP tunnels active in each time bucket, honoring the filter builder.")
    @ApiResponse(responseCode = "200", description = "Histogram found.")
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response tunnelsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                     @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                     @Parameter(description = "Comma separated list of tap UUIDs. Only tunnels observed by at least one of these taps are counted. Omit or pass * for all taps the user can access.") @QueryParam("taps") @Nullable String tapIds,
                                     @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        Map<DateTime, Integer> histogram = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().icmp()
                .tunnelHistogram(filters, taps, organizationId, tenantId, timeRange, bucketing)) {
            histogram.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(histogram).build();
    }

    @GET
    @Path("/tunnels/show/{tunnel_uuid}")
    @Operation(operationId = "findIcmpTunnel", summary = "Get one suspected ICMP tunnel",
            description = "Returns a single tunnel finding by its UUID, for sharing by link.")
    @ApiResponse(responseCode = "200", description = "Tunnel found.",
            content = @Content(schema = @Schema(implementation = IcmpTunnelResponse.class)))
    @ApiResponse(responseCode = "404", description = "Tunnel, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response tunnel(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Tunnel UUID.") @PathParam("tunnel_uuid") UUID tunnelUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<IcmpTunnelEntry> t = nzyme.getEthernet().icmp().findTunnel(tunnelUuid, organizationId, tenantId);
        if (t.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(IcmpTunnelResponse.create(
                t.get().uuid(),
                t.get().sourceAddress(),
                addressResponse(t.get().sourceAddress(), t.get().sourceMac(), organizationId, tenantId),
                macResponse(t.get().sourceMac(), organizationId, tenantId),
                t.get().destinationAddress(),
                addressResponse(t.get().destinationAddress(), t.get().destinationMac(), organizationId, tenantId),
                macResponse(t.get().destinationMac(), organizationId, tenantId),
                t.get().maxPayloadBytes(),
                t.get().packetCount(),
                t.get().tapUuids(),
                t.get().firstSeen(),
                t.get().lastSeen()
        )).build();
    }

    @GET
    @Path("/tunnels/histograms")
    @Operation(operationId = "findIcmpTunnelsBreakdown", summary = "Break tunnels down by peer or source",
            description = "Returns the top peers or sources by the number of distinct hosts on the other side, as "
                    + "a two column histogram. A peer shared by many hosts is a likely command-and-control endpoint. "
                    + "Honors the filter builder, tap selection and time range. Paginated and sortable.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = TwoColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown column, sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response tunnelsBreakdown(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                     @Parameter(description = "What to break down by: PEER or SOURCE.") @QueryParam("by") @Nullable String byParam,
                                     @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                     @Parameter(description = "Comma separated list of tap UUIDs to include. Omit or pass * for all taps the user can access.") @QueryParam("taps") @Nullable String tapIds,
                                     @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                     @Parameter(description = "Sorting column: KEY or VALUE (count). Defaults to VALUE.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                     @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                     @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                     @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        String by = byParam == null ? "" : byParam.toUpperCase();
        if (!by.equals("PEER") && !by.equals("SOURCE")) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        TwoColumnHistogramOrderColumn orderColumn = TwoColumnHistogramOrderColumn.VALUE;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = TwoColumnHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        ICMP icmp = nzyme.getEthernet().icmp();
        List<TwoColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (ICMP.UnreachableAddressGroup g : icmp.tunnelBreakdown(by, filters, taps, timeRange, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            values.add(TwoColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            addressResponse(g.address(), g.mac(), organizationId, tenantId),
                            HistogramValueType.L4_ADDRESS_NO_PORT, null),
                    HistogramValueStructureResponse.create(g.packetCount(), HistogramValueType.INTEGER, null)
            ));
        }

        return Response.ok(TwoColumnTableHistogramResponse.create(
                icmp.countTunnelBreakdown(by, filters, taps, timeRange, organizationId, tenantId), true, values)).build();
    }

    @GET
    @Path("/unreachables")
    @Operation(operationId = "findIcmpUnreachables", summary = "List port-unreachable events",
            description = "Returns ICMP port-unreachable messages grouped by host, source, protocol and "
                    + "port. Each row is a host that received traffic on a closed port. Honors the filter builder, "
                    + "tap selection and time range. Paginated and sortable.")
    @ApiResponse(responseCode = "200", description = "Unreachables found.",
            content = @Content(schema = @Schema(implementation = IcmpUnreachablesListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response unreachables(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                 @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                 @Parameter(description = "Comma separated list of tap UUIDs to include. Omit or pass * for all taps the user can access.") @QueryParam("taps") @Nullable String tapIds,
                                 @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                 @Parameter(description = "Sorting column: HOST, SOURCE, PROTOCOL, PORT, PACKET_COUNT, FIRST_SEEN or LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                 @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                 @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                 @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        ICMP.UnreachableOrderColumn orderColumn = ICMP.UnreachableOrderColumn.PACKET_COUNT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ICMP.UnreachableOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        ICMP icmp = nzyme.getEthernet().icmp();

        List<IcmpUnreachableResponse> rows = Lists.newArrayList();
        for (ICMP.UnreachableRow r : icmp.findUnreachables(timeRange, filters, taps, orderColumn, orderDirection, limit, offset)) {
            rows.add(IcmpUnreachableResponse.create(
                    r.hostAddress(),
                    addressResponse(r.hostAddress(), r.hostMac(), organizationId, tenantId),
                    macResponse(r.hostMac(), organizationId, tenantId),
                    r.sourceAddress(),
                    addressResponse(r.sourceAddress(), r.sourceMac(), organizationId, tenantId),
                    macResponse(r.sourceMac(), organizationId, tenantId),
                    r.protocol(),
                    protocolName(r.protocol()),
                    r.port(),
                    serviceName(r.port()),
                    r.packetCount(),
                    r.firstSeen(),
                    r.lastSeen()
            ));
        }

        return Response.ok(IcmpUnreachablesListResponse.create(
                icmp.countUnreachables(timeRange, filters, taps), rows)).build();
    }

    @GET
    @Path("/unreachables/histogram")
    @Operation(operationId = "findIcmpUnreachablesHistogram", summary = "Port-unreachable volume over time",
            description = "Returns the number of port-unreachable packets in each time bucket. Honors the filter "
                    + "builder, tap selection and time range.")
    @ApiResponse(responseCode = "200", description = "Histogram found.")
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response unreachablesHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                          @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                          @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                          @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                          @Parameter(description = "Comma separated list of tap UUIDs to include. Omit or pass * for all taps the user can access.") @QueryParam("taps") @Nullable String tapIds,
                                          @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        Map<DateTime, Integer> histogram = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().icmp()
                .unreachableHistogram(timeRange, filters, taps, bucketing)) {
            histogram.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(histogram).build();
    }

    @GET
    @Path("/unreachables/histograms")
    @Operation(operationId = "findIcmpUnreachablesBreakdown", summary = "Break port-unreachables down by port, host or source",
            description = "Returns the top closed ports and services, hosts or sources, as a two column "
                    + "histogram. Honors the filter builder, tap selection and time range. Paginated and sortable.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = TwoColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown column, sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response unreachablesBreakdown(@Parameter(hidden = true) @Context SecurityContext sc,
                                          @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                          @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                          @Parameter(description = "What to break down by: PORT, HOST or SOURCE.") @QueryParam("by") @Nullable String byParam,
                                          @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                          @Parameter(description = "Comma separated list of tap UUIDs to include. Omit or pass * for all taps the user can access.") @QueryParam("taps") @Nullable String tapIds,
                                          @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                          @Parameter(description = "Sorting column: KEY or VALUE (packets). Defaults to VALUE.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                          @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                          @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                          @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        String by = byParam == null ? "" : byParam.toUpperCase();
        if (!by.equals("PORT") && !by.equals("HOST") && !by.equals("SOURCE")) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        TwoColumnHistogramOrderColumn orderColumn = TwoColumnHistogramOrderColumn.VALUE;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = TwoColumnHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        ICMP icmp = nzyme.getEthernet().icmp();
        List<TwoColumnTableHistogramValueResponse> values = Lists.newArrayList();
        long total;

        if (by.equals("PORT")) {
            total = icmp.countUnreachablePortGroups(timeRange, filters, taps);
            for (ICMP.UnreachablePortGroup g : icmp.unreachablePortGroups(timeRange, filters, taps, orderColumn, orderDirection, limit, offset)) {
                String label = (g.protocol() == null ? "" : protocolName(g.protocol()) + "/") + g.port();
                values.add(TwoColumnTableHistogramValueResponse.create(
                        HistogramValueStructureResponse.create(label, HistogramValueType.GENERIC,
                                Map.of("port", String.valueOf(g.port()))),
                        HistogramValueStructureResponse.create(g.packetCount(), HistogramValueType.INTEGER, null)
                ));
            }
        } else {
            String column = by.equals("HOST") ? "source" : "destination";
            total = icmp.countUnreachableAddressGroups(column, timeRange, filters, taps);
            for (ICMP.UnreachableAddressGroup g : icmp.unreachableAddressGroups(column, timeRange, filters, taps, orderColumn, orderDirection, limit, offset)) {
                values.add(TwoColumnTableHistogramValueResponse.create(
                        HistogramValueStructureResponse.create(
                                addressResponse(g.address(), g.mac(), organizationId, tenantId),
                                HistogramValueType.L4_ADDRESS_NO_PORT, null),
                        HistogramValueStructureResponse.create(g.packetCount(), HistogramValueType.INTEGER, null)
                ));
            }
        }

        return Response.ok(TwoColumnTableHistogramResponse.create(total, true, values)).build();
    }

    @GET
    @Path("/unreachables/histograms/host-port")
    @Operation(operationId = "findIcmpUnreachablesByHostAndPort", summary = "Top host and port pairs",
            description = "Returns the host and closed port pairs with the most port-unreachable packets, as a "
                    + "three column histogram of host, port and packet count. Honors the filter builder, tap "
                    + "selection and time range. Paginated and sortable per column.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response unreachablesByHostAndPort(@Parameter(hidden = true) @Context SecurityContext sc,
                                              @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                              @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                              @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                              @Parameter(description = "Comma separated list of tap UUIDs to include. Omit or pass * for all taps the user can access.") @QueryParam("taps") @Nullable String tapIds,
                                              @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                              @Parameter(description = "Sorting column: KEY (host), VALUE1 (port) or VALUE2 (packets). Defaults to VALUE2.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                              @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                              @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                              @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        ThreeColumnWithKeyHistogramOrderColumn orderColumn = ThreeColumnWithKeyHistogramOrderColumn.VALUE2;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ThreeColumnWithKeyHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        ICMP icmp = nzyme.getEthernet().icmp();

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (ICMP.UnreachableHostPortGroup g : icmp.unreachableHostPortGroups(timeRange, filters, taps, orderColumn, orderDirection, limit, offset)) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            addressResponse(g.address(), g.mac(), organizationId, tenantId),
                            HistogramValueType.L4_ADDRESS_NO_PORT, null),
                    HistogramValueStructureResponse.create(g.port(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(g.packetCount(), HistogramValueType.INTEGER, null),
                    g.address()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(
                icmp.countUnreachableHostPortGroups(timeRange, filters, taps), false, values)).build();
    }

    @GET
    @Path("/unreachable-hosts")
    @Operation(operationId = "findIcmpUnreachableHosts", summary = "List host and network unreachable events",
            description = "Returns ICMP host-unreachable and net-unreachable messages grouped by source, "
                    + "unreachable host, protocol and reason. A router sends these when it cannot deliver a "
                    + "packet, so they cover TCP, UDP and ICMP. Honors the filter builder, tap selection and "
                    + "time range. Paginated and sortable.")
    @ApiResponse(responseCode = "200", description = "Events found.",
            content = @Content(schema = @Schema(implementation = IcmpUnreachableHostsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response unreachableHosts(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                     @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                     @Parameter(description = "Comma separated list of tap UUIDs to include. Omit or pass * for all taps the user can access.") @QueryParam("taps") @Nullable String tapIds,
                                     @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                     @Parameter(description = "Sorting column: SOURCE, UNREACHABLE_HOST, PROTOCOL, REASON, PACKET_COUNT, FIRST_SEEN or LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                     @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                     @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                     @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        ICMP.UnreachableHostOrderColumn orderColumn = ICMP.UnreachableHostOrderColumn.PACKET_COUNT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ICMP.UnreachableHostOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        ICMP icmp = nzyme.getEthernet().icmp();

        List<IcmpUnreachableHostResponse> rows = Lists.newArrayList();
        for (ICMP.UnreachableHostEvent e : icmp.findUnreachableHostEvents(timeRange, filters, taps, orderColumn, orderDirection, limit, offset)) {
            rows.add(IcmpUnreachableHostResponse.create(
                    e.sourceAddress(),
                    addressResponse(e.sourceAddress(), e.sourceMac(), organizationId, tenantId),
                    macResponse(e.sourceMac(), organizationId, tenantId),
                    e.unreachableHost(),
                    addressResponse(e.unreachableHost(), null, organizationId, tenantId),
                    e.protocol(),
                    protocolName(e.protocol()),
                    e.code(),
                    unreachableReason(e.code()),
                    e.routerAddress(),
                    addressResponse(e.routerAddress(), e.routerMac(), organizationId, tenantId),
                    macResponse(e.routerMac(), organizationId, tenantId),
                    e.packetCount(),
                    e.firstSeen(),
                    e.lastSeen()
            ));
        }

        return Response.ok(IcmpUnreachableHostsListResponse.create(
                icmp.countUnreachableHostEvents(timeRange, filters, taps), rows)).build();
    }

    @GET
    @Path("/unreachable-hosts/histogram")
    @Operation(operationId = "findIcmpUnreachableHostsHistogram", summary = "Host and network unreachable volume over time",
            description = "Returns the number of host-unreachable and net-unreachable packets in each time bucket. "
                    + "Honors the filter builder, tap selection and time range.")
    @ApiResponse(responseCode = "200", description = "Histogram found.")
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response unreachableHostsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                              @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                              @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                              @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                              @Parameter(description = "Comma separated list of tap UUIDs to include. Omit or pass * for all taps the user can access.") @QueryParam("taps") @Nullable String tapIds,
                                              @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        Map<DateTime, Integer> histogram = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().icmp()
                .unreachableHostsHistogram(timeRange, filters, taps, bucketing)) {
            histogram.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(histogram).build();
    }

    @GET
    @Path("/unreachable-hosts/histograms")
    @Operation(operationId = "findIcmpUnreachableHostsBreakdown", summary = "Break host unreachables down by host, source, router or protocol",
            description = "Returns the top unreachable hosts, sources, reporting routers or protocols, as a two "
                    + "column histogram. Honors the filter builder, tap selection and time range. Paginated and sortable.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = TwoColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown column, sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response unreachableHostsBreakdown(@Parameter(hidden = true) @Context SecurityContext sc,
                                              @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                              @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                              @Parameter(description = "What to break down by: UNREACHABLE_HOST, SOURCE, ROUTER or PROTOCOL.") @QueryParam("by") @Nullable String byParam,
                                              @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                              @Parameter(description = "Comma separated list of tap UUIDs to include. Omit or pass * for all taps the user can access.") @QueryParam("taps") @Nullable String tapIds,
                                              @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                              @Parameter(description = "Sorting column: KEY or VALUE (packets). Defaults to VALUE.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                              @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                              @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                              @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        String by = byParam == null ? "" : byParam.toUpperCase();
        if (!by.equals("UNREACHABLE_HOST") && !by.equals("SOURCE") && !by.equals("ROUTER") && !by.equals("PROTOCOL")) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        TwoColumnHistogramOrderColumn orderColumn = TwoColumnHistogramOrderColumn.VALUE;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = TwoColumnHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        ICMP icmp = nzyme.getEthernet().icmp();
        List<TwoColumnTableHistogramValueResponse> values = Lists.newArrayList();

        for (ICMP.UnreachableAddressGroup g : icmp.unreachableHostBreakdown(by, timeRange, filters, taps, orderColumn, orderDirection, limit, offset)) {
            HistogramValueStructureResponse keyValue;
            if (by.equals("PROTOCOL")) {
                Integer protocol = g.address() == null ? null : Integer.valueOf(g.address());
                keyValue = HistogramValueStructureResponse.create(
                        protocol == null ? "Unknown" : protocolName(protocol) + " (" + protocol + ")",
                        HistogramValueType.GENERIC,
                        protocol == null ? null : Map.of("protocol", String.valueOf(protocol)));
            } else {
                keyValue = HistogramValueStructureResponse.create(
                        addressResponse(g.address(), g.mac(), organizationId, tenantId),
                        HistogramValueType.L4_ADDRESS_NO_PORT, null);
            }
            values.add(TwoColumnTableHistogramValueResponse.create(
                    keyValue,
                    HistogramValueStructureResponse.create(g.packetCount(), HistogramValueType.INTEGER, null)
            ));
        }

        return Response.ok(TwoColumnTableHistogramResponse.create(
                icmp.countUnreachableHostBreakdown(by, timeRange, filters, taps), true, values)).build();
    }

    private static String unreachableReason(int code) {
        return switch (code) {
            case 0 -> "Network unreachable";
            case 1 -> "Host unreachable";
            default -> "Code " + code;
        };
    }

    private static String serviceName(int port) {
        return switch (port) {
            case 53 -> "DNS";
            case 67, 68 -> "DHCP";
            case 69 -> "TFTP";
            case 111 -> "RPC";
            case 123 -> "NTP";
            case 137, 138 -> "NetBIOS";
            case 161, 162 -> "SNMP";
            case 177 -> "XDMCP";
            case 445 -> "SMB";
            case 500 -> "IKE";
            case 514 -> "syslog";
            case 520 -> "RIP";
            case 623 -> "IPMI";
            case 1434 -> "MSSQL";
            case 1701 -> "L2TP";
            case 1900 -> "SSDP";
            case 4500 -> "IPsec NAT-T";
            case 5060 -> "SIP";
            case 5353 -> "mDNS";
            default -> null;
        };
    }

    private static String protocolName(@Nullable Integer protocol) {
        if (protocol == null) {
            return null;
        }

        return switch (protocol) {
            case 1 -> "ICMP";
            case 6 -> "TCP";
            case 17 -> "UDP";
            default -> "Protocol " + protocol;
        };
    }

    private List<L4AddressResponse> redirectedDestinationResponses(List<String> destinations, UUID organizationId, UUID tenantId) {
        List<L4AddressResponse> result = Lists.newArrayList();
        for (String destination : destinations) {
            L4AddressResponse response = addressResponse(destination, null, organizationId, tenantId);
            if (response != null) {
                result.add(response);
            }
        }

        return result;
    }

    @Nullable
    private EthernetMacAddressResponse macResponse(@Nullable String mac, UUID organizationId, UUID tenantId) {
        if (mac == null) {
            return null;
        }

        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAssetByMac(mac, organizationId, tenantId);
        Optional<MacAddressContextEntry> context = nzyme.getContextService()
                .findMacAddressContext(mac, organizationId, tenantId);

        return EthernetMacAddressResponse.create(
                mac,
                nzyme.getOuiService().lookup(mac).orElse(null),
                asset.map(AssetEntry::uuid).orElse(null),
                asset.map(AssetEntry::isActive).orElse(null),
                RestHelpers.macContextEntryToResponse(context)
        );
    }

    @Nullable
    // Resolve the asset that owns an IP, independent of any frame MAC, so the address shows its own asset name.
    // A redirect's L2 source MAC and L3 source IP can belong to different assets; the sender IP is labelled by
    // the IP's asset while the separate Sender MAC column keeps the frame MAC's asset.
    private L4AddressResponse addressResponseByIp(@Nullable String address, UUID organizationId, UUID tenantId) {
        String mac = null;
        if (address != null && BeaconDetector.isValidAddress(address)) {
            try {
                InetAddress inet = InetAddress.getByName(address);
                List<AssetEntry> assets = nzyme.getAssetsManager().findAssetsByIpAddress(
                        inet, organizationId, tenantId, new DateTime().minusDays(7), 1, 0);
                if (!assets.isEmpty()) {
                    mac = assets.get(0).mac();
                }
            } catch (Exception e) {
                LOG.warn("Could not resolve asset for ICMP address [{}].", address, e);
            }
        }

        return addressResponse(address, mac, organizationId, tenantId);
    }

    private L4AddressResponse addressResponse(@Nullable String address, @Nullable String mac, UUID organizationId, UUID tenantId) {
        if (address == null || !BeaconDetector.isValidAddress(address)) {
            return null;
        }

        InetAddress inet;
        try {
            inet = InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            return null;
        }

        GeoData geo = null;
        Optional<GeoIpLookupResult> lookup = nzyme.getGeoIpService().lookup(inet);
        if (lookup.isPresent() && (lookup.get().asn() != null || lookup.get().geo() != null)) {
            GeoIpLookupResult r = lookup.get();
            geo = GeoData.create(
                    r.asn() == null ? null : r.asn().number(),
                    r.asn() == null ? null : r.asn().name(),
                    r.asn() == null ? null : r.asn().domain(),
                    r.geo() == null ? null : r.geo().city(),
                    r.geo() == null ? null : r.geo().countryCode(),
                    r.geo() == null ? null : r.geo().latitude(),
                    r.geo() == null ? null : r.geo().longitude()
            );
        }

        L4AddressAttributes attributes = L4AddressAttributes.create(
                inet.isSiteLocalAddress(), inet.isLoopbackAddress(), inet.isMulticastAddress()
        );

        try {
            return RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.TCP,
                    L4AddressData.create(mac, address, null, geo, attributes));
        } catch (Exception e) {
            LOG.warn("Could not build address data for ICMP address [{}].", address, e);
            return null;
        }
    }

}
