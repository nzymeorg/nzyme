package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class SpoofingExclusionImpactResponse {

    @JsonProperty("matching_findings")
    public abstract int matchingFindings();

    @JsonProperty("findings")
    public abstract List<SpoofingExclusionMatchedFindingResponse> findings();

    public static SpoofingExclusionImpactResponse create(int matchingFindings, List<SpoofingExclusionMatchedFindingResponse> findings) {
        return builder()
                .matchingFindings(matchingFindings)
                .findings(findings)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SpoofingExclusionImpactResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder matchingFindings(int matchingFindings);
        public abstract Builder findings(List<SpoofingExclusionMatchedFindingResponse> findings);
        public abstract SpoofingExclusionImpactResponse build();
    }
}
