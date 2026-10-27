package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.dns.tunnels.DnsTunnelConfiguration;
import app.nzyme.core.ethernet.dns.tunnels.DnsTunnelExclusionMatcher;
import app.nzyme.core.ethernet.dns.tunnels.DnsTunnelRegistryKeys;
import app.nzyme.core.ethernet.dns.tunnels.DnsTunnels;
import app.nzyme.core.ethernet.dns.tunnels.db.DnsTunnelExclusionEntry;
import app.nzyme.core.ethernet.dns.tunnels.db.DnsTunnelFindingEntry;
import app.nzyme.core.registry.RegistryChangeValidator;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.requests.GenericConfigurationUpdateRequest;
import app.nzyme.core.rest.responses.ethernet.dnstunnels.*;
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
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/dns/tunnels")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "DNS Tunnels", description = "DNS tunnel findings of a tenant and the detection configuration. A "
        + "finding is one client that looked up many distinct names below one registered domain inside the "
        + "detection window, where the labels below the registered domain show at least one indicator of "
        + "tunneling: high entropy, long names or an unusual share of TXT, NULL, CNAME, MX, KEY or SRV queries. "
        + "Findings are built on the node from the DNS transactions recorded by all taps of the tenant.",
        externalDocs = @io.swagger.v3.oas.annotations.ExternalDocumentation(description = "DNS tunnels in the Nzyme documentation",
                url = "https://go.nzyme.org/ethernet-dns-tunnels"))
public class DnsTunnelsResource extends TapDataHandlingResource {

    private static final String MANAGE_PERMISSION = "ethernet_monitoring_manage";

