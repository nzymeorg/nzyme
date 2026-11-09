package app.nzyme.core.monitoring.health.indicators;

import app.nzyme.core.database.maintenance.DatabaseMaintenance;
import app.nzyme.core.database.maintenance.DatabaseStatistics;
import app.nzyme.core.database.maintenance.DatabaseStatistics.ServerSetting;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.monitoring.health.Indicator;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;

import java.util.Optional;

public class DatabaseConfigurationIndicator extends Indicator {

    private final DatabaseMaintenance maintenance;

    public DatabaseConfigurationIndicator(DatabaseMaintenance maintenance) {
        this.maintenance = maintenance;
    }

    @Override
    protected IndicatorStatus doRun() {
        Optional<DatabaseStatistics> stats = maintenance.getStatistics();
        if (stats.isEmpty()) {
            return NO_RESULT;
        }

        boolean requiredMissing = false;
        for (ServerSetting s : stats.get().settings()) {
            if (s.ok() || s.severity() != DatabaseStatistics.SettingSeverity.REQUIRED) {
                continue;
            }

            if (s.name().equals("autovacuum")) {
                return IndicatorStatus.red(this);
            }

            requiredMissing = true;
        }

        return requiredMissing ? IndicatorStatus.orange(this) : IndicatorStatus.green(this);
    }

    @Override
    public String getId() {
        return "db_configuration";
    }

    @Override
    public String getName() {
        return "Database Configuration";
    }

    @Override
    public SystemEventType getSystemEventType() {
        return SystemEventType.HEALTH_INDICATOR_DB_CONFIGURATION_TOGGLED;
    }

}
