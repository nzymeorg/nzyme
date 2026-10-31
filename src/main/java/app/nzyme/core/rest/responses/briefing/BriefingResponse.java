package app.nzyme.core.rest.responses.briefing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;
import java.util.Map;
import org.joda.time.DateTime;

@AutoValue
public abstract class BriefingResponse {

    @JsonProperty("briefing_type")
    public abstract String briefingType();

    @JsonProperty("generated_at")
    public abstract DateTime generatedAt();

    @JsonProperty("window")
    public abstract BriefingWindowResponse window();

    @JsonProperty("attention")
    public abstract List<BriefingAttentionItemResponse> attention();

    @JsonProperty("monitoring")
    public abstract BriefingMonitoringResponse monitoring();

    @JsonProperty("changes")
    public abstract List<BriefingChangeGroupResponse> changes();

    @JsonProperty("activity")
    public abstract Map<String, Object> activity();

    @JsonProperty("subsystems")
    public abstract List<String> subsystems();

    @JsonProperty("locations")
    public abstract List<BriefingLocationResponse> locations();

    public static BriefingResponse create(String briefingType, DateTime generatedAt, BriefingWindowResponse window, List<BriefingAttentionItemResponse> attention, BriefingMonitoringResponse monitoring, List<BriefingChangeGroupResponse> changes, Map<String, Object> activity, List<String> subsystems, List<BriefingLocationResponse> locations) {
        return builder()
                .briefingType(briefingType)
                .generatedAt(generatedAt)
                .window(window)
                .attention(attention)
                .monitoring(monitoring)
                .changes(changes)
                .activity(activity)
                .subsystems(subsystems)
                .locations(locations)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BriefingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder briefingType(String briefingType);

        public abstract Builder generatedAt(DateTime generatedAt);

        public abstract Builder window(BriefingWindowResponse window);

        public abstract Builder attention(List<BriefingAttentionItemResponse> attention);

        public abstract Builder monitoring(BriefingMonitoringResponse monitoring);

        public abstract Builder changes(List<BriefingChangeGroupResponse> changes);

        public abstract Builder activity(Map<String, Object> activity);

        public abstract Builder subsystems(List<String> subsystems);

        public abstract Builder locations(List<BriefingLocationResponse> locations);

        public abstract BriefingResponse build();
    }
}
