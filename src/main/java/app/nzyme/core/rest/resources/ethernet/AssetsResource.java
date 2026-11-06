package app.nzyme.core.rest.resources.ethernet;

import org.joda.time.LocalDate;
import org.joda.time.Days;
import app.nzyme.plugin.RegistryKey;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.util.Tools;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.floorplans.db.TenantLocationEntry;
import app.nzyme.core.rest.responses.ethernet.assets.profile.*;
import app.nzyme.core.assets.profile.AssetRole;
import app.nzyme.core.assets.profile.AssetRiskAssessor;
import app.nzyme.core.assets.profile.AssetProfiles;
import app.nzyme.core.assets.profile.AssetAttributeDimension;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.AssetManager;
import app.nzyme.core.assets.AssetRegistryKeys;
import java.util.regex.Pattern;
import com.google.common.net.InetAddresses;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import app.nzyme.core.ethernet.l4.db.L4AddressAttributes;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.assets.db.AssetHostnameEntry;
import app.nzyme.core.assets.db.AssetIpAddressEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.context.db.MacAddressTransparentContextEntry;
import app.nzyme.core.context.db.NetworkContextEntry;
import app.nzyme.core.database.OrderDirection;
import com.google.common.base.Strings;
import app.nzyme.core.rest.responses.ethernet.assets.profile.AssetInboundPortProtocolResponse;
import app.nzyme.core.rest.responses.ethernet.assets.profile.AssetInboundPortResponse;
import app.nzyme.core.rest.responses.ethernet.assets.profile.AssetInboundPortsResponse;
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
            description = "Returns the asset settings of the tenant: the retention time of asset statistics in "
                    + "days, which defaults to 365, the retention times of the long-term asset activity (default "
                    + "365), attribute (default 730) and communication (default 90) data in days, and whether DHCP "
                    + "fingerprint monitoring is enabled, which defaults to true. Requires the ethernet_assets_manage feature permission.",
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

        return Response.ok(AssetsConfigurationResponse.create(
                ConfigurationEntryResponse.create(
                        AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS.key(),
                        "Asset Statistics Retention Time (Days)",
                        retentionTimeDays,
                        ConfigurationEntryValueType.NUMBER,
                        AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS.defaultValue().orElse(null),
                        AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS.requiresRestart(),
                        AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS.constraints().orElse(Collections.emptyList()),
                        "assets-config"
                ),
                retentionEntry(AssetRegistryKeys.ASSETS_ACTIVITY_RETENTION_TIME_DAYS,
                        "Asset Activity Retention Time (Days)", organizationId, tenantId),
                retentionEntry(AssetRegistryKeys.ASSETS_ATTRIBUTES_RETENTION_TIME_DAYS,
                        "Asset Attributes Retention Time (Days)", organizationId, tenantId),
                retentionEntry(AssetRegistryKeys.ASSETS_COMMUNICATION_RETENTION_TIME_DAYS,
                        "Asset Communication Retention Time (Days)", organizationId, tenantId),
                ConfigurationEntryResponse.create(
                        AssetRegistryKeys.DHCP_FINGERPRINT_MONITORING_ENABLED.key(),
                        "DHCP Fingerprint Monitoring Enabled",
                        nzyme.getEthernet().dhcp().fingerprintMonitoringEnabled(organizationId, tenantId),
                        ConfigurationEntryValueType.BOOLEAN,
                        AssetRegistryKeys.DHCP_FINGERPRINT_MONITORING_ENABLED.defaultValue().orElse(null),
                        AssetRegistryKeys.DHCP_FINGERPRINT_MONITORING_ENABLED.requiresRestart(),
                        AssetRegistryKeys.DHCP_FINGERPRINT_MONITORING_ENABLED.constraints().orElse(Collections.emptyList()),
                        "assets-config"
                )
        )).build();

    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "ethernet_assets_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "updateAssetsConfiguration", summary = "Update the asset configuration of a tenant",
            description = "Updates the asset settings of the tenant. Accepted keys are "
                    + "assets_statistics_retention_time_days, the number of days asset statistics are kept, "
                    + "assets_activity_retention_time_days, assets_attributes_retention_time_days and "
                    + "assets_communication_retention_time_days, the retention of the long-term asset activity, "
                    + "attribute and communication data, and "
                    + "assets_dhcp_fingerprint_monitoring_enabled, which controls whether a changed DHCP "
                    + "fingerprint of an asset raises an alert. Requires the ethernet_assets_manage feature "
                    + "permission.",
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
                .allowing(AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS,
                        AssetRegistryKeys.ASSETS_ACTIVITY_RETENTION_TIME_DAYS,
                        AssetRegistryKeys.ASSETS_ATTRIBUTES_RETENTION_TIME_DAYS,
                        AssetRegistryKeys.ASSETS_COMMUNICATION_RETENTION_TIME_DAYS,
                        AssetRegistryKeys.DHCP_FINGERPRINT_MONITORING_ENABLED)
                .validate(req.change());

        if (changes.isEmpty()) {
            return Response.status(422).build();
        }

        for (RegistryChangeValidator.Change c : changes.get()) {
            nzyme.getDatabaseCoreRegistry().setValue(c.key(), c.value(), organizationId, tenantId);
        }

        return Response.ok().build();
    }

    private ConfigurationEntryResponse retentionEntry(RegistryKey key, String title, UUID organizationId, UUID tenantId) {
        int value = Integer.parseInt(nzyme.getDatabaseCoreRegistry()
                .getValue(key.key(), organizationId, tenantId)
                .orElse(key.defaultValue().get()));

        return ConfigurationEntryResponse.create(
                key.key(),
                title,
                value,
                ConfigurationEntryValueType.NUMBER,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                "assets-config"
        );
    }

    @GET
    @Path("/show/{asset_id}/profile/summary")
    @Operation(operationId = "findAssetProfileSummary", summary = "Get the profile summary of an asset",
            description = "Returns the numbers of the asset details summary strip: activity state, alerts involving "
                    + "the asset, exclusions covering it, the most recent change, the taps that observed it and the "
                    + "roles it fulfills.")
    @ApiResponse(responseCode = "200", description = "Summary found.",
            content = @Content(schema = @Schema(implementation = AssetProfileSummaryResponse.class)))
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response profileSummary(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                                   @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                   @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        Optional<AssetEntry> asset = resolveAsset(sc, assetId, organizationId, tenantId);
        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        AssetProfiles profiles = nzyme.getAssetsManager().profiles();
        String mac = asset.get().mac();

        List<AssetProfiles.AlertSummary> alerts = profiles.alerts(organizationId, tenantId, mac);
        long totalAlerts = alerts.stream().mapToLong(AssetProfiles.AlertSummary::count).sum();
        long activeAlerts = alerts.stream().mapToLong(AssetProfiles.AlertSummary::activeCount).sum();

        List<AssetProfiles.ExclusionMatch> exclusions = profiles.exclusions(
                organizationId, tenantId, mac, asset.get().ipAddresses());

        AssetProfiles.TimelineEvent lastChange = null;
        TimeRange allTime = TimeRange.create(new DateTime(0), DateTime.now().plusDays(1), true);
        for (AssetProfiles.TimelineEvent event : profiles.timeline(asset.get(), allTime, Filters.create(Map.of()),
                AssetProfiles.TimelineOrder.TIMESTAMP, OrderDirection.DESC, 25, 0)) {
            if (!event.type().equals("ALERT")) {
                lastChange = event;
                break;
            }
        }

        List<AssetTapResponse> taps = new ArrayList<>();
        List<AssetRoleResponse> roles = new ArrayList<>();
        for (AssetProfiles.AttributeEntry attribute : profiles.attributes(organizationId, tenantId, mac)) {
            if (attribute.dimension().equals(AssetAttributeDimension.TAP.name())) {
                UUID tapUuid;
                try {
                    tapUuid = UUID.fromString(attribute.value());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                taps.add(AssetTapResponse.create(
                        tapUuid,
                        nzyme.getTapManager().findTap(tapUuid)
                                .filter(t -> Objects.equals(t.organizationId(), organizationId)
                                        && Objects.equals(t.tenantId(), tenantId))
                                .map(Tap::name).orElse(null),
                        attribute.count(),
                        attribute.firstSeen(),
                        attribute.lastSeen()
                ));
            } else if (attribute.dimension().equals(AssetAttributeDimension.ROLE.name())) {
                Long dependents = null;
                if (attribute.value().equals(AssetRole.GATEWAY.name())) {
                    dependents = profiles.countAssetsUsingGateway(organizationId, tenantId, mac);
                }
                roles.add(AssetRoleResponse.create(
                        attribute.value(), attribute.count(), attribute.firstSeen(), attribute.lastSeen(), dependents
                ));
            }
        }

        return Response.ok(AssetProfileSummaryResponse.create(
                asset.get().isActive(),
                asset.get().lastSeen(),
                activeAlerts,
                totalAlerts,
                exclusions.size(),
                lastChange == null ? null : lastChange.timestamp(),
                lastChange == null ? null : lastChange.type(),
                lastChange == null ? null : lastChange.value(),
                lastChange == null ? null : lastChange.detail(),
                nzyme.getContextService().findMacAddressContext(mac, organizationId, tenantId).isPresent(),
                Tools.macAddressIsRandomized(mac),
                taps,
                roles
        )).build();
    }

    @GET
    @Path("/show/{asset_id}/profile/attributes")
    @Operation(operationId = "findAssetProfileAttributes", summary = "Get the attribute ledger of an asset",
            description = "Returns every attribute value ever recorded for the asset, grouped by dimension, with "
                    + "first and last seen timestamps and a count. Dimensions include fingerprints, protocols, "
                    + "inbound ports, external networks, infrastructure servers, roles and taps. Dimensions can be "
                    + "excluded, which the web interface does for inbound ports since those have their own paginated "
                    + "endpoint and can number in the thousands on a scanned host.")
    @ApiResponse(responseCode = "200", description = "Attributes found.",
            content = @Content(schema = @Schema(implementation = AssetAttributesResponse.class)))
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response profileAttributes(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                                      @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                      @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                      @Parameter(description = "Comma-separated dimensions to leave out, like INBOUND_PORT,INBOUND_PORT_PROTOCOL.") @QueryParam("exclude_dimensions") String excludeDimensions) {
        Optional<AssetEntry> asset = resolveAsset(sc, assetId, organizationId, tenantId);
        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Set<String> excluded = new HashSet<>();
        if (!Strings.isNullOrEmpty(excludeDimensions)) {
            for (String dimension : excludeDimensions.split(",")) {
                if (!dimension.isBlank()) {
                    excluded.add(dimension.trim().toUpperCase());
                }
            }
        }

        Map<String, Integer> externalResolved = new HashMap<>();

        Map<String, List<AssetAttributeResponse>> attributes = new LinkedHashMap<>();
        for (AssetProfiles.AttributeEntry a : nzyme.getAssetsManager().profiles()
                .attributes(organizationId, tenantId, asset.get().mac(), excluded)) {
            L4AddressResponse l4Address = null;
            EthernetMacAddressResponse macAddress = null;

            if (INFRASTRUCTURE_DIMENSIONS.contains(a.dimension())) {
                if (MAC_PATTERN.matcher(a.value()).matches()) {
                    macAddress = infrastructureMac(a.value(), organizationId, tenantId);
                } else {
                    InetAddress ip = parseAddress(a.value());
                    if (ip != null) {
                        boolean local = ip.isSiteLocalAddress() || ip.isLinkLocalAddress();
                        int resolved = externalResolved.getOrDefault(a.dimension(), 0);
                        if (local || resolved < EXTERNAL_INFRASTRUCTURE_RESOLVED) {
                            l4Address = infrastructureAddress(ip, a.value(), organizationId, tenantId);
                            if (!local) {
                                externalResolved.put(a.dimension(), resolved + 1);
                            }
                        }
                    }
                }
            }

            attributes.computeIfAbsent(a.dimension(), k -> new ArrayList<>()).add(AssetAttributeResponse.create(
                    a.dimension(), a.value(), a.count(), a.bytes(), a.firstSeen(), a.lastSeen(), l4Address, macAddress
            ));
        }

        return Response.ok(AssetAttributesResponse.create(attributes)).build();
    }

    @GET
    @Path("/show/{asset_id}/profile/inbound-ports")
    @Operation(operationId = "findAssetProfileInboundPorts", summary = "Get the inbound ports of an asset",
            description = "Returns one page of the ports the asset accepted sessions on, each with the session "
                    + "and byte counts, first and last seen timestamps and the protocols served on it, busiest "
                    + "first. The totals and maximums cover every port of the asset, not only the page. Sorted by "
                    + "port, count, bytes, first_seen or last_seen.")
    @ApiResponse(responseCode = "200", description = "Inbound ports found.",
            content = @Content(schema = @Schema(implementation = AssetInboundPortsResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid sort column.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response profileInboundPorts(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                                        @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                        @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                        @Parameter(description = "Sort column: port, count, bytes, first_seen or last_seen. Defaults to count.") @QueryParam("order_by") String orderBy,
                                        @Parameter(description = "Sort direction: ASC or DESC. Defaults to DESC.") @QueryParam("order_direction") String orderDirection,
                                        @Parameter(description = "Page size, at most 100.") @QueryParam("limit") int limit,
                                        @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        Optional<AssetEntry> asset = resolveAsset(sc, assetId, organizationId, tenantId);
        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        AssetProfiles.InboundPortOrder order;
        try {
            order = Strings.isNullOrEmpty(orderBy)
                    ? AssetProfiles.InboundPortOrder.COUNT
                    : AssetProfiles.InboundPortOrder.valueOf(orderBy.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        OrderDirection direction = "ASC".equalsIgnoreCase(orderDirection) ? OrderDirection.ASC : OrderDirection.DESC;

        AssetProfiles profiles = nzyme.getAssetsManager().profiles();
        String mac = asset.get().mac();

        List<AssetProfiles.AttributeEntry> page = profiles.inboundPorts(organizationId, tenantId, mac, order, direction,
                Math.max(1, Math.min(limit <= 0 ? 10 : limit, 100)), Math.max(0, offset));

        Map<String, List<AssetInboundPortProtocolResponse>> protocolsByPort = new HashMap<>();
        for (AssetProfiles.AttributeEntry p : profiles.inboundPortProtocols(organizationId, tenantId, mac,
                page.stream().map(AssetProfiles.AttributeEntry::value).toList())) {
            int separator = p.value().indexOf(' ');
            if (separator < 0) {
                continue;
            }
            protocolsByPort.computeIfAbsent(p.value().substring(0, separator), k -> new ArrayList<>())
                    .add(AssetInboundPortProtocolResponse.create(p.value().substring(separator + 1), p.count()));
        }

        List<AssetInboundPortResponse> ports = new ArrayList<>();
        for (AssetProfiles.AttributeEntry a : page) {
            String[] parts = a.value().split("/", 2);
            int port = 0;
            if (parts.length == 2) {
                try {
                    port = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) { }
            }

            ports.add(AssetInboundPortResponse.create(
                    a.value(), parts[0], port, a.count(), a.bytes(), a.firstSeen(), a.lastSeen(),
                    protocolsByPort.getOrDefault(a.value(), List.of())
            ));
        }

        AssetProfiles.InboundPortTotals totals = profiles.inboundPortTotals(organizationId, tenantId, mac);

        return Response.ok(AssetInboundPortsResponse.create(
                totals.ports(), totals.sessions(), totals.bytes(), totals.maxSessions(), totals.maxBytes(), ports
        )).build();
    }

    @GET
    @Path("/show/{asset_id}/profile/activity")
    @Operation(operationId = "findAssetProfileActivity", summary = "Get the long-term activity of an asset",
            description = "Returns the activity of the asset over the requested number of days as an hour of day "
                    + "by day of week heatmap and a per-day calendar, shifted into the timezone offset of the caller. "
                    + "Activity is kept outside of the regular protocol data retention.")
    @ApiResponse(responseCode = "200", description = "Activity found.",
            content = @Content(schema = @Schema(implementation = AssetActivityResponse.class)))
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response profileActivity(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                                    @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                    @Parameter(description = "Number of days to look back. Defaults to 90, maximum is 730.") @QueryParam("days") Integer days,
                                    @Parameter(description = "Timezone offset of the caller in minutes east of UTC. Defaults to 0.") @QueryParam("tz_offset") Integer timezoneOffset) {
        Optional<AssetEntry> asset = resolveAsset(sc, assetId, organizationId, tenantId);
        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        int lookback = days == null ? 90 : Math.max(1, Math.min(days, 730));
        int tz = timezoneOffset == null ? 0 : Math.max(-900, Math.min(timezoneOffset, 900));

        AssetProfiles profiles = nzyme.getAssetsManager().profiles();

        List<AssetActivityCellResponse> heatmap = new ArrayList<>();
        for (AssetProfiles.ActivityCell c : profiles.activityHeatmap(organizationId, tenantId, asset.get().mac(), lookback, tz)) {
            heatmap.add(AssetActivityCellResponse.create(c.dayOfWeek(), c.hour(), c.events(), c.bytes(),
                    c.arpPackets(), c.dhcpTransactions(), c.dnsQueries(), c.tcpSessions(), c.udpSessions()));
        }

        List<AssetActivityDayResponse> calendar = new ArrayList<>();
        long totalEvents = 0;
        long totalBytes = 0;
        long longestGap = 0;
        LocalDate longestGapEnd = null;
        LocalDate previous = null;
        for (AssetProfiles.ActivityDay d : profiles.activityCalendar(organizationId, tenantId, asset.get().mac(), lookback, tz)) {
            calendar.add(AssetActivityDayResponse.create(d.day().toString(), d.events(), d.bytes(), d.arpPackets(),
                    d.dhcpTransactions(), d.dnsQueries(), d.tcpSessions(), d.udpSessions()));
            totalEvents += d.events();
            totalBytes += d.bytes();

            if (previous != null) {
                long gap = Days.daysBetween(previous, d.day()).getDays() - 1;
                if (gap > longestGap) {
                    longestGap = gap;
                    longestGapEnd = d.day();
                }
            }
            previous = d.day();
        }

        int retentionDays = Integer.parseInt(nzyme.getDatabaseCoreRegistry()
                .getValue(AssetRegistryKeys.ASSETS_ACTIVITY_RETENTION_TIME_DAYS.key(), organizationId, tenantId)
                .orElse(AssetRegistryKeys.ASSETS_ACTIVITY_RETENTION_TIME_DAYS.defaultValue().orElse("365")));

        return Response.ok(AssetActivityResponse.create(
                retentionDays, lookback, heatmap, calendar, totalEvents, totalBytes, calendar.size(), longestGap,
                longestGapEnd == null ? null : longestGapEnd.toString()
        )).build();
    }

    @GET
    @Path("/show/{asset_id}/profile/communication")
    @Operation(operationId = "findAssetProfileCommunication", summary = "Get the communication peers of an asset",
            description = "Returns the aggregated communication edges of the asset in the time range: internal "
                    + "peers by address and MAC, external peers by autonomous system and country, each with "
                    + "direction, layer 4 type, session and byte counts. Communication is aggregated per UTC day, so "
                    + "the time range applies to whole days: a range shorter than a day returns that whole day. "
                    + "Internal peers that are assets carry the "
                    + "asset reference, external networks carry the GeoIP city and coordinates first seen for them. "
                    + "Edges are ordered by bytes, descending. The origin is the map position of the asset, from the "
                    + "coordinates of the tap that observed it most or of that tap's location, and null without any.")
    @ApiResponse(responseCode = "200", description = "Communication found.",
            content = @Content(schema = @Schema(implementation = AssetCommunicationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response profileCommunication(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                                         @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                         @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                         @Parameter(description = "Time range.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                         @Parameter(description = "Maximum number of edges. Defaults to 250, maximum is 2000.") @QueryParam("limit") Integer limit) {
        Optional<AssetEntry> asset = resolveAsset(sc, assetId, organizationId, tenantId);
        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        int edgeLimit = limit == null ? 250 : Math.max(1, Math.min(limit, 2000));

        Map<String, L4AddressResponse> peerAddresses = new HashMap<>();

        List<AssetCommunicationEdgeResponse> edges = new ArrayList<>();
        for (AssetProfiles.CommunicationEdge e : nzyme.getAssetsManager().profiles()
                .communication(organizationId, tenantId, asset.get().mac(), timeRange, edgeLimit)) {
            L4AddressResponse peerAddress = null;
            if ("INTERNAL".equals(e.peerType()) && e.peerAddress() != null) {
                String cacheKey = e.peerAddress() + "|" + e.peerMac() + "|" + e.l4Type();
                peerAddress = peerAddresses.computeIfAbsent(cacheKey, k -> RestHelpers.L4AddressDataToResponse(
                        nzyme, organizationId, tenantId, L4Type.valueOf(e.l4Type()),
                        L4AddressData.create(e.peerMac(), e.peerAddress(), 0, null,
                                L4AddressAttributes.create(true, false, false))
                ));
            }

            AssetPeerAssetResponse peerAsset = null;
            if (e.peerMac() != null) {
                Optional<AssetEntry> peer = nzyme.getAssetsManager().findAssetByMac(e.peerMac(), organizationId, tenantId);
                if (peer.isPresent()) {
                    peerAsset = AssetPeerAssetResponse.create(
                            peer.get().uuid(),
                            peer.get().mac(),
                            nzyme.getContextService().findMacAddressContext(peer.get().mac(), organizationId, tenantId)
                                    .map(MacAddressContextEntry::name).orElse(null)
                    );
                }
            }

            edges.add(AssetCommunicationEdgeResponse.create(
                    e.direction(), e.l4Type(), e.peerType(), e.peerKey(), e.peerMac(), e.peerAddress(), peerAsset,
                    e.peerAsnNumber(), e.peerAsnName(), e.peerCountryCode(), e.peerCity(), e.peerLatitude(),
                    e.peerLongitude(), peerAddress, e.port(), e.protocol(), e.sessions(), e.bytesRx(), e.bytesTx(),
                    e.firstSeen(), e.lastSeen()
            ));
        }

        int retentionDays = Integer.parseInt(nzyme.getDatabaseCoreRegistry()
                .getValue(AssetRegistryKeys.ASSETS_COMMUNICATION_RETENTION_TIME_DAYS.key(), organizationId, tenantId)
                .orElse(AssetRegistryKeys.ASSETS_COMMUNICATION_RETENTION_TIME_DAYS.defaultValue().orElse("365")));

        return Response.ok(AssetCommunicationResponse.create(
                asset.get().mac(),
                asset.get().uuid(),
                nzyme.getContextService().findMacAddressContext(asset.get().mac(), organizationId, tenantId)
                        .map(MacAddressContextEntry::name).orElse(null),
                edges,
                retentionDays,
                communicationOrigin(organizationId, tenantId, asset.get().mac())
        )).build();
    }

    @Nullable
    private static String timelineLabel(AssetProfiles.TimelineEvent e) {
        if (!"ALERT".equals(e.type()) || e.value() == null) {
            return null;
        }

        try {
            return DetectionType.valueOf(e.value()).getTitle();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Nullable
    private AssetCommunicationOriginResponse communicationOrigin(UUID organizationId, UUID tenantId, String mac) {
        List<AssetProfiles.AttributeEntry> taps = nzyme.getAssetsManager().profiles()
                .attributes(organizationId, tenantId, mac).stream()
                .filter(a -> AssetAttributeDimension.TAP.name().equals(a.dimension()))
                .sorted(Comparator.comparingLong(AssetProfiles.AttributeEntry::count).reversed())
                .toList();

        for (AssetProfiles.AttributeEntry entry : taps) {
            UUID tapUuid;
            try {
                tapUuid = UUID.fromString(entry.value());
            } catch (IllegalArgumentException e) {
                continue;
            }

            Optional<Tap> tap = nzyme.getTapManager().findTap(tapUuid);
            if (tap.isEmpty() || !Objects.equals(tap.get().organizationId(), organizationId)
                    || !Objects.equals(tap.get().tenantId(), tenantId)) {
                continue;
            }

            String locationName = null;
            Double latitude = tap.get().latitude();
            Double longitude = tap.get().longitude();
            if (tap.get().locationId() != null) {
                Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                        .findTenantLocation(tap.get().locationId(), organizationId, tenantId);
                if (location.isPresent()) {
                    locationName = location.get().name();
                    if (latitude == null || longitude == null) {
                        latitude = location.get().latitude();
                        longitude = location.get().longitude();
                    }
                }
            }

            if (latitude != null && longitude != null) {
                return AssetCommunicationOriginResponse.create(
                        latitude, longitude, tap.get().uuid(), tap.get().name(), locationName
                );
            }
        }

        return null;
    }

    @GET
    @Path("/show/{asset_id}/profile/timeline")
    @Operation(operationId = "findAssetProfileTimeline", summary = "Get the change timeline of an asset",
            description = "Returns the timeline of the asset in the time range: when it was first seen, every "
                    + "hostname, IP address and attribute value that showed up for the first time, and every alert "
                    + "it was involved in. Filtered by type, which is an event type or ATTRIBUTE:<dimension>, and by "
                    + "value, and sorted by timestamp, type or value.")
    @ApiResponse(responseCode = "200", description = "Timeline found.",
            content = @Content(schema = @Schema(implementation = AssetTimelineResponse.class)))
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response profileTimeline(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                                    @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                    @Parameter(description = "Time range.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                    @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                    @Parameter(description = "Sort column: timestamp, type or value. Defaults to timestamp.") @QueryParam("order_by") String orderBy,
                                    @Parameter(description = "Sort direction: ASC or DESC. Defaults to DESC.") @QueryParam("order_direction") String orderDirection,
                                    @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                    @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        Optional<AssetEntry> asset = resolveAsset(sc, assetId, organizationId, tenantId);
        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        AssetProfiles profiles = nzyme.getAssetsManager().profiles();

        Filters filters = parseFiltersQueryParameter(filtersParameter);

        AssetProfiles.TimelineOrder order;
        try {
            order = orderBy == null ? AssetProfiles.TimelineOrder.TIMESTAMP
                    : AssetProfiles.TimelineOrder.valueOf(orderBy.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        OrderDirection direction = "ASC".equalsIgnoreCase(orderDirection) ? OrderDirection.ASC : OrderDirection.DESC;

        List<AssetTimelineEventResponse> events = new ArrayList<>();
        for (AssetProfiles.TimelineEvent e : profiles.timeline(asset.get(), timeRange, filters, order, direction,
                Math.max(1, Math.min(limit, 250)), Math.max(0, offset))) {
            events.add(AssetTimelineEventResponse.create(e.type(), e.timestamp(), e.value(), e.detail(), timelineLabel(e)));
        }

        int attributeRetentionDays = Integer.parseInt(nzyme.getDatabaseCoreRegistry()
                .getValue(AssetRegistryKeys.ASSETS_ATTRIBUTES_RETENTION_TIME_DAYS.key(), organizationId, tenantId)
                .orElse(AssetRegistryKeys.ASSETS_ATTRIBUTES_RETENTION_TIME_DAYS.defaultValue().orElse("730")));

        return Response.ok(AssetTimelineResponse.create(
                profiles.countTimeline(asset.get(), timeRange, filters), events, attributeRetentionDays
        )).build();
    }

    @GET
    @Path("/show/{asset_id}/profile/timeline/histogram")
    @Operation(operationId = "findAssetProfileTimelineHistogram", summary = "Get the change histogram of an asset",
            description = "Returns the number of timeline events of the asset per time bucket, under the same "
                    + "filters as the timeline. The response is an object that maps each bucket timestamp to a "
                    + "count. The bucket size is chosen automatically from the time range.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response profileTimelineHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                             @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                                             @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                             @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                             @Parameter(description = "Time range.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                             @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter) {
        Optional<AssetEntry> asset = resolveAsset(sc, assetId, organizationId, tenantId);
        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Map<DateTime, Integer> histogram = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getAssetsManager().profiles()
                .timelineHistogram(asset.get(), timeRange, filters, bucketing)) {
            histogram.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(histogram).build();
    }

    @GET
    @Path("/show/{asset_id}/profile/alerts")
    @Operation(operationId = "findAssetProfileAlerts", summary = "Get the alerts involving an asset",
            description = "Returns the alerts that carry the MAC address of the asset in any attribute, grouped by "
                    + "detection type, with the role of the asset in them: ACTOR when it caused the alert, VICTIM "
                    + "when it was the target, SUBJECT when the alert is about the asset itself.")
    @ApiResponse(responseCode = "200", description = "Alerts found.",
            content = @Content(schema = @Schema(implementation = AssetAlertsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response profileAlerts(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                                  @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        Optional<AssetEntry> asset = resolveAsset(sc, assetId, organizationId, tenantId);
        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<AssetAlertSummaryResponse> alerts = new ArrayList<>();
        long total = 0;
        long active = 0;
        for (AssetProfiles.AlertSummary a : nzyme.getAssetsManager().profiles()
                .alerts(organizationId, tenantId, asset.get().mac())) {
            String description = null;
            try {
                description = DetectionType.valueOf(a.detectionType()).getTitle();
            } catch (IllegalArgumentException ignored) { }

            alerts.add(AssetAlertSummaryResponse.create(
                    a.detectionType(), description, AssetRiskAssessor.alertRole(a.attributeKeys()),
                    a.count(), a.activeCount(), a.lastSeen()
            ));
            total += a.count();
            active += a.activeCount();
        }

        return Response.ok(AssetAlertsResponse.create(total, active, alerts)).build();
    }

    @GET
    @Path("/show/{asset_id}/profile/findings")
    @Operation(operationId = "findAssetProfileFindings", summary = "Get the detection findings involving an asset",
            description = "Returns the findings of the detection methods that keep their own finding tables, such "
                    + "as scans, beacons, tunnels and spoofing, with the role of the asset in them: ACTOR or VICTIM.")
    @ApiResponse(responseCode = "200", description = "Findings found.",
            content = @Content(schema = @Schema(implementation = AssetFindingsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response profileFindings(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                                    @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        Optional<AssetEntry> asset = resolveAsset(sc, assetId, organizationId, tenantId);
        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<AssetFindingResponse> findings = new ArrayList<>();
        for (AssetProfiles.FindingSummary f : nzyme.getAssetsManager().profiles()
                .findings(organizationId, tenantId, asset.get().mac())) {
            findings.add(AssetFindingResponse.create(f.type(), f.role(), f.count(), f.lastSeen()));
        }

        return Response.ok(AssetFindingsResponse.create(findings)).build();
    }

    @GET
    @Path("/show/{asset_id}/profile/exclusions")
    @Operation(operationId = "findAssetProfileExclusions", summary = "Get the exclusions covering an asset",
            description = "Returns every detection exclusion that matches the asset, by its MAC address directly or "
                    + "by one of its IP addresses through a CIDR, across DHCP fingerprint, spoofing, beacon, ICMP, "
                    + "recon and DNS tunnel exclusions.")
    @ApiResponse(responseCode = "200", description = "Exclusions found.",
            content = @Content(schema = @Schema(implementation = AssetExclusionsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response profileExclusions(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                                      @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                      @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId) {
        Optional<AssetEntry> asset = resolveAsset(sc, assetId, organizationId, tenantId);
        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<AssetExclusionMatchResponse> exclusions = new ArrayList<>();
        for (AssetProfiles.ExclusionMatch e : nzyme.getAssetsManager().profiles()
                .exclusions(organizationId, tenantId, asset.get().mac(), asset.get().ipAddresses())) {
            exclusions.add(AssetExclusionMatchResponse.create(
                    e.type(), e.uuid(), e.name(), e.enabled(), e.matchedBy(), e.matchCount(), e.lastMatchedAt()
            ));
        }

        return Response.ok(AssetExclusionsResponse.create(exclusions)).build();
    }

    @GET
    @Path("/show/{asset_id}/profile/risks")
    @Operation(operationId = "findAssetProfileRisks", summary = "Get the risk signals of an asset",
            description = "Returns risk signals derived from the asset profile and the detection findings: "
                    + "plaintext protocols, legacy TLS, untrusted or self-signed TLS servers it connected to, "
                    + "sessions accepted from outside the network, tunnels, beacons, scans, spoofing, a randomized "
                    + "MAC address and missing context. TLS signals are limited to the time range and the TLS data "
                    + "retention.")
    @ApiResponse(responseCode = "200", description = "Risk signals found.",
            content = @Content(schema = @Schema(implementation = AssetRisksResponse.class)))
    @ApiResponse(responseCode = "404", description = "Asset not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response profileRisks(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Asset UUID.") @PathParam("asset_id") UUID assetId,
                                 @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                 @Parameter(description = "Time range for the TLS and inbound session signals.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        Optional<AssetEntry> asset = resolveAsset(sc, assetId, organizationId, tenantId);
        if (asset.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        AssetRiskAssessor.Assessment assessment = new AssetRiskAssessor(nzyme).assess(asset.get(), timeRange);

        List<AssetRiskSignalResponse> signals = new ArrayList<>();
        for (AssetRiskAssessor.RiskSignal s : assessment.signals()) {
            signals.add(AssetRiskSignalResponse.create(s.signal(), s.severity(), s.count(), s.detail(), s.lastSeen()));
        }

        return Response.ok(AssetRisksResponse.create(signals, assessment.tls().sessions(), assessment.tls().versions())).build();
    }

    private Optional<AssetEntry> resolveAsset(SecurityContext sc, UUID assetId, UUID organizationId, UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Optional.empty();
        }

        return nzyme.getAssetsManager().findAsset(assetId, organizationId, tenantId);
    }

    private void invalidateAssetByMacCachesClusterWide() {
        nzyme.getMessageBus().sendToAllOnlineNodes(ClusterMessage.create(
                MessageType.INVALIDATE_CACHE,
                Map.of("cache_type", "asset_by_mac"),
                false
        ));
    }


    private static final Set<String> INFRASTRUCTURE_DIMENSIONS = Set.of("GATEWAY_MAC", "DNS_SERVER", "DHCP_SERVER", "NTP_SERVER");
    private static final Pattern MAC_PATTERN = Pattern.compile("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$");
    private static final int EXTERNAL_INFRASTRUCTURE_RESOLVED = 25;
    private static final int INFRASTRUCTURE_ASSET_LOOKBACK_DAYS = 30;

    @Nullable
    private static InetAddress parseAddress(String value) {
        try {
            return InetAddresses.forString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private L4AddressResponse infrastructureAddress(InetAddress ip, String value, UUID organizationId, UUID tenantId) {
        boolean local = ip.isSiteLocalAddress() || ip.isLinkLocalAddress();

        String mac = null;
        if (local) {
            mac = nzyme.getAssetsManager()
                    .findAssetsByIpAddress(ip, organizationId, tenantId,
                            DateTime.now().minusDays(INFRASTRUCTURE_ASSET_LOOKBACK_DAYS), 1, 0)
                    .stream().findFirst().map(AssetEntry::mac).orElse(null);
        }

        return RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.NONE,
                L4AddressData.create(mac, value, 0, null,
                        L4AddressAttributes.create(local, ip.isLoopbackAddress(), ip.isMulticastAddress())));
    }

    private EthernetMacAddressResponse infrastructureMac(String mac, UUID organizationId, UUID tenantId) {
        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAssetByMac(mac, organizationId, tenantId);
        return EthernetMacAddressResponse.create(
                mac,
                nzyme.getOuiService().lookup(mac).orElse(null),
                asset.map(AssetEntry::uuid).orElse(null),
                asset.map(AssetEntry::isActive).orElse(null),
                RestHelpers.macContextEntryToResponse(
                        nzyme.getContextService().findMacAddressContext(mac, organizationId, tenantId))
        );
    }

}