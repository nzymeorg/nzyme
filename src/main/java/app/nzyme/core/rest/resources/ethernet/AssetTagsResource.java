package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.assets.tags.AssetTag;
import app.nzyme.core.assets.tags.AssetTags;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.segmentation.BulkResult;
import app.nzyme.core.ethernet.segmentation.BulkSelection;
import app.nzyme.core.ethernet.segmentation.Policy;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.requests.AssetTagRequest;
import app.nzyme.core.rest.requests.UUIDListRequest;
import app.nzyme.core.rest.responses.ethernet.assets.AssetTagAssetResponse;
import app.nzyme.core.rest.responses.ethernet.assets.AssetTagAssetsListResponse;
import app.nzyme.core.rest.responses.ethernet.assets.AssetTagResponse;
import app.nzyme.core.rest.responses.ethernet.assets.AssetTagSuggestionResponse;
import app.nzyme.core.rest.responses.ethernet.assets.AssetTagSuggestionsListResponse;
import app.nzyme.core.rest.responses.ethernet.assets.AssetTagsListResponse;
import app.nzyme.core.rest.responses.ethernet.segmentation.BulkActionResponse;
import app.nzyme.core.rest.responses.ethernet.segmentation.BulkActionSkippedResponse;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
import app.nzyme.core.util.filters.Filters;
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
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/assets/tags")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Asset Tags", description = "Labels on assets, like db-servers. An asset can have any number of tags. "
        + "Tags group assets independent of where they are on the network and can be used as source or "
        + "destination in segmentation policies.")
public class AssetTagsResource extends TapDataHandlingResource {

    private static final String MANAGE_PERMISSION = "ethernet_assets_manage";

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 250;

    @Inject
    private NzymeNode nzyme;

    private AssetTags tags() {
        return nzyme.getAssetsManager().tags();
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(ErrorResponse.create(message)).build();
    }

    private static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    private static int pageSize(int limit) {
        return limit <= 0 ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
    }

    private static Response bulkResponse(BulkResult result) {
        List<BulkActionSkippedResponse> skipped = Lists.newArrayList();
        for (BulkResult.Skipped entry : result.skipped()) {
            skipped.add(BulkActionSkippedResponse.create(entry.uuid(), entry.name(), entry.reason()));
        }

        return Response.ok(BulkActionResponse.create(result.affected(), skipped)).build();
    }

