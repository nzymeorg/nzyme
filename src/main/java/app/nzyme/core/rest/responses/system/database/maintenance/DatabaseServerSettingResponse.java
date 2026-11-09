package app.nzyme.core.rest.responses.system.database.maintenance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class DatabaseServerSettingResponse {

    @JsonProperty("name")
    public abstract String name();

    @JsonProperty("current_value")
    public abstract String currentValue();

    @JsonProperty("recommended_value")
    public abstract String recommendedValue();

    @JsonProperty("ok")
    public abstract boolean ok();

    @JsonProperty("severity")
    public abstract String severity();

    @JsonProperty("scope")
    public abstract String scope();

    @JsonProperty("description")
    public abstract String description();

    @Nullable
    @JsonProperty("config_lines")
    public abstract String configLines();

    @JsonProperty("pending_restart")
    public abstract boolean pendingRestart();

    public static DatabaseServerSettingResponse create(String name, String currentValue, String recommendedValue, boolean ok, String severity, String scope, String description, String configLines, boolean pendingRestart) {
        return builder()
                .name(name)
                .currentValue(currentValue)
                .recommendedValue(recommendedValue)
                .ok(ok)
                .severity(severity)
                .scope(scope)
                .description(description)
                .configLines(configLines)
                .pendingRestart(pendingRestart)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DatabaseServerSettingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder currentValue(String currentValue);
        public abstract Builder recommendedValue(String recommendedValue);
        public abstract Builder ok(boolean ok);
        public abstract Builder severity(String severity);
        public abstract Builder scope(String scope);
        public abstract Builder description(String description);
        public abstract Builder configLines(String configLines);
        public abstract Builder pendingRestart(boolean pendingRestart);

        public abstract DatabaseServerSettingResponse build();
    }

}
