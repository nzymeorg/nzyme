package app.nzyme.core.rest.resources.system.cluster;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.crypto.tls.TLSKeyAndCertificate;
import app.nzyme.core.distributed.Node;
import app.nzyme.core.distributed.NodeRegistryKeys;
import app.nzyme.core.distributed.database.metrics.GaugeHistogramBucket;
import app.nzyme.core.distributed.MetricExternalName;
import app.nzyme.core.monitoring.TimerEntry;
import app.nzyme.core.rest.requests.NodesConfigurationUpdateRequest;
import app.nzyme.core.rest.responses.metrics.GaugeResponse;
import app.nzyme.core.rest.responses.metrics.TimerResponse;
import app.nzyme.core.rest.responses.nodes.*;
import app.nzyme.core.taps.db.metrics.BucketSize;
import app.nzyme.core.util.MetricNames;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraintValidator;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/api/system/cluster/nodes")
@RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Cluster", description = "A cluster consists of one or more Nzyme nodes that share a database. These "
        + "endpoints expose the nodes, the message bus and the tasks queue that connect them.")
public class NodesResource {

    private static final Logger LOG = LogManager.getLogger(NodesResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findNodes", summary = "List cluster nodes",
            description = "Returns all nodes of the cluster with their most recent metrics and TLS certificate "
                    + "information. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Nodes found.",
            content = @Content(schema = @Schema(implementation = NodesListResponse.class)))
    @ApiResponse(responseCode = "500", description = "A node has no TLS certificate on record.", content = @Content)
    public Response findAll() {
        List<NodeResponse> nodes = Lists.newArrayList();
        for (Node node : nzyme.getNodeManager().getNodes()) {
            Optional<TLSKeyAndCertificate> tls = nzyme.getCrypto().getTLSCertificateOfNode(node.uuid());
            if (tls.isEmpty()) {
                LOG.info("Could not find TLS certificate of node [{}].", node.uuid());
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }

            nodes.add(buildNodeResponse(node, tls.get()));
        }

        return Response.ok(NodesListResponse.create(nodes)).build();
    }