    @GET
    @Operation(operationId = "findAssetTags", summary = "List the asset tags of a tenant",
            description = "Sorted by name by default. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Tags found.",
            content = @Content(schema = @Schema(implementation = AssetTagsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid filters, or unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") @Nullable String filtersParameter,
                            @Parameter(description = "Sorting column: NAME, DESCRIPTION, ASSET_COUNT, CREATED_AT or UPDATED_AT. Defaults to NAME.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size, 250 at most. Defaults to 25.")
                            @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);

        AssetTags.OrderColumn orderColumn = AssetTags.OrderColumn.NAME;
        if (orderColumnParam != null && !orderColumnParam.isBlank()) {
            try {
                orderColumn = AssetTags.OrderColumn.valueOf(orderColumnParam.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return badRequest("Unknown sorting column [" + orderColumnParam + "].");
            }
        }

        OrderDirection orderDirection = OrderDirection.ASC;
        if (orderDirectionParam != null && !orderDirectionParam.isBlank()) {
            try {
                orderDirection = OrderDirection.valueOf(orderDirectionParam.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return badRequest("Unknown sorting direction [" + orderDirectionParam + "].");
            }
        }

        List<Policy> policies = nzyme.getEthernet().segmentation().policies().findAll(organizationId, tenantId);

        long total = tags().count(filters, organizationId, tenantId);
        List<AssetTagResponse> tags = Lists.newArrayList();
        for (AssetTag tag : tags().findAll(filters, orderColumn, orderDirection, pageSize(limit), Math.max(offset, 0),
                organizationId, tenantId)) {
            tags.add(response(tag, policies));
        }

        return Response.ok(AssetTagsListResponse.create(total, tags)).build();
    }

    @GET
    @Path("/show/{uuid}")
    @Operation(operationId = "findAssetTag", summary = "Get an asset tag")
    @ApiResponse(responseCode = "200", description = "Tag found.",
            content = @Content(schema = @Schema(implementation = AssetTagResponse.class)))
    @ApiResponse(responseCode = "404", description = "Tag, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Tag UUID.") @PathParam("uuid") UUID uuid,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<AssetTag> tag = tags().find(uuid, organizationId, tenantId);
        if (tag.isEmpty()) {
            return notFound();
        }

        return Response.ok(response(tag.get(),
                nzyme.getEthernet().segmentation().policies().findAll(organizationId, tenantId))).build();
    }

    @GET
    @Path("/suggestions")
    @Operation(operationId = "findAssetTagSuggestions", summary = "List tags that assets could get",
            description = "Suggests tags by the ports assets serve according to their profile, for example "
                    + "db-servers for assets that accept connections on 5432/tcp. Assets that carry the suggested "
                    + "tag already are left out. Only the served ports with the most sessions are read. The "
                    + "response carries that limit and says if there were more.")
    @ApiResponse(responseCode = "200", description = "Suggestions found.",
            content = @Content(schema = @Schema(implementation = AssetTagSuggestionsListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response suggestions(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Map<String, UUID> existing = Maps.newHashMap();
        for (AssetTag tag : tags().findAll(organizationId, tenantId)) {
            existing.put(tag.name().toLowerCase(), tag.uuid());
        }

        AssetTags.Suggestions found = tags().suggestions(organizationId, tenantId);

        List<AssetTagSuggestionResponse> suggestions = Lists.newArrayList();
        for (AssetTags.Suggestion suggestion : found.suggestions()) {
            Optional<AssetEntry> asset = nzyme.getAssetsManager()
                    .findAsset(suggestion.assetUuid(), organizationId, tenantId);
            if (asset.isEmpty()) {
                continue;
            }

            suggestions.add(AssetTagSuggestionResponse.create(suggestion.tag(),
                    existing.get(suggestion.tag().toLowerCase()),
                    assetResponse(asset.get().uuid(), asset.get().mac(), asset.get().hostnames(),
                            asset.get().ipAddresses(), organizationId, tenantId),
                    suggestion.ports(), suggestion.sessions()));
        }

        return Response.ok(AssetTagSuggestionsListResponse.create(suggestions, found.limit(), found.truncated()))
                .build();
    }

    @GET
    @Path("/asset/{asset_id}")
    @Operation(operationId = "findAssetTagsOfAsset", summary = "List the tags of an asset")
    @ApiResponse(responseCode = "200", description = "Tags found.",
            content = @Content(schema = @Schema(implementation = AssetTagsListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Asset, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findOfAsset(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                                @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAsset(assetId, organizationId, tenantId);
        if (asset.isEmpty()) {
            return notFound();
        }

        List<AssetTagResponse> tags = Lists.newArrayList();
        for (AssetTag tag : tags().findTagsOfAsset(asset.get().id())) {
            tags.add(response(tag, Lists.newArrayList()));
        }

        return Response.ok(AssetTagsListResponse.create(tags.size(), tags)).build();
    }

    @POST
    @Path("/organization/{organization_id}/tenant/{tenant_id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "createAssetTag", summary = "Create an asset tag")
    @ApiResponse(responseCode = "201", description = "Tag created.",
            content = @Content(schema = @Schema(implementation = AssetTagResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid name or description. The response carries the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response create(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           AssetTagRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        if (req == null) {
            return badRequest("No tag passed.");
        }

        try {
            AssetTag tag = tags().create(organizationId, tenantId, req.name(), req.description());
            return Response.status(Response.Status.CREATED).entity(response(tag, Lists.newArrayList())).build();
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PUT
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{uuid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "updateAssetTag", summary = "Rename or describe an asset tag")
    @ApiResponse(responseCode = "200", description = "Tag updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Invalid name or description. The response carries the reason.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Tag, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response update(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Tag UUID.") @PathParam("uuid") UUID uuid,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           AssetTagRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<AssetTag> tag = tags().find(uuid, organizationId, tenantId);
        if (tag.isEmpty()) {
            return notFound();
        }

        if (req == null) {
            return badRequest("No tag passed.");
        }

        try {
            tags().update(tag.get(), req.name(), req.description());
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }

        return Response.ok().build();
    }

    @PUT
    @Path("/organization/{organization_id}/tenant/{tenant_id}/many/delete")
    @Consumes(MediaType.APPLICATION_JSON)
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "deleteAssetTags", summary = "Delete multiple asset tags",
            description = "Deletes every tag in the list that belongs to the tenant and removes it from all "
                    + "assets. A tag that a segmentation policy refers to is left alone and returned as skipped. "
                    + "Unknown UUIDs are ignored. At most 250 UUIDs per call. Requires the ethernet_assets_manage "
                    + "feature permission.")
    @ApiResponse(responseCode = "200", description = "Action applied.",
            content = @Content(schema = @Schema(implementation = BulkActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Missing request body or more than 250 UUIDs.",
            content = @Content)
    @ApiResponse(responseCode = "404",
            description = "Organization or tenant not found, or not accessible by the calling user.",
            content = @Content)
    public Response deleteMany(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "Organization UUID.")
                               @PathParam("organization_id") UUID organizationId,
                               @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                               @RequestBody(description = "The UUIDs of the tags to delete.", required = true,
                                       content = @Content(mediaType = "application/json")) UUIDListRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        if (req == null || req.uuids() == null || req.uuids().size() > MAX_PAGE_SIZE) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        return deleteSelection(BulkSelection.checked(req.uuids()), organizationId, tenantId);
    }

    @PUT
    @Path("/organization/{organization_id}/tenant/{tenant_id}/filtered/delete")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "deleteFilteredAssetTags", summary = "Delete all matching asset tags",
            description = "Deletes every tag of the tenant that matches the filters, not only the ones of one "
                    + "page, and removes it from all assets. A tag that a segmentation policy refers to is left "
                    + "alone and returned as skipped. Requires the ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Action applied.",
            content = @Content(schema = @Schema(implementation = BulkActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid filters.", content = @Content)
    @ApiResponse(responseCode = "404",
            description = "Organization or tenant not found, or not accessible by the calling user.",
            content = @Content)
    public Response deleteFiltered(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Organization UUID.")
                                   @PathParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                   @Parameter(description = "JSON encoded filter definition as produced by the "
                                           + "web interface filter builder. Without it, every tag is selected.")
                                   @QueryParam("filters") @Nullable String filtersParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        return deleteSelection(BulkSelection.matching(parseFiltersQueryParameter(filtersParameter)), organizationId,
                tenantId);
    }

    @DELETE
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{uuid}")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "deleteAssetTag", summary = "Delete an asset tag",
            description = "Removes the tag from all assets. A tag that segmentation policies refer to cannot be deleted.")
    @ApiResponse(responseCode = "200", description = "Tag deleted.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Policies refer to the tag. The response names them.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Tag, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response delete(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Tag UUID.") @PathParam("uuid") UUID uuid,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<AssetTag> tag = tags().find(uuid, organizationId, tenantId);
        if (tag.isEmpty()) {
            return notFound();
        }

        List<String> referencing = AssetTags.referencingPolicies(uuid,
                nzyme.getEthernet().segmentation().policies().findAll(organizationId, tenantId));
        if (!referencing.isEmpty()) {
            return badRequest(AssetTags.inUseReason(referencing) + " Remove it from there first.");
        }

        tags().delete(tag.get());
        nzyme.getEthernet().segmentation().invalidateResolver(organizationId, tenantId);

        return Response.ok().build();
    }

    @GET
    @Path("/show/{uuid}/assets")
    @Operation(operationId = "findAssetsOfAssetTag", summary = "List the assets that carry a tag",
            description = "The assets that were seen last come first. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Assets found.",
            content = @Content(schema = @Schema(implementation = AssetTagAssetsListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Tag, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAssets(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "Tag UUID.") @PathParam("uuid") UUID uuid,
                               @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                               @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                               @Parameter(description = "Page size, 250 at most. Defaults to 25.")
                               @QueryParam("limit") int limit,
                               @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<AssetTag> tag = tags().find(uuid, organizationId, tenantId);
        if (tag.isEmpty()) {
            return notFound();
        }

        List<AssetTagAssetResponse> assets = Lists.newArrayList();
        for (AssetTags.TaggedAssetReference asset : tags().findAssetsOfTag(tag.get(), pageSize(limit),
                Math.max(offset, 0))) {
            assets.add(assetResponse(asset.uuid(), asset.mac(), asset.hostnames(), asset.addresses(), organizationId,
                    tenantId));
        }

        return Response.ok(AssetTagAssetsListResponse.create(tag.get().assetCount(), assets)).build();
    }

    @PUT
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{uuid}/assets/many/remove")
    @Consumes(MediaType.APPLICATION_JSON)
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "unassignAssetTagFromAssets", summary = "Take a tag off multiple assets",
            description = "Takes the tag off every asset in the list that carries it. Assets that do not carry it "
                    + "and unknown UUIDs are ignored. At most 250 UUIDs per call. Requires the "
                    + "ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Action applied. Nothing is ever skipped.",
            content = @Content(schema = @Schema(implementation = BulkActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Missing request body or more than 250 UUIDs.",
            content = @Content)
    @ApiResponse(responseCode = "404",
            description = "Tag, organization or tenant not found, or not accessible by the calling user.",
            content = @Content)
    public Response unassignMany(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Tag UUID.") @PathParam("uuid") UUID uuid,
                                 @Parameter(description = "Organization UUID.")
                                 @PathParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                 @RequestBody(description = "The UUIDs of the assets to take the tag off.",
                                         required = true, content = @Content(mediaType = "application/json"))
                                 UUIDListRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<AssetTag> tag = tags().find(uuid, organizationId, tenantId);
        if (tag.isEmpty()) {
            return notFound();
        }

        if (req == null || req.uuids() == null || req.uuids().size() > MAX_PAGE_SIZE) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        int removed = tags().unassign(tag.get(), req.uuids());
        nzyme.getEthernet().segmentation().invalidateResolver(organizationId, tenantId);

        return bulkResponse(BulkResult.of(removed));
    }

    @PUT
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{uuid}/assets/all/remove")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "unassignAssetTagFromAllAssets", summary = "Take a tag off all of its assets",
            description = "Takes the tag off every asset that carries it, not only the ones of one page. The tag "
                    + "itself stays. Requires the ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Action applied. Nothing is ever skipped.",
            content = @Content(schema = @Schema(implementation = BulkActionResponse.class)))
    @ApiResponse(responseCode = "404",
            description = "Tag, organization or tenant not found, or not accessible by the calling user.",
            content = @Content)
    public Response unassignAll(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Tag UUID.") @PathParam("uuid") UUID uuid,
                                @Parameter(description = "Organization UUID.")
                                @PathParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<AssetTag> tag = tags().find(uuid, organizationId, tenantId);
        if (tag.isEmpty()) {
            return notFound();
        }

        int removed = tags().unassignAll(tag.get());
        nzyme.getEthernet().segmentation().invalidateResolver(organizationId, tenantId);

        return bulkResponse(BulkResult.of(removed));
    }

    @PUT
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{uuid}/assets/{asset_id}")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "assignAssetTag", summary = "Put a tag on an asset")
    @ApiResponse(responseCode = "200", description = "Tag assigned. Also returned if the asset carried the tag already.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Tag, asset, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response assign(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Tag UUID.") @PathParam("uuid") UUID uuid,
                           @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<AssetTag> tag = tags().find(uuid, organizationId, tenantId);
        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAsset(assetId, organizationId, tenantId);
        if (tag.isEmpty() || asset.isEmpty()) {
            return notFound();
        }

        tags().assign(tag.get(), asset.get().id());
        nzyme.getEthernet().segmentation().invalidateResolver(organizationId, tenantId);

        return Response.ok().build();
    }

    @DELETE
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{uuid}/assets/{asset_id}")
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Operation(operationId = "unassignAssetTag", summary = "Take a tag off an asset")
    @ApiResponse(responseCode = "200", description = "Tag removed from the asset.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Tag, asset, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response unassign(@Parameter(hidden = true) @Context SecurityContext sc,
                             @Parameter(description = "Tag UUID.") @PathParam("uuid") UUID uuid,
                             @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                             @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                             @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return notFound();
        }

        Optional<AssetTag> tag = tags().find(uuid, organizationId, tenantId);
        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAsset(assetId, organizationId, tenantId);
        if (tag.isEmpty() || asset.isEmpty()) {
            return notFound();
        }

        tags().unassign(tag.get(), asset.get().id());
        nzyme.getEthernet().segmentation().invalidateResolver(organizationId, tenantId);

        return Response.ok().build();
    }

    private Response deleteSelection(BulkSelection selection, UUID organizationId, UUID tenantId) {
        BulkResult result = tags().delete(selection,
                nzyme.getEthernet().segmentation().policies().findAll(organizationId, tenantId), organizationId,
                tenantId);
        nzyme.getEthernet().segmentation().invalidateResolver(organizationId, tenantId);

        return bulkResponse(result);
    }

    private AssetTagAssetResponse assetResponse(UUID uuid,
                                                String mac,
                                                List<String> hostnames,
                                                List<String> addresses,
                                                UUID organizationId,
                                                UUID tenantId) {
        String name = nzyme.getContextService().findMacAddressContext(mac, organizationId, tenantId)
                .map(MacAddressContextEntry::name)
                .orElse(null);

        if (name == null && !hostnames.isEmpty()) {
            name = hostnames.get(0);
        }

        return AssetTagAssetResponse.create(uuid, mac, name, addresses);
    }

    private static AssetTagResponse response(AssetTag tag, List<Policy> policies) {
        return AssetTagResponse.create(tag.uuid(), tag.name(), tag.description(), tag.assetCount(),
                AssetTags.referencingPolicies(tag.uuid(), policies), tag.createdAt(), tag.updatedAt());
    }

}
