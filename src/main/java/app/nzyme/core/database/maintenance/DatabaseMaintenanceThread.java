package app.nzyme.core.database.maintenance;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.periodicals.Periodical;
import org.joda.time.DateTime;

public class DatabaseMaintenanceThread extends Periodical {

    private static final int LOG_RETENTION_DAYS = 90;

    private final NzymeNode nzyme;
    private int runs = 0;

    public DatabaseMaintenanceThread(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    @Override
    protected void execute() {
        nzyme.getDatabaseMaintenance().run();

        // Runs once a minute. Clean the log once an hour.
        if (runs++ % 60 == 0) {
            nzyme.getDatabaseMaintenance().retentionCleanLog(DateTime.now().minusDays(LOG_RETENTION_DAYS));
        }
    }

    @Override
    public String getName() {
        return "DatabaseMaintenanceThread";
    }

}
