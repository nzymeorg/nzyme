package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.portalintegrity.PortalIntegrity;
import app.nzyme.core.ethernet.portalintegrity.db.PortalIntegrityReportEntry;
import app.nzyme.core.ethernet.portalintegrity.db.PortalIntegrityReportHopEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.portalintegrity.PortalIntegrityReportDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.portalintegrity.PortalIntegrityReportHopDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.portalintegrity.PortalIntegrityReportsListResponse;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
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
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/portalintegrity")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Portal Integrity", description = "Portal integrity reports record what a tap received when it "
        + "probed a control URL, including every redirect hop and a verdict with the reasons behind it. Use them to "
        + "detect captive portals, redirects and tampered responses on a network.")
public class PortalIntegrityResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/reports")
    @Operation(operationId = "findPortalIntegrityReports", summary = "List portal integrity reports",
            description = "Returns all portal integrity reports from the time range, newest probe first by default. "
                    + "Each report carries the control URL, the probing interface with its assigned address, "
                    + "gateway, DHCP server and DNS servers, and the verdict. The individual redirect hops are not "
                    + "included here, request a single report for those. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Reports found.",
            content = @Content(schema = @Schema(implementation = PortalIntegrityReportsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response allReports(@Parameter(hidden = true) @Context SecurityContext sc,
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

        PortalIntegrity.ReportOrderColumn orderColumn = PortalIntegrity.ReportOrderColumn.PROBED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = PortalIntegrity.ReportOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().portalIntegrity().countAllIntegrityReports(timeRange, filters, taps);
        List<PortalIntegrityReportDetailsResponse> reports = Lists.newArrayList();
        for (PortalIntegrityReportEntry report : nzyme.getEthernet().portalIntegrity()
                .findAllIntegrityReports(timeRange, filters, orderColumn, orderDirection, limit, offset, taps)) {
            reports.add(PortalIntegrityReportDetailsResponse.create(
                    report.uuid(),
                    report.controlUrl(),
                    report.probeInterface(),
                    report.probeMac(),
                    report.probeName(),
                    report.assignedAddress(),
                    report.gatewayAddress(),
                    report.dhcpServerAddress(),
                    report.dnsServers(),
                    report.hopCount(),
                    report.lastHopUrl(),
                    report.error(),
                    report.probedAt(),
                    null
            ));
        }

        return Response.ok(PortalIntegrityReportsListResponse.create(total, reports)).build();
    }


    @GET
    @Path("/reports/show/{uuid}")
    @Operation(operationId = "findPortalIntegrityReport", summary = "Get a portal integrity report",
            description = "Returns a single portal integrity report with every redirect hop the tap followed, "
                    + "including the resolved address, the TLS details and the response body hash of each hop.")
    @ApiResponse(responseCode = "200", description = "Report found.",
            content = @Content(schema = @Schema(implementation = PortalIntegrityReportDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Report not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response oneReport(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Report UUID.") @PathParam(("uuid")) UUID uuid,
                              @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                              @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<PortalIntegrityReportEntry> report = nzyme.getEthernet().portalIntegrity()
                .findOneIntegrityReport(uuid, taps);

        if (report.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<PortalIntegrityReportHopDetailsResponse> hops = Lists.newArrayList();
        for (PortalIntegrityReportHopEntry hop : nzyme.getEthernet().portalIntegrity()
                .findAllHopsOfIntegrityReport(report.get().uuid())) {
            hops.add(PortalIntegrityReportHopDetailsResponse.create(
                    hop.hopIndex(),
                    hop.url(),
                    RestHelpers.L4AddressDataToResponse(
                            nzyme, organizationId, tenantId, L4Type.TCP, hop.resolvedAddress()
                    ),
                    hop.status(),
                    hop.followedTo(),
                    hop.completeness(),
                    hop.raw(),
                    hop.bodySha256(),
                    hop.tls()
            ));
        }

        PortalIntegrityReportDetailsResponse response = PortalIntegrityReportDetailsResponse.create(
                report.get().uuid(),
                report.get().controlUrl(),
                report.get().probeInterface(),
                report.get().probeMac(),
                report.get().probeName(),
                report.get().assignedAddress(),
                report.get().gatewayAddress(),
                report.get().dhcpServerAddress(),
                report.get().dnsServers(),
                report.get().hopCount(),
                report.get().lastHopUrl(),
                report.get().error(),
                report.get().probedAt(),
                hops
        );

        return Response.ok(response).build();
    }

}
