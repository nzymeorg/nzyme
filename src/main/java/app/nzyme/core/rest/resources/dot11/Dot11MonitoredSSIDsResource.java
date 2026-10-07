package app.nzyme.core.rest.resources.dot11;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.db.Dot11KnownNetwork;
import app.nzyme.core.dot11.monitoring.ssids.KnownSSIDsRegistryKeys;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.requests.ApproveByRegexRequest;
import app.nzyme.core.rest.requests.UpdateConfigurationRequest;
import app.nzyme.core.rest.responses.dot11.monitoring.ssids.KnownNetworkDetailsResponse;
import app.nzyme.core.rest.responses.dot11.monitoring.ssids.KnownNetworksListResponse;
import app.nzyme.core.rest.responses.dot11.monitoring.ssids.SSIDMonitoringConfigurationResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraintValidator;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
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
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Path("/api/dot11/monitoring/networks")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Monitoring", description = "A monitored network describes the expected state of one of your own WiFi "
        + "networks so that Nzyme can alert on any deviation. This group also covers SSID monitoring, probe request "
        + "monitoring and the known clients of a monitored network.",
        externalDocs = @ExternalDocumentation(description = "Network monitoring in the Nzyme documentation",
                url = "https://go.nzyme.org/wifi-network-monitoring"))
public class Dot11MonitoredSSIDsResource extends UserAuthenticatedResource {

