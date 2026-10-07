package app.nzyme.core.rest.resources.dot11;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.dot11.db.monitoring.probereq.MonitoredProbeRequestEntry;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.requests.CreateMonitoredProbeRequestRequest;
import app.nzyme.core.rest.requests.UpdateMonitoredProbeRequestRequest;
import app.nzyme.core.rest.responses.dot11.monitoring.probereq.MonitoredProbeRequestDetailsResponse;
import app.nzyme.core.rest.responses.dot11.monitoring.probereq.MonitoredProbeRequestListResponse;
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
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Path("/api/dot11/monitoring/proberequests")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Monitoring", description = "A monitored network describes the expected state of one of your own WiFi "
        + "networks so that Nzyme can alert on any deviation. This group also covers SSID monitoring, probe request "
        + "monitoring and the known clients of a monitored network.",
        externalDocs = @ExternalDocumentation(description = "Network monitoring in the Nzyme documentation",
                url = "https://go.nzyme.org/wifi-network-monitoring"))
public class Dot11MonitoredProbeRequestResource extends UserAuthenticatedResource {

    private static final Logger LOG = LogManager.getLogger(Dot11MonitoredProbeRequestResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Operation(operationId = "findMonitoredProbeRequests", summary = "List monitored probe requests of a tenant",
            description = "Returns the SSIDs that Nzyme watches for in the probe requests of a tenant. A probe "
                    + "request is a frame a WiFi device sends to look for a network it knows. The page size is "
                    + "limited to 250. Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Monitored probe requests found.",
            content = @Content(schema = @Schema(implementation = MonitoredProbeRequestListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Requested page size is larger than 250.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_uuid") @NotNull UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_uuid") @NotNull UUID tenantId) {
        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long total = nzyme.getDot11().countAllMonitoredProbeRequests(organizationId, tenantId);

        List<MonitoredProbeRequestDetailsResponse> ssids = Lists.newArrayList();
        for (MonitoredProbeRequestEntry ssid : nzyme.getDot11()
                .findAllMonitoredProbeRequests(organizationId, tenantId, limit, offset)) {
            ssids.add(MonitoredProbeRequestDetailsResponse.create(
                    ssid.uuid(),
                    ssid.organizationId(),
                    ssid.tenantId(),
                    ssid.ssid(),
                    ssid.notes(),
                    ssid.updatedAt(),
                    ssid.createdAt()
            ));
        }

        return Response.ok(MonitoredProbeRequestListResponse.create(total, ssids)).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/show/{uuid}")
    @Operation(operationId = "findMonitoredProbeRequest", summary = "Get monitored probe request details",
            description = "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Monitored probe request found.",
            content = @Content(schema = @Schema(implementation = MonitoredProbeRequestDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Monitored probe request not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Monitored probe request UUID.") @PathParam("uuid") UUID uuid,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_uuid") @NotNull UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_uuid") @NotNull UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<MonitoredProbeRequestEntry> ssid = nzyme.getDot11()
                .findMonitoredProbeRequest(uuid, organizationId, tenantId);

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        MonitoredProbeRequestDetailsResponse response = MonitoredProbeRequestDetailsResponse.create(
                ssid.get().uuid(),
                ssid.get().organizationId(),
                ssid.get().tenantId(),
                ssid.get().ssid(),
                ssid.get().notes(),
                ssid.get().updatedAt(),
                ssid.get().createdAt()
        );

        return Response.ok(response).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Operation(operationId = "createMonitoredProbeRequest", summary = "Create a monitored probe request",
            description = "Adds an SSID to watch for in the probe requests of the organization and tenant passed in "
                    + "the body. Any string is accepted. Nzyme alerts as soon as a probe request looking for this "
                    + "SSID is recorded, and the alert resolves automatically after several minutes without such a "
                    + "frame. Requires the dot11_monitoring_manage feature permission.",
            externalDocs = @ExternalDocumentation(description = "Probe request monitoring in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-probereq-monitoring"))
    @ApiResponse(responseCode = "201", description = "Monitored probe request created.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response create(@Parameter(hidden = true) @Context SecurityContext sc,
                           @RequestBody(description = "Organization UUID, tenant UUID, SSID and optional notes.", required = true, content = @Content(mediaType = "application/json"))
                           @Valid CreateMonitoredProbeRequestRequest req) {
        if (!passedTenantDataAccessible(sc, req.organizationId(), req.tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().createMonitoredProbeRequest(req.organizationId(), req.tenantId(), req.ssid(), req.notes());

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/show/{uuid}")
    @Operation(operationId = "updateMonitoredProbeRequest", summary = "Update a monitored probe request",
            description = "Replaces SSID and notes of the monitored probe request. The organization and tenant in the "
                    + "body must match the existing entry. "
                    + "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Monitored probe request updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored probe request not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response update(@Parameter(hidden = true) @Context SecurityContext sc,
                           @RequestBody(description = "Organization UUID, tenant UUID, new SSID and optional notes.", required = true, content = @Content(mediaType = "application/json"))
                           @Valid UpdateMonitoredProbeRequestRequest req,
                           @Parameter(description = "Monitored probe request UUID.") @PathParam("uuid") UUID uuid) {
        if (!passedTenantDataAccessible(sc, req.organizationId(), req.tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<MonitoredProbeRequestEntry> ssid = nzyme.getDot11()
                .findMonitoredProbeRequest(uuid, req.organizationId(), req.tenantId());

        if (ssid.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().updateMonitoredProbeRequest(uuid, req.organizationId(), req.tenantId(), req.ssid(), req.notes());

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/show/{uuid}")
    @Operation(operationId = "deleteMonitoredProbeRequest", summary = "Delete a monitored probe request",
            description = "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Monitored probe request deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Monitored probe request not found or not accessible by the calling user.", content = @Content)
    public Response delete(@Parameter(hidden = true) @Context SecurityContext sc, @Parameter(description = "Monitored probe request UUID.") @PathParam("uuid") UUID uuid) {
        Optional<MonitoredProbeRequestEntry> ssid = nzyme.getDot11()
                .findMonitoredProbeRequest(uuid);

        if (ssid.isEmpty() || !passedTenantDataAccessible(sc, ssid.get().organizationId(), ssid.get().tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().deleteMonitoredProbeRequest(uuid, ssid.get().organizationId(), ssid.get().tenantId());

        return Response.ok().build();
    }

}
