package app.nzyme.core.rest.resources.system.cluster;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.rest.responses.distributed.TasksQueueTaskResponse;
import app.nzyme.core.rest.responses.distributed.TasksQueueTasksListResponse;
import app.nzyme.plugin.distributed.tasksqueue.StoredTask;
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

@Path("/api/system/cluster/tasksqueue")
@RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Cluster", description = "A cluster consists of one or more Nzyme nodes that share a database. These "
        + "endpoints expose the nodes, the message bus and the tasks queue that connect them.")
public class TasksQueueResource {

    private static final Logger LOG = LogManager.getLogger(MessageBusResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/tasks")
    @Operation(operationId = "findTasksQueueTasks", summary = "List tasks queue tasks",
            description = "Nodes of a cluster hand work to each other through the tasks queue. Returns the stored "
                    + "tasks, newest first, together with their processing status and the node that processed them. "
                    + "Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Tasks found.",
            content = @Content(schema = @Schema(implementation = TasksQueueTasksListResponse.class)))
    @ApiResponse(responseCode = "401", description = "The requested page size is larger than the maximum of 250.", content = @Content)
    public Response findTasks(@Parameter(description = "Page size. Must not be larger than 250.") @QueryParam("limit") int limit,
                              @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (limit > 250) {
            LOG.warn("Requested limit larger than 250. Not allowed.");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<TasksQueueTaskResponse> tasks = Lists.newArrayList();
        for (StoredTask task : nzyme.getTasksQueue().getAllTasks(limit, offset)) {
            tasks.add(TasksQueueTaskResponse.create(
                    task.id(),
                    task.sender(),
                    task.type(),
                    task.allowRetry(),
                    task.createdAt(),
                    task.status(),
                    task.retries(),
                    task.allowProcessSelf(),
                    task.processingTimeMs(),
                    task.firstProcessedAt(),
                    task.lastProcessedAt(),
                    task.processedBy()
            ));
        }

        long count = nzyme.getTasksQueue().getTotalTaskCount();

        return Response.ok(TasksQueueTasksListResponse.create(count, tasks)).build();
    }

    @PUT
    @Path("/tasks/show/{id}/acknowledgefailure")
    @Operation(operationId = "acknowledgeTasksQueueTaskFailure", summary = "Acknowledge a failed task",
            description = "Marks the failure of a single task as acknowledged so that it no longer shows up as a "
                    + "problem. The task itself is not retried. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Failure acknowledged.", content = @Content)
    public Response acknowledgeFailure(@Parameter(description = "Task ID.") @PathParam("id") long id) {
        nzyme.getTasksQueue().acknowledgeTaskFailure(id);

        return Response.ok().build();
    }

    @PUT
    @Path("/tasks/all/acknowledgefailure")
    @Operation(operationId = "acknowledgeAllTasksQueueTaskFailures", summary = "Acknowledge all failed tasks",
            description = "Marks the failures of all failed tasks as acknowledged. The tasks themselves are not "
                    + "retried. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Failures acknowledged.", content = @Content)
    public Response acknowledgeAllFailures() {
        nzyme.getTasksQueue().acknowledgeAllTaskFailures();

        return Response.ok().build();
    }

}
