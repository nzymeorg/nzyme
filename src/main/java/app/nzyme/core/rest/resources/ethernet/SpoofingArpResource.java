package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.spoofing.Spoofing;
import app.nzyme.core.ethernet.spoofing.SpoofingConfiguration;
import app.nzyme.core.ethernet.spoofing.SpoofingExclusionMatcher;
import app.nzyme.core.ethernet.spoofing.db.ArpSpoofingFindingEntry;
import app.nzyme.core.ethernet.spoofing.db.ArpSpoofingVictimEntry;
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

import java.util.*;
import java.util.stream.Collectors;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/spoofing/arp")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Spoofing: ARP", description = "ARP poisoning findings of a tenant. A finding is one address that a "
        + "device (the spoofer) claimed with its own MAC address while the legitimate owner of the address was "
        + "still claiming it as well. It carries the devices that received the spoofer's replies, which are the "
        + "devices whose ARP cache may now point at the spoofer. Findings are built from the ARP packets of all "
        + "taps of the tenant.")
public class SpoofingArpResource extends TapDataHandlingResource {

    private static final String MANAGE_PERMISSION = "ethernet_monitoring_manage";

    private static final int VICTIMS_LIMIT = 500;

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findArpSpoofingFindings", summary = "List ARP poisoning findings of a tenant",
            description = "Returns the findings of the tenant that were active at any point inside the time range, "
                    + "most recently seen first by default. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Findings found.",
            content = @Content(schema = @Schema(implementation = ArpSpoofingFindingsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Fields: address, spoofer_mac, owner_mac, claim_count, unsolicited_count, victim_count.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker. A finding matches when it was active at any point inside the range.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "Sorting column: ADDRESS, SPOOFER_MAC, OWNER_MAC, CLAIM_COUNT, UNSOLICITED_COUNT, VICTIM_COUNT, FIRST_SEEN or LAST_SEEN. Defaults to LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        Spoofing.ArpOrderColumn orderColumn = Spoofing.ArpOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Spoofing.ArpOrderColumn.valueOf(orderColumnParam.toUpperCase());
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

        long total = spoofing.countArpFindings(filters, timeRange, organizationId, tenantId);
        List<ArpSpoofingFindingResponse> findings = Lists.newArrayList();
        for (ArpSpoofingFindingEntry finding : spoofing.findAllArpFindings(
                filters, timeRange, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            findings.add(toResponse(finding, config, exclusions, addresses, organizationId, tenantId));
        }

        return Response.ok(ArpSpoofingFindingsListResponse.create(total, findings)).build();
    }

    @GET
    @Path("/histogram/active")
    @Operation(operationId = "findActiveArpSpoofingFindingsHistogram", summary = "Get the number of active ARP poisoning findings over time",
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
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().spoofing().activeArpFindingsHistogram(
                filters, organizationId, tenantId, timeRange, bucketing)) {
            histogram.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(histogram).build();
    }

    @GET
    @Path("/show/{finding_uuid}")
    @Operation(operationId = "findArpSpoofingFinding", summary = "Get an ARP poisoning finding",
            description = "Returns one finding with the devices that received the spoofer's replies and a timeline "
                    + "of who claimed the address, built from the retained ARP packets.")
    @ApiResponse(responseCode = "200", description = "Finding found.",
            content = @Content(schema = @Schema(implementation = ArpSpoofingFindingDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Finding, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Finding UUID.") @PathParam("finding_uuid") UUID findingUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Spoofing spoofing = nzyme.getEthernet().spoofing();

        Optional<ArpSpoofingFindingEntry> finding = spoofing.findArpFinding(findingUuid, organizationId, tenantId);
        if (finding.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        SpoofingConfiguration config = spoofing.getConfiguration(organizationId, tenantId);
        Map<SpoofingExclusionEntry, SpoofingExclusionMatcher> exclusions = spoofing.exclusions()
                .compileEnabled(organizationId, tenantId);
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);

        List<ArpSpoofingVictimResponse> victims = Lists.newArrayList();
        for (ArpSpoofingVictimEntry victim : spoofing.findArpVictims(finding.get().id(), VICTIMS_LIMIT)) {
            victims.add(ArpSpoofingVictimResponse.create(
                    victim.mac(),
                    addresses.macResponse(victim.mac(), organizationId, tenantId),
                    victim.address(),
                    victim.address() == null ? null
                            : addresses.addressResponse(victim.address(), null, victim.mac(), L4Type.TCP, organizationId, tenantId),
                    victim.packetCount(),
                    victim.unsolicitedCount(),
                    victim.firstSeen(),
                    victim.lastSeen()
            ));
        }

        // The timeline spans the finding, padded so that the claims around its edges are visible.
        DateTime from = finding.get().firstSeen().minusMinutes(config.arpDetectionWindowMinutes());
        DateTime to = finding.get().lastSeen().plusMinutes(config.arpDetectionWindowMinutes());
        TimeRange timelineRange = TimeRange.create(from, to, false);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timelineRange);

        List<UUID> taps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)
                .stream().map(Tap::uuid).collect(Collectors.toList());

        Map<DateTime, long[]> timeline = new TreeMap<>();
        for (Spoofing.ClaimTimelineBucket bucket : spoofing.arpClaimTimeline(finding.get(), taps, from, to, bucketing)) {
            long[] counts = timeline.computeIfAbsent(bucket.bucket(), k -> new long[2]);
            if (bucket.mac().equalsIgnoreCase(finding.get().spooferMac())) {
                counts[0] += bucket.count();
            } else {
                counts[1] += bucket.count();
            }
        }

        List<ArpSpoofingClaimBucketResponse> claimTimeline = Lists.newArrayList();
        for (Map.Entry<DateTime, long[]> entry : timeline.entrySet()) {
            claimTimeline.add(ArpSpoofingClaimBucketResponse.create(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }

        return Response.ok(ArpSpoofingFindingDetailsResponse.create(
                toResponse(finding.get(), config, exclusions, addresses, organizationId, tenantId),
                victims,
                claimTimeline
        )).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{finding_uuid}")
    @Operation(operationId = "deleteArpSpoofingFinding", summary = "Delete an ARP poisoning finding",
            description = "Deletes a finding with its victims. The finding is detected again as a new finding if "
                    + "the device keeps claiming the address. Requires the ethernet_monitoring_manage feature permission.")
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

        if (spoofing.findArpFinding(findingUuid, organizationId, tenantId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        spoofing.deleteArpFinding(findingUuid, organizationId, tenantId);

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "deleteAllArpSpoofingFindings", summary = "Delete all ARP poisoning findings of a tenant",
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

        int deleted = nzyme.getEthernet().spoofing().deleteAllArpFindings(organizationId, tenantId);

        return Response.ok(SpoofingFindingsDeletedResponse.create(deleted)).build();
    }

    private ArpSpoofingFindingResponse toResponse(ArpSpoofingFindingEntry finding,
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

        boolean recent = finding.lastSeen().isAfter(DateTime.now().minusMinutes(config.arpDetectionWindowMinutes()));

        return ArpSpoofingFindingResponse.create(
                finding.uuid(),
                finding.address(),
                addresses.addressResponse(finding.address(), null, finding.ownerMac(), L4Type.TCP, organizationId, tenantId),
                finding.spooferMac(),
                addresses.macResponse(finding.spooferMac(), organizationId, tenantId),
                finding.ownerMac(),
                addresses.macResponse(finding.ownerMac(), organizationId, tenantId),
                finding.claimCount(),
                finding.replyCount(),
                finding.unsolicitedCount(),
                finding.broadcastCount(),
                finding.ownerClaimCount(),
                finding.victimCount(),
                recent,
                config.arpDetectionWindowMinutes(),
                taps,
                matchingExclusions,
                finding.firstSeen(),
                finding.lastSeen()
        );
    }

}
