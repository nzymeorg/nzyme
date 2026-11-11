package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.assets.identity.*;
import app.nzyme.core.assets.identity.db.*;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.registry.RegistryChangeValidator;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.requests.GenericConfigurationUpdateRequest;
import app.nzyme.core.rest.requests.UUIDListRequest;
import app.nzyme.core.rest.responses.ethernet.assets.identity.*;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import app.nzyme.core.rest.responses.ethernet.spoofing.SpoofingTapResponse;
import app.nzyme.core.ethernet.l4.tcp.TcpSignature;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import app.nzyme.core.rest.responses.ethernet.assets.identity.DuplicateMacDestinationResponse;
import app.nzyme.core.taps.Tap;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
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

import java.util.*;
import java.util.stream.Collectors;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/assets/identity")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Asset Identity", description = "Who is really behind a MAC address. Three detections run over the "
        + "identity attributes of assets: duplicate MAC findings, where several directly attached TCP stacks used "
        + "one address at the same time; identity change findings, where an established asset started presenting "
        + "a different stack, hostname or behaviour and the old one stopped; and links between assets that are "
        + "probably one device that moved to a new MAC address. Every detection has its own thresholds and alert "
        + "switch, and exclusions by MAC address or vendor prefix.")
public class AssetIdentityResource extends TapDataHandlingResource {

    private static final String MANAGE_PERMISSION = "ethernet_assets_manage";

    private static final int SUMMARY_LIMIT = 10;
    private static final int SUMMARY_DAYS = 30;
    private static final int MAXIMUM_PAGE_SIZE = 250;

