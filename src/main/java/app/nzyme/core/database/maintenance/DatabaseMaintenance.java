package app.nzyme.core.database.maintenance;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Timer;
import app.nzyme.core.database.maintenance.DatabaseStatistics.IndexStatistics;
import app.nzyme.core.database.maintenance.DatabaseStatistics.ServerSetting;
import app.nzyme.core.database.maintenance.DatabaseStatistics.ServerStatistics;
import app.nzyme.core.database.maintenance.DatabaseStatistics.TableStatistics;
import app.nzyme.core.database.maintenance.db.MaintenanceLogEntry;
import app.nzyme.core.database.maintenance.db.MaintenanceLogEntryMapper;
import app.nzyme.core.database.usage.DatabaseUsage;
import app.nzyme.core.distributed.NodeInformation;
import app.nzyme.core.distributed.database.metrics.GaugeHistogramBucket;
import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.distributed.tasksqueue.TaskType;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.net.URI;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class DatabaseMaintenance {

    private static final Logger LOG = LogManager.getLogger(DatabaseMaintenance.class);

    public static final long ADVISORY_LOCK_KEY = 7_420_001L;

    private static final int SAMPLE_WINDOW = 16;
    private static final int AUTO_VACUUM_DEAD_RATIO_PERCENT = 20;
    private static final long AUTO_VACUUM_MINIMUM_TABLE_BYTES = 50L * 1024L * 1024L;
    private static final int AUTO_VACUUM_MINIMUM_LAG_MINUTES = 30;
    private static final int VACUUM_COOLDOWN_MINUTES = 60;
    private static final int REINDEX_COOLDOWN_HOURS = 24;
    private static final int USAGE_INTERVAL_MINUTES = 60;
    public static final int MANUAL_USAGE_COOLDOWN_MINUTES = 5;
    private static final int LOCK_WAIT_MINUTES = 10;
    private static final int STALE_RUNNING_HOURS = 6;
    private static final int INEFFECTIVE_REINDEX_COOLDOWN_DAYS = 7;

    private static final List<String> MANAGED_STORAGE_PARAMETERS = List.of(
            "autovacuum_vacuum_scale_factor", "autovacuum_vacuum_threshold", "autovacuum_analyze_scale_factor",
            "autovacuum_vacuum_cost_delay", "autovacuum_vacuum_insert_scale_factor", "fillfactor"
    );

    public enum ActionType {
        VACUUM_TABLE, REINDEX_TABLE, REINDEX_INDEX, STORAGE_PARAMETERS, USAGE_STATISTICS
    }

    public enum Initiator {
        AUTOMATIC, MANUAL, STARTUP
    }

    public record MaintenanceAction(ActionType type, @Nullable String target, Initiator initiator) { }

    public record PendingAction(MaintenanceAction action, DateTime queuedAt, @Nullable DateTime startedAt,
                                boolean running) { }

    public record ClassParameters(int vacuumScalePercent, int vacuumThreshold, int analyzeScalePercent, int fillfactor) { }

    private record CounterSample(DateTime at, long blksHit, long blksRead, long tempFiles, long tempBytes,
                                 long deadlocks, long tupUpd, long tupHotUpd, long checkpointsTimed,
                                 long checkpointsRequested, double blkReadTime, double blkWriteTime) { }

    private final NzymeNode nzyme;

    private final AtomicReference<DatabaseStatistics> statistics = new AtomicReference<>();
    private final Deque<CounterSample> samples = new ArrayDeque<>();
    private final ObjectMapper om = JsonMapper.builder().build();
    private final DatabaseUsage usage;
    private final Timer statisticsTimer;
    private final Timer usageCalculationTimer;
    private volatile boolean storageParametersChecked = false;

    public DatabaseMaintenance(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.usage = new DatabaseUsage(nzyme);
        this.statisticsTimer = nzyme.getMetrics().timer(MetricNames.DATABASE_STATISTICS_COLLECTION_TIMER);
        this.usageCalculationTimer = nzyme.getMetrics().timer(MetricNames.DATABASE_USAGE_CALCULATION_TIMER);
    }

    public DatabaseUsage usage() {
        return usage;
    }

    public RequestResult requestUsageCalculation() {
        return requestUsageCalculation(false);
    }

    public RequestResult requestUsageCalculation(boolean manual) {
        if (manual) {
            DatabaseUsage.Snapshot snapshot = usage.getSnapshot();
            if (!snapshot.estimated() && snapshot.calculatedAt().isAfter(DateTime.now().minusMinutes(MANUAL_USAGE_COOLDOWN_MINUTES))) {
                return RequestResult.TOO_SOON;
            }
        }

        Initiator initiator = manual ? Initiator.MANUAL : Initiator.AUTOMATIC;
        return enqueue(new MaintenanceAction(ActionType.USAGE_STATISTICS, null, initiator))
                ? RequestResult.QUEUED : RequestResult.ALREADY_PENDING;
    }

    public boolean isUsageCalculationPending() {
        for (PendingAction a : findPendingActions()) {
            if (a.action().type() == ActionType.USAGE_STATISTICS) {
                return true;
            }
        }

        return false;
    }

    public Optional<DatabaseStatistics> getStatistics() {
        return Optional.ofNullable(statistics.get());
    }

    public List<PendingAction> findPendingActions() {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT parameters, status, created_at, last_acked_at FROM tasks_queue " +
                                "WHERE type = :type AND status IN ('NEW', 'NEW_RETRY', 'ACK') ORDER BY created_at ASC")
                        .bind("type", TaskType.DATABASE_MAINTENANCE.name())
                        .map((rs, ctx) -> {
                            Map<String, Object> parameters;
                            try {
                                parameters = om.readValue(rs.getString("parameters"), new TypeReference<HashMap<String, Object>>() {});
                            } catch (Exception e) {
                                LOG.warn("Could not parse parameters of database maintenance task.", e);
                                return null;
                            }

                            boolean running = "ACK".equals(rs.getString("status"));
                            Timestamp acked = rs.getTimestamp("last_acked_at");

                            // A task acknowledged hours ago belongs to a node that died mid-run. Only that node
                            // resets its own tasks at startup, so it must not block maintenance forever.
                            if (running && acked != null && new DateTime(acked).isBefore(DateTime.now().minusHours(STALE_RUNNING_HOURS))) {
                                return null;
                            }

                            return new PendingAction(
                                    DatabaseMaintenanceTask.fromParameters(parameters),
                                    new DateTime(rs.getTimestamp("created_at")),
                                    running && acked != null ? new DateTime(acked) : null,
                                    running
                            );
                        })
                        .list()
        ).stream().filter(Objects::nonNull).toList();
    }

    public Optional<PendingAction> getRunningAction() {
        return findPendingActions().stream().filter(PendingAction::running).findFirst();
    }

    public List<PendingAction> getQueuedActions() {
        return findPendingActions().stream().filter(a -> !a.running()).toList();
    }

    public void run() {
        try (Timer.Context ignored = statisticsTimer.time()) {
            statistics.set(collectStatistics());
        } catch (Exception e) {
            LOG.error("Could not collect database statistics.", e);
            return;
        }

        try {
            maintain();
        } catch (Exception e) {
            LOG.error("Could not run database maintenance.", e);
        }
    }

    public void onConfigurationChanged() {
        enqueue(new MaintenanceAction(ActionType.STORAGE_PARAMETERS, null, Initiator.MANUAL));
    }

    public DatabaseStatistics collectStatistics() {
        return nzyme.getDatabase().withHandle(handle -> {
            Map<String, Object> db = handle.createQuery("SELECT current_setting('server_version') AS version, " +
                            "current_setting('server_version_num')::int AS version_num, " +
                            "pg_database_size(current_database()) AS db_size, " +
                            "(SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database()) AS connections, " +
                            "current_setting('max_connections')::int AS max_connections, " +
                            "d.blks_hit, d.blks_read, d.temp_files, d.temp_bytes, d.deadlocks, d.tup_updated, " +
                            "d.blk_read_time, d.blk_write_time, d.stats_reset, age(p.datfrozenxid) AS xid_age, " +
                            "pg_postmaster_start_time() AS server_started_at, " +
                            "(SELECT COALESCE(EXTRACT(EPOCH FROM (NOW() - MIN(xact_start))), 0) " +
                            " FROM pg_stat_activity WHERE datname = current_database() AND xact_start IS NOT NULL " +
                            " AND backend_type = 'client backend') AS oldest_transaction_seconds " +
                            "FROM pg_stat_database d JOIN pg_database p ON p.datname = d.datname " +
                            "WHERE d.datname = current_database()")
                    .mapToMap()
                    .one();

            int versionNumber = ((Number) db.get("version_num")).intValue();

            Map<String, Object> totals = handle.createQuery("SELECT COALESCE(SUM(pg_relation_size(relid)), 0) AS heap, " +
                            "COALESCE(SUM(pg_indexes_size(relid)), 0) AS idx, COALESCE(SUM(n_tup_upd), 0) AS upd, " +
                            "COALESCE(SUM(n_tup_hot_upd), 0) AS hot FROM pg_stat_user_tables")
                    .mapToMap()
                    .one();

            Map<String, Object> checkpoints = versionNumber >= 170000
                    ? handle.createQuery("SELECT num_timed AS timed, num_requested AS requested FROM pg_stat_checkpointer")
                    .mapToMap().one()
                    : handle.createQuery("SELECT checkpoints_timed AS timed, checkpoints_req AS requested FROM pg_stat_bgwriter")
                    .mapToMap().one();

            CounterSample sample = new CounterSample(
                    DateTime.now(),
                    longOf(db.get("blks_hit")), longOf(db.get("blks_read")), longOf(db.get("temp_files")),
                    longOf(db.get("temp_bytes")), longOf(db.get("deadlocks")), longOf(totals.get("upd")),
                    longOf(totals.get("hot")), longOf(checkpoints.get("timed")), longOf(checkpoints.get("requested")),
                    doubleOf(db.get("blk_read_time")), doubleOf(db.get("blk_write_time"))
            );

            DateTime serverStartedAt = new DateTime((Timestamp) db.get("server_started_at"));

            CounterSample oldest;
            synchronized (samples) {
                // A PostgreSQL restart resets the counters. Start the window over.
                if (!samples.isEmpty() && serverStartedAt.isAfter(samples.getFirst().at())) {
                    samples.clear();
                }
                samples.addLast(sample);
                while (samples.size() > SAMPLE_WINDOW) {
                    samples.removeFirst();
                }
                oldest = samples.getFirst();
            }

            double minutes = Math.max(1.0, (sample.at().getMillis() - oldest.at().getMillis()) / 60_000.0);
            long hitDelta = sample.blksHit() - oldest.blksHit();
            long readDelta = sample.blksRead() - oldest.blksRead();
            long updDelta = sample.tupUpd() - oldest.tupUpd();
            long hotDelta = sample.tupHotUpd() - oldest.tupHotUpd();
            long cpTimedDelta = sample.checkpointsTimed() - oldest.checkpointsTimed();
            long cpReqDelta = sample.checkpointsRequested() - oldest.checkpointsRequested();
            long totalReads = sample.blksHit() + sample.blksRead();

            Timestamp statsReset = (Timestamp) db.get("stats_reset");

            Map<String, ServerSettingRecommendations.Setting> settingsMap = readSettings(handle);
            ServerSettingRecommendations.Setting trackIo = settingsMap.get("track_io_timing");
            boolean ioTimingEnabled = trackIo != null && "on".equalsIgnoreCase(trackIo.setting());

            ServerStatistics server = new ServerStatistics(
                    (String) db.get("version"),
                    versionNumber,
                    longOf(db.get("db_size")),
                    longOf(totals.get("heap")),
                    longOf(totals.get("idx")),
                    ((Number) db.get("connections")).intValue(),
                    ((Number) db.get("max_connections")).intValue(),
                    hitDelta + readDelta > 0 ? 100.0 * hitDelta / (hitDelta + readDelta) : -1,
                    totalReads > 0 ? 100.0 * sample.blksHit() / totalReads : -1,
                    Math.round((sample.tempBytes() - oldest.tempBytes()) / minutes),
                    (sample.tempFiles() - oldest.tempFiles()) / minutes,
                    cpTimedDelta + cpReqDelta > 0 ? 100.0 * cpReqDelta / (cpTimedDelta + cpReqDelta) : -1,
                    cpTimedDelta + cpReqDelta,
                    (sample.deadlocks() - oldest.deadlocks()) / minutes,
                    updDelta > 0 ? 100.0 * hotDelta / updDelta : -1,
                    longOf(db.get("xid_age")),
                    Math.round(((Number) db.get("oldest_transaction_seconds")).doubleValue()),
                    ioTimingEnabled,
                    (sample.blkReadTime() - oldest.blkReadTime()) / minutes,
                    (sample.blkWriteTime() - oldest.blkWriteTime()) / minutes,
                    statsReset == null ? null : new DateTime(statsReset),
                    serverStartedAt,
                    (int) Math.round(minutes)
            );

            List<ServerSetting> settings = ServerSettingRecommendations.evaluate(
                    new ServerSettingRecommendations.Input(
                            settingsMap,
                            versionNumber,
                            (String) db.get("version"),
                            databaseHostMemory(),
                            isSessionTuningEnabled(),
                            sessionWorkMemMb(),
                            isTableTuningEnabled()
                    )
            );

            List<TableStatistics> tables = handle.createQuery("SELECT relname, pg_relation_size(relid) AS heap, " +
                            "pg_indexes_size(relid) AS idx, pg_total_relation_size(relid) AS total, n_live_tup, " +
                            "n_dead_tup, GREATEST(last_vacuum, last_autovacuum) AS last_vacuum, " +
                            "GREATEST(last_analyze, last_autoanalyze) AS last_analyze, n_tup_ins, n_tup_upd, " +
                            "n_tup_hot_upd, n_tup_del, vacuum_count + autovacuum_count AS vacuum_count " +
                            "FROM pg_stat_user_tables WHERE schemaname = 'public' ORDER BY total DESC")
                    .map((rs, ctx) -> {
                        long live = rs.getLong("n_live_tup");
                        long dead = rs.getLong("n_dead_tup");
                        Timestamp lastVacuum = rs.getTimestamp("last_vacuum");
                        Timestamp lastAnalyze = rs.getTimestamp("last_analyze");
                        return new TableStatistics(
                                rs.getString("relname"),
                                TableClass.ofTable(rs.getString("relname")).orElse(null),
                                rs.getLong("heap"),
                                rs.getLong("idx"),
                                rs.getLong("total"),
                                live,
                                dead,
                                live + dead > 0 ? 100.0 * dead / (live + dead) : 0,
                                lastVacuum == null ? null : new DateTime(lastVacuum),
                                lastAnalyze == null ? null : new DateTime(lastAnalyze),
                                rs.getLong("n_tup_ins"),
                                rs.getLong("n_tup_upd"),
                                rs.getLong("n_tup_hot_upd"),
                                rs.getLong("n_tup_del"),
                                rs.getLong("vacuum_count")
                        );
                    })
                    .list();

            Map<String, double[]> bloat = Maps.newHashMap();
            try {
                handle.createQuery(BTREE_BLOAT_SQL)
                        .map((rs, ctx) -> {
                            bloat.put(rs.getString("idxname"),
                                    new double[]{rs.getDouble("bloat_pct"), rs.getDouble("bloat_size")});
                            return null;
                        })
                        .list();
            } catch (Exception e) {
                LOG.warn("Could not estimate index bloat.", e);
            }

            List<IndexStatistics> indexes = handle.createQuery("SELECT s.relname AS tbl, s.indexrelname AS idx, " +
                            "pg_relation_size(s.indexrelid) AS size, s.idx_scan, i.indisprimary, i.indisunique, i.indisvalid " +
                            "FROM pg_stat_user_indexes s JOIN pg_index i ON i.indexrelid = s.indexrelid " +
                            "WHERE s.schemaname = 'public' AND pg_relation_size(s.indexrelid) >= 1048576 " +
                            "ORDER BY size DESC")
                    .map((rs, ctx) -> {
                        double[] b = bloat.get(rs.getString("idx"));
                        return new IndexStatistics(
                                rs.getString("idx"),
                                rs.getString("tbl"),
                                rs.getLong("size"),
                                rs.getLong("idx_scan"),
                                rs.getBoolean("indisprimary"),
                                rs.getBoolean("indisunique"),
                                rs.getBoolean("indisvalid"),
                                b == null ? null : Math.max(0, b[0]),
                                b == null ? 0 : Math.max(0, Math.round(b[1]))
                        );
                    })
                    .list();

            return new DatabaseStatistics(DateTime.now(), server, settings, tables, indexes);
        });
    }

    private Map<String, ServerSettingRecommendations.Setting> readSettings(Handle handle) {
        Map<String, ServerSettingRecommendations.Setting> settings = Maps.newHashMap();
        handle.createQuery("SELECT name, setting, unit, source, pending_restart FROM pg_settings " +
                        "WHERE name IN (<names>)")
                .bindList("names", ServerSettingRecommendations.SETTING_NAMES)
                .map((rs, ctx) -> {
                    settings.put(rs.getString("name"), new ServerSettingRecommendations.Setting(
                            rs.getString("name"), rs.getString("setting"), rs.getString("unit"), rs.getString("source"),
                            rs.getBoolean("pending_restart")
                    ));
                    return null;
                })
                .list();

        return settings;
    }

    // Only known when the database runs on this node.
    private OptionalLong databaseHostMemory() {
        try {
            String path = nzyme.getConfiguration().databasePath();
            String host = URI.create(path.startsWith("postgresql://") ? path : "postgresql://" + path).getHost();
            if (host == null || host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")) {
                return OptionalLong.of(new NodeInformation().collect().memoryTotal());
            }
        } catch (Exception e) {
            LOG.debug("Could not determine database host.", e);
        }

        return OptionalLong.empty();
    }

    private void maintain() {
        if (!storageParametersChecked) {
            enqueue(new MaintenanceAction(ActionType.STORAGE_PARAMETERS, null, Initiator.STARTUP));
            storageParametersChecked = true;
        }

        DatabaseStatistics stats = statistics.get();
        if (stats == null || !findPendingActions().isEmpty()) {
            return;
        }

        DateTime now = DateTime.now();

        DatabaseUsage.Snapshot snapshot = usage.getSnapshot();
        if (snapshot.estimated() || snapshot.calculatedAt().isBefore(now.minusMinutes(USAGE_INTERVAL_MINUTES))) {
            enqueue(new MaintenanceAction(ActionType.USAGE_STATISTICS, null, Initiator.AUTOMATIC));
            return;
        }

        if (isAutoVacuumEnabled()) {
            for (TableStatistics t : stats.tables()) {
                if (t.totalBytes() < AUTO_VACUUM_MINIMUM_TABLE_BYTES
                        || t.deadRatioPercent() < AUTO_VACUUM_DEAD_RATIO_PERCENT) {
                    continue;
                }

                if (t.lastVacuum() != null && t.lastVacuum().isAfter(now.minusMinutes(AUTO_VACUUM_MINIMUM_LAG_MINUTES))) {
                    continue;
                }

                if (ranRecently(ActionType.VACUUM_TABLE, t.name(), now.minusMinutes(VACUUM_COOLDOWN_MINUTES))) {
                    continue;
                }

                LOG.info("Table [{}] has <{}%> dead rows and was last vacuumed at [{}]. Vacuuming.",
                        t.name(), Math.round(t.deadRatioPercent()), t.lastVacuum());
                enqueue(new MaintenanceAction(ActionType.VACUUM_TABLE, t.name(), Initiator.AUTOMATIC));
                return;
            }
        }

        if (isAutoReindexEnabled() && isInMaintenanceWindow(now)) {
            long minimumBytes = reindexMinimumIndexMb() * 1024L * 1024L;
            int threshold = reindexBloatThresholdPercent();

            for (IndexStatistics i : stats.indexes()) {
                if (!i.valid() || i.bytes() < minimumBytes || i.bloatPercent() == null || i.bloatPercent() < threshold) {
                    continue;
                }

                // A rebuild that did not shrink the index means the estimate is off for it. Back off for a week.
                if (ranRecently(ActionType.REINDEX_INDEX, i.name(), now.minusHours(REINDEX_COOLDOWN_HOURS))
                        || lastReindexWasIneffective(i.name(), now.minusDays(INEFFECTIVE_REINDEX_COOLDOWN_DAYS))) {
                    continue;
                }

                LOG.info("Index [{}] of table [{}] is estimated <{}%> bloated. Rebuilding.",
                        i.name(), i.table(), Math.round(i.bloatPercent()));
                enqueue(new MaintenanceAction(ActionType.REINDEX_INDEX, i.name(), Initiator.AUTOMATIC));
                return;
            }
        }
    }

    public boolean isInMaintenanceWindow(DateTime now) {
        return isInMaintenanceWindow(now, maintenanceWindowStartHour(), maintenanceWindowHours());
    }

    // May wrap around midnight.
    static boolean isInMaintenanceWindow(DateTime now, int startHour, int hours) {
        int hour = now.withZone(DateTimeZone.UTC).getHourOfDay();

        return ((hour - startHour) % 24 + 24) % 24 < hours;
    }

    public enum RequestResult {
        QUEUED, UNKNOWN_TARGET, ALREADY_PENDING, TOO_SOON
    }

    public RequestResult requestVacuum(String table) {
        if (!tableExists(table)) {
            return RequestResult.UNKNOWN_TARGET;
        }

        return enqueue(new MaintenanceAction(ActionType.VACUUM_TABLE, table, Initiator.MANUAL))
                ? RequestResult.QUEUED : RequestResult.ALREADY_PENDING;
    }

    public RequestResult requestReindexTable(String table) {
        if (!tableExists(table)) {
            return RequestResult.UNKNOWN_TARGET;
        }

        return enqueue(new MaintenanceAction(ActionType.REINDEX_TABLE, table, Initiator.MANUAL))
                ? RequestResult.QUEUED : RequestResult.ALREADY_PENDING;
    }

    public RequestResult requestReindexIndex(String index) {
        if (!indexExists(index)) {
            return RequestResult.UNKNOWN_TARGET;
        }

        return enqueue(new MaintenanceAction(ActionType.REINDEX_INDEX, index, Initiator.MANUAL))
                ? RequestResult.QUEUED : RequestResult.ALREADY_PENDING;
    }

    private boolean tableExists(String table) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = :name")
                        .bind("name", table)
                        .mapTo(Long.class)
                        .one()) > 0;
    }

    private boolean indexExists(String index) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = :name")
                        .bind("name", index)
                        .mapTo(Long.class)
                        .one()) > 0;
    }

    private boolean enqueue(MaintenanceAction action) {
        for (PendingAction pending : findPendingActions()) {
            if (pending.action().type() == action.type() && Objects.equals(pending.action().target(), action.target())) {
                return false;
            }
        }

        nzyme.getTasksQueue().publish(new DatabaseMaintenanceTask(action));
        return true;
    }

    // Cluster-wide advisory lock. Waiting is bounded so a stuck run on another node cannot block this node's
    // tasks poller forever; the task fails and the queue retries it.
    public boolean execute(MaintenanceAction action) {
        return nzyme.getDatabase().withHandle(handle -> {
            if (!acquireLock(handle)) {
                LOG.warn("Could not acquire database maintenance lock within {} minutes. Another node is still " +
                        "running maintenance. Retrying [{}] on [{}] later.", LOCK_WAIT_MINUTES, action.type(), action.target());
                return false;
            }

            try {
                // Two nodes can queue the same automatic action within the same minute. The second one is dropped
                // here, after the first one has finished and is in the log.
                if (action.initiator() == Initiator.AUTOMATIC && action.target() != null) {
                    DateTime cooldown = action.type() == ActionType.VACUUM_TABLE
                            ? DateTime.now().minusMinutes(VACUUM_COOLDOWN_MINUTES)
                            : DateTime.now().minusHours(REINDEX_COOLDOWN_HOURS);
                    if (ranRecently(action.type(), action.target(), cooldown)) {
                        LOG.info("Skipping duplicate automatic [{}] on [{}].", action.type(), action.target());
                        return true;
                    }
                }

                return switch (action.type()) {
                    case STORAGE_PARAMETERS -> applyStorageParameters(handle, action.initiator());
                    case VACUUM_TABLE -> runLogged(handle, action, "VACUUM (ANALYZE) " + quote(action.target()));
                    case REINDEX_TABLE -> {
                        dropInvalidIndexes(handle, action.target(), null);
                        yield runLogged(handle, action, "REINDEX TABLE CONCURRENTLY " + quote(action.target()));
                    }
                    case REINDEX_INDEX -> {
                        dropInvalidIndexes(handle, null, action.target());
                        yield runLogged(handle, action, "REINDEX INDEX CONCURRENTLY " + quote(action.target()));
                    }
                    case USAGE_STATISTICS -> calculateUsage(handle, action);
                };
            } finally {
                handle.createQuery("SELECT pg_advisory_unlock(:key)")
                        .bind("key", ADVISORY_LOCK_KEY)
                        .mapTo(Boolean.class)
                        .one();
            }
        });
    }

    private boolean acquireLock(Handle handle) {
        for (int i = 0; i < LOCK_WAIT_MINUTES * 12; i++) {
            boolean locked = handle.createQuery("SELECT pg_try_advisory_lock(:key)")
                    .bind("key", ADVISORY_LOCK_KEY)
                    .mapTo(Boolean.class)
                    .one();
            if (locked) {
                return true;
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    /*
     * A REINDEX CONCURRENTLY that fails or is interrupted leaves an invalid "<index>_ccnew" index behind. PostgreSQL
     * maintains it on every write and REINDEX CONCURRENTLY refuses to rebuild it, so it is dropped before the
     * next attempt. Either the table or the index of the attempt is given.
     */
    private void dropInvalidIndexes(Handle handle, @Nullable String table, @Nullable String index) {
        List<String> invalid = handle.createQuery("SELECT c.relname FROM pg_index i " +
                        "JOIN pg_class c ON c.oid = i.indexrelid " +
                        "JOIN pg_class t ON t.oid = i.indrelid " +
                        "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                        "WHERE n.nspname = 'public' AND NOT i.indisvalid AND c.relname LIKE '%\\_ccnew%' " +
                        "AND (:table IS NULL OR t.relname = :table) " +
                        "AND (:index IS NULL OR c.relname LIKE :index_pattern)")
                .bind("table", table)
                .bind("index", index)
                .bind("index_pattern", index == null ? null : index.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "\\_ccnew%")
                .mapTo(String.class)
                .list();

        for (String name : invalid) {
            try {
                handle.execute("DROP INDEX CONCURRENTLY IF EXISTS " + quote(name));
                LOG.warn("Dropped invalid index [{}] left behind by an interrupted rebuild.", name);
            } catch (Exception e) {
                LOG.error("Could not drop invalid index [{}].", name, e);
            }
        }
    }

    private boolean ranRecently(ActionType type, String target, DateTime since) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM database_maintenance_log WHERE action = :action " +
                                "AND target = :target AND started_at > :since")
                        .bind("action", type.name())
                        .bind("target", target)
                        .bind("since", since)
                        .mapTo(Long.class)
                        .one()) > 0;
    }

    private boolean lastReindexWasIneffective(String index, DateTime since) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT size_before_bytes, size_after_bytes FROM database_maintenance_log " +
                                "WHERE action = :action AND target = :target AND success = TRUE AND started_at > :since " +
                                "ORDER BY started_at DESC LIMIT 1")
                        .bind("action", ActionType.REINDEX_INDEX.name())
                        .bind("target", index)
                        .bind("since", since)
                        .map((rs, ctx) -> {
                            long before = rs.getLong("size_before_bytes");
                            long after = rs.getLong("size_after_bytes");
                            return !rs.wasNull() && before > 0 && after >= before * 0.9;
                        })
                        .findOne()
                        .orElse(false));
    }

    private boolean runLogged(Handle handle, MaintenanceAction action, String sql) {
        long logId = logStart(handle, action, null);
        DateTime started = DateTime.now();

        Long sizeBefore = relationSize(handle, action.target(), action.type());

        try {
            // VACUUM and REINDEX CONCURRENTLY cannot run inside a transaction block. The handle is in auto-commit mode.
            handle.execute(sql);

            Long sizeAfter = relationSize(handle, action.target(), action.type());
            String details = "Finished in " + (DateTime.now().getMillis() - started.getMillis()) + " ms.";
            if (sizeBefore != null && sizeAfter != null) {
                details += " Size before: " + ServerSettingRecommendations.human(sizeBefore)
                        + ", after: " + ServerSettingRecommendations.human(sizeAfter) + ".";
            }

            logFinish(handle, logId, true, details, sizeBefore, sizeAfter);
            LOG.info("Database maintenance [{}] on [{}] completed. {}", action.type(), action.target(), details);
            return true;
        } catch (Exception e) {
            logFinish(handle, logId, false, "Failed: " + e.getMessage());
            LOG.error("Database maintenance [{}] on [{}] failed.", action.type(), action.target(), e);
            return false;
        }
    }

    private boolean calculateUsage(Handle handle, MaintenanceAction action) {
        long logId = logStart(handle, action, null);
        DateTime started = DateTime.now();

        try (Timer.Context ignored = usageCalculationTimer.time()) {
            int entries = usage.calculate(handle);
            logFinish(handle, logId, true, "Counted " + entries + " category entries in "
                    + (DateTime.now().getMillis() - started.getMillis()) + " ms.");
            return true;
        } catch (Exception e) {
            logFinish(handle, logId, false, "Failed: " + e.getMessage());
            LOG.error("Could not calculate database usage.", e);
            return false;
        }
    }

    @Nullable
    private Long relationSize(Handle handle, String target, ActionType type) {
        try {
            String sql = type == ActionType.REINDEX_INDEX
                    ? "SELECT pg_relation_size(:target::regclass)"
                    : "SELECT pg_total_relation_size(:target::regclass)";
            return handle.createQuery(sql).bind("target", quote(target)).mapTo(Long.class).one();
        } catch (Exception e) {
            return null;
        }
    }

    public Map<TableClass, ClassParameters> getTableClassParameters() {
        Map<TableClass, ClassParameters> result = new EnumMap<>(TableClass.class);
        for (TableClass c : TableClass.values()) {
            result.put(c, new ClassParameters(
                    intValue(DatabaseMaintenanceRegistryKeys.vacuumScalePercent(c)),
                    intValue(DatabaseMaintenanceRegistryKeys.vacuumThreshold(c)),
                    intValue(DatabaseMaintenanceRegistryKeys.analyzeScalePercent(c)),
                    intValue(DatabaseMaintenanceRegistryKeys.fillfactor(c))
            ));
        }

        return result;
    }

    static Map<String, String> desiredStorageParameters(TableClass c, ClassParameters p) {
        Map<String, String> desired = new LinkedHashMap<>();
        desired.put("autovacuum_vacuum_scale_factor", formatFactor(p.vacuumScalePercent()));
        desired.put("autovacuum_vacuum_threshold", String.valueOf(p.vacuumThreshold()));
        desired.put("autovacuum_analyze_scale_factor", formatFactor(p.analyzeScalePercent()));
        desired.put("fillfactor", String.valueOf(p.fillfactor()));

        if (c.isDisableVacuumCostDelay()) {
            desired.put("autovacuum_vacuum_cost_delay", "0");
        }

        if (c == TableClass.APPEND) {
            desired.put("autovacuum_vacuum_insert_scale_factor", formatFactor(p.vacuumScalePercent()));
        }

        return desired;
    }

    private static String formatFactor(int percent) {
        return String.format(Locale.ROOT, "%.2f", percent / 100.0);
    }

    private boolean applyStorageParameters(Handle handle, Initiator initiator) {
        boolean enabled = isTableTuningEnabled();
        Map<String, Map<String, String>> actual = Maps.newHashMap();
        handle.createQuery("SELECT c.relname, c.reloptions FROM pg_class c " +
                        "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                        "WHERE n.nspname = 'public' AND c.relkind = 'r'")
                .map((rs, ctx) -> {
                    Map<String, String> options = Maps.newHashMap();
                    java.sql.Array array = rs.getArray("reloptions");
                    if (array != null) {
                        for (String option : (String[]) array.getArray()) {
                            int eq = option.indexOf('=');
                            if (eq > 0) {
                                options.put(option.substring(0, eq), option.substring(eq + 1));
                            }
                        }
                    }
                    actual.put(rs.getString("relname"), options);
                    return null;
                })
                .list();

        Map<TableClass, ClassParameters> parameters = getTableClassParameters();
        List<String> statements = Lists.newArrayList();
        List<String> changedTables = Lists.newArrayList();

        for (TableClass c : TableClass.values()) {
            for (String table : c.getTables()) {
                Map<String, String> current = actual.get(table);
                if (current == null) {
                    // Table does not exist in this installation.
                    continue;
                }

                Map<String, String> desired = enabled
                        ? desiredStorageParameters(c, parameters.get(c))
                        : Map.of();

                List<String> set = Lists.newArrayList();
                for (Map.Entry<String, String> d : desired.entrySet()) {
                    if (!valuesEqual(current.get(d.getKey()), d.getValue())) {
                        set.add(d.getKey() + " = " + d.getValue());
                    }
                }

                List<String> reset = Lists.newArrayList();
                for (String managed : MANAGED_STORAGE_PARAMETERS) {
                    if (current.containsKey(managed) && !desired.containsKey(managed)) {
                        reset.add(managed);
                    }
                }

                if (!set.isEmpty()) {
                    statements.add("ALTER TABLE " + quote(table) + " SET (" + String.join(", ", set) + ")");
                }

                if (!reset.isEmpty()) {
                    statements.add("ALTER TABLE " + quote(table) + " RESET (" + String.join(", ", reset) + ")");
                }

                if (!set.isEmpty() || !reset.isEmpty()) {
                    changedTables.add(table);
                }
            }
        }

        if (statements.isEmpty()) {
            LOG.debug("Table storage parameters are up to date.");
            return true;
        }

        MaintenanceAction action = new MaintenanceAction(ActionType.STORAGE_PARAMETERS, null, initiator);
        long logId = logStart(handle, action, (enabled ? "Applying" : "Resetting") + " storage parameters of "
                + changedTables.size() + " tables.");

        try {
            // Do not wait forever for a table that is being vacuumed right now.
            handle.execute("SET lock_timeout = '30s'");
            for (String statement : statements) {
                handle.execute(statement);
            }
            handle.execute("RESET lock_timeout");

            logFinish(handle, logId, true, (enabled ? "Applied" : "Reset") + " storage parameters of "
                    + changedTables.size() + " tables: " + String.join(", ", changedTables));
            LOG.info("{} storage parameters of <{}> tables.", enabled ? "Applied" : "Reset", changedTables.size());
            return true;
        } catch (Exception e) {
            try {
                handle.execute("RESET lock_timeout");
            } catch (Exception ignored) { }

            logFinish(handle, logId, false, "Failed: " + e.getMessage());
            LOG.error("Could not apply table storage parameters.", e);
            return false;
        }
    }

    private static boolean valuesEqual(@Nullable String current, String desired) {
        if (current == null) {
            return false;
        }

        try {
            return Double.parseDouble(current) == Double.parseDouble(desired);
        } catch (NumberFormatException e) {
            return current.equals(desired);
        }
    }

    private long logStart(Handle handle, MaintenanceAction action, @Nullable String details) {
        return handle.createQuery("INSERT INTO database_maintenance_log(node_id, action, target, initiator, " +
                        "details, started_at) VALUES(:node_id, :action, :target, :initiator, :details, NOW()) " +
                        "RETURNING id")
                .bind("node_id", nzyme.getNodeInformation().id())
                .bind("action", action.type().name())
                .bind("target", action.target())
                .bind("initiator", action.initiator().name())
                .bind("details", details)
                .mapTo(Long.class)
                .one();
    }

    private void logFinish(Handle handle, long id, boolean success, String details) {
        logFinish(handle, id, success, details, null, null);
    }

    private void logFinish(Handle handle, long id, boolean success, String details,
                           @Nullable Long sizeBefore, @Nullable Long sizeAfter) {
        handle.createUpdate("UPDATE database_maintenance_log SET success = :success, details = :details, " +
                        "size_before_bytes = :size_before, size_after_bytes = :size_after, finished_at = NOW() " +
                        "WHERE id = :id")
                .bind("success", success)
                .bind("details", details)
                .bind("size_before", sizeBefore)
                .bind("size_after", sizeAfter)
                .bind("id", id)
                .execute();
    }

    public Optional<MaintenanceLogEntry> findLastLogEntry(ActionType type) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM database_maintenance_log WHERE action = :action " +
                                "AND finished_at IS NOT NULL ORDER BY started_at DESC LIMIT 1")
                        .bind("action", type.name())
                        .map(new MaintenanceLogEntryMapper())
                        .findOne()
        );
    }

    public List<MaintenanceLogEntry> findLog(int limit, int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM database_maintenance_log ORDER BY started_at DESC " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map(new MaintenanceLogEntryMapper())
                        .list()
        );
    }

    public long countLog() {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM database_maintenance_log")
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<GaugeHistogramBucket> findGaugeHistogram(String metricName, int hours) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT AVG(metric_value) AS average, MAX(metric_value) AS maximum, " +
                                "MIN(metric_value) AS minimum, SUM(metric_value) AS sum, " +
                                "date_trunc('minute', created_at) AS bucket " +
                                "FROM node_metrics_gauges WHERE metric_name = :metric_name " +
                                "AND created_at > :created_at GROUP BY bucket ORDER BY bucket DESC")
                        .bind("metric_name", metricName)
                        .bind("created_at", DateTime.now().minusHours(hours))
                        .mapTo(GaugeHistogramBucket.class)
                        .list()
        );
    }

    public void retentionCleanLog(DateTime olderThan) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM database_maintenance_log WHERE started_at < :cutoff")
                        .bind("cutoff", olderThan)
                        .execute()
        );
    }

    public boolean isTableTuningEnabled() {
        return boolValue(DatabaseMaintenanceRegistryKeys.TABLE_TUNING_ENABLED);
    }

    public boolean isAutoVacuumEnabled() {
        return boolValue(DatabaseMaintenanceRegistryKeys.AUTO_VACUUM_ENABLED);
    }

    public boolean isAutoReindexEnabled() {
        return boolValue(DatabaseMaintenanceRegistryKeys.AUTO_REINDEX_ENABLED);
    }

    public boolean isSessionTuningEnabled() {
        return boolValue(DatabaseMaintenanceRegistryKeys.SESSION_TUNING_ENABLED);
    }

    public int sessionWorkMemMb() {
        return intValue(DatabaseMaintenanceRegistryKeys.SESSION_WORK_MEM_MB);
    }

    public int reindexBloatThresholdPercent() {
        return intValue(DatabaseMaintenanceRegistryKeys.REINDEX_BLOAT_THRESHOLD_PERCENT);
    }

    public int reindexMinimumIndexMb() {
        return intValue(DatabaseMaintenanceRegistryKeys.REINDEX_MINIMUM_INDEX_MB);
    }

    public int maintenanceWindowStartHour() {
        return intValue(DatabaseMaintenanceRegistryKeys.MAINTENANCE_WINDOW_START_HOUR);
    }

    public int maintenanceWindowHours() {
        return intValue(DatabaseMaintenanceRegistryKeys.MAINTENANCE_WINDOW_HOURS);
    }

    public boolean boolValue(RegistryKey key) {
        return nzyme.getDatabaseCoreRegistry().getValue(key.key())
                .map(Boolean::parseBoolean)
                .orElse(Boolean.parseBoolean(key.defaultValue().orElse("false")));
    }

    public int intValue(RegistryKey key) {
        String value = nzyme.getDatabaseCoreRegistry().getValue(key.key()).orElse(key.defaultValue().orElse("0"));
        try {
            return new java.math.BigDecimal(value).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return Integer.parseInt(key.defaultValue().orElse("0"));
        }
    }

    private static long longOf(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static double doubleOf(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    // After https://github.com/ioguix/pgsql-bloat-estimation. Needs planner statistics, so indexes of tables that
    // were never analyzed are missing from the result.
    static final String BTREE_BLOAT_SQL = """
            SELECT idxname, tblname,
              CASE WHEN relpages > est_pages_ff THEN bs*(relpages-est_pages_ff) ELSE 0 END AS bloat_size,
              CASE WHEN relpages > 0 THEN 100 * (relpages-est_pages_ff)::float / relpages ELSE 0 END AS bloat_pct
            FROM (
              SELECT coalesce(1 + ceil(reltuples/floor((bs-pageopqdata-pagehdr)*fillfactor/(100*(4+nulldatahdrwidth)::float))), 0) AS est_pages_ff,
                     bs, tblname, idxname, relpages, fillfactor, is_na
              FROM (
                SELECT maxalign, bs, nspname, tblname, idxname, reltuples, relpages, idxoid, fillfactor,
                       ( index_tuple_hdr_bm + maxalign
                         - CASE WHEN index_tuple_hdr_bm%maxalign = 0 THEN maxalign ELSE index_tuple_hdr_bm%maxalign END
                         + nulldatawidth + maxalign
                         - CASE WHEN nulldatawidth = 0 THEN 0
                                WHEN nulldatawidth::integer%maxalign = 0 THEN maxalign
                                ELSE nulldatawidth::integer%maxalign END
                       )::numeric AS nulldatahdrwidth, pagehdr, pageopqdata, is_na
                FROM (
                  SELECT n.nspname, i.tblname, i.idxname, i.reltuples, i.relpages, i.idxoid, i.fillfactor,
                         current_setting('block_size')::numeric AS bs,
                         CASE WHEN version() ~ 'mingw32' OR version() ~ '64-bit|x86_64|ppc64|ia64|amd64' THEN 8 ELSE 4 END AS maxalign,
                         24 AS pagehdr,
                         16 AS pageopqdata,
                         CASE WHEN max(coalesce(s.null_frac,0)) = 0 THEN 8 ELSE 8 + (( 32 + 8 - 1 ) / 8) END AS index_tuple_hdr_bm,
                         sum( (1-coalesce(s.null_frac, 0)) * coalesce(s.avg_width, 1024)) AS nulldatawidth,
                         max( CASE WHEN i.atttypid = 'pg_catalog.name'::regtype THEN 1 ELSE 0 END ) > 0 AS is_na
                  FROM (
                    SELECT ct.relname AS tblname, ct.relnamespace, ic.idxname, ic.attpos, ic.indkey, ic.indkey[ic.attpos],
                           ic.reltuples, ic.relpages, ic.tbloid, ic.idxoid, ic.fillfactor,
                           coalesce(a1.attnum, a2.attnum) AS attnum,
                           coalesce(a1.attname, a2.attname) AS attname,
                           coalesce(a1.atttypid, a2.atttypid) AS atttypid,
                           CASE WHEN a1.attnum IS NULL THEN ic.idxname ELSE ct.relname END AS attrelname
                    FROM (
                      SELECT idxname, reltuples, relpages, tbloid, idxoid, fillfactor, indkey,
                             pg_catalog.generate_series(1,indnatts) AS attpos
                      FROM (
                        SELECT ci.relname AS idxname, ci.reltuples, ci.relpages, i.indrelid AS tbloid,
                               i.indexrelid AS idxoid,
                               coalesce(substring(array_to_string(ci.reloptions, ' ') from 'fillfactor=([0-9]+)')::smallint, 90) AS fillfactor,
                               i.indnatts,
                               pg_catalog.string_to_array(pg_catalog.textin(pg_catalog.int2vectorout(i.indkey)),' ')::int[] AS indkey
                        FROM pg_catalog.pg_index i
                        JOIN pg_catalog.pg_class ci ON ci.oid = i.indexrelid
                        WHERE ci.relam=(SELECT oid FROM pg_am WHERE amname = 'btree')
                          AND ci.relpages > 0
                      ) AS idx_data
                    ) AS ic
                    JOIN pg_catalog.pg_class ct ON ct.oid = ic.tbloid
                    LEFT JOIN pg_catalog.pg_attribute a1 ON ic.indkey[ic.attpos] <> 0 AND a1.attrelid = ic.tbloid AND a1.attnum = ic.indkey[ic.attpos]
                    LEFT JOIN pg_catalog.pg_attribute a2 ON ic.indkey[ic.attpos] = 0 AND a2.attrelid = ic.idxoid AND a2.attnum = ic.attpos
                  ) i
                  JOIN pg_catalog.pg_namespace n ON n.oid = i.relnamespace
                  JOIN pg_catalog.pg_stats s ON s.schemaname = n.nspname AND s.tablename = i.attrelname AND s.attname = i.attname
                  WHERE n.nspname = 'public'
                  GROUP BY 1,2,3,4,5,6,7,8,9,10,11
                ) AS rows_data_stats
              ) AS rows_hdr_pdg_stats
            ) AS relation_stats
            WHERE NOT is_na
            """;

}
