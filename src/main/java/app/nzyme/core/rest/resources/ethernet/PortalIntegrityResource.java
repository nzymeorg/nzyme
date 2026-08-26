package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.portalintegrity.PortalIntegrity;
import app.nzyme.core.ethernet.portalintegrity.db.PortalIntegrityReportEntry;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.portalintegrity.PortalIntegrityReportDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.portalintegrity.PortalIntegrityReportsListResponse;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/portalintegrity")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class PortalIntegrityResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/reports")
    public Response allReports(@Context SecurityContext sc,
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
                    report.probedAt()
            ));
        }

        return Response.ok(PortalIntegrityReportsListResponse.create(total, reports)).build();
    }

}
