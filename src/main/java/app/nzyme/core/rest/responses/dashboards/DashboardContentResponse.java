package app.nzyme.core.rest.responses.dashboards;

import app.nzyme.core.dashboards.DashboardWidget;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;

@AutoValue
public abstract class DashboardContentResponse {

    @JsonProperty("generated_at")
    public abstract DateTime generatedAt();

    @JsonProperty("window_minutes")
    public abstract int windowMinutes();

    @JsonProperty("scope_type")
    public abstract String scopeType();

    @JsonProperty("scope_count")
    public abstract int scopeCount();

    @JsonProperty("redacted")
    public abstract boolean redacted();

    @JsonProperty("tap_count")
    public abstract int tapCount();

    @JsonProperty("condition")
    public abstract StatusWidgetResponse condition();

    @JsonProperty("widgets")
    public abstract List<DashboardWidget> widgets();

    @JsonProperty("data")
    public abstract Map<String, Object> data();

    public static DashboardContentResponse create(DateTime generatedAt, int windowMinutes, String scopeType, int scopeCount, boolean redacted, int tapCount, StatusWidgetResponse condition, List<DashboardWidget> widgets, Map<String, Object> data) {
        return builder()
                .generatedAt(generatedAt)
                .windowMinutes(windowMinutes)
                .scopeType(scopeType)
                .scopeCount(scopeCount)
                .redacted(redacted)
                .tapCount(tapCount)
                .condition(condition)
                .widgets(widgets)
                .data(data)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DashboardContentResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder generatedAt(DateTime generatedAt);

        public abstract Builder windowMinutes(int windowMinutes);

        public abstract Builder scopeType(String scopeType);

        public abstract Builder scopeCount(int scopeCount);

        public abstract Builder redacted(boolean redacted);

        public abstract Builder tapCount(int tapCount);

        public abstract Builder condition(StatusWidgetResponse condition);

        public abstract Builder widgets(List<DashboardWidget> widgets);

        public abstract Builder data(Map<String, Object> data);

        public abstract DashboardContentResponse build();
    }

}
