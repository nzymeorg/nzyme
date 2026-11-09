package app.nzyme.core.rest.responses.system.database.maintenance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class DatabaseMaintenanceLogListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("entries")
    public abstract List<DatabaseMaintenanceLogEntryResponse> entries();

    public static DatabaseMaintenanceLogListResponse create(long total, List<DatabaseMaintenanceLogEntryResponse> entries) {
        return builder()
                .total(total)
                .entries(entries)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DatabaseMaintenanceLogListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder entries(List<DatabaseMaintenanceLogEntryResponse> entries);

        public abstract DatabaseMaintenanceLogListResponse build();
    }

}
