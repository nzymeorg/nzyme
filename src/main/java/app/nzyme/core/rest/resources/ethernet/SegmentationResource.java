package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.tags.AssetTag;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.CIDR;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.segmentation.BulkResult;
import app.nzyme.core.ethernet.segmentation.BulkSelection;
import app.nzyme.core.ethernet.segmentation.Endpoint;
import app.nzyme.core.ethernet.segmentation.EngineState;
import app.nzyme.core.ethernet.segmentation.Exclusion;
import app.nzyme.core.ethernet.segmentation.Exclusions;
import app.nzyme.core.ethernet.segmentation.Finding;
import app.nzyme.core.ethernet.segmentation.FindingClass;
import app.nzyme.core.ethernet.segmentation.Findings;
import app.nzyme.core.ethernet.segmentation.Flow;
import app.nzyme.core.ethernet.segmentation.Flows;
import app.nzyme.core.ethernet.segmentation.MembershipResolver;
import app.nzyme.core.ethernet.segmentation.Policies;
import app.nzyme.core.ethernet.segmentation.Policy;
import app.nzyme.core.ethernet.segmentation.PolicyRule;
import app.nzyme.core.ethernet.segmentation.PolicyStatus;
import app.nzyme.core.ethernet.segmentation.Segment;
import app.nzyme.core.ethernet.segmentation.SegmentKind;
import app.nzyme.core.ethernet.segmentation.Segmentation;
import app.nzyme.core.ethernet.segmentation.SegmentationConfiguration;
import app.nzyme.core.ethernet.segmentation.SegmentationEngine;
import app.nzyme.core.ethernet.segmentation.SegmentationRegistryKeys;
import app.nzyme.core.ethernet.segmentation.SegmentationViews;
import app.nzyme.core.ethernet.segmentation.Segments;
import app.nzyme.core.ethernet.topology.Topology;
import app.nzyme.core.registry.RegistryChangeValidator;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.requests.GenericConfigurationUpdateRequest;
import app.nzyme.core.rest.requests.SegmentExclusionRequest;
import app.nzyme.core.rest.requests.SegmentMemberRequest;
import app.nzyme.core.rest.requests.SegmentRequest;
import app.nzyme.core.rest.requests.SegmentationPolicyAllowRequest;
import app.nzyme.core.rest.requests.SegmentationPolicyPreviewRequest;
import app.nzyme.core.rest.requests.SegmentationPolicyRequest;
import app.nzyme.core.rest.requests.UUIDListRequest;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import app.nzyme.core.rest.responses.ethernet.assets.AssetTagResponse;
import app.nzyme.core.rest.responses.ethernet.segmentation.*;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/segmentation")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Segmentation", description = "Shows how the segments of a network talk to each other and checks that "
        + "they only do so as intended. A segment is a named part of the network, defined by address ranges. An "
        + "external network is the same for addresses outside of the network and can also be defined by autonomous "
        + "system. A policy is a scope and a list of what is allowed inside of it: any connection in scope that is "
        + "not allowed is a finding, separately for connections the target answered and for attempts that were "
        + "rejected or got no reply. Everything is built on the node from the TCP and UDP sessions recorded by all "
        + "taps of the tenant, which are rolled up into flows once and never read again.")
public class SegmentationResource extends TapDataHandlingResource {

    private static final String MANAGE_PERMISSION = "ethernet_monitoring_manage";

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 250;

    private static final RegistryKey[] CONFIGURATION_KEYS = {
            SegmentationRegistryKeys.ENABLED,
            SegmentationRegistryKeys.SETTLE_SECONDS,
            SegmentationRegistryKeys.INITIAL_BACKFILL_HOURS,
            SegmentationRegistryKeys.FLOW_RETENTION_TIME_DAYS,
            SegmentationRegistryKeys.FINDING_RETENTION_TIME_DAYS
    };

    @Inject
    private NzymeNode nzyme;

    private Segmentation segmentation() {
        return nzyme.getEthernet().segmentation();
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(ErrorResponse.create(message)).build();
    }

    // An exclusion decides what is recorded of every tap of the segment, and creating one deletes flows of them.
    private static Response exclusionsForbidden() {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(ErrorResponse.create("Requires access to all taps that record the segment."))
                .build();
    }

