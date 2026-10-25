package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.recon.*;
import app.nzyme.core.ethernet.recon.db.ReconExclusionEntry;
import app.nzyme.core.ethernet.recon.db.ReconScanEntry;
import app.nzyme.core.ethernet.recon.db.ReconScanTargetEntry;
import app.nzyme.core.registry.RegistryChangeValidator;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.requests.GenericConfigurationUpdateRequest;
import app.nzyme.core.rest.responses.ethernet.recon.*;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.taps.Tap;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/recon/scans")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Recon Scans", description = "A scan is a device probing the network: a port scan tries many ports "
        + "of one host, a host sweep tries many hosts, an ARP sweep asks for many addresses on the local "
        + "segment. Nzyme detects scans passively from the TCP, UDP and ARP traffic the taps report, merges "
        + "observations of several taps and keeps a scan open while the device continues probing. Each scan "
        + "carries the targets it probed and whether they answered. Scans are retention cleaned.")
public class ReconScansResource extends TapDataHandlingResource {

    private static final Logger LOG = LogManager.getLogger(ReconScansResource.class);

    private static final String MANAGE_PERMISSION = "ethernet_monitoring_manage";

    private final ObjectMapper om = new ObjectMapper();

    @Inject
    private NzymeNode nzyme;

    private ReconAddressResponses addresses;

    private ReconAddressResponses addresses() {
        if (addresses == null) {
            addresses = new ReconAddressResponses(nzyme);
        }

        return addresses;
    }

