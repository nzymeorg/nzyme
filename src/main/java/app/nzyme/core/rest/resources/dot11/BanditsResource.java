package app.nzyme.core.rest.resources.dot11;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.dot11.db.monitoring.CustomBanditDescription;
import app.nzyme.core.dot11.bandits.Dot11BanditDescription;
import app.nzyme.core.dot11.bandits.Dot11Bandits;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.CreateBanditFingerprintRequest;
import app.nzyme.core.rest.requests.CreateCustomBanditRequest;
import app.nzyme.core.rest.requests.UpdateCustomBanditRequest;
import app.nzyme.core.rest.responses.dot11.monitoring.BuiltinBanditDetailsResponse;
import app.nzyme.core.rest.responses.dot11.monitoring.CustomBanditDetailsResponse;
import app.nzyme.core.rest.responses.dot11.monitoring.CustomBanditListResponse;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Path("/api/dot11/bandits")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Bandits", description = "A bandit is a malicious WiFi device that Nzyme alerts on when it shows up "
        + "in range. Bandits are identified by the fingerprints of the frames they send. Nzyme ships built in bandits "
        + "for common attack platforms like the WiFi Pineapple or the Flipper Zero, and you can define your own "
        + "custom bandits per tenant.",
        externalDocs = @ExternalDocumentation(description = "WiFi bandits in the Nzyme documentation",
                url = "https://go.nzyme.org/wifi-bandits"))
public class BanditsResource extends UserAuthenticatedResource {

