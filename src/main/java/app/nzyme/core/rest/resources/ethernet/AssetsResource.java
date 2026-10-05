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
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraintValidator;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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
public class AssetsResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    public Response allAssets(@Context SecurityContext sc,
                              @QueryParam("organization_id") UUID organizationId,
                              @QueryParam("tenant_id") UUID tenantId,
                              @QueryParam("time_range") @Valid String timeRangeParameter,
                              @QueryParam("filters") String filtersParameter,
                              @QueryParam("order_column") @Nullable String orderColumnParam,
                              @QueryParam("order_direction") @Nullable String orderDirectionParam,
                              @QueryParam("limit") int limit,
                              @QueryParam("offset") int offset) {
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
    public Response activeAssetHistogram(@Context SecurityContext sc,
                              @QueryParam("organization_id") UUID organizationId,
                              @QueryParam("tenant_id") UUID tenantId,
                              @QueryParam("time_range") @Valid String timeRangeParameter) {
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
    public Response latestAssets(@Context SecurityContext sc,
                                 @QueryParam("organization_id") UUID organizationId,
                                 @QueryParam("tenant_id") UUID tenantId,
                                 @QueryParam("time_range") @Valid String timeRangeParameter,
                                 @QueryParam("filters") String filtersParameter,
                                 @QueryParam("limit") int limit,
                                 @QueryParam("offset") int offset) {
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
    public Response recentlyDisappearedAssets(@Context SecurityContext sc,
                                              @QueryParam("organization_id") UUID organizationId,
                                              @QueryParam("tenant_id") UUID tenantId,
                                              @QueryParam("time_range") @Valid String timeRangeParameter,
                                              @QueryParam("filters") String filtersParameter,
                                              @QueryParam("limit") int limit,
                                              @QueryParam("offset") int offset) {
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
    public Response one(@Context SecurityContext sc,
                        @PathParam("asset_id") UUID assetId,
                        @QueryParam("organization_id") UUID organizationId,
                        @QueryParam("tenant_id") UUID tenantId) {
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
    public Response hostnames(@Context SecurityContext sc,
                              @PathParam("asset_id") UUID assetId,
                              @QueryParam("organization_id") UUID organizationId,
                              @QueryParam("tenant_id") UUID tenantId,
                              @QueryParam("time_range") @Valid String timeRangeParameter,
                              @QueryParam("order_column") @Nullable String orderColumnParam,
                              @QueryParam("order_direction") @Nullable String orderDirectionParam,
                              @QueryParam("limit") int limit,
                              @QueryParam("offset") int offset) {
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
    public Response ipAddresses(@Context SecurityContext sc,
                                @PathParam("asset_id") UUID assetId,
                                @QueryParam("organization_id") UUID organizationId,
                                @QueryParam("tenant_id") UUID tenantId,
                                @QueryParam("time_range") @Valid String timeRangeParameter,
                                @QueryParam("order_column") @Nullable String orderColumnParam,
                                @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                @QueryParam("limit") int limit,
                                @QueryParam("offset") int offset) {
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
    public Response deleteHostname(@Context SecurityContext sc,
                                   @PathParam("asset_id") UUID assetId,
                                   @PathParam("hostname_id") UUID hostnameId,
                                   @PathParam("organization_id") UUID organizationId,
                                   @PathParam("tenant_id") UUID tenantId) {
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
    public Response deleteIpAddress(@Context SecurityContext sc,
                                    @PathParam("asset_id") UUID assetId,
                                    @PathParam("address_id") UUID addressId,
                                    @PathParam("organization_id") UUID organizationId,
                                    @PathParam("tenant_id") UUID tenantId) {
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
    public Response getConfiguration(@Context SecurityContext sc,
                                     @PathParam("organization_id") UUID organizationId,
                                     @PathParam("tenant_id") UUID tenantId) {
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
    public Response setConfiguration(@Context SecurityContext sc,
                                     @PathParam("organization_id") UUID organizationId,
                                     @PathParam("tenant_id") UUID tenantId,
                                     @Valid GenericConfigurationUpdateRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (req.change().isEmpty()) {
            return Response.status(422).build();
        }

        for (Map.Entry<String, Object> c : req.change().entrySet()) {
            //noinspection SwitchStatementWithTooFewBranches
            switch (c.getKey()) {
                case "assets_statistics_retention_time_days":
                    if (!ConfigurationEntryConstraintValidator
                            .checkConstraints(AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS, c)) {
                        return Response.status(422).build();
                    }
                    break;
                default:
                    return Response.status(Response.Status.BAD_REQUEST).build();
            }

            nzyme.getDatabaseCoreRegistry().setValue(c.getKey(), c.getValue().toString(), organizationId, tenantId);
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