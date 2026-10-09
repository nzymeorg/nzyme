package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.AssetManager;
import app.nzyme.core.assets.AssetRegistryKeys;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.assets.db.AssetHostnameEntry;
import app.nzyme.core.assets.db.AssetIpAddressEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.context.db.MacAddressTransparentContextEntry;
import app.nzyme.core.context.db.NetworkContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.requests.GenericConfigurationUpdateRequest;
import app.nzyme.core.rest.responses.context.NetworkContextDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressContextResponse;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.assets.*;
import app.nzyme.core.rest.responses.shared.*;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.distributed.messaging.ClusterMessage;
import app.nzyme.plugin.distributed.messaging.MessageType;
import app.nzyme.core.registry.RegistryChangeValidator;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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

import java.net.InetAddress;
import java.util.*;

import static app.nzyme.core.rest.RestHelpers.macContextEntryToResponse;
import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/assets")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Assets", description = "An asset is a device Nzyme detected in the monitored Ethernet traffic, "
        + "identified by its MAC address. Nzyme builds this inventory passively and uses it to enrich MAC addresses "
        + "with the hostnames and IP addresses of the asset, its DHCP fingerprints and when it was first and last "
        + "seen.")
public class AssetsResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findAssets", summary = "List assets of a tenant",
            description = "Returns all assets that were seen in the time range, most recently seen first by "
                    + "default. Each asset carries its MAC address with OUI and context information, all known "
                    + "hostnames and IP addresses and its DHCP fingerprints. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Assets found.",
            content = @Content(schema = @Schema(implementation = AssetSummariesListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response allAssets(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                              @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                              @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                              @Parameter(description = "Sorting column. Defaults to the time the asset was last seen.") @QueryParam("order_column") @Nullable String orderColumnParam,
                              @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                              @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                              @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        AssetManager.OrderColumn orderColumn = AssetManager.OrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = AssetManager.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getAssetsManager().countAssets(timeRange, filters, organizationId, tenantId);

        List<AssetSummaryResponse> assets = Lists.newArrayList();
        for (AssetEntry asset : nzyme.getAssetsManager()
                .findAllAssets(organizationId, tenantId, timeRange, filters, limit, offset, orderColumn, orderDirection)) {

            Optional<MacAddressContextEntry> context = nzyme.getContextService().findMacAddressContext(
                    asset.mac(), organizationId, tenantId
            );

            assets.add(AssetSummaryResponse.create(
                    asset.uuid(),
                    EthernetMacAddressResponse.create(
                            asset.mac(),
                            nzyme.getOuiService().lookup(asset.mac()).orElse(null),
                            asset.uuid(),
                            asset.isActive(),
                            macContextEntryToResponse(context)
                    ),
                    nzyme.getOuiService().lookup(asset.mac()).orElse(null),
                    asset.isActive(),
                    context.map(MacAddressContextEntry::name).orElse(null),
                    asset.hostnames(),
                    asset.ipAddresses(),
                    asset.dhcpFingerprintInitial(),
                    asset.dhcpFingerprintRenew(),
                    asset.dhcpFingerprintReboot(),
                    asset.dhcpFingerprintRebind(),
                    asset.firstSeen(),
                    asset.lastSeen()
            ));
        }

        return Response.ok(AssetSummariesListResponse.create(total, assets)).build();
    }

    @GET
    @Path("/active/histogram")
    @Operation(operationId = "findActiveAssetsHistogram", summary = "Get the active asset count histogram",
            description = "Returns the number of active assets per time bucket. The response is an object that "
                    + "maps each bucket timestamp to a count. The bucket size is chosen automatically from the "
                    + "time range.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response activeAssetHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                              @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Map<DateTime, Integer> histogram = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getAssetsManager()
                .activeAssetCountHistogram(timeRange, bucketing, organizationId, tenantId)) {
            histogram.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(histogram).build();
    }

    @GET
    @Path("/latest/histogram")
    @Operation(operationId = "findNewestAssets", summary = "List newly appeared assets",
            description = "Returns the assets of the tenant ordered by the time they were first seen, newest "
                    + "first, as a table of MAC address, hostnames and first seen time. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Assets found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response latestAssets(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                 @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                 @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                 @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                 @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long total = nzyme.getAssetsManager().countAssets(timeRange, filters, organizationId, tenantId);

        List<ThreeColumnTableHistogramValueResponse> columns = Lists.newArrayList();
        for (AssetEntry asset : nzyme.getAssetsManager()
                .findAllAssets(organizationId, tenantId, timeRange, filters, limit,
                        offset, AssetManager.OrderColumn.FIRST_SEEN, OrderDirection.DESC)) {
            Optional<MacAddressContextEntry> context = nzyme.getContextService().findMacAddressContext(
                    asset.mac(),
                    organizationId,
                    tenantId
            );

            HistogramValueStructureResponse columnOne = HistogramValueStructureResponse.create(
                    asset.mac(),
                    HistogramValueType.ETHERNET_MAC,
                    EthernetMacAddressResponse.create(
                            asset.mac(),
                            nzyme.getOuiService().lookup(asset.mac()).orElse(null),
                            asset.uuid(),
                            asset.isActive(),
                            macContextEntryToResponse(context)
                    )
            );

            HistogramValueStructureResponse columnTwo = HistogramValueStructureResponse.create(
                    asset.hostnames(), HistogramValueType.ASSET_HOSTNAMES, null
            );


            HistogramValueStructureResponse columnThree = HistogramValueStructureResponse.create(
                    asset.firstSeen(), HistogramValueType.DATETIME, null
            );


            columns.add(ThreeColumnTableHistogramValueResponse.create(columnOne, columnTwo, columnThree, asset.mac()));
        }


        return Response.ok(ThreeColumnTableHistogramResponse.create(total, false, columns)).build();
    }


    @GET
    @Path("/disappeared/histogram")
    @Operation(operationId = "findDisappearedAssets", summary = "List recently disappeared assets",
            description = "Returns the assets that are no longer active, ordered by the time they were last seen, "
                    + "most recent first, as a table of MAC address, hostnames and last seen time. Results are "
                    + "paginated.")
    @ApiResponse(responseCode = "200", description = "Assets found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response recentlyDisappearedAssets(@Parameter(hidden = true) @Context SecurityContext sc,
                                              @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                              @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                              @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                              @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                              @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                              @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long total = nzyme.getAssetsManager().countAllInactiveAssets(timeRange, filters, organizationId, tenantId);

        List<ThreeColumnTableHistogramValueResponse> columns = Lists.newArrayList();
        for (AssetEntry asset : nzyme.getAssetsManager()
                .findAllInactiveAssets(organizationId, tenantId, timeRange, filters, limit,
                        offset, AssetManager.OrderColumn.LAST_SEEN, OrderDirection.DESC)) {
            Optional<MacAddressContextEntry> context = nzyme.getContextService().findMacAddressContext(
                    asset.mac(),
                    organizationId,
                    tenantId
            );

            HistogramValueStructureResponse columnOne = HistogramValueStructureResponse.create(
                    asset.mac(),
                    HistogramValueType.ETHERNET_MAC,
                    EthernetMacAddressResponse.create(
                            asset.mac(),
                            nzyme.getOuiService().lookup(asset.mac()).orElse(null),
                            asset.uuid(),
                            asset.isActive(),
                            macContextEntryToResponse(context)
                    )
            );

            HistogramValueStructureResponse columnTwo = HistogramValueStructureResponse.create(
                    asset.hostnames(), HistogramValueType.ASSET_HOSTNAMES, null
            );


            HistogramValueStructureResponse columnThree = HistogramValueStructureResponse.create(
                    asset.lastSeen(), HistogramValueType.DATETIME, null
            );


            columns.add(ThreeColumnTableHistogramValueResponse.create(columnOne, columnTwo, columnThree, asset.mac()));
        }


        return Response.ok(ThreeColumnTableHistogramResponse.create(total, false, columns)).build();
    }

    @GET
    @Path("/show/{asset_id}")
    @Operation(operationId = "findAsset", summary = "Get asset details",
            description = "Returns one asset with its MAC address, OUI, context name, DHCP fingerprints, which "
                    + "protocols it was seen with and when it was first and last seen.")
    @ApiResponse(responseCode = "200", description = "Asset found.",
            content = @Content(schema = @Schema(implementation = AssetDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response one(@Parameter(hidden = true) @Context SecurityContext sc,
                        @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                        @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                        @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAsset(assetId, organizationId, tenantId);

        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<MacAddressContextEntry> context = nzyme.getContextService().findMacAddressContext(
                asset.get().mac(), organizationId, tenantId
        );

        return Response.ok(AssetDetailsResponse.create(
                asset.get().uuid(),
                EthernetMacAddressResponse.create(
                        asset.get().mac(),
                        nzyme.getOuiService().lookup(asset.get().mac()).orElse(null),
                        asset.get().uuid(),
                        asset.map(AssetEntry::isActive).orElse(null),
                        macContextEntryToResponse(context)
                ),
                nzyme.getOuiService().lookup(asset.get().mac()).orElse(null),
                asset.get().isActive(),
                context.map(MacAddressContextEntry::name).orElse(null),
                asset.get().dhcpFingerprintInitial(),
                asset.get().dhcpFingerprintRenew(),
                asset.get().dhcpFingerprintReboot(),
                asset.get().dhcpFingerprintRebind(),
                asset.get().seenArp(),
                asset.get().seenDhcp(),
                asset.get().seenTcp(),
                asset.get().seenUdp(),
                asset.get().firstSeen(),
                asset.get().lastSeen()
        )).build();
    }

    @GET
    @Path("/show/{asset_id}/hostnames")
    @Operation(operationId = "findAssetHostnames", summary = "List hostnames of an asset",
            description = "Returns all hostnames the asset was seen with in the time range, most recently seen "
                    + "first by default. Each entry names the protocol the hostname was learned from. Results are "
                    + "paginated.")
    @ApiResponse(responseCode = "200", description = "Hostnames found.",
            content = @Content(schema = @Schema(implementation = AssetHostnamesListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response hostnames(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                              @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                              @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                              @Parameter(description = "Sorting column. Defaults to the time the hostname was last seen.") @QueryParam("order_column") @Nullable String orderColumnParam,
                              @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                              @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                              @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        AssetManager.HostnameOrderColumn orderColumn = AssetManager.HostnameOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = AssetManager.HostnameOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAsset(assetId, organizationId, tenantId);

        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long total = nzyme.getAssetsManager().countHostnamesOfAsset(asset.get().id(), timeRange);

        List<AssetHostnameDetailsResponse> hostnames = Lists.newArrayList();
        for (AssetHostnameEntry h : nzyme.getAssetsManager()
                .findHostnamesOfAsset(asset.get().id(), timeRange, limit, offset, orderColumn, orderDirection)) {
            hostnames.add(AssetHostnameDetailsResponse.create(
                    h.uuid(),
                    h.hostname(),
                    h.source(),
                    h.firstSeen(),
                    h.lastSeen()
            ));
        }

        return Response.ok(AssetHostnamesListResponse.create(total, hostnames)).build();
    }


    @GET
    @Path("/show/{asset_id}/ip_addresses")
    @Operation(operationId = "findAssetIpAddresses", summary = "List IP addresses of an asset",
            description = "Returns all IP addresses the asset was seen with in the time range, most recently seen "
                    + "first by default. Each entry names the protocol the address was learned from and the "
                    + "network context entries the address falls into. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "IP addresses found.",
            content = @Content(schema = @Schema(implementation = AssetIpAddressesListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response ipAddresses(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                                @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                @Parameter(description = "Sorting column. Defaults to the time the address was last seen.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        AssetManager.IpAddressOrderColumn orderColumn = AssetManager.IpAddressOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = AssetManager.IpAddressOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAsset(assetId, organizationId, tenantId);

        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long total = nzyme.getAssetsManager().countIpAddressesOfAsset(asset.get().id(), timeRange);

        List<AssetIpAddressDetailsResponse> addresses = Lists.newArrayList();
        for (AssetIpAddressEntry i : nzyme.getAssetsManager()
                .findIpAddressesOfAsset(asset.get().id(), timeRange, limit, offset, orderColumn, orderDirection)) {
            List<NetworkContextDetailsResponse> context =  Lists.newArrayList();
            for (NetworkContextEntry c : nzyme.getContextService()
                    .findNetworkContext(InetAddress.ofLiteral(i.address()), organizationId, tenantId)) {
                context.add(NetworkContextDetailsResponse.create(
                        c.uuid(),
                        c.network().toString(),
                        c.name(),
                        c.description(),
                        c.notes(),
                        c.createdAt(),
                        c.updatedAt()
                ));
            }

            addresses.add(AssetIpAddressDetailsResponse.create(
                    i.uuid(),
                    i.address(),
                    i.source(),
                    context,
                    i.firstSeen(),
                    i.lastSeen()
            ));
        }

        return Response.ok(AssetIpAddressesListResponse.create(total, addresses)).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "ethernet_assets_manage" })
    @Path("/show/{asset_id}/hostnames/{hostname_id}/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "deleteAssetHostname", summary = "Delete a hostname of an asset",
            description = "Removes one hostname from the asset and invalidates the asset caches on all online "
                    + "nodes. The hostname comes back if a tap reports it again. Requires the "
                    + "ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Hostname deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response deleteHostname(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                                   @Parameter(description = "UUID of the hostname entry to delete.") @PathParam("hostname_id") UUID hostnameId,
                                   @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAsset(assetId, organizationId, tenantId);

        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAssetsManager().deleteHostnameOfAsset(asset.get().id(), hostnameId);

        invalidateAssetByMacCachesClusterWide();

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "ethernet_assets_manage" })
    @Path("/show/{asset_id}/ip_addresses/{address_id}/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "deleteAssetIpAddress", summary = "Delete an IP address of an asset",
            description = "Removes one IP address from the asset and invalidates the asset caches on all online "
                    + "nodes. The address comes back if a tap reports it again. Requires the "
                    + "ethernet_assets_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "IP address deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response deleteIpAddress(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                                    @Parameter(description = "UUID of the IP address entry to delete.") @PathParam("address_id") UUID addressId,
                                    @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAsset(assetId, organizationId, tenantId);

        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getAssetsManager().deleteIpAddressOfAsset(asset.get().id(), addressId);

        invalidateAssetByMacCachesClusterWide();

        return Response.ok().build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "ethernet_assets_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "findAssetsConfiguration", summary = "Get the asset configuration of a tenant",
            description = "Returns the asset settings of the tenant, currently the retention time of asset "
                    + "statistics in days, which defaults to 365. Requires the ethernet_assets_manage feature "
                    + "permission.",
            externalDocs = @ExternalDocumentation(description = "Asset configuration in the Nzyme documentation",
                    url = "https://go.nzyme.org/assets-config"))
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = AssetsConfigurationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response getConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        int retentionTimeDays = Integer.parseInt(nzyme.getDatabaseCoreRegistry().getValue(
                AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS.key(),
                organizationId,
                tenantId
        ).orElse(AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS.defaultValue().get()));

        return Response.ok(AssetsConfigurationResponse.create(ConfigurationEntryResponse.create(
                AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS.key(),
                "Asset Statistics Retention Time (Days)",
                retentionTimeDays,
                ConfigurationEntryValueType.NUMBER,
                AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS.defaultValue().orElse(null),
                AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS.requiresRestart(),
                AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS.constraints().orElse(Collections.emptyList()),
                "assets-config"
        ))).build();

    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "ethernet_assets_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "updateAssetsConfiguration", summary = "Update the asset configuration of a tenant",
            description = "Updates the asset settings of the tenant. The only accepted key is "
                    + "assets_statistics_retention_time_days, the number of days asset statistics are kept. "
                    + "Requires the ethernet_assets_manage feature permission.",
            externalDocs = @ExternalDocumentation(description = "Asset configuration in the Nzyme documentation",
                    url = "https://go.nzyme.org/assets-config"))
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The change contains a key that is not part of the asset configuration.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The change is empty or a value violates the constraints of its configuration key.", content = @Content)
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
                .allowing(AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS)
                .validate(req.change());

        if (changes.isEmpty()) {
            return Response.status(422).build();
        }

        for (RegistryChangeValidator.Change c : changes.get()) {
            nzyme.getDatabaseCoreRegistry().setValue(c.key(), c.value(), organizationId, tenantId);
        }

        return Response.ok().build();
    }

    private void invalidateAssetByMacCachesClusterWide() {
        nzyme.getMessageBus().sendToAllOnlineNodes(ClusterMessage.create(
                MessageType.INVALIDATE_CACHE,
                Map.of("cache_type", "asset_by_mac"),
                false
        ));
    }

}