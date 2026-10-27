package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.spoofing.Spoofing;
import app.nzyme.core.ethernet.spoofing.SpoofingConfiguration;
import app.nzyme.core.ethernet.spoofing.SpoofingExclusionMatcher;
import app.nzyme.core.ethernet.spoofing.db.NameResolutionSpoofingFindingEntry;
import app.nzyme.core.ethernet.spoofing.db.NameResolutionSpoofingVictimEntry;
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
import java.util.stream.Collectors;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/spoofing/nameres")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Spoofing: Name Resolution", description = "Name resolution poisoning findings of a tenant. A finding is "
        + "one device (the responder) that answered LLMNR, NBT-NS or mDNS queries for more different names than a "
        + "device legitimately has, pointing them at its own address. It carries the names, the protocols, and the "
        + "clients that received the answers. Findings are built from the name resolution answers recorded by all "
        + "taps of the tenant.")
public class SpoofingNameResolutionResource extends TapDataHandlingResource {

    private static final String MANAGE_PERMISSION = "ethernet_monitoring_manage";

    private static final int VICTIMS_LIMIT = 500;

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findNameResolutionSpoofingFindings", summary = "List name resolution poisoning findings of a tenant",
            description = "Returns the findings of the tenant that were active at any point inside the time range, "
                    + "most recently seen first by default. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Findings found.",
            content = @Content(schema = @Schema(implementation = NameResolutionSpoofingFindingsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Fields: responder_mac, name_count, response_count, victim_count.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker. A finding matches when it was active at any point inside the range.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "Sorting column: RESPONDER_MAC, NAME_COUNT, RESPONSE_COUNT, VICTIM_COUNT, FIRST_SEEN or LAST_SEEN. Defaults to LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        Spoofing.NameResolutionOrderColumn orderColumn = Spoofing.NameResolutionOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Spoofing.NameResolutionOrderColumn.valueOf(orderColumnParam.toUpperCase());
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

        long total = spoofing.countNameResolutionFindings(filters, timeRange, organizationId, tenantId);
        List<NameResolutionSpoofingFindingResponse> findings = Lists.newArrayList();
        for (NameResolutionSpoofingFindingEntry finding : spoofing.findAllNameResolutionFindings(
                filters, timeRange, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            findings.add(toResponse(finding, config, exclusions, addresses, organizationId, tenantId));
        }

        return Response.ok(NameResolutionSpoofingFindingsListResponse.create(total, findings)).build();
    }

    @GET
    @Path("/histogram/active")
    @Operation(operationId = "findActiveNameResolutionSpoofingFindingsHistogram", summary = "Get the number of active name resolution poisoning findings over time",
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
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().spoofing().activeNameResolutionFindingsHistogram(
                filters, organizationId, tenantId, timeRange, bucketing)) {
            histogram.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(histogram).build();
    }

    @GET
    @Path("/show/{finding_uuid}")
    @Operation(operationId = "findNameResolutionSpoofingFinding", summary = "Get a name resolution poisoning finding",
            description = "Returns one finding with the names it answered and the clients that received the answers.")
    @ApiResponse(responseCode = "200", description = "Finding found.",
            content = @Content(schema = @Schema(implementation = NameResolutionSpoofingFindingDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Finding, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Finding UUID.") @PathParam("finding_uuid") UUID findingUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Spoofing spoofing = nzyme.getEthernet().spoofing();

        Optional<NameResolutionSpoofingFindingEntry> finding = spoofing.findNameResolutionFinding(findingUuid, organizationId, tenantId);
        if (finding.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        SpoofingConfiguration config = spoofing.getConfiguration(organizationId, tenantId);
        Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> exclusions = spoofing.exclusions()
                .compileEnabled(organizationId, tenantId);
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);

        List<NameResolutionSpoofingVictimResponse> victims = Lists.newArrayList();
        for (NameResolutionSpoofingVictimEntry victim : spoofing.findNameResolutionVictims(finding.get().id(), VICTIMS_LIMIT)) {
            victims.add(NameResolutionSpoofingVictimResponse.create(
                    victim.mac(),
                    addresses.macResponse(victim.mac(), organizationId, tenantId),
                    victim.address(),
                    victim.address() == null ? null
                            : addresses.addressResponse(victim.address(), null, victim.mac(), L4Type.UDP, organizationId, tenantId),
                    victim.names(),
                    victim.responseCount(),
                    victim.firstSeen(),
                    victim.lastSeen()
            ));
        }

        return Response.ok(NameResolutionSpoofingFindingDetailsResponse.create(
                toResponse(finding.get(), config, exclusions, addresses, organizationId, tenantId),
                victims
        )).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{finding_uuid}")
    @Operation(operationId = "deleteNameResolutionSpoofingFinding", summary = "Delete a name resolution poisoning finding",
            description = "Deletes a finding with its victims. The finding is detected again as a new finding if "
                    + "the device keeps answering name queries. Requires the ethernet_monitoring_manage feature permission.")
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

        if (spoofing.findNameResolutionFinding(findingUuid, organizationId, tenantId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        spoofing.deleteNameResolutionFinding(findingUuid, organizationId, tenantId);

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "deleteAllNameResolutionSpoofingFindings", summary = "Delete all name resolution poisoning findings of a tenant",
            description = "Deletes every finding of the tenant with all victims. Exclusions and the detection "
                    + "configuration are kept. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Findings deleted.",
            content = @Content(schema = @Schema(implementation = SpoofingFindingsDeletedResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteAll(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        int deleted = nzyme.getEthernet().spoofing().deleteAllNameResolutionFindings(organizationId, tenantId);

        return Response.ok(SpoofingFindingsDeletedResponse.create(deleted)).build();
    }

    @GET
    @Path("/responders")
    @Operation(operationId = "findNameResolutionResponders", summary = "List devices that answered name queries",
            description = "Returns every device that answered LLMNR, NBT-NS or mDNS queries on the taps of the "
                    + "tenant inside the time range, with how many different names it answered and how many of "
                    + "those it pointed at its own address. Most suspicious first. Built from the retained name "
                    + "resolution answers, which are kept for one day. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Responders found.",
            content = @Content(schema = @Schema(implementation = NameResolutionRespondersListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findResponders(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                   @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                   @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                   @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        List<UUID> taps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)
                .stream().map(Tap::uuid).collect(Collectors.toList());

        Spoofing spoofing = nzyme.getEthernet().spoofing();
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);

        long total = spoofing.countNameResolutionResponders(taps, timeRange);
        List<NameResolutionResponderResponse> responders = Lists.newArrayList();
        for (Spoofing.NameResolutionResponder r : spoofing.findNameResolutionResponders(taps, timeRange, limit, offset)) {
            responders.add(NameResolutionResponderResponse.create(
                    r.mac(),
                    addresses.macResponse(r.mac(), organizationId, tenantId),
                    r.addresses(),
                    r.protocols(),
                    r.nameCount(),
                    r.selfPointingNameCount(),
                    r.responseCount(),
                    r.unicastCount(),
                    r.namesSample(),
                    r.firstSeen(),
                    r.lastSeen()
            ));
        }

        return Response.ok(NameResolutionRespondersListResponse.create(total, responders)).build();
    }

    private NameResolutionSpoofingFindingResponse toResponse(NameResolutionSpoofingFindingEntry finding,
                                                             SpoofingConfiguration config,
                                                             Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> exclusions,
                                                             ReconAddressResponses addresses,
                                                             UUID organizationId,
                                                             UUID tenantId) {
        List<SpoofingExclusionReferenceResponse> matchingExclusions = Lists.newArrayList();
        for (SpoofingExclusionEntry exclusion : nzyme.getEthernet().spoofing().exclusions()
                .findMatchingExclusions(finding, exclusions)) {
            matchingExclusions.add(SpoofingExclusionReferenceResponse.create(exclusion.uuid(), exclusion.name()));
        }

        List<SpoofingTapResponse> taps = Lists.newArrayList();
        for (UUID tapUuid : finding.tapUuids()) {
            taps.add(SpoofingTapResponse.create(
                    tapUuid, nzyme.getTapManager().findTap(tapUuid).map(Tap::name).orElse(null)
            ));
        }

        boolean recent = finding.lastSeen().isAfter(DateTime.now().minusMinutes(config.nameresDetectionWindowMinutes()));

        return NameResolutionSpoofingFindingResponse.create(
                finding.uuid(),
                finding.responderMac(),
                addresses.macResponse(finding.responderMac(), organizationId, tenantId),
                finding.responderAddresses(),
                finding.protocols(),
                finding.names(),
                finding.nameCount(),
                finding.responseCount(),
                finding.unicastCount(),
                finding.victimCount(),
                recent,
                config.nameresDetectionWindowMinutes(),
                taps,
                matchingExclusions,
                finding.firstSeen(),
                finding.lastSeen()
        );
    }

}
