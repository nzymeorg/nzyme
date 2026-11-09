package app.nzyme.core.monitoring.health.indicators;

import app.nzyme.core.database.maintenance.DatabaseMaintenance;
import app.nzyme.core.database.maintenance.DatabaseStatistics;
import app.nzyme.core.database.maintenance.DatabaseStatistics.TableStatistics;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.monitoring.health.Indicator;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;

import java.util.Optional;

public class DatabaseVacuumIndicator extends Indicator {

    public static final long MINIMUM_TABLE_BYTES = 100L * 1024L * 1024L;
    public static final int ORANGE_DEAD_PERCENT = 20;
    public static final int RED_DEAD_PERCENT = 50;

    private final DatabaseMaintenance maintenance;

    public DatabaseVacuumIndicator(DatabaseMaintenance maintenance) {
        this.maintenance = maintenance;
    }

    @Override
    protected IndicatorStatus doRun() {
        Optional<DatabaseStatistics> stats = maintenance.getStatistics();
        if (stats.isEmpty()) {
            return NO_RESULT;
        }

        boolean orange = false;
        for (TableStatistics t : stats.get().tables()) {
            if (t.totalBytes() < MINIMUM_TABLE_BYTES) {
                continue;
            }

            if (t.deadRatioPercent() >= RED_DEAD_PERCENT) {
                return IndicatorStatus.red(this);
            }

            if (t.deadRatioPercent() >= ORANGE_DEAD_PERCENT) {
                orange = true;
            }
        }

        return orange ? IndicatorStatus.orange(this) : IndicatorStatus.green(this);
    }

    @Override
    public String getId() {
        return "db_vacuum";
    }

    @Override
    public String getName() {
        return "Database Vacuum";
    }

    @Override
    public SystemEventType getSystemEventType() {
        return SystemEventType.HEALTH_INDICATOR_DB_VACUUM_TOGGLED;
    }

}
