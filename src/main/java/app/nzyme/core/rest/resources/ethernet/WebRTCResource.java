package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.AddressPairNumberAggregationResult;
import app.nzyme.core.database.generic.AssetPairNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnHistogramOrderColumn;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.nat.db.STUNNegotiationEntry;
import app.nzyme.core.ethernet.webrtc.WebRTC;
import app.nzyme.core.ethernet.webrtc.db.WebRTCSessionEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.misc.NATHelper;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressContextResponse;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.nat.NATSTUNNegotiationDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.webrtc.WebRTCSessionDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.webrtc.WebRTCSessionsListResponse;
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
import org.joda.time.DateTime;

import java.util.*;
import java.util.function.Function;

import static app.nzyme.core.rest.misc.WebRTCHelper.buildWebRTCSessionDetailsResponse;
import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/webrtc")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class WebRTCResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/sessions")
    public Response allSessions(@Context SecurityContext sc,
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

        WebRTC.SessionOrderColumn orderColumn = WebRTC.SessionOrderColumn.INITIATED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = WebRTC.SessionOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().webRtc().countAllSessions(timeRange, filters, taps);

        List<WebRTCSessionDetailsResponse> sessions = Lists.newArrayList();
        for (WebRTCSessionEntry session : nzyme.getEthernet().webRtc()
                .findAllSessions(timeRange, filters, orderColumn, orderDirection, limit, offset, taps)) {

            sessions.add(buildWebRTCSessionDetailsResponse(session, null, null, nzyme, organizationId, tenantId));
        }

        return Response.ok(WebRTCSessionsListResponse.create(total, sessions)).build();
    }


    @GET
    @Path("/sessions/show/{negotiation_key_sha256}")
    public Response oneSession(@Context SecurityContext sc,
                               @PathParam("negotiation_key_sha256") String negotiationKeySha256,
                               @QueryParam("organization_id") UUID organizationId,
                               @QueryParam("tenant_id") UUID tenantId,
                               @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<WebRTCSessionEntry> session = nzyme.getEthernet().webRtc().findOneSession(negotiationKeySha256, taps);

        if (session.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<WebRTCSessionDetailsResponse> subSessions = nzyme.getEthernet()
                .webRtc()
                .findSubSessionsOfSession(session.get().negotiationKeySha256(), taps)
                .stream()
                .map(webRTCSessionEntry ->
                        buildWebRTCSessionDetailsResponse(webRTCSessionEntry, null, null, nzyme, organizationId, tenantId)
                ).toList();

        // Add a STUN negotiation if there is one.
        Optional<STUNNegotiationEntry> stun = nzyme.getEthernet().nat()
                .findOneNegotiation(session.get().negotiationKeySha256(), taps);

        NATSTUNNegotiationDetailsResponse stunResponse = null;
        if (stun.isPresent()) {
            stunResponse = NATHelper.buildNegotiationDetailsResponse(
                    stun.get(), Collections.emptyList(), null, nzyme, organizationId, tenantId
            );
        }

        return Response.ok(buildWebRTCSessionDetailsResponse(
                session.get(), subSessions, stunResponse, nzyme, organizationId, tenantId
        )).build();
    }

    @GET
    @Path("/sessions/active/histogram")
    public Response activeSessionsHistogram(@Context SecurityContext sc,
                                            @QueryParam("time_range") @Valid String timeRangeParameter,
                                            @QueryParam("filters") String filtersParameter,
                                            @QueryParam("taps") String taps) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Map<DateTime, Integer> buckets = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().webRtc()
                .getActiveSessionsHistogram(timeRange, bucketing, filters, tapUUIDs)) {
            buckets.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(NumericHistogramResponse.create(buckets, bucketing.bucketSizeMs())).build();
    }

    @GET
    @Path("/sessions/peers/addresses/top/histogram")
    public Response topPeerAddressPairHistogram(@Context SecurityContext sc,
                                                @QueryParam("organization_id") UUID organizationId,
                                                @QueryParam("tenant_id") UUID tenantId,
                                                @QueryParam("time_range") @Valid String timeRangeParameter,
                                                @QueryParam("filters") String filtersParameter,
                                                @QueryParam("taps") String taps,
                                                @QueryParam("order_column") @Nullable String orderColumnParam,
                                                @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                                @QueryParam("limit") int limit,
                                                @QueryParam("offset") int offset) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
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

        long count = nzyme.getEthernet().webRtc().getTopPeerAddressPairsByBytesCount(timeRange, filters, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (AddressPairNumberAggregationResult x : nzyme.getEthernet().webRtc()
                .getTopPeerAddressPairsByBytes(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.UDP, x.address1()),
                            HistogramValueType.L4_ADDRESS,
                            null),
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.UDP, x.address2()),
                            HistogramValueType.L4_ADDRESS,
                            null),
                    HistogramValueStructureResponse.create(x.value(), HistogramValueType.BYTES, null),
                    x.address1().address() + " <> " + x.address2().address()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, true, values)).build();
    }

}
