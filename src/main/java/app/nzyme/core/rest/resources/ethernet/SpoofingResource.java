package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.ethernet.spoofing.SpoofingRegistryKeys;
import app.nzyme.core.registry.RegistryChangeValidator;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.requests.GenericConfigurationUpdateRequest;
import app.nzyme.core.rest.responses.ethernet.spoofing.SpoofingConfigurationResponse;
import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Path("/api/ethernet/spoofing")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Spoofing", description = "Configuration of the spoofing detections of a tenant.")
public class SpoofingResource extends TapDataHandlingResource {

    private static final String MANAGE_PERMISSION = "ethernet_monitoring_manage";

    private static final RegistryKey[] CONFIGURATION_KEYS = {
            SpoofingRegistryKeys.ARP_DETECTION_ENABLED,
            SpoofingRegistryKeys.ARP_DETECTION_WINDOW_MINUTES,
            SpoofingRegistryKeys.ARP_OWNER_LOOKBACK_HOURS,
            SpoofingRegistryKeys.ARP_MINIMUM_CLAIMS,
            SpoofingRegistryKeys.ARP_RETENTION_TIME_DAYS,
            SpoofingRegistryKeys.ARP_ALERT_ON_NEW,
            SpoofingRegistryKeys.DHCP_DETECTION_ENABLED,
            SpoofingRegistryKeys.DHCP_LEARNING_PERIOD_HOURS,
            SpoofingRegistryKeys.DHCP_DETECTION_WINDOW_MINUTES,
            SpoofingRegistryKeys.DHCP_RETENTION_TIME_DAYS,
            SpoofingRegistryKeys.DHCP_ALERT_ON_ROGUE_SERVER,
            SpoofingRegistryKeys.DHCP_ALERT_ON_PARAMETERS_CHANGED,
            SpoofingRegistryKeys.NAMERES_DETECTION_ENABLED,
            SpoofingRegistryKeys.NAMERES_DETECTION_WINDOW_MINUTES,
            SpoofingRegistryKeys.NAMERES_LLMNR_NAME_THRESHOLD,
            SpoofingRegistryKeys.NAMERES_NBTNS_NAME_THRESHOLD,
            SpoofingRegistryKeys.NAMERES_MDNS_NAME_THRESHOLD,
            SpoofingRegistryKeys.NAMERES_RETENTION_TIME_DAYS,
            SpoofingRegistryKeys.NAMERES_RESPONSES_RETENTION_TIME_DAYS,
            SpoofingRegistryKeys.NAMERES_ALERT_ON_NEW
    };

