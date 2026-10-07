package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.dhcp.DHCP;
import app.nzyme.core.ethernet.dhcp.db.DHCPStatisticsBucket;
import app.nzyme.core.ethernet.dhcp.db.DHCPTransactionEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressContextResponse;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.dhcp.DHCPStatisticsBucketResponse;
import app.nzyme.core.rest.responses.ethernet.dhcp.DHCPTimelineStepResponse;
import app.nzyme.core.rest.responses.ethernet.dhcp.DHCPTransactionDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.dhcp.DHCPTransactionsListResponse;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
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
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.*;

import static app.nzyme.core.rest.RestHelpers.macContextEntryToResponse;
import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/dhcp")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "DHCP", description = "DHCP transactions that taps recorded on the Ethernet network, including "
        + "client and server addresses, offered and requested IP addresses, DHCP fingerprints and timelines. Nzyme "
        + "also uses DHCP traffic to enrich the asset inventory, and its fingerprints help to detect spoofing.")
public class DHCPResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/transactions")
    @Operation(operationId = "findDhcpTransactions", summary = "List DHCP transactions",
            description = "Returns all DHCP transactions in the time range, newest first by default. Each "
                    + "transaction bundles the packets of one DHCP exchange and carries the client and server MAC "
                    + "addresses, offered and requested IP addresses, DHCP options, fingerprints and a timeline of "
                    + "all steps. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Transactions found.",
            content = @Content(schema = @Schema(implementation = DHCPTransactionsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response transactions(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                 @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                 @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                 @Parameter(description = "Sorting column. Defaults to the time the transaction was initiated.") @QueryParam("order_column") @Nullable String orderColumnParam,
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

        DHCP.OrderColumn orderColumn = DHCP.OrderColumn.INITIATED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = DHCP.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().dhcp().countAllTransactions(timeRange, filters, taps);
        List<DHCPTransactionDetailsResponse> txs = Lists.newArrayList();
        for (DHCPTransactionEntry tx : nzyme.getEthernet().dhcp()
                .findAllTransactions(timeRange, limit, offset, orderColumn, orderDirection, filters, taps)) {
            txs.add(buildTransactionResponse(tx, organizationId, tenantId));
        }

        return Response.ok(DHCPTransactionsListResponse.create(total, txs)).build();
    }

    @GET
    @Path("/transactions/statistics")
    @Operation(operationId = "findDhcpTransactionStatistics", summary = "Get DHCP transaction statistics",
            description = "Returns total, successful and failed transaction counts per time bucket. The response is "
                    + "an object that maps each bucket timestamp to its counts. The bucket size is chosen "
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

        Map<DateTime, DHCPStatisticsBucketResponse> statistics = Maps.newHashMap();
        for (DHCPStatisticsBucket s : nzyme.getEthernet().dhcp().getStatistics(timeRange, bucketing, filters, taps)) {
            statistics.put(s.bucket(), DHCPStatisticsBucketResponse.create(
                    s.totalTransactionCount(), s.successfulTransactionCount(), s.failedTransactionCount())
            );
        }

        return Response.ok(statistics).build();
    }

    @GET
    @Path("/transactions/show/{transaction_id}")
    @Operation(operationId = "findDhcpTransaction", summary = "Get a DHCP transaction",
            description = "DHCP transaction IDs are not unique over time, so the transaction time is required to "
                    + "identify a single transaction. Pass the time as an ISO 8601 timestamp.")
    @ApiResponse(responseCode = "200", description = "Transaction found.",
            content = @Content(schema = @Schema(implementation = DHCPTransactionDetailsResponse.class)))
    @ApiResponse(responseCode = "400", description = "The transaction time is not a valid timestamp.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Transaction not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response transaction(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                @Parameter(description = "DHCP transaction ID.") @PathParam("transaction_id") long transactionId,
                                @Parameter(description = "Time of the transaction as an ISO 8601 timestamp.") @QueryParam("transaction_time") String transactionTimeP,
                                @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DateTime transactionTime;
        try {
            transactionTime = DateTime.parse(transactionTimeP);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Optional<DHCPTransactionEntry> txe = nzyme.getEthernet().dhcp()
                .findTransaction(transactionId, transactionTime, taps);

        if (txe.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(buildTransactionResponse(txe.get(), organizationId, tenantId)).build();
    }

    private DHCPTransactionDetailsResponse buildTransactionResponse(DHCPTransactionEntry tx,
                                                                    UUID organizationId,
                                                                    UUID tenantId) {
        Long duration = null;
        if (tx.isComplete()) {
            duration = new Duration(tx.firstPacket(), tx.latestPacket()).getMillis();
        }

        Optional<MacAddressContextEntry> clientMacContext = nzyme.getContextService().findMacAddressContext(
                tx.clientMac(),
                organizationId,
                tenantId
        );

        // We change the structure of the timestamps to be easier to use in the client.
        List<DHCPTimelineStepResponse> unsortedTimeline = Lists.newArrayList();
        for (Map.Entry<String, List<String>> step : tx.timestamps().entrySet()) {
            for (String timestamp : step.getValue()) {
                unsortedTimeline.add(DHCPTimelineStepResponse.create(step.getKey(), timestamp));
            }
        }

        List<DHCPTimelineStepResponse> sortedTimeline = tx.timestamps().entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .map(ts -> DHCPTimelineStepResponse.create(e.getKey(), ts)))
                .sorted(Comparator.comparing(DHCPTimelineStepResponse::timestamp))
                .toList();

        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAssetByMac(tx.clientMac(), organizationId, tenantId);

        return DHCPTransactionDetailsResponse.create(
                tx.transactionId(),
                tx.transactionType(),
                EthernetMacAddressResponse.create(
                        tx.clientMac(),
                        nzyme.getOuiService().lookup(tx.clientMac()).orElse(null),
                        asset.map(AssetEntry::uuid).orElse(null),
                        asset.map(AssetEntry::isActive).orElse(null),
                        macContextEntryToResponse(clientMacContext)
                ),
                tx.additionalClientMacs(),
                buildServerMacResponse(tx, organizationId, tenantId),
                tx.additionalServerMacs(),
                tx.offeredIpAddresses(),
                RestHelpers.internalAddressDataToResponse(
                        nzyme, null, tx.requestedIpAddress(), organizationId, tenantId
                ),
                tx.options(),
                tx.additionalOptions(),
                tx.fingerprint(),
                tx.additionalFingerprints(),
                tx.vendorClass(),
                tx.additionalVendorClasses(),
                sortedTimeline,
                tx.firstPacket(),
                tx.latestPacket(),
                tx.notes(),
                tx.isSuccessful(),
                tx.isComplete(),
                duration
        );
    }

    @Nullable
    private EthernetMacAddressResponse buildServerMacResponse(DHCPTransactionEntry tx,
                                                              UUID organizationId,
                                                              UUID tenantId) {
        if (tx.serverMac() != null) {
            Optional<MacAddressContextEntry> serverMacContext = nzyme.getContextService().findMacAddressContext(
                    tx.serverMac(),
                    organizationId,
                    tenantId
            );

            Optional<AssetEntry> asset = nzyme.getAssetsManager()
                    .findAssetByMac(tx.serverMac(), organizationId, tenantId);

            return EthernetMacAddressResponse.create(
                    tx.serverMac(),
                    nzyme.getOuiService().lookup(tx.serverMac()).orElse(null),
                    asset.map(AssetEntry::uuid).orElse(null),
                    asset.map(AssetEntry::isActive).orElse(null),
                    macContextEntryToResponse(serverMacContext)
            );
        } else {
            return null;
        }
    }



}
