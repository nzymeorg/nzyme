package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class SegmentationWidgetResponse {

    @Schema(description = "Policies whose traffic taps see and that had no findings in the window.")
    @JsonProperty("holding")
    public abstract long holding();

    @Schema(description = "Policies with a connection that is not allowed and was answered.")
    @JsonProperty("crossed")
    public abstract long crossed();

    @Schema(description = "Policies with a connection that is not allowed and was not answered.")
    @JsonProperty("attempted")
    public abstract long attempted();

    @Schema(description = "Policies without findings whose traffic no tap sees.")
    @JsonProperty("not_observable")
    public abstract long notObservable();

    @JsonProperty("audit")
    public abstract long audit();

    @JsonProperty("paused")
    public abstract long paused();

    @Schema(description = "The policies that are crossed or attempted, crossed first, 6 at most. How many there "
            + "are in total is in the counts. Empty on redacted screens.")
    @JsonProperty("policies")
    public abstract List<SegmentationWidgetPolicyResponse> policies();

    public static SegmentationWidgetResponse create(long holding,
                                                    long crossed,
                                                    long attempted,
                                                    long notObservable,
                                                    long audit,
                                                    long paused,
                                                    List<SegmentationWidgetPolicyResponse> policies) {
        return builder()
                .holding(holding)
                .crossed(crossed)
                .attempted(attempted)
                .notObservable(notObservable)
                .audit(audit)
                .paused(paused)
                .policies(policies)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentationWidgetResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder holding(long holding);
        public abstract Builder crossed(long crossed);
        public abstract Builder attempted(long attempted);
        public abstract Builder notObservable(long notObservable);
        public abstract Builder audit(long audit);
        public abstract Builder paused(long paused);
        public abstract Builder policies(List<SegmentationWidgetPolicyResponse> policies);

        public abstract SegmentationWidgetResponse build();
    }

}
