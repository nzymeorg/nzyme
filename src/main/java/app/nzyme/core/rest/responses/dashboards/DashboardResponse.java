package app.nzyme.core.rest.responses.dashboards;

import app.nzyme.core.dashboards.DashboardWidget;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class DashboardResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("description")
    public abstract String description();

    @JsonProperty("scope_type")
    public abstract String scopeType();

    @JsonProperty("scope")
    public abstract List<DashboardScopeItemResponse> scope();

    @JsonProperty("window_minutes")
    public abstract int windowMinutes();

    @JsonProperty("widgets")
    public abstract List<DashboardWidget> widgets();

    @JsonProperty("display_count")
    public abstract long displayCount();

    @JsonProperty("created_at")
    public abstract DateTime createdAt();

    @JsonProperty("updated_at")
    public abstract DateTime updatedAt();

    public static DashboardResponse create(UUID uuid, String name, String description, String scopeType, List<DashboardScopeItemResponse> scope, int windowMinutes, List<DashboardWidget> widgets, long displayCount, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .uuid(uuid)
                .name(name)
                .description(description)
                .scopeType(scopeType)
                .scope(scope)
                .windowMinutes(windowMinutes)
                .widgets(widgets)
                .displayCount(displayCount)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DashboardResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder name(String name);

        public abstract Builder description(String description);

        public abstract Builder scopeType(String scopeType);

        public abstract Builder scope(List<DashboardScopeItemResponse> scope);

        public abstract Builder windowMinutes(int windowMinutes);

        public abstract Builder widgets(List<DashboardWidget> widgets);

        public abstract Builder displayCount(long displayCount);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract DashboardResponse build();
    }

}
