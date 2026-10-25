package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.beacons.BeaconDetector;
import app.nzyme.core.ethernet.recon.Recon;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.recon.*;
import app.nzyme.core.util.TimeRange;
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

@Path("/api/ethernet/recon/targets")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Recon Targets", description = "The probed hosts of a tenant, seen from the target's side: which ports "
        + "were probed, how many of them never answered, and how many distinct devices probed them. Built from "
        + "the probe history that scan detection keeps for the slow scan memory period, across all sources, so a "
        + "scan spread over many devices shows up here even when no single device crosses a scan threshold.")
public class ReconTargetsResource extends TapDataHandlingResource {

    private static final int DETAILS_LIMIT = 500;

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findReconTargets", summary = "List probed targets of a tenant",
            description = "Returns the hosts that were probed within the time range, with the number of distinct "
                    + "probing devices and probed and unanswered ports. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Targets found.",
            content = @Content(schema = @Schema(implementation = ReconTargetsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker. Cannot reach further back than the slow scan memory of the tenant.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "Sorting column. Defaults to the number of unanswered ports.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        Recon.ProbedTargetOrderColumn orderColumn = Recon.ProbedTargetOrderColumn.FAILED_PORT_COUNT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Recon.ProbedTargetOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        Recon recon = nzyme.getEthernet().recon();
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);

        long total = recon.countProbedTargets(organizationId, tenantId, timeRange);
        List<ReconTargetResponse> targets = Lists.newArrayList();
        for (Recon.ProbedTarget target : recon.findProbedTargets(
                organizationId, tenantId, timeRange, orderColumn, orderDirection, limit, offset)) {
            targets.add(toResponse(target, addresses, organizationId, tenantId));
        }

        return Response.ok(ReconTargetsListResponse.create(total, targets)).build();
    }

    @GET
    @Path("/show/{address}")
    @Operation(operationId = "findReconTarget", summary = "Get a probed target",
            description = "Returns one probed host with the devices that probed it and the ports they probed, "
                    + "within the time range.")
    @ApiResponse(responseCode = "200", description = "Target found.",
            content = @Content(schema = @Schema(implementation = ReconTargetDetailsResponse.class)))
    @ApiResponse(responseCode = "400", description = "The address is not a valid IP address.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Target, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "IP address of the target.") @PathParam("address") String address) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!BeaconDetector.isValidAddress(address)) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        Recon recon = nzyme.getEthernet().recon();
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);

        Optional<Recon.ProbedTarget> target = recon.findProbedTarget(organizationId, tenantId, address, timeRange);
        if (target.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<ReconTargetSourceResponse> sources = Lists.newArrayList();
        for (Recon.ProbedTargetSource source : recon.findProbedTargetSources(
                organizationId, tenantId, address, timeRange, DETAILS_LIMIT)) {
            sources.add(ReconTargetSourceResponse.create(
                    source.address(),
                    addresses.addressResponse(source.address(), null, source.mac(), L4Type.TCP, organizationId, tenantId),
                    source.mac(),
                    addresses.macResponse(source.mac(), organizationId, tenantId),
                    source.l4Types(),
                    source.portCount(),
                    source.failedPortCount(),
                    source.firstSeen(),
                    source.lastSeen()
            ));
        }

        List<ReconTargetPortResponse> ports = Lists.newArrayList();
        for (Recon.ProbedTargetPort port : recon.findProbedTargetPorts(
                organizationId, tenantId, address, timeRange, DETAILS_LIMIT)) {
            ports.add(ReconTargetPortResponse.create(
                    port.l4Type(), port.port(), port.sourceCount(), port.answered(), port.firstSeen(), port.lastSeen()
            ));
        }

        return Response.ok(ReconTargetDetailsResponse.create(
                toResponse(target.get(), addresses, organizationId, tenantId), sources, ports
        )).build();
    }

    private static ReconTargetResponse toResponse(Recon.ProbedTarget target,
                                                  ReconAddressResponses addresses,
                                                  UUID organizationId,
                                                  UUID tenantId) {
        return ReconTargetResponse.create(
                target.address(),
                addresses.addressResponse(target.address(), null, null, L4Type.TCP, organizationId, tenantId),
                target.sourceCount(),
                target.portCount(),
                target.failedPortCount(),
                target.pairCount(),
                target.firstSeen(),
                target.lastSeen()
        );
    }

}
