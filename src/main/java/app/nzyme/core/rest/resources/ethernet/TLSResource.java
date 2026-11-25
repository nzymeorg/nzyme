package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.ethernet.InternalAddresses;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.StringNumberNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.l4.db.L4AddressAttributes;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import app.nzyme.core.ethernet.l4.tcp.db.TcpSessionEntry;
import app.nzyme.core.ethernet.tls.TLS;
import app.nzyme.core.ethernet.tls.db.TLSCertificateDetailsEntry;
import app.nzyme.core.ethernet.tls.db.TLSCertificateEntry;
import app.nzyme.core.ethernet.tls.db.TLSChainCertificateEntry;
import app.nzyme.core.ethernet.tls.db.TLSSessionEntry;
import app.nzyme.core.ethernet.tls.db.TLSSessionTapEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import app.nzyme.core.rest.responses.ethernet.tls.TLSAlertResponse;
import app.nzyme.core.rest.responses.ethernet.tls.TLSCertificateDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.tls.TLSCertificateFullDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.tls.TLSCertificatesListResponse;
import app.nzyme.core.rest.responses.ethernet.tls.TLSChainCertificateResponse;
import app.nzyme.core.rest.responses.ethernet.tls.TLSClientHelloResponse;
import app.nzyme.core.rest.responses.ethernet.tls.TLSServerHelloResponse;
import app.nzyme.core.rest.responses.ethernet.tls.TLSSessionDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.tls.TLSSessionFullDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.tls.TLSSessionsListResponse;
import app.nzyme.core.rest.responses.shared.HistogramValueStructureResponse;
import app.nzyme.core.rest.responses.shared.HistogramValueType;
import app.nzyme.core.rest.responses.shared.NumericHistogramResponse;
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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.joda.time.DateTime;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Path("/api/ethernet/tls")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "TLS", description = "TLS sessions that Nzyme taps observed on the network, with everything the "
        + "handshake exposes in the clear: negotiated version and cipher, SNI, ALPN, and on TLS 1.2 and older the "
        + "server and client certificate chains. Certificates are stored once and shared with the portal integrity "
        + "probes.",
        externalDocs = @ExternalDocumentation(description = "TLS in the Nzyme documentation",
                url = "https://go.nzyme.org/ethernet-tls"))
public class TLSResource extends TapDataHandlingResource {

