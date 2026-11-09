package app.nzyme.core.monitoring.health.indicators;

import app.nzyme.core.database.maintenance.DatabaseMaintenance;
import app.nzyme.core.database.maintenance.DatabaseStatistics;
import app.nzyme.core.database.maintenance.DatabaseStatistics.ServerStatistics;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.monitoring.health.Indicator;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;

import java.util.Optional;

public class DatabasePerformanceIndicator extends Indicator {

    public static final int ORANGE_CACHE_HIT_PERCENT = 90;
    public static final int RED_CACHE_HIT_PERCENT = 70;
    public static final long ORANGE_TEMP_BYTES_PER_MINUTE = 100L * 1024L * 1024L;
    public static final int ORANGE_REQUESTED_CHECKPOINT_PERCENT = 50;
    public static final int MINIMUM_CHECKPOINTS = 3;
    public static final double ORANGE_IO_WAIT_MS_PER_MINUTE = 30_000;

    private final DatabaseMaintenance maintenance;

    public DatabasePerformanceIndicator(DatabaseMaintenance maintenance) {
        this.maintenance = maintenance;
    }

    @Override
    protected IndicatorStatus doRun() {
        Optional<DatabaseStatistics> stats = maintenance.getStatistics();
        if (stats.isEmpty()) {
            return NO_RESULT;
        }

        ServerStatistics s = stats.get().server();

        // This node has too few samples since it started. The server-side warm-up is the same on every node.
        if (s.windowMinutes() < 5) {
            return NO_RESULT;
        }

        if (s.isWarmingUp()) {
            return IndicatorStatus.unavailable(this);
        }

        if (s.cacheHitRatioRecent() >= 0 && s.cacheHitRatioRecent() < RED_CACHE_HIT_PERCENT) {
            return IndicatorStatus.red(this);
        }

        boolean orange = s.cacheHitRatioRecent() >= 0 && s.cacheHitRatioRecent() < ORANGE_CACHE_HIT_PERCENT;
        orange |= s.tempBytesPerMinute() >= ORANGE_TEMP_BYTES_PER_MINUTE;
        orange |= s.checkpointsRecent() >= MINIMUM_CHECKPOINTS
                && s.checkpointsRequestedRatioRecent() >= ORANGE_REQUESTED_CHECKPOINT_PERCENT;
        orange |= s.ioTimingEnabled()
                && s.ioReadWaitMsPerMinute() + s.ioWriteWaitMsPerMinute() >= ORANGE_IO_WAIT_MS_PER_MINUTE;

        return orange ? IndicatorStatus.orange(this) : IndicatorStatus.green(this);
    }

    @Override
    public String getId() {
        return "db_performance";
    }

    @Override
    public String getName() {
        return "Database Performance";
    }

    @Override
    public SystemEventType getSystemEventType() {
        return SystemEventType.HEALTH_INDICATOR_DB_PERFORMANCE_TOGGLED;
    }

}
