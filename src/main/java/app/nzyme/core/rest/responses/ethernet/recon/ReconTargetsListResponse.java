package app.nzyme.core.rest.responses.ethernet.recon;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class ReconTargetsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("targets")
    public abstract List<ReconTargetResponse> targets();

    public static ReconTargetsListResponse create(long total, List<ReconTargetResponse> targets) {
        return builder()
                .total(total)
                .targets(targets)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ReconTargetsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder targets(List<ReconTargetResponse> targets);
        public abstract ReconTargetsListResponse build();
    }
}
