package app.nzyme.core.monitoring.health.indicators;

import app.nzyme.core.database.maintenance.DatabaseMaintenance;
import app.nzyme.core.database.maintenance.DatabaseStatistics;
import app.nzyme.core.database.maintenance.DatabaseStatistics.IndexStatistics;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.monitoring.health.Indicator;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;

import java.util.Optional;

public class DatabaseBloatIndicator extends Indicator {

    public static final long MINIMUM_INDEX_BYTES = 100L * 1024L * 1024L;
    public static final int ORANGE_BLOAT_PERCENT = 60;
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

        boolean orange = false;
        for (IndexStatistics i : stats.get().indexes()) {
            if (i.bytes() < MINIMUM_INDEX_BYTES || i.bloatPercent() == null) {
                continue;
            }

            if (i.bloatPercent() >= RED_BLOAT_PERCENT) {
                return IndicatorStatus.red(this);
            }

            if (i.bloatPercent() >= ORANGE_BLOAT_PERCENT) {
                orange = true;
            }
        }

        return orange ? IndicatorStatus.orange(this) : IndicatorStatus.green(this);
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
