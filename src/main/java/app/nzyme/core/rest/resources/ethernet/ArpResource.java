package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.AssetPairNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnHistogramOrderColumn;
import app.nzyme.core.ethernet.arp.ARP;
import app.nzyme.core.ethernet.arp.ARPExplainer;
import app.nzyme.core.ethernet.arp.db.ARPStatisticsBucket;
import app.nzyme.core.ethernet.arp.db.ArpPacketEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.*;
import app.nzyme.core.rest.responses.ethernet.arp.ArpPacketDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.arp.ArpPacketsListResponse;
import app.nzyme.core.rest.responses.ethernet.arp.ArpStatisticsBucketResponse;
import app.nzyme.core.rest.responses.shared.*;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.rest.RestHelpers.macContextEntryToResponse;
import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/arp")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "ARP", description = "ARP requests and replies that taps recorded on the Ethernet network, with "
        + "statistics and histograms of the most active requesters and responders. Nzyme also uses ARP traffic to "
        + "enrich the asset inventory.")
public class ArpResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/packets")
    @Operation(operationId = "findArpPackets", summary = "List ARP packets",
            description = "Returns all ARP packets in the time range, newest first by default. Each packet includes "
                    + "the Ethernet and ARP addresses enriched with asset, OUI and context information, plus a plain "
                    + "language explanation of what the packet does. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Packets found.",
            content = @Content(schema = @Schema(implementation = ArpPacketsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response packets(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Sorting column. Defaults to the packet timestamp.") @QueryParam("order_column") @Nullable String orderColumnParam,
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

        ARP.OrderColumn orderColumn = ARP.OrderColumn.TIMESTAMP;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ARP.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().arp().countAllPackets(timeRange, filters, taps);

        List<ArpPacketDetailsResponse> packets = Lists.newArrayList();
        for (ArpPacketEntry packet : nzyme.getEthernet().arp()
                .findAllPackets(timeRange, filters, limit, offset, orderColumn, orderDirection, taps)) {

           packets.add(buildDetailsResponse(packet, organizationId, tenantId));
        }

        return Response.ok(ArpPacketsListResponse.create(total, packets)).build();
    }

    @GET
    @Path("/statistics")
    @Operation(operationId = "findArpStatistics", summary = "Get ARP statistics",
            description = "Returns packet counts per time bucket, broken down into requests, replies, gratuitous "
                    + "requests and gratuitous replies, together with the request to reply ratio. The response is an "
                    + "object that maps each bucket timestamp to its values. The bucket size is chosen automatically "
                    + "from the time range.")
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

        Map<DateTime, ArpStatisticsBucketResponse> statistics = Maps.newHashMap();
        for (ARPStatisticsBucket s : nzyme.getEthernet().arp().getStatistics(timeRange, bucketing, filters, taps)) {
            statistics.put(s.bucket(), ArpStatisticsBucketResponse.create(
                    s.totalCount(),
                    s.requestCount(),
                    s.replyCount(),
                    s.requestToReplyRatio(),
                    s.gratuitousRequestCount(),
                    s.gratuitousReplyCount()
            ));
        }

        return Response.ok(statistics).build();
    }

    @GET
    @Path("/histograms/requesters/pairs")
    @Operation(operationId = "findArpRequesterPairs", summary = "List top ARP requester pairs",
            description = "Returns sender and target MAC address pairs of ARP requests with the number of requests "
                    + "each pair exchanged, most active pair first by default. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Pairs found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response requesterPairs(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                   @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                   @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                   @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                   @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                   @Parameter(description = "Sorting column. Defaults to the request count.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                   @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                   @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ThreeColumnHistogramOrderColumn orderColumn = ThreeColumnHistogramOrderColumn.VALUE3;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ThreeColumnHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long topRequesterPairsCount = nzyme.getEthernet().arp()
                .countPairs("Request", timeRange,  filters, taps);

        List<ThreeColumnTableHistogramValueResponse> topRequesterPairs = buildPairs(
                "Request", organizationId, tenantId, timeRange, filters, limit, offset, orderColumn, orderDirection, taps
        );

        return Response.ok(
                ThreeColumnTableHistogramResponse.create(topRequesterPairsCount, true, topRequesterPairs)
        ).build();
    }

    @GET
    @Path("/histograms/responders/pairs")
    @Operation(operationId = "findArpResponderPairs", summary = "List top ARP responder pairs",
            description = "Returns sender and target MAC address pairs of ARP replies with the number of replies "
                    + "each pair exchanged, most active pair first by default. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Pairs found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response responderPairs(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                   @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                   @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                   @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                   @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                   @Parameter(description = "Sorting column. Defaults to the reply count.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                   @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                   @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ThreeColumnHistogramOrderColumn orderColumn = ThreeColumnHistogramOrderColumn.VALUE3;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ThreeColumnHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }


        long topRequesterPairsCount = nzyme.getEthernet().arp()
                .countPairs("Reply", timeRange,  filters, taps);

        List<ThreeColumnTableHistogramValueResponse> topRequesterPairs = buildPairs(
                "Reply", organizationId, tenantId, timeRange, filters, limit, offset, orderColumn, orderDirection, taps
        );

        return Response.ok(ThreeColumnTableHistogramResponse.create(
                topRequesterPairsCount, true, topRequesterPairs)
        ).build();
    }

    private ArpPacketDetailsResponse buildDetailsResponse(ArpPacketEntry packet, UUID organizationId, UUID tenantId) {
        Optional<AssetEntry> ethernetSourceAsset = nzyme.getAssetsManager()
                .findAssetByMac(packet.ethernetSourceMac(), organizationId, tenantId);
        Optional<MacAddressContextEntry> ethernetSourceMacContext = nzyme.getContextService()
                .findMacAddressContext(packet.ethernetSourceMac(), organizationId, tenantId);
        Optional<AssetEntry> ethernetDestinationAsset = nzyme.getAssetsManager()
                .findAssetByMac(packet.ethernetDestinationMac(), organizationId, tenantId);
        Optional<MacAddressContextEntry> ethernetDestinationMacContext = nzyme.getContextService()
                .findMacAddressContext(packet.ethernetDestinationMac(), organizationId, tenantId);

        return ArpPacketDetailsResponse.create(
                packet.tapUUID(),
                EthernetMacAddressResponse.create(
                        packet.ethernetSourceMac(),
                        nzyme.getOuiService().lookup(packet.ethernetSourceMac()).orElse(null),
                        ethernetSourceAsset.map(AssetEntry::uuid).orElse(null),
                        ethernetSourceAsset.map(AssetEntry::isActive).orElse(null),
                        macContextEntryToResponse(ethernetSourceMacContext)
                ),
                EthernetMacAddressResponse.create(
                        packet.ethernetDestinationMac(),
                        nzyme.getOuiService().lookup(packet.ethernetDestinationMac()).orElse(null),
                        ethernetDestinationAsset.map(AssetEntry::uuid).orElse(null),
                        ethernetDestinationAsset.map(AssetEntry::isActive).orElse(null),
                        macContextEntryToResponse(ethernetDestinationMacContext)
                ),
                packet.hardwareType(),
                packet.protocolType(),
                packet.operation(),
                RestHelpers.internalAddressDataToResponse(
                        nzyme, packet.arpSenderMac(), packet.arpSenderAddress(), organizationId, tenantId
                ),
                RestHelpers.internalAddressDataToResponse(
                        nzyme, packet.arpTargetMac(), packet.arpTargetAddress(), organizationId, tenantId
                ),
                packet.size(),
                packet.timestamp(),
                ARPExplainer.explain(
                        packet.ethernetDestinationMac(),
                        packet.operation(),
                        packet.arpSenderMac(),
                        packet.arpSenderAddress(),
                        packet.arpTargetMac(),
                        packet.arpTargetAddress()
                )
        );
    }

    private List<ThreeColumnTableHistogramValueResponse> buildPairs(String operation,
                                                                    UUID organizationId,
                                                                    UUID tenantId,
                                                                    TimeRange timeRange,
                                                                    Filters filters,
                                                                    int limit,
                                                                    int offset,
                                                                    ThreeColumnHistogramOrderColumn orderColumn,
                                                                    OrderDirection orderDirection,
                                                                    List<UUID> taps) {
        List<ThreeColumnTableHistogramValueResponse> pairs = Lists.newArrayList();

        for (AssetPairNumberAggregationResult pair : nzyme.getEthernet().arp()
                .getPairs(operation, timeRange, filters, limit, offset, orderColumn, orderDirection, taps)) {
            Optional<MacAddressContextEntry> senderMacContext = nzyme.getContextService().findMacAddressContext(
                    pair.mac1(),
                    organizationId,
                    tenantId
            );
            Optional<MacAddressContextEntry> targetMacContext = nzyme.getContextService().findMacAddressContext(
                    pair.mac2(),
                    organizationId,
                    tenantId
            );

            Optional<AssetEntry> senderAsset = nzyme.getAssetsManager()
                    .findAssetByMac(pair.mac1(), organizationId, tenantId);
            Optional<AssetEntry> targetAsset = nzyme.getAssetsManager()
                    .findAssetByMac(pair.mac2(), organizationId, tenantId);

            pairs.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            pair.mac1(),
                            HistogramValueType.ETHERNET_MAC,
                            EthernetMacAddressResponse.create(
                                    pair.mac1(),
                                    nzyme.getOuiService().lookup(pair.mac1()).orElse(null),
                                    senderAsset.map(AssetEntry::uuid).orElse(null),
                                    senderAsset.map(AssetEntry::isActive).orElse(null),
                                    macContextEntryToResponse(senderMacContext)
                            )
                    ),
                    HistogramValueStructureResponse.create(
                            pair.mac2(),
                            HistogramValueType.ETHERNET_MAC,
                            EthernetMacAddressResponse.create(
                                    pair.mac2(),
                                    nzyme.getOuiService().lookup(pair.mac2()).orElse(null),
                                    targetAsset.map(AssetEntry::uuid).orElse(null),
                                    targetAsset.map(AssetEntry::isActive).orElse(null),
                                    macContextEntryToResponse(targetMacContext)
                            )
                    ),
                    HistogramValueStructureResponse.create(pair.value(), HistogramValueType.INTEGER, null),
                    pair.mac1() + " -> " + pair.mac2()
            ));
        }

        return pairs;
    }

}
