package app.nzyme.core.monitoring.health.indicators;

import app.nzyme.core.database.maintenance.DatabaseMaintenance;
import app.nzyme.core.database.maintenance.DatabaseStatistics;
import app.nzyme.core.database.maintenance.DatabaseStatistics.IndexStatistics;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.monitoring.health.Indicator;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;

import java.util.List;
import java.util.Optional;

public class DatabaseBloatIndicator extends Indicator {

    public static final int RED_BLOAT_PERCENT = 85;

    private final DatabaseMaintenance maintenance;

    public DatabaseBloatIndicator(DatabaseMaintenance maintenance) {
        this.maintenance = maintenance;
    }

    @Override
    protected IndicatorStatus doRun() {
        Optional<DatabaseStatistics> stats = maintenance.getStatistics();
        if (stats.isEmpty()) {
            return NO_RESULT;
        }

        List<IndexStatistics> overdue = maintenance.findOverdueRebuildCandidates(stats.get());
        if (overdue.isEmpty()) {
            return IndicatorStatus.green(this);
        }

        return overdue.stream().anyMatch(i -> i.bloatPercent() >= RED_BLOAT_PERCENT)
                ? IndicatorStatus.red(this) : IndicatorStatus.orange(this);
    }

    @Override
    public String getId() {
        return "db_bloat";
    }

    @Override
    public String getName() {
        return "Database Index Bloat";
    }

    @Override
    public SystemEventType getSystemEventType() {
        return SystemEventType.HEALTH_INDICATOR_DB_BLOAT_TOGGLED;
    }

}
