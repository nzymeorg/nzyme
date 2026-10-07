package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.generic.DateTimeNumberAggregationResult;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.dns.DNSTransaction;
import app.nzyme.core.ethernet.dns.db.*;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.dns.*;
import app.nzyme.core.rest.responses.shared.*;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.filters.FilterFrontendParameter;
import app.nzyme.core.util.filters.FilterFrontendParametersBuilder;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.core.util.TimeRange;
import app.nzyme.plugin.rest.security.PermissionLevel;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import app.nzyme.plugin.rest.security.RESTSecured;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.joda.time.DateTime;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.*;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/dns")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "DNS", description = "DNS queries and responses that taps recorded on the Ethernet network, "
        + "with traffic statistics, client and server pairs and the entropy log of suspicious query names.",
        externalDocs = @ExternalDocumentation(description = "DNS in the Nzyme documentation",
                url = "https://go.nzyme.org/ethernet-dns"))
public class DNSResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/global/charts/{type}")
    @Operation(operationId = "findDnsStatisticsChart", summary = "Get a DNS statistics chart",
            description = "Returns one DNS metric per time bucket. The response is an object that maps each bucket "
                    + "timestamp to the value of the requested metric. The bucket size is chosen automatically from "
                    + "the time range.")
    @ApiResponse(responseCode = "200", description = "Chart data found.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "400", description = "Unknown metric type.", content = @Content)
    public Response globalCharts(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                 @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds,
                                 @Parameter(description = "Metric to chart. One of request_count, request_bytes, response_count, response_bytes or nxdomain_count.") @PathParam("type") String type) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        List<DNSStatisticsBucket> statistics = nzyme.getEthernet().dns().getStatistics(timeRange, bucketing, taps);

        Map<DateTime, Long> response = Maps.newHashMap();
        for (DNSStatisticsBucket b : statistics) {
            Long value;
            switch (type) {
                case "request_count":
                    value = b.requestCount();
                    break;
                case "request_bytes":
                    value = b.requestBytes();
                    break;
                case "response_count":
                    value = b.responseCount();
                    break;
                case "response_bytes":
                    value = b.responseBytes();
                    break;
                case "nxdomain_count":
                    value = b.nxdomainCount();
                    break;
                default:
                    return Response.status(Response.Status.BAD_REQUEST).build();
            }

            response.put(b.bucket(), value);
        }

        return Response.ok(response).build();
    }

    @GET
    @Path("/global/statistics/{type}")
    @Operation(operationId = "findDnsTrafficSummary", summary = "Get a DNS traffic summary value",
            description = "Returns a single total for the whole time range. The response is an object with one "
                    + "value field.")
    @ApiResponse(responseCode = "200", description = "Value found.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "400", description = "Unknown summary type.", content = @Content)
    public Response globalStatistics(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                     @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds,
                                     @Parameter(description = "Summary to return. One of packets, traffic or nxdomains.") @PathParam("type") String type) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        DNSTrafficSummary trafficSummary = nzyme.getEthernet().dns().getTrafficSummary(timeRange, taps);

        long value;
        switch (type) {
            case "packets":
                value = trafficSummary.totalPackets();
                break;
            case "traffic":
                value = trafficSummary.totalTrafficBytes();
                break;
            case "nxdomains":
                value = trafficSummary.totalNxdomains();
                break;
            default:
                return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Map<String, Long> response = Maps.newHashMap();
        response.put("value", value);

        return Response.ok(response).build();
    }

    @GET
    @Path("/global/pairs")
    @Operation(operationId = "findDnsPairs", summary = "List top DNS servers and their clients",
            description = "Returns each DNS server that was queried in the time range, the number of distinct "
                    + "clients that queried it and the total number of requests. Busiest server first. Results are "
                    + "paginated.")
    @ApiResponse(responseCode = "200", description = "Pairs found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response globalPairs(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        long total = nzyme.getEthernet().dns().countPairs(timeRange, taps);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (DNSPairSummary ps : nzyme.getEthernet().dns().getPairs(timeRange, limit, offset, taps)) {
            Map<String, Object> requestCount = Maps.newHashMap();
            requestCount.put("title", ps.requestCount());

            String requestCountFilterParameters = new FilterFrontendParametersBuilder()
                    .addFilter("server_address", FilterFrontendParameter.create(
                            "server_address",
                            "Server Address",
                            "equals",
                            "==",
                            ps.server().address())
                    ).build();

            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.UDP, ps.server()),
                            HistogramValueType.L4_ADDRESS,
                            null
                    ),
                    HistogramValueStructureResponse.create(
                            ps.clientCount(),
                            HistogramValueType.INTEGER,
                            null
                    ),
                    HistogramValueStructureResponse.create(
                            requestCount,
                            HistogramValueType.DNS_TRANSACTION_LOG_LINK,
                            Map.of("filter_parameters", requestCountFilterParameters)
                    ),
                    ps.server().address() + ":" + ps.server().port()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(total, true, values)).build();
    }

    @GET
    @Path("/global/entropylog")
    @Operation(operationId = "findDnsEntropyLog", summary = "List DNS entropy log entries",
            description = "The entropy log records DNS queries whose names have an unusually high character "
                    + "entropy, which can indicate DNS tunneling or algorithmically generated domain names. Each "
                    + "entry carries the query, its responses, the measured entropy, the mean entropy and the "
                    + "resulting z-score. A tap logs an entry when the z-score exceeds the threshold configured in "
                    + "its own configuration file. Many legitimate queries have high entropy, so review entries "
                    + "manually. Entries whose transaction can no longer be resolved are skipped, so a page can "
                    + "hold fewer entries than the total count suggests. Results are paginated.",
            externalDocs = @ExternalDocumentation(description = "Entropy outliers in the Nzyme documentation",
                    url = "https://go.nzyme.org/ethernet-dns-entropy"))
    @ApiResponse(responseCode = "200", description = "Entropy log entries found.",
            content = @Content(schema = @Schema(implementation = DNSEntropyLogListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response globalEntropyLog(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                     @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                     @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                     @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                     @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        long total = nzyme.getEthernet().dns().countAllEntropyLogs(timeRange, taps);
        List<DNSEntropyLogResponse> logs = Lists.newArrayList();

        // Pull all required information and build response.
        List<DNSEntropyLogEntry> entropyLogs = nzyme.getEthernet().dns()
                .findAllEntropyLogs(timeRange, limit, offset, taps);

        nzyme.getDatabase().useHandle(handle -> {
            for (DNSEntropyLogEntry el : entropyLogs) {
                Optional<DNSTransaction> transaction = nzyme.getEthernet().dns()
                        .findTransaction(el.transactionId(), el.timestamp(), taps, handle);

                if (transaction.isEmpty()) {
                    continue;
                }

                DNSLogDataResponse query = logToResponse(organizationId, tenantId, transaction.get().query());
                List<DNSLogDataResponse> responses = Lists.newArrayList();
                for (DNSLogEntry response : transaction.get().responses()) {
                    responses.add(logToResponse(organizationId, tenantId, response));
                }

                logs.add(DNSEntropyLogResponse.create(query, responses, el.entropy(), el.entropyMean(), el.zscore()));
            }
        });

        return Response.ok(DNSEntropyLogListResponse.create(total, logs)).build();
    }

    @GET
    @Path("/transactions/log")
    @Operation(operationId = "findDnsTransactionLog", summary = "List DNS queries",
            description = "Returns the DNS queries in the time range, newest first. Each entry holds the query "
                    + "itself; fetch the matching responses separately. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Queries found.",
            content = @Content(schema = @Schema(implementation = DNSLogListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response transactionLog(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                   @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") String timeRangeParameter,
                                   @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                   @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                   @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                   @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        long total = nzyme.getEthernet().dns().countAllQueries(timeRange, filters, taps);

        List<DNSLogEntryResponse> transactions = Lists.newArrayList();
        for (DNSLogEntry q : nzyme.getEthernet().dns()
                .findAllQueries(timeRange, filters, limit, offset, taps)) {
            DNSLogDataResponse query = logToResponse(organizationId, tenantId, q);

            transactions.add(DNSLogEntryResponse.create(query));
        }

        transactions.sort((o1, o2) -> o2.query().timestamp().compareTo(o1.query().timestamp()));

        return Response.ok(DNSLogListResponse.create(total, transactions)).build();
    }

    @GET
    @Path("/transactions/log/{transactionId}/responses")
    @Operation(operationId = "findDnsTransactionResponses", summary = "List responses of a DNS transaction",
            description = "DNS transaction IDs are not unique over time, so the transaction timestamp is required "
                    + "to identify a single transaction. Pass it as an ISO 8601 timestamp. Returns an empty list if "
                    + "the transaction was never answered.")
    @ApiResponse(responseCode = "200", description = "Responses found.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = DNSLogDataResponse.class))))
    @ApiResponse(responseCode = "400", description = "The transaction timestamp is not a valid timestamp.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Transaction not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response findTransactionResponses(@Parameter(hidden = true) @Context SecurityContext sc,
                                             @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                             @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                             @Parameter(description = "DNS transaction ID.") @PathParam("transactionId") int transactionId,
                                             @Parameter(description = "Time of the transaction as an ISO 8601 timestamp.") @QueryParam("transaction_timestamp") String transactionTimestamp,
                                             @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DateTime timestamp;
        try {
            timestamp = DateTime.parse(transactionTimestamp);
        } catch(Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Optional<DNSTransaction> result = nzyme.getEthernet().dns()
                .findTransaction(transactionId, timestamp, taps);

        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<DNSLogDataResponse> responses = Lists.newArrayList();
        for (DNSLogEntry response : result.get().responses()) {
            responses.add(logToResponse(organizationId, tenantId, response));
        }

        return Response.ok(responses).build();
    }

    @GET
    @Path("/transactions/charts/count")
    @Operation(operationId = "findDnsTransactionCountChart", summary = "Get the DNS query count chart",
            description = "Returns the number of DNS queries per time bucket, with the same filters the query log "
                    + "accepts. The response is an object that maps each bucket timestamp to a count. The bucket "
                    + "size is chosen automatically from the time range.")
    @ApiResponse(responseCode = "200", description = "Chart data found.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    public Response transactionChart(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                     @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                     @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Map<DateTime, Long> response = Maps.newHashMap();
        for (DateTimeNumberAggregationResult r : nzyme.getEthernet().dns()
                .getTransactionCountHistogram("query", timeRange, filters, bucketing, taps)) {

            response.put(r.bucket(), r.count());
        }

        return Response.ok(response).build();
    }

    private DNSLogDataResponse logToResponse(UUID organizationId, UUID tenantId, DNSLogEntry log) {
        return DNSLogDataResponse.create(
                log.uuid(),
                log.tapUUID(),
                log.transactionId(),
                RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.UDP, log.client()),
                RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.UDP, log.server()),
                log.dataValue(),
                log.dataValueEtld(),
                log.dataType(),
                log.dnsType(),
                log.timestamp(),
                log.createdAt()
        );
    }

}
