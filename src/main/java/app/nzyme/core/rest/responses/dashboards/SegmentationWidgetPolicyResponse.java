package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

@AutoValue
public abstract class SegmentationWidgetPolicyResponse {

    @JsonProperty("name")
    public abstract String name();

    @Schema(description = "CROSSED or ATTEMPTED.")
    @JsonProperty("state")
    public abstract String state();

    @JsonProperty("crossed_findings")
    public abstract long crossedFindings();

    @JsonProperty("attempted_findings")
    public abstract long attemptedFindings();

    public static SegmentationWidgetPolicyResponse create(String name,
                                                          String state,
                                                          long crossedFindings,
                                                          long attemptedFindings) {
        return builder()
                .name(name)
                .state(state)
                .crossedFindings(crossedFindings)
                .attemptedFindings(attemptedFindings)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentationWidgetPolicyResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder state(String state);
        public abstract Builder crossedFindings(long crossedFindings);
        public abstract Builder attemptedFindings(long attemptedFindings);

        public abstract SegmentationWidgetPolicyResponse build();
    }

}
