package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.portalintegrity.PortalExpectation;
import app.nzyme.core.ethernet.portalintegrity.PortalIntegrity;
import app.nzyme.core.ethernet.portalintegrity.PortalIntegrityJudge;
import app.nzyme.core.ethernet.portalintegrity.PortalProbeConfigurations;
import app.nzyme.core.ethernet.portalintegrity.PortalUrls;
import app.nzyme.core.ethernet.portalintegrity.db.PortalIntegrityControlUrlEntry;
import app.nzyme.core.ethernet.portalintegrity.db.PortalIntegrityReportEntry;
import app.nzyme.core.ethernet.portalintegrity.db.PortalIntegrityReportHopEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.requests.PortalIntegrityExpectationRequest;
import app.nzyme.core.rest.requests.PortalIntegrityExpectedValuesRequest;
import app.nzyme.core.rest.responses.ethernet.portalintegrity.PortalIntegrityControlUrlResponse;
import app.nzyme.core.rest.responses.ethernet.portalintegrity.PortalIntegrityControlUrlsListResponse;
import app.nzyme.core.rest.responses.ethernet.portalintegrity.PortalIntegrityObservedOutcomeResponse;
import app.nzyme.core.rest.responses.ethernet.portalintegrity.PortalIntegrityObservedStateResponse;
import app.nzyme.core.rest.responses.ethernet.portalintegrity.PortalIntegrityReportDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.portalintegrity.PortalIntegrityReportHopDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.portalintegrity.PortalIntegrityReportsListResponse;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
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
import com.google.common.collect.Maps;
import org.apache.commons.compress.utils.Lists;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;


@Path("/api/ethernet/portalintegrity")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Portal Integrity", description = "Portal integrity reports record what a tap received when it "
        + "probed a control URL, including every redirect hop. Use them to detect captive portals, redirects and "
        + "tampered responses on a network. Every control URL a probe requests is kept with what its last report "
        + "showed and can be given an expectation: a captive portal, a certain captive portal, or none. A last "
        + "report that does not meet the expectation raises an alert.")
public class PortalIntegrityResource extends TapDataHandlingResource {

    private static final String MANAGE_PERMISSION = "ethernet_monitoring_manage";

