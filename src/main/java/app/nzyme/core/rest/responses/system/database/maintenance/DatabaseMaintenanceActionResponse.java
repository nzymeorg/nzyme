package app.nzyme.core.rest.responses.system.database.maintenance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class DatabaseMaintenanceActionResponse {

    @JsonProperty("type")
    public abstract String type();

    @Nullable
    @JsonProperty("target")
    public abstract String target();

    @JsonProperty("initiator")
    public abstract String initiator();

    @JsonProperty("queued_at")
    public abstract DateTime queuedAt();

    @Nullable
    @JsonProperty("started_at")
    public abstract DateTime startedAt();

    public static DatabaseMaintenanceActionResponse create(String type, String target, String initiator, DateTime queuedAt, DateTime startedAt) {
        return builder()
                .type(type)
                .target(target)
                .initiator(initiator)
                .queuedAt(queuedAt)
                .startedAt(startedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DatabaseMaintenanceActionResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder type(String type);
        public abstract Builder target(String target);
        public abstract Builder initiator(String initiator);
        public abstract Builder queuedAt(DateTime queuedAt);
        public abstract Builder startedAt(DateTime startedAt);

        public abstract DatabaseMaintenanceActionResponse build();
    }

}