    private static final Logger LOG = LogManager.getLogger(BanditsResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/builtin")
    @Operation(operationId = "findBuiltinBandits", summary = "List built in bandits",
            description = "Returns all bandits that ship with Nzyme, including their fingerprints. Built in "
                    + "bandits are the same for every tenant and cannot be changed. The fingerprint list is empty for "
                    + "bandits that are not identified by fingerprints. Requires the dot11_monitoring_manage feature "
                    + "permission.")
    @ApiResponse(responseCode = "200", description = "Bandits found.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = BuiltinBanditDetailsResponse.class))))
    public Response findAllBuiltIn() {
        List<BuiltinBanditDetailsResponse> bandits = Lists.newArrayList();

        for (Dot11BanditDescription bandit : Dot11Bandits.BUILT_IN) {
            bandits.add(BuiltinBanditDetailsResponse.create(
                    bandit.id(),
                    bandit.name(),
                    bandit.description(),
                    bandit.fingerprints() == null ? Collections.emptyList() : bandit.fingerprints()
            ));
        }

        return Response.ok(bandits).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/builtin/show/{id}")
    @Operation(operationId = "findBuiltinBandit", summary = "Get a built in bandit",
            description = "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Bandit found.",
            content = @Content(schema = @Schema(implementation = BuiltinBanditDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "No built in bandit with this identifier exists.", content = @Content)
    public Response findOneBuiltIn(@Parameter(description = "Built in bandit identifier.") @PathParam("id") @NotEmpty String id) {
        Dot11BanditDescription bandit = null;
        for (Dot11BanditDescription b : Dot11Bandits.BUILT_IN) {
            if (b.id().equals(id)) {
                bandit = b;
                break;
            }
        }

        if (bandit == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(BuiltinBanditDetailsResponse.create(
                bandit.id(),
                bandit.name(),
                bandit.description(),
                bandit.fingerprints() == null ? Collections.emptyList() : bandit.fingerprints()
        )).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/custom")
    @Operation(operationId = "findCustomBandits", summary = "List custom bandits of a tenant",
            description = "Returns the custom bandits of a tenant with their fingerprints. The page size cannot exceed "
                    + "250. Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Bandits found.",
            content = @Content(schema = @Schema(implementation = CustomBanditListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The requested page size is larger than 250.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not accessible by the calling user.", content = @Content)
    public Response findAllCustom(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Page size. Cannot exceed 250.") @QueryParam("limit") int limit,
                                  @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                                  @Parameter(description = "Organization UUID.") @QueryParam("organization_uuid") @NotNull UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @QueryParam("tenant_uuid") @NotNull UUID tenantId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!hasPermissions(authenticatedUser, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long total = nzyme.getDot11().countCustomBandits(organizationId, tenantId);
        List<CustomBanditDetailsResponse> bandits = Lists.newArrayList();

        for (CustomBanditDescription b : nzyme.getDot11().findAllCustomBandits(organizationId, tenantId, limit, offset)) {
            // Find fingerprints of bandit.
            List<String> fingerprints = nzyme.getDot11().findFingerprintsOfCustomBandit(b.id());

            bandits.add(CustomBanditDetailsResponse.create(
                    b.uuid(),
                    b.name(),
                    b.description(),
                    fingerprints,
                    b.createdAt(),
                    b.updatedAt()
            ));
        }

        return Response.ok(CustomBanditListResponse.create(total, bandits)).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/custom/show/{id}")
    @Operation(operationId = "findCustomBandit", summary = "Get a custom bandit",
            description = "Requires the dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Bandit found.",
            content = @Content(schema = @Schema(implementation = CustomBanditDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Bandit not found or not accessible by the calling user.", content = @Content)
    public Response findCustom(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "Custom bandit UUID.") @PathParam("id") @NotNull UUID id) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<CustomBanditDescription> bandit = nzyme.getDot11().findCustomBandit(id);

        if (bandit.isEmpty()
                || !hasPermissions(authenticatedUser, bandit.get().organizationId(), bandit.get().tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<String> fingerprints = nzyme.getDot11().findFingerprintsOfCustomBandit(bandit.get().id());

        return Response.ok(CustomBanditDetailsResponse.create(
                bandit.get().uuid(),
                bandit.get().name(),
                bandit.get().description(),
                fingerprints,
                bandit.get().createdAt(),
                bandit.get().updatedAt()
        )).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/custom")
    @Operation(operationId = "createCustomBandit", summary = "Create a custom bandit",
            description = "Creates a custom bandit for a tenant. Add fingerprints in a separate call. Custom "
                    + "bandits are evaluated exactly like built in bandits. Requires the dot11_monitoring_manage "
                    + "feature permission.")
    @ApiResponse(responseCode = "201", description = "Bandit created.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not accessible by the calling user.", content = @Content)
    public Response createCustom(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @RequestBody(description = "Organization UUID, tenant UUID, name and description of the new bandit.", required = true, content = @Content(mediaType = "application/json"))
                                 @Valid CreateCustomBanditRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!hasPermissions(authenticatedUser, req.organizationId(), req.tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().createCustomBandit(req.organizationId(), req.tenantId(), req.name(), req.description());

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/custom/show/{id}")
    @Operation(operationId = "updateCustomBandit", summary = "Update a custom bandit",
            description = "Changes name and description of a custom bandit. Fingerprints are not touched. Requires the "
                    + "dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Bandit updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Bandit not found or not accessible by the calling user.", content = @Content)
    public Response editCustom(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "Custom bandit UUID.") @PathParam("id") @NotNull UUID id,
                               @RequestBody(description = "New name and description of the bandit.", required = true, content = @Content(mediaType = "application/json"))
                               @Valid UpdateCustomBanditRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<CustomBanditDescription> bandit = nzyme.getDot11().findCustomBandit(id);

        if (bandit.isEmpty()
                || !hasPermissions(authenticatedUser, bandit.get().organizationId(), bandit.get().tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().editCustomBandit(bandit.get().id(), req.name(), req.description());
        nzyme.getDot11().bumpCustomBanditUpdatedAt(bandit.get().id());

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/custom/show/{id}")
    @Operation(operationId = "deleteCustomBandit", summary = "Delete a custom bandit",
            description = "Deletes the bandit and all of its fingerprints. Requires the dot11_monitoring_manage "
                    + "feature permission.")
    @ApiResponse(responseCode = "200", description = "Bandit deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Bandit not found or not accessible by the calling user.", content = @Content)
    public Response deleteCustom(@Parameter(hidden = true) @Context SecurityContext sc,
                                 @Parameter(description = "Custom bandit UUID.") @PathParam("id") @NotNull UUID id) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<CustomBanditDescription> bandit = nzyme.getDot11().findCustomBandit(id);

        if (bandit.isEmpty()
                || !hasPermissions(authenticatedUser, bandit.get().organizationId(), bandit.get().tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().deleteCustomBandit(bandit.get().id());

        return Response.ok().build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/custom/show/{id}/fingerprints")
    @Operation(operationId = "createCustomBanditFingerprint", summary = "Add a fingerprint to a custom bandit",
            description = "A fingerprint is a hash that Nzyme calculates from the tagged parameters of the frames "
                    + "a device sends. It must be exactly 64 characters long and must not already be attached to this "
                    + "bandit. Requires the dot11_monitoring_manage feature permission.",
            externalDocs = @ExternalDocumentation(description = "WiFi fingerprinting in the Nzyme documentation",
                    url = "https://go.nzyme.org/wifi-fingerprinting"))
    @ApiResponse(responseCode = "201", description = "Fingerprint added.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The fingerprint is not 64 characters long or is already attached to this bandit.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Bandit not found or not accessible by the calling user.", content = @Content)
    public Response addFingerprint(@Parameter(hidden = true) @Context SecurityContext sc,
                                   @Parameter(description = "Custom bandit UUID.") @PathParam("id") @NotNull UUID id,
                                   @RequestBody(description = "The 64 character fingerprint to add.", required = true, content = @Content(mediaType = "application/json"))
                                   @Valid CreateBanditFingerprintRequest req) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<CustomBanditDescription> bandit = nzyme.getDot11().findCustomBandit(id);

        if (bandit.isEmpty()
                || !hasPermissions(authenticatedUser, bandit.get().organizationId(), bandit.get().tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (req.fingerprint().length() != 64) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        List<String> fingerprints = nzyme.getDot11().findFingerprintsOfCustomBandit(bandit.get().id());

        // Check if fingerprint already exists.
        if (fingerprints.contains(req.fingerprint())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        nzyme.getDot11().addFingerprintOfCustomBandit(bandit.get().id(), req.fingerprint());
        nzyme.getDot11().bumpCustomBanditUpdatedAt(bandit.get().id());

        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "dot11_monitoring_manage" })
    @Path("/custom/show/{id}/fingerprints/show/{fingerprint}")
    @Operation(operationId = "deleteCustomBanditFingerprint", summary = "Remove a fingerprint from a custom bandit",
            description = "Succeeds even if the bandit does not carry this fingerprint. Requires the "
                    + "dot11_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Fingerprint removed.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Bandit not found or not accessible by the calling user.", content = @Content)
    public Response removeFingerprint(@Parameter(hidden = true) @Context SecurityContext sc,
                                      @Parameter(description = "Custom bandit UUID.") @PathParam("id") @NotNull UUID id,
                                      @Parameter(description = "The fingerprint to remove.") @PathParam("fingerprint") @NotEmpty String fingerprint) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<CustomBanditDescription> bandit = nzyme.getDot11().findCustomBandit(id);

        if (bandit.isEmpty()
                || !hasPermissions(authenticatedUser, bandit.get().organizationId(), bandit.get().tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getDot11().removeFingerprintOfCustomBandit(bandit.get().id(), fingerprint);
        nzyme.getDot11().bumpCustomBanditUpdatedAt(bandit.get().id());

        return Response.ok().build();
    }

    private boolean hasPermissions(AuthenticatedUser authenticatedUser, UUID organizationId, UUID tenantId) {
        if (!authenticatedUser.isSuperAdministrator()) {
            if (authenticatedUser.isOrganizationAdministrator()) {
                // Org admin. Must be their org.
                if (!organizationId.equals(authenticatedUser.getOrganizationId())) {
                    return false;
                }
            } else {
                // Tenant user. Must be their org and tenant.
                if (!organizationId.equals(authenticatedUser.getOrganizationId())
                        || !tenantId.equals(authenticatedUser.getTenantId())) {
                    return false;
                }
            }
        }

        return true;
    }

}