    private static final int MAX_PAGE_SIZE = 250;

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
                               @Parameter(description = "Comma separated list of tap UUIDs to include. Use * for all taps the user can access. Without the parameter, no taps are selected and the result is empty.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFilters(filtersParameter, sc);

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
                              @Parameter(description = "Comma separated list of tap UUIDs to include. Use * for all taps the user can access. Without the parameter, no taps are selected and the result is empty.") @QueryParam("taps") String tapIds) {
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

    @GET
    @Path("/controlurls")
    @Operation(operationId = "findPortalIntegrityControlUrls", summary = "List the control URLs of portal integrity probes",
            description = "Returns every control URL the probes of the selected taps have requested, per IP version, "
                    + "with the expectation, what the last report showed and how that compares to the expectation. "
                    + "Sorted by probe name. At most " + PortalIntegrity.MAX_CONTROL_URLS_PER_TAP + " control URLs "
                    + "are recorded per tap. Results are paginated with a page size of up to " + MAX_PAGE_SIZE + ".")
    @ApiResponse(responseCode = "200", description = "Control URLs found.",
            content = @Content(schema = @Schema(implementation = PortalIntegrityControlUrlsListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response allControlUrls(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                   @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                   @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                   @Parameter(description = "Comma separated list of tap UUIDs to include. Use * for all taps the user can access. Without the parameter, no taps are selected and the result is empty.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        PortalIntegrity portalIntegrity = nzyme.getEthernet().portalIntegrity();
        TapsOfTenant tapsOfTenant = tapsOfTenant(organizationId, tenantId);
        Map<UUID, String> tapNames = tapsOfTenant.names();

        long total = portalIntegrity.countAllControlUrls(organizationId, tenantId, taps);
        List<PortalIntegrityControlUrlResponse> controlUrls = Lists.newArrayList();
        for (PortalIntegrityControlUrlEntry entry : portalIntegrity.findAllControlUrls(organizationId, tenantId, taps,
                Math.min(Math.max(limit, 1), MAX_PAGE_SIZE), Math.max(offset, 0))) {
            controlUrls.add(toResponse(entry, tapsOfTenant));
        }

        List<String> tapsAtLimit = Lists.newArrayList();
        for (UUID tap : portalIntegrity.findTapsAtControlUrlLimit(taps)) {
            if (tapNames.containsKey(tap)) {
                tapsAtLimit.add(tapNames.get(tap));
            }
        }

        return Response.ok(PortalIntegrityControlUrlsListResponse.create(
                total, controlUrls, PortalIntegrity.MAX_CONTROL_URLS_PER_TAP, tapsAtLimit
        )).build();
    }

    @GET
    @Path("/controlurls/show/{uuid}")
    @Operation(operationId = "findPortalIntegrityControlUrl", summary = "Get a control URL of a portal integrity probe",
            description = "Returns one control URL with its expectation and what its last report showed.")
    @ApiResponse(responseCode = "200", description = "Control URL found.",
            content = @Content(schema = @Schema(implementation = PortalIntegrityControlUrlResponse.class)))
    @ApiResponse(responseCode = "404", description = "Control URL, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response oneControlUrl(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Control URL UUID.") @PathParam("uuid") UUID uuid,
                                  @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        Optional<PortalIntegrityControlUrlEntry> entry = findAccessibleControlUrl(sc, uuid, organizationId, tenantId);
        if (entry.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(toResponse(entry.get(), tapsOfTenant(organizationId, tenantId))).build();
    }

    @GET
    @Path("/controlurls/show/{uuid}/observed")
    @Operation(operationId = "findPortalIntegrityObservedState", summary = "Get what the reports of a control URL showed",
            description = "Summarizes the reports of a control URL in the time range: every captive portal the probe "
                    + "was presented, by final URL, address and certificate, whether requests were not intercepted, "
                    + "and how many failed. Each outcome is compared to the current expectation, so the values of "
                    + "an outcome that is not expected yet can be taken over with the expectation endpoint. Looks at "
                    + "the latest " + PortalIntegrity.MAX_OBSERVED_REPORTS + " reports the probe sent for the "
                    + "control URL in the time range, over both IP versions together, and returns the "
                    + PortalIntegrity.MAX_OBSERVED_OUTCOMES + " outcomes seen last, next to the failed requests. "
                    + "The response says if either limit was reached.")
    @ApiResponse(responseCode = "200", description = "Observed state found.",
            content = @Content(schema = @Schema(implementation = PortalIntegrityObservedStateResponse.class)))
    @ApiResponse(responseCode = "404", description = "Control URL, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The reports of the time range took too long to read. The response body says so.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response observedState(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Control URL UUID.") @PathParam("uuid") UUID uuid,
                                  @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                  @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        Optional<PortalIntegrityControlUrlEntry> entry = findAccessibleControlUrl(sc, uuid, organizationId, tenantId);
        if (entry.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        PortalIntegrity.ObservedState state;
        try {
            state = nzyme.getEthernet().portalIntegrity().findObservedState(entry.get(), timeRange);
        } catch (PortalIntegrity.ObservedStateTimeoutException e) {
            return unprocessable("The reports of this time range took too long to read. Select a shorter one.");
        }

        List<PortalIntegrityObservedOutcomeResponse> outcomes = Lists.newArrayList();
        for (PortalIntegrity.ObservedOutcome outcome : state.outcomes()) {
            PortalIntegrityJudge.Judgement judgement = PortalIntegrityJudge.judge(
                    entry.get().expectation(),
                    entry.get().expectedFinalUrls(),
                    entry.get().expectedAddresses(),
                    entry.get().expectedCertificates(),
                    outcome.observation()
            );

            outcomes.add(PortalIntegrityObservedOutcomeResponse.create(
                    outcome.kind().name(),
                    outcome.observation().finalUrl(),
                    outcome.observation().finalAddress(),
                    outcome.observation().finalCertificate(),
                    outcome.observation().error(),
                    outcome.reportCount(),
                    outcome.firstSeen(),
                    outcome.lastSeen(),
                    judgement.result().name(),
                    judgement.reasons().stream().map(Enum::name).toList()
            ));
        }

        return Response.ok(PortalIntegrityObservedStateResponse.create(
                state.reportCount(),
                PortalIntegrity.MAX_OBSERVED_REPORTS,
                state.reportsLimited(),
                outcomes,
                PortalIntegrity.MAX_OBSERVED_OUTCOMES,
                state.outcomesLimited()
        )).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/controlurls/organization/{organization_id}/tenant/{tenant_id}/show/{uuid}/expectation")
    @Operation(operationId = "setPortalIntegrityExpectation", summary = "Set what a probe is expected to see for a control URL",
            description = "Sets the expectation and replaces the three lists that describe the expected portal. With "
                    + "PORTAL, a last report without a captive portal, or with one that is not in the lists, raises "
                    + "an alert. With NO_PORTAL, a last report with any captive portal raises an alert. With either, "
                    + "a failed request raises an alert. NOT_MONITORED raises none. The lists take up to "
                    + PortalIntegrity.MAX_EXPECTED_VALUES + " values each. Takes effect within a minute. Requires "
                    + "the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Expectation set.",
            content = @Content(schema = @Schema(implementation = PortalIntegrityControlUrlResponse.class)))
    @ApiResponse(responseCode = "404", description = "Control URL, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "Unknown expectation or invalid value. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response setExpectation(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                   @Parameter(description = "Control URL UUID.") @PathParam("uuid") UUID uuid,
                                   @RequestBody(description = "The expectation.", required = true, content = @Content(mediaType = "application/json"))
                                   PortalIntegrityExpectationRequest req) {
        Optional<PortalIntegrityControlUrlEntry> entry = findAccessibleControlUrl(sc, uuid, organizationId, tenantId);
        if (entry.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (req == null) {
            return unprocessable("No expectation given.");
        }

        PortalExpectation expectation;
        try {
            expectation = PortalExpectation.valueOf(req.expectation());
        } catch (IllegalArgumentException | NullPointerException e) {
            return unprocessable("Unknown expectation.");
        }

        List<String> urls = Lists.newArrayList();
        List<String> addresses = Lists.newArrayList();
        List<String> certificates = Lists.newArrayList();
        Optional<String> invalid = normalize(req.expectedFinalUrls(), "URL", PortalIntegrityResource::normalizeUrl, urls)
                .or(() -> normalize(req.expectedAddresses(), "IP address", PortalIntegrityResource::normalizeAddress, addresses))
                .or(() -> normalize(req.expectedCertificates(), "SHA-256 certificate fingerprint",
                        PortalUrls::normalizeCertificate, certificates));
        if (invalid.isPresent()) {
            return unprocessable(invalid.get());
        }

        if (expectation == PortalExpectation.PORTAL && urls.isEmpty()) {
            return unprocessable("An expected portal needs at least one final URL.");
        }

        PortalIntegrity portalIntegrity = nzyme.getEthernet().portalIntegrity();
        portalIntegrity.setExpectation(entry.get().id(), expectation, urls, addresses, certificates);

        return portalIntegrity.findControlUrl(uuid, organizationId, tenantId)
                .map(updated -> Response.ok(toResponse(updated, tapsOfTenant(organizationId, tenantId))).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/controlurls/organization/{organization_id}/tenant/{tenant_id}/show/{uuid}/expected")
    @Operation(operationId = "addPortalIntegrityExpectedValues", summary = "Add values to the expected portal of a control URL",
            description = "Adds a final URL, an address or a certificate fingerprint to the lists that describe the "
                    + "expected portal, for example the values of a portal that raised an alert and turned out to "
                    + "be legitimate. Values that are already expected are ignored. Only for a control URL that "
                    + "expects a portal. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Values added.",
            content = @Content(schema = @Schema(implementation = PortalIntegrityControlUrlResponse.class)))
    @ApiResponse(responseCode = "404", description = "Control URL, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "No or invalid value, the control URL does not expect a portal, or a list is full. The response body contains the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response addExpectedValues(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                      @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                      @Parameter(description = "Control URL UUID.") @PathParam("uuid") UUID uuid,
                                      @RequestBody(description = "The values to add.", required = true, content = @Content(mediaType = "application/json"))
                                      PortalIntegrityExpectedValuesRequest req) {
        Optional<PortalIntegrityControlUrlEntry> entry = findAccessibleControlUrl(sc, uuid, organizationId, tenantId);
        if (entry.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (req == null || (req.finalUrl() == null && req.address() == null && req.certificate() == null)) {
            return unprocessable("No value given.");
        }

        if (entry.get().expectation() != PortalExpectation.PORTAL) {
            return unprocessable("The control URL does not expect a portal.");
        }

        List<String> urls = Lists.newArrayList();
        List<String> addresses = Lists.newArrayList();
        List<String> certificates = Lists.newArrayList();
        Optional<String> invalid = normalize(single(req.finalUrl()), "URL", PortalIntegrityResource::normalizeUrl, urls)
                .or(() -> normalize(single(req.address()), "IP address", PortalIntegrityResource::normalizeAddress, addresses))
                .or(() -> normalize(single(req.certificate()), "SHA-256 certificate fingerprint",
                        PortalUrls::normalizeCertificate, certificates));
        if (invalid.isPresent()) {
            return unprocessable(invalid.get());
        }

        PortalIntegrity portalIntegrity = nzyme.getEthernet().portalIntegrity();
        if (!portalIntegrity.addExpectedValues(entry.get().id(), first(urls), first(addresses), first(certificates))) {
            return unprocessable("A list of expected values is full. It takes up to "
                    + PortalIntegrity.MAX_EXPECTED_VALUES + " values.");
        }

        return portalIntegrity.findControlUrl(uuid, organizationId, tenantId)
                .map(updated -> Response.ok(toResponse(updated, tapsOfTenant(organizationId, tenantId))).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/controlurls/organization/{organization_id}/tenant/{tenant_id}/show/{uuid}")
    @Operation(operationId = "deletePortalIntegrityControlUrl", summary = "Forget a control URL of a portal integrity probe",
            description = "Removes a control URL with its expectation, for example after it was taken out of the "
                    + "probe configuration. Reports are kept. If the probe requests the URL again, it is recorded "
                    + "anew and not monitored. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Control URL deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Control URL, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteControlUrl(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                     @Parameter(description = "Control URL UUID.") @PathParam("uuid") UUID uuid) {
        Optional<PortalIntegrityControlUrlEntry> entry = findAccessibleControlUrl(sc, uuid, organizationId, tenantId);
        if (entry.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getEthernet().portalIntegrity().deleteControlUrl(entry.get().id());

        return Response.ok().build();
    }

    // Empty for a control URL of another tenant or of a tap the user has no access to.
    private Optional<PortalIntegrityControlUrlEntry> findAccessibleControlUrl(SecurityContext sc,
                                                                              UUID uuid,
                                                                              UUID organizationId,
                                                                              UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Optional.empty();
        }

        return nzyme.getEthernet().portalIntegrity().findControlUrl(uuid, organizationId, tenantId)
                .filter(entry -> tapIdAccessible(getAuthenticatedUser(sc), nzyme, entry.tapUuid()));
    }

    private record TapsOfTenant(Map<UUID, String> names, PortalProbeConfigurations probes, DateTime now) {}

    private TapsOfTenant tapsOfTenant(UUID organizationId, UUID tenantId) {
        Map<UUID, String> names = Maps.newHashMap();
        for (Tap tap : nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)) {
            names.put(tap.uuid(), tap.name());
        }

        return new TapsOfTenant(
                names,
                nzyme.getEthernet().portalIntegrity().findProbeConfigurations(organizationId, tenantId),
                nzyme.getDatabase().getDatabaseClock()
        );
    }

    private static PortalIntegrityControlUrlResponse toResponse(PortalIntegrityControlUrlEntry entry,
                                                                TapsOfTenant taps) {
        PortalIntegrityJudge.Status status = PortalIntegrityJudge.status(entry, taps.probes(), taps.now());

        return PortalIntegrityControlUrlResponse.create(
                entry.uuid(),
                entry.tapUuid(),
                taps.names().get(entry.tapUuid()),
                entry.probeName(),
                entry.controlUrl(),
                entry.ipVersion(),
                entry.expectation().name(),
                entry.expectedFinalUrls(),
                entry.expectedAddresses(),
                entry.expectedCertificates(),
                status.result().name(),
                status.reasons().stream().map(Enum::name).toList(),
                status.stale(),
                status.inTapConfiguration(),
                entry.lastReportUuid(),
                entry.lastReportAt(),
                entry.lastError(),
                entry.lastHopCount(),
                entry.lastPortalPresented(),
                entry.lastFinalUrl(),
                entry.lastFinalAddress(),
                entry.lastFinalCertificate(),
                entry.createdAt(),
                entry.updatedAt(),
                PortalIntegrity.MAX_EXPECTED_VALUES
        );
    }

    private static Optional<String> normalizeUrl(String url) {
        return PortalUrls.isHttpUrl(url) ? Optional.of(PortalUrls.withoutQuery(url)) : Optional.empty();
    }

    private static Optional<String> normalizeAddress(String address) {
        try {
            return PortalUrls.normalizeAddress(address);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // Fills the target with the normalized values. Returns what is wrong with the first value that is not valid.
    private static Optional<String> normalize(@Nullable List<String> values,
                                              String noun,
                                              Function<String, Optional<String>> normalizer,
                                              List<String> target) {
        if (values == null) {
            return Optional.empty();
        }

        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }

            Optional<String> normalized = value.length() > PortalUrls.MAX_URL_LENGTH
                    ? Optional.empty() : normalizer.apply(value);
            if (normalized.isEmpty()) {
                String shown = value.replaceAll("\\p{Cntrl}", "?");
                return Optional.of("Not a valid " + noun + ": " + (shown.length() > 100 ? shown.substring(0, 100) : shown));
            }

            if (!target.contains(normalized.get())) {
                target.add(normalized.get());
            }

            if (target.size() > PortalIntegrity.MAX_EXPECTED_VALUES) {
                return Optional.of("No more than " + PortalIntegrity.MAX_EXPECTED_VALUES + " values per list.");
            }
        }

        return Optional.empty();
    }

    @Nullable
    private static List<String> single(@Nullable String value) {
        return value == null ? null : List.of(value);
    }

    @Nullable
    private static String first(List<String> values) {
        return values.isEmpty() ? null : values.get(0);
    }

    private static Response unprocessable(String message) {
        return Response.status(422).entity(ErrorResponse.create(message)).build();
    }

}
