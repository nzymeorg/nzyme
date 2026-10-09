package app.nzyme.core.database;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.PoolStats;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.codahale.metrics.MetricRegistry.name;

public class PoolMetricsTracker implements IMetricsTracker {

    public static final String WAIT = "Wait";
    public static final String USAGE = "Usage";
    public static final String CONNECTION_CREATION = "ConnectionCreation";
    public static final String CONNECTION_TIMEOUT_RATE = "ConnectionTimeoutRate";
    public static final String TOTAL_CONNECTIONS = "TotalConnections";
    public static final String IDLE_CONNECTIONS = "IdleConnections";
    public static final String ACTIVE_CONNECTIONS = "ActiveConnections";
    public static final String PENDING_CONNECTIONS = "PendingConnections";
    public static final String MAX_CONNECTIONS = "MaxConnections";
    public static final String MIN_CONNECTIONS = "MinConnections";
    public static final String PEAK_ACTIVE_CONNECTIONS = "PeakActiveConnections";
    public static final String PEAK_PENDING_CONNECTIONS = "PeakPendingConnections";

    public static String metricName(String poolName, String metric) {
        return name(poolName, "pool", metric);
    }

    private final String poolName;
    private final PoolStats poolStats;
    private final MetricRegistry registry;

    private final Timer connectionObtainTimer;
    private final Histogram connectionUsage;
    private final Timer connectionCreation;
    private final Meter connectionTimeoutMeter;

    private final AtomicInteger inUse = new AtomicInteger(0);
    private final MinutePeak peakActive = new MinutePeak();
    private final MinutePeak peakPending = new MinutePeak();

    public PoolMetricsTracker(String poolName, PoolStats poolStats, MetricRegistry registry) {
        this.poolName = poolName;
        this.poolStats = poolStats;
        this.registry = registry;

        this.connectionObtainTimer = registry.timer(metricName(poolName, WAIT));
        this.connectionUsage = registry.histogram(metricName(poolName, USAGE));
        this.connectionCreation = registry.timer(metricName(poolName, CONNECTION_CREATION));
        this.connectionTimeoutMeter = registry.meter(metricName(poolName, CONNECTION_TIMEOUT_RATE));

        registry.register(metricName(poolName, TOTAL_CONNECTIONS), (Gauge<Integer>) poolStats::getTotalConnections);
        registry.register(metricName(poolName, IDLE_CONNECTIONS), (Gauge<Integer>) poolStats::getIdleConnections);
        registry.register(metricName(poolName, ACTIVE_CONNECTIONS), (Gauge<Integer>) poolStats::getActiveConnections);
        registry.register(metricName(poolName, PENDING_CONNECTIONS), (Gauge<Integer>) poolStats::getPendingThreads);
        registry.register(metricName(poolName, MAX_CONNECTIONS), (Gauge<Integer>) poolStats::getMaxConnections);
        registry.register(metricName(poolName, MIN_CONNECTIONS), (Gauge<Integer>) poolStats::getMinConnections);
        registry.register(metricName(poolName, PEAK_ACTIVE_CONNECTIONS), (Gauge<Integer>) peakActive::lastCompletedMinute);
        registry.register(metricName(poolName, PEAK_PENDING_CONNECTIONS), (Gauge<Integer>) peakPending::lastCompletedMinute);
    }

    @Override
    public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
        connectionObtainTimer.update(elapsedAcquiredNanos, TimeUnit.NANOSECONDS);

        // Exact count of connections handed out and not yet returned.
        peakActive.record(inUse.incrementAndGet());

        // Threads are only waiting while the pool is exhausted, which is when acquisitions happen back-to-back.
        peakPending.record(poolStats.getPendingThreads());
    }

    @Override
    public void recordConnectionUsageMillis(long elapsedBorrowedMillis) {
        connectionUsage.update(elapsedBorrowedMillis);
        inUse.updateAndGet(v -> Math.max(0, v - 1));
    }

    @Override
    public void recordConnectionCreatedMillis(long connectionCreatedMillis) {
        connectionCreation.update(connectionCreatedMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordConnectionTimeout() {
        connectionTimeoutMeter.mark();
        peakPending.record(poolStats.getPendingThreads());
    }

    @Override
    public void close() {
        registry.removeMatching((name, metric) -> name.startsWith(metricName(poolName, "")));
    }

    static class MinutePeak {

        private long window = -1;
        private int current = 0;
        private int previous = 0;

        private static long currentWindow() {
            return System.currentTimeMillis() / 60_000;
        }

        synchronized void record(int value) {
            roll(currentWindow());

            if (value > current) {
                current = value;
            }
        }

        synchronized int lastCompletedMinute() {
            long now = currentWindow();

            if (now == window) {
                return previous;
            }

            if (now == window + 1) {
                return current;
            }

            // No activity in the last completed minute.
            return 0;
        }

        private void roll(long now) {
            if (now == window) {
                return;
            }

            previous = (now == window + 1) ? current : 0;
            window = now;
            current = 0;
        }

    }

}
