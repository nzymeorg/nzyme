package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.beacons.BeaconDetector;
import app.nzyme.core.ethernet.recon.Recon;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.recon.*;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/recon/hosts")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Recon Probed Hosts", description = "The hosts of a tenant that received connection attempts, seen "
        + "from the host's side: which ports were probed, how many of them never answered, and how many distinct "
        + "devices probed them. Built from the probe history that scan detection keeps for the slow scan memory "
        + "period, across all sources, so a scan spread over many devices shows up here even when no single device "
        + "crosses a scan threshold. Probes from devices that were already attributed to a detected scan are left "
        + "out unless requested.")
public class ReconProbedHostsResource extends TapDataHandlingResource {

    private static final int DETAILS_LIMIT = 500;

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findReconProbedHosts", summary = "List probed hosts of a tenant",
            description = "Returns the hosts that received connection attempts within the time range, with the "
                    + "number of distinct probing devices and probed and unanswered ports. Every host with any "
                    + "probe qualifies, so callers usually filter by probing devices and unanswered ports, as the "
                    + "web interface does by default. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Probed hosts found.",
            content = @Content(schema = @Schema(implementation = ReconProbedHostsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker. Cannot reach further back than the slow scan memory of the tenant.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Fields: address, source_count, port_count, failed_port_count.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Also count probes from devices that were attributed to a detected scan. Defaults to false, which leaves out everything a detected scan already explains.") @QueryParam("include_scanners") boolean includeScanners,
                            @Parameter(description = "Sorting column. Defaults to the number of unanswered ports.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Recon.ProbedHostOrderColumn orderColumn = Recon.ProbedHostOrderColumn.FAILED_PORT_COUNT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Recon.ProbedHostOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        Recon recon = nzyme.getEthernet().recon();
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);

        long total = recon.countProbedHosts(organizationId, tenantId, timeRange, includeScanners, filters);
        List<ReconProbedHostResponse> hosts = Lists.newArrayList();
        for (Recon.ProbedHost host : recon.findProbedHosts(
                organizationId, tenantId, timeRange, includeScanners, filters, orderColumn, orderDirection, limit, offset)) {
            hosts.add(toResponse(host, addresses, organizationId, tenantId));
        }

        return Response.ok(ReconProbedHostsListResponse.create(total, hosts)).build();
    }

    @GET
    @Path("/show/{address}")
    @Operation(operationId = "findReconProbedHost", summary = "Get a probed host",
            description = "Returns one probed host with the devices that probed it and the ports they probed, "
                    + "within the time range.")
    @ApiResponse(responseCode = "200", description = "Probed host found.",
            content = @Content(schema = @Schema(implementation = ReconProbedHostDetailsResponse.class)))
    @ApiResponse(responseCode = "400", description = "The address is not a valid IP address.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Host, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "Also include probes from devices that were attributed to a detected scan. Defaults to false.") @QueryParam("include_scanners") boolean includeScanners,
                            @Parameter(description = "IP address of the host.") @PathParam("address") String address) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!BeaconDetector.isValidAddress(address)) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        Recon recon = nzyme.getEthernet().recon();
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);

        Optional<Recon.ProbedHost> host = recon.findProbedHost(organizationId, tenantId, address, timeRange, includeScanners);
        if (host.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<ReconProbedHostSourceResponse> sources = Lists.newArrayList();
        for (Recon.ProbedHostSource source : recon.findProbedHostSources(
                organizationId, tenantId, address, timeRange, includeScanners, DETAILS_LIMIT)) {
            sources.add(ReconProbedHostSourceResponse.create(
                    source.address(),
                    addresses.addressResponse(source.address(), null, source.mac(), L4Type.TCP, organizationId, tenantId),
                    source.mac(),
                    addresses.macResponse(source.mac(), organizationId, tenantId),
                    source.scanner(),
                    source.l4Types(),
                    source.portCount(),
                    source.failedPortCount(),
                    source.firstSeen(),
                    source.lastSeen()
            ));
        }

        List<ReconProbedHostPortResponse> ports = Lists.newArrayList();
        for (Recon.ProbedHostPort port : recon.findProbedHostPorts(
                organizationId, tenantId, address, timeRange, includeScanners, DETAILS_LIMIT)) {
            ports.add(ReconProbedHostPortResponse.create(
                    port.l4Type(), port.port(), port.sourceCount(), port.answered(), port.firstSeen(), port.lastSeen()
            ));
        }

        return Response.ok(ReconProbedHostDetailsResponse.create(
                toResponse(host.get(), addresses, organizationId, tenantId), sources, ports
        )).build();
    }

    private static ReconProbedHostResponse toResponse(Recon.ProbedHost host,
                                                      ReconAddressResponses addresses,
                                                      UUID organizationId,
                                                      UUID tenantId) {
        return ReconProbedHostResponse.create(
                host.address(),
                addresses.addressResponse(host.address(), null, null, L4Type.TCP, organizationId, tenantId),
                host.sourceCount(),
                host.portCount(),
                host.failedPortCount(),
                host.pairCount(),
                host.firstSeen(),
                host.lastSeen()
        );
    }

}
