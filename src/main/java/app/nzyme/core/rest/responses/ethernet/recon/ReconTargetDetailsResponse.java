package app.nzyme.core.rest.responses.ethernet.recon;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class ReconTargetDetailsResponse {

    @JsonProperty("target")
    public abstract ReconTargetResponse target();

    @JsonProperty("sources")
    public abstract List<ReconTargetSourceResponse> sources();

    @JsonProperty("ports")
    public abstract List<ReconTargetPortResponse> ports();

    public static ReconTargetDetailsResponse create(ReconTargetResponse target, List<ReconTargetSourceResponse> sources, List<ReconTargetPortResponse> ports) {
        return builder()
                .target(target)
                .sources(sources)
                .ports(ports)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ReconTargetDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder target(ReconTargetResponse target);
        public abstract Builder sources(List<ReconTargetSourceResponse> sources);
        public abstract Builder ports(List<ReconTargetPortResponse> ports);
        public abstract ReconTargetDetailsResponse build();
    }
}