    @GET
    @Operation(operationId = "findReconScans", summary = "List scans of a tenant",
            description = "Returns all detected scans of the tenant, most recently seen first by default. The "
                    + "response also includes the number of scans per type, independent of the filters. Results "
                    + "are paginated.")
    @ApiResponse(responseCode = "200", description = "Scans found.",
            content = @Content(schema = @Schema(implementation = ReconScansListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Comma separated list of tap UUIDs. Only scans observed by at least one of these taps are returned. Slow scans are built from the probe history of the whole tenant and are always included. Omit or pass * for all taps the user can access. An empty value selects no taps. Detection itself always covers all taps of the tenant.") @QueryParam("taps") @Nullable String tapIds,
                            @Parameter(description = "SCANS for port scans and sweeps (default), FINGERPRINTING for OS fingerprinting attempts.") @QueryParam("category") @Nullable String categoryParam,
                            @Parameter(description = "Sorting column. Defaults to the time the scan was last seen.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseTapsFilter(sc, tapIds);

        ReconScanCategory category;
        try {
            category = parseCategory(categoryParam);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Recon.OrderColumn orderColumn = Recon.OrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Recon.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        Recon recon = nzyme.getEthernet().recon();
        ReconConfiguration config = recon.getConfiguration(organizationId, tenantId);

        long total = recon.countScans(filters, category, taps, organizationId, tenantId);
        Map<ReconExclusionEntry, ReconExclusionMatcher> exclusions = recon.exclusions()
                .compileEnabled(organizationId, tenantId);
        List<ReconScanDetailsResponse> result = Lists.newArrayList();
        for (ReconScanEntry scan : recon.findAllScans(
                filters, category, taps, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            result.add(toResponse(scan, config, organizationId, tenantId, exclusions));
        }

        return Response.ok(ReconScansListResponse.create(
                total, recon.countScansByType(category, taps, organizationId, tenantId), result
        )).build();
    }

    @GET
    @Path("/histogram/active")
    @Operation(operationId = "findActiveReconScansHistogram", summary = "Get the number of active scans over time",
            description = "Returns the number of scans of the tenant that were going on per time bucket. Respects "
                    + "the same filters as the scans list. The response is an object that maps each bucket "
                    + "timestamp to a count. The bucket size is chosen automatically from the time range.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response activeScansHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                         @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                         @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Same fields as the scans list.") @QueryParam("filters") String filtersParameter,
                                         @Parameter(description = "Comma separated list of tap UUIDs. Only scans observed by at least one of these taps are counted. Slow scans are built from the probe history of the whole tenant and are always included. Omit or pass * for all taps the user can access.") @QueryParam("taps") @Nullable String tapIds,
                                         @Parameter(description = "SCANS for port scans and sweeps (default), FINGERPRINTING for OS fingerprinting attempts.") @QueryParam("category") @Nullable String categoryParam,
                                         @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        List<UUID> taps = parseTapsFilter(sc, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        ReconScanCategory category;
        try {
            category = parseCategory(categoryParam);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Map<DateTime, Integer> histogram = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().recon().activeScansHistogram(
                filters, category, taps, organizationId, tenantId, timeRange, bucketing)) {
            histogram.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(histogram).build();
    }

    @GET
    @Path("/show/{scan_uuid}")
    @Operation(operationId = "findReconScan", summary = "Get a scan",
            description = "Returns one scan with all of its details, including the packet attributes of the "
                    + "probes that help identify the scanning tool.")
    @ApiResponse(responseCode = "200", description = "Scan found.",
            content = @Content(schema = @Schema(implementation = ReconScanDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Scan, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Scan UUID.") @PathParam("scan_uuid") UUID scanUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Recon recon = nzyme.getEthernet().recon();

        Optional<ReconScanEntry> scan = recon.findScan(scanUuid, organizationId, tenantId);
        if (scan.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Map<ReconExclusionEntry, ReconExclusionMatcher> exclusions = recon.exclusions()
                .compileEnabled(organizationId, tenantId);

        return Response.ok(toResponse(scan.get(), recon.getConfiguration(organizationId, tenantId),
                organizationId, tenantId, exclusions)).build();
    }

    @GET
    @Path("/show/{scan_uuid}/targets")
    @Operation(operationId = "findReconScanTargets", summary = "List the targets of a scan",
            description = "Returns the hosts and ports the scan probed, with the outcome of each probe: OPEN when "
                    + "the target answered, CLOSED when it refused, UNANSWERED when nothing came back. Targets "
                    + "that answered are listed first by default. Very large scans only keep a sample of their "
                    + "unanswered targets. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Targets found.",
            content = @Content(schema = @Schema(implementation = ReconScanTargetsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Scan, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findTargets(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                @Parameter(description = "Scan UUID.") @PathParam("scan_uuid") UUID scanUuid,
                                @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                @Parameter(description = "Sorting column: TARGET_ADDRESS, TARGET_PORT, OUTCOME, PROBE_COUNT, FIRST_SEEN or LAST_SEEN. Defaults to OUTCOME.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Recon recon = nzyme.getEthernet().recon();

        Optional<ReconScanEntry> scan = recon.findScan(scanUuid, organizationId, tenantId);
        if (scan.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Recon.TargetOrderColumn orderColumn = Recon.TargetOrderColumn.OUTCOME;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Recon.TargetOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        L4Type l4Type = scan.get().type().startsWith("UDP") ? L4Type.UDP : L4Type.TCP;

        long total = recon.countTargets(scan.get().id(), filters);
        List<ReconScanTargetResponse> targets = Lists.newArrayList();
        for (ReconScanTargetEntry target : recon.findTargets(
                scan.get().id(), filters, orderColumn, orderDirection, limit, offset)) {
            targets.add(ReconScanTargetResponse.create(
                    target.targetAddress(),
                    addresses().addressResponse(target.targetAddress(), target.targetPort(), null, l4Type, organizationId, tenantId),
                    target.targetPort(),
                    target.outcome(),
                    target.probeCount(),
                    target.firstSeen(),
                    target.lastSeen()
            ));
        }

        return Response.ok(ReconScanTargetsListResponse.create(total, targets)).build();
    }

    @GET
    @Path("/show/{scan_uuid}/summary")
    @Operation(operationId = "findReconScanSummary", summary = "Get a condensed view of what a scan targeted and found",
            description = "Returns what answered the scan (ports for a port scan, hosts with their answering ports "
                    + "for a sweep), a timeline of when targets were first probed per outcome, and a breakdown of "
                    + "the targets by network (sweeps) or by port range (port scans). This is the analyst's "
                    + "overview; the targets endpoint has the full detail.")
    @ApiResponse(responseCode = "200", description = "Summary found.",
            content = @Content(schema = @Schema(implementation = ReconScanSummaryResponse.class)))
    @ApiResponse(responseCode = "404", description = "Scan, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response summary(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Scan UUID.") @PathParam("scan_uuid") UUID scanUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Recon recon = nzyme.getEthernet().recon();

        Optional<ReconScanEntry> scan = recon.findScan(scanUuid, organizationId, tenantId);
        if (scan.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long scanId = scan.get().id();

        boolean portScan;
        try {
            portScan = ReconScanType.valueOf(scan.get().type()).isPortScan();
        } catch (IllegalArgumentException e) {
            portScan = false;
        }

        List<ReconScanAnsweredPortResponse> answeredPorts = null;
        Long answeredPortsTotal = null;
        List<ReconScanAnsweredPortResponse> unansweredPorts = null;
        Long unansweredPortsTotal = null;
        List<ReconScanAnsweredHostResponse> answeredHosts = null;
        Long answeredHostsTotal = null;
        List<ReconScanTargetNetworkResponse> networks = null;
        List<ReconScanPortRangeResponse> portRanges = null;

        if (portScan) {
            answeredPorts = Lists.newArrayList();
            for (Recon.AnsweredPort p : recon.findAnsweredPorts(scanId, 500)) {
                answeredPorts.add(ReconScanAnsweredPortResponse.create(p.port(), p.probeCount(), p.firstSeen(), p.lastSeen()));
            }
            answeredPortsTotal = recon.countAnsweredPorts(scanId);

            unansweredPorts = Lists.newArrayList();
            for (Recon.AnsweredPort p : recon.findUnansweredPorts(scanId, 500)) {
                unansweredPorts.add(ReconScanAnsweredPortResponse.create(p.port(), p.probeCount(), p.firstSeen(), p.lastSeen()));
            }
            unansweredPortsTotal = recon.countUnansweredPorts(scanId);

            portRanges = Lists.newArrayList();
            for (Recon.PortRange r : recon.portRanges(scanId)) {
                portRanges.add(ReconScanPortRangeResponse.create(r.range(), r.probed(), r.answered()));
            }
        } else {
            answeredHosts = Lists.newArrayList();
            for (Recon.AnsweredHost h : recon.findAnsweredHosts(scanId, 250)) {
                answeredHosts.add(ReconScanAnsweredHostResponse.create(h.address(), h.ports()));
            }
            answeredHostsTotal = recon.countAnsweredHosts(scanId);

            networks = Lists.newArrayList();
            for (Recon.TargetNetwork n : recon.targetNetworks(scanId, 100)) {
                networks.add(ReconScanTargetNetworkResponse.create(n.network(), n.hosts(), n.answeredHosts(), n.ports()));
            }
        }

        Map<DateTime, ReconScanTimelineBucketResponse> timeline = Maps.newLinkedHashMap();
        for (Recon.TimelineBucket b : recon.targetTimeline(scanId, scan.get().firstSeen(), scan.get().lastSeen())) {
            timeline.put(b.bucket(), ReconScanTimelineBucketResponse.create(b.open(), b.closed(), b.unanswered()));
        }
        String timelineBucket = new org.joda.time.Duration(scan.get().firstSeen(), scan.get().lastSeen())
                .getStandardHours() <= 24 ? "minute" : "hour";

        return Response.ok(ReconScanSummaryResponse.create(
                answeredPorts, answeredPortsTotal, unansweredPorts, unansweredPortsTotal, answeredHosts, answeredHostsTotal,
                timeline, timelineBucket, networks, portRanges
        )).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{scan_uuid}")
    @Operation(operationId = "deleteReconScan", summary = "Delete a scan",
            description = "Deletes a scan with all of its targets. The scan is detected again as a new scan if "
                    + "the device keeps probing. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Scan deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Scan, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response delete(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Scan UUID.") @PathParam("scan_uuid") UUID scanUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Recon recon = nzyme.getEthernet().recon();

        if (recon.findScan(scanUuid, organizationId, tenantId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        recon.deleteScan(scanUuid, organizationId, tenantId);

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "deleteAllReconScans", summary = "Delete all scans of a tenant",
            description = "Deletes every scan of the tenant with all targets. Exclusions and the detection "
                    + "configuration are kept. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Scans deleted.",
            content = @Content(schema = @Schema(implementation = ReconScansDeletedResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteAll(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        int deleted = nzyme.getEthernet().recon().deleteAllScans(organizationId, tenantId);

        return Response.ok(ReconScansDeletedResponse.create(deleted)).build();
    }

    private static final RegistryKey[] CONFIGURATION_KEYS = {
            ReconRegistryKeys.DETECTION_ENABLED,
            ReconRegistryKeys.INCLUDE_EXTERNAL_SOURCES,
            ReconRegistryKeys.INCLUDE_EXTERNAL_TARGETS,
            ReconRegistryKeys.DETECTION_WINDOW_MINUTES,
            ReconRegistryKeys.PORT_SCAN_MINIMUM_PORTS,
            ReconRegistryKeys.HOST_SWEEP_MINIMUM_HOSTS,
            ReconRegistryKeys.ARP_SWEEP_MINIMUM_HOSTS,
            ReconRegistryKeys.MINIMUM_FAILURE_PERCENT,
            ReconRegistryKeys.SCAN_RETENTION_TIME_DAYS,
            ReconRegistryKeys.SLOW_SCAN_ENABLED,
            ReconRegistryKeys.SLOW_SCAN_MEMORY_DAYS,
            ReconRegistryKeys.SLOW_SCAN_MINIMUM_TARGETS,
            ReconRegistryKeys.SOURCE_TCP_ENABLED,
            ReconRegistryKeys.SOURCE_UDP_ENABLED,
            ReconRegistryKeys.SOURCE_ARP_ENABLED,
            ReconRegistryKeys.SOURCE_ICMP_ENABLED,
            ReconRegistryKeys.SOURCE_DNS_ENABLED,
            ReconRegistryKeys.FINGERPRINTING_ENABLED,
            ReconRegistryKeys.ALERT_ON_NEW_SCAN,
            ReconRegistryKeys.ALERT_ON_NEW_FINGERPRINTING
    };

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "findReconConfiguration", summary = "Get the scan detection configuration of a tenant",
            description = "Returns the scan detection settings of the tenant in three groups: detection "
                    + "parameters (window, thresholds, retention), enabled data sources and alerting. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = ReconConfigurationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response getConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<ConfigurationEntryResponse> detection = Lists.newArrayList();
        detection.add(booleanEntry(ReconRegistryKeys.DETECTION_ENABLED, "Scan detection enabled", organizationId, tenantId));
        detection.add(booleanEntry(ReconRegistryKeys.INCLUDE_EXTERNAL_SOURCES, "Include scans from external (non site-local) sources", organizationId, tenantId));
        detection.add(booleanEntry(ReconRegistryKeys.INCLUDE_EXTERNAL_TARGETS, "Include scans against external (non site-local) targets", organizationId, tenantId));
        detection.add(numberEntry(ReconRegistryKeys.DETECTION_WINDOW_MINUTES, "Detection window (Minutes)", organizationId, tenantId));
        detection.add(numberEntry(ReconRegistryKeys.PORT_SCAN_MINIMUM_PORTS, "Port scan: Minimum ports on one host", organizationId, tenantId));
        detection.add(numberEntry(ReconRegistryKeys.HOST_SWEEP_MINIMUM_HOSTS, "Host sweep: Minimum hosts", organizationId, tenantId));
        detection.add(numberEntry(ReconRegistryKeys.ARP_SWEEP_MINIMUM_HOSTS, "ARP sweep: Minimum hosts", organizationId, tenantId));
        detection.add(numberEntry(ReconRegistryKeys.MINIMUM_FAILURE_PERCENT, "Minimum share of unanswered or refused probes (Percent)", organizationId, tenantId));
        detection.add(numberEntry(ReconRegistryKeys.SCAN_RETENTION_TIME_DAYS, "Scan retention time (Days)", organizationId, tenantId));
        detection.add(booleanEntry(ReconRegistryKeys.SLOW_SCAN_ENABLED, "Slow scan detection enabled", organizationId, tenantId));
        detection.add(numberEntry(ReconRegistryKeys.SLOW_SCAN_MEMORY_DAYS, "Slow scan: Memory (Days)", organizationId, tenantId));
        detection.add(numberEntry(ReconRegistryKeys.SLOW_SCAN_MINIMUM_TARGETS, "Slow scan: Minimum unanswered or refused host and port pairs", organizationId, tenantId));

        List<ConfigurationEntryResponse> sources = Lists.newArrayList();
        sources.add(booleanEntry(ReconRegistryKeys.SOURCE_TCP_ENABLED, "TCP sessions and stateless probes", organizationId, tenantId));
        sources.add(booleanEntry(ReconRegistryKeys.SOURCE_UDP_ENABLED, "UDP conversations", organizationId, tenantId));
        sources.add(booleanEntry(ReconRegistryKeys.SOURCE_ARP_ENABLED, "ARP requests", organizationId, tenantId));
        sources.add(booleanEntry(ReconRegistryKeys.SOURCE_ICMP_ENABLED, "ICMP echo and discovery requests (ping sweeps)", organizationId, tenantId));
        sources.add(booleanEntry(ReconRegistryKeys.SOURCE_DNS_ENABLED, "DNS queries (reverse sweeps)", organizationId, tenantId));
        sources.add(booleanEntry(ReconRegistryKeys.FINGERPRINTING_ENABLED, "OS fingerprinting probes (ICMP, TCP, UDP)", organizationId, tenantId));

        List<ConfigurationEntryResponse> alerting = Lists.newArrayList();
        alerting.add(booleanEntry(ReconRegistryKeys.ALERT_ON_NEW_SCAN, "Alert on new scan", organizationId, tenantId));
        alerting.add(booleanEntry(ReconRegistryKeys.ALERT_ON_NEW_FINGERPRINTING, "Alert on new OS fingerprinting attempt", organizationId, tenantId));

        return Response.ok(ReconConfigurationResponse.create(detection, sources, alerting)).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "updateReconConfiguration", summary = "Update the scan detection configuration of a tenant",
            description = "Updates one or more scan detection settings of the tenant. Accepted keys are the keys "
                    + "returned by the configuration endpoint. Requires the ethernet_monitoring_manage feature "
                    + "permission.")
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The change contains a key that is not part of the scan detection configuration.", content = @Content)
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

    private static ReconScanCategory parseCategory(@Nullable String param) {
        if (param == null || param.isBlank()) {
            return ReconScanCategory.SCANS;
        }

        return ReconScanCategory.valueOf(param.toUpperCase());
    }

    // Absent means no tap filter; present but empty means no taps selected, which yields no scans.
    @Nullable
    private List<UUID> parseTapsFilter(SecurityContext sc, @Nullable String tapIds) {
        if (tapIds == null) {
            return null;
        }

        return parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
    }

    private ConfigurationEntryResponse booleanEntry(RegistryKey key, String title, UUID organizationId, UUID tenantId) {
        return ConfigurationEntryResponse.create(
                key.key(),
                title,
                nzyme.getEthernet().recon().booleanValue(key, organizationId, tenantId),
                ConfigurationEntryValueType.BOOLEAN,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                "ethernet-recon-config"
        );
    }

    private ConfigurationEntryResponse numberEntry(RegistryKey key, String title, UUID organizationId, UUID tenantId) {
        return ConfigurationEntryResponse.create(
                key.key(),
                title,
                nzyme.getEthernet().recon().intValue(key, organizationId, tenantId),
                ConfigurationEntryValueType.NUMBER,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                "ethernet-recon-config"
        );
    }

    private ReconScanDetailsResponse toResponse(ReconScanEntry scan,
                                                ReconConfiguration config,
                                                UUID organizationId,
                                                UUID tenantId,
                                                Map<ReconExclusionEntry, ReconExclusionMatcher> exclusions) {
        List<ReconExclusionReferenceResponse> matchingExclusions = Lists.newArrayList();
        for (ReconExclusionEntry exclusion : nzyme.getEthernet().recon().exclusions()
                .findMatchingExclusions(scan, exclusions)) {
            matchingExclusions.add(ReconExclusionReferenceResponse.create(exclusion.uuid(), exclusion.name()));
        }

        Map<String, Object> enrichment = null;
        if (scan.enrichment() != null) {
            try {
                enrichment = om.readValue(scan.enrichment(), new TypeReference<>() {});
            } catch (Exception e) {
                LOG.error("Could not parse enrichment of scan [{}].", scan.uuid(), e);
            }
        }

        ReconScanType type;
        String typeHumanReadable;
        boolean isPortScan;
        try {
            type = ReconScanType.valueOf(scan.type());
            typeHumanReadable = type.getHumanReadable();
            isPortScan = type.isPortScan();
        } catch (IllegalArgumentException e) {
            type = null;
            typeHumanReadable = scan.type();
            isPortScan = false;
        }

        L4Type l4Type = type == ReconScanType.UDP_PORT_SCAN || type == ReconScanType.UDP_HOST_SWEEP
                ? L4Type.UDP : L4Type.TCP;

        List<ReconScanTapResponse> taps = Lists.newArrayList();
        for (UUID tapUuid : scan.tapUuids()) {
            taps.add(ReconScanTapResponse.create(
                    tapUuid, nzyme.getTapManager().findTap(tapUuid).map(Tap::name).orElse(null)
            ));
        }

        // A slow scan stays open for the memory period, a regular scan for the detection window.
        int windowMinutes = type == ReconScanType.SLOW_SCAN
                ? config.slowScanMemoryDays() * 24 * 60 : config.detectionWindowMinutes();
        boolean recent = scan.lastSeen().isAfter(DateTime.now().minusMinutes(windowMinutes));

        return ReconScanDetailsResponse.create(
                scan.uuid(),
                scan.type(),
                typeHumanReadable,
                isPortScan,
                recent,
                windowMinutes,
                scan.sourceAddress(),
                addresses().addressResponse(scan.sourceAddress(), null, scan.sourceMac(), l4Type, organizationId, tenantId),
                scan.sourceMac(),
                addresses().macResponse(scan.sourceMac(), organizationId, tenantId),
                ReconDetector.sourceDescription(scan),
                scan.targetAddress(),
                addresses().addressResponse(scan.targetAddress(), null, null, l4Type, organizationId, tenantId),
                ReconDetector.targetDescription(scan),
                scan.hostCount(),
                scan.portCount(),
                scan.probeCount(),
                scan.openCount(),
                scan.closedCount(),
                scan.unansweredCount(),
                scan.targetsTruncated(),
                enrichment,
                taps,
                scan.firstSeen(),
                scan.lastSeen(),
                scan.createdAt(),
                scan.updatedAt(),
                matchingExclusions
        );
    }

}
