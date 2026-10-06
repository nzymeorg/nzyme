package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.NumberNumberNumberAggregationResult;
import app.nzyme.core.database.generic.StringNumberNumberAggregationResult;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.l4.L4;
import app.nzyme.core.ethernet.l4.db.L4Numbers;
import app.nzyme.core.ethernet.l4.db.L4Session;
import app.nzyme.core.ethernet.l4.db.L4StatisticsBucket;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.*;
import app.nzyme.core.rest.responses.ethernet.l4.L4NumbersResponse;
import app.nzyme.core.rest.responses.ethernet.l4.L4SessionDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.l4.L4SessionsListResponse;
import app.nzyme.core.rest.responses.ethernet.l4.L4StatisticsBucketResponse;
import app.nzyme.core.rest.responses.shared.*;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.net.InetAddresses;
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

import static app.nzyme.core.rest.RestHelpers.macContextEntryToResponse;
import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/l4")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class L4Resource extends TapDataHandlingResource  {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/sessions")
    public Response allSessions(@Context SecurityContext sc,
                                @QueryParam("organization_id") UUID organizationId,
                                @QueryParam("tenant_id") UUID tenantId,
                                @QueryParam("time_range") String timeRangeParameter,
                                @QueryParam("filters") String filtersParameter,
                                @QueryParam("limit") int limit,
                                @QueryParam("offset") int offset,
                                @QueryParam("order_column") @Nullable String orderColumnParam,
                                @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        L4.OrderColumn orderColumn = L4.OrderColumn.MOST_RECENT_SEGMENT_TIME;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = L4.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().l4().countAllSessions(timeRange, filters, taps);

        List<L4SessionDetailsResponse> sessions = Lists.newArrayList();
        for (L4Session session : nzyme.getEthernet().l4()
                .findAllSessions(timeRange, filters, limit, offset, orderColumn, orderDirection, taps)) {
            sessions.add(buildSessionDetailsResponse(organizationId, tenantId, session));
        }

        return Response.ok(L4SessionsListResponse.create(total, sessions)).build();
    }

    @GET
    @Path("/sessions/show/{type}/{session_key}/{start_time}")
    public Response session(@Context SecurityContext sc,
                            @PathParam("type") String typeP,
                            @PathParam("session_key") String sessionKey,
                            @PathParam("start_time") String startTimeP,
                            @QueryParam("organization_id") UUID organizationId,
                            @QueryParam("tenant_id") UUID tenantId,
                            @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DateTime startTime;
        L4Type type;
        try {
            startTime = DateTime.parse(startTimeP);
            type = L4Type.valueOf(typeP.toUpperCase());
        } catch(IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Optional<L4Session> session = nzyme.getEthernet().l4().findSession(type, startTime, sessionKey, taps);

        if (session.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(buildSessionDetailsResponse(organizationId, tenantId, session.get())).build();
    }

    @GET
    @Path("/sessions/statistics")
    public Response sessionsStatistics(@Context SecurityContext sc,
                                       @QueryParam("time_range") @Valid String timeRangeParameter,
                                       @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        Map<DateTime, L4StatisticsBucketResponse> statistics = Maps.newHashMap();
        for (L4StatisticsBucket b : nzyme.getEthernet().l4().getStatistics(timeRange, bucketing, taps)) {
            statistics.put(b.bucket(), L4StatisticsBucketResponse.create(
                    b.bytesRxTcp(),
                    b.bytesTxTcp(),
                    b.bytesRxInternalTcp(),
                    b.bytesTxInternalTcp(),
                    b.bytesRxUdp(),
                    b.bytesTxUdp(),
                    b.bytesRxInternalUdp(),
                    b.bytesTxInternalUdp(),
                    b.segmentsTcp(),
                    b.datagramsUdp(),
                    b.sessionsTcp(),
                    b.sessionsUdp(),
                    b.sessionsInternalTcp(),
                    b.sessionsInternalUdp()
            ));
        }

        L4Numbers totals = nzyme.getEthernet().l4().getTotals(timeRange, taps);
        L4NumbersResponse numbers = L4NumbersResponse.create(
                totals.bytesTcp(),
                totals.bytesInternalTcp(),
                totals.bytesUdp(),
                totals.bytesInternalUdp(),
                totals.segmentsTcp(),
                totals.datagramsUdp()
        );

        Map<String, Object> response = Maps.newHashMap();
        response.put("statistics", statistics);
        response.put("numbers", numbers);

        return Response.ok(response).build();
    }

    @GET
    @Path("/sessions/histograms/sources/traffic/macs/top")
    public Response topTrafficSourceMacs(@Context SecurityContext sc,
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

        L4.AggregationPage<StringNumberNumberAggregationResult> page = nzyme.getEthernet().l4()
                .getTopTrafficSourceMacs(timeRange, filters, limit, offset, taps);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (StringNumberNumberAggregationResult source : page.results()) {
            Optional<MacAddressContextEntry> sourceContext = nzyme.getContextService().findMacAddressContext(
                    source.key(),
                    organizationId,
                    tenantId
            );

            Optional<AssetEntry> sourceAsset = nzyme.getAssetsManager()
                    .findAssetByMac(source.key(), organizationId, tenantId);

            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(source.key(),
                            HistogramValueType.ETHERNET_MAC,
                            EthernetMacAddressResponse.create(
                                    source.key(),
                                    nzyme.getOuiService().lookup(source.key()).orElse(null),
                                    sourceAsset.map(AssetEntry::uuid).orElse(null),
                                    sourceAsset.map(AssetEntry::isActive).orElse(null),
                                    macContextEntryToResponse(sourceContext)
                            )
                    ),
                    HistogramValueStructureResponse.create(source.value1(), HistogramValueType.BYTES, null),
                    HistogramValueStructureResponse.create(source.value2(), HistogramValueType.BYTES, null),
                    source.key()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(page.total(), false, values)).build();
    }

    @GET
    @Path("/sessions/histograms/sources/traffic/addresses/top")
    public Response topTrafficSourceAddresses(@Context SecurityContext sc,
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

        L4.AggregationPage<L4AddressDataAddressNumberNumberAggregationResult> page = nzyme.getEthernet().l4()
                .getTopTrafficSourceAddresses(timeRange, filters, limit, offset, taps);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (L4AddressDataAddressNumberNumberAggregationResult source : page.results()) {

            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(
                                    nzyme, organizationId, tenantId, L4Type.NONE, source.key()
                            ),
                            HistogramValueType.L4_ADDRESS,
                            null
                    ),
                    HistogramValueStructureResponse.create(source.value1(), HistogramValueType.BYTES, null),
                    HistogramValueStructureResponse.create(source.value2(), HistogramValueType.BYTES, null),
                    source.key().address()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(page.total(), false, values)).build();
    }


    @GET
    @Path("/sessions/histograms/destinations/traffic/macs/top")
    public Response topTrafficDestinationMacs(@Context SecurityContext sc,
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

        L4.AggregationPage<StringNumberNumberAggregationResult> page = nzyme.getEthernet().l4()
                .getTopTrafficDestinationMacs(timeRange, filters, limit, offset, taps);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (StringNumberNumberAggregationResult dest : page.results()) {
            Optional<MacAddressContextEntry> destinationContext = nzyme.getContextService().findMacAddressContext(
                    dest.key(),
                    organizationId,
                    tenantId
            );

            Optional<AssetEntry> destinationAsset = nzyme.getAssetsManager()
                    .findAssetByMac(dest.key(), organizationId, tenantId);

            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(dest.key(),
                            HistogramValueType.ETHERNET_MAC,
                            EthernetMacAddressResponse.create(
                                    dest.key(),
                                    nzyme.getOuiService().lookup(dest.key()).orElse(null),
                                    destinationAsset.map(AssetEntry::uuid).orElse(null),
                                    destinationAsset.map(AssetEntry::isActive).orElse(null),
                                    macContextEntryToResponse(destinationContext)
                            )
                    ),
                    HistogramValueStructureResponse.create(dest.value1(), HistogramValueType.BYTES, null),
                    HistogramValueStructureResponse.create(dest.value2(), HistogramValueType.BYTES, null),
                    dest.key()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(page.total(), false, values)).build();
    }

    @GET
    @Path("/sessions/histograms/destinations/traffic/addresses/top")
    public Response topTrafficDestinationAddresses(@Context SecurityContext sc,
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

        L4.AggregationPage<L4AddressDataAddressNumberNumberAggregationResult> page = nzyme.getEthernet().l4()
                .getTopTrafficDestinationAddresses(timeRange, filters, limit, offset, taps);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (L4AddressDataAddressNumberNumberAggregationResult dest : page.results()) {

            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(
                                    nzyme, organizationId, tenantId, L4Type.NONE, dest.key()
                            ),
                            HistogramValueType.L4_ADDRESS,
                            null
                    ),
                    HistogramValueStructureResponse.create(dest.value1(), HistogramValueType.BYTES, null),
                    HistogramValueStructureResponse.create(dest.value2(), HistogramValueType.BYTES, null),
                    dest.key().address()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(page.total(), false, values)).build();
    }

    @GET
    @Path("/sessions/histograms/ports/destination/all/top")
    public Response topDestinationPorts(@Context SecurityContext sc,
                                        @QueryParam("time_range") String timeRangeParameter,
                                        @QueryParam("filters") String filtersParameter,
                                        @QueryParam("limit") int limit,
                                        @QueryParam("offset") int offset,
                                        @QueryParam("taps") String tapIds) {

        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        L4.AggregationPage<NumberNumberNumberAggregationResult> page = nzyme.getEthernet().l4()
                .getTopDestinationPorts(timeRange, filters, limit, offset, taps);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (NumberNumberNumberAggregationResult port : page.results()) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(port.key(), HistogramValueType.L4_PORT, null),
                    HistogramValueStructureResponse.create(port.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(port.value2(), HistogramValueType.BYTES, null),
                    String.valueOf(port.key())
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(page.total(), false, values)).build();
    }

    @GET
    @Path("/sessions/histograms/ports/destination/non-ephemeral/bottom")
    public Response leastCommonNonEphemeralDestinationPorts(@Context SecurityContext sc,
                                                            @QueryParam("time_range") String timeRangeParameter,
                                                            @QueryParam("filters") String filtersParameter,
                                                            @QueryParam("limit") int limit,
                                                            @QueryParam("offset") int offset,
                                                            @QueryParam("taps") String tapIds) {

        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        L4.AggregationPage<NumberNumberNumberAggregationResult> page = nzyme.getEthernet().l4()
                .getLeastCommonNonEphemeralDestinationPorts(timeRange, filters, limit, offset, taps);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (NumberNumberNumberAggregationResult port : page.results()) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(port.key(), HistogramValueType.L4_PORT, null),
                    HistogramValueStructureResponse.create(port.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(port.value2(), HistogramValueType.BYTES, null),
                    String.valueOf(port.key())
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(page.total(), false, values)).build();
    }

    private L4SessionDetailsResponse buildSessionDetailsResponse(UUID organizationId, UUID tenantId, L4Session session) {
        return L4SessionDetailsResponse.create(
                session.sessionKey(),
                L4AddressTypeResponse.valueOf(session.l4Type().toString()),
                RestHelpers.L4AddressDataToResponse(
                        nzyme, organizationId, tenantId, session.l4Type(), session.source()
                ),
                RestHelpers.L4AddressDataToResponse(
                        nzyme, organizationId, tenantId, session.l4Type(), session.destination()
                ),
                session.bytesRxCount() + session.bytesTxCount(),
                session.bytesRxCount(),
                session.bytesTxCount(),
                session.segmentsCount(),
                session.startTime(),
                session.endTime(),
                session.mostRecentSegmentTime(),
                session.durationMs(),
                session.state(),
                session.tags(),
                session.fingerprint()
        );
    }

}