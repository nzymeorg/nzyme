package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.spoofing.DhcpOfferedParameters;
import app.nzyme.core.ethernet.spoofing.Spoofing;
import app.nzyme.core.ethernet.spoofing.SpoofingConfiguration;
import app.nzyme.core.ethernet.spoofing.SpoofingExclusionMatcher;
import app.nzyme.core.ethernet.spoofing.db.DhcpSpoofingFindingEntry;
import app.nzyme.core.ethernet.spoofing.db.DhcpSpoofingServerEntry;
import app.nzyme.core.ethernet.spoofing.db.DhcpSpoofingVictimEntry;
import app.nzyme.core.ethernet.spoofing.db.SpoofingExclusionEntry;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.spoofing.*;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.taps.Tap;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/spoofing/dhcp")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Spoofing: DHCP", description = "DHCP spoofing findings and the DHCP servers of a tenant. Every server "
        + "that answers clients is learned with the configuration it hands out. A server that answers while it is "
        + "not trusted is a rogue server finding. A trusted server that hands out a gateway, DNS server, domain or "
        + "subnet mask different from its baseline is a changed configuration finding. Findings carry the clients "
        + "that accepted a lease from the server. Built from the DHCP transactions of all taps of the tenant.")
public class SpoofingDhcpResource extends TapDataHandlingResource {

    private static final String MANAGE_PERMISSION = "ethernet_monitoring_manage";

    private static final int VICTIMS_LIMIT = 500;

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findDhcpSpoofingFindings", summary = "List DHCP spoofing findings of a tenant",
            description = "Returns the findings of the tenant that were active at any point inside the time range, "
                    + "most recently seen first by default. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Findings found.",
            content = @Content(schema = @Schema(implementation = DhcpSpoofingFindingsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Fields: type, server_mac, server_address, response_count, transaction_count, victim_count.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker. A finding matches when it was active at any point inside the range.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "Sorting column: TYPE, SERVER_MAC, SERVER_ADDRESS, RESPONSE_COUNT, TRANSACTION_COUNT, VICTIM_COUNT, FIRST_SEEN or LAST_SEEN. Defaults to LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        Spoofing.DhcpFindingOrderColumn orderColumn = Spoofing.DhcpFindingOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Spoofing.DhcpFindingOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        Spoofing spoofing = nzyme.getEthernet().spoofing();
        SpoofingConfiguration config = spoofing.getConfiguration(organizationId, tenantId);
        Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> exclusions = spoofing.exclusions()
                .compileEnabled(organizationId, tenantId);
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);

        long total = spoofing.countDhcpFindings(filters, timeRange, organizationId, tenantId);
        List<DhcpSpoofingFindingResponse> findings = Lists.newArrayList();
        for (DhcpSpoofingFindingEntry finding : spoofing.findAllDhcpFindings(
                filters, timeRange, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            findings.add(toFindingResponse(finding, config, exclusions, addresses, organizationId, tenantId));
        }

        return Response.ok(DhcpSpoofingFindingsListResponse.create(total, findings)).build();
    }

    @GET
    @Path("/histogram/active")
    @Operation(operationId = "findActiveDhcpSpoofingFindingsHistogram", summary = "Get the number of active DHCP spoofing findings over time",
            description = "Returns the number of findings of the tenant that were going on per time bucket. "
                    + "Respects the same filters as the findings list. The response is an object that maps each "
                    + "bucket timestamp to a count. The bucket size is chosen automatically from the time range.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response activeFindingsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Same fields as the findings list.") @QueryParam("filters") String filtersParameter,
                                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        Map<DateTime, Integer> histogram = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().spoofing().activeDhcpFindingsHistogram(
                filters, organizationId, tenantId, timeRange, bucketing)) {
            histogram.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(histogram).build();
    }

