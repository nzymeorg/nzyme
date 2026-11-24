package app.nzyme.core.rest.responses.alerts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.Map;

@AutoValue
public abstract class DetectionAlertActionResponse {

    @JsonProperty("type")
    public abstract String type();

    @JsonProperty("done")
    public abstract boolean done();

    @JsonProperty("target")
    public abstract Map<String, String> target();

    public static DetectionAlertActionResponse create(String type, boolean done, Map<String, String> target) {
        return builder()
                .type(type)
                .done(done)
                .target(target)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DetectionAlertActionResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder type(String type);

        public abstract Builder done(boolean done);

        public abstract Builder target(Map<String, String> target);

        public abstract DetectionAlertActionResponse build();
    }
}