    @GET
    @Path("/show/{uuid}")
    @Operation(operationId = "findNode", summary = "Get details of a cluster node",
            description = "Returns the configuration, process information and most recent metrics of a single node. "
                    + "Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Node found.",
            content = @Content(schema = @Schema(implementation = NodeResponse.class)))
    @ApiResponse(responseCode = "403", description = "The node ID is not a valid UUID.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Node not found.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The node has no TLS certificate on record.", content = @Content)
    public Response findOne(@Parameter(description = "Node UUID.") @PathParam("uuid") String uuid) {
        UUID nodeId;

        try {
            nodeId = UUID.fromString(uuid);
        } catch(IllegalArgumentException e) {
            LOG.warn("Invalid UUID supplied.", e);
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Optional<Node> res = nzyme.getNodeManager().getNode(nodeId);
        if (res.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TLSKeyAndCertificate> tls = nzyme.getCrypto().getTLSCertificateOfNode(nodeId);
        if (tls.isEmpty()) {
            LOG.info("Could not find TLS certificate of node [{}].", nodeId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.ok(buildNodeResponse(res.get(), tls.get())).build();
    }

    @DELETE
    @Path("/show/{uuid}")
    @Operation(operationId = "deleteNode", summary = "Delete a cluster node",
            description = "Removes a node from the cluster. A node that is still running will register itself again "
                    + "with its next report. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Node deleted.", content = @Content)
    @ApiResponse(responseCode = "403", description = "The node ID is not a valid UUID.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Node not found.", content = @Content)
    public Response deleteNode(@Parameter(description = "Node UUID.") @PathParam("uuid") String uuid) {
        UUID nodeId;

        try {
            nodeId = UUID.fromString(uuid);
        } catch(IllegalArgumentException e) {
            LOG.warn("Invalid UUID supplied.", e);
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Optional<Node> res = nzyme.getNodeManager().getNode(nodeId);
        if (res.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getNodeManager().deleteNode(nodeId);

        return Response.ok().build();
    }

    @GET
    @Path("/show/{uuid}/metrics/gauges/{metricname}/histogram")
    @Operation(operationId = "findNodeGaugeHistogram", summary = "Get 24 hour histogram of a node gauge metric",
            description = "Buckets are one minute wide and ordered oldest first. Requires super administrator "
                    + "permissions.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = NodeMetricsGaugeHistogramResponse.class)))
    @ApiResponse(responseCode = "403", description = "The node ID is not a valid UUID, or the metric name is unknown.", content = @Content)
    @ApiResponse(responseCode = "404", description = "The node has not reported this metric.", content = @Content)
    public Response findMetricsGaugeHistogram(@Parameter(description = "Node UUID.") @PathParam("uuid") String uuid,
                                              @Parameter(description = "Metric name as reported by the node.") @PathParam("metricname") String n) {
        MetricExternalName metricName;
        UUID nodeId;

        try {
            metricName = MetricExternalName.valueOf(n.toUpperCase());
        } catch(IllegalArgumentException e) {
            LOG.warn("Unknown node metric name [{}].", n);
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        try {
            nodeId = UUID.fromString(uuid);
        } catch(IllegalArgumentException e) {
            LOG.warn("Invalid node ID provided.", e);
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Optional<Map<DateTime, GaugeHistogramBucket>> histo = nzyme.getNodeManager().findMetricsHistogram(
                nodeId, metricName.database_label, 24, BucketSize.MINUTE
        );

        if (histo.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Map<DateTime, NodeMetricsGaugeHistogramValueResponse> result = Maps.newTreeMap();
        for (GaugeHistogramBucket value : histo.get().values()) {
            result.put(value.bucket(), NodeMetricsGaugeHistogramValueResponse.create(
                    value.bucket(), value.sum(), value.average(), value.maximum(), value.minimum()
            ));
        }

        return Response.ok(NodeMetricsGaugeHistogramResponse.create(result)).build();
    }

    @GET
    @Path("/configuration")
    @Operation(operationId = "findNodesConfiguration", summary = "Get cluster node configuration",
            description = "Returns the configuration that applies to all nodes of the cluster, including the current "
                    + "value, the default value and the validation constraints of every setting. This is currently "
                    + "only the regular expression that marks nodes with a matching name as ephemeral. Requires super "
                    + "administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = NodesConfigurationResponse.class)))
    public Response configuration() {
        String ephemeralNodesRegexValue = nzyme.getDatabaseCoreRegistry().getValue(NodeRegistryKeys.EPHEMERAL_NODES_REGEX.key())
                .orElse(null);

        ConfigurationEntryResponse ephemeralNodesRegex = ConfigurationEntryResponse.create(
                NodeRegistryKeys.EPHEMERAL_NODES_REGEX.key(),
                "Ephemeral Nodes Regular Expression",
                ephemeralNodesRegexValue,
                ConfigurationEntryValueType.STRING,
                null,
                NodeRegistryKeys.EPHEMERAL_NODES_REGEX.requiresRestart(),
                NodeRegistryKeys.EPHEMERAL_NODES_REGEX.constraints().get(),
                "ephemeral-nodes"
        );

        return Response.ok(NodesConfigurationResponse.create(ephemeralNodesRegex)).build();
    }

    @PUT
    @Path("/configuration")
    @Operation(operationId = "updateNodesConfiguration", summary = "Update cluster node configuration",
            description = "Each submitted value is validated against the constraints of its configuration key before "
                    + "it is stored. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "422", description = "No changes were submitted, a value failed validation, or an "
            + "unknown configuration key was submitted.", content = @Content)
    public Response update(@RequestBody(description = "Configuration keys and their new values.", required = true, content = @Content(mediaType = "application/json"))
                           NodesConfigurationUpdateRequest ur) {
        if (ur.change().isEmpty()) {
            LOG.info("Empty configuration parameters.");
            return Response.status(422).build();
        }

        for (Map.Entry<String, Object> c : ur.change().entrySet()) {
            switch (c.getKey()) {
                case "ephemeral_nodes_regex":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(NodeRegistryKeys.EPHEMERAL_NODES_REGEX, c)) {
                        return Response.status(422).build();
                    }
                    break;
                default:
                    LOG.info("Unknown configuration parameter [{}].", c.getKey());
                    return Response.status(422).build();
            }

            nzyme.getDatabaseCoreRegistry().setValue(c.getKey(), c.getValue().toString());
        }

        return Response.ok().build();
    }

    private NodeResponse buildNodeResponse(Node node, TLSKeyAndCertificate tls) {
        Map<String, GaugeResponse> gauges = Maps.newHashMap();
        Map<String, TimerResponse> timers = Maps.newHashMap();

        nzyme.getDatabase().useHandle(handle -> {
            // Timers.
            timers.put("password_hashing", buildTimerResponse(nzyme.getNodeManager().findLatestActiveMetricsTimerValue(
                    node.uuid(), MetricExternalName.PASSWORD_HASHING_TIMER.database_label, handle)));
            timers.put("context_mac_lookup", buildTimerResponse(nzyme.getNodeManager().findLatestActiveMetricsTimerValue(
                    node.uuid(), MetricExternalName.CONTEXT_MAC_LOOKUP_TIMER.database_label, handle)));

            // Report processing timers.
            timers.put("report_processing_dot11", buildTimerResponse(nzyme.getNodeManager().findLatestActiveMetricsTimerValue(
                    node.uuid(), MetricExternalName.REPORT_PROCESSING_DOT11_TIMER.database_label, handle)));
            timers.put("report_processing_tcp", buildTimerResponse(nzyme.getNodeManager().findLatestActiveMetricsTimerValue(
                    node.uuid(), MetricExternalName.REPORT_PROCESSING_TCP_TIMER.database_label, handle)));
            timers.put("report_processing_dns", buildTimerResponse(nzyme.getNodeManager().findLatestActiveMetricsTimerValue(
                    node.uuid(), MetricExternalName.REPORT_PROCESSING_DNS_TIMER.database_label, handle)));
            timers.put("report_processing_ssh", buildTimerResponse(nzyme.getNodeManager().findLatestActiveMetricsTimerValue(
                    node.uuid(), MetricExternalName.REPORT_PROCESSING_SSH_TIMER.database_label, handle)));
            timers.put("report_processing_socks", buildTimerResponse(nzyme.getNodeManager().findLatestActiveMetricsTimerValue(
                    node.uuid(), MetricExternalName.REPORT_PROCESSING_SOCKS_TIMER.database_label, handle)));

            // Gauges.
            gauges.put("geoip_cache_size", GaugeResponse.create(nzyme.getNodeManager().findLatestActiveMetricsGaugeValue(
                    node.uuid(), MetricExternalName.GEOIP_CACHE_SIZE.database_label, handle
            ).orElse(0D)));
            gauges.put("context_mac_cache_size", GaugeResponse.create(nzyme.getNodeManager().findLatestActiveMetricsGaugeValue(
                    node.uuid(), MetricExternalName.CONTEXT_MAC_CACHE_SIZE.database_label, handle
            ).orElse(0D)));
            gauges.put("context_network_cache_size", GaugeResponse.create(nzyme.getNodeManager().findLatestActiveMetricsGaugeValue(
                    node.uuid(), MetricExternalName.CONTEXT_NETWORK_CACHE_SIZE.database_label, handle
            ).orElse(0D)));

            // Log counts.
            gauges.put("log_counts_trace", GaugeResponse.create( nzyme.getNodeManager().findLatestActiveMetricsGaugeValue(
                    node.uuid(), MetricExternalName.LOG_COUNTS_TRACE.database_label, handle
            ).orElse(0D)));

            gauges.put("log_counts_debug", GaugeResponse.create(nzyme.getNodeManager().findLatestActiveMetricsGaugeValue(
                    node.uuid(), MetricExternalName.LOG_COUNTS_DEBUG.database_label, handle
            ).orElse(0D)));
            gauges.put("log_counts_info", GaugeResponse.create(nzyme.getNodeManager().findLatestActiveMetricsGaugeValue(
                    node.uuid(), MetricExternalName.LOG_COUNTS_INFO.database_label, handle
            ).orElse(0D)));
            gauges.put("log_counts_warn", GaugeResponse.create(nzyme.getNodeManager().findLatestActiveMetricsGaugeValue(
                    node.uuid(), MetricExternalName.LOG_COUNTS_WARN.database_label, handle
            ).orElse(0D)));
            gauges.put("log_counts_error", GaugeResponse.create( nzyme.getNodeManager().findLatestActiveMetricsGaugeValue(
                    node.uuid(), MetricExternalName.LOG_COUNTS_ERROR.database_label, handle
            ).orElse(0D)));
            gauges.put("log_counts_fatal", GaugeResponse.create(nzyme.getNodeManager().findLatestActiveMetricsGaugeValue(
                    node.uuid(), MetricExternalName.LOG_COUNTS_FATAL.database_label, handle
            ).orElse(0D)));
        });

        return NodeResponse.create(
                node.uuid().toString(),
                node.name(),
                node.lastSeen().isAfter(DateTime.now().minusMinutes(2)),
                node.httpListenUri().toString(),
                node.httpExternalUri().toString(),
                tls.signature(),
                tls.expiresAt(),
                node.memoryBytesTotal(),
                node.memoryBytesAvailable(),
                node.memoryBytesUsed(),
                node.heapBytesTotal(),
                node.heapBytesAvailable(),
                node.heapBytesUsed(),
                node.cpuSystemLoad(),
                node.cpuThreadCount(),
                node.processStartTime(),
                node.processVirtualSize(),
                node.processArguments(),
                node.osInformation(),
                node.version(),
                node.lastSeen(),
                node.deleted(),
                node.clock(),
                node.clockDriftMs(),
                node.isEphemeral(),
                node.cycle(),
                timers,
                gauges
        );
    }

    private static TimerResponse buildTimerResponse(Optional<TimerEntry> t) {
        if (t.isEmpty()) {
            return TimerResponse.create(0, 0, 0, 0, 0, 0);
        }

        return TimerResponse.create(
                t.get().mean(),
                t.get().max(),
                t.get().min(),
                t.get().stddev(),
                t.get().p99(),
                t.get().counter()
        );
    }

}
