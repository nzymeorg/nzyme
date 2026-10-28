package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.L4AddressDataAddressNumberNumberAggregationResult;
import app.nzyme.core.database.generic.ThreeColumnWithKeyHistogramOrderColumn;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.vpn.VPN;
import app.nzyme.core.ethernet.vpn.VpnRegistryKeys;
import app.nzyme.core.rest.requests.GenericConfigurationUpdateRequest;
import app.nzyme.core.rest.responses.ethernet.vpn.VpnConfigurationResponse;
import app.nzyme.core.rest.responses.ethernet.vpn.VpnDisplaySettingsResponse;
import app.nzyme.core.registry.RegistryChangeValidator;
import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.core.ethernet.vpn.db.VpnTunnelEntry;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.vpn.VpnTunnelDetailsResponse;
import app.nzyme.core.rest.responses.ethernet.vpn.VpnTunnelsListResponse;
import app.nzyme.core.rest.responses.shared.*;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/vpn")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "VPN", description = "WireGuard and OpenVPN tunnels that Nzyme taps observed on the network, "
        + "including the tunnel endpoints, the transport, whether the handshake was observed and the tunneled "
        + "bytes. Each tunnel is tracked over its lifetime across all underlying TCP sessions or UDP conversations "
        + "between the same endpoints.",
        externalDocs = @ExternalDocumentation(description = "VPN tunnels in the Nzyme documentation",
                url = "https://go.nzyme.org/ethernet-vpn"))
public class VpnResource extends TapDataHandlingResource {

    private static final String MANAGE_PERMISSION = "ethernet_monitoring_manage";

