package app.nzyme.core.rest.resources.dot11;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.detection.alerts.db.DetectionAlertEntry;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.db.Dot11KnownClient;
import app.nzyme.core.dot11.db.Dot11SecuritySuiteJson;
import app.nzyme.core.dot11.db.monitoring.*;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.*;
import app.nzyme.core.rest.responses.dot11.Dot11MacAddressContextResponse;
import app.nzyme.core.rest.responses.dot11.Dot11MacAddressResponse;
import app.nzyme.core.rest.responses.dot11.SSIDSimilarityResponse;
import app.nzyme.core.rest.responses.dot11.monitoring.*;
import app.nzyme.core.rest.responses.dot11.monitoring.clients.ClientMonitoringConfigurationResponse;
import app.nzyme.core.rest.responses.dot11.monitoring.clients.KnownClientDetailsResponse;
import app.nzyme.core.rest.responses.dot11.monitoring.clients.KnownClientsListResponse;
import app.nzyme.core.rest.responses.dot11.monitoring.configimport.*;
import app.nzyme.core.util.TimeRangeFactory;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import tools.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import info.debatty.java.stringsimilarity.JaroWinkler;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

@Path("/api/dot11/monitoring")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Monitoring", description = "A monitored network describes the expected state of one of your own WiFi "
        + "networks so that Nzyme can alert on any deviation. This group also covers SSID monitoring, probe request "
        + "monitoring and the known clients of a monitored network.",
        externalDocs = @ExternalDocumentation(description = "Network monitoring in the Nzyme documentation",
                url = "https://go.nzyme.org/wifi-network-monitoring"))
public class Dot11MonitoredNetworksResource extends TapDataHandlingResource {

