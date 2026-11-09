package app.nzyme.core.rest.responses.system.database.maintenance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class DatabaseMaintenanceLogEntryResponse {

    @JsonProperty("id")
    public abstract long id();

    @JsonProperty("node_id")
    public abstract UUID nodeId();

    @Nullable
    @JsonProperty("node_name")
    public abstract String nodeName();

    @JsonProperty("action")
    public abstract String action();

    @Nullable
    @JsonProperty("target")
    public abstract String target();

    @JsonProperty("initiator")
    public abstract String initiator();

    @Nullable
    @JsonProperty("details")
    public abstract String details();

    @Nullable
    @JsonProperty("success")
    public abstract Boolean success();

    @JsonProperty("started_at")
    public abstract DateTime startedAt();

    @Nullable
    @JsonProperty("finished_at")
    public abstract DateTime finishedAt();

    public static DatabaseMaintenanceLogEntryResponse create(long id, UUID nodeId, String nodeName, String action, String target, String initiator, String details, Boolean success, DateTime startedAt, DateTime finishedAt) {
        return builder()
                .id(id)
                .nodeId(nodeId)
                .nodeName(nodeName)
                .action(action)
                .target(target)
                .initiator(initiator)
                .details(details)
                .success(success)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DatabaseMaintenanceLogEntryResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder nodeId(UUID nodeId);
        public abstract Builder nodeName(String nodeName);
        public abstract Builder action(String action);
        public abstract Builder target(String target);
        public abstract Builder initiator(String initiator);
        public abstract Builder details(String details);
        public abstract Builder success(Boolean success);
        public abstract Builder startedAt(DateTime startedAt);
        public abstract Builder finishedAt(DateTime finishedAt);

        public abstract DatabaseMaintenanceLogEntryResponse build();
    }

}