    private static final ObjectMapper OM = new ObjectMapper();

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/sessions")
    @Operation(operationId = "findTlsSessions", summary = "List TLS sessions",
            description = "Returns all TLS sessions observed in the time range, newest established first by default. "
                    + "Certificate fields are only present for handshakes where the chain was visible, which is TLS "
                    + "1.2 and older.")
    @ApiResponse(responseCode = "200", description = "Sessions found.",
            content = @Content(schema = @Schema(implementation = TLSSessionsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response sessions(@Parameter(hidden = true) @Context SecurityContext sc,
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
        Filters filters = parseFilters(filtersParameter, sc);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TLS.SessionOrderColumn orderColumn = TLS.SessionOrderColumn.ESTABLISHED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = TLS.SessionOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().tls().countAllSessions(timeRange, filters, taps);

        List<TLSSessionDetailsResponse> sessions = Lists.newArrayList();
        for (TLSSessionEntry s : nzyme.getEthernet().tls()
                .findAllSessions(timeRange, filters, orderColumn, orderDirection, limit, offset, taps)) {
            sessions.add(buildSessionDetails(s, organizationId, tenantId, taps));
        }

        return Response.ok(TLSSessionsListResponse.create(total, sessions)).build();
    }

    @GET
    @Path("/sessions/show/{session_key}")
    @Operation(operationId = "findTlsSession", summary = "Get a TLS session",
            description = "Returns a single TLS session by the session key of the underlying TCP session, with the "
                    + "complete ClientHello and ServerHello, all alerts, and the server and client certificate chains as "
                    + "far as they were visible.")
    @ApiResponse(responseCode = "200", description = "Session found.",
            content = @Content(schema = @Schema(implementation = TLSSessionFullDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Session not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response session(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Session key of the underlying TCP session.") @PathParam("session_key") String sessionKey,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TLSSessionEntry> session = nzyme.getEthernet().tls().findSession(sessionKey, taps);

        if (session.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<TLSSessionTapEntry> tapEntries = nzyme.getEthernet().tls().findSessionTapEntries(sessionKey, taps);

        if (tapEntries.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // The most recently updated tap row carries the most complete picture of the handshake.
        TLSSessionTapEntry primary = tapEntries.get(0);

        return Response.ok(TLSSessionFullDetailsResponse.builder()
                .session(buildSessionDetails(session.get(), organizationId, tenantId, taps))
                .clientHello(parseClientHello(primary.clientHello()))
                .serverHello(parseServerHello(primary.serverHello()))
                .alerts(parseAlerts(primary.alerts()))
                .serverChain(buildChain(primary.serverChainSha256()))
                .clientChain(buildChain(primary.clientChainSha256()))
                .clientChangeCipherSpec(primary.clientChangeCipherSpec())
                .serverChangeCipherSpec(primary.serverChangeCipherSpec())
                .clientAppDataRecords(primary.clientAppDataRecords())
                .clientAppDataBytes(primary.clientAppDataBytes())
                .serverAppDataRecords(primary.serverAppDataRecords())
                .serverAppDataBytes(primary.serverAppDataBytes())
                .build()).build();
    }

    @GET
    @Path("/sessions/active/histogram")
    @Operation(operationId = "findTlsActiveSessionsHistogram", summary = "Get histogram of active TLS sessions",
            description = "Returns the number of TLS sessions that were active in each bucket of the time range. "
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
        Filters filters = parseFilters(filtersParameter, sc);

        Map<DateTime, Integer> buckets = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().tls()
                .getActiveSessionsHistogram(timeRange, bucketing, filters, tapUUIDs)) {
            buckets.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(NumericHistogramResponse.create(buckets, bucketing.bucketSizeMs())).build();
    }

    @GET
    @Path("/sessions/clients/top/histogram")
    @Operation(operationId = "findTlsTopClients", summary = "List top TLS clients",
            description = "Returns the client addresses with the most TLS sessions in the time range, with the "
                    + "session count and the number of bytes exchanged.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response topClientsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                        @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                        @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                        @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                        @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                        @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                        @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                        @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                        @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        return addressHistogram(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, taps,
                orderColumnParam, orderDirectionParam, limit, offset, true);
    }

    @GET
    @Path("/sessions/servers/top/histogram")
    @Operation(operationId = "findTlsTopServers", summary = "List top TLS servers",
            description = "Returns the server addresses with the most TLS sessions in the time range, with the "
                    + "session count and the number of bytes exchanged.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response topServersHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                        @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                        @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                        @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                        @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                        @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                        @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                        @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                        @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        return addressHistogram(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, taps,
                orderColumnParam, orderDirectionParam, limit, offset, false);
    }

    @GET
    @Path("/sessions/servernames/top/histogram")
    @Operation(operationId = "findTlsTopServerNames", summary = "List top TLS server names",
            description = "Returns the server names (SNI) with the most TLS sessions in the time range, with the "
                    + "session count and the number of bytes exchanged. Visible on every TLS version.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response topServerNamesHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                            @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                            @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        return stringHistogram(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, taps,
                orderColumnParam, orderDirectionParam, limit, offset, StringHistogram.SERVER_NAMES);
    }

    @GET
    @Path("/sessions/ports/top/histogram")
    @Operation(operationId = "findTlsTopServerPorts", summary = "List top TLS server ports",
            description = "Returns the server ports with the most TLS sessions in the time range, with the session "
                    + "count and the number of bytes exchanged. Useful to find TLS on ports other than 443.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response topServerPortsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                            @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                            @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        return stringHistogram(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, taps,
                orderColumnParam, orderDirectionParam, limit, offset, StringHistogram.SERVER_PORTS);
    }

    @GET
    @Path("/sessions/versions/histogram")
    @Operation(operationId = "findTlsVersions", summary = "List negotiated TLS versions",
            description = "Returns the negotiated TLS versions in the time range, with the session count and the "
                    + "number of bytes exchanged per version. Sessions without an observed ServerHello are not included.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response versionsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                      @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                      @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                      @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                      @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                      @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                      @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                      @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                      @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        return stringHistogram(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, taps,
                orderColumnParam, orderDirectionParam, limit, offset, StringHistogram.VERSIONS);
    }

    @GET
    @Path("/sessions/ja4/top/histogram")
    @Operation(operationId = "findTlsTopJa4", summary = "List top JA4 client fingerprints",
            description = "Returns the JA4 TLS client fingerprints with the most sessions in the time range, with "
                    + "the session count and the number of bytes exchanged. Sessions without an observed ClientHello "
                    + "are not included.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response topJa4Histogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                    @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                    @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                    @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                    @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                    @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                    @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                    @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        return stringHistogram(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, taps,
                orderColumnParam, orderDirectionParam, limit, offset, StringHistogram.JA4);
    }

    private enum StringHistogram { SERVER_NAMES, SERVER_PORTS, VERSIONS, JA4 }

    private Response addressHistogram(SecurityContext sc, UUID organizationId, UUID tenantId, String timeRangeParameter,
                                      String filtersParameter, String taps, String orderColumnParam,
                                      String orderDirectionParam, int limit, int offset, boolean clients) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

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

        TLS tls = nzyme.getEthernet().tls();
        long count = clients ? tls.getTopClientsCount(timeRange, filters, tapUUIDs)
                : tls.getTopServersCount(timeRange, filters, tapUUIDs);
        InternalAddresses.Rules internalAddresses = nzyme.getEthernet().internalAddresses().of(organizationId, tenantId);
        List<L4AddressDataAddressNumberNumberAggregationResult> results = clients
                ? tls.getTopClients(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs, internalAddresses)
                : tls.getTopServers(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs, internalAddresses);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (L4AddressDataAddressNumberNumberAggregationResult x : results) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.TCP, x.key()),
                            HistogramValueType.L4_ADDRESS_NO_PORT,
                            null),
                    HistogramValueStructureResponse.create(x.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(x.value2(), HistogramValueType.BYTES, null),
                    x.key().address()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, true, values)).build();
    }

    private Response stringHistogram(SecurityContext sc, UUID organizationId, UUID tenantId, String timeRangeParameter,
                                     String filtersParameter, String taps, String orderColumnParam,
                                     String orderDirectionParam, int limit, int offset, StringHistogram type) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

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

        TLS tls = nzyme.getEthernet().tls();
        long count;
        List<StringNumberNumberAggregationResult> results;
        switch (type) {
            case SERVER_NAMES -> {
                count = tls.getTopServerNamesCount(timeRange, filters, tapUUIDs);
                results = tls.getTopServerNames(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs);
            }
            case SERVER_PORTS -> {
                count = tls.getTopServerPortsCount(timeRange, filters, tapUUIDs);
                results = tls.getTopServerPorts(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs);
            }
            case JA4 -> {
                count = tls.getTopJa4Count(timeRange, filters, tapUUIDs);
                results = tls.getTopJa4(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs);
            }
            default -> {
                count = tls.getVersionsCount(timeRange, filters, tapUUIDs);
                results = tls.getVersions(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs);
            }
        }

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (StringNumberNumberAggregationResult x : results) {
            HistogramValueStructureResponse key = type == StringHistogram.SERVER_PORTS
                    ? HistogramValueStructureResponse.create(Integer.parseInt(x.key()), HistogramValueType.L4_PORT, null)
                    : HistogramValueStructureResponse.create(x.key(), HistogramValueType.GENERIC, null);

            values.add(ThreeColumnTableHistogramValueResponse.create(
                    key,
                    HistogramValueStructureResponse.create(x.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(x.value2(), HistogramValueType.BYTES, null),
                    x.key()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, true, values)).build();
    }

    @GET
    @Path("/certificates")
    @Operation(operationId = "findTlsCertificates", summary = "List TLS certificates",
            description = "Returns the certificates that appeared in TLS sessions observed in the time range, server "
                    + "and client chains alike, with the number of sessions that referenced each certificate.")
    @ApiResponse(responseCode = "200", description = "Certificates found.",
            content = @Content(schema = @Schema(implementation = TLSCertificatesListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response certificates(@Parameter(hidden = true) @Context SecurityContext sc,
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
        Filters filters = parseFilters(filtersParameter, sc);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TLS.CertificateOrderColumn orderColumn = TLS.CertificateOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = TLS.CertificateOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().tls().countAllCertificates(timeRange, filters, taps);

        List<TLSCertificateDetailsResponse> certificates = Lists.newArrayList();
        for (TLSCertificateEntry c : nzyme.getEthernet().tls()
                .findAllCertificates(timeRange, filters, orderColumn, orderDirection, limit, offset, taps)) {
            certificates.add(buildCertificateDetails(c));
        }

        return Response.ok(TLSCertificatesListResponse.create(total, certificates)).build();
    }

    @GET
    @Path("/certificates/show/{sha256}")
    @Operation(operationId = "findTlsCertificate", summary = "Get a certificate",
            description = "Returns a single certificate by its SHA-256 fingerprint. The certificate fields are "
                    + "global, the usage counters are scoped to the time range and taps. A certificate is only "
                    + "returned if one of the passed taps has seen it, in a TLS session or a portal integrity probe.")
    @ApiResponse(responseCode = "200", description = "Certificate found.",
            content = @Content(schema = @Schema(implementation = TLSCertificateFullDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Certificate not found, not seen by the passed taps, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response certificate(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "SHA-256 fingerprint of the certificate, lowercase hex.") @PathParam("sha256") String sha256,
                                @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                @Parameter(description = "Time range selector for the usage counters. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String fingerprint = sha256 == null ? null : sha256.toLowerCase();

        if (!nzyme.getEthernet().tls().isCertificateVisible(fingerprint, taps)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TLSCertificateDetailsEntry> certificate = nzyme.getEthernet().tls()
                .findCertificate(fingerprint, timeRange, taps);

        if (certificate.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TLSCertificateDetailsEntry c = certificate.get();

        TLSChainCertificateResponse issuer = null;
        if (c.authorityKeyIdentifier() != null && !Boolean.TRUE.equals(c.selfSigned())) {
            issuer = nzyme.getEthernet().tls().findIssuerCertificate(c.authorityKeyIdentifier(), taps)
                    .map(TLSResource::buildChainCertificate)
                    .orElse(null);
        }

        return Response.ok(TLSCertificateFullDetailsResponse.builder()
                .certificate(TLSCertificateDetailsResponse.builder()
                        .sha256(c.sha256())
                        .sha1(c.sha1())
                        .parsed(c.parsed())
                        .subject(c.subject())
                        .issuer(c.issuer())
                        .commonName(c.commonName())
                        .subjectAlternativeNames(c.subjectAlternativeNames())
                        .notBefore(c.notBefore())
                        .notAfter(c.notAfter())
                        .isCa(c.isCa())
                        .selfSigned(c.selfSigned())
                        .publicKeyAlgorithm(c.publicKeyAlgorithm())
                        .publicKeyBits(c.publicKeyBits())
                        .signatureAlgorithm(c.signatureAlgorithm())
                        .extendedKeyUsage(c.extendedKeyUsage())
                        .firstSeen(c.firstSeen())
                        .lastSeen(c.lastSeen())
                        .sessionCount(c.sessionCount())
                        .sources(c.sources())
                        .build())
                .parseError(c.parseError())
                .version(c.version())
                .serial(c.serial())
                .subjectKeyIdentifier(c.subjectKeyIdentifier())
                .authorityKeyIdentifier(c.authorityKeyIdentifier())
                .pem(toPem(c.der()))
                .derLength(c.der().length)
                .issuerCertificate(issuer)
                .build()).build();
    }

    @GET
    @Path("/certificates/expiring")
    @Operation(operationId = "findTlsExpiringCertificates", summary = "List expiring TLS certificates",
            description = "Returns certificates that were in use in the time range and are expired or expire within "
                    + "the given number of days, soonest expiry first by default.")
    @ApiResponse(responseCode = "200", description = "Certificates found.",
            content = @Content(schema = @Schema(implementation = TLSCertificatesListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response expiringCertificates(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                         @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                         @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                         @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                         @Parameter(description = "Include certificates expiring within this many days. Default 30.") @QueryParam("days") @Nullable Integer days,
                                         @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                         @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                         @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                         @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                         @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        int withinDays = days == null ? 30 : Math.max(0, days);

        TLS.CertificateOrderColumn orderColumn = TLS.CertificateOrderColumn.NOT_AFTER;
        OrderDirection orderDirection = OrderDirection.ASC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = TLS.CertificateOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().tls().countExpiringCertificates(timeRange, filters, withinDays, taps);

        List<TLSCertificateDetailsResponse> certificates = Lists.newArrayList();
        for (TLSCertificateEntry c : nzyme.getEthernet().tls()
                .findExpiringCertificates(timeRange, filters, withinDays, orderColumn, orderDirection, limit, offset, taps)) {
            certificates.add(buildCertificateDetails(c));
        }

        return Response.ok(TLSCertificatesListResponse.create(total, certificates)).build();
    }

    @GET
    @Path("/certificates/new/histogram")
    @Operation(operationId = "findTlsNewCertificatesHistogram", summary = "Get histogram of newly seen certificates",
            description = "Returns the number of certificates first seen in each bucket of the time range, among the "
                    + "certificates referenced by sessions and probes in the time range.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = NumericHistogramResponse.class)))
    public Response newCertificatesHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                             @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                             @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                             @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        Filters filters = parseFilters(filtersParameter, sc);

        Map<DateTime, Integer> buckets = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().tls()
                .getNewCertificatesHistogram(timeRange, bucketing, filters, tapUUIDs)) {
            buckets.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(NumericHistogramResponse.create(buckets, bucketing.bucketSizeMs())).build();
    }

    @GET
    @Path("/certificates/issuers/top/histogram")
    @Operation(operationId = "findTlsTopCertificateIssuers", summary = "List top certificate issuers",
            description = "Returns the certificate issuers seen in the time range, with the number of sessions and "
                    + "probes that used a certificate from the issuer and the number of distinct certificates.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response topIssuersHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                        @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                        @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                        @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                        @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                        @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                        @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                        @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                        @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        return certificateHistogram(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, taps,
                orderColumnParam, orderDirectionParam, limit, offset, TLS.CertificateHistogramKey.ISSUER);
    }

    @GET
    @Path("/certificates/keytypes/histogram")
    @Operation(operationId = "findTlsCertificateKeyTypes", summary = "List certificate key types",
            description = "Returns the public key algorithms and sizes of certificates seen in the time range, with "
                    + "the number of sessions and probes that used them and the number of distinct certificates.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response keyTypesHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                      @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                      @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                      @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                      @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                      @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                      @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                      @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                      @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        return certificateHistogram(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, taps,
                orderColumnParam, orderDirectionParam, limit, offset, TLS.CertificateHistogramKey.KEY_TYPE);
    }

    @GET
    @Path("/certificates/signaturealgorithms/histogram")
    @Operation(operationId = "findTlsCertificateSignatureAlgorithms", summary = "List certificate signature algorithms",
            description = "Returns the signature algorithms of certificates seen in the time range, with the number "
                    + "of sessions and probes that used them and the number of distinct certificates.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response signatureAlgorithmsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                                 @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                                 @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                                 @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                                 @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                                 @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                                 @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                                 @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                                 @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                                 @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        return certificateHistogram(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, taps,
                orderColumnParam, orderDirectionParam, limit, offset, TLS.CertificateHistogramKey.SIGNATURE_ALGORITHM);
    }

    private Response certificateHistogram(SecurityContext sc, UUID organizationId, UUID tenantId, String timeRangeParameter,
                                          String filtersParameter, String taps, String orderColumnParam,
                                          String orderDirectionParam, int limit, int offset,
                                          TLS.CertificateHistogramKey key) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

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

        TLS tls = nzyme.getEthernet().tls();
        long count = tls.getCertificateHistogramCount(key, timeRange, filters, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (StringNumberNumberAggregationResult x : tls.getCertificateHistogram(
                key, timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(x.key(), HistogramValueType.GENERIC, null),
                    HistogramValueStructureResponse.create(x.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(x.value2(), HistogramValueType.INTEGER, null),
                    x.key()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, true, values)).build();
    }

    private static String toPem(byte[] der) {
        return "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END CERTIFICATE-----";
    }

    private List<TLSChainCertificateResponse> buildChain(List<String> hashes) {
        List<TLSChainCertificateResponse> chain = Lists.newArrayList();
        for (TLSChainCertificateEntry c : nzyme.getEthernet().tls().findChainCertificates(hashes)) {
            chain.add(buildChainCertificate(c));
        }

        return chain;
    }

    private static TLSChainCertificateResponse buildChainCertificate(TLSChainCertificateEntry c) {
        return TLSChainCertificateResponse.builder()
                .sha256(c.sha256())
                .parsed(c.parsed())
                .subject(c.subject())
                .issuer(c.issuer())
                .commonName(c.commonName())
                .notBefore(c.notBefore())
                .notAfter(c.notAfter())
                .isCa(c.isCa())
                .selfSigned(c.selfSigned())
                .publicKeyAlgorithm(c.publicKeyAlgorithm())
                .publicKeyBits(c.publicKeyBits())
                .signatureAlgorithm(c.signatureAlgorithm())
                .build();
    }

    // The hello messages are stored as the JSON the tap reported. A row written by an older node version may lack fields.
    @Nullable
    private static TLSClientHelloResponse parseClientHello(@Nullable String json) {
        JsonNode node = readJson(json);
        if (node == null || !node.isObject()) {
            return null;
        }

        return TLSClientHelloResponse.builder()
                .recordVersion(text(node, "record_version"))
                .legacyVersion(text(node, "legacy_version"))
                .supportedVersions(strings(node, "supported_versions"))
                .cipherSuites(ints(node, "cipher_suites"))
                .extensions(ints(node, "extensions"))
                .supportedGroups(ints(node, "supported_groups"))
                .ecPointFormats(ints(node, "ec_point_formats"))
                .signatureAlgorithms(ints(node, "signature_algorithms"))
                .alpn(strings(node, "alpn"))
                .sessionIdPresent(bool(node, "session_id_present"))
                .offersSessionTicket(bool(node, "offers_session_ticket"))
                .offersPreSharedKey(bool(node, "offers_pre_shared_key"))
                .build();
    }

    @Nullable
    private static TLSServerHelloResponse parseServerHello(@Nullable String json) {
        JsonNode node = readJson(json);
        if (node == null || !node.isObject()) {
            return null;
        }

        return TLSServerHelloResponse.create(
                text(node, "record_version"),
                text(node, "legacy_version"),
                node.path("compression_method").asInt(0),
                ints(node, "extensions")
        );
    }

    private static List<TLSAlertResponse> parseAlerts(@Nullable String json) {
        JsonNode node = readJson(json);
        List<TLSAlertResponse> alerts = Lists.newArrayList();
        if (node == null || !node.isArray()) {
            return alerts;
        }

        for (JsonNode alert : node) {
            alerts.add(TLSAlertResponse.create(
                    alert.path("direction").asString("Unknown"),
                    alert.path("level").asInt(0),
                    alert.path("description").asInt(0),
                    text(alert, "description_name"),
                    bool(alert, "encrypted")
            ));
        }

        return alerts;
    }

    @Nullable
    private static JsonNode readJson(@Nullable String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        try {
            return OM.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static boolean bool(JsonNode node, String field) {
        return node.path(field).asBoolean(false);
    }

    private static List<String> strings(JsonNode node, String field) {
        List<String> values = Lists.newArrayList();
        for (JsonNode value : node.path(field)) {
            values.add(value.asString());
        }
        return values;
    }

    private static List<Integer> ints(JsonNode node, String field) {
        List<Integer> values = Lists.newArrayList();
        for (JsonNode value : node.path(field)) {
            values.add(value.asInt());
        }
        return values;
    }

    private static TLSCertificateDetailsResponse buildCertificateDetails(TLSCertificateEntry c) {
        return TLSCertificateDetailsResponse.builder()
                .sha256(c.sha256())
                .sha1(c.sha1())
                .parsed(c.parsed())
                .subject(c.subject())
                .issuer(c.issuer())
                .commonName(c.commonName())
                .subjectAlternativeNames(c.subjectAlternativeNames())
                .notBefore(c.notBefore())
                .notAfter(c.notAfter())
                .isCa(c.isCa())
                .selfSigned(c.selfSigned())
                .publicKeyAlgorithm(c.publicKeyAlgorithm())
                .publicKeyBits(c.publicKeyBits())
                .signatureAlgorithm(c.signatureAlgorithm())
                .extendedKeyUsage(c.extendedKeyUsage())
                .firstSeen(c.firstSeen())
                .lastSeen(c.lastSeen())
                .sessionCount(c.sessionCount())
                .sources(c.sources())
                .build();
    }

    private TLSSessionDetailsResponse buildSessionDetails(TLSSessionEntry s,
                                                          UUID organizationId,
                                                          UUID tenantId,
                                                          List<UUID> taps) {
        /*
         * The underlying TCP session carries geo and address context. It can be gone already,
         * in which case the addresses the TLS session itself recorded are used.
         */
        Optional<TcpSessionEntry> tcpSession = nzyme.getEthernet().tcp()
                .findSessionBySessionKey(s.tcpSessionKey(), s.establishedAt(), taps);
        InternalAddresses.Rules internalAddresses = nzyme.getEthernet().internalAddresses().of(organizationId, tenantId);

        L4AddressData clientData = tcpSession.map(TcpSessionEntry::source)
                .orElseGet(() -> addressData(s.sourceMac(), s.sourceAddress(), s.sourcePort(), internalAddresses));
        L4AddressData serverData = tcpSession.map(TcpSessionEntry::destination)
                .orElseGet(() -> addressData(s.destinationMac(), s.destinationAddress(), s.destinationPort(), internalAddresses));

        L4AddressResponse client = RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.TCP, clientData);
        L4AddressResponse server = RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.TCP, serverData);

        return TLSSessionDetailsResponse.builder()
                .tcpSessionKey(s.tcpSessionKey())
                .client(client)
                .server(server)
                .connectionStatus(s.connectionStatus())
                .bytesCount(s.bytesCount())
                .serverName(s.serverName())
                .version(s.version())
                .clientMaxVersion(s.clientMaxVersion())
                .cipherSuite(s.cipherSuite())
                .alpnSelected(s.alpnSelected())
                .alpnOffered(s.alpnOffered())
                .ja4(s.ja4())
                .sessionResumed(s.sessionResumed())
                .helloRetryRequest(s.helloRetryRequest())
                .encryptedClientHello(s.encryptedClientHello())
                .clientCertificateRequested(s.clientCertificateRequested())
                .serverLeafSha256(s.serverLeafSha256())
                .serverLeafCommonName(s.serverLeafCommonName())
                .serverLeafIssuer(s.serverLeafIssuer())
                .serverLeafNotAfter(s.serverLeafNotAfter())
                .serverChainLength(s.serverChainLength())
                .clientLeafSha256(s.clientLeafSha256())
                .serverLeafSelfSigned(s.serverLeafSelfSigned())
                .sniMatchesCertificate(s.sniMatchesCertificate())
                .leafValidAtHandshake(s.leafValidAtHandshake())
                .chainTrusted(s.chainTrusted())
                .chainTrustError(s.chainTrustError())
                .establishedAt(s.establishedAt())
                .terminatedAt(s.terminatedAt())
                .mostRecentSegmentTime(s.mostRecentSegmentTime())
                .durationMs(s.durationMs())
                .build();
    }

    private static L4AddressData addressData(String mac, String address, int port,
                                             InternalAddresses.Rules internalAddresses) {
        boolean siteLocal = false;
        boolean loopback = false;
        boolean multicast = false;
        try {
            InetAddress inet = InetAddress.getByName(address);
            siteLocal = internalAddresses.isSiteLocal(inet);
            loopback = inet.isLoopbackAddress();
            multicast = inet.isMulticastAddress();
        } catch (UnknownHostException ignored) {
            // Stored addresses are literal IPs. Attributes stay false if this ever fails.
        }

        return L4AddressData.create(mac, address, port, null, L4AddressAttributes.create(siteLocal, loopback, multicast));
    }

}