    private static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    @Path("/status")
    @Operation(operationId = "getSegmentationStatus", summary = "Get the state of the segmentation engine of a tenant",
            description = "Returns how far the engine has evaluated recorded sessions and what its last run did.")
    @ApiResponse(responseCode = "200", description = "Status found.",
            content = @Content(schema = @Schema(implementation = SegmentationStatusResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response status(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        EngineState state = segmentation().state(organizationId, tenantId);
        SegmentationConfiguration config = segmentation().getConfiguration(organizationId, tenantId);

        Long lag = state.watermark() == null ? null
                : Math.max(0, (DateTime.now().getMillis() - state.watermark().getMillis()) / 1000);

        return Response.ok(SegmentationStatusResponse.create(
                config.enabled(),
                state.watermark(),
                lag,
                state.lastRunAt(),
                state.lastRunDurationMs(),
                state.lastRunSessions(),
                state.lastRunFlows(),
                state.lastRunFindings(),
                state.restampPending(),
                config.settleSeconds(),
                segmentation().segments().findAll(organizationId, tenantId).size(),
                segmentation().policies().findAll(organizationId, tenantId).size(),
                state.limitReachedAt(),
                SegmentationEngine.MAX_GROUPS_PER_CHUNK,
                SegmentationEngine.MAX_FINDINGS_PER_POLICY_PER_CHUNK
        )).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "findSegmentationConfiguration", summary = "Get the segmentation configuration of a tenant",
            description = "Returns whether segmentation is enabled, how long after a session was recorded it is "
                    + "evaluated, how far back the first run reads, and how long flows and findings are kept. "
                    + "Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = SegmentationConfigurationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response getConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        SegmentationConfiguration config = segmentation().getConfiguration(organizationId, tenantId);

        return Response.ok(SegmentationConfigurationResponse.create(
                entry(SegmentationRegistryKeys.ENABLED, "Segmentation enabled", config.enabled(), ConfigurationEntryValueType.BOOLEAN),
                entry(SegmentationRegistryKeys.SETTLE_SECONDS, "Evaluate sessions after (Seconds)", config.settleSeconds(), ConfigurationEntryValueType.NUMBER),
                entry(SegmentationRegistryKeys.INITIAL_BACKFILL_HOURS, "Sessions read on first run (Hours)", config.initialBackfillHours(), ConfigurationEntryValueType.NUMBER),
                entry(SegmentationRegistryKeys.FLOW_RETENTION_TIME_DAYS, "Flow retention time (Days)", config.flowRetentionTimeDays(), ConfigurationEntryValueType.NUMBER),
                entry(SegmentationRegistryKeys.FINDING_RETENTION_TIME_DAYS, "Finding retention time (Days)", config.findingRetentionTimeDays(), ConfigurationEntryValueType.NUMBER)
        )).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "updateSegmentationConfiguration", summary = "Update the segmentation configuration of a tenant",
            description = "Updates one or more segmentation settings of the tenant. Accepted keys are the keys "
                    + "returned by the configuration endpoint. Requires the ethernet_monitoring_manage feature "
                    + "permission.")
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The change is empty, contains an unknown key, or a value violates the constraints of its configuration key.", content = @Content)
    public Response setConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                     @Valid GenericConfigurationUpdateRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        if (req == null || req.change().isEmpty()) {
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

    private static Response bulkResponse(BulkResult result) {
        List<BulkActionSkippedResponse> skipped = Lists.newArrayList();
        for (BulkResult.Skipped entry : result.skipped()) {
            skipped.add(BulkActionSkippedResponse.create(entry.uuid(), entry.name(), entry.reason()));
        }

        return Response.ok(BulkActionResponse.create(result.affected(), skipped)).build();
    }

    @Nullable
    private static FindingClass alertClass(@Nullable String param) {
        switch (param == null ? "" : param.trim().toLowerCase()) {
            case "connected":
                return FindingClass.CROSSED;
            case "attempted":
                return FindingClass.ATTEMPTED;
            default:
                return null;
        }
    }

    @Nullable
    private static Boolean strictBoolean(@Nullable String param) {
        switch (param == null ? "" : param.trim().toLowerCase()) {
            case "true":
                return true;
            case "false":
                return false;
            default:
                return null;
        }
    }

    private static int pageSize(int limit) {
        return limit <= 0 ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
    }

    private static <E extends Enum<E>> E orderColumn(Class<E> columns, @Nullable String param, E fallback) {
        if (param == null || param.isBlank()) {
            return fallback;
        }

        try {
            return Enum.valueOf(columns, param.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown sorting column [" + param + "].");
        }
    }

    private static OrderDirection orderDirection(@Nullable String param, OrderDirection fallback) {
        if (param == null || param.isBlank()) {
            return fallback;
        }

        try {
            return OrderDirection.valueOf(param.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown sorting direction [" + param + "].");
        }
    }

    private static ConfigurationEntryResponse entry(RegistryKey key, String title, Object value, ConfigurationEntryValueType type) {
        return ConfigurationEntryResponse.create(
                key.key(),
                title,
                value,
                type,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                "ethernet-segmentation-config"
        );
    }

    @GET
    @Path("/segments")
    @Operation(operationId = "findSegments", summary = "List the segments and external networks of a tenant",
            description = "Sorted by name by default. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Segments found.",
            content = @Content(schema = @Schema(implementation = SegmentsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid filters, or unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findSegments(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                 @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") @Nullable String filtersParameter,
                                 @Parameter(description = "Sorting column: NAME, KIND, DESCRIPTION, CREATED_AT or UPDATED_AT. Defaults to NAME.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                 @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                 @Parameter(description = "Page size. 25 if not set, 250 at most.") @QueryParam("limit") int limit,
                                 @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        Segments.OrderColumn orderColumn = orderColumn(Segments.OrderColumn.class, orderColumnParam, Segments.OrderColumn.NAME);
        OrderDirection orderDirection = orderDirection(orderDirectionParam, OrderDirection.ASC);

        List<Policy> policies = segmentation().policies().findAll(organizationId, tenantId);
        TapAccess tapAccess = tapAccess(sc, organizationId, tenantId);

        long total = segmentation().segments().count(filters, organizationId, tenantId);
        List<SegmentResponse> segments = Lists.newArrayList();
        for (Segment segment : segmentation().segments().findAll(filters, orderColumn, orderDirection,
                pageSize(limit), Math.max(offset, 0), organizationId, tenantId)) {
            segments.add(segmentResponse(segment, policies, tapAccess));
        }

        return Response.ok(SegmentsListResponse.create(total, segments)).build();
    }

    @GET
    @Path("/segments/suggestions")
    @Operation(operationId = "findSegmentSuggestions", summary = "List networks that could become segments",
            description = "Returns the networks trusted DHCP servers hand out addresses for and the networks of "
                    + "addresses that were seen talking and are in no segment.")
    @ApiResponse(responseCode = "200", description = "Suggestions found.",
            content = @Content(schema = @Schema(implementation = SegmentSuggestionsListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findSegmentSuggestions(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                           @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Segments.Suggestions found = segmentation().segments().suggestions(organizationId, tenantId,
                tapAccess(sc, organizationId, tenantId).accessibleUuids());

        List<SegmentSuggestionResponse> suggestions = Lists.newArrayList();
        for (Segments.Suggestion s : found.suggestions()) {
            suggestions.add(SegmentSuggestionResponse.create(s.source().name(), s.network(), s.hosts(),
                    s.dhcpServer()));
        }

        return Response.ok(SegmentSuggestionsListResponse.create(suggestions, Segments.SUGGESTION_LIMIT,
                found.limited(), Segments.SUGGESTION_LOOKBACK_DAYS)).build();
    }

    @GET
    @Path("/segments/show/{uuid}")
    @Operation(operationId = "findSegment", summary = "Get a segment or external network")
    @ApiResponse(responseCode = "200", description = "Segment found.",
            content = @Content(schema = @Schema(implementation = SegmentResponse.class)))
    @ApiResponse(responseCode = "404", description = "Segment, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findSegment(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Segment UUID.") @PathParam("uuid") UUID uuid,
                                @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<Segment> segment = segmentation().segments().find(uuid, organizationId, tenantId);
        if (segment.isEmpty()) {
            return notFound();
        }

        return Response.ok(segmentResponse(segment.get(), segmentation().policies().findAll(organizationId, tenantId),
                tapAccess(sc, organizationId, tenantId))).build();
    }

    @POST
    @Path("/organization/{organization_id}/tenant/{tenant_id}/segments")
    @Consumes(MediaType.APPLICATION_JSON)
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "createSegment", summary = "Create a segment or external network",
            description = "Stored flows are matched against the new definition in the background.")
    @ApiResponse(responseCode = "201", description = "Segment created.",
            content = @Content(schema = @Schema(implementation = SegmentResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid definition. The response carries the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response createSegment(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                  SegmentRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        if (req == null) {
            return badRequest("No definition passed.");
        }

        TapAccess tapAccess = tapAccess(sc, organizationId, tenantId);
        if (!tapAccess.mayLimitTo(req.tapUuids(), null)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        try {
            SegmentKind kind = SegmentKind.valueOf(req.kind() == null ? "" : req.kind().toUpperCase());
            Segment segment = segmentation().segments().create(organizationId, tenantId, kind, req.name(),
                    req.description(), req.notes(), tapAccess.limitedTaps(req.tapUuids(), null),
                    members(req.members()));

            return Response.status(Response.Status.CREATED)
                    .entity(segmentResponse(segment, Collections.emptyList(), tapAccess))
                    .build();
        } catch (IllegalArgumentException e) {
            return badRequest(message(e, "The kind must be INTERNAL or EXTERNAL."));
        }
    }

    @PUT
    @Path("/organization/{organization_id}/tenant/{tenant_id}/segments/show/{uuid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "updateSegment", summary = "Update a segment or external network",
            description = "The kind of a segment cannot be changed. If the networks or taps change, stored flows "
                    + "are matched against the new definition in the background.")
    @ApiResponse(responseCode = "200", description = "Segment updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Invalid definition. The response carries the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Segment, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response updateSegment(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Segment UUID.") @PathParam("uuid") UUID uuid,
                                  @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                  SegmentRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<Segment> segment = segmentation().segments().find(uuid, organizationId, tenantId);
        if (segment.isEmpty()) {
            return notFound();
        }

        if (req == null) {
            return badRequest("No definition passed.");
        }

        TapAccess tapAccess = tapAccess(sc, organizationId, tenantId);
        if (!tapAccess.mayLimitTo(req.tapUuids(), segment.get().tapUuids())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        try {
            segmentation().segments().update(segment.get(), req.name(), req.description(), req.notes(),
                    tapAccess.limitedTaps(req.tapUuids(), segment.get().tapUuids()), members(req.members()));
        } catch (IllegalArgumentException e) {
            return badRequest(message(e, "Invalid definition."));
        }

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/segments/many/delete")
    @Operation(operationId = "deleteSegments", summary = "Delete multiple segments",
            description = "Deletes every segment or external network in the list that belongs to the tenant. One that a policy "
                    + "refers to is left alone and returned as skipped. Unknown UUIDs are skipped. At most 250 UUIDs per call. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Action applied.",
            content = @Content(schema = @Schema(implementation = BulkActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Missing request body or more than 250 UUIDs.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteSegments(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                   @RequestBody(description = "The UUIDs of the segments to delete.", required = true, content = @Content(mediaType = "application/json")) UUIDListRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        if (req == null || req.uuids() == null || req.uuids().size() > MAX_PAGE_SIZE) {
            return badRequest("Pass at most " + MAX_PAGE_SIZE + " UUIDs.");
        }

        BulkSelection selection = BulkSelection.checked(req.uuids());
        return bulkResponse(segmentation().segments().delete(selection, organizationId, tenantId));
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/segments/filtered/delete")
    @Operation(operationId = "deleteFilteredSegments", summary = "Delete all matching segments",
            description = "Deletes every segment or external network of the tenant that matches the filters, not only the ones "
                    + "of one page. One that a policy refers to is left alone and returned as skipped. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Action applied.",
            content = @Content(schema = @Schema(implementation = BulkActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid filters.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteFilteredSegments(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                           @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Without it, every entry is selected.") @QueryParam("filters") @Nullable String filtersParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        BulkSelection selection = BulkSelection.matching(parseFiltersQueryParameter(filtersParameter));
        return bulkResponse(segmentation().segments().delete(selection, organizationId, tenantId));
    }

    @DELETE
    @Path("/organization/{organization_id}/tenant/{tenant_id}/segments/show/{uuid}")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "deleteSegment", summary = "Delete a segment or external network",
            description = "A segment that policies refer to cannot be deleted.")
    @ApiResponse(responseCode = "200", description = "Segment deleted.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Policies refer to the segment. The response names them.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Segment, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteSegment(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Segment UUID.") @PathParam("uuid") UUID uuid,
                                  @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<Segment> segment = segmentation().segments().find(uuid, organizationId, tenantId);
        if (segment.isEmpty()) {
            return notFound();
        }

        List<Policy> referencing = segmentation().policies()
                .findReferencing(organizationId, tenantId, PolicyRule.SelectorType.SEGMENT, uuid);
        if (!referencing.isEmpty()) {
            return badRequest("In use by " + names(referencing) + ". Remove it from there first.");
        }

        segmentation().segments().delete(segment.get());

        return Response.ok().build();
    }

    @GET
    @Path("/segments/show/{uuid}/exclusions")
    @Operation(operationId = "findSegmentExclusions", summary = "List the excluded destinations of a segment",
            description = "Connection attempts from the segment to an excluded destination that are rejected or get "
                    + "no reply are not recorded: they show in no crossing and no flow and cause no finding. "
                    + "Connections the destination answers are recorded as usual.")
    @ApiResponse(responseCode = "200", description = "Excluded destinations found.",
            content = @Content(schema = @Schema(implementation = SegmentExclusionsListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Segment, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findSegmentExclusions(@Parameter(hidden = true) @Context SecurityContext sc,
                                          @Parameter(description = "Segment UUID.") @PathParam("uuid") UUID uuid,
                                          @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                          @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<Segment> segment = segmentation().segments().find(uuid, organizationId, tenantId);
        if (segment.isEmpty()) {
            return notFound();
        }

        boolean allTaps = tapAccess(sc, organizationId, tenantId).coversAllTapsOf(segment.get());

        List<SegmentExclusionResponse> exclusions = Lists.newArrayList();
        for (Exclusion exclusion : segmentation().exclusions().findAllOfSegment(segment.get())) {
            exclusions.add(exclusionResponse(exclusion, allTaps));
        }

        return Response.ok(SegmentExclusionsListResponse.create(exclusions, Exclusions.MAX_PER_SEGMENT, allTaps)).build();
    }

    @POST
    @Path("/organization/{organization_id}/tenant/{tenant_id}/segments/show/{uuid}/exclusions")
    @Consumes(MediaType.APPLICATION_JSON)
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "createSegmentExclusion", summary = "Exclude a destination for a segment",
            description = "From then on, connection attempts from the segment to the destination that are rejected "
                    + "or get no reply are not recorded. Flows of such attempts that are stored already are "
                    + "deleted. Findings they caused stay. Only segments can have excluded destinations, external "
                    + "networks cannot. Requires the ethernet_monitoring_manage feature permission and access to "
                    + "every tap that records the segment.")
    @ApiResponse(responseCode = "201", description = "Destination excluded.",
            content = @Content(schema = @Schema(implementation = SegmentExclusionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid destination or note. The response carries the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The caller has no access to a tap that records the segment.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Segment, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response createSegmentExclusion(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Segment UUID.") @PathParam("uuid") UUID uuid,
                                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                           SegmentExclusionRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<Segment> segment = segmentation().segments().find(uuid, organizationId, tenantId);
        if (segment.isEmpty()) {
            return notFound();
        }

        if (!tapAccess(sc, organizationId, tenantId).coversAllTapsOf(segment.get())) {
            return exclusionsForbidden();
        }

        if (req == null || req.destination() == null || req.destination().isBlank()) {
            return badRequest("A destination is required.");
        }

        CIDR destination;
        try {
            destination = CIDR.parse(req.destination().trim());
        } catch (Exception e) {
            return badRequest("The destination is not an address and not a network in CIDR notation, like "
                    + "10.1.0.0/24.");
        }

        try {
            Exclusion exclusion = segmentation().exclusions().create(segment.get(), destination, req.note());

            return Response.status(Response.Status.CREATED).entity(exclusionResponse(exclusion, true)).build();
        } catch (IllegalArgumentException e) {
            return badRequest(message(e, "Invalid excluded destination."));
        }
    }

    @DELETE
    @Path("/organization/{organization_id}/tenant/{tenant_id}/segments/show/{uuid}/exclusions/show/{exclusion_uuid}")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "deleteSegmentExclusion", summary = "Stop excluding a destination for a segment",
            description = "Attempts to the destination are recorded again from then on. What was not recorded "
                    + "while it was excluded is not restored. Requires the ethernet_monitoring_manage feature "
                    + "permission and access to every tap that records the segment.")
    @ApiResponse(responseCode = "200", description = "Exclusion deleted.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The caller has no access to a tap that records the segment.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Exclusion, segment, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteSegmentExclusion(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Segment UUID.") @PathParam("uuid") UUID uuid,
                                           @Parameter(description = "Exclusion UUID.") @PathParam("exclusion_uuid") UUID exclusionUuid,
                                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<Segment> segment = segmentation().segments().find(uuid, organizationId, tenantId);
        if (segment.isEmpty()) {
            return notFound();
        }

        Optional<Exclusion> exclusion = segmentation().exclusions().find(segment.get(), exclusionUuid);
        if (exclusion.isEmpty()) {
            return notFound();
        }

        if (!tapAccess(sc, organizationId, tenantId).coversAllTapsOf(segment.get())) {
            return exclusionsForbidden();
        }

        segmentation().exclusions().delete(segment.get(), exclusion.get());

        return Response.ok().build();
    }

    @GET
    @Path("/matrix")
    @Operation(operationId = "getSegmentationMatrix", summary = "Get the crossings between segments",
            description = "Returns one cell per pair of groups that talked in the time range, with the sessions "
                    + "per outcome, the busiest services, how much of the crossing policies have in scope and the "
                    + "number of findings in it. A group is a segment, an external network, everything internal "
                    + "that is in no segment, or everything external that is in no external network. Where several "
                    + "taps recorded the same crossing, the tap that saw the most of it counts.")
    @ApiResponse(responseCode = "200", description = "Matrix found.",
            content = @Content(schema = @Schema(implementation = MatrixResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response matrix(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        TapAccess tapAccess = tapAccess(sc, organizationId, tenantId);
        SegmentationViews.Matrix matrix = segmentation().views().matrix(organizationId, tenantId, timeRange,
                tapAccess.accessibleUuids());
        MembershipResolver resolver = segmentation().resolver(organizationId, tenantId);

        Map<Long, Set<UUID>> tapsOfGroups = Maps.newHashMap();
        List<MatrixCellResponse> cells = Lists.newArrayList();
        for (SegmentationViews.Cell cell : matrix.cells()) {
            tapsOfGroups.computeIfAbsent(cell.sourceGroup(), k -> Sets.newLinkedHashSet()).addAll(cell.taps());
            tapsOfGroups.computeIfAbsent(cell.destinationGroup(), k -> Sets.newLinkedHashSet()).addAll(cell.taps());

            List<MatrixServiceResponse> services = Lists.newArrayList();
            for (SegmentationViews.Service service : cell.services()) {
                services.add(MatrixServiceResponse.create(service.l4Type(), service.port(), service.protocol(),
                        service.connected(), service.rejected(), service.noReply(), service.sent()));
            }

            cells.add(MatrixCellResponse.create(cell.sourceGroup(), cell.destinationGroup(), cell.connected(),
                    cell.rejected(), cell.noReply(), cell.sent(), cell.continued(), services, cell.serviceCount(),
                    cell.taps(),
                    cell.firstSeen(), cell.lastSeen(), cell.coverage().name(), cell.policies(),
                    cell.crossedFindings(), cell.attemptedFindings()));
        }

        List<MatrixGroupResponse> groups = Lists.newArrayList();
        for (long group : matrix.groups()) {
            List<UUID> taps = Lists.newArrayList(tapsOfGroups.getOrDefault(group, Collections.emptySet()));

            if (group == Flow.GROUP_UNASSIGNED) {
                groups.add(MatrixGroupResponse.create(group, "UNASSIGNED", "Unassigned", null,
                        "Internal addresses that are in no segment", Collections.emptyList(),
                        Collections.emptyList(), taps));
            } else if (group == Flow.GROUP_INTERNET) {
                groups.add(MatrixGroupResponse.create(group, "INTERNET", "Internet", null,
                        "External addresses that are in no external network", Collections.emptyList(),
                        Collections.emptyList(), taps));
            } else if (group == Flow.GROUP_BROADCAST) {
                groups.add(MatrixGroupResponse.create(group, "BROADCAST", SegmentationViews.BROADCAST_NAME, null,
                        "Broadcast and multicast addresses", Collections.emptyList(), Collections.emptyList(), taps));
            } else {
                Segment segment = resolver.segment(group);

                List<String> networks = Lists.newArrayList();
                List<Long> asns = Lists.newArrayList();
                for (Segment.Member member : segment.members()) {
                    if (member.network() != null) {
                        networks.add(member.network().toString());
                    } else if (member.asn() != null) {
                        asns.add(member.asn());
                    }
                }

                groups.add(MatrixGroupResponse.create(group, segment.kind().name(), segment.name(), segment.uuid(),
                        segment.description(), networks, asns, taps));
            }
        }

        List<PolicyReferenceResponse> policies = Lists.newArrayList();
        for (Policy policy : matrix.policies()) {
            policies.add(PolicyReferenceResponse.create(policy.uuid(), policy.name()));
        }

        return Response.ok(MatrixResponse.create(groups, cells,
                tapResponses(tapAccess.accessible(), organizationId, tenantId), policies,
                matrix.restampPending(), SegmentationViews.FINDINGS_FOR_MATRIX, matrix.findingsLimited())).build();
    }

    @GET
    @Path("/matrix/flows")
    @Operation(operationId = "findSegmentationFlows", summary = "List the flows between two groups of the matrix",
            description = "Returns the flows behind one cell of the matrix, added up over the time range: one "
                    + "entry per source, destination, service and outcome, busiest first by default. Internal "
                    + "sides are single addresses, with the MAC address of the asset that used the address in "
                    + "the time range. External sides are networks, because external addresses are not stored one "
                    + "by one. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Flows found.",
            content = @Content(schema = @Schema(implementation = FlowsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid filters, or unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response flows(@Parameter(hidden = true) @Context SecurityContext sc,
                          @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                          @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                          @Parameter(description = "Group ID of the source side, as returned by the matrix.") @QueryParam("source_group") long sourceGroup,
                          @Parameter(description = "Group ID of the destination side, as returned by the matrix.") @QueryParam("destination_group") long destinationGroup,
                          @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") String timeRangeParameter,
                          @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") @Nullable String filtersParameter,
                          @Parameter(description = "Sorting column: SESSIONS, LAST_SEEN, FIRST_SEEN, SOURCE, DESTINATION, PORT or OUTCOME. Defaults to SESSIONS.") @QueryParam("order_column") @Nullable String orderColumnParam,
                          @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                          @Parameter(description = "Page size. 25 if not set, 250 at most.") @QueryParam("limit") int limit,
                          @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);
        Flows.OrderColumn orderColumn = orderColumn(Flows.OrderColumn.class, orderColumnParam, Flows.OrderColumn.SESSIONS);
        OrderDirection orderDirection = orderDirection(orderDirectionParam, OrderDirection.DESC);

        Flows.Page page = segmentation().views().flowsBetween(organizationId, tenantId, timeRange,
                tapAccess(sc, organizationId, tenantId).accessibleUuids(), sourceGroup, destinationGroup, filters,
                orderColumn, orderDirection, pageSize(limit), Math.max(offset, 0));

        Set<String> internal = Sets.newHashSet();
        for (Flow flow : page.flows()) {
            for (Endpoint endpoint : List.of(flow.source(), flow.destination())) {
                if (endpoint.internal() && endpoint.hasAddress()) {
                    internal.add(InetAddresses.toAddrString(endpoint.address()));
                }
            }
        }

        Addresses addresses = new Addresses(organizationId, tenantId);
        addresses.findAssets(internal, timeRange);

        List<FlowResponse> flows = Lists.newArrayList();
        for (Flow flow : page.flows()) {
            flows.add(FlowResponse.create(
                    side(flow.source(), flow.sourceKey(), flow.l4Type(), addresses),
                    side(flow.destination(), flow.destinationKey(), flow.l4Type(), addresses),
                    flow.l4Type(), flow.port(), flow.protocol(), flow.outcome().name(), flow.sessions(),
                    flow.continued(), flow.tap(), flow.firstSeen(), flow.lastSeen()
            ));
        }

        return Response.ok(FlowsListResponse.create(page.total(), flows, SegmentationEngine.MAX_PORTS_PER_PAIR,
                SegmentationEngine.MAX_ATTEMPT_ROWS_PER_SOURCE)).build();
    }

    @GET
    @Path("/matrix/addresses")
    @Operation(operationId = "findSegmentationGroupAddresses", summary = "List the addresses of a group of the matrix",
            description = "Returns the addresses of a segment, or the internal addresses that are in no segment, "
                    + "that taps recorded traffic of in the time range, with the sessions they opened and the "
                    + "sessions that went to them, and with the MAC address of the asset that used the address in "
                    + "the time range. Where several taps recorded an address, the tap that saw the "
                    + "most of it counts. A session that stays open counts once for every hour it is open, like in "
                    + "the matrix. An address that only was one of many unanswered destinations of a sweep "
                    + "is not stored and missing. Addresses of external networks are not stored one by one and "
                    + "cannot be listed. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Addresses found.",
            content = @Content(schema = @Schema(implementation = MatrixAddressesListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The group is no segment and not the group of internal addresses that are in no segment, invalid filters, or unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response groupAddresses(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                   @Parameter(description = "Group ID as returned by the matrix: the ID of a segment, or 0 for the internal addresses that are in no segment.") @QueryParam("group") long group,
                                   @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") String timeRangeParameter,
                                   @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") @Nullable String filtersParameter,
                                   @Parameter(description = "Sorting column: ADDRESS, SESSIONS_FROM, SESSIONS_TO, ANSWERED, FIRST_SEEN or LAST_SEEN. Defaults to SESSIONS_TO.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                   @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                   @Parameter(description = "Page size. 25 if not set, 250 at most.") @QueryParam("limit") int limit,
                                   @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        if (group != Flow.GROUP_UNASSIGNED) {
            // Not from the resolver, which a node caches: the segment may just have been created on another node.
            Segment segment = segmentation().segments().findAll(organizationId, tenantId).stream()
                    .filter(s -> s.id() == group)
                    .findFirst()
                    .orElse(null);
            if (segment == null || segment.kind() != SegmentKind.INTERNAL) {
                return badRequest("Addresses are only stored for segments and for internal addresses that are in "
                        + "no segment.");
            }
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);
        Flows.AddressOrderColumn orderColumn = orderColumn(Flows.AddressOrderColumn.class, orderColumnParam,
                Flows.AddressOrderColumn.SESSIONS_TO);
        OrderDirection orderDirection = orderDirection(orderDirectionParam, OrderDirection.DESC);

        Flows.AddressPage page = segmentation().views().addressesOf(organizationId, tenantId, timeRange,
                tapAccess(sc, organizationId, tenantId).accessibleUuids(), group, filters, orderColumn,
                orderDirection, pageSize(limit), Math.max(offset, 0));

        Addresses resolved = new Addresses(organizationId, tenantId);
        resolved.findAssets(page.addresses().stream().map(Flows.GroupAddress::address).toList(), timeRange);

        List<MatrixAddressResponse> addresses = Lists.newArrayList();
        for (Flows.GroupAddress address : page.addresses()) {
            addresses.add(MatrixAddressResponse.create(resolved.of(address.address(), L4Type.TCP.name()),
                    address.address(), address.sessionsFrom(), address.sessionsTo(), address.answered(), address.taps(),
                    address.firstSeen(), address.lastSeen()));
        }

        return Response.ok(MatrixAddressesListResponse.create(page.total(), addresses, page.foldedSessions(),
                SegmentationEngine.MAX_ATTEMPT_ROWS_PER_SOURCE)).build();
    }

    @GET
    @Path("/selectors")
    @Operation(operationId = "findSegmentationSelectorOptions", summary = "List what a policy can refer to",
            description = "Returns the segments, external networks and asset tags of the tenant and the protocols "
                    + "the taps detect.")
    @ApiResponse(responseCode = "200", description = "Options found.",
            content = @Content(schema = @Schema(implementation = SelectorOptionsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response selectors(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        TapAccess tapAccess = tapAccess(sc, organizationId, tenantId);
        List<SegmentResponse> segments = Lists.newArrayList();
        for (Segment segment : segmentation().segments().findAll(organizationId, tenantId)) {
            segments.add(segmentResponse(segment, Collections.emptyList(), tapAccess));
        }

        List<AssetTagResponse> tags = Lists.newArrayList();
        for (AssetTag tag : nzyme.getAssetsManager().tags().findAll(organizationId, tenantId)) {
            tags.add(AssetTagResponse.create(tag.uuid(), tag.name(), tag.description(), tag.assetCount(),
                    Collections.emptyList(), tag.createdAt(), tag.updatedAt()));
        }

        return Response.ok(SelectorOptionsResponse.create(segments, tags, PolicyRule.PROTOCOLS)).build();
    }

    @GET
    @Path("/policies")
    @Operation(operationId = "findSegmentationPolicies", summary = "List the segmentation policies of a tenant",
            description = "Every policy comes with its state. HOLDING: taps see traffic in scope and all of it "
                    + "was allowed. CROSSED: a connection that is not allowed was answered. ATTEMPTED: one was "
                    + "tried and not answered. NOT_OBSERVABLE: no tap recorded both sides of the scope, so the "
                    + "absence of findings says nothing. AUDIT and PAUSED follow the status of the policy. "
                    + "Sorted by name by default. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Policies found.",
            content = @Content(schema = @Schema(implementation = PoliciesListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid filters, or unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findPolicies(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                 @Parameter(description = "Time range selector. The state of a policy and its findings are judged by the findings and traffic since the start of the range.") @QueryParam("time_range") String timeRangeParameter,
                                 @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") @Nullable String filtersParameter,
                                 @Parameter(description = "Sorting column: NAME, STATUS, CROSSED_FINDINGS, ATTEMPTED_FINDINGS, LAST_FINDING, CREATED_AT or UPDATED_AT. Defaults to NAME.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                 @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                 @Parameter(description = "Page size. 25 if not set, 250 at most.") @QueryParam("limit") int limit,
                                 @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);
        Policies.OrderColumn orderColumn = orderColumn(Policies.OrderColumn.class, orderColumnParam, Policies.OrderColumn.NAME);
        OrderDirection orderDirection = orderDirection(orderDirectionParam, OrderDirection.ASC);

        long total = segmentation().policies().count(filters, organizationId, tenantId, timeRange.from());
        List<PolicyResponse> policies = Lists.newArrayList();
        for (SegmentationViews.PolicyOverview overview : segmentation().views().policies(organizationId, tenantId,
                timeRange.from(), filters, orderColumn, orderDirection, pageSize(limit), Math.max(offset, 0))) {
            policies.add(policyResponse(overview));
        }

        return Response.ok(PoliciesListResponse.create(total, policies)).build();
    }

    @GET
    @Path("/policies/show/{uuid}")
    @Operation(operationId = "findSegmentationPolicy", summary = "Get a segmentation policy")
    @ApiResponse(responseCode = "200", description = "Policy found.",
            content = @Content(schema = @Schema(implementation = PolicyResponse.class)))
    @ApiResponse(responseCode = "404", description = "Policy, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findPolicy(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "Policy UUID.") @PathParam("uuid") UUID uuid,
                               @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                               @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                               @Parameter(description = "Time range selector. The state of the policy is judged by the findings and traffic since the start of the range.") @QueryParam("time_range") String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<Policy> policy = segmentation().policies().find(uuid, organizationId, tenantId);
        if (policy.isEmpty()) {
            return notFound();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        return Response.ok(policyResponse(segmentation().views().policy(policy.get(), timeRange.from()))).build();
    }

    @POST
    @Path("/organization/{organization_id}/tenant/{tenant_id}/policies")
    @Consumes(MediaType.APPLICATION_JSON)
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "createSegmentationPolicy", summary = "Create a segmentation policy",
            description = "A policy is a scope and a list of allowed crossings, each made of sources, "
                    + "destinations and services. An empty list of sources, destinations or services means any. "
                    + "The scope must name a source or a destination. A new policy is evaluated against sessions "
                    + "recorded from now on.")
    @ApiResponse(responseCode = "201", description = "Policy created.",
            content = @Content(schema = @Schema(implementation = PolicyResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid definition. The response carries the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response createPolicy(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                 SegmentationPolicyRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        if (req == null) {
            return badRequest("No definition passed.");
        }

        try {
            Policy policy = segmentation().policies().create(organizationId, tenantId, req.name(),
                    req.description(), status(req.status()), Boolean.TRUE.equals(req.alertOnConnected()),
                    Boolean.TRUE.equals(req.alertOnAttempted()), req.scope(),
                    req.allowed() == null ? Lists.newArrayList() : req.allowed());

            return Response.status(Response.Status.CREATED)
                    .entity(policyResponse(segmentation().views().policy(policy, DateTime.now().minusDays(1))))
                    .build();
        } catch (IllegalArgumentException e) {
            return badRequest(message(e, "Invalid definition."));
        }
    }

    @PUT
    @Path("/organization/{organization_id}/tenant/{tenant_id}/policies/show/{uuid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "updateSegmentationPolicy", summary = "Update a segmentation policy",
            description = "If the scope or the allowed crossings change, existing findings of the policy are "
                    + "judged again and what is no finding anymore is removed.")
    @ApiResponse(responseCode = "200", description = "Policy updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Invalid definition. The response carries the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Policy, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response updatePolicy(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Policy UUID.") @PathParam("uuid") UUID uuid,
                                 @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                 SegmentationPolicyRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<Policy> policy = segmentation().policies().find(uuid, organizationId, tenantId);
        if (policy.isEmpty()) {
            return notFound();
        }

        if (req == null) {
            return badRequest("No definition passed.");
        }

        try {
            segmentation().policies().update(policy.get(), req.name(), req.description(), status(req.status()),
                    Boolean.TRUE.equals(req.alertOnConnected()), Boolean.TRUE.equals(req.alertOnAttempted()),
                    req.scope(), req.allowed() == null ? Lists.newArrayList() : req.allowed());
        } catch (IllegalArgumentException e) {
            return badRequest(message(e, "Invalid definition."));
        }

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/policies/many/status/{status}")
    @Operation(operationId = "setSegmentationPoliciesStatus", summary = "Activate, pause or set multiple policies to audit",
            description = "Sets the status of every policy in the list that belongs to the tenant. Unknown UUIDs are skipped. At most 250 UUIDs per call. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Action applied.",
            content = @Content(schema = @Schema(implementation = BulkActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown status, missing request body or more than 250 UUIDs.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response setPoliciesStatus(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                      @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                      @Parameter(description = "ACTIVE, AUDIT or PAUSED.") @PathParam("status") String statusParam,
                                      @RequestBody(description = "The UUIDs of the policies to change.", required = true, content = @Content(mediaType = "application/json")) UUIDListRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        if (req == null || req.uuids() == null || req.uuids().size() > MAX_PAGE_SIZE) {
            return badRequest("Pass at most " + MAX_PAGE_SIZE + " UUIDs.");
        }

        PolicyStatus status;
        try {
            status = PolicyStatus.valueOf(statusParam == null ? "" : statusParam.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return badRequest("The status must be ACTIVE, AUDIT or PAUSED.");
        }

        BulkSelection selection = BulkSelection.checked(req.uuids());
        return bulkResponse(BulkResult.of(segmentation().policies().setStatus(selection, status, organizationId,
                tenantId, DateTime.now())));
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/policies/filtered/status/{status}")
    @Operation(operationId = "setFilteredSegmentationPoliciesStatus", summary = "Activate, pause or set all matching policies to audit",
            description = "Sets the status of every policy of the tenant that matches the filters, not only the ones of one page. "
                    + "The filters can refer to the findings of a time range, which is why the time range of the list is passed along. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Action applied.",
            content = @Content(schema = @Schema(implementation = BulkActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown status, invalid filters or missing time range.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response setFilteredPoliciesStatus(@Parameter(hidden = true) @Context SecurityContext sc,
                                              @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                              @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                              @Parameter(description = "ACTIVE, AUDIT or PAUSED.") @PathParam("status") String statusParam,
                                              @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Without it, every entry is selected.") @QueryParam("filters") @Nullable String filtersParameter,
                                              @Parameter(description = "Time range selector, the one the list was loaded with.") @QueryParam("time_range") String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        PolicyStatus status;
        try {
            status = PolicyStatus.valueOf(statusParam == null ? "" : statusParam.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return badRequest("The status must be ACTIVE, AUDIT or PAUSED.");
        }

        BulkSelection selection = BulkSelection.matching(parseFiltersQueryParameter(filtersParameter));
        return bulkResponse(BulkResult.of(segmentation().policies().setStatus(selection, status, organizationId,
                tenantId, parseTimeRangeQueryParameter(timeRangeParameter).from())));
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/policies/many/alerts/{alert}/{enabled}")
    @Operation(operationId = "setSegmentationPoliciesAlert", summary = "Turn an alert option of multiple policies on or off",
            description = "Sets one of the two alert options of every policy in the list that belongs to the tenant. Unknown UUIDs are skipped. At most 250 UUIDs per call. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Action applied.",
            content = @Content(schema = @Schema(implementation = BulkActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown alert or value, missing request body or more than 250 UUIDs.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response setPoliciesAlert(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                     @Parameter(description = "Which alerts: connected for connections that were answered or sent to a broadcast or multicast address, attempted for ones that were rejected or got no reply.") @PathParam("alert") String alertParam,
                                     @Parameter(description = "true or false.") @PathParam("enabled") String enabledParam,
                                     @RequestBody(description = "The UUIDs of the policies to change.", required = true, content = @Content(mediaType = "application/json")) UUIDListRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        if (req == null || req.uuids() == null || req.uuids().size() > MAX_PAGE_SIZE) {
            return badRequest("Pass at most " + MAX_PAGE_SIZE + " UUIDs.");
        }

        FindingClass alert = alertClass(alertParam);
        Boolean enabled = strictBoolean(enabledParam);
        if (alert == null || enabled == null) {
            return badRequest("The alert must be connected or attempted, and turned on with true or off with false.");
        }

        BulkSelection selection = BulkSelection.checked(req.uuids());
        return bulkResponse(BulkResult.of(segmentation().policies().setAlert(selection, alert, enabled,
                organizationId, tenantId, DateTime.now())));
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/policies/filtered/alerts/{alert}/{enabled}")
    @Operation(operationId = "setFilteredSegmentationPoliciesAlert", summary = "Turn an alert option of all matching policies on or off",
            description = "Sets one of the two alert options of every policy of the tenant that matches the filters, not only the "
                    + "ones of one page. The filters can refer to the findings of a time range, which is why the time range of the list is passed along. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Action applied.",
            content = @Content(schema = @Schema(implementation = BulkActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown alert or value, invalid filters or missing time range.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response setFilteredPoliciesAlert(@Parameter(hidden = true) @Context SecurityContext sc,
                                             @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                             @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                             @Parameter(description = "Which alerts: connected for connections that were answered or sent to a broadcast or multicast address, attempted for ones that were rejected or got no reply.") @PathParam("alert") String alertParam,
                                             @Parameter(description = "true or false.") @PathParam("enabled") String enabledParam,
                                             @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Without it, every entry is selected.") @QueryParam("filters") @Nullable String filtersParameter,
                                             @Parameter(description = "Time range selector, the one the list was loaded with.") @QueryParam("time_range") String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        FindingClass alert = alertClass(alertParam);
        Boolean enabled = strictBoolean(enabledParam);
        if (alert == null || enabled == null) {
            return badRequest("The alert must be connected or attempted, and turned on with true or off with false.");
        }

        BulkSelection selection = BulkSelection.matching(parseFiltersQueryParameter(filtersParameter));
        return bulkResponse(BulkResult.of(segmentation().policies().setAlert(selection, alert, enabled,
                organizationId, tenantId, parseTimeRangeQueryParameter(timeRangeParameter).from())));
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/policies/many/delete")
    @Operation(operationId = "deleteSegmentationPolicies", summary = "Delete multiple policies",
            description = "Deletes every policy in the list that belongs to the tenant, with its findings. Unknown UUIDs are skipped. At most 250 UUIDs per call. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Action applied.",
            content = @Content(schema = @Schema(implementation = BulkActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Missing request body or more than 250 UUIDs.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deletePolicies(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                   @RequestBody(description = "The UUIDs of the policies to delete.", required = true, content = @Content(mediaType = "application/json")) UUIDListRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        if (req == null || req.uuids() == null || req.uuids().size() > MAX_PAGE_SIZE) {
            return badRequest("Pass at most " + MAX_PAGE_SIZE + " UUIDs.");
        }

        BulkSelection selection = BulkSelection.checked(req.uuids());
        return bulkResponse(BulkResult.of(segmentation().policies().delete(selection, organizationId, tenantId,
                DateTime.now())));
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/policies/filtered/delete")
    @Operation(operationId = "deleteFilteredSegmentationPolicies", summary = "Delete all matching policies",
            description = "Deletes every policy of the tenant that matches the filters, with its findings, not only the ones of one "
                    + "page. The filters can refer to the findings of a time range, which is why the time range of the list is passed along. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Action applied.",
            content = @Content(schema = @Schema(implementation = BulkActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid filters or missing time range.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteFilteredPolicies(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                           @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Without it, every entry is selected.") @QueryParam("filters") @Nullable String filtersParameter,
                                           @Parameter(description = "Time range selector, the one the list was loaded with.") @QueryParam("time_range") String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        BulkSelection selection = BulkSelection.matching(parseFiltersQueryParameter(filtersParameter));
        return bulkResponse(BulkResult.of(segmentation().policies().delete(selection, organizationId, tenantId,
                parseTimeRangeQueryParameter(timeRangeParameter).from())));
    }

    @DELETE
    @Path("/organization/{organization_id}/tenant/{tenant_id}/policies/show/{uuid}")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "deleteSegmentationPolicy", summary = "Delete a segmentation policy",
            description = "Deletes the policy and its findings.")
    @ApiResponse(responseCode = "200", description = "Policy deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Policy, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deletePolicy(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Policy UUID.") @PathParam("uuid") UUID uuid,
                                 @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<Policy> policy = segmentation().policies().find(uuid, organizationId, tenantId);
        if (policy.isEmpty()) {
            return notFound();
        }

        segmentation().policies().delete(policy.get());

        return Response.ok().build();
    }

    @POST
    @Path("/organization/{organization_id}/tenant/{tenant_id}/policies/show/{uuid}/allowed")
    @Consumes(MediaType.APPLICATION_JSON)
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "allowSegmentationPolicyCrossing", summary = "Add an allowed crossing to a policy",
            description = "Adds one allowed crossing to the policy, for example to accept what a finding reported. "
                    + "Existing findings of the policy are judged again afterwards.")
    @ApiResponse(responseCode = "200", description = "Crossing added.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Invalid crossing. The response carries the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Policy, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response allow(@Parameter(hidden = true) @Context SecurityContext sc,
                          @Parameter(description = "Policy UUID.") @PathParam("uuid") UUID uuid,
                          @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                          @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                          SegmentationPolicyAllowRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<Policy> policy = segmentation().policies().find(uuid, organizationId, tenantId);
        if (policy.isEmpty()) {
            return notFound();
        }

        if (req == null || req.rule() == null) {
            return badRequest("No crossing passed.");
        }

        try {
            segmentation().policies().allow(policy.get(), req.rule());
        } catch (IllegalArgumentException e) {
            return badRequest(message(e, "Invalid crossing."));
        }

        return Response.ok().build();
    }

    @POST
    @Path("/organization/{organization_id}/tenant/{tenant_id}/policies/preview")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "previewSegmentationPolicy", summary = "Show what a policy definition does to recorded traffic",
            description = "Evaluates a scope and allowed crossings against the flows of the time range without "
                    + "saving anything. Returns what was observed inside the scope, grouped by segment or "
                    + "network on each side and by service, and what the allowed crossings make of each group. "
                    + "Used to fill in a new policy from what the network does, and to see what a policy would "
                    + "have reported before it is turned on. The selectors of each row can be used as they are "
                    + "in an allowed crossing.")
    @ApiResponse(responseCode = "200", description = "Preview computed.",
            content = @Content(schema = @Schema(implementation = PolicyPreviewResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid definition. The response carries the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response preview(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") String timeRangeParameter,
                            SegmentationPolicyPreviewRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        if (req == null) {
            return badRequest("No definition passed.");
        }

        List<PolicyRule> allowed = req.allowed() == null ? Lists.newArrayList() : req.allowed();
        try {
            segmentation().policies().validateDefinition(organizationId, tenantId, req.scope(), allowed);
        } catch (IllegalArgumentException e) {
            return badRequest(message(e, "Invalid definition."));
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        SegmentationViews.Preview preview = segmentation().views()
                .preview(organizationId, tenantId, req.scope(), allowed, timeRange,
                        tapAccess(sc, organizationId, tenantId).accessibleUuids());

        List<PolicyPreviewRowResponse> rows = Lists.newArrayList();
        for (SegmentationViews.PreviewRow row : preview.rows()) {
            rows.add(PolicyPreviewRowResponse.create(
                    PolicyPreviewSideResponse.create(row.source().selector(), row.source().label()),
                    PolicyPreviewSideResponse.create(row.destination().selector(), row.destination().label()),
                    row.l4Type(), row.port(), row.protocol(), row.verdict().name(), row.connected(), row.rejected(),
                    row.noReply(), row.sent(), row.sourceHosts(), row.destinationHosts(), row.sourceSample(),
                    row.destinationSample(), row.firstSeen(), row.lastSeen()
            ));
        }

        return Response.ok(PolicyPreviewResponse.create(rows, preview.allowedSessions(), preview.crossedSessions(),
                preview.attemptedSessions(), preview.indeterminateSessions(), preview.unusedAllowed(),
                preview.truncated(), SegmentationViews.PREVIEW_FLOW_LIMIT, SegmentationViews.PREVIEW_ROW_LIMIT)).build();
    }

    @GET
    @Path("/findings")
    @Operation(operationId = "findSegmentationFindings", summary = "List segmentation findings of a tenant",
            description = "Returns the findings that were active at any point inside the time range, most "
                    + "recently seen first by default. A finding is everything one source did against one policy, "
                    + "separately for answered connections (CROSSED) and for rejected or unanswered ones "
                    + "(ATTEMPTED). Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Findings found.",
            content = @Content(schema = @Schema(implementation = FindingsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid filters, or unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findFindings(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                 @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") String timeRangeParameter,
                                 @Parameter(description = "Only findings of the policy with this UUID.") @QueryParam("policy") @Nullable UUID policy,
                                 @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") @Nullable String filtersParameter,
                                 @Parameter(description = "Sorting column: LAST_SEEN, FIRST_SEEN, POLICY_NAME, FINDING_CLASS, SOURCE, SESSION_COUNT or TARGET_COUNT. Defaults to LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                 @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                 @Parameter(description = "Page size. 25 if not set, 250 at most.") @QueryParam("limit") int limit,
                                 @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);
        Findings.OrderColumn orderColumn = orderColumn(Findings.OrderColumn.class, orderColumnParam, Findings.OrderColumn.LAST_SEEN);
        OrderDirection orderDirection = orderDirection(orderDirectionParam, OrderDirection.DESC);

        MembershipResolver resolver = segmentation().resolver(organizationId, tenantId);
        Addresses addresses = new Addresses(organizationId, tenantId);

        long total = segmentation().findings().count(organizationId, tenantId, timeRange, policy, filters);
        List<FindingResponse> findings = Lists.newArrayList();
        for (Finding finding : segmentation().findings().findAll(organizationId, tenantId, timeRange, policy,
                filters, orderColumn, orderDirection, pageSize(limit), Math.max(offset, 0))) {
            findings.add(findingResponse(finding, resolver, addresses));
        }

        return Response.ok(FindingsListResponse.create(total, findings)).build();
    }

    @GET
    @Path("/findings/show/{uuid}/targets")
    @Operation(operationId = "findSegmentationFindingTargets", summary = "List the targets of a segmentation finding",
            description = "Returns what the source of a finding reached, sent to or tried to reach against the "
                    + "policy, most recently seen first by default. A finding keeps its most recent targets, and "
                    + "the list is of those. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Targets found.",
            content = @Content(schema = @Schema(implementation = FindingTargetsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid filters, or unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Finding, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findFindingTargets(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @Parameter(description = "Finding UUID.") @PathParam("uuid") UUID uuid,
                                       @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                       @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                       @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") @Nullable String filtersParameter,
                                       @Parameter(description = "Sorting column: LAST_SEEN, FIRST_SEEN, ADDRESS, PORT, OUTCOME or SESSIONS. Defaults to LAST_SEEN.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                       @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                       @Parameter(description = "Page size. 25 if not set, 250 at most.") @QueryParam("limit") int limit,
                                       @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<Finding> found = segmentation().findings().find(uuid, organizationId, tenantId);
        if (found.isEmpty()) {
            return notFound();
        }

        Finding finding = found.get();
        Filters filters = parseFiltersQueryParameter(filtersParameter);
        Findings.TargetOrderColumn orderColumn = orderColumn(Findings.TargetOrderColumn.class, orderColumnParam,
                Findings.TargetOrderColumn.LAST_SEEN);
        OrderDirection orderDirection = orderDirection(orderDirectionParam, OrderDirection.DESC);

        MembershipResolver resolver = segmentation().resolver(organizationId, tenantId);
        Addresses addresses = new Addresses(organizationId, tenantId);
        UUID tap = finding.tapUuids().isEmpty() ? new UUID(0, 0) : finding.tapUuids().get(0);

        long total = segmentation().findings().countTargets(finding, filters);
        List<FindingTargetResponse> targets = Lists.newArrayList();
        for (Finding.Target target : segmentation().findings().findTargets(finding, filters, orderColumn,
                orderDirection, pageSize(limit), Math.max(offset, 0))) {
            targets.add(targetResponse(target, resolver, tap, addresses));
        }

        return Response.ok(FindingTargetsListResponse.create(total, targets)).build();
    }

    @GET
    @Path("/findings/show/{uuid}")
    @Operation(operationId = "findSegmentationFinding", summary = "Get a segmentation finding")
    @ApiResponse(responseCode = "200", description = "Finding found.",
            content = @Content(schema = @Schema(implementation = FindingResponse.class)))
    @ApiResponse(responseCode = "404", description = "Finding, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findFinding(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Finding UUID.") @PathParam("uuid") UUID uuid,
                                @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<Finding> finding = segmentation().findings().find(uuid, organizationId, tenantId);
        if (finding.isEmpty()) {
            return notFound();
        }

        return Response.ok(findingResponse(finding.get(), segmentation().resolver(organizationId, tenantId),
                new Addresses(organizationId, tenantId))).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/findings/many/delete")
    @Operation(operationId = "deleteSegmentationFindings", summary = "Dismiss multiple findings",
            description = "Deletes every finding in the list that belongs to the tenant. A finding comes back as a new one if its "
                    + "source does the same again. Unknown UUIDs are skipped. At most 250 UUIDs per call. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Action applied.",
            content = @Content(schema = @Schema(implementation = BulkActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Missing request body or more than 250 UUIDs.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteFindings(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                   @RequestBody(description = "The UUIDs of the findings to delete.", required = true, content = @Content(mediaType = "application/json")) UUIDListRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        if (req == null || req.uuids() == null || req.uuids().size() > MAX_PAGE_SIZE) {
            return badRequest("Pass at most " + MAX_PAGE_SIZE + " UUIDs.");
        }

        BulkSelection selection = BulkSelection.checked(req.uuids());
        return bulkResponse(BulkResult.of(segmentation().findings().delete(selection, organizationId, tenantId,
                null, null)));
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/findings/filtered/delete")
    @Operation(operationId = "deleteFilteredSegmentationFindings", summary = "Dismiss all matching findings",
            description = "Deletes every finding of the tenant that was active at any point inside the time range and matches the "
                    + "filters, not only the ones of one page. A finding comes back as a new one if its source does "
                    + "the same again. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Action applied.",
            content = @Content(schema = @Schema(implementation = BulkActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid filters or missing time range.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteFilteredFindings(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                           @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Without it, every entry is selected.") @QueryParam("filters") @Nullable String filtersParameter,
                                           @Parameter(description = "Time range selector, the one the list was loaded with.") @QueryParam("time_range") String timeRangeParameter,
                                           @Parameter(description = "Only findings of the policy with this UUID.") @QueryParam("policy") @Nullable UUID policy) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        BulkSelection selection = BulkSelection.matching(parseFiltersQueryParameter(filtersParameter));
        return bulkResponse(BulkResult.of(segmentation().findings().delete(selection, organizationId, tenantId,
                parseTimeRangeQueryParameter(timeRangeParameter), policy)));
    }

    @DELETE
    @Path("/organization/{organization_id}/tenant/{tenant_id}/findings/show/{uuid}")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "deleteSegmentationFinding", summary = "Dismiss a segmentation finding",
            description = "Deletes the finding. It comes back if the source does the same again. To accept what a "
                    + "finding reported for good, add an allowed crossing to its policy.")
    @ApiResponse(responseCode = "200", description = "Finding deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Finding, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteFinding(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Finding UUID.") @PathParam("uuid") UUID uuid,
                                  @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<Finding> finding = segmentation().findings().find(uuid, organizationId, tenantId);
        if (finding.isEmpty()) {
            return notFound();
        }

        segmentation().findings().delete(finding.get());

        return Response.ok().build();
    }

    private class Addresses {

        private final UUID organizationId;
        private final UUID tenantId;
        private final ReconAddressResponses builder = new ReconAddressResponses(nzyme);
        private final Map<String, Optional<L4AddressResponse>> cache = Maps.newHashMap();
        private final Map<UUID, String> tagNames = Maps.newHashMap();
        private Map<String, Topology.AddressAsset> assets = Collections.emptyMap();

        Addresses(UUID organizationId, UUID tenantId) {
            this.organizationId = organizationId;
            this.tenantId = tenantId;

            for (AssetTag tag : nzyme.getAssetsManager().tags().findAll(organizationId, tenantId)) {
                tagNames.put(tag.uuid(), tag.name());
            }
        }

        // Flows store no MAC address. Has to be called before of() for an address to come with one.
        void findAssets(Collection<String> addresses, TimeRange timeRange) {
            assets = nzyme.getEthernet().topology().findAssetsOfAddresses(organizationId, tenantId, timeRange, addresses);
        }

        @Nullable
        L4AddressResponse of(@Nullable String address, String l4Type) {
            if (address == null) {
                return null;
            }

            return cache.computeIfAbsent(address, a -> {
                Topology.AddressAsset asset = assets.get(a);

                return Optional.ofNullable(builder.addressResponse(
                        a, null, asset == null ? null : asset.mac(), "UDP".equals(l4Type) ? L4Type.UDP : L4Type.TCP,
                        organizationId, tenantId
                ));
            }).orElse(null);
        }

        List<String> tags(Endpoint endpoint) {
            List<String> names = Lists.newArrayList();
            for (UUID tag : endpoint.tags()) {
                String name = tagNames.get(tag);
                if (name != null) {
                    names.add(name);
                }
            }

            Collections.sort(names);
            return names;
        }

    }

    // The matches are counted over every tap that records the segment and are only for who has access to all of them.
    private static SegmentExclusionResponse exclusionResponse(Exclusion exclusion, boolean withMatches) {
        return SegmentExclusionResponse.create(exclusion.uuid(), exclusion.destination().toString(), exclusion.note(),
                withMatches ? exclusion.matchCount() : null, withMatches ? exclusion.lastMatchedAt() : null,
                exclusion.createdAt());
    }

    private static FlowSideResponse side(Endpoint endpoint, String key, String l4Type, Addresses addresses) {
        return FlowSideResponse.create(
                key,
                endpoint.internal(),
                endpoint.broadcast(),
                endpoint.hasAddress() ? addresses.of(InetAddresses.toAddrString(endpoint.address()), l4Type) : null,
                endpoint.segment() == null ? null : endpoint.segment().uuid(),
                endpoint.segment() == null ? null : endpoint.segment().name(),
                endpoint.asn(),
                endpoint.asnName(),
                endpoint.asnDomain(),
                endpoint.countryCode(),
                addresses.tags(endpoint)
        );
    }

    private static FindingTargetResponse targetResponse(Finding.Target target,
                                                        MembershipResolver resolver,
                                                        UUID tap,
                                                        Addresses addresses) {
        Endpoint destination = Findings.destination(resolver, tap, target);

        return FindingTargetResponse.create(
                addresses.of(target.address(), target.l4Type()),
                target.address(),
                destination.internal(),
                destination.segment() == null ? null : destination.segment().name(),
                target.l4Type(),
                target.port(),
                target.protocol(),
                target.outcome().name(),
                target.sessions(),
                new DateTime(target.firstSeen()),
                new DateTime(target.lastSeen())
        );
    }

    private FindingResponse findingResponse(Finding finding, MembershipResolver resolver, Addresses addresses) {
        UUID tap = finding.tapUuids().isEmpty() ? new UUID(0, 0) : finding.tapUuids().get(0);

        Endpoint source = null;
        if (finding.sourceAddress() != null) {
            source = resolver.resolve(tap, InetAddresses.forString(finding.sourceAddress()), finding.sourceInternal(),
                    finding.sourceAsn(), finding.sourceCountryCode());
        } else if (!finding.sourceAddresses().isEmpty()) {
            source = resolver.resolve(tap, InetAddresses.forString(finding.sourceAddresses().get(0)),
                    finding.sourceInternal(), finding.sourceAsn(), finding.sourceCountryCode());
        }

        String label;
        if (finding.sourceAddress() != null) {
            label = finding.sourceAddress();
        } else if (source != null && source.segment() != null) {
            label = source.segment().name();
        } else if (finding.sourceAsn() != null) {
            label = "AS" + finding.sourceAsn();
        } else if (finding.sourceCountryCode() != null) {
            label = finding.sourceCountryCode();
        } else {
            label = "Internet";
        }

        List<FindingTargetResponse> targets = Lists.newArrayList();
        for (Finding.Target target : finding.targets()) {
            targets.add(targetResponse(target, resolver, tap, addresses));
        }

        return FindingResponse.create(
                finding.uuid(),
                finding.policyUuid(),
                finding.policyName(),
                finding.findingClass().name(),
                finding.sourceKey(),
                label,
                addresses.of(finding.sourceAddress(), "TCP"),
                finding.sourceInternal(),
                source == null || source.segment() == null ? null : source.segment().name(),
                finding.sourceAsn(),
                finding.sourceCountryCode(),
                finding.sourceAddresses(),
                targets,
                finding.targetCount(),
                Findings.MAX_TARGETS,
                Findings.MAX_SOURCE_ADDRESSES,
                finding.sessionCount(),
                finding.tapUuids(),
                finding.audit(),
                finding.firstSeen(),
                finding.lastSeen()
        );
    }

    private static PolicyResponse policyResponse(SegmentationViews.PolicyOverview overview) {
        Policy policy = overview.policy();

        return PolicyResponse.create(
                policy.uuid(),
                policy.name(),
                policy.description(),
                policy.status().name(),
                policy.alertOnConnected(),
                policy.alertOnAttempted(),
                policy.scope(),
                policy.allowed(),
                overview.state().name(),
                overview.summary().crossedFindings(),
                overview.summary().attemptedFindings(),
                overview.summary().lastCrossed(),
                overview.summary().lastAttempted(),
                Findings.MAX_FINDINGS_PER_POLICY,
                overview.findingsLimitReached(),
                policy.createdAt(),
                policy.updatedAt()
        );
    }

    private static SegmentResponse segmentResponse(Segment segment, List<Policy> policies, TapAccess tapAccess) {
        List<SegmentMemberResponse> members = Lists.newArrayList();
        for (Segment.Member member : segment.members()) {
            members.add(SegmentMemberResponse.create(member.type().name(),
                    member.network() == null ? null : member.network().toString(), member.asn()));
        }

        List<PolicyReferenceResponse> referencing = Lists.newArrayList();
        for (Policy policy : policies) {
            if (Policies.references(policy, PolicyRule.SelectorType.SEGMENT, segment.uuid().toString())) {
                referencing.add(PolicyReferenceResponse.create(policy.uuid(), policy.name()));
            }
        }

        // Taps the caller has no access to are counted and not named. Deleted taps stay in the list, so that
        // they can be taken off the segment.
        List<UUID> taps = null;
        int inaccessibleTaps = 0;
        if (segment.tapUuids() != null) {
            taps = Lists.newArrayList();
            for (UUID tap : segment.tapUuids()) {
                if (tapAccess.hides(tap)) {
                    inaccessibleTaps++;
                } else {
                    taps.add(tap);
                }
            }
        }

        return SegmentResponse.create(segment.uuid(), segment.kind().name(), segment.name(), segment.description(),
                segment.notes(), taps, inaccessibleTaps, members, referencing, segment.createdAt(),
                segment.updatedAt());
    }

    private record TapAccess(List<Tap> accessible, Set<UUID> ofTenant) {

        List<UUID> accessibleUuids() {
            List<UUID> uuids = Lists.newArrayList();
            for (Tap tap : accessible) {
                uuids.add(tap.uuid());
            }

            return uuids;
        }

        boolean coversAllTapsOf(Segment segment) {
            for (UUID tap : ofTenant) {
                if (segment.coversTap(tap) && hides(tap)) {
                    return false;
                }
            }

            return true;
        }

        // A tap of the tenant that the caller has no access to. A deleted tap is not hidden.
        boolean hides(UUID tap) {
            return ofTenant.contains(tap) && !accessibleUuids().contains(tap);
        }

        // New taps have to be accessible. Taps the segment is limited to already can stay.
        boolean mayLimitTo(@Nullable List<UUID> requested, @Nullable List<UUID> current) {
            if (requested == null) {
                return true;
            }

            List<UUID> accessible = accessibleUuids();
            for (UUID tap : requested) {
                if (tap == null || !accessible.contains(tap) && (current == null || !current.contains(tap))) {
                    return false;
                }
            }

            return true;
        }

        // What was requested, and what the caller cannot see and therefore cannot have meant to remove.
        @Nullable
        List<UUID> limitedTaps(@Nullable List<UUID> requested, @Nullable List<UUID> current) {
            Set<UUID> taps = Sets.newLinkedHashSet();
            if (requested != null) {
                taps.addAll(requested);
            }

            if (current != null) {
                for (UUID tap : current) {
                    if (hides(tap)) {
                        taps.add(tap);
                    }
                }
            }

            return taps.isEmpty() ? null : Lists.newArrayList(taps);
        }

    }

    private TapAccess tapAccess(SecurityContext sc, UUID organizationId, UUID tenantId) {
        List<UUID> accessibleByUser = nzyme.getTapManager().allTapUUIDsAccessibleByUser(getAuthenticatedUser(sc));

        List<Tap> accessible = Lists.newArrayList();
        Set<UUID> ofTenant = Sets.newHashSet();
        for (Tap tap : nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)) {
            ofTenant.add(tap.uuid());
            if (accessibleByUser.contains(tap.uuid())) {
                accessible.add(tap);
            }
        }

        return new TapAccess(accessible, ofTenant);
    }

    private List<SegmentationTapResponse> tapResponses(List<Tap> accessible, UUID organizationId, UUID tenantId) {
        Map<UUID, String> locations = Maps.newHashMap();

        List<SegmentationTapResponse> taps = Lists.newArrayList();
        for (Tap tap : accessible) {
            String location = null;
            if (tap.locationId() != null) {
                location = locations.computeIfAbsent(tap.locationId(), id -> nzyme.getAuthenticationService()
                        .findTenantLocation(id, organizationId, tenantId)
                        .map(l -> l.name())
                        .orElse(""));
            }

            taps.add(SegmentationTapResponse.create(tap.uuid(), tap.name(), tap.locationId(),
                    location == null || location.isEmpty() ? null : location));
        }

        return taps;
    }

    private static List<Segment.Member> members(@Nullable List<SegmentMemberRequest> requested) {
        List<Segment.Member> members = Lists.newArrayList();
        if (requested == null) {
            return members;
        }

        for (SegmentMemberRequest member : requested) {
            if (member == null) {
                continue;
            }

            if ("ASN".equalsIgnoreCase(member.type())) {
                if (member.asn() == null) {
                    throw new IllegalArgumentException("An autonomous system number is missing.");
                }
                members.add(Segment.Member.ofAsn(member.asn()));
                continue;
            }

            if (member.network() == null || member.network().isBlank()) {
                throw new IllegalArgumentException("A network is empty.");
            }

            try {
                members.add(Segment.Member.ofNetwork(CIDR.parse(member.network().trim())));
            } catch (Exception e) {
                throw new IllegalArgumentException("\"" + member.network().trim() + "\" is not a network in CIDR "
                        + "notation, like 10.1.0.0/16.");
            }
        }

        return members;
    }

    private static PolicyStatus status(@Nullable String status) {
        if (status == null || status.isBlank()) {
            return PolicyStatus.AUDIT;
        }

        try {
            return PolicyStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The status must be ACTIVE, AUDIT or PAUSED.");
        }
    }

    private static String names(List<Policy> policies) {
        List<String> names = Lists.newArrayList();
        for (Policy policy : policies) {
            names.add("\"" + policy.name() + "\"");
        }

        return (names.size() == 1 ? "policy " : "policies ") + String.join(", ", names);
    }

    private static String message(IllegalArgumentException e, String fallback) {
        // Enum lookups fail with a message that is not for the user.
        return e.getMessage() == null || e.getMessage().startsWith("No enum constant") ? fallback : e.getMessage();
    }

}
