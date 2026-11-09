package app.nzyme.core.rest.responses.system.database.maintenance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import org.joda.time.DateTime;

@AutoValue
public abstract class DatabaseHealthResponse {

    @JsonProperty("node_name")
    public abstract String nodeName();

    @JsonProperty("collected_at")
    public abstract DateTime collectedAt();

    @JsonProperty("server")
    public abstract DatabaseServerResponse server();

    @JsonProperty("settings")
    public abstract List<DatabaseServerSettingResponse> settings();

    @JsonProperty("tables")
    public abstract List<DatabaseTableResponse> tables();

    @JsonProperty("indexes")
    public abstract List<DatabaseIndexResponse> indexes();

    @Nullable
    @JsonProperty("running_action")
    public abstract DatabaseMaintenanceActionResponse runningAction();

    @JsonProperty("queued_actions")
    public abstract List<DatabaseMaintenanceActionResponse> queuedActions();

    @JsonProperty("in_maintenance_window")
    public abstract boolean inMaintenanceWindow();

    @JsonProperty("maintenance_window_start_hour")
    public abstract int maintenanceWindowStartHour();

    @JsonProperty("maintenance_window_hours")
    public abstract int maintenanceWindowHours();

    @JsonProperty("table_tuning_enabled")
    public abstract boolean tableTuningEnabled();

    @JsonProperty("auto_vacuum_enabled")
    public abstract boolean autoVacuumEnabled();

    @JsonProperty("auto_reindex_enabled")
    public abstract boolean autoReindexEnabled();

    @JsonProperty("reindex_bloat_threshold_percent")
    public abstract int reindexBloatThresholdPercent();

    @JsonProperty("reindex_minimum_index_mb")
    public abstract int reindexMinimumIndexMb();

    public static DatabaseHealthResponse create(String nodeName, DateTime collectedAt, DatabaseServerResponse server, List<DatabaseServerSettingResponse> settings, List<DatabaseTableResponse> tables, List<DatabaseIndexResponse> indexes, DatabaseMaintenanceActionResponse runningAction, List<DatabaseMaintenanceActionResponse> queuedActions, boolean inMaintenanceWindow, int maintenanceWindowStartHour, int maintenanceWindowHours, boolean tableTuningEnabled, boolean autoVacuumEnabled, boolean autoReindexEnabled, int reindexBloatThresholdPercent, int reindexMinimumIndexMb) {
        return builder()
                .nodeName(nodeName)
                .collectedAt(collectedAt)
                .server(server)
                .settings(settings)
                .tables(tables)
                .indexes(indexes)
                .runningAction(runningAction)
                .queuedActions(queuedActions)
                .inMaintenanceWindow(inMaintenanceWindow)
                .maintenanceWindowStartHour(maintenanceWindowStartHour)
                .maintenanceWindowHours(maintenanceWindowHours)
                .tableTuningEnabled(tableTuningEnabled)
                .autoVacuumEnabled(autoVacuumEnabled)
                .autoReindexEnabled(autoReindexEnabled)
                .reindexBloatThresholdPercent(reindexBloatThresholdPercent)
                .reindexMinimumIndexMb(reindexMinimumIndexMb)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DatabaseHealthResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder nodeName(String nodeName);
        public abstract Builder collectedAt(DateTime collectedAt);
        public abstract Builder server(DatabaseServerResponse server);
        public abstract Builder settings(List<DatabaseServerSettingResponse> settings);
        public abstract Builder tables(List<DatabaseTableResponse> tables);
        public abstract Builder indexes(List<DatabaseIndexResponse> indexes);
        public abstract Builder runningAction(DatabaseMaintenanceActionResponse runningAction);
        public abstract Builder queuedActions(List<DatabaseMaintenanceActionResponse> queuedActions);
        public abstract Builder inMaintenanceWindow(boolean inMaintenanceWindow);
        public abstract Builder maintenanceWindowStartHour(int maintenanceWindowStartHour);
        public abstract Builder maintenanceWindowHours(int maintenanceWindowHours);
        public abstract Builder tableTuningEnabled(boolean tableTuningEnabled);
        public abstract Builder autoVacuumEnabled(boolean autoVacuumEnabled);
        public abstract Builder autoReindexEnabled(boolean autoReindexEnabled);
        public abstract Builder reindexBloatThresholdPercent(int reindexBloatThresholdPercent);
        public abstract Builder reindexMinimumIndexMb(int reindexMinimumIndexMb);

        public abstract DatabaseHealthResponse build();
    }

}
