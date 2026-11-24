package app.nzyme.core.rest.responses.alerts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class DetectionAlertActionListResponse {

    @JsonProperty("actions")
    public abstract List<DetectionAlertActionResponse> actions();

    public static DetectionAlertActionListResponse create(List<DetectionAlertActionResponse> actions) {
        return builder()
                .actions(actions)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DetectionAlertActionListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder actions(List<DetectionAlertActionResponse> actions);

        public abstract DetectionAlertActionListResponse build();
    }
}
