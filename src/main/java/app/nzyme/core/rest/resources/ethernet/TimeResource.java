package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.StringDoubleDoubleNumberAggregationResult;
import app.nzyme.core.database.generic.StringStringNumberAggregationResult;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.l4.tcp.db.TcpSessionEntry;
import app.nzyme.core.ethernet.time.ntp.NTP;
import app.nzyme.core.ethernet.time.ntp.db.NTPTransactionEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.authentication.mgmt.UsersListResponse;
import app.nzyme.core.rest.responses.ethernet.*;
import app.nzyme.core.rest.responses.ethernet.ntp.NTPTransactionDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.ntp.NTPTransactionsListResponse;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/time")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class TimeResource extends TapDataHandlingResource {

    private static final Logger LOG = LogManager.getLogger(TimeResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/ntp/transactions")
    public Response ntpTransactions(@Context SecurityContext sc,
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

        NTP.OrderColumn orderColumn = NTP.OrderColumn.INITIATED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = NTP.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().ntp().countAllTransactions(timeRange, filters, taps);

        List<NTPTransactionDetailsResponse> transactions = Lists.newArrayList();
        for (NTPTransactionEntry tx : nzyme.getEthernet().ntp()
                .findAllTransactions(timeRange, filters, orderColumn, orderDirection, limit, offset, taps)) {
            transactions.add(buildTransactionDetails(tx, organizationId, tenantId, taps));
        }

        return Response.ok(NTPTransactionsListResponse.create(total, transactions)).build();
    }

    @GET
    @Path("/ntp/transactions/show/{transaction_id}")
    public Response ntpTransaction(@Context SecurityContext sc,
                                   @PathParam("transaction_id") String transactionId,
                                   @QueryParam("organization_id") UUID organizationId,
                                   @QueryParam("tenant_id") UUID tenantId,
                                   @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<NTPTransactionEntry> transaction = nzyme.getEthernet().ntp().findTransaction(transactionId, taps);

        if (transaction.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(buildTransactionDetails(transaction.get(), organizationId, tenantId, taps)).build();
    }

    @GET
    @Path("/ntp/transactions/histogram")
    public Response ntpTransactionsHistogram(@Context SecurityContext sc,
                                             @QueryParam("time_range") @Valid String timeRangeParameter,
                                             @QueryParam("filters") String filtersParameter,
                                             @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Map<DateTime, Integer> response = Maps.newHashMap();

        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().ntp()
                .getTransactionCountHistogram(timeRange, bucketing, filters, taps)) {
            response.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(response).build();
    }

    @GET
    @Path("/ntp/clients/requestresponseratio/histogram")
    public Response clientRequestResponseRatioHistogram(@Context SecurityContext sc,
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

        long total = nzyme.getEthernet().ntp()
                .countClientRequestResponseRatioHistogramClients(timeRange, filters, taps);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (StringDoubleDoubleNumberAggregationResult c : nzyme.getEthernet().ntp()
                .getClientRequestResponseRatioHistogram(timeRange, filters, limit, offset, taps)) {

            L4AddressResponse l4AddressResponse = L4AddressResponse.create(
                    L4AddressTypeResponse.UDP,
                    null,
                    c.key(),
                    null,
                    null,
                    null,
                    L4AddressContextResponse.create()
            );

            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            l4AddressResponse,
                            HistogramValueType.L4_ADDRESS,
                            null
                    ),
                    HistogramValueStructureResponse.create(c.value1(), HistogramValueType.DOUBLE_DECIMAL2, null),
                    HistogramValueStructureResponse.create(c.value2(), HistogramValueType.INTEGER, null),
                    c.key()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(total, true, values)).build();
    }

    @GET
    @Path("/ntp/servers/top/histogram")
    public Response topServersHistogram(@Context SecurityContext sc,
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

        long total = nzyme.getEthernet().ntp()
                .countTopServersHistogramServers(timeRange, filters, taps);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (StringStringNumberAggregationResult s : nzyme.getEthernet().ntp()
                .getTopServersHistogram(timeRange, filters, limit, offset, taps)) {

            Optional<MacAddressContextEntry> serverContext = nzyme.getContextService().findMacAddressContext(
                    s.value1(),
                    organizationId,
                    tenantId
            );

            Optional<AssetEntry> serverAsset = nzyme.getAssetsManager()
                    .findAssetByMac(s.value1(), organizationId, tenantId);

            // Pull the most recent address data of this asset.
            Optional<L4AddressData> addressData = nzyme.getEthernet().l4()
                    .findMostRecentDestinationAddressData(taps, s.key());

            EthernetMacAddressResponse value1;
            L4AddressResponse l4AddressResponse;
            if (addressData.isPresent() && s.value1() != null) {
                if (addressData.get().attributes() != null && addressData.get().attributes().isSiteLocal()) {
                    value1 = EthernetMacAddressResponse.create(
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
                    value1 = null;
                }
                l4AddressResponse = RestHelpers.L4AddressDataToResponse(
                        nzyme, organizationId, tenantId, L4Type.NONE, addressData.get()
                );
            } else {
                value1 = null;
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
                            value1
                    ),
                    HistogramValueStructureResponse.create(s.value2(), HistogramValueType.INTEGER, null),
                    s.key()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(total, true, values)).build();
    }

    private NTPTransactionDetailsResponse buildTransactionDetails(NTPTransactionEntry tx,
                                                                  UUID organizationId,
                                                                  UUID tenantId,
                                                                  List<UUID> taps) {
        L4AddressResponse client;
        L4AddressResponse server;
        Optional<TcpSessionEntry> udpConversation = nzyme.getEthernet().tcp()
                .findSessionBySessionKey(tx.transactionKey(), tx.timestampClientTapReceive(), taps);
        if (udpConversation.isPresent()) {
            client = RestHelpers.L4AddressDataToResponse(
                    nzyme, organizationId, tenantId, L4Type.UDP, udpConversation.get().source()
            );
            server = RestHelpers.L4AddressDataToResponse(
                    nzyme, organizationId, tenantId, L4Type.UDP, udpConversation.get().destination()
            );
        } else {
            // No underlying UDP conversation found. Fall back.
            client = RestHelpers.L4AddressDataToResponse(
                    nzyme, organizationId, tenantId, L4Type.UDP,
                    L4AddressData.create(tx.clientMac(), tx.clientAddress(), tx.clientPort(), null, null)
            );

            server = RestHelpers.L4AddressDataToResponse(
                    nzyme, organizationId, tenantId, L4Type.UDP,
                    L4AddressData.create(tx.serverMac(), tx.serverAddress(), tx.serverPort(), null, null)
            );
        }

        return NTPTransactionDetailsResponse.create(
                tx.transactionKey(),
                tx.complete(),
                tx.notes(),
                client,
                server,
                tx.requestSize(),
                tx.responseSize(),
                tx.timestampClientTransmit(),
                tx.timestampServerReceive(),
                tx.timestampServerTransmit(),
                tx.timestampClientTapReceive(),
                tx.timestampServerTapReceive(),
                tx.serverVersion(),
                tx.clientVersion(),
                tx.serverMode(),
                tx.clientMode(),
                tx.stratum(),
                tx.leapIndicator(),
                tx.precision(),
                tx.pollInterval(),
                tx.rootDelaySeconds(),
                tx.rootDispersionSeconds(),
                tx.delaySeconds(),
                tx.offsetSeconds(),
                tx.rttSeconds(),
                tx.serverProcessingSeconds(),
                tx.referenceId(),
                tx.createdAt()
        );
    }

}