    private static final RegistryKey[] CONFIGURATION_KEYS = {
            VpnRegistryKeys.EXCLUDE_TAILSCALE_BY_DEFAULT
    };

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/organization/{organization_id}/tenant/{tenant_id}/display")
    @Operation(operationId = "findVpnDisplaySettings", summary = "Get the VPN connection display settings of a tenant",
            description = "Returns the settings every user needs to render the VPN connections page, such as "
                    + "whether Tailscale connections are hidden by a default filter.")
    @ApiResponse(responseCode = "200", description = "Settings found.",
            content = @Content(schema = @Schema(implementation = VpnDisplaySettingsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response displaySettings(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                    @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(VpnDisplaySettingsResponse.create(
                nzyme.getEthernet().vpn().excludeTailscaleByDefault(organizationId, tenantId)
        )).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "findVpnConfiguration", summary = "Get the VPN connection settings of a tenant",
            description = "Returns the VPN connection settings of the tenant. Requires the ethernet_monitoring_manage "
                    + "feature permission.")
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = VpnConfigurationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response getConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(VpnConfigurationResponse.create(List.of(
                booleanEntry(VpnRegistryKeys.EXCLUDE_TAILSCALE_BY_DEFAULT, "Exclude Tailscale by default", organizationId, tenantId)
        ))).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "updateVpnConfiguration", summary = "Update the VPN connection settings of a tenant",
            description = "Updates one or more VPN connection settings of the tenant. Accepted keys are the keys "
                    + "returned by the configuration endpoint. Requires the ethernet_monitoring_manage feature "
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
                nzyme.getEthernet().vpn().booleanValue(key, organizationId, tenantId),
                ConfigurationEntryValueType.BOOLEAN,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                "ethernet-vpn-config"
        );
    }

    @GET
    @Path("/tunnels")
    @Operation(operationId = "findVpnTunnels", summary = "List VPN tunnels",
            description = "Returns all VPN tunnels observed in the time range, newest established first by default. "
                    + "Each tunnel carries the VPN type, the transport protocol, the handshake status and the "
                    + "message types observed.")
    @ApiResponse(responseCode = "200", description = "Tunnels found.",
            content = @Content(schema = @Schema(implementation = VpnTunnelsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response tunnels(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                            @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        VPN.OrderColumn orderColumn = VPN.OrderColumn.ESTABLISHED_AT;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = VPN.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getEthernet().vpn().countAllTunnels(timeRange, filters, taps);

        List<VpnTunnelDetailsResponse> tunnels = Lists.newArrayList();
        for (VpnTunnelEntry tunnel : nzyme.getEthernet().vpn()
                .findAllTunnels(timeRange, filters, orderColumn, orderDirection, limit, offset, taps)) {
            tunnels.add(buildTunnelDetails(tunnel, organizationId, tenantId));
        }

        return Response.ok(VpnTunnelsListResponse.create(total, tunnels)).build();
    }

    @GET
    @Path("/tunnels/show/{session_key}")
    @Operation(operationId = "findVpnTunnel", summary = "Get a VPN tunnel",
            description = "Returns a single VPN tunnel by its session key.")
    @ApiResponse(responseCode = "200", description = "Tunnel found.",
            content = @Content(schema = @Schema(implementation = VpnTunnelDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Tunnel not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response tunnel(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Session key of the tunnel.") @PathParam("session_key") String sessionKey,
                           @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<VpnTunnelEntry> tunnel = nzyme.getEthernet().vpn().findTunnel(sessionKey, taps);

        if (tunnel.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(buildTunnelDetails(tunnel.get(), organizationId, tenantId)).build();
    }

    @GET
    @Path("/tunnels/l4/{l4_type}/{session_key}")
    @Operation(operationId = "findVpnTunnelOfL4Session", summary = "Get the VPN tunnel of an L4 session",
            description = "Returns the VPN tunnel that a TCP session or UDP conversation belongs to. A tunnel is "
                    + "keyed by its first underlying session, but UDP tunnels are usually carried by a chain of "
                    + "conversations between the same endpoints. Later conversations are matched by endpoints and "
                    + "time overlap.")
    @ApiResponse(responseCode = "200", description = "Tunnel found.",
            content = @Content(schema = @Schema(implementation = VpnTunnelDetailsResponse.class)))
    @ApiResponse(responseCode = "400", description = "The L4 type or start time is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Tunnel not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response tunnelOfL4Session(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "L4 type of the session, TCP or UDP.") @PathParam("l4_type") String l4TypeParam,
                                      @Parameter(description = "Session key of the L4 session.") @PathParam("session_key") String sessionKey,
                                      @Parameter(description = "Start time of the L4 session in ISO 8601 format.") @QueryParam("start_time") String startTimeParam,
                                      @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                      @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                      @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String tapIds) {
        List<UUID> taps = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, tapIds);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        L4Type l4Type;
        DateTime startTime;
        try {
            l4Type = L4Type.valueOf(l4TypeParam.toUpperCase());
            startTime = DateTime.parse(startTimeParam);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Optional<VpnTunnelEntry> tunnel = nzyme.getEthernet().vpn()
                .findTunnelOfL4Session(l4Type, sessionKey, startTime, taps);

        if (tunnel.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(buildTunnelDetails(tunnel.get(), organizationId, tenantId)).build();
    }

    @GET
    @Path("/tunnels/active/histogram")
    @Operation(operationId = "findVpnActiveTunnelsHistogram", summary = "Get histogram of active VPN tunnels",
            description = "Returns the number of VPN tunnels that were active in each bucket of the time range. "
                    + "The bucket size is chosen automatically based on the length of the time range.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = NumericHistogramResponse.class)))
    public Response activeTunnelsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                           @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                           @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Map<DateTime, Integer> buckets = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().vpn()
                .getActiveTunnelsHistogram(timeRange, bucketing, filters, tapUUIDs)) {
            buckets.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(NumericHistogramResponse.create(buckets, bucketing.bucketSizeMs())).build();
    }

    @GET
    @Path("/tunnels/clients/top/histogram")
    @Operation(operationId = "findVpnTopClients", summary = "List top VPN clients",
            description = "Returns the client addresses with the most VPN tunnels in the time range, together with "
                    + "the tunnel count and the number of tunneled bytes.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response topClientsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                        @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                        @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                        @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                        @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                        @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                        @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                        @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                        @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        return topAddresses(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, taps,
                orderColumnParam, orderDirectionParam, limit, offset, true);
    }

    @GET
    @Path("/tunnels/servers/top/histogram")
    @Operation(operationId = "findVpnTopServers", summary = "List top VPN servers",
            description = "Returns the VPN server addresses with the most tunnels in the time range, together with "
                    + "the tunnel count and the number of tunneled bytes.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ThreeColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response topServersHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                        @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                        @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                        @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                        @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                        @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                        @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                        @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                        @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        return topAddresses(sc, organizationId, tenantId, timeRangeParameter, filtersParameter, taps,
                orderColumnParam, orderDirectionParam, limit, offset, false);
    }

    private Response topAddresses(SecurityContext sc,
                                  UUID organizationId,
                                  UUID tenantId,
                                  String timeRangeParameter,
                                  String filtersParameter,
                                  String taps,
                                  String orderColumnParam,
                                  String orderDirectionParam,
                                  int limit,
                                  int offset,
                                  boolean clients) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ThreeColumnWithKeyHistogramOrderColumn orderColumn = ThreeColumnWithKeyHistogramOrderColumn.VALUE1;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = ThreeColumnWithKeyHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        VPN vpn = nzyme.getEthernet().vpn();
        long count = clients
                ? vpn.getTopClientsCount(timeRange, filters, tapUUIDs)
                : vpn.getTopServersCount(timeRange, filters, tapUUIDs);
        List<L4AddressDataAddressNumberNumberAggregationResult> results = clients
                ? vpn.getTopClients(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)
                : vpn.getTopServers(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs);

        List<ThreeColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (L4AddressDataAddressNumberNumberAggregationResult x : results) {
            values.add(ThreeColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(
                            RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, L4Type.UDP, x.key()),
                            HistogramValueType.L4_ADDRESS,
                            null),
                    HistogramValueStructureResponse.create(x.value1(), HistogramValueType.INTEGER, null),
                    HistogramValueStructureResponse.create(x.value2(), HistogramValueType.BYTES, null),
                    x.key().address()
            ));
        }

        return Response.ok(ThreeColumnTableHistogramResponse.create(count, true, values)).build();
    }

    private VpnTunnelDetailsResponse buildTunnelDetails(VpnTunnelEntry t, UUID organizationId, UUID tenantId) {
        L4Type l4Type = L4Type.valueOf(t.l4Type());

        return VpnTunnelDetailsResponse.create(
                RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, l4Type, t.client()),
                RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, l4Type, t.server()),
                t.sessionKey(),
                t.l4Type(),
                t.vpnType(),
                t.handshakeStatus(),
                t.messageTypes(),
                t.provider(),
                t.serverName(),
                t.details(),
                t.connectionStatus(),
                t.tunneledBytes(),
                t.establishedAt(),
                t.terminatedAt(),
                t.mostRecentSegmentTime(),
                t.durationMs()
        );
    }

}