    private static final Logger LOG = LogManager.getLogger(Dot11MonitoredSSIDsResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "findMonitoredSsids", summary = "List known networks of a tenant",
            description = "Returns all networks that SSID monitoring recorded for a tenant, called known networks, "
                    + "with their approval and ignore status. Pass a regular expression to only return matching SSIDs. "
                    + "Sorted by SSID ascending unless both sorting parameters are passed. Networks that have not "
                    + "been seen for 30 days are deleted automatically. The page size is limited to 250. "
                    + "Requires the dot11_monitoring_manage feature permission.",
            externalDocs = @ExternalDocumentation(description = "SSID monitoring in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-ssid-monitoring"))
    @ApiResponse(responseCode = "200", description = "Known networks found.",
            content = @Content(schema = @Schema(implementation = KnownNetworksListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Requested page size is larger than 250, the sorting parameters are invalid, or the regular expression does not compile.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                            @Parameter(description = "Optional regular expression. Only SSIDs matching it are returned.") @QueryParam("regex") @Nullable String regex,
                            @Parameter(description = "Sorting column. One of SSID, STATUS, LAST_SEEN. Case insensitive.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction: ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Organization UUID.") @PathParam("organization_id") @NotNull UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") @NotNull UUID tenantId) {
        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Dot11.KnownSSIDOrderColumn orderColumn = Dot11.KnownSSIDOrderColumn.SSID;
        OrderDirection orderDirection = OrderDirection.ASC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Dot11.KnownSSIDOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        List<Dot11KnownNetwork> networks;
        long total;
        if (regex != null && !regex.isBlank()) {
            // Regex was supplied. Only search for matching.
            Pattern pattern;
            try {
                pattern = Pattern.compile(regex);
            } catch (PatternSyntaxException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            networks = nzyme.getDot11().findAllKnownNetworksByPattern(organizationId, tenantId, pattern, orderColumn, orderDirection, limit, offset);
            total = nzyme.getDot11().countAllKnownNetworksByPattern(organizationId, tenantId, pattern);
        } else {
            // No regex supplied. Search for all.
            total = nzyme.getDot11().countAllKnownNetworks(organizationId, tenantId);
            networks = nzyme.getDot11().findAllKnownNetworks(organizationId, tenantId, orderColumn, orderDirection, limit, offset);
        }

        List<KnownNetworkDetailsResponse> result = Lists.newArrayList();
        for (Dot11KnownNetwork kn : networks) {
            result.add(KnownNetworkDetailsResponse.create(
                    kn.uuid(),
                    kn.organizationId(),
                    kn.tenantId(),
                    kn.ssid(),
                    kn.isApproved(),
                    kn.isIgnored(),
                    kn.firstSeen(),
                    kn.lastSeen()
            ));
        }

        return Response.ok(KnownNetworksListResponse.create(total, result)).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{uuid}/approve")
    @Operation(operationId = "approveMonitoredSsid", summary = "Approve a known network",
            description = "Marks the known network as approved. Approved networks do not trigger new alerts and their "
                    + "existing alerts resolve within a few minutes. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Known network approved.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Known network not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response approve(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Known network UUID.") @PathParam("uuid") UUID uuid,
                            @Parameter(description = "Organization UUID.") @PathParam("organization_id") @NotNull UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") @NotNull UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Dot11KnownNetwork> knownNetwork = nzyme.getDot11().findKnownNetwork(uuid, organizationId, tenantId);
        if (knownNetwork.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().changeStatusOfKnownNetwork(knownNetwork.get().id(), true);

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{uuid}/revoke")
    @Operation(operationId = "revokeMonitoredSsid", summary = "Revoke approval of a known network",
            description = "Marks the known network as not approved again. It triggers alerts again until it is "
                    + "approved, ignored or deleted. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Approval revoked.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Known network not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response revoke(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Known network UUID.") @PathParam("uuid") UUID uuid,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") @NotNull UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") @NotNull UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Dot11KnownNetwork> knownNetwork = nzyme.getDot11().findKnownNetwork(uuid, organizationId, tenantId);
        if (knownNetwork.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().changeStatusOfKnownNetwork(knownNetwork.get().id(), false);

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{uuid}/ignore")
    @Operation(operationId = "ignoreMonitoredSsid", summary = "Ignore a known network",
            description = "Marks the known network as ignored. It stays in the list and stops triggering alerts, but "
                    + "it is not marked as approved. Existing alerts resolve within a few minutes. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Known network ignored.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Known network not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response ignore(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Known network UUID.") @PathParam("uuid") UUID uuid,
                            @Parameter(description = "Organization UUID.") @PathParam("organization_id") @NotNull UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") @NotNull UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Dot11KnownNetwork> knownNetwork = nzyme.getDot11().findKnownNetwork(uuid, organizationId, tenantId);
        if (knownNetwork.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().changeIgnoreStatusOfKnownNetwork(knownNetwork.get().id(), true);

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{uuid}/unignore")
    @Operation(operationId = "unignoreMonitoredSsid", summary = "Stop ignoring a known network",
            description = "Removes the ignored flag. The network triggers alerts again unless it is approved. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Known network no longer ignored.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Known network not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response unignore(@Parameter(hidden = true) @Context SecurityContext sc,
                             @Parameter(description = "Known network UUID.") @PathParam("uuid") UUID uuid,
                             @Parameter(description = "Organization UUID.") @PathParam("organization_id") @NotNull UUID organizationId,
                             @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") @NotNull UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Dot11KnownNetwork> knownNetwork = nzyme.getDot11().findKnownNetwork(uuid, organizationId, tenantId);
        if (knownNetwork.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().changeIgnoreStatusOfKnownNetwork(knownNetwork.get().id(), false);

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{uuid}")
    @Operation(operationId = "deleteMonitoredSsid", summary = "Delete a known network",
            description = "Deletes the known network record and resolves its alerts within a few minutes. The network "
                    + "reappears as a new, unapproved network the next time it is observed. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Known network deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Known network not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response deleteSingle(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Known network UUID.") @PathParam("uuid") UUID uuid,
                                 @Parameter(description = "Organization UUID.") @PathParam("organization_id") @NotNull UUID organizationId,
                                 @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") @NotNull UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Dot11KnownNetwork> knownNetwork = nzyme.getDot11().findKnownNetwork(uuid, organizationId, tenantId);
        if (knownNetwork.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().deleteKnownNetwork(knownNetwork.get().id());

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "deleteMonitoredSsids", summary = "Delete all known networks of a tenant",
            description = "Deletes every known network record of the tenant, including approved and ignored networks. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Known networks deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteAllOfTenant(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Organization UUID.") @PathParam("organization_id") @NotNull UUID organizationId,
                                      @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") @NotNull UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().deleteKnownNetworksOfTenant(organizationId, tenantId);

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/approve")
    @Operation(operationId = "approveMonitoredSsids", summary = "Approve all known networks of a tenant",
            description = "Approves every known network of the tenant. If a regular expression is passed in the body, "
                    + "only networks with a matching SSID are approved. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Known networks approved.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The regular expression does not compile.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response approveAllOfTenant(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @RequestBody(description = "Optional regular expression that limits approval to "
                                               + "matching SSIDs. Omit the body or the regex to approve all SSIDs.", required = false, content = @Content(mediaType = "application/json"))
                                       @Nullable ApproveByRegexRequest req,
                                       @Parameter(description = "Organization UUID.") @PathParam("organization_id") @NotNull UUID organizationId,
                                       @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") @NotNull UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (req != null && req.regex() != null && !req.regex().isEmpty()) {
            // Regex was supplied. Approve only matching.
            Pattern pattern;
            try {
                pattern = Pattern.compile(req.regex());
            } catch (PatternSyntaxException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            nzyme.getDot11().changeStatusOfAllKnownNetworksOfTenantByRegex(organizationId, tenantId, pattern, true);
        } else {
            // No regex supplied. Approve all.
            nzyme.getDot11().changeStatusOfAllKnownNetworksOfTenant(organizationId, tenantId, true);
        }

        return Response.ok().build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "findSsidMonitoringConfiguration", summary = "Get SSID monitoring configuration of a tenant",
            description = "Returns whether SSID monitoring and event generation are enabled, plus the dwell time in "
                    + "minutes that an SSID has to be active within the last 24 hours before Nzyme adds it to the "
                    + "known networks. The dwell time defaults to 5 minutes. Values use the generic configuration "
                    + "entry format of the web interface. Requires the dot11_monitoring_manage feature permission.",
            externalDocs = @ExternalDocumentation(description = "SSID monitoring in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-ssid-monitoring"))
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = SSIDMonitoringConfigurationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response configuration(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Organization UUID.") @PathParam("organization_id") @NotNull UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") @NotNull UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<String> isEnabled = nzyme.getDatabaseCoreRegistry().getValue(
                KnownSSIDsRegistryKeys.IS_ENABLED.key(), organizationId, tenantId
        );

        Optional<String> eventingIsEnabled = nzyme.getDatabaseCoreRegistry().getValue(
                KnownSSIDsRegistryKeys.EVENTING_IS_ENABLED.key(), organizationId, tenantId
        );

        int dwellTimeMinutes = nzyme.getDatabaseCoreRegistry()
                .getValue(KnownSSIDsRegistryKeys.DWELL_TIME_MINUTES.key(), organizationId, tenantId)
                .map(Integer::valueOf)
                .orElse(5);

        SSIDMonitoringConfigurationResponse configuration = SSIDMonitoringConfigurationResponse.create(
                ConfigurationEntryResponse.create(
                        KnownSSIDsRegistryKeys.IS_ENABLED.key(),
                        "Is enabled",
                        isEnabled.isPresent() && isEnabled.get().equals("true"),
                        ConfigurationEntryValueType.BOOLEAN,
                        KnownSSIDsRegistryKeys.IS_ENABLED.defaultValue().orElse(null),
                        KnownSSIDsRegistryKeys.IS_ENABLED.requiresRestart(),
                        KnownSSIDsRegistryKeys.IS_ENABLED.constraints().orElse(Collections.emptyList()),
                        "wifi-ssid-monitoring"
                ),
                ConfigurationEntryResponse.create(
                        KnownSSIDsRegistryKeys.EVENTING_IS_ENABLED.key(),
                        "Event generation is enabled",
                        eventingIsEnabled.isPresent() && eventingIsEnabled.get().equals("true"),
                        ConfigurationEntryValueType.BOOLEAN,
                        KnownSSIDsRegistryKeys.EVENTING_IS_ENABLED.defaultValue().orElse(null),
                        KnownSSIDsRegistryKeys.EVENTING_IS_ENABLED.requiresRestart(),
                        KnownSSIDsRegistryKeys.EVENTING_IS_ENABLED.constraints().orElse(Collections.emptyList()),
                        "wifi-ssid-monitoring"
                ),
                ConfigurationEntryResponse.create(
                        KnownSSIDsRegistryKeys.DWELL_TIME_MINUTES.key(),
                        "24-hour minimum dwell time (Minutes)",
                        dwellTimeMinutes,
                        ConfigurationEntryValueType.NUMBER,
                        KnownSSIDsRegistryKeys.DWELL_TIME_MINUTES.defaultValue().orElse(null),
                        KnownSSIDsRegistryKeys.DWELL_TIME_MINUTES.requiresRestart(),
                        KnownSSIDsRegistryKeys.DWELL_TIME_MINUTES.constraints().orElse(Collections.emptyList()),
                        "wifi-ssid-monitoring"
                )
        );

        return Response.ok(configuration).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "updateSsidMonitoringConfiguration", summary = "Update SSID monitoring configuration of a tenant",
            description = "Accepts the keys is_enabled, eventing_is_enabled and dwell_time_minutes in the change map. "
                    + "Nzyme only collects known networks while is_enabled is true and only creates alerts while "
                    + "eventing_is_enabled is true. Each value is validated against the constraints of its "
                    + "configuration entry. Requires the dot11_monitoring_manage feature permission.",
            externalDocs = @ExternalDocumentation(description = "SSID monitoring in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-ssid-monitoring"))
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The change map is empty or a value violates the constraints of its "
            + "configuration entry.", content = @Content)
    public Response updateConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @RequestBody(description = "Map of configuration keys to new values.", required = true, content = @Content(mediaType = "application/json"))
                                        UpdateConfigurationRequest req,
                                        @Parameter(description = "Organization UUID.") @PathParam("organization_id") @NotNull UUID organizationId,
                                        @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") @NotNull UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (req.change().isEmpty()) {
            LOG.info("Empty configuration parameters.");
            return Response.status(422).build();
        }

        for (Map.Entry<String, Object> c : req.change().entrySet()) {
            switch (c.getKey()) {
                case "is_enabled":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(KnownSSIDsRegistryKeys.IS_ENABLED, c)) {
                        return Response.status(422).build();
                    }
                    break;
                case "eventing_is_enabled":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(KnownSSIDsRegistryKeys.EVENTING_IS_ENABLED, c)) {
                        return Response.status(422).build();
                    }
                    break;
                case "dwell_time_minutes":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(KnownSSIDsRegistryKeys.DWELL_TIME_MINUTES, c)) {
                        return Response.status(422).build();
                    }
                    break;
            }

            nzyme.getDatabaseCoreRegistry().setValue(c.getKey(), c.getValue().toString(), organizationId, tenantId);
        }

        return Response.ok().build();
    }

}
