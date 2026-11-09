package app.nzyme.core.monitoring.health.indicators;

import app.nzyme.core.database.maintenance.DatabaseMaintenance;
import app.nzyme.core.database.maintenance.DatabaseStatistics;
import app.nzyme.core.database.maintenance.DatabaseStatistics.ServerStatistics;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.monitoring.health.Indicator;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;

import java.util.Optional;

public class DatabaseTransactionsIndicator extends Indicator {

    public static final long ORANGE_XID_AGE = 1_000_000_000L;
    public static final long RED_XID_AGE = 1_500_000_000L;
    public static final long ORANGE_TRANSACTION_SECONDS = 3600;
    public static final long RED_TRANSACTION_SECONDS = 6 * 3600;

    private final DatabaseMaintenance maintenance;

    public DatabaseTransactionsIndicator(DatabaseMaintenance maintenance) {
        this.maintenance = maintenance;
    }

    @Override
    protected IndicatorStatus doRun() {
        Optional<DatabaseStatistics> stats = maintenance.getStatistics();
        if (stats.isEmpty()) {
            return NO_RESULT;
        }

        ServerStatistics s = stats.get().server();

        if (s.xidAge() >= RED_XID_AGE || s.oldestTransactionSeconds() >= RED_TRANSACTION_SECONDS) {
            return IndicatorStatus.red(this);
        }

        if (s.xidAge() >= ORANGE_XID_AGE || s.oldestTransactionSeconds() >= ORANGE_TRANSACTION_SECONDS) {
            return IndicatorStatus.orange(this);
        }

        return IndicatorStatus.green(this);
    }

    @Override
    public String getId() {
        return "db_transactions";
    }

    @Override
    public String getName() {
        return "Database Transactions";
    }

    @Override
    public SystemEventType getSystemEventType() {
        return SystemEventType.HEALTH_INDICATOR_DB_TRANSACTIONS_TOGGLED;
    }

}