    @Inject
    private NzymeNode nzyme;

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "findAssetIdentityConfiguration", summary = "Get the asset identity detection configuration of a tenant",
            description = "Returns the settings of the three asset identity detections as one section per "
                    + "detection, each with its detection parameters and alerting settings, plus the finding "
                    + "retention. Requires the ethernet_assets_manage feature permission.",
            externalDocs = @ExternalDocumentation(description = "Asset identity in the Nzyme documentation",
                    url = "https://go.nzyme.org/assets-identity"))
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = AssetIdentityConfigurationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response getConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<AssetIdentityConfigurationSectionResponse> sections = Lists.newArrayList();

        sections.add(AssetIdentityConfigurationSectionResponse.create("duplicate", "Duplicate MAC Address",
                List.of(
                        booleanEntry(AssetIdentityRegistryKeys.DUPLICATE_DETECTION_ENABLED, "Detection enabled", organizationId, tenantId),
                        numberEntry(AssetIdentityRegistryKeys.DUPLICATE_WINDOW_MINUTES, "Detection window (Minutes)", organizationId, tenantId),
                        numberEntry(AssetIdentityRegistryKeys.DUPLICATE_MINIMUM_SESSIONS, "Minimum sessions per stack in window", organizationId, tenantId),
                        booleanEntry(AssetIdentityRegistryKeys.DUPLICATE_REQUIRE_STRONG_DIFFERENCE, "Ignore stacks that differ only in window scale, MSS, ECN or timestamps", organizationId, tenantId)
                ),
                List.of(
                        booleanEntry(AssetIdentityRegistryKeys.DUPLICATE_ALERT_ON_NEW, "Alert on new duplicate MAC finding", organizationId, tenantId)
                )
        ));

        sections.add(AssetIdentityConfigurationSectionResponse.create("change", "Identity Change",
                List.of(
                        booleanEntry(AssetIdentityRegistryKeys.CHANGE_DETECTION_ENABLED, "Detection enabled", organizationId, tenantId),
                        numberEntry(AssetIdentityRegistryKeys.CHANGE_WINDOW_HOURS, "Detection window (Hours)", organizationId, tenantId),
                        numberEntry(AssetIdentityRegistryKeys.CHANGE_MINIMUM_ASSET_AGE_HOURS, "Minimum asset age (Hours)", organizationId, tenantId),
                        numberEntry(AssetIdentityRegistryKeys.CHANGE_SCORE_THRESHOLD, "Score threshold", organizationId, tenantId)
                ),
                List.of(
                        booleanEntry(AssetIdentityRegistryKeys.CHANGE_ALERT_ON_NEW, "Alert on new identity change finding", organizationId, tenantId)
                )
        ));

        sections.add(AssetIdentityConfigurationSectionResponse.create("successor", "MAC Address Change",
                List.of(
                        booleanEntry(AssetIdentityRegistryKeys.SUCCESSOR_DETECTION_ENABLED, "Detection enabled", organizationId, tenantId),
                        numberEntry(AssetIdentityRegistryKeys.SUCCESSOR_LOOKBACK_HOURS, "Lookback for silent assets (Hours)", organizationId, tenantId),
                        numberEntry(AssetIdentityRegistryKeys.SUCCESSOR_QUIET_MINUTES, "Quiet period before an asset counts as gone (Minutes)", organizationId, tenantId),
                        numberEntry(AssetIdentityRegistryKeys.SUCCESSOR_MINIMUM_CONFIDENCE, "Minimum confidence", organizationId, tenantId)
                ),
                List.of(
                        booleanEntry(AssetIdentityRegistryKeys.SUCCESSOR_ALERT_ON_NEW, "Alert on new MAC address change", organizationId, tenantId)
                )
        ));

        sections.add(AssetIdentityConfigurationSectionResponse.create("retention", "Retention",
                List.of(
                        numberEntry(AssetIdentityRegistryKeys.RETENTION_TIME_DAYS, "Finding retention time (Days)", organizationId, tenantId)
                ),
                List.of()
        ));

        return Response.ok(AssetIdentityConfigurationResponse.create(sections)).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "updateAssetIdentityConfiguration", summary = "Update the asset identity detection configuration of a tenant",
            description = "Updates one or more asset identity settings. Accepted keys are the keys returned by the "
                    + "configuration endpoint. Requires the ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The change is empty, contains an unknown key or a value violates the constraints of its key.", content = @Content)
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
                .allowing(AssetIdentityRegistryKeys.ALL)
                .validate(req.change());

        if (changes.isEmpty()) {
            return Response.status(422).build();
        }

        for (RegistryChangeValidator.Change c : changes.get()) {
            nzyme.getDatabaseCoreRegistry().setValue(c.key(), c.value(), organizationId, tenantId);
        }

        return Response.ok().build();
    }

    @GET
    @Path("/duplicates")
    @Operation(operationId = "findDuplicateMacFindings", summary = "List duplicate MAC findings of a tenant",
            description = "Returns the findings of the tenant that were active at any point inside the time range, "
                    + "most recently seen first by default. A finding is one MAC address and the TCP stacks that "
                    + "used it at the same time. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Findings found.",
            content = @Content(schema = @Schema(implementation = DuplicateMacFindingsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction, page size above 250 or negative offset.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAllDuplicates(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                      @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                      @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Fields: mac, stack_count, session_count.") @QueryParam("filters") String filtersParameter,
                                      @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker. A finding matches when it was active at any point inside the range.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                      @Parameter(description = "Sorting column: MAC, STACK_COUNT, SESSION_COUNT, FIRST_SEEN or LAST_SEEN. Defaults to LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                      @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                      @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                      @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (limit > MAXIMUM_PAGE_SIZE || offset < 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        AssetIdentity.DuplicateOrderColumn orderColumn = AssetIdentity.DuplicateOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = AssetIdentity.DuplicateOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        AssetIdentity identity = nzyme.getAssetsManager().identity();
        AssetIdentityConfiguration config = identity.getConfiguration(organizationId, tenantId);
        Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> exclusions = identity.exclusions()
                .compileEnabled(organizationId, tenantId);
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);

        long total = identity.countDuplicateFindings(filters, timeRange, organizationId, tenantId);
        List<DuplicateMacFindingResponse> findings = Lists.newArrayList();
        for (DuplicateMacFindingEntry finding : identity.findAllDuplicateFindings(
                filters, timeRange, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            findings.add(toResponse(finding, config, exclusions, addresses, organizationId, tenantId));
        }

        return Response.ok(DuplicateMacFindingsListResponse.create(total, findings)).build();
    }

    @GET
    @Path("/duplicates/show/{finding_uuid}")
    @Operation(operationId = "findDuplicateMacFinding", summary = "Get a duplicate MAC finding",
            description = "Returns one finding with every stack that used the address, their sessions, sample "
                    + "destinations and source port range.")
    @ApiResponse(responseCode = "200", description = "Finding found.",
            content = @Content(schema = @Schema(implementation = DuplicateMacFindingResponse.class)))
    @ApiResponse(responseCode = "404", description = "Finding, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findDuplicate(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                  @Parameter(description = "Finding UUID.") @PathParam("finding_uuid") UUID findingUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        AssetIdentity identity = nzyme.getAssetsManager().identity();
        Optional<DuplicateMacFindingEntry> finding = identity.findDuplicateFinding(findingUuid, organizationId, tenantId);
        if (finding.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(toResponse(
                finding.get(),
                identity.getConfiguration(organizationId, tenantId),
                identity.exclusions().compileEnabled(organizationId, tenantId),
                new ReconAddressResponses(nzyme),
                organizationId,
                tenantId
        )).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/duplicates/organization/{organization_id}/tenant/{tenant_id}/show/{finding_uuid}")
    @Operation(operationId = "deleteDuplicateMacFinding", summary = "Delete a duplicate MAC finding",
            description = "Deletes a finding. It is created again if the stacks keep sharing the address. Requires "
                    + "the ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Finding deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Finding, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteDuplicate(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                    @Parameter(description = "Finding UUID.") @PathParam("finding_uuid") UUID findingUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        AssetIdentity identity = nzyme.getAssetsManager().identity();
        Optional<DuplicateMacFindingEntry> finding = identity.findDuplicateFinding(findingUuid, organizationId, tenantId);
        if (finding.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        identity.deleteDuplicateFinding(finding.get().id());

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/duplicates/organization/{organization_id}/tenant/{tenant_id}/many/delete")
    @Operation(operationId = "deleteDuplicateMacFindings", summary = "Delete multiple findings",
            description = "Deletes every finding in the list that belongs to the tenant. Unknown UUIDs are skipped. "
                    + "At most 250 UUIDs per call. Requires the ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Duplicatemacfindings deleted.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Missing request body or more than 250 UUIDs.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteDuplicateMacFindings(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                   @RequestBody(description = "The UUIDs of the findings to delete.", required = true, content = @Content(mediaType = "application/json")) UUIDListRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (req == null || req.uuids() == null || req.uuids().size() > MAXIMUM_PAGE_SIZE) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!req.uuids().isEmpty()) {
            nzyme.getAssetsManager().identity().deleteDuplicateFindings(req.uuids(), organizationId, tenantId);
        }

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/duplicates/organization/{organization_id}/tenant/{tenant_id}/filtered/delete")
    @Operation(operationId = "deleteFilteredDuplicateMacFindings", summary = "Delete all matching findings",
            description = "Deletes every finding of the tenant that was active at any point inside the time range and matches the filters, "
                    + "not only the findings of one page. Requires the ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Matching findings deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteFilteredDuplicateMacFindings(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                           @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                           @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAssetsManager().identity().deleteDuplicateFindings(parseFiltersQueryParameter(filtersParameter),
                parseTimeRangeQueryParameter(timeRangeParameter), organizationId, tenantId);

        return Response.ok().build();
    }

    @GET
    @Path("/changes")
    @Operation(operationId = "findIdentityChangeFindings", summary = "List identity change findings of a tenant",
            description = "Returns the findings of the tenant that were active at any point inside the time range, "
                    + "most recently seen first by default. A finding is one established asset whose identity "
                    + "changed enough inside the detection window to reach the score threshold. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Findings found.",
            content = @Content(schema = @Schema(implementation = IdentityChangeFindingsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction, page size above 250 or negative offset.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAllChanges(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                   @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Fields: mac, score, previous_fingerprint, new_fingerprint.") @QueryParam("filters") String filtersParameter,
                                   @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker. A finding matches when it was active at any point inside the range.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                   @Parameter(description = "Sorting column: MAC, SCORE, FIRST_SEEN or LAST_SEEN. Defaults to LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                   @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                   @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                   @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (limit > MAXIMUM_PAGE_SIZE || offset < 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        AssetIdentity.ChangeOrderColumn orderColumn = AssetIdentity.ChangeOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = AssetIdentity.ChangeOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        AssetIdentity identity = nzyme.getAssetsManager().identity();
        AssetIdentityConfiguration config = identity.getConfiguration(organizationId, tenantId);
        Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> exclusions = identity.exclusions()
                .compileEnabled(organizationId, tenantId);
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);

        long total = identity.countChangeFindings(filters, timeRange, organizationId, tenantId);
        List<IdentityChangeFindingResponse> findings = Lists.newArrayList();
        for (IdentityChangeFindingEntry finding : identity.findAllChangeFindings(
                filters, timeRange, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            findings.add(toResponse(finding, config, exclusions, addresses, organizationId, tenantId));
        }

        return Response.ok(IdentityChangeFindingsListResponse.create(total, findings)).build();
    }

    @GET
    @Path("/changes/show/{finding_uuid}")
    @Operation(operationId = "findIdentityChangeFinding", summary = "Get an identity change finding",
            description = "Returns one finding with every scored change: what was presented before, what is "
                    + "presented now, and when the new value was first seen.")
    @ApiResponse(responseCode = "200", description = "Finding found.",
            content = @Content(schema = @Schema(implementation = IdentityChangeFindingResponse.class)))
    @ApiResponse(responseCode = "404", description = "Finding, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findChange(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                               @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                               @Parameter(description = "Finding UUID.") @PathParam("finding_uuid") UUID findingUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        AssetIdentity identity = nzyme.getAssetsManager().identity();
        Optional<IdentityChangeFindingEntry> finding = identity.findChangeFinding(findingUuid, organizationId, tenantId);
        if (finding.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(toResponse(
                finding.get(),
                identity.getConfiguration(organizationId, tenantId),
                identity.exclusions().compileEnabled(organizationId, tenantId),
                new ReconAddressResponses(nzyme),
                organizationId,
                tenantId
        )).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/changes/organization/{organization_id}/tenant/{tenant_id}/show/{finding_uuid}")
    @Operation(operationId = "deleteIdentityChangeFinding", summary = "Delete an identity change finding",
            description = "Deletes a finding. It is created again while the changes stay inside the detection "
                    + "window. Requires the ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Finding deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Finding, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteChange(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                 @Parameter(description = "Finding UUID.") @PathParam("finding_uuid") UUID findingUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        AssetIdentity identity = nzyme.getAssetsManager().identity();
        Optional<IdentityChangeFindingEntry> finding = identity.findChangeFinding(findingUuid, organizationId, tenantId);
        if (finding.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        identity.deleteChangeFinding(finding.get().id());

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/changes/organization/{organization_id}/tenant/{tenant_id}/many/delete")
    @Operation(operationId = "deleteIdentityChangeFindings", summary = "Delete multiple findings",
            description = "Deletes every finding in the list that belongs to the tenant. Unknown UUIDs are skipped. "
                    + "At most 250 UUIDs per call. Requires the ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Identitychangefindings deleted.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Missing request body or more than 250 UUIDs.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteIdentityChangeFindings(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                   @RequestBody(description = "The UUIDs of the findings to delete.", required = true, content = @Content(mediaType = "application/json")) UUIDListRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (req == null || req.uuids() == null || req.uuids().size() > MAXIMUM_PAGE_SIZE) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!req.uuids().isEmpty()) {
            nzyme.getAssetsManager().identity().deleteChangeFindings(req.uuids(), organizationId, tenantId);
        }

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/changes/organization/{organization_id}/tenant/{tenant_id}/filtered/delete")
    @Operation(operationId = "deleteFilteredIdentityChangeFindings", summary = "Delete all matching findings",
            description = "Deletes every finding of the tenant that was active at any point inside the time range and matches the filters, "
                    + "not only the findings of one page. Requires the ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Matching findings deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteFilteredIdentityChangeFindings(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                           @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                           @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAssetsManager().identity().deleteChangeFindings(parseFiltersQueryParameter(filtersParameter),
                parseTimeRangeQueryParameter(timeRangeParameter), organizationId, tenantId);

        return Response.ok().build();
    }

    @GET
    @Path("/links")
    @Operation(operationId = "findAssetLinks", summary = "List links between assets of a tenant",
            description = "Returns the links detected inside the time range, newest first by default. A link "
                    + "says that a new asset is probably a known device that moved to a new MAC address "
                    + "(kind SUCCESSOR), or a device that alternates between two addresses (kind ALTERNATE). "
                    + "Links are OPEN until a user confirms or dismisses them. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Links found.",
            content = @Content(schema = @Schema(implementation = AssetLinksListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction, page size above 250 or negative offset.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAllLinks(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                 @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Fields: predecessor_mac, successor_mac, kind, status, confidence.") @QueryParam("filters") String filtersParameter,
                                 @Parameter(description = "Time range selector over the detection time. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                 @Parameter(description = "Sorting column: PREDECESSOR_MAC, SUCCESSOR_MAC, KIND, CONFIDENCE, STATUS, SUCCESSOR_FIRST_SEEN or DETECTED_AT. Defaults to DETECTED_AT.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                 @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                 @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                 @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (limit > MAXIMUM_PAGE_SIZE || offset < 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        AssetIdentity.LinkOrderColumn orderColumn = AssetIdentity.LinkOrderColumn.DETECTED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = AssetIdentity.LinkOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        AssetIdentity identity = nzyme.getAssetsManager().identity();
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);

        long total = identity.countLinks(filters, timeRange, organizationId, tenantId);
        List<AssetLinkResponse> links = Lists.newArrayList();
        for (AssetLinkEntry link : identity.findAllLinks(
                filters, timeRange, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            links.add(toResponse(link, addresses, organizationId, tenantId));
        }

        return Response.ok(AssetLinksListResponse.create(total, links)).build();
    }

    @GET
    @Path("/links/show/{link_uuid}")
    @Operation(operationId = "findAssetLink", summary = "Get a link between two assets",
            description = "Returns one link with the evidence that the two assets are one device.")
    @ApiResponse(responseCode = "200", description = "Link found.",
            content = @Content(schema = @Schema(implementation = AssetLinkResponse.class)))
    @ApiResponse(responseCode = "404", description = "Link, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findLink(@Parameter(hidden = true) @Context SecurityContext sc,
                             @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                             @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                             @Parameter(description = "Link UUID.") @PathParam("link_uuid") UUID linkUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<AssetLinkEntry> link = nzyme.getAssetsManager().identity().findLink(linkUuid, organizationId, tenantId);
        if (link.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(toResponse(link.get(), new ReconAddressResponses(nzyme), organizationId, tenantId)).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/links/organization/{organization_id}/tenant/{tenant_id}/show/{link_uuid}/status/{status}")
    @Operation(operationId = "setAssetLinkStatus", summary = "Confirm, dismiss or reopen a link between two assets",
            description = "Sets the status of a link to CONFIRMED, DISMISSED or OPEN. A dismissed link is not "
                    + "detected again, a confirmed link is kept beyond the finding retention. Requires the "
                    + "ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Status updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Unknown status.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Link, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response setLinkStatus(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                  @Parameter(description = "Link UUID.") @PathParam("link_uuid") UUID linkUuid,
                                  @Parameter(description = "CONFIRMED, DISMISSED or OPEN.") @PathParam("status") String status) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String normalized = normalizeLinkStatus(status);
        if (normalized == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        AssetIdentity identity = nzyme.getAssetsManager().identity();
        Optional<AssetLinkEntry> link = identity.findLink(linkUuid, organizationId, tenantId);
        if (link.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        identity.setLinkStatus(link.get().id(), normalized);

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/links/organization/{organization_id}/tenant/{tenant_id}/show/{link_uuid}")
    @Operation(operationId = "deleteAssetLink", summary = "Delete a link between two assets",
            description = "Deletes a link. It can be detected again while the successor is inside the lookback. "
                    + "Dismiss a link instead to keep it from coming back. Requires the ethernet_assets_manage "
                    + "feature permission.")
    @ApiResponse(responseCode = "200", description = "Link deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Link, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteLink(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                               @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                               @Parameter(description = "Link UUID.") @PathParam("link_uuid") UUID linkUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        AssetIdentity identity = nzyme.getAssetsManager().identity();
        Optional<AssetLinkEntry> link = identity.findLink(linkUuid, organizationId, tenantId);
        if (link.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        identity.deleteLink(link.get().id());

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/links/organization/{organization_id}/tenant/{tenant_id}/many/status/{status}")
    @Operation(operationId = "setAssetLinksStatus", summary = "Confirm, dismiss or reopen multiple links",
            description = "Sets the status of every link in the list that belongs to the tenant to CONFIRMED, "
                    + "DISMISSED or OPEN. Unknown UUIDs are skipped. At most 250 UUIDs per call. Requires the "
                    + "ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Status updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Unknown status, missing request body or more than 250 UUIDs.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response setLinksStatus(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                   @Parameter(description = "CONFIRMED, DISMISSED or OPEN.") @PathParam("status") String status,
                                   @RequestBody(description = "The UUIDs of the links.", required = true, content = @Content(mediaType = "application/json")) UUIDListRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String normalized = normalizeLinkStatus(status);
        if (normalized == null || req == null || req.uuids() == null || req.uuids().size() > MAXIMUM_PAGE_SIZE) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!req.uuids().isEmpty()) {
            nzyme.getAssetsManager().identity().setLinksStatus(req.uuids(), normalized, organizationId, tenantId);
        }

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/links/organization/{organization_id}/tenant/{tenant_id}/filtered/status/{status}")
    @Operation(operationId = "setFilteredAssetLinksStatus", summary = "Confirm, dismiss or reopen all matching links",
            description = "Sets the status of every link of the tenant that was detected inside the time range and "
                    + "matches the filters, not only the links of one page. Requires the ethernet_assets_manage "
                    + "feature permission.")
    @ApiResponse(responseCode = "200", description = "Status updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Unknown status.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response setFilteredLinksStatus(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                           @Parameter(description = "CONFIRMED, DISMISSED or OPEN.") @PathParam("status") String status,
                                           @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                           @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String normalized = normalizeLinkStatus(status);
        if (normalized == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        nzyme.getAssetsManager().identity().setLinksStatus(parseFiltersQueryParameter(filtersParameter),
                parseTimeRangeQueryParameter(timeRangeParameter), normalized, organizationId, tenantId);

        return Response.ok().build();
    }

    @Nullable
    private static String normalizeLinkStatus(@Nullable String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!normalized.equals(AssetIdentity.LINK_STATUS_OPEN) && !normalized.equals(AssetIdentity.LINK_STATUS_CONFIRMED)
                && !normalized.equals(AssetIdentity.LINK_STATUS_DISMISSED)) {
            return null;
        }

        return normalized;
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/links/organization/{organization_id}/tenant/{tenant_id}/many/delete")
    @Operation(operationId = "deleteAssetLinks", summary = "Delete multiple links",
            description = "Deletes every link in the list that belongs to the tenant. Unknown UUIDs are skipped. "
                    + "At most 250 UUIDs per call. Requires the ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Assetlinks deleted.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Missing request body or more than 250 UUIDs.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteAssetLinks(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                   @RequestBody(description = "The UUIDs of the links to delete.", required = true, content = @Content(mediaType = "application/json")) UUIDListRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (req == null || req.uuids() == null || req.uuids().size() > MAXIMUM_PAGE_SIZE) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!req.uuids().isEmpty()) {
            nzyme.getAssetsManager().identity().deleteLinks(req.uuids(), organizationId, tenantId);
        }

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/links/organization/{organization_id}/tenant/{tenant_id}/filtered/delete")
    @Operation(operationId = "deleteFilteredAssetLinks", summary = "Delete all matching links",
            description = "Deletes every link of the tenant that was detected inside the time range and matches the filters, "
                    + "not only the links of one page. Requires the ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Matching links deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteFilteredAssetLinks(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                           @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                           @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAssetsManager().identity().deleteLinks(parseFiltersQueryParameter(filtersParameter),
                parseTimeRangeQueryParameter(timeRangeParameter), organizationId, tenantId);

        return Response.ok().build();
    }

    @GET
    @Path("/asset/{asset_uuid}")
    @Operation(operationId = "findAssetIdentitySummary", summary = "Get the identity findings and links of one asset",
            description = "Returns the newest duplicate MAC and identity change findings of the asset from the last "
                    + "30 days together with the total number of findings in that period, and every open or confirmed "
                    + "link that involves it, for the notices on the asset details page. Dismissed links are not "
                    + "included.")
    @ApiResponse(responseCode = "200", description = "Summary found.",
            content = @Content(schema = @Schema(implementation = AssetIdentitySummaryResponse.class)))
    @ApiResponse(responseCode = "404", description = "Asset, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response assetSummary(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                 @Parameter(description = "Asset UUID.") @PathParam("asset_uuid") UUID assetUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAsset(assetUuid, organizationId, tenantId);
        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        AssetIdentity identity = nzyme.getAssetsManager().identity();
        AssetIdentityConfiguration config = identity.getConfiguration(organizationId, tenantId);
        Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> exclusions = identity.exclusions()
                .compileEnabled(organizationId, tenantId);
        ReconAddressResponses addresses = new ReconAddressResponses(nzyme);
        DateTime since = DateTime.now().minusDays(SUMMARY_DAYS);
        String mac = asset.get().mac();

        List<DuplicateMacFindingResponse> duplicates = Lists.newArrayList();
        for (DuplicateMacFindingEntry finding : identity.findDuplicateFindingsOfMac(mac, organizationId, tenantId, since, SUMMARY_LIMIT)) {
            duplicates.add(toResponse(finding, config, exclusions, addresses, organizationId, tenantId));
        }

        List<IdentityChangeFindingResponse> changes = Lists.newArrayList();
        for (IdentityChangeFindingEntry finding : identity.findChangeFindingsOfMac(mac, organizationId, tenantId, since, SUMMARY_LIMIT)) {
            changes.add(toResponse(finding, config, exclusions, addresses, organizationId, tenantId));
        }

        List<AssetLinkResponse> links = Lists.newArrayList();
        for (AssetLinkEntry link : identity.findOpenLinksOfMac(mac, organizationId, tenantId, SUMMARY_LIMIT)) {
            links.add(toResponse(link, addresses, organizationId, tenantId));
        }

        long duplicatesTotal = identity.countDuplicateFindingsOfMac(mac, organizationId, tenantId, since);
        long changesTotal = identity.countChangeFindingsOfMac(mac, organizationId, tenantId, since);

        return Response.ok(AssetIdentitySummaryResponse.create(
                duplicates, duplicatesTotal, changes, changesTotal, links, SUMMARY_DAYS
        )).build();
    }

    private DuplicateMacFindingResponse toResponse(DuplicateMacFindingEntry finding,
                                                   AssetIdentityConfiguration config,
                                                   Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> exclusions,
                                                   ReconAddressResponses addresses,
                                                   UUID organizationId,
                                                   UUID tenantId) {
        List<DuplicateMacStackResponse> stacks = Lists.newArrayList();
        for (DuplicateMacStack stack : finding.stacks()) {
            stacks.add(DuplicateMacStackResponse.create(
                    stack.fingerprint(),
                    TcpSignature.shortId(stack.fingerprint()),
                    stack.signatures(),
                    stack.sessions(),
                    DateTime.parse(stack.firstSeen()),
                    DateTime.parse(stack.lastSeen()),
                    stack.sourceAddresses().stream()
                            .map(a -> addresses.addressResponse(a, null, finding.mac(), L4Type.TCP, organizationId, tenantId))
                            .filter(Objects::nonNull)
                            .toList(),
                    stack.destinations().stream()
                            .map(d -> {
                                L4AddressResponse address = addresses.addressResponse(
                                        d.address(), d.port(), null, L4Type.TCP, organizationId, tenantId);
                                return address == null ? null : DuplicateMacDestinationResponse.create(address, d.sessions(), d.bytes());
                            })
                            .filter(Objects::nonNull)
                            .toList(),
                    stack.destinationCount(),
                    stack.sourcePortMin(),
                    stack.sourcePortMax(),
                    stack.taps()
            ));
        }

        List<SpoofingTapResponse> taps = Lists.newArrayList();
        for (UUID tapUuid : finding.tapUuids()) {
            taps.add(SpoofingTapResponse.create(
                    tapUuid, nzyme.getTapManager().findTap(tapUuid).map(Tap::name).orElse(null)
            ));
        }

        return DuplicateMacFindingResponse.create(
                finding.uuid(),
                finding.mac(),
                addresses.macResponse(finding.mac(), organizationId, tenantId),
                stacks,
                finding.stackCount(),
                finding.sessionCount(),
                finding.lastSeen().isAfter(DateTime.now().minusMinutes(config.duplicateWindowMinutes())),
                config.duplicateWindowMinutes(),
                taps,
                exclusionReferences(exclusions, finding.mac()),
                finding.firstSeen(),
                finding.lastSeen(),
                finding.createdAt()
        );
    }

    private IdentityChangeFindingResponse toResponse(IdentityChangeFindingEntry finding,
                                                     AssetIdentityConfiguration config,
                                                     Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> exclusions,
                                                     ReconAddressResponses addresses,
                                                     UUID organizationId,
                                                     UUID tenantId) {
        List<IdentityChangeResponse> changes = Lists.newArrayList();
        for (IdentityChange change : finding.changes()) {
            changes.add(IdentityChangeResponse.create(
                    change.signal(),
                    change.weight(),
                    change.previousValue(),
                    change.newValue(),
                    DateTime.parse(change.seenAt())
            ));
        }

        return IdentityChangeFindingResponse.create(
                finding.uuid(),
                finding.mac(),
                addresses.macResponse(finding.mac(), organizationId, tenantId),
                finding.score(),
                config.changeScoreThreshold(),
                changes,
                finding.previousFingerprint(),
                finding.newFingerprint(),
                finding.lastSeen().isAfter(DateTime.now().minusHours(config.changeWindowHours())),
                exclusionReferences(exclusions, finding.mac()),
                finding.firstSeen(),
                finding.lastSeen(),
                finding.createdAt()
        );
    }

    private AssetLinkResponse toResponse(AssetLinkEntry link,
                                         ReconAddressResponses addresses,
                                         UUID organizationId,
                                         UUID tenantId) {
        List<AssetLinkEvidenceResponse> evidence = Lists.newArrayList();
        for (AssetLinkEvidence e : link.evidence()) {
            evidence.add(AssetLinkEvidenceResponse.create(e.signal(), e.weight(), e.value()));
        }

        return AssetLinkResponse.create(
                link.uuid(),
                link.predecessorMac(),
                addresses.macResponse(link.predecessorMac(), organizationId, tenantId),
                link.successorMac(),
                addresses.macResponse(link.successorMac(), organizationId, tenantId),
                link.kind(),
                link.confidence(),
                evidence,
                link.status(),
                link.predecessorLastSeen(),
                link.successorFirstSeen(),
                link.detectedAt(),
                link.updatedAt()
        );
    }

    private static List<AssetIdentityExclusionReferenceResponse> exclusionReferences(
            Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> exclusions, String mac) {
        return AssetIdentityExclusions.findAllMatchingMac(exclusions, mac).stream()
                .map(e -> AssetIdentityExclusionReferenceResponse.create(e.uuid(), e.name()))
                .collect(Collectors.toList());
    }

    private ConfigurationEntryResponse booleanEntry(RegistryKey key, String title, UUID organizationId, UUID tenantId) {
        return ConfigurationEntryResponse.create(
                key.key(),
                title,
                nzyme.getAssetsManager().identity().booleanValue(key, organizationId, tenantId),
                ConfigurationEntryValueType.BOOLEAN,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                "assets-identity-config"
        );
    }

    private ConfigurationEntryResponse numberEntry(RegistryKey key, String title, UUID organizationId, UUID tenantId) {
        return ConfigurationEntryResponse.create(
                key.key(),
                title,
                nzyme.getAssetsManager().identity().intValue(key, organizationId, tenantId),
                ConfigurationEntryValueType.NUMBER,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                "assets-identity-config"
        );
    }

}
