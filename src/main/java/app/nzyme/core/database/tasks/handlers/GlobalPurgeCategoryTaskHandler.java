package app.nzyme.core.database.tasks.handlers;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.DataCategory;
import app.nzyme.core.database.DataTableInformation;
import app.nzyme.core.database.DatabaseImpl;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.distributed.tasksqueue.ReceivedTask;
import app.nzyme.plugin.distributed.tasksqueue.TaskHandler;
import app.nzyme.plugin.distributed.tasksqueue.TaskProcessingResult;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

public class GlobalPurgeCategoryTaskHandler extends PurgeTask implements TaskHandler {

    private final NzymeNode nzyme;

    public GlobalPurgeCategoryTaskHandler(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    @Override
    public TaskProcessingResult handle(ReceivedTask task) {
        List<DataTableInformation> tables = ((DatabaseImpl) nzyme.getDatabase()).getTablesOfDataCategory(
                DataCategory.valueOf((String) task.parametersMap().get("category"))
        );
        DateTime since = DateTime.parse((String) task.parametersMap().get("since"));

        List<UUID> tapUuids = Tools.getTapUuids(nzyme, null, null);

        return purge(nzyme, tapUuids, null, null, tables, since);
    }

    @Override
    public String getName() {
        return "Data Category Purger: Global";
    }

}
