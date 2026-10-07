package app.nzyme.core.rest.resources.system.cluster;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.rest.responses.distributed.MessageBusMessageListResponse;
import app.nzyme.core.rest.responses.distributed.MessageBusMessageResponse;
import app.nzyme.plugin.distributed.messaging.StoredMessage;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/api/system/cluster/messagebus")
@RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Cluster", description = "A cluster consists of one or more Nzyme nodes that share a database. These "
        + "endpoints expose the nodes, the message bus and the tasks queue that connect them.")
public class MessageBusResource {

    private static final Logger LOG = LogManager.getLogger(MessageBusResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("messages")
    @Operation(operationId = "findMessageBusMessages", summary = "List message bus messages",
            description = "Nodes of a cluster exchange messages over the message bus. Returns the stored messages, "
                    + "newest first, together with their delivery status. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Messages found.",
            content = @Content(schema = @Schema(implementation = MessageBusMessageListResponse.class)))
    @ApiResponse(responseCode = "401", description = "The requested page size is larger than the maximum of 250.", content = @Content)
    public Response findMessages(@Parameter(description = "Page size. Must not be larger than 250.") @QueryParam("limit") int limit,
                                 @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<MessageBusMessageResponse> responseMessages = Lists.newArrayList();

        for (StoredMessage message : nzyme.getMessageBus().getAllMessages(limit, offset)) {
            responseMessages.add(MessageBusMessageResponse.create(
                    message.id(),
                    message.sender(),
                    message.receiver(),
                    message.type(),
                    message.status(),
                    message.createdAt(),
                    message.cycleLimiter(),
                    message.acknowledgedAt(),
                    message.processingTimeMs()
            ));
        }

        long count = nzyme.getMessageBus().getTotalMessageCount();

        return Response.ok(MessageBusMessageListResponse.create(count, responseMessages)).build();
    }

    @PUT
    @Path("/messages/show/{id}/acknowledgefailure")
    @Operation(operationId = "acknowledgeMessageBusMessageFailure", summary = "Acknowledge a failed message",
            description = "Marks the failure of a single message as acknowledged so that it no longer shows up as a "
                    + "problem. The message itself is not retried. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Failure acknowledged.", content = @Content)
    public Response acknowledgeFailure(@Parameter(description = "Message ID.") @PathParam("id") long id) {
        nzyme.getMessageBus().acknowledgeMessageFailure(id);

        return Response.ok().build();
    }

    @PUT
    @Path("/messages/all/acknowledgefailure")
    @Operation(operationId = "acknowledgeAllMessageBusMessageFailures", summary = "Acknowledge all failed messages",
            description = "Marks the failures of all failed messages as acknowledged. The messages themselves are not "
                    + "retried. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Failures acknowledged.", content = @Content)
    public Response acknowledgeAllFailures() {
        nzyme.getMessageBus().acknowledgeAllMessageFailures();

        return Response.ok().build();
    }

}
