package app.nzyme.core.rest.resources.system;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.maintenance.DatabaseMaintenance;
import app.nzyme.core.database.maintenance.DatabaseMaintenanceRegistryKeys;
import app.nzyme.core.database.maintenance.DatabaseStatistics;
import app.nzyme.core.database.maintenance.TableClass;
import app.nzyme.core.database.maintenance.db.MaintenanceLogEntry;
import app.nzyme.core.distributed.MetricExternalName;
import app.nzyme.core.distributed.Node;
import app.nzyme.core.distributed.database.metrics.GaugeHistogramBucket;
import app.nzyme.core.registry.RegistryChangeValidator;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.requests.GenericConfigurationUpdateRequest;
import app.nzyme.core.rest.responses.nodes.NodeMetricsGaugeHistogramResponse;
import app.nzyme.core.rest.responses.nodes.NodeMetricsGaugeHistogramValueResponse;
import app.nzyme.core.rest.responses.system.database.maintenance.*;
import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/api/system/database/maintenance")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
@Tag(name = "Database Maintenance", description = "Health of the PostgreSQL server behind Nzyme: server settings "
        + "compared with recommendations, table and index statistics, the automatic maintenance Nzyme runs on its "
        + "own tables, and its log. All endpoints require super administrator permissions.")
public class DatabaseMaintenanceResource extends UserAuthenticatedResource {

