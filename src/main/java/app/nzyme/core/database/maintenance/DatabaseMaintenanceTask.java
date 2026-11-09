package app.nzyme.core.database.maintenance;

import app.nzyme.core.database.maintenance.DatabaseMaintenance.ActionType;
import app.nzyme.core.database.maintenance.DatabaseMaintenance.Initiator;
import app.nzyme.core.database.maintenance.DatabaseMaintenance.MaintenanceAction;
import app.nzyme.plugin.distributed.tasksqueue.Task;
import app.nzyme.plugin.distributed.tasksqueue.TaskType;

import java.util.HashMap;
import java.util.Map;

public class DatabaseMaintenanceTask extends Task {

    private final MaintenanceAction action;

    public DatabaseMaintenanceTask(MaintenanceAction action) {
        this.action = action;
    }

    @Override
    public TaskType type() {
        return TaskType.DATABASE_MAINTENANCE;
    }

    @Override
    public boolean allowProcessSelf() {
        return true;
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("action", action.type().name());
        parameters.put("target", action.target());
        parameters.put("initiator", action.initiator().name());
        return parameters;
    }

    @Override
    public boolean allowRetry() {
        return true;
    }

    public static MaintenanceAction fromParameters(Map<String, Object> parameters) {
        Object target = parameters.get("target");
        return new MaintenanceAction(
                ActionType.valueOf((String) parameters.get("action")),
                target == null ? null : target.toString(),
                Initiator.valueOf((String) parameters.get("initiator"))
        );
    }

}