    @Inject
    private NzymeNode nzyme;

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "findSpoofingConfiguration", summary = "Get the spoofing detection configuration of a tenant",
            description = "Returns the spoofing detection settings of the tenant in two groups: detection "
                    + "parameters and alerting. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = SpoofingConfigurationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response getConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<ConfigurationEntryResponse> detection = Lists.newArrayList();
        detection.add(booleanEntry(SpoofingRegistryKeys.ARP_DETECTION_ENABLED, "ARP poisoning detection enabled", organizationId, tenantId));
        detection.add(numberEntry(SpoofingRegistryKeys.ARP_DETECTION_WINDOW_MINUTES, "ARP poisoning: Detection window (Minutes)", organizationId, tenantId));
        detection.add(numberEntry(SpoofingRegistryKeys.ARP_OWNER_LOOKBACK_HOURS, "ARP poisoning: Address owner lookback (Hours)", organizationId, tenantId));
        detection.add(numberEntry(SpoofingRegistryKeys.ARP_MINIMUM_CLAIMS, "ARP poisoning: Minimum claims in window", organizationId, tenantId));
        detection.add(numberEntry(SpoofingRegistryKeys.ARP_RETENTION_TIME_DAYS, "ARP poisoning: Finding retention time (Days)", organizationId, tenantId));
        detection.add(booleanEntry(SpoofingRegistryKeys.DHCP_DETECTION_ENABLED, "DHCP spoofing detection enabled", organizationId, tenantId));
        detection.add(numberEntry(SpoofingRegistryKeys.DHCP_LEARNING_PERIOD_HOURS, "DHCP spoofing: Server learning period (Hours)", organizationId, tenantId));
        detection.add(numberEntry(SpoofingRegistryKeys.DHCP_DETECTION_WINDOW_MINUTES, "DHCP spoofing: Detection window (Minutes)", organizationId, tenantId));
        detection.add(numberEntry(SpoofingRegistryKeys.DHCP_RETENTION_TIME_DAYS, "DHCP spoofing: Finding retention time (Days)", organizationId, tenantId));
        detection.add(booleanEntry(SpoofingRegistryKeys.NAMERES_DETECTION_ENABLED, "Name resolution poisoning detection enabled", organizationId, tenantId));
        detection.add(numberEntry(SpoofingRegistryKeys.NAMERES_DETECTION_WINDOW_MINUTES, "Name resolution poisoning: Detection window (Minutes)", organizationId, tenantId));
        detection.add(numberEntry(SpoofingRegistryKeys.NAMERES_LLMNR_NAME_THRESHOLD, "Name resolution poisoning: LLMNR names threshold", organizationId, tenantId));
        detection.add(numberEntry(SpoofingRegistryKeys.NAMERES_NBTNS_NAME_THRESHOLD, "Name resolution poisoning: NBT-NS names threshold", organizationId, tenantId));
        detection.add(numberEntry(SpoofingRegistryKeys.NAMERES_MDNS_NAME_THRESHOLD, "Name resolution poisoning: mDNS names threshold", organizationId, tenantId));
        detection.add(numberEntry(SpoofingRegistryKeys.NAMERES_RETENTION_TIME_DAYS, "Name resolution poisoning: Finding retention time (Days)", organizationId, tenantId));
        detection.add(numberEntry(SpoofingRegistryKeys.NAMERES_RESPONSES_RETENTION_TIME_DAYS, "Name resolution poisoning: Response retention time (Days)", organizationId, tenantId));

        List<ConfigurationEntryResponse> alerting = Lists.newArrayList();
        alerting.add(booleanEntry(SpoofingRegistryKeys.ARP_ALERT_ON_NEW, "Alert on new ARP poisoning finding", organizationId, tenantId));
        alerting.add(booleanEntry(SpoofingRegistryKeys.DHCP_ALERT_ON_ROGUE_SERVER, "Alert on rogue DHCP server", organizationId, tenantId));
        alerting.add(booleanEntry(SpoofingRegistryKeys.DHCP_ALERT_ON_PARAMETERS_CHANGED, "Alert on changed DHCP configuration", organizationId, tenantId));
        alerting.add(booleanEntry(SpoofingRegistryKeys.NAMERES_ALERT_ON_NEW, "Alert on name resolution poisoning", organizationId, tenantId));

        return Response.ok(SpoofingConfigurationResponse.create(detection, alerting)).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "updateSpoofingConfiguration", summary = "Update the spoofing detection configuration of a tenant",
            description = "Updates one or more spoofing detection settings of the tenant. Accepted keys are the "
                    + "keys returned by the configuration endpoint. Requires the ethernet_monitoring_manage feature "
                    + "permission.")
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The change contains a key that is not part of the spoofing detection configuration.", content = @Content)
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
                nzyme.getEthernet().spoofing().booleanValue(key, organizationId, tenantId),
                ConfigurationEntryValueType.BOOLEAN,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                "ethernet-spoofing-config"
        );
    }

    private ConfigurationEntryResponse numberEntry(RegistryKey key, String title, UUID organizationId, UUID tenantId) {
        return ConfigurationEntryResponse.create(
                key.key(),
                title,
                nzyme.getEthernet().spoofing().intValue(key, organizationId, tenantId),
                ConfigurationEntryValueType.NUMBER,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                "ethernet-spoofing-config"
        );
    }

}
