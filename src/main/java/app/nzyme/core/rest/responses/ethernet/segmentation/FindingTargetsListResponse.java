package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@AutoValue
public abstract class FindingTargetsListResponse {

    @Schema(description = "Number of all targets the finding keeps that pass the filters, of which this is one page.")
    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("targets")
    public abstract List<FindingTargetResponse> targets();

    public static FindingTargetsListResponse create(long total, List<FindingTargetResponse> targets) {
        return builder()
                .total(total)
                .targets(targets)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FindingTargetsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder targets(List<FindingTargetResponse> targets);

        public abstract FindingTargetsListResponse build();
    }

}