    private static final Logger LOG = LogManager.getLogger(DatabaseMaintenanceResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/health")
    @Operation(operationId = "findDatabaseHealth", summary = "Get database health statistics",
            description = "Returns the latest statistics snapshot of the PostgreSQL server: version and size, cache "
                    + "and checkpoint behavior over the last minutes, every relevant server setting with its "
                    + "recommended value, all tables with their dead row share and last vacuum, all indexes of at "
                    + "least 1 MB with an estimate of their bloat, and the maintenance action that is running or "
                    + "queued. Statistics are collected once a minute.")
    @ApiResponse(responseCode = "200", description = "Statistics found.",
            content = @Content(schema = @Schema(implementation = DatabaseHealthResponse.class)))
    @ApiResponse(responseCode = "404", description = "No statistics collected yet. Retry in a minute.",
            content = @Content)
    public Response health() {
        DatabaseMaintenance maintenance = nzyme.getDatabaseMaintenance();
        Optional<DatabaseStatistics> stats = maintenance.getStatistics();

        if (stats.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DatabaseStatistics.ServerStatistics s = stats.get().server();

        List<DatabaseServerSettingResponse> settings = Lists.newArrayList();
        for (DatabaseStatistics.ServerSetting setting : stats.get().settings()) {
            settings.add(DatabaseServerSettingResponse.create(
                    setting.name(), setting.currentValue(), setting.recommendedValue(), setting.ok(),
                    setting.severity().name(), setting.scope().name(), setting.description(), setting.configLines(),
                    setting.pendingRestart()
            ));
        }

        List<DatabaseTableResponse> tables = Lists.newArrayList();
        for (DatabaseStatistics.TableStatistics t : stats.get().tables()) {
            tables.add(DatabaseTableResponse.create(
                    t.name(),
                    t.tableClass() == null ? null : t.tableClass().name(),
                    t.tableClass() == null ? null : t.tableClass().getTitle(),
                    t.heapBytes(), t.indexBytes(), t.totalBytes(), t.liveTuples(), t.deadTuples(),
                    t.deadRatioPercent(), t.lastVacuum(), t.lastAnalyze(), t.insertsTotal(), t.updatesTotal(),
                    t.hotUpdatesTotal(), t.deletesTotal(), t.vacuumCount()
            ));
        }

        List<DatabaseIndexResponse> indexes = Lists.newArrayList();
        for (DatabaseStatistics.IndexStatistics i : stats.get().indexes()) {
            indexes.add(DatabaseIndexResponse.create(
                    i.name(), i.table(), i.bytes(), i.scans(), i.primary(), i.unique(), i.valid(), i.bloatPercent(), i.bloatBytes()
            ));
        }

        List<DatabaseMaintenance.PendingAction> pending = maintenance.findPendingActions();
        DatabaseMaintenanceActionResponse running = null;
        List<DatabaseMaintenanceActionResponse> queued = Lists.newArrayList();
        for (DatabaseMaintenance.PendingAction a : pending) {
            if (a.running() && running == null) {
                running = actionResponse(a);
            } else {
                queued.add(actionResponse(a));
            }
        }

        return Response.ok(DatabaseHealthResponse.create(
                nzyme.getNodeInformation().name(),
                stats.get().collectedAt(),
                DatabaseServerResponse.create(
                        s.version(), s.versionNumber(), s.databaseSizeBytes(), s.heapBytes(), s.indexBytes(),
                        s.connections(), s.maxConnections(), s.cacheHitRatioRecent(), s.cacheHitRatioTotal(),
                        s.tempBytesPerMinute(), s.tempFilesPerMinute(), s.checkpointsRequestedRatioRecent(),
                        s.checkpointsRecent(), s.deadlocksPerMinute(), s.hotUpdateRatioRecent(), s.xidAge(),
                        s.oldestTransactionSeconds(), s.ioTimingEnabled(), s.ioReadWaitMsPerMinute(),
                        s.ioWriteWaitMsPerMinute(), s.statisticsSince(), s.windowMinutes(), s.serverStartedAt(),
                        s.isWarmingUp()
                ),
                settings,
                tables,
                indexes,
                running,
                queued,
                maintenance.isInMaintenanceWindow(DateTime.now()),
                maintenance.maintenanceWindowStartHour(),
                maintenance.maintenanceWindowHours(),
                maintenance.isTableTuningEnabled(),
                maintenance.isAutoVacuumEnabled(),
                maintenance.isAutoReindexEnabled(),
                maintenance.reindexBloatThresholdPercent(),
                maintenance.reindexMinimumIndexMb()
        )).build();
    }

    @GET
    @Path("/histogram/{metric}")
    @Operation(operationId = "findDatabaseMetricHistogram", summary = "Get a 24 hour histogram of a database metric",
            description = "Database metrics are reported by every node as node gauges. This endpoint averages them "
                    + "across all nodes into one minute buckets, ordered oldest first. Only metric names starting "
                    + "with database_ are accepted.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = NodeMetricsGaugeHistogramResponse.class)))
    @ApiResponse(responseCode = "404", description = "Unknown metric name or no values recorded yet.",
            content = @Content)
    public Response histogram(@Parameter(description = "Database metric name, for example "
            + "database_cache_hit_ratio.") @PathParam("metric") String metric) {
        MetricExternalName name;
        try {
            name = MetricExternalName.valueOf(metric.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!name.database_label.startsWith("database_") || name.database_label.startsWith("database_pool_")) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<GaugeHistogramBucket> buckets = nzyme.getDatabaseMaintenance().findGaugeHistogram(name.database_label, 24);
        if (buckets.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Map<DateTime, NodeMetricsGaugeHistogramValueResponse> values = Maps.newTreeMap();
        for (GaugeHistogramBucket b : buckets) {
            values.put(b.bucket(), NodeMetricsGaugeHistogramValueResponse.create(
                    b.bucket(), b.sum(), b.average(), b.maximum(), b.minimum()
            ));
        }

        return Response.ok(NodeMetricsGaugeHistogramResponse.create(values)).build();
    }

    @GET
    @Path("/log")
    @Operation(operationId = "findDatabaseMaintenanceLog", summary = "Get the database maintenance log",
            description = "Every vacuum, reindex and storage parameter change Nzyme ran, newest first, with the "
                    + "node that ran it, whether it was automatic or requested by a user, and the result.")
    @ApiResponse(responseCode = "200", description = "Log entries found.",
            content = @Content(schema = @Schema(implementation = DatabaseMaintenanceLogListResponse.class)))
    public Response log(@Parameter(description = "Maximum number of entries to return, at most 250.")
                        @QueryParam("limit") @DefaultValue("50") int limit,
                        @Parameter(description = "Number of entries to skip.")
                        @QueryParam("offset") @DefaultValue("0") int offset) {
        if (limit < 1 || limit > 250 || offset < 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Map<UUID, String> nodeNames = Maps.newHashMap();
        for (Node node : nzyme.getNodeManager().getNodes()) {
            nodeNames.put(node.uuid(), node.name());
        }

        List<DatabaseMaintenanceLogEntryResponse> entries = Lists.newArrayList();
        for (MaintenanceLogEntry e : nzyme.getDatabaseMaintenance().findLog(limit, offset)) {
            entries.add(DatabaseMaintenanceLogEntryResponse.create(
                    e.id(), e.nodeId(), nodeNames.get(e.nodeId()), e.action(), e.target(), e.initiator(),
                    e.details(), e.success(), e.startedAt(), e.finishedAt()
            ));
        }

        return Response.ok(DatabaseMaintenanceLogListResponse.create(
                nzyme.getDatabaseMaintenance().countLog(), entries
        )).build();
    }

    @GET
    @Path("/configuration")
    @Operation(operationId = "findDatabaseMaintenanceConfiguration", summary = "Get database maintenance settings",
            description = "General settings of the automatic database maintenance and the storage parameters of "
                    + "each table class. Settings marked as requiring a restart only take effect after all nodes "
                    + "were restarted.")
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = DatabaseMaintenanceConfigurationResponse.class)))
    public Response configuration() {
        DatabaseMaintenance maintenance = nzyme.getDatabaseMaintenance();

        Map<String, ConfigurationEntryResponse> entries = Maps.newLinkedHashMap();
        entries.put("table_tuning_enabled", bool(DatabaseMaintenanceRegistryKeys.TABLE_TUNING_ENABLED,
                "Manage table storage parameters", maintenance.isTableTuningEnabled()));
        entries.put("auto_vacuum_enabled", bool(DatabaseMaintenanceRegistryKeys.AUTO_VACUUM_ENABLED,
                "Vacuum tables with too many dead rows", maintenance.isAutoVacuumEnabled()));
        entries.put("auto_reindex_enabled", bool(DatabaseMaintenanceRegistryKeys.AUTO_REINDEX_ENABLED,
                "Rebuild bloated indexes in the maintenance window", maintenance.isAutoReindexEnabled()));
        entries.put("reindex_bloat_threshold_percent", number(DatabaseMaintenanceRegistryKeys.REINDEX_BLOAT_THRESHOLD_PERCENT,
                "Rebuild indexes estimated to be more than this percent empty", maintenance.reindexBloatThresholdPercent()));
        entries.put("reindex_minimum_index_mb", number(DatabaseMaintenanceRegistryKeys.REINDEX_MINIMUM_INDEX_MB,
                "Only rebuild indexes of at least this size (MB)", maintenance.reindexMinimumIndexMb()));
        entries.put("window_start_hour", number(DatabaseMaintenanceRegistryKeys.MAINTENANCE_WINDOW_START_HOUR,
                "Maintenance window start hour (UTC)", maintenance.maintenanceWindowStartHour()));
        entries.put("window_hours", number(DatabaseMaintenanceRegistryKeys.MAINTENANCE_WINDOW_HOURS,
                "Maintenance window length (hours)", maintenance.maintenanceWindowHours()));
        entries.put("session_tuning_enabled", bool(DatabaseMaintenanceRegistryKeys.SESSION_TUNING_ENABLED,
                "Apply session settings to Nzyme connections", maintenance.isSessionTuningEnabled()));
        entries.put("session_work_mem_mb", number(DatabaseMaintenanceRegistryKeys.SESSION_WORK_MEM_MB,
                "work_mem of Nzyme connections (MB)", maintenance.sessionWorkMemMb()));

        Map<TableClass, DatabaseMaintenance.ClassParameters> parameters = maintenance.getTableClassParameters();
        List<DatabaseTableClassConfigurationResponse> classes = Lists.newArrayList();
        for (TableClass c : TableClass.values()) {
            DatabaseMaintenance.ClassParameters p = parameters.get(c);
            Map<String, ConfigurationEntryResponse> classEntries = Maps.newLinkedHashMap();
            classEntries.put("vacuum_scale_percent", number(DatabaseMaintenanceRegistryKeys.vacuumScalePercent(c),
                    "Vacuum when this percent of rows is dead", p.vacuumScalePercent()));
            classEntries.put("vacuum_threshold", number(DatabaseMaintenanceRegistryKeys.vacuumThreshold(c),
                    "Minimum dead rows before vacuum", p.vacuumThreshold()));
            classEntries.put("analyze_scale_percent", number(DatabaseMaintenanceRegistryKeys.analyzeScalePercent(c),
                    "Refresh statistics when this percent of rows changed", p.analyzeScalePercent()));
            classEntries.put("fillfactor", number(DatabaseMaintenanceRegistryKeys.fillfactor(c),
                    "Page fill factor (percent)", p.fillfactor()));

            classes.add(DatabaseTableClassConfigurationResponse.create(
                    c.name(), c.getTitle(), c.getDescription(), c.getTables(), classEntries
            ));
        }

        return Response.ok(DatabaseMaintenanceConfigurationResponse.create(entries, classes)).build();
    }

    @PUT
    @Path("/configuration")
    @Operation(operationId = "updateDatabaseMaintenanceConfiguration", summary = "Update database maintenance settings",
            description = "Each submitted value is validated against the constraints of its key before it is "
                    + "stored. Changed table storage parameters are applied to the tables right away. Session "
                    + "settings take effect after a node restart.")
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "422", description = "No changes were submitted, a value failed validation, or an "
            + "unknown configuration key was submitted.", content = @Content)
    public Response update(@RequestBody(description = "Configuration keys and their new values.", required = true,
            content = @Content(mediaType = "application/json")) GenericConfigurationUpdateRequest req) {
        if (req == null || req.change() == null || req.change().isEmpty()) {
            return Response.status(422).build();
        }

        Optional<List<RegistryChangeValidator.Change>> changes = RegistryChangeValidator
                .allowing(DatabaseMaintenanceRegistryKeys.allKeys().toArray(new RegistryKey[0]))
                .validate(req.change());

        if (changes.isEmpty()) {
            return Response.status(422).build();
        }

        for (RegistryChangeValidator.Change c : changes.get()) {
            nzyme.getDatabaseCoreRegistry().setValue(c.key(), c.value());
        }

        nzyme.getDatabaseMaintenance().onConfigurationChanged();

        return Response.ok().build();
    }

    @POST
    @Path("/actions/tables/{table}/vacuum")
    @Operation(operationId = "vacuumDatabaseTable", summary = "Vacuum a table now",
            description = "Queues a VACUUM (ANALYZE) of the table in the cluster tasks queue. Any node picks it "
                    + "up, maintenance actions run one at a time, and it appears in the maintenance log and on the "
                    + "tasks page. Vacuum does not block reads or writes.")
    @ApiResponse(responseCode = "202", description = "Vacuum queued.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Unknown table.", content = @Content)
    @ApiResponse(responseCode = "409", description = "The same action is already queued or running.", content = @Content)
    public Response vacuum(@Parameter(description = "Table name.") @PathParam("table") String table) {
        LOG.info("Vacuum of table [{}] requested through API.", table);
        return actionResult(nzyme.getDatabaseMaintenance().requestVacuum(table));
    }

    @POST
    @Path("/actions/tables/{table}/reindex")
    @Operation(operationId = "reindexDatabaseTable", summary = "Rebuild all indexes of a table now",
            description = "Queues a REINDEX TABLE CONCURRENTLY. The rebuild does not block reads or writes but "
                    + "can take minutes on large tables and needs free disk space for a second copy of the "
                    + "indexes while it runs.")
    @ApiResponse(responseCode = "202", description = "Reindex queued.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Unknown table.", content = @Content)
    @ApiResponse(responseCode = "409", description = "The same action is already queued or running.", content = @Content)
    public Response reindexTable(@Parameter(description = "Table name.") @PathParam("table") String table) {
        LOG.info("Reindex of table [{}] requested through API.", table);
        return actionResult(nzyme.getDatabaseMaintenance().requestReindexTable(table));
    }

    @POST
    @Path("/actions/indexes/{index}/reindex")
    @Operation(operationId = "reindexDatabaseIndex", summary = "Rebuild one index now",
            description = "Queues a REINDEX INDEX CONCURRENTLY. The rebuild does not block reads or writes and "
                    + "needs free disk space for a second copy of the index while it runs.")
    @ApiResponse(responseCode = "202", description = "Reindex queued.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Unknown index.", content = @Content)
    @ApiResponse(responseCode = "409", description = "The same action is already queued or running.", content = @Content)
    public Response reindexIndex(@Parameter(description = "Index name.") @PathParam("index") String index) {
        LOG.info("Reindex of index [{}] requested through API.", index);
        return actionResult(nzyme.getDatabaseMaintenance().requestReindexIndex(index));
    }

    private Response actionResult(DatabaseMaintenance.RequestResult result) {
        return switch (result) {
            case QUEUED -> Response.status(Response.Status.ACCEPTED).build();
            case UNKNOWN_TARGET -> Response.status(Response.Status.NOT_FOUND).build();
            case ALREADY_PENDING -> Response.status(Response.Status.CONFLICT).build();
            case TOO_SOON -> Response.status(429).build();
        };
    }

    private DatabaseMaintenanceActionResponse actionResponse(DatabaseMaintenance.PendingAction a) {
        return DatabaseMaintenanceActionResponse.create(
                a.action().type().name(), a.action().target(), a.action().initiator().name(), a.queuedAt(), a.startedAt()
        );
    }

    private ConfigurationEntryResponse bool(RegistryKey key, String title, boolean value) {
        return ConfigurationEntryResponse.create(
                key.key(), title, value, ConfigurationEntryValueType.BOOLEAN,
                key.defaultValue().orElse(null), key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()), "database-maintenance"
        );
    }

    private ConfigurationEntryResponse number(RegistryKey key, String title, int value) {
        return ConfigurationEntryResponse.create(
                key.key(), title, value, ConfigurationEntryValueType.NUMBER,
                key.defaultValue().orElse(null), key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()), "database-maintenance"
        );
    }

}