    private static final Logger LOG = LogManager.getLogger(Dot11MonitoredNetworksResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids")
    @Operation(operationId = "findMonitoredNetworks", summary = "List monitored networks of a tenant",
            description = "Returns a summary of every monitored network of a tenant, including whether any enabled "
                    + "monitor of the network currently has an active alert. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Monitored networks found.",
            content = @Content(schema = @Schema(implementation = MonitoredSSIDListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") @NotNull UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") @NotNull UUID tenantId) {
        if (!passedTenantDataAccessible(sc,  organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<MonitoredSSIDSummaryResponse> ssids = Lists.newArrayList();
        for (MonitoredSSID ssid : nzyme.getDot11().findAllMonitoredSSIDs(organizationId, tenantId)) {

            boolean isAlerted = false;
            for (DetectionAlertEntry alert : nzyme.getDetectionAlertService()
                    .findAllActiveAlertsOfMonitoredNetwork(ssid.uuid())) {
                DetectionType detectionType;
                try {
                    detectionType = DetectionType.valueOf(alert.detectionType());
                } catch(IllegalArgumentException e) {
                    LOG.error("Invalid detection type [{}]. Skipping.", alert.detectionType());
                    continue;
                }

                switch (detectionType) {
                    case DOT11_MONITOR_BSSID:
                        if (!ssid.enabledUnexpectedBSSID()) {
                            continue;
                        }
                        isAlerted = true;
                        break;
                    case DOT11_MONITOR_CHANNEL:
                        if (!ssid.enabledUnexpectedChannel()) {
                            continue;
                        }
                        isAlerted = true;
                        break;
                    case DOT11_MONITOR_SECURITY_SUITE:
                        if (!ssid.enabledUnexpectedSecuritySuites()) {
                            continue;
                        }
                        isAlerted = true;
                        break;
                    case DOT11_MONITOR_FINGERPRINT:
                        if (!ssid.enabledUnexpectedFingerprint()) {
                            continue;
                        }
                        isAlerted = true;
                        break;
                    case DOT11_MONITOR_SIGNAL_TRACK:
                        if (!ssid.enabledUnexpectedSignalTracks()) {
                            continue;
                        }
                        isAlerted = true;
                        break;
                    case DOT11_MONITOR_DISCO_ANOMALIES:
                        if (!ssid.enabledDiscoMonitor()) {
                            continue;
                        }
                        isAlerted = true;
                        break;
                    case DOT11_MONITOR_SIMILAR_LOOKING_SSID:
                        if (!ssid.enabledSimilarLookingSSID()) {
                            continue;
                        }
                        isAlerted = true;
                        break;
                    case DOT11_MONITOR_SSID_SUBSTRING:
                        if (!ssid.enabledSSIDSubstring()) {
                            continue;
                        }
                        isAlerted = true;
                        break;
                    case DOT11_UNAPPROVED_CLIENT:
                        if (!ssid.enabledClientEventing() || !ssid.enabledClientMonitoring()) {
                            continue;
                        }
                        isAlerted = true;
                        break;
                }

                if (isAlerted) {
                    break;
                }
            }

            ssids.add(MonitoredSSIDSummaryResponse.create(
                    ssid.uuid(),
                    ssid.isEnabled(),
                    ssid.ssid(),
                    ssid.organizationId(),
                    ssid.tenantId(),
                    null,
                    null,
                    null,
                    ssid.createdAt(),
                    ssid.updatedAt(),
                    isAlerted
            ));
        }

        return Response.ok(MonitoredSSIDListResponse.create(ssids)).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}")
    @Operation(operationId = "findMonitoredNetwork", summary = "Get monitored network details",
            description = "Returns the full configuration of a monitored network: expected BSSIDs with fingerprints, "
                    + "channels, security suites, restricted SSID substrings, which monitors are enabled and which "
                    + "monitors currently have an active alert. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Monitored network found.",
            content = @Content(schema = @Schema(implementation = MonitoredSSIDDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc, @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID uuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> allAccessibleTapUUIDs = parseAndValidateTapIds(authenticatedUser, nzyme, "*");

        Optional<MonitoredSSID> result = nzyme.getDot11().findMonitoredSSID(uuid);

        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        MonitoredSSID ssid = result.get();

        if (!entityAccessible(authenticatedUser, ssid)){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Find all monitored BSSIDs.
        List<MonitoredBSSIDDetailsResponse> bssids = Lists.newArrayList();
        for (MonitoredBSSID bssid : nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(ssid.id())) {
            List<MonitoredFingerprintResponse> fingerprints = Lists.newArrayList();
            for (MonitoredFingerprint fp : nzyme.getDot11().findMonitoredFingerprintsOfMonitoredBSSID(bssid.id())) {
                fingerprints.add(MonitoredFingerprintResponse.create(fp.uuid(), fp.fingerprint()));
            }

            boolean isOnline = nzyme.getDot11()
                    .bssidExist(bssid.bssid(), TimeRangeFactory.fifteenMinutes(), allAccessibleTapUUIDs);

            Optional<MacAddressContextEntry> bssidContext = nzyme.getContextService().findMacAddressContext(
                    bssid.bssid(),
                    authenticatedUser.getOrganizationId(),
                    authenticatedUser.getTenantId()
            );

            bssids.add(MonitoredBSSIDDetailsResponse.create(
                    ssid.uuid(),
                    bssid.uuid(),
                    Dot11MacAddressResponse.create(
                            bssid.bssid(),
                            nzyme.getOuiService().lookup(bssid.bssid()).orElse(null),
                            null,
                            bssidContext.map(macAddressContextEntry ->
                                            Dot11MacAddressContextResponse.create(
                                                    macAddressContextEntry.name(),
                                                    macAddressContextEntry.description(),
                                                    macAddressContextEntry.notes()
                                            ))
                                    .orElse(null)
                    ),
                    isOnline,
                    fingerprints
            ));
        }

        // Monitored channels.
        List<MonitoredChannelResponse> channels = Lists.newArrayList();
        for (MonitoredChannel channel : nzyme.getDot11().findMonitoredChannelsOfMonitoredNetwork(ssid.id())) {
            int channelNumber = Dot11.frequencyToChannel((int) channel.frequency());
            channels.add(MonitoredChannelResponse.create(channel.uuid(), channel.frequency(), channelNumber));
        }

        // Monitored security suites.
        List<MonitoredSecuritySuiteResponse> securitySuites = Lists.newArrayList();
        for (MonitoredSecuritySuite suite : nzyme.getDot11().findMonitoredSecuritySuitesOfMonitoredNetwork(ssid.id())) {
            securitySuites.add(MonitoredSecuritySuiteResponse.create(suite.uuid(), suite.securitySuite()));
        }

        boolean isAlerted = false;
        boolean bssidAlerted = false;
        boolean channelAlerted = false;
        boolean securitySuitesAlerted = false;
        boolean fingerprintAlerted = false;
        boolean signalTracksAlerted = false;
        boolean discoAnomaliesAlerted = false;
        boolean similarSSIDAlerted = false;
        boolean restrictedSSIDSubstringAlerted = false;
        boolean unapprovedClientAlerted = false;
        for (DetectionAlertEntry alert : nzyme.getDetectionAlertService()
                .findAllActiveAlertsOfMonitoredNetwork(ssid.uuid())) {
            DetectionType detectionType;
            try {
                detectionType = DetectionType.valueOf(alert.detectionType());
            } catch(IllegalArgumentException e) {
                LOG.error("Invalid detection type [{}]. Skipping.", alert.detectionType());
                continue;
            }

            switch (detectionType) {
                case DOT11_MONITOR_BSSID:
                    if (!ssid.enabledUnexpectedBSSID()) {
                        continue;
                    }
                    bssidAlerted = true;
                    break;
                case DOT11_MONITOR_CHANNEL:
                    if (!ssid.enabledUnexpectedChannel()) {
                        continue;
                    }
                    channelAlerted = true;
                    break;
                case DOT11_MONITOR_SECURITY_SUITE:
                    if (!ssid.enabledUnexpectedSecuritySuites()) {
                        continue;
                    }
                    securitySuitesAlerted = true;
                    break;
                case DOT11_MONITOR_FINGERPRINT:
                    if (!ssid.enabledUnexpectedFingerprint()) {
                        continue;
                    }
                    fingerprintAlerted = true;
                    break;
                case DOT11_MONITOR_SIGNAL_TRACK:
                    if (!ssid.enabledUnexpectedSignalTracks()) {
                        continue;
                    }
                    signalTracksAlerted = true;
                    break;
                case DOT11_MONITOR_DISCO_ANOMALIES:
                    if (!ssid.enabledDiscoMonitor()) {
                        continue;
                    }
                    discoAnomaliesAlerted = true;
                    break;
                case DOT11_MONITOR_SIMILAR_LOOKING_SSID:
                    if (!ssid.enabledSimilarLookingSSID()) {
                        continue;
                    }
                    similarSSIDAlerted = true;
                    break;
                case DOT11_MONITOR_SSID_SUBSTRING:
                    if (!ssid.enabledSSIDSubstring()) {
                        continue;
                    }
                    restrictedSSIDSubstringAlerted = true;
                    break;
                case DOT11_UNAPPROVED_CLIENT:
                    if (!ssid.enabledClientEventing() || !ssid.enabledClientMonitoring()) {
                        continue;
                    }
                    unapprovedClientAlerted = true;
                    break;
            }

            isAlerted = true;
        }

        List<RestrictedSSIDSubstringDetailsResponse> restrictedSSIDSubstrings = nzyme.getDot11()
                .findAllRestrictedSSIDSubstrings(ssid.id())
                .stream()
                .map(rss -> RestrictedSSIDSubstringDetailsResponse.create(rss.uuid(), rss.substring(), rss.createdAt()))
                .collect(Collectors.toList());

        return Response.ok(MonitoredSSIDDetailsResponse.create(
                ssid.uuid(),
                ssid.isEnabled(),
                ssid.ssid(),
                ssid.organizationId(),
                ssid.tenantId(),
                bssids,
                channels,
                securitySuites,
                ssid.detectionConfigSimilarLookingSSIDThreshold(),
                restrictedSSIDSubstrings,
                ssid.createdAt(),
                ssid.updatedAt(),
                isAlerted,
                bssidAlerted,
                channelAlerted,
                securitySuitesAlerted,
                fingerprintAlerted,
                signalTracksAlerted,
                discoAnomaliesAlerted,
                similarSSIDAlerted,
                restrictedSSIDSubstringAlerted,
                unapprovedClientAlerted,
                ssid.enabledUnexpectedBSSID(),
                ssid.enabledUnexpectedChannel(),
                ssid.enabledUnexpectedSecuritySuites(),
                ssid.enabledUnexpectedFingerprint(),
                ssid.enabledUnexpectedSignalTracks(),
                ssid.enabledDiscoMonitor(),
                ssid.enabledSimilarLookingSSID(),
                ssid.enabledSSIDSubstring(),
                (ssid.enabledClientMonitoring() && ssid.enabledClientEventing())
        )).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids")
    @Operation(operationId = "createMonitoredNetwork", summary = "Create a monitored network",
            description = "Creates a new monitored network for the given SSID in an organization and tenant. The network "
                    + "starts without any expected BSSIDs, channels or security suites. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "201", description = "Monitored network created.", content = @Content)
    @ApiResponse(responseCode = "401", description = "The calling user cannot create monitored networks in the requested "
            + "organization or tenant.", content = @Content)
    public Response createMonitoredSSID(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @RequestBody(description = "SSID, organization UUID and tenant UUID of the new "
                                                + "monitored network.", required = true, content = @Content(mediaType = "application/json"))
                                        @Valid CreateDot11MonitoredNetworkRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!authenticatedUser.isSuperAdministrator()) {
            if (authenticatedUser.isOrganizationAdministrator()) {
                // Org admin.
                if (!authenticatedUser.getOrganizationId().equals(req.organizationId())) {
                    return Response.status(Response.Status.UNAUTHORIZED).build();
                }
            } else {
                // Tenant user.
                if (!authenticatedUser.getOrganizationId().equals(req.organizationId())
                        || !authenticatedUser.getTenantId().equals(req.tenantId())) {
                    return Response.status(Response.Status.UNAUTHORIZED).build();
                }
            }
        }

        nzyme.getDot11().createMonitoredSSID(
                req.ssid(),
                req.organizationId(),
                req.tenantId()
        );

        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}")
    @Operation(operationId = "deleteMonitoredNetwork", summary = "Delete a monitored network",
            description = "Deletes a monitored network together with all of its expected BSSIDs, channels, security "
                    + "suites and known clients. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Monitored network deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response delete(@Parameter(hidden = true) @Context SecurityContext sc, @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID uuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> result = nzyme.getDot11().findMonitoredSSID(uuid);

        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        MonitoredSSID ssid = result.get();

        if (!entityAccessible(authenticatedUser, ssid)){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().deleteMonitoredSSID(ssid.id());

        return Response.ok().build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/bssids")
    @Operation(operationId = "createMonitoredNetworkBssid", summary = "Add an expected BSSID to a monitored network",
            description = "Adds a BSSID, the MAC address of an access point, that is expected to advertise the monitored "
                    + "network. Nzyme alerts when any other BSSID advertises the SSID of this network. Add the BSSIDs "
                    + "of all of your own access points. Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "201", description = "BSSID added.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The BSSID is not a valid MAC address or is already monitored for this network.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response createMonitoredBSSID(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID ssidUUID,
                                         @RequestBody(description = "BSSID to add.", required = true, content = @Content(mediaType = "application/json"))
                                         @Valid CreateDot11MonitoredBSSIDRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUUID);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!Tools.isValidMacAddress(req.bssid())) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        List<String> existingBSSIDs = Lists.newArrayList();
        for (MonitoredBSSID monitored : nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(ssid.get().id())) {
            existingBSSIDs.add(monitored.bssid());
        }

        if (existingBSSIDs.contains(req.bssid().toUpperCase())) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        nzyme.getDot11().createMonitoredBSSID(ssid.get().id(), req.bssid());

        nzyme.getDot11().bumpMonitoredSSIDUpdatedAt(ssid.get().id());

        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{ssid_uuid}/bssids/show/{bssid_uuid}")
    @Operation(operationId = "deleteMonitoredNetworkBssid", summary = "Remove an expected BSSID from a monitored network",
            description = "Removes the BSSID and all fingerprints monitored for it. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "BSSID removed.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network or BSSID not found, or not accessible by the calling user.", content = @Content)
    public Response deleteMonitoredBSSID(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @Parameter(description = "Monitored network UUID.") @PathParam("ssid_uuid") UUID ssidUUID,
                                         @Parameter(description = "Monitored BSSID UUID.") @PathParam("bssid_uuid") UUID bssidUUID) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUUID);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Long> bssidId = nzyme.getDot11().findMonitoredBSSIDId(ssid.get().id(), bssidUUID);

        if (bssidId.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().deleteMonitoredBSSID(bssidId.get());

        nzyme.getDot11().bumpMonitoredSSIDUpdatedAt(ssid.get().id());

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{ssid_uuid}/bssids")
    @Operation(operationId = "deleteMonitoredNetworkBssids", summary = "Remove all expected BSSIDs from a monitored network",
            description = "Removes every BSSID and all of their fingerprints from the monitored network. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "BSSIDs removed.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response deleteAllMonitoredBSSIDs(@Parameter(hidden = true) @Context SecurityContext sc, @Parameter(description = "Monitored network UUID.") @PathParam("ssid_uuid") UUID ssidUUID) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUUID);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().deleteAllMonitoredBSSIDs(ssid.get().id());
        nzyme.getDot11().bumpMonitoredSSIDUpdatedAt(ssid.get().id());

        return Response.ok().build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{ssid_uuid}/bssids/show/{bssid_uuid}/fingerprints")
    @Operation(operationId = "createMonitoredNetworkBssidFingerprint", summary = "Add an expected fingerprint to a BSSID",
            description = "Adds a fingerprint that the BSSID is expected to produce. A fingerprint is a 64 character "
                    + "hash that Nzyme calculates from the tagged parameters of the beacon and probe response frames "
                    + "of an access point. List every fingerprint an expected BSSID produces, because Nzyme alerts on "
                    + "any fingerprint that is not listed. Requires the dot11_monitoring_manage feature permission.",
            externalDocs = @ExternalDocumentation(description = "WiFi fingerprinting in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-fingerprinting"))
    @ApiResponse(responseCode = "201", description = "Fingerprint added.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The fingerprint is not 64 characters long or is already monitored for this BSSID.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network or BSSID not found, or not accessible by the calling user.", content = @Content)
    public Response createMonitoredBSSIDFingerprint(@Parameter(hidden = true) @Context SecurityContext sc,
                                                    @Parameter(description = "Monitored network UUID.") @PathParam("ssid_uuid") UUID ssidUUID,
                                                    @Parameter(description = "Monitored BSSID UUID.") @PathParam("bssid_uuid") UUID bssidUUID,
                                                    @RequestBody(description = "Fingerprint to add.", required = true, content = @Content(mediaType = "application/json"))
                                                    @Valid CreateDot11MonitoredBSSIDFingerprintRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUUID);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Long> bssidId = nzyme.getDot11().findMonitoredBSSIDId(ssid.get().id(), bssidUUID);

        if (bssidId.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (req.fingerprint().length() != 64) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        List<String> existingFingerprints = Lists.newArrayList();
        for (MonitoredFingerprint fp : nzyme.getDot11().findMonitoredFingerprintsOfMonitoredBSSID(bssidId.get())) {
            existingFingerprints.add(fp.fingerprint());
        }

        if (existingFingerprints.contains(req.fingerprint())) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        nzyme.getDot11().createdMonitoredBSSIDFingerprint(bssidId.get(), req.fingerprint());

        nzyme.getDot11().bumpMonitoredSSIDUpdatedAt(ssid.get().id());

        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{ssid_uuid}/bssids/show/{bssid_uuid}/fingerprints/show/{fingerprint_uuid}")
    @Operation(operationId = "deleteMonitoredNetworkBssidFingerprint", summary = "Remove an expected fingerprint from a BSSID",
            description = "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Fingerprint removed.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network or BSSID not found, or not accessible by the calling user.", content = @Content)
    public Response deleteMonitoredBSSIDFingerprint(@Parameter(hidden = true) @Context SecurityContext sc,
                                                    @Parameter(description = "Monitored network UUID.") @PathParam("ssid_uuid") UUID ssidUUID,
                                                    @Parameter(description = "Monitored BSSID UUID.") @PathParam("bssid_uuid") UUID bssidUUID,
                                                    @Parameter(description = "Monitored fingerprint UUID.") @PathParam("fingerprint_uuid") UUID fingerprintUUID) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUUID);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Long> bssidId = nzyme.getDot11().findMonitoredBSSIDId(ssid.get().id(), bssidUUID);

        if (bssidId.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().deleteMonitoredBSSIDFingerprint(bssidId.get(), fingerprintUUID);

        nzyme.getDot11().bumpMonitoredSSIDUpdatedAt(ssid.get().id());

        return Response.ok().build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/channels")
    @Operation(operationId = "createMonitoredNetworkChannel", summary = "Add an expected channel to a monitored network",
            description = "Adds a frequency, in MHz, on which the monitored network is expected to operate. Nzyme "
                    + "alerts when the network is advertised on a channel that is not listed. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "201", description = "Channel added.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The frequency does not map to a known WiFi channel or is already monitored for this network.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response createMonitoredSSIDChannel(@Parameter(hidden = true) @Context SecurityContext sc,
                                               @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID ssidUUID,
                                               @RequestBody(description = "Frequency in MHz to add.", required = true, content = @Content(mediaType = "application/json"))
                                               @Valid CreateDot11MonitoredChannelRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUUID);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (Dot11.frequencyToChannel((int) req.frequency()) == -1) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        List<Long> existingChannels = Lists.newArrayList();
        for (MonitoredChannel channel : nzyme.getDot11().findMonitoredChannelsOfMonitoredNetwork(ssid.get().id())) {
            existingChannels.add(channel.frequency());
        }
        if (existingChannels.contains(req.frequency())) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        nzyme.getDot11().createMonitoredChannel(ssid.get().id(), req.frequency());

        nzyme.getDot11().bumpMonitoredSSIDUpdatedAt(ssid.get().id());

        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{ssid_uuid}/channels/show/{channel_uuid}")
    @Operation(operationId = "deleteMonitoredNetworkChannel", summary = "Remove an expected channel from a monitored network",
            description = "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Channel removed.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response deleteMonitoredSSIDChannel(@Parameter(hidden = true) @Context SecurityContext sc,
                                               @Parameter(description = "Monitored network UUID.") @PathParam("ssid_uuid") UUID ssidUUID,
                                               @Parameter(description = "Monitored channel UUID.") @PathParam("channel_uuid") UUID channelUUID) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUUID);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().deleteMonitoredChannel(ssid.get().id(), channelUUID);

        nzyme.getDot11().bumpMonitoredSSIDUpdatedAt(ssid.get().id());

        return Response.ok().build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/securitysuites")
    @Operation(operationId = "createMonitoredNetworkSecuritySuite", summary = "Add an expected security suite to a monitored network",
            description = "Adds a security suite identifier, for example WPA2-PSK-CCMP/CCMP or NONE, that the monitored "
                    + "network is expected to use. Nzyme alerts when the network is advertised with a suite that is "
                    + "not listed. Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "201", description = "Security suite added.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The security suite is already monitored for this network.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user, or the "
            + "security suite identifier has an invalid format.", content = @Content)
    public Response createMonitoredSSIDSecuritySuite(@Parameter(hidden = true) @Context SecurityContext sc,
                                                     @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID ssidUUID,
                                                     @RequestBody(description = "Security suite identifier to add.", required = true, content = @Content(mediaType = "application/json"))
                                                     @Valid CreateDot11MonitoredSecuritySuiteRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUUID);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // TODO we might want to tighten this down in the future.
        if (!req.suite().equals("NONE") && (!req.suite().contains("-") || !req.suite().contains("/"))) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<String> existingSuites = Lists.newArrayList();
        for (MonitoredSecuritySuite suite : nzyme.getDot11().findMonitoredSecuritySuitesOfMonitoredNetwork(ssid.get().id())) {
            existingSuites.add(suite.securitySuite());
        }
        if (existingSuites.contains(req.suite())) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        nzyme.getDot11().createMonitoredSecuritySuite(ssid.get().id(), req.suite());

        nzyme.getDot11().bumpMonitoredSSIDUpdatedAt(ssid.get().id());

        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{ssid_uuid}/securitysuites/show/{suite_uuid}")
    @Operation(operationId = "deleteMonitoredNetworkSecuritySuite", summary = "Remove an expected security suite from a monitored network",
            description = "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Security suite removed.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response deleteMonitoredSSIDSecuritySuite(@Parameter(hidden = true) @Context SecurityContext sc,
                                                     @Parameter(description = "Monitored network UUID.") @PathParam("ssid_uuid") UUID ssidUUID,
                                                     @Parameter(description = "Monitored security suite UUID.") @PathParam("suite_uuid") UUID suiteUUID) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUUID);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().deleteMonitoredSecuritySuite(ssid.get().id(), suiteUUID);

        nzyme.getDot11().bumpMonitoredSSIDUpdatedAt(ssid.get().id());

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/enable")
    @Operation(operationId = "enableMonitoredNetwork", summary = "Enable a monitored network",
            description = "Enables monitoring of the network. Only enabled networks are evaluated by the detection "
                    + "engine. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Monitored network enabled.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response enableMonitoredNetwork(@Parameter(hidden = true) @Context SecurityContext sc, @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID ssidUUID) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUUID);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().setMonitoredSSIDEnabledState(ssid.get().id(), true);

        nzyme.getDot11().bumpMonitoredSSIDUpdatedAt(ssid.get().id());

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/disable")
    @Operation(operationId = "disableMonitoredNetwork", summary = "Disable a monitored network",
            description = "Disables monitoring of the network. The configuration is kept and can be enabled again later. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Monitored network disabled.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response disableMonitoredNetwork(@Parameter(hidden = true) @Context SecurityContext sc, @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID ssidUUID) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUUID);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().setMonitoredSSIDEnabledState(ssid.get().id(), false);

        nzyme.getDot11().bumpMonitoredSSIDUpdatedAt(ssid.get().id());

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/alertenabledstatus/{alert}/set/{status}")
    @Operation(operationId = "updateMonitoredNetworkAlertStatus", summary = "Enable or disable a monitor of a monitored network",
            description = "Switches a single monitor type of the network on or off. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Monitor status updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user, or the "
            + "monitor type is unknown.", content = @Content)
    public Response setAlertEnabledStatus(@Parameter(hidden = true) @Context SecurityContext sc,
                                          @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID ssidUUID,
                                          @Parameter(description = "Monitor type. One of UNEXPECTED_BSSID, UNEXPECTED_CHANNEL, "
                                                  + "UNEXPECTED_SECURITY_SUITES, UNEXPECTED_FINGERPRINT, UNEXPECTED_SIGNAL_TRACKS, "
                                                  + "DISCO_MONITOR, SIMILAR_SSIDS, RESTRICTED_SSID_SUBSTRINGS, CLIENT_MONITORING, "
                                                  + "CLIENT_EVENTING. Case insensitive.") @PathParam("alert") @NotEmpty String alert,
                                          @Parameter(description = "New status: true to enable, false to disable.") @PathParam("status") boolean status) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUUID);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Dot11.MonitorActiveStatusTypeColumn alertColumn;
        try {
            alertColumn = Dot11.MonitorActiveStatusTypeColumn.valueOf(alert.toUpperCase());
        } catch(IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().setMonitorAlertStatus(ssid.get().id(), alertColumn, status);

        nzyme.getDot11().bumpMonitoredSSIDUpdatedAt(ssid.get().id());

        return Response.ok().build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/import/data")
    @Operation(operationId = "findMonitoredNetworkImportData", summary = "Get observed data of a monitored network for import",
            description = "Returns the BSSIDs, fingerprints, channels and security suites that taps the calling user can "
                    + "access observed for the SSID of the monitored network in the last 24 hours, each flagged with "
                    + "whether it is already part of the monitored configuration. Use it to prefill an import. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Import data found.",
            content = @Content(schema = @Schema(implementation = MonitoredNetworkImportDataResponse.class)))
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response getImportData(@Parameter(hidden = true) @Context SecurityContext sc, @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID uuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> allAccessibleTapUUIDs = parseAndValidateTapIds(authenticatedUser, nzyme, "*");

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(uuid);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<MonitoredBSSID> monitoredBSSIDs = nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(ssid.get().id());
        List<BSSIDImportDataResponse> bssidsResponse = Lists.newArrayList();
        for (String bssid : nzyme.getDot11().findBSSIDsAdvertisingSSID(ssid.get().ssid(), allAccessibleTapUUIDs)) {
            MonitoredBSSID monitoredBSSID = null;
            List<String> monitoredFingerprints = Lists.newArrayList();
            for (MonitoredBSSID b : monitoredBSSIDs) {
                if (b.bssid().equals(bssid)) {
                    monitoredBSSID = b;
                    monitoredFingerprints = nzyme.getDot11().findMonitoredFingerprintsOfMonitoredBSSID(b.id())
                            .stream()
                            .map(MonitoredFingerprint::fingerprint)
                            .collect(Collectors.toList());
                    break;
                }
            }

            List<FingerprintImportDataResponse> fingerprints = Lists.newArrayList();
            for (String fingerprint : nzyme.getDot11()
                    .findFingerprintsOfBSSID(bssid, TimeRangeFactory.oneDay(), allAccessibleTapUUIDs)) {
                if (fingerprint != null) {
                    fingerprints.add(FingerprintImportDataResponse.create(
                            fingerprint,
                            monitoredFingerprints.contains(fingerprint)
                    ));
                }
            }

            Optional<MacAddressContextEntry> ctx = nzyme.getContextService().findMacAddressContext(
                    bssid,
                    authenticatedUser.getOrganizationId(),
                    authenticatedUser.getTenantId()
            );

            bssidsResponse.add(BSSIDImportDataResponse.create(
                    Dot11MacAddressResponse.create(
                            bssid,
                            nzyme.getOuiService().lookup(bssid).orElse(null),
                            null,
                            ctx.map(c -> Dot11MacAddressContextResponse.create(
                                    c.name(), c.description(), c.notes())
                            ).orElse(null)
                    ),
                    fingerprints,
                    monitoredBSSID != null
            ));
        }

        List<String> monitoredSuites = nzyme.getDot11().findMonitoredSecuritySuitesOfMonitoredNetwork(ssid.get().id())
                .stream().map(MonitoredSecuritySuite::securitySuite).collect(Collectors.toList());
        List<SecuritySuiteImportDataResponse> securitySuitesResponse = Lists.newArrayList();
        for (Dot11SecuritySuiteJson ss : nzyme.getDot11().findSecuritySuitesOfSSID(ssid.get().ssid(), allAccessibleTapUUIDs)) {
            if (ss != null) {
                String ssId = Dot11.securitySuitesToIdentifier(ss);
                securitySuitesResponse.add(SecuritySuiteImportDataResponse.create(
                        ssId, monitoredSuites.contains(ssId)
                ));
            }
        }

        List<Long> monitoredChannels = nzyme.getDot11().findMonitoredChannelsOfMonitoredNetwork(ssid.get().id())
                .stream().map(MonitoredChannel::frequency).collect(Collectors.toList());
        List<ChannelImportDataResponse> channelsResponse = Lists.newArrayList();
        for (Long f : nzyme.getDot11().findChannelsOfSSID(ssid.get().ssid(), allAccessibleTapUUIDs)) {
            if (f != null) {
                channelsResponse.add(ChannelImportDataResponse.create(f, monitoredChannels.contains(f)));
            }
        }

        return Response.ok(MonitoredNetworkImportDataResponse.create(
                bssidsResponse, channelsResponse, securitySuitesResponse
        )).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/import/data")
    @Operation(operationId = "importMonitoredNetworkData", summary = "Import observed data into a monitored network",
            description = "Adds the passed BSSIDs with their fingerprints, channels and security suites to the monitored "
                    + "network. Entries that are already monitored are skipped, nothing is removed. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "201", description = "Data imported.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response writeImportData(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID uuid,
                                    @RequestBody(description = "BSSIDs with fingerprints, channel frequencies and security "
                                            + "suite identifiers to add.", required = true, content = @Content(mediaType = "application/json"))
                                    @Valid ImportMonitoredNetworkDataRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(uuid);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Load currently monitored data.
        List<String> currentBSSIDs = nzyme.getDot11()
                .findMonitoredBSSIDsOfMonitoredNetwork(ssid.get().id())
                .stream()
                .map(MonitoredBSSID::bssid)
                .collect(Collectors.toList());

        List<Long> currentChannels = nzyme.getDot11()
                .findMonitoredChannelsOfMonitoredNetwork(ssid.get().id())
                .stream()
                .map(MonitoredChannel::frequency)
                .collect(Collectors.toList());

        List<String> currentSecSuites = nzyme.getDot11()
                .findMonitoredSecuritySuitesOfMonitoredNetwork(ssid.get().id())
                .stream()
                .map(MonitoredSecuritySuite::securitySuite)
                .collect(Collectors.toList());

        // BSSIDS.
        for (ImportMonitoredNetworkDataBSSIDRequest bssid : req.bssids()) {
            if (currentBSSIDs.contains(bssid.bssid())) {
                // Existing BSSID. Check if we need to add fingeprints.
                @SuppressWarnings("OptionalGetWithoutIsPresent")
                long bssidId = nzyme.getDot11().findMonitoredBSSIDId(ssid.get().id(), bssid.bssid()).get();

                List<String> existingFingerprints = nzyme.getDot11().findMonitoredFingerprintsOfMonitoredBSSID(bssidId)
                        .stream()
                        .map(MonitoredFingerprint::fingerprint)
                        .collect(Collectors.toList());

                for (String fingerprint : bssid.fingerprints()) {
                    if (!existingFingerprints.contains(fingerprint)) {
                        nzyme.getDot11().createdMonitoredBSSIDFingerprint(bssidId, fingerprint);
                    }
                }
            } else {
                // New BSSID.
                long bssidId = nzyme.getDot11().createMonitoredBSSID(ssid.get().id(), bssid.bssid());

                for (String fingerprint : bssid.fingerprints()) {
                    nzyme.getDot11().createdMonitoredBSSIDFingerprint(bssidId, fingerprint);
                }
            }
        }

        // Channels.
        for (Long channel : req.channels()) {
            if (!currentChannels.contains(channel)) {
                nzyme.getDot11().createMonitoredChannel(ssid.get().id(), channel);
            }
        }

        // Security Suites.
        for (String securitySuite : req.securitySuites()) {
            if (!currentSecSuites.contains(securitySuite)) {
                nzyme.getDot11().createMonitoredSecuritySuite(ssid.get().id(), securitySuite);
            }
        }

        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/configuration/similarssids/simulate")
    @Operation(operationId = "simulateMonitoredNetworkSimilarSsids", summary = "Simulate the similar SSID monitor",
            description = "Compares the SSID of the monitored network with all SSIDs seen in the last 15 minutes by taps "
                    + "the calling user can access and returns the similarity of each in percent, together with "
                    + "whether it would trigger an alert at the given threshold. SSIDs that are themselves monitored "
                    + "networks of the tenant never trigger an alert. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Similarities calculated.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SSIDSimilarityResponse.class))))
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response simulateSimilarSSIDs(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID uuid,
                                         @Parameter(description = "Similarity threshold in percent, 0 to 100.") @QueryParam("threshold") int threshold) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(uuid);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<SSIDSimilarityResponse> similarities = Lists.newArrayList();
        List<String> monitoredSSIDNames = nzyme.getDot11()
                .findAllMonitoredSSIDs(authenticatedUser.getOrganizationId(), authenticatedUser.getTenantId())
                .stream().map(MonitoredSSID::ssid)
                .collect(Collectors.toList());

        JaroWinkler jaroWinkler = new JaroWinkler();
        for (String ssidName : nzyme.getDot11()
                .findAllRecentSSIDNames(nzyme.getTapManager().allTapUUIDsAccessibleByUser(authenticatedUser), 15)) {
            double similarity = jaroWinkler.similarity(ssid.get().ssid(), ssidName) * 100.0;
            boolean monitored = monitoredSSIDNames.contains(ssidName);
            boolean alerted = !monitored && similarity > threshold;

            similarities.add(SSIDSimilarityResponse.create(ssidName, similarity, monitored, alerted));
        }

        return Response.ok(similarities).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/configuration/similarssids")
    @Operation(operationId = "updateMonitoredNetworkSimilarSsidConfiguration", summary = "Update similar SSID monitor threshold",
            description = "Sets the similarity threshold in percent above which an SSID is considered similar looking. "
                    + "Nzyme alerts when another SSID in range is more similar to the SSID of this monitored network "
                    + "than the threshold allows. Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response setSimilarSSIDConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                                @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID uuid,
                                                @RequestBody(description = "Threshold in percent, 0 to 100.", required = true, content = @Content(mediaType = "application/json"))
                                                @Valid UpdateSimilarSSIDNetworkMonitorConfiguration req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(uuid);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().setSimilarSSIDMonitorConfiguration(ssid.get().id(), (int) req.threshold());

        return Response.ok().build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/configuration/restricted-ssid-substrings")
    @Operation(operationId = "createMonitoredNetworkRestrictedSsidSubstring", summary = "Add a restricted SSID substring",
            description = "Adds a substring that other SSIDs must not contain. Nzyme alerts when an SSID that is not "
                    + "the monitored network contains it. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "201", description = "Restricted SSID substring added.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response addRestrictedSSIDSubstring(@Parameter(hidden = true) @Context SecurityContext sc,
                                               @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID uuid,
                                               @RequestBody(description = "Substring to restrict.", required = true, content = @Content(mediaType = "application/json"))
                                               @Valid CreateDot11MonitoredNetworkRestrictedSSIDSubstringRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(uuid);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().createRestrictedSSIDSubstring(ssid.get().id(), req.substring());

        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/configuration/restricted-ssid-substrings/show/{substring_uuid}")
    @Operation(operationId = "deleteMonitoredNetworkRestrictedSsidSubstring", summary = "Remove a restricted SSID substring",
            description = "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Restricted SSID substring removed.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response deleteRestrictedSSIDSubstring(@Parameter(hidden = true) @Context SecurityContext sc,
                                                  @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID uuid,
                                                  @Parameter(description = "Restricted SSID substring UUID.") @PathParam("substring_uuid") UUID substringUuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(uuid);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().deleteRestrictedSSIDSubstring(ssid.get().id(), substringUuid);

        return Response.ok().build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/configuration/clients")
    @Operation(operationId = "findMonitoredNetworkClientMonitoringConfiguration", summary = "Get client monitoring configuration",
            description = "Returns whether client monitoring and client event generation are enabled for the monitored "
                    + "network, in the generic configuration entry format used by the web interface. Both are disabled "
                    + "by default. Requires the dot11_monitoring_manage feature permission.",
            externalDocs = @ExternalDocumentation(description = "Client monitoring in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-client-monitoring"))
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = ClientMonitoringConfigurationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response getClientMonitoringConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                                     @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID uuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(uuid);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        /*
         * We are not using the usual flow of registry keys here because the config is attached to
         * the monitored network, not in a registry.
         */
        ClientMonitoringConfigurationResponse configuration = ClientMonitoringConfigurationResponse.create(
                ConfigurationEntryResponse.create(
                        "monitoring_is_enabled",
                        "Is enabled",
                        ssid.get().enabledClientMonitoring() ? "true" : false,
                        ConfigurationEntryValueType.BOOLEAN,
                        "false",
                        false,
                        new ArrayList<>() {{
                            add(ConfigurationEntryConstraint.createSimpleBooleanConstraint());
                        }},
                        "wifi-client-monitoring"
                ),
                ConfigurationEntryResponse.create(
                        "eventing_is_enabled",
                        "Event generation is enabled",
                        ssid.get().enabledClientEventing() ? "true" : false,
                        ConfigurationEntryValueType.BOOLEAN,
                        "false",
                        false,
                        new ArrayList<>() {{
                            add(ConfigurationEntryConstraint.createSimpleBooleanConstraint());
                        }},
                        "wifi-client-monitoring"
                )
        );

        return Response.ok(configuration).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/configuration/clients")
    @Operation(operationId = "updateMonitoredNetworkClientMonitoringConfiguration", summary = "Update client monitoring configuration",
            description = "Accepts the keys monitoring_is_enabled and eventing_is_enabled with boolean values in the "
                    + "change map. Keys that are not passed stay unchanged. Nzyme only collects known clients while "
                    + "monitoring is enabled and only creates alerts while event generation is enabled, so you can "
                    + "use a training period to approve the clients in range first. "
                    + "Requires the dot11_monitoring_manage feature permission.",
            externalDocs = @ExternalDocumentation(description = "Client monitoring in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-client-monitoring"))
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response setClientMonitoringConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                                     @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID uuid,
                                                     @RequestBody(description = "Map of configuration keys to new values.", required = true, content = @Content(mediaType = "application/json"))
                                                     @Valid UpdateConfigurationRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(uuid);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (req.change().containsKey("monitoring_is_enabled")) {
            boolean status = (boolean) req.change().get("monitoring_is_enabled");

            nzyme.getDot11().setMonitorAlertStatus(
                    ssid.get().id(),
                    Dot11.MonitorActiveStatusTypeColumn.CLIENT_MONITORING,
                    status
            );
        }

        if (req.change().containsKey("eventing_is_enabled")) {
            boolean status = (boolean) req.change().get("eventing_is_enabled");

            nzyme.getDot11().setMonitorAlertStatus(
                    ssid.get().id(),
                    Dot11.MonitorActiveStatusTypeColumn.CLIENT_EVENTING,
                    status
            );
        }

        nzyme.getDot11().bumpMonitoredSSIDUpdatedAt(ssid.get().id());

        return Response.ok().build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/clients")
    @Operation(operationId = "findMonitoredNetworkKnownClients", summary = "List known clients of a monitored network",
            description = "Returns the clients that were seen connected to an expected BSSID of the monitored network, "
                    + "with their approval and ignore status. Clients that have not been seen for 30 days are deleted "
                    + "automatically. The page size is limited to 250. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Known clients found.",
            content = @Content(schema = @Schema(implementation = KnownClientsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Requested page size is larger than 250.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response findAllKnownClients(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID uuid,
                                        @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                        @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(uuid);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long total = nzyme.getDot11().countAllKnownClients(ssid.get().id());
        List<KnownClientDetailsResponse> clients = Lists.newArrayList();
        for (Dot11KnownClient client : nzyme.getDot11().findAllKnownClients(ssid.get().id(), limit, offset)) {
            Optional<MacAddressContextEntry> clientContext = nzyme.getContextService().findMacAddressContext(
                    client.mac(),
                    authenticatedUser.getOrganizationId(),
                    authenticatedUser.getTenantId()
            );

            clients.add(KnownClientDetailsResponse.create(
                    client.uuid(),
                    ssid.get().uuid(),
                    Dot11MacAddressResponse.create(
                            client.mac(),
                            nzyme.getOuiService().lookup(client.mac()).orElse(null),
                            Tools.macAddressIsRandomized(client.mac()),
                            clientContext.map(macAddressContextEntry ->
                                            Dot11MacAddressContextResponse.create(
                                                    macAddressContextEntry.name(),
                                                    macAddressContextEntry.description(),
                                                    macAddressContextEntry.notes()
                                            ))
                                    .orElse(null)
                    ),
                    client.isApproved(),
                    client.isIgnored(),
                    client.firstSeen(),
                    client.lastSeen()
            ));
        }

        return Response.ok(KnownClientsListResponse.create(total, clients)).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/clients/show/{client_uuid}/approve")
    @Operation(operationId = "approveMonitoredNetworkKnownClient", summary = "Approve a known client",
            description = "Marks the client as approved. Approved clients do not trigger new unapproved client alerts "
                    + "and their existing alerts resolve within a few minutes. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Client approved.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network or client not found, or not accessible by the calling user.", content = @Content)
    public Response approveKnownClient(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID ssidUuid,
                                       @Parameter(description = "Known client UUID.") @PathParam("client_uuid") UUID clientUuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUuid);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Dot11KnownClient> client = nzyme.getDot11().findKnownClientByUuid(clientUuid, ssid.get().id());

        if (client.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().changeStatusOfKnownClient(client.get().id(), true);

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/clients/show/{client_uuid}/revoke")
    @Operation(operationId = "revokeMonitoredNetworkKnownClient", summary = "Revoke approval of a known client",
            description = "Marks the client as not approved again. It triggers alerts again until it is approved, "
                    + "ignored or deleted. Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Client approval revoked.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network or client not found, or not accessible by the calling user.", content = @Content)
    public Response revokeKnownClient(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID ssidUuid,
                                      @Parameter(description = "Known client UUID.") @PathParam("client_uuid") UUID clientUuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUuid);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Dot11KnownClient> client = nzyme.getDot11().findKnownClientByUuid(clientUuid, ssid.get().id());

        if (client.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().changeStatusOfKnownClient(client.get().id(), false);

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/clients/show/{client_uuid}/ignore")
    @Operation(operationId = "ignoreMonitoredNetworkKnownClient", summary = "Ignore a known client",
            description = "Marks the client as ignored. It stays in the list of known clients and stops triggering "
                    + "alerts, but it is not marked as approved. Existing alerts resolve within a few minutes. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Client ignored.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network or client not found, or not accessible by the calling user.", content = @Content)
    public Response ignoreKnownClient(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID ssidUuid,
                                      @Parameter(description = "Known client UUID.") @PathParam("client_uuid") UUID clientUuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUuid);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Dot11KnownClient> client = nzyme.getDot11().findKnownClientByUuid(clientUuid, ssid.get().id());

        if (client.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().changeIgnoreStatusOfKnownClient(client.get().id(), true);

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/clients/show/{client_uuid}/unignore")
    @Operation(operationId = "unignoreMonitoredNetworkKnownClient", summary = "Stop ignoring a known client",
            description = "Removes the ignored flag from the client. It triggers alerts again unless it is approved. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Client no longer ignored.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network or client not found, or not accessible by the calling user.", content = @Content)
    public Response unignoreKnownClient(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID ssidUuid,
                                        @Parameter(description = "Known client UUID.") @PathParam("client_uuid") UUID clientUuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUuid);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Dot11KnownClient> client = nzyme.getDot11().findKnownClientByUuid(clientUuid, ssid.get().id());

        if (client.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().changeIgnoreStatusOfKnownClient(client.get().id(), false);

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/clients/show/{client_uuid}")
    @Operation(operationId = "deleteMonitoredNetworkKnownClient", summary = "Delete a known client",
            description = "Deletes the client record and resolves its alerts within a few minutes. The client "
                    + "reappears as a new, unapproved client the next time it connects to the network. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Client deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network or client not found, or not accessible by the calling user.", content = @Content)
    public Response deleteKnownClient(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID ssidUuid,
                                      @Parameter(description = "Known client UUID.") @PathParam("client_uuid") UUID clientUuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUuid);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Dot11KnownClient> client = nzyme.getDot11().findKnownClientByUuid(clientUuid, ssid.get().id());

        if (client.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().deleteKnownClient(client.get().id());

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/ssids/show/{uuid}/clients/")
    @Operation(operationId = "deleteMonitoredNetworkKnownClients", summary = "Delete all known clients of a monitored network",
            description = "Deletes every known client record of the network, including approved and ignored clients. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Known clients deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored network not found or not accessible by the calling user.", content = @Content)
    public Response deleteAllKnownClients(@Parameter(hidden = true) @Context SecurityContext sc, @Parameter(description = "Monitored network UUID.") @PathParam("uuid") UUID ssidUuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredSSID> ssid = nzyme.getDot11().findMonitoredSSID(ssidUuid);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, ssid.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().deleteKnownClientsOfMonitoredNetwork(ssid.get().id());

        return Response.ok().build();
    }

}