    private static final RegistryKey[] CONFIGURATION_KEYS = {
            DnsTunnelRegistryKeys.DETECTION_ENABLED,
            DnsTunnelRegistryKeys.DETECTION_WINDOW_MINUTES,
            DnsTunnelRegistryKeys.MINIMUM_DISTINCT_NAMES,
            DnsTunnelRegistryKeys.ENTROPY_THRESHOLD_TENTHS,
            DnsTunnelRegistryKeys.NAME_LENGTH_THRESHOLD,
            DnsTunnelRegistryKeys.RECORD_TYPE_SHARE_THRESHOLD,
            DnsTunnelRegistryKeys.RETENTION_TIME_DAYS,
            DnsTunnelRegistryKeys.ALERT_ON_NEW
    };

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findDnsTunnelFindings", summary = "List DNS tunnel findings of a tenant",
            description = "Returns the findings of the tenant that were active at any point inside the time range, "
                    + "most recently seen first by default. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Findings found.",
            content = @Content(schema = @Schema(implementation = DnsTunnelFindingsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Fields: client_address, client_mac, domain, query_count, distinct_name_count, negative_count, indicator (equals or not equals one of HIGH_ENTROPY, LONG_NAMES, UNUSUAL_RECORD_TYPES).") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker. A finding matches when it was active at any point inside the range.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "Sorting column: CLIENT_ADDRESS, CLIENT_MAC, DOMAIN, QUERY_COUNT, DISTINCT_NAME_COUNT, NEGATIVE_COUNT, AVERAGE_ENTROPY, AVERAGE_NAME_LENGTH, FIRST_SEEN or LAST_SEEN. Defaults to LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        DnsTunnels.OrderColumn orderColumn = DnsTunnels.OrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = DnsTunnels.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        DnsTunnels tunnels = nzyme.getEthernet().dnsTunnels();
        DnsTunnelConfiguration config = tunnels.getConfiguration(organizationId, tenantId);
        Map<DnsTunnelExclusionEntry, DnsTunnelExclusionMatcher> exclusions = tunnels.exclusions()
                .compileEnabled(organizationId, tenantId);
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);

        long total = tunnels.countFindings(filters, timeRange, organizationId, tenantId);
        List<DnsTunnelFindingResponse> findings = Lists.newArrayList();
        for (DnsTunnelFindingEntry finding : tunnels.findAllFindings(
                filters, timeRange, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            findings.add(toResponse(finding, config, exclusions, addresses, organizationId, tenantId));
        }

        return Response.ok(DnsTunnelFindingsListResponse.create(total, findings)).build();
    }

    @GET
    @Path("/histogram/active")
    @Operation(operationId = "findActiveDnsTunnelFindingsHistogram", summary = "Get the number of active DNS tunnel findings over time",
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
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().dnsTunnels().activeFindingsHistogram(
                filters, organizationId, tenantId, timeRange, bucketing)) {
            histogram.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(histogram).build();
    }

    @GET
    @Path("/show/{finding_uuid}")
    @Operation(operationId = "findDnsTunnelFinding", summary = "Get a DNS tunnel finding",
            description = "Returns one finding with its indicators, statistics, record type breakdown and the "
                    + "sampled names.")
    @ApiResponse(responseCode = "200", description = "Finding found.",
            content = @Content(schema = @Schema(implementation = DnsTunnelFindingResponse.class)))
    @ApiResponse(responseCode = "404", description = "Finding, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Finding UUID.") @PathParam("finding_uuid") UUID findingUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DnsTunnels tunnels = nzyme.getEthernet().dnsTunnels();
        Optional<DnsTunnelFindingEntry> finding = tunnels.findFinding(findingUuid, organizationId, tenantId);

        if (finding.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DnsTunnelConfiguration config = tunnels.getConfiguration(organizationId, tenantId);
        Map<DnsTunnelExclusionEntry, DnsTunnelExclusionMatcher> exclusions = tunnels.exclusions()
                .compileEnabled(organizationId, tenantId);
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);

        return Response.ok(toResponse(finding.get(), config, exclusions, addresses, organizationId, tenantId)).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{finding_uuid}")
    @Operation(operationId = "deleteDnsTunnelFinding", summary = "Delete a DNS tunnel finding",
            description = "Deletes a finding. It is detected again as a new finding if the client keeps querying "
                    + "the domain. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Finding deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Finding, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response delete(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Finding UUID.") @PathParam("finding_uuid") UUID findingUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DnsTunnels tunnels = nzyme.getEthernet().dnsTunnels();
        if (tunnels.findFinding(findingUuid, organizationId, tenantId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        tunnels.deleteFinding(findingUuid, organizationId, tenantId);

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "deleteAllDnsTunnelFindings", summary = "Delete all DNS tunnel findings of a tenant",
            description = "Deletes every finding of the tenant. Exclusions and the detection configuration are "
                    + "kept. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Findings deleted.",
            content = @Content(schema = @Schema(implementation = DnsTunnelFindingsDeletedResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteAll(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        int deleted = nzyme.getEthernet().dnsTunnels().deleteAllFindings(organizationId, tenantId);

        return Response.ok(DnsTunnelFindingsDeletedResponse.create(deleted)).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "findDnsTunnelConfiguration", summary = "Get the DNS tunnel detection configuration of a tenant",
            description = "Returns the DNS tunnel detection settings of the tenant as one section with its "
                    + "detection parameters and alerting settings. The entropy threshold is in tenths of a bit per "
                    + "character. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = DnsTunnelConfigurationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response getConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<DnsTunnelConfigurationSectionResponse> sections = Lists.newArrayList();
        sections.add(DnsTunnelConfigurationSectionResponse.create("dns_tunnels", "DNS Tunnels",
                List.of(
                        booleanEntry(DnsTunnelRegistryKeys.DETECTION_ENABLED, "Detection enabled", organizationId, tenantId),
                        numberEntry(DnsTunnelRegistryKeys.DETECTION_WINDOW_MINUTES, "Detection window (Minutes)", organizationId, tenantId),
                        numberEntry(DnsTunnelRegistryKeys.MINIMUM_DISTINCT_NAMES, "Minimum distinct names in window", organizationId, tenantId),
                        numberEntry(DnsTunnelRegistryKeys.ENTROPY_THRESHOLD_TENTHS, "Entropy threshold (Tenths of bits per character)", organizationId, tenantId),
                        numberEntry(DnsTunnelRegistryKeys.NAME_LENGTH_THRESHOLD, "Name length threshold (Characters)", organizationId, tenantId),
                        numberEntry(DnsTunnelRegistryKeys.RECORD_TYPE_SHARE_THRESHOLD, "Unusual record type share threshold (Percent)", organizationId, tenantId),
                        numberEntry(DnsTunnelRegistryKeys.RETENTION_TIME_DAYS, "Finding retention time (Days)", organizationId, tenantId)
                ),
                List.of(
                        booleanEntry(DnsTunnelRegistryKeys.ALERT_ON_NEW, "Alert on new DNS tunnel finding", organizationId, tenantId)
                )
        ));

        return Response.ok(DnsTunnelConfigurationResponse.create(sections)).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "updateDnsTunnelConfiguration", summary = "Update the DNS tunnel detection configuration of a tenant",
            description = "Updates one or more DNS tunnel detection settings of the tenant. Accepted keys are the "
                    + "keys returned by the configuration endpoint. Requires the ethernet_monitoring_manage feature "
                    + "permission.")
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The change is empty, contains an unknown key, or a value violates the constraints of its configuration key.", content = @Content)
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

    private ConfigurationEntryResponse booleanEntry(RegistryKey key, String title, UUID organizationId, UUID tenantId) {
        return ConfigurationEntryResponse.create(
                key.key(),
                title,
                nzyme.getEthernet().dnsTunnels().booleanValue(key, organizationId, tenantId),
                ConfigurationEntryValueType.BOOLEAN,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                "ethernet-dns-tunnels-config"
        );
    }

    private ConfigurationEntryResponse numberEntry(RegistryKey key, String title, UUID organizationId, UUID tenantId) {
        return ConfigurationEntryResponse.create(
                key.key(),
                title,
                nzyme.getEthernet().dnsTunnels().intValue(key, organizationId, tenantId),
                ConfigurationEntryValueType.NUMBER,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                "ethernet-dns-tunnels-config"
        );
    }

    private DnsTunnelFindingResponse toResponse(DnsTunnelFindingEntry finding,
                                                DnsTunnelConfiguration config,
                                                Map<DnsTunnelExclusionEntry, DnsTunnelExclusionMatcher> exclusions,
                                                ReconAddressResponses addresses,
                                                UUID organizationId,
                                                UUID tenantId) {
        List<DnsTunnelExclusionReferenceResponse> matchingExclusions = Lists.newArrayList();
        for (DnsTunnelExclusionEntry exclusion : nzyme.getEthernet().dnsTunnels().exclusions()
                .findMatchingExclusions(finding, exclusions)) {
            matchingExclusions.add(DnsTunnelExclusionReferenceResponse.create(exclusion.uuid(), exclusion.name()));
        }

        List<DnsTunnelTapResponse> taps = Lists.newArrayList();
        for (UUID tapUuid : finding.tapUuids()) {
            taps.add(DnsTunnelTapResponse.create(
                    tapUuid, nzyme.getTapManager().findTap(tapUuid).map(Tap::name).orElse(null)
            ));
        }

        boolean recent = finding.lastSeen().isAfter(DateTime.now().minusMinutes(config.detectionWindowMinutes()));

        return DnsTunnelFindingResponse.create(
                finding.uuid(),
                finding.clientAddress(),
                addresses.addressResponse(finding.clientAddress(), null, finding.clientMac(), L4Type.UDP, organizationId, tenantId),
                finding.clientMac(),
                finding.clientMac() == null ? null : addresses.macResponse(finding.clientMac(), organizationId, tenantId),
                finding.domain(),
                finding.serverAddresses(),
                finding.indicators(),
                finding.queryCount(),
                finding.distinctNameCount(),
                finding.negativeCount(),
                finding.averageEntropy(),
                finding.maximumEntropy(),
                finding.averageNameLength(),
                finding.maximumNameLength(),
                finding.recordTypes(),
                finding.names(),
                finding.nameCount(),
                recent,
                config.detectionWindowMinutes(),
                taps,
                matchingExclusions,
                finding.firstSeen(),
                finding.lastSeen()
        );
    }

}
