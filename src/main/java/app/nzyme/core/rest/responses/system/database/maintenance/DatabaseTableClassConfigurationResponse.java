package app.nzyme.core.rest.responses.system.database.maintenance;

import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;
import java.util.Map;

@AutoValue
public abstract class DatabaseTableClassConfigurationResponse {

    @JsonProperty("key")
    public abstract String key();

    @JsonProperty("title")
    public abstract String title();

    @JsonProperty("description")
    public abstract String description();

    @JsonProperty("tables")
    public abstract List<String> tables();

    @JsonProperty("entries")
    public abstract Map<String, ConfigurationEntryResponse> entries();

    public static DatabaseTableClassConfigurationResponse create(String key, String title, String description, List<String> tables, Map<String, ConfigurationEntryResponse> entries) {
        return builder()
                .key(key)
                .title(title)
                .description(description)
                .tables(tables)
                .entries(entries)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DatabaseTableClassConfigurationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder key(String key);
        public abstract Builder title(String title);
        public abstract Builder description(String description);
        public abstract Builder tables(List<String> tables);
        public abstract Builder entries(Map<String, ConfigurationEntryResponse> entries);

        public abstract DatabaseTableClassConfigurationResponse build();
    }

}
