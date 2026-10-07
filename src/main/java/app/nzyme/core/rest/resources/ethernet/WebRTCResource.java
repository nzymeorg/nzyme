package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressPairNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnHistogramOrderColumn;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.nat.db.STUNNegotiationEntry;
import app.nzyme.core.ethernet.webrtc.WebRTC;
import app.nzyme.core.ethernet.webrtc.db.WebRTCSessionEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.misc.NATHelper;
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
import org.joda.time.DateTime;

import java.util.*;

import static app.nzyme.core.rest.misc.WebRTCHelper.buildWebRTCSessionDetailsResponse;
import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/webrtc")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "WebRTC", description = "WebRTC sessions that Nzyme taps observed on the network. WebRTC carries "
        + "live audio and video and the data channels of browser and app based calls, screen shares and remote "
        + "control tools. Nzyme recognizes a session when a STUN negotiation also carries RTP or DTLS, and reports "
        + "what the connection is carrying rather than how it was established.",
        externalDocs = @ExternalDocumentation(description = "WebRTC in the Nzyme documentation",
                url = "https://go.nzyme.org/ethernet-webrtc"))
public class WebRTCResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/sessions")
    @Operation(operationId = "findWebRtcSessions", summary = "List WebRTC sessions",
            description = "Returns all WebRTC sessions observed in the time range, newest initiated first by "
                    + "default. Each session reports whether it carries RTP, DTLS, audio and video, and the audio "
                    + "or video classification of a stream is a best effort guess from traffic characteristics. "
                    + "Sub sessions and the STUN negotiation are only included in the single session endpoint.")
    @ApiResponse(responseCode = "200", description = "Sessions found.",
            content = @Content(schema = @Schema(implementation = WebRTCSessionsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response allSessions(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
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
    @Operation(operationId = "findWebRtcSession", summary = "Get a WebRTC session",
            description = "Returns a single WebRTC session by its negotiation key. All sub sessions are included, "
                    + "which are the individual network flows that belong to the same negotiation, and the STUN "
                    + "negotiation is included if Nzyme recorded one.")
    @ApiResponse(responseCode = "200", description = "Session found.",
            content = @Content(schema = @Schema(implementation = WebRTCSessionDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Session not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response oneSession(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "SHA256 hash of the session negotiation key.") @PathParam("negotiation_key_sha256") String negotiationKeySha256,
                               @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                               @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                               @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
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
    @Operation(operationId = "findWebRtcActiveSessionsHistogram", summary = "Get histogram of active WebRTC sessions",
            description = "Returns the number of WebRTC sessions that were active in each bucket of the time range. "
                    + "The bucket size is chosen automatically based on the length of the time range.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = NumericHistogramResponse.class)))
    public Response activeSessionsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                            @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
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
    @Operation(operationId = "findWebRtcTopPeerAddressPairs", summary = "List top WebRTC peer address pairs",
            description = "Returns the peer address pairs that exchanged the most bytes in WebRTC sessions during "
                    + "the time range. Each row holds both peer addresses and the number of exchanged bytes. "
                    + "Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response topPeerAddressPairHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                                @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                                @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                                @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                                @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                                @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                                @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                                @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                                @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                                @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
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
        for (L4AddressPairNumberAggregationResult x : nzyme.getEthernet().webRtc()
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
