package app.nzyme.core.rest.responses.system.database.maintenance;

import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;
import java.util.Map;

@AutoValue
public abstract class DatabaseMaintenanceConfigurationResponse {

    @JsonProperty("entries")
    public abstract Map<String, ConfigurationEntryResponse> entries();

    @JsonProperty("classes")
    public abstract List<DatabaseTableClassConfigurationResponse> classes();

    public static DatabaseMaintenanceConfigurationResponse create(Map<String, ConfigurationEntryResponse> entries, List<DatabaseTableClassConfigurationResponse> classes) {
        return builder()
                .entries(entries)
                .classes(classes)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DatabaseMaintenanceConfigurationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder entries(Map<String, ConfigurationEntryResponse> entries);
        public abstract Builder classes(List<DatabaseTableClassConfigurationResponse> classes);

        public abstract DatabaseMaintenanceConfigurationResponse build();
    }

}