    @GET
    @Path("/show/{finding_uuid}")
    @Operation(operationId = "findDhcpSpoofingFinding", summary = "Get a DHCP spoofing finding",
            description = "Returns one finding with the clients that accepted a lease from the server or chose its "
                    + "offer.")
    @ApiResponse(responseCode = "200", description = "Finding found.",
            content = @Content(schema = @Schema(implementation = DhcpSpoofingFindingDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Finding, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Finding UUID.") @PathParam("finding_uuid") UUID findingUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Spoofing spoofing = nzyme.getEthernet().spoofing();

        Optional<DhcpSpoofingFindingEntry> finding = spoofing.findDhcpFinding(findingUuid, organizationId, tenantId);
        if (finding.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        SpoofingConfiguration config = spoofing.getConfiguration(organizationId, tenantId);
        Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> exclusions = spoofing.exclusions()
                .compileEnabled(organizationId, tenantId);
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);

        List<DhcpSpoofingVictimResponse> victims = Lists.newArrayList();
        for (DhcpSpoofingVictimEntry victim : spoofing.findDhcpVictims(finding.get().id(), VICTIMS_LIMIT)) {
            victims.add(DhcpSpoofingVictimResponse.create(
                    victim.mac(),
                    addresses.macResponse(victim.mac(), organizationId, tenantId),
                    victim.assignedAddress(),
                    victim.assignedAddress() == null ? null
                            : addresses.addressResponse(victim.assignedAddress(), null, victim.mac(), L4Type.TCP, organizationId, tenantId),
                    victim.transactionCount(),
                    victim.leaseCount(),
                    victim.firstSeen(),
                    victim.lastSeen()
            ));
        }

        return Response.ok(DhcpSpoofingFindingDetailsResponse.create(
                toFindingResponse(finding.get(), config, exclusions, addresses, organizationId, tenantId),
                victims
        )).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{finding_uuid}")
    @Operation(operationId = "deleteDhcpSpoofingFinding", summary = "Delete a DHCP spoofing finding",
            description = "Deletes a finding with its victims. The finding is detected again as a new finding if "
                    + "the server keeps answering clients. To stop that, trust the server or create an exclusion. "
                    + "Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Finding deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Finding, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response delete(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Finding UUID.") @PathParam("finding_uuid") UUID findingUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Spoofing spoofing = nzyme.getEthernet().spoofing();

        if (spoofing.findDhcpFinding(findingUuid, organizationId, tenantId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        spoofing.deleteDhcpFinding(findingUuid, organizationId, tenantId);

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "deleteAllDhcpSpoofingFindings", summary = "Delete all DHCP spoofing findings of a tenant",
            description = "Deletes every finding of the tenant with all victims. Learned servers, exclusions and the "
                    + "detection configuration are kept. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Findings deleted.",
            content = @Content(schema = @Schema(implementation = SpoofingFindingsDeletedResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteAll(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        int deleted = nzyme.getEthernet().spoofing().deleteAllDhcpFindings(organizationId, tenantId);

        return Response.ok(SpoofingFindingsDeletedResponse.create(deleted)).build();
    }

    @GET
    @Path("/servers")
    @Operation(operationId = "findDhcpServers", summary = "List the DHCP servers of a tenant",
            description = "Returns every DHCP server that answered clients on the taps of the tenant, with whether "
                    + "it is trusted, the configuration it hands out (latest) and the configuration it is expected to "
                    + "hand out (baseline). Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Servers found.",
            content = @Content(schema = @Schema(implementation = DhcpSpoofingServersListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAllServers(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                   @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Fields: mac, address, trusted, response_count, transaction_count.") @QueryParam("filters") String filtersParameter,
                                   @Parameter(description = "Sorting column: MAC, ADDRESS, TRUSTED, RESPONSE_COUNT, TRANSACTION_COUNT, FIRST_SEEN or LAST_SEEN. Defaults to LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                   @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                   @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                   @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Spoofing.DhcpServerOrderColumn orderColumn = Spoofing.DhcpServerOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Spoofing.DhcpServerOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        Spoofing spoofing = nzyme.getEthernet().spoofing();
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);

        long total = spoofing.countDhcpServers(filters, organizationId, tenantId);
        List<DhcpSpoofingServerResponse> servers = Lists.newArrayList();
        for (DhcpSpoofingServerEntry server : spoofing.findAllDhcpServers(
                filters, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            servers.add(toServerResponse(server, addresses, organizationId, tenantId));
        }

        SpoofingConfiguration config = spoofing.getConfiguration(organizationId, tenantId);
        DateTime learningStart = spoofing.findDhcpLearningStartedAt(organizationId, tenantId)
                .or(() -> spoofing.findEarliestDhcpServerFirstSeen(organizationId, tenantId))
                .orElse(null);
        DateTime learningEndedAt = spoofing.findDhcpLearningEndedAt(organizationId, tenantId).orElse(null);
        DateTime learningEnd = learningStart == null ? null : learningStart.plusHours(config.dhcpLearningPeriodHours());
        boolean endedByUser = learningEnd != null && learningEndedAt != null && learningEndedAt.isBefore(learningEnd);
        if (endedByUser) {
            learningEnd = learningEndedAt;
        }
        boolean learningActive = learningEnd != null && config.dhcpLearningPeriodHours() > 0
                && learningEnd.isAfterNow();

        return Response.ok(DhcpSpoofingServersListResponse.create(
                total, servers, learningStart, learningEnd, learningActive, endedByUser
        )).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/servers/organization/{organization_id}/tenant/{tenant_id}/learning/end")
    @Operation(operationId = "endDhcpLearningPeriod", summary = "End the DHCP server learning period now",
            description = "Ends a running learning period. Servers that answer clients from now on are not trusted "
                    + "automatically until a user trusts them or restarts the learning period. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Learning period ended.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response endLearningPeriod(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                      @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getEthernet().spoofing().endDhcpLearning(organizationId, tenantId);

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/servers/organization/{organization_id}/tenant/{tenant_id}/learning/restart")
    @Operation(operationId = "restartDhcpLearningPeriod", summary = "Restart the DHCP server learning period",
            description = "Starts a new learning period now and clears a manual end. Every server that answers clients "
                    + "during the configured learning period from now on is trusted automatically, including servers "
                    + "that are known but not trusted. Trusted servers and findings are kept. Requires the ethernet_monitoring_manage "
                    + "feature permission.")
    @ApiResponse(responseCode = "200", description = "Learning period restarted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response restartLearningPeriod(@Parameter(hidden = true) @Context SecurityContext sc,
                                          @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                          @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getEthernet().spoofing().restartDhcpLearning(organizationId, tenantId);

        return Response.ok().build();
    }

    @GET
    @Path("/servers/show/{server_uuid}")
    @Operation(operationId = "findDhcpServer", summary = "Get a DHCP server",
            description = "Returns one learned DHCP server.")
    @ApiResponse(responseCode = "200", description = "Server found.",
            content = @Content(schema = @Schema(implementation = DhcpSpoofingServerResponse.class)))
    @ApiResponse(responseCode = "404", description = "Server, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findServer(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                               @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                               @Parameter(description = "Server UUID.") @PathParam("server_uuid") UUID serverUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<DhcpSpoofingServerEntry> server = nzyme.getEthernet().spoofing()
                .findDhcpServer(serverUuid, organizationId, tenantId);
        if (server.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(toServerResponse(server.get(), new ReconAddressResponses(nzyme), organizationId, tenantId)).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/servers/organization/{organization_id}/tenant/{tenant_id}/show/{server_uuid}/trusted/{trusted}")
    @Operation(operationId = "setDhcpServerTrusted", summary = "Trust or untrust a DHCP server",
            description = "A trusted server does not raise rogue server findings. Trusting a server takes the "
                    + "configuration it currently hands out as its baseline. Untrusting a server makes its next "
                    + "answer a rogue server finding. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Server updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Server, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response setServerTrusted(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                     @Parameter(description = "Server UUID.") @PathParam("server_uuid") UUID serverUuid,
                                     @Parameter(description = "Whether the server is trusted.") @PathParam("trusted") boolean trusted) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Spoofing spoofing = nzyme.getEthernet().spoofing();

        Optional<DhcpSpoofingServerEntry> server = spoofing.findDhcpServer(serverUuid, organizationId, tenantId);
        if (server.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        spoofing.setDhcpServerTrusted(server.get().id(), trusted);

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/servers/organization/{organization_id}/tenant/{tenant_id}/show/{server_uuid}/baseline")
    @Operation(operationId = "acceptDhcpServerConfiguration", summary = "Accept the current configuration of a DHCP server as its baseline",
            description = "Takes the configuration the server hands out at the moment as the expected "
                    + "configuration, which ends changed configuration findings for it. Use this after an intended "
                    + "change of the gateway, DNS servers, domain or subnet mask. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Baseline updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Server, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response acceptServerConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                              @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                              @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                              @Parameter(description = "Server UUID.") @PathParam("server_uuid") UUID serverUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Spoofing spoofing = nzyme.getEthernet().spoofing();

        Optional<DhcpSpoofingServerEntry> server = spoofing.findDhcpServer(serverUuid, organizationId, tenantId);
        if (server.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        spoofing.resetDhcpServerBaselineToLatest(server.get().id());

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/servers/organization/{organization_id}/tenant/{tenant_id}/show/{server_uuid}")
    @Operation(operationId = "deleteDhcpServer", summary = "Forget a DHCP server",
            description = "Removes a learned server. If it answers clients again it is learned anew, and trusted "
                    + "only if that still falls inside the learning period. Findings of the server are kept. "
                    + "Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Server deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Server, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteServer(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                 @Parameter(description = "Server UUID.") @PathParam("server_uuid") UUID serverUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Spoofing spoofing = nzyme.getEthernet().spoofing();

        Optional<DhcpSpoofingServerEntry> server = spoofing.findDhcpServer(serverUuid, organizationId, tenantId);
        if (server.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        spoofing.deleteDhcpServer(server.get().id());

        return Response.ok().build();
    }

    private DhcpSpoofingFindingResponse toFindingResponse(DhcpSpoofingFindingEntry finding,
                                                          SpoofingConfiguration config,
                                                          Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> exclusions,
                                                          ReconAddressResponses addresses,
                                                          UUID organizationId,
                                                          UUID tenantId) {
        Spoofing spoofing = nzyme.getEthernet().spoofing();

        List<SpoofingExclusionReferenceResponse> matchingExclusions = Lists.newArrayList();
        for (SpoofingExclusionEntry exclusion : spoofing.exclusions().findMatchingExclusions(finding, exclusions)) {
            matchingExclusions.add(SpoofingExclusionReferenceResponse.create(exclusion.uuid(), exclusion.name()));
        }

        Optional<DhcpSpoofingServerEntry> server = spoofing
                .findDhcpServerByIdentity(finding.serverMac(), finding.serverAddress(), organizationId, tenantId);

        boolean recent = finding.lastSeen().isAfter(DateTime.now().minusMinutes(config.dhcpDetectionWindowMinutes()));

        Boolean offeredAccepted = null;
        Boolean serverLatestMatchesBaseline = null;
        if (server.isPresent()) {
            DhcpOfferedParameters baseline = server.get().baseline();
            DhcpOfferedParameters latest = server.get().latest();

            if (finding.offered() != null) {
                offeredAccepted = server.get().trusted() && baseline != null
                        && baseline.sameConfigurationAs(finding.offered());
            }

            serverLatestMatchesBaseline = baseline == null || latest == null || latest.sameConfigurationAs(baseline);
        }

        return DhcpSpoofingFindingResponse.create(
                finding.uuid(),
                finding.type().name(),
                finding.serverMac(),
                addresses.macResponse(finding.serverMac(), organizationId, tenantId),
                finding.serverAddress(),
                addresses.addressResponse(finding.serverAddress(), null, finding.serverMac(), L4Type.UDP, organizationId, tenantId),
                finding.serverIdentifier(),
                server.map(DhcpSpoofingServerEntry::uuid).orElse(null),
                server.map(DhcpSpoofingServerEntry::trusted).orElse(null),
                offeredAccepted,
                serverLatestMatchesBaseline,
                DhcpOfferedParametersResponse.fromParameters(finding.offered()),
                DhcpOfferedParametersResponse.fromParameters(finding.expected()),
                finding.responseCount(),
                finding.offerCount(),
                finding.ackCount(),
                finding.transactionCount(),
                finding.victimCount(),
                recent,
                config.dhcpDetectionWindowMinutes(),
                tapResponses(finding.tapUuids()),
                matchingExclusions,
                finding.firstSeen(),
                finding.lastSeen()
        );
    }

    private DhcpSpoofingServerResponse toServerResponse(DhcpSpoofingServerEntry server,
                                                        ReconAddressResponses addresses,
                                                        UUID organizationId,
                                                        UUID tenantId) {
        DhcpOfferedParameters baseline = server.baseline();
        DhcpOfferedParameters latest = server.latest();
        boolean latestMatchesBaseline = baseline == null || latest == null || latest.sameConfigurationAs(baseline);

        return DhcpSpoofingServerResponse.create(
                server.uuid(),
                server.mac(),
                addresses.macResponse(server.mac(), organizationId, tenantId),
                server.address(),
                addresses.addressResponse(server.address(), null, server.mac(), L4Type.UDP, organizationId, tenantId),
                server.serverIdentifier(),
                server.trusted(),
                server.trustSource(),
                DhcpOfferedParametersResponse.fromParameters(baseline),
                server.baselineUpdatedAt(),
                DhcpOfferedParametersResponse.fromParameters(latest),
                latestMatchesBaseline,
                server.responseCount(),
                server.offerCount(),
                server.ackCount(),
                server.transactionCount(),
                tapResponses(server.tapUuids()),
                server.firstSeen(),
                server.lastSeen()
        );
    }

    private List<SpoofingTapResponse> tapResponses(List<UUID> tapUuids) {
        List<SpoofingTapResponse> taps = Lists.newArrayList();
        for (UUID tapUuid : tapUuids) {
            taps.add(SpoofingTapResponse.create(
                    tapUuid, nzyme.getTapManager().findTap(tapUuid).map(Tap::name).orElse(null)
            ));
        }

        return taps;
    }

}
