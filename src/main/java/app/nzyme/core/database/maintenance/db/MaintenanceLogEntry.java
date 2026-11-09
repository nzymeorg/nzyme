package app.nzyme.core.database.maintenance.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class MaintenanceLogEntry {

    public abstract long id();
    public abstract UUID nodeId();
    public abstract String action();
    @Nullable
    public abstract String target();
    public abstract String initiator();
    @Nullable
    public abstract String details();
    @Nullable
    public abstract Boolean success();
    public abstract DateTime startedAt();
    @Nullable
    public abstract DateTime finishedAt();

    public static MaintenanceLogEntry create(long id, UUID nodeId, String action, String target, String initiator, String details, Boolean success, DateTime startedAt, DateTime finishedAt) {
        return builder()
                .id(id)
                .nodeId(nodeId)
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
        return new AutoValue_MaintenanceLogEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder nodeId(UUID nodeId);
        public abstract Builder action(String action);
        public abstract Builder target(String target);
        public abstract Builder initiator(String initiator);
        public abstract Builder details(String details);
        public abstract Builder success(Boolean success);
        public abstract Builder startedAt(DateTime startedAt);
        public abstract Builder finishedAt(DateTime finishedAt);
        public abstract MaintenanceLogEntry build();
    }

}
