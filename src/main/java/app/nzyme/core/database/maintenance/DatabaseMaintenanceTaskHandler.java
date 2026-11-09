package app.nzyme.core.database.maintenance;

import app.nzyme.core.NzymeNode;
import app.nzyme.plugin.distributed.tasksqueue.ReceivedTask;
import app.nzyme.plugin.distributed.tasksqueue.TaskHandler;
import app.nzyme.plugin.distributed.tasksqueue.TaskProcessingResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DatabaseMaintenanceTaskHandler implements TaskHandler {

    private static final Logger LOG = LogManager.getLogger(DatabaseMaintenanceTaskHandler.class);

    private final NzymeNode nzyme;

    public DatabaseMaintenanceTaskHandler(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    @Override
    public TaskProcessingResult handle(ReceivedTask task) {
        DatabaseMaintenance.MaintenanceAction action;
        try {
            action = DatabaseMaintenanceTask.fromParameters(task.parametersMap());
        } catch (Exception e) {
            LOG.error("Invalid database maintenance task parameters [{}].", task.parametersString(), e);
            return TaskProcessingResult.FAILURE;
        }

        return nzyme.getDatabaseMaintenance().execute(action)
                ? TaskProcessingResult.SUCCESS : TaskProcessingResult.FAILURE;
    }

    @Override
    public String getName() {
        return "Database Maintenance";
    }

}
