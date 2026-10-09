package app.nzyme.core.distributed;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.crypto.pgp.PGPKeys;
import app.nzyme.core.security.NodeFilePermissions;
import app.nzyme.core.distributed.database.NodeEntry;
import app.nzyme.core.distributed.database.metrics.GaugeHistogramBucket;
import app.nzyme.core.logging.CountingAppender;
import app.nzyme.core.monitoring.GaugeEntryAverage;
import app.nzyme.core.monitoring.TimerEntry;
import app.nzyme.core.monitoring.TimerEntryAverage;
import app.nzyme.core.taps.db.metrics.BucketSize;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.PeriodType;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class NodeManager {

    private static final Logger LOG = LogManager.getLogger(NodeManager.class);

    private final NzymeNode nzyme;

    private UUID localNodeId;
    private long localCycle;

    private final NodeInformation nodeInformation;

    private final AtomicLong tapReportSize;

    private final NodeFilePermissions filePermissions;
    private int previousFilePermissionIssues = 0;

    private final LoadingCache<UUID, String> nodeNameCache;

    public NodeManager(NzymeNode nzyme) {
        this.nzyme = nzyme;

        this.nodeInformation = new NodeInformation();

        this.tapReportSize = new AtomicLong(0);
        this.filePermissions = new NodeFilePermissions(nzyme.getConfiguration());
        this.nodeNameCache = CacheBuilder.newBuilder().
                expireAfterAccess(10, TimeUnit.SECONDS)
                .build(new CacheLoader<>() {
                    @Override
                    public String load(@NotNull UUID nodeId) {
                        String nodeName = nzyme.getDatabase().withHandle(handle ->
                            handle.createQuery("SELECT name FROM nodes WHERE uuid = :uuid")
                                    .bind("uuid", nodeId)
                                    .mapTo(String.class)
                                    .one()
                        );

                        if (nodeName == null) {
                            return "[invalid node]";
                        }

                        return nodeName;
                    }
                });
    }

    public void initialize() throws NodeInitializationException {
        // Read local node id.
        Path nodeIdFile = Path.of(nzyme.getDataDirectory().toString(), "node_id");
        if (Files.exists(nodeIdFile)) {
            try {
                LOG.debug("Node ID file exists at [{}]", nodeIdFile.toAbsolutePath());
                localNodeId = UUID.fromString(Files.readString(nodeIdFile));
            } catch (IOException e) {
                throw new NodeInitializationException("Could not read node ID file at [" + nodeIdFile.toAbsolutePath() + "]", e);
            }
        } else {
            LOG.debug("Node ID file does not exist at [{}]. Creating.", nodeIdFile.toAbsolutePath());
            UUID newNodeId = UUID.randomUUID();

            try {
                Files.writeString(nodeIdFile, newNodeId.toString(), Charsets.UTF_8, StandardOpenOption.CREATE);
            } catch (IOException e) {
                throw new NodeInitializationException("Could not write node ID file at [" + nodeIdFile.toAbsolutePath() + "]", e);
            }

            localNodeId = newNodeId;
            LOG.info("Created node ID: [{}]", localNodeId);
        }

        // Check if a node with same name but different ID already exists. Not allowed.
        for (Node node : getNodes()) {
            if (node.uuid().equals(localNodeId)) {
                // Don't look at our own node if it's already registered.
                continue;
            }

            if (node.name().trim().equals(nzyme.getBaseConfiguration().name().trim())) {
                throw new NodeInitializationException("Node with same name already exists. Please choose another name.");
            }
        }

        // Increment cycle counter.
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE nodes SET cycle = cycle+1 WHERE uuid = :node_id")
                        .bind("node_id", localNodeId)
                        .execute()
        );

        // Get current cycle.
        localCycle = getCycleOfNode(localNodeId);

        Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder()
                        .setNameFormat("node-metrics-updater-%d")
                        .setDaemon(true)
                        .build()
        ).scheduleAtFixedRate(this::runMetrics, 0, 1, TimeUnit.MINUTES);

        LOG.info("Node ID: [{}]", localNodeId);
    }


    public void registerSelf() {
        if (localNodeId == null) {
            throw new RuntimeException("Not initialized. Cannot register myself.");
        }

        registerSelf(localNodeId);
    }

    // The custom UUID is for testing.
    public void registerSelf(UUID uuid) {
        NodeInformation.Info ni = nodeInformation.collect();

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO nodes(uuid, name, http_listen_uri, http_external_uri, version, " +
                                " last_seen, memory_bytes_total, memory_bytes_available, memory_bytes_used, " +
                                "heap_bytes_total, heap_bytes_available, heap_bytes_used, cpu_system_load, " +
                                "cpu_thread_count, process_start_time, process_virtual_size, process_arguments, " +
                                "os_information, clock, deleted) VALUES(:uuid, :name, :http_listen_uri, " +
                                ":http_external_uri, :version, NOW(), :memory_bytes_total, :memory_bytes_available, " +
                                ":memory_bytes_used, :heap_bytes_total, :heap_bytes_available, " +
                                ":heap_bytes_used, :cpu_system_load, :cpu_thread_count, :process_start_time, " +
                                ":process_virtual_size, :process_arguments, :os_information, :clock, false) " +
                                "ON CONFLICT(uuid) DO UPDATE SET name = :name, http_external_uri = :http_external_uri, " +
                                "http_listen_uri = :http_listen_uri, version = :version, last_seen = NOW()," +
                                "memory_bytes_total = :memory_bytes_total, " +
                                "memory_bytes_available = :memory_bytes_available, memory_bytes_used = :memory_bytes_used, " +
                                "heap_bytes_total = :heap_bytes_total, heap_bytes_available = :heap_bytes_available, " +
                                "heap_bytes_used = :heap_bytes_used, cpu_system_load = :cpu_system_load, " +
                                "cpu_thread_count = :cpu_thread_count, process_start_time = :process_start_time, " +
                                "process_virtual_size = :process_virtual_size, process_arguments = :process_arguments, " +
                                "os_information = :os_information, clock = :clock, deleted = false")
                        .bind("uuid", uuid)
                        .bind("name", nzyme.getNodeInformation().name())
                        .bind("http_listen_uri", nzyme.getConfiguration().restListenUri().toString())
                        .bind("http_external_uri", nzyme.getConfiguration().httpExternalUri().toString())
                        .bind("version", nzyme.getVersion().getVersion().toString())
                        .bind("memory_bytes_total", ni.memoryTotal())
                        .bind("memory_bytes_available", ni.memoryAvailable())
                        .bind("memory_bytes_used", ni.memoryUsed())
                        .bind("heap_bytes_total", ni.heapTotal())
                        .bind("heap_bytes_available", ni.heapAvailable())
                        .bind("heap_bytes_used", ni.heapUsed())
                        .bind("cpu_system_load", ni.cpuSystemLoad())
                        .bind("cpu_thread_count", ni.cpuThreadCount())
                        .bind("process_start_time", ni.processStartTime())
                        .bind("process_virtual_size", ni.processVirtualSize())
                        .bind("process_arguments", ni.processArguments())
                        .bind("os_information", ni.osInformation())
                        .bind("clock", DateTime.now())
                        .execute()
        );
    }

    public List<Node> getNodes() {
        List<NodeEntry> dbEntries = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM nodes WHERE last_seen > :timeout ORDER BY name DESC")
                        .bind("timeout", DateTime.now().minusHours(24))
                        .mapTo(NodeEntry.class)
                        .list()
        );

        List<Node> nodes = Lists.newArrayList();
        for (NodeEntry dbEntry : dbEntries) {
            try {
                URI listenUri = URI.create(dbEntry.httpListenUri());
                URI httpExternalUri = URI.create(dbEntry.httpExternalUri());
                nodes.add(Node.create(
                        dbEntry.uuid(),
                        dbEntry.name(),
                        listenUri,
                        httpExternalUri,
                        dbEntry.memoryBytesTotal(),
                        dbEntry.memoryBytesAvailable(),
                        dbEntry.memoryBytesUsed(),
                        dbEntry.heapBytesTotal(),
                        dbEntry.heapBytesAvailable(),
                        dbEntry.heapBytesUsed(),
                        dbEntry.cpuSystemLoad(),
                        dbEntry.cpuThreadCount(),
                        dbEntry.processStartTime(),
                        dbEntry.processVirtualSize(),
                        dbEntry.processArguments(),
                        dbEntry.osInformation(),
                        dbEntry.version(),
                        dbEntry.lastSeen(),
                        dbEntry.clock(),
                        (long) new Period(dbEntry.lastSeen(), dbEntry.clock(), PeriodType.millis()).getMillis(),
                        isNodeEphemeral(dbEntry),
                        dbEntry.deleted(),
                        dbEntry.cycle()
                ));
            } catch (Exception e) {
                LOG.error("Could not create node from database entry. Skipping.", e);
            }
        }

        return nodes;
    }

    public Optional<Node> getNode(UUID nodeId) {
        Optional<NodeEntry> result = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM nodes WHERE uuid = :uuid")
                        .bind("uuid", nodeId)
                        .mapTo(NodeEntry.class)
                        .findFirst()
        );

        if (result.isPresent()) {
            NodeEntry ne = result.get();
            try {
                URI httpListenUri = URI.create(ne.httpListenUri());
                URI httpExternalUri = URI.create(ne.httpExternalUri());
                return Optional.of(Node.create(
                        ne.uuid(),
                        ne.name(),
                        httpListenUri,
                        httpExternalUri,
                        ne.memoryBytesTotal(),
                        ne.memoryBytesAvailable(),
                        ne.memoryBytesUsed(),
                        ne.heapBytesTotal(),
                        ne.heapBytesAvailable(),
                        ne.heapBytesUsed(),
                        ne.cpuSystemLoad(),
                        ne.cpuThreadCount(),
                        ne.processStartTime(),
                        ne.processVirtualSize(),
                        ne.processArguments(),
                        ne.osInformation(),
                        ne.version(),
                        ne.lastSeen(),
                        ne.clock(),
                        (long) new Period(ne.lastSeen(), ne.clock(), PeriodType.millis()).getMillis(),
                        isNodeEphemeral(ne),
                        ne.deleted(),
                        ne.cycle()
                ));
            } catch (Exception e) {
                throw new RuntimeException("Could not create node from database entry.", e);
            }
        } else {
            return Optional.empty();
        }
    }

    public void deleteNode(UUID nodeId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE nodes SET deleted = true WHERE uuid = :node_id")
                        .bind("node_id", nodeId)
                        .execute()
        );
    }

    private void runMetrics() {
        try {
            MetricRegistry metrics = nzyme.getMetrics();
            long tapReportSize = this.tapReportSize.getAndSet(0);
            NodeInformation.Info ni = new NodeInformation().collect();
            Map<String, Long> logCounts = CountingAppender.getCounts();

            writeGauge(MetricExternalName.MEMORY_BYTES_TOTAL.database_label, ni.memoryTotal());
            writeGauge(MetricExternalName.MEMORY_BYTES_AVAILABLE.database_label, ni.memoryAvailable());
            writeGauge(MetricExternalName.MEMORY_BYTES_USED.database_label, ni.memoryUsed());
            writeGauge(MetricExternalName.HEAP_BYTES_TOTAL.database_label, ni.heapTotal());
            writeGauge(MetricExternalName.HEAP_BYTES_AVAILABLE.database_label, ni.heapAvailable());
            writeGauge(MetricExternalName.HEAP_BYTES_USED.database_label, ni.heapUsed());
            writeGauge(MetricExternalName.CPU_SYSTEM_LOAD.database_label, ni.cpuSystemLoad());
            writeGauge(MetricExternalName.PROCESS_VIRTUAL_SIZE.database_label, ni.processVirtualSize());
            writeGauge(MetricExternalName.TAP_REPORT_SIZE.database_label, tapReportSize);
            writeGauge(MetricExternalName.LOG_COUNTS_TRACE.database_label, logCounts.getOrDefault("TRACE", 0L));
            writeGauge(MetricExternalName.LOG_COUNTS_DEBUG.database_label, logCounts.getOrDefault("DEBUG", 0L));
            writeGauge(MetricExternalName.LOG_COUNTS_INFO.database_label, logCounts.getOrDefault("INFO", 0L));
            writeGauge(MetricExternalName.LOG_COUNTS_WARN.database_label, logCounts.getOrDefault("WARN", 0L));
            writeGauge(MetricExternalName.LOG_COUNTS_ERROR.database_label, logCounts.getOrDefault("ERROR", 0L));
            writeGauge(MetricExternalName.LOG_COUNTS_FATAL.database_label, logCounts.getOrDefault("FATAL", 0L));
            writeGauge(MetricExternalName.GEOIP_CACHE_SIZE.database_label, getLocalMetricsGaugeValue(metrics, MetricNames.GEOIP_CACHE_SIZE));
            writeGauge(MetricExternalName.CONTEXT_MAC_CACHE_SIZE.database_label, getLocalMetricsGaugeValue(metrics, MetricNames.CONTEXT_MAC_CACHE_SIZE));
            writeGauge(MetricExternalName.CONTEXT_NETWORK_CACHE_SIZE.database_label, getLocalMetricsGaugeValue(metrics, MetricNames.CONTEXT_NETWORK_CACHE_SIZE));

            // Files holding secrets that other users could read. Nodes refuse to start like this, but permissions can change at runtime.
            List<NodeFilePermissions.Problem> filePermissionProblems = filePermissions.check();
            if (filePermissionProblems.size() != previousFilePermissionIssues) {
                if (!filePermissionProblems.isEmpty()) {
                    LOG.warn(NodeFilePermissions.buildMessage(filePermissionProblems));
                } else {
                    LOG.info("File permission problems resolved.");
                }
                previousFilePermissionIssues = filePermissionProblems.size();
            }
            writeGauge(MetricExternalName.FILE_PERMISSION_ISSUES.database_label, filePermissionProblems.size());

            // Database connection pool. Active and pending are peaks of the last completed minute, see PoolMetricsTracker.
            writeGauge(MetricExternalName.DATABASE_POOL_ACTIVE_CONNECTIONS.database_label, getLocalMetricsGaugeValue(metrics, MetricNames.DATABASE_POOL_PEAK_ACTIVE_CONNECTIONS));
            writeGauge(MetricExternalName.DATABASE_POOL_IDLE_CONNECTIONS.database_label, getLocalMetricsGaugeValue(metrics, MetricNames.DATABASE_POOL_IDLE_CONNECTIONS));
            writeGauge(MetricExternalName.DATABASE_POOL_PENDING_CONNECTIONS.database_label, getLocalMetricsGaugeValue(metrics, MetricNames.DATABASE_POOL_PEAK_PENDING_CONNECTIONS));
            writeGauge(MetricExternalName.DATABASE_POOL_TOTAL_CONNECTIONS.database_label, getLocalMetricsGaugeValue(metrics, MetricNames.DATABASE_POOL_TOTAL_CONNECTIONS));
            writeGauge(MetricExternalName.DATABASE_POOL_MAX_CONNECTIONS.database_label, getLocalMetricsGaugeValue(metrics, MetricNames.DATABASE_POOL_MAX_CONNECTIONS));
            writeGauge(MetricExternalName.DATABASE_POOL_CONNECTION_TIMEOUTS.database_label, getLocalMetricsMeterCount(metrics, MetricNames.DATABASE_POOL_CONNECTION_TIMEOUT_RATE));
            writeTimer(MetricExternalName.DATABASE_POOL_WAIT_TIMER.database_label,
                    metrics.getTimers().get(MetricNames.DATABASE_POOL_WAIT_TIMER));
            writeMillisecondHistogramAsTimer(MetricExternalName.DATABASE_POOL_USAGE_TIMER.database_label,
                    metrics.getHistograms().get(MetricNames.DATABASE_POOL_USAGE_HISTOGRAM));

            writeTimer(MetricExternalName.PGP_ENCRYPTION_TIMER.database_label,
                    metrics.getTimers().get(MetricNames.PGP_ENCRYPTION_TIMING));
            writeTimer(MetricExternalName.PGP_DECRYPTION_TIMER.database_label,
                    metrics.getTimers().get(MetricNames.PGP_DECRYPTION_TIMING));
            writeTimer(MetricExternalName.PASSWORD_HASHING_TIMER.database_label,
                    metrics.getTimers().get(MetricNames.PASSWORD_HASHING_TIMER));
            writeTimer(MetricExternalName.CONTEXT_MAC_LOOKUP_TIMER.database_label,
                    metrics.getTimers().get(MetricNames.CONTEXT_MAC_LOOKUP_TIMING));

            writeTimer(MetricExternalName.REPORT_PROCESSING_DOT11_TIMER.database_label,
                    metrics.getTimers().get(MetricNames.DOT11_TOTAL_REPORT_PROCESSING_TIMER));
            writeTimer(MetricExternalName.REPORT_PROCESSING_TCP_TIMER.database_label,
                    metrics.getTimers().get(MetricNames.TCP_TOTAL_REPORT_PROCESSING_TIMER));
            writeTimer(MetricExternalName.REPORT_PROCESSING_DNS_TIMER.database_label,
                    metrics.getTimers().get(MetricNames.DNS_TOTAL_REPORT_PROCESSING_TIMER));
            writeTimer(MetricExternalName.REPORT_PROCESSING_SSH_TIMER.database_label,
                    metrics.getTimers().get(MetricNames.SSH_TOTAL_REPORT_PROCESSING_TIMER));
            writeTimer(MetricExternalName.REPORT_PROCESSING_SOCKS_TIMER.database_label,
                    metrics.getTimers().get(MetricNames.SOCKS_TOTAL_REPORT_PROCESSING_TIMER));

            // Write all report execution timers.
            for (Map.Entry<String, Timer> timer : metrics.getTimers().entrySet()) {
                if (timer.getKey().startsWith(MetricNames.MONITOR_EXECUTION_TIMER_BASE)) {
                    writeTimer(
                            "monitor_execution_" + timer.getKey().split(MetricNames.MONITOR_EXECUTION_TIMER_BASE)[1],
                            timer.getValue()
                    );
                }
            }

        } catch(Exception e) {
            LOG.error("Could not write node metrics.", e);
        } finally {
            CountingAppender.resetCounts();
        }

        // Retention clean old metrics.
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM node_metrics_gauges WHERE created_at < :created_at")
                        .bind("created_at", DateTime.now().minusHours(24))
                        .execute());
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM node_metrics_timers WHERE created_at < :created_at")
                        .bind("created_at", DateTime.now().minusHours(24))
                        .execute());
    }

    private double getLocalMetricsGaugeValue(MetricRegistry metrics, String metricName) {
        Gauge gauge = metrics.getGauges().get(metricName);

        /*
         * The gauge *should* always exist because it's tied to a metric name that we defined, but this may
         * be called before the gauge is initialized.
         */
        if (gauge == null) {
            return 0;
        }

        Object value = gauge.getValue();

        if (value == null) {
            return 0;
        }

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else {
            LOG.error("Unknown value type [{}] in gauge [{}]",
                    value.getClass().getCanonicalName(), metricName);
            return 0;
        }
    }

    private double getLocalMetricsMeterCount(MetricRegistry metrics, String metricName) {
        Meter meter = metrics.getMeters().get(metricName);

        // May be called before the meter is registered.
        return meter == null ? 0 : meter.getCount();
    }

    /*
     * Timers are stored in microseconds. This writes a histogram that measures milliseconds (like the HikariCP
     * connection usage histogram) in the same format, so it can be displayed like a timer.
     */
    private void writeMillisecondHistogramAsTimer(String metricName, @Nullable Histogram histogram) {
        if (histogram == null) {
            return;
        }

        Snapshot s = histogram.getSnapshot();
        writeTimer(
                metricName,
                TimeUnit.MICROSECONDS.convert(s.getMax(), TimeUnit.MILLISECONDS),
                TimeUnit.MICROSECONDS.convert(s.getMin(), TimeUnit.MILLISECONDS),
                TimeUnit.MICROSECONDS.convert((long) s.getMean(), TimeUnit.MILLISECONDS),
                TimeUnit.MICROSECONDS.convert((long) s.get99thPercentile(), TimeUnit.MILLISECONDS),
                TimeUnit.MICROSECONDS.convert((long) s.getStdDev(), TimeUnit.MILLISECONDS),
                histogram.getCount()
        );
    }

    private void writeGauge(String metricName, double metricValue) {
        nzyme.getDatabase().withHandle(handle -> handle.createUpdate("INSERT INTO node_metrics_gauges(node_id, " +
                        "metric_name, metric_value, created_at) VALUES(:node_id, :metric_name, :metric_value, " +
                        ":created_at)")
                .bind("node_id", nzyme.getNodeInformation().id())
                .bind("metric_name", metricName)
                .bind("metric_value", metricValue)
                .bind("created_at", DateTime.now())
                .execute()
        );
    }

    private void writeTimer(String metricName, @Nullable Timer timer) {
        if (timer == null) {
            return;
        }

        Snapshot s = timer.getSnapshot();
        writeTimer(
                metricName,
                TimeUnit.MICROSECONDS.convert(s.getMax(), TimeUnit.NANOSECONDS),
                TimeUnit.MICROSECONDS.convert(s.getMin(), TimeUnit.NANOSECONDS),
                TimeUnit.MICROSECONDS.convert((long) s.getMean(), TimeUnit.NANOSECONDS),
                TimeUnit.MICROSECONDS.convert((long) s.get99thPercentile(), TimeUnit.NANOSECONDS),
                TimeUnit.MICROSECONDS.convert((long) s.getStdDev(), TimeUnit.NANOSECONDS),
                timer.getCount()
        );
    }

    private void writeTimer(String metricName, long max, long min, long mean, long p99, long stddev, long counter) {
        nzyme.getDatabase().withHandle(handle -> handle.createUpdate("INSERT INTO node_metrics_timers(node_id, " +
                        "metric_name, metric_max, metric_min, metric_mean, metric_p99, metric_stddev, metric_counter, " +
                        "created_at) VALUES(:node_id, :metric_name, :metric_max, :metric_min, :metric_mean, " +
                        ":metric_p99, :metric_stddev, :metric_counter, :created_at)")
                .bind("node_id", nzyme.getNodeInformation().id())
                .bind("metric_name", metricName)
                .bind("metric_max", max)
                .bind("metric_min", min)
                .bind("metric_mean", mean)
                .bind("metric_p99", p99)
                .bind("metric_stddev", stddev)
                .bind("metric_counter", counter)
                .bind("created_at", DateTime.now())
                .execute()
        );
    }

    public Optional<Map<DateTime, GaugeHistogramBucket>> findMetricsHistogram(UUID nodeId, String metricName, int hours, BucketSize bucketSize) {
        Map<DateTime, GaugeHistogramBucket> result = Maps.newHashMap();

        List<GaugeHistogramBucket> agg = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT AVG(metric_value) AS average, MAX(metric_value) AS maximum, " +
                                "MIN(metric_value) AS minimum, SUM(metric_value) AS sum, " +
                                "date_trunc(:bucket_size, created_at) AS bucket " +
                                "FROM node_metrics_gauges WHERE node_id = :node_id AND metric_name = :metric_name " +
                                "AND created_at > :created_at GROUP BY bucket ORDER BY bucket DESC")
                        .bind("bucket_size", bucketSize.toString().toLowerCase())
                        .bind("node_id", nodeId)
                        .bind("metric_name", metricName)
                        .bind("created_at", DateTime.now().minusHours(hours))
                        .mapTo(GaugeHistogramBucket.class)
                        .list()
        );

        if (agg == null || agg.isEmpty()) {
            return Optional.empty();
        }

        for (GaugeHistogramBucket x : agg) {
            result.put(x.bucket(), x);
        }

        return Optional.of(result);
    }

    public Optional<TimerEntry> findLatestActiveMetricsTimerValue(UUID nodeId,
                                                                  String metricName,
                                                                  Handle handle) {
        return handle.createQuery("SELECT * FROM node_metrics_timers " +
                        "WHERE node_id = :node_id AND metric_name = :metric_name AND created_at > :created_at " +
                        "ORDER BY created_at DESC " +
                        "LIMIT 1")
                .bind("node_id", nodeId)
                .bind("metric_name", metricName)
                .bind("created_at", DateTime.now().minusMinutes(2))
                .mapTo(TimerEntry.class)
                .findOne();
    }

    public Optional<Double> findLatestActiveMetricsGaugeValue(UUID nodeId,
                                                              String metricName,
                                                              Handle handle) {
        return handle.createQuery("SELECT metric_value FROM node_metrics_gauges " +
                        "WHERE node_id = :node_id AND metric_name = :metric_name " +
                        "AND created_at > :created_at " +
                        "ORDER BY created_at DESC " +
                        "LIMIT 1")
                .bind("node_id", nodeId)
                .bind("metric_name", metricName)
                .bind("created_at", DateTime.now().minusMinutes(2))
                .mapTo(Double.class)
                .findOne();
    }

    public List<TimerEntryAverage> findAverageTimerValuesOfNode(UUID nodeId, DateTime since) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT metric_name AS name, AVG(metric_max) AS max, AVG(metric_min) AS min, " +
                                "AVG(metric_mean) AS mean, AVG(metric_p99) AS p99, AVG(metric_stddev) AS stddev, " +
                                "AVG(metric_counter) AS counter FROM node_metrics_timers " +
                                "WHERE node_id = :node_id AND created_at >= :since AND created_at <= NOW() " +
                                "GROUP BY metric_name")
                        .bind("node_id", nodeId)
                        .bind("since", since)
                        .mapTo(TimerEntryAverage.class)
                        .list()
        );
    }

    public List<GaugeEntryAverage> findAverageGaugeValuesOfNode(UUID nodeId, DateTime since) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT metric_name AS name, AVG(metric_value) AS value " +
                                "FROM node_metrics_gauges " +
                                "WHERE node_id = :node_id AND created_at >= :since AND created_at <= NOW() " +
                                "GROUP BY metric_name")
                        .bind("node_id", nodeId)
                        .bind("since", since)
                        .mapTo(GaugeEntryAverage.class)
                        .list()
        );
    }

    private boolean isNodeEphemeral(NodeEntry node) {
        return nzyme.getDatabaseCoreRegistry()
                .getValue(NodeRegistryKeys.EPHEMERAL_NODES_REGEX.key())
                .filter(r -> node.name().matches(r))
                .isPresent();
    }

    public UUID getLocalNodeId() {
        return localNodeId;
    }

    public void recordTapReportSize(long size) {
        this.tapReportSize.addAndGet(size);
    }

    public String findNameOfNode(UUID nodeId) {
        try {
            return nodeNameCache.get(nodeId);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public long getCycleOfNode(UUID nodeId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT cycle FROM nodes WHERE uuid = :node_id")
                        .bind("node_id", nodeId)
                        .mapTo(Long.class)
                        .findOne()
        ).orElse(1L); // 1 if this is a brand-new node that hasn't registered yet. (happens in init once)
    }

    public long getLocalCycle() {
        return localCycle;
    }

    public void setLocalPGPPublicKey(PGPKeys keys) {
        String key = BaseEncoding.base64().encode(keys.publicKey());
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE nodes SET public_key = :key WHERE uuid = :node_id")
                        .bind("key", key)
                        .bind("node_id", localNodeId)
                        .execute()
        );
    }

    public byte[] getPGPPublicKeyOfNode(UUID nodeId) {
        String b64 = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT public_key FROM nodes WHERE uuid = :node_id")
                        .bind("node_id", nodeId)
                        .mapTo(String.class)
                        .one()
        );

        return BaseEncoding.base64().decode(b64);
    }

    public static final class NodeInitializationException extends Throwable {

        public NodeInitializationException(String msg) {
            super(msg);
        }

        public NodeInitializationException(String msg, Throwable e) {
            super(msg, e);
        }

    }

}
