package app.nzyme.core.rest.responses.ethernet.dnstunnels;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class DnsTunnelExclusionImpactResponse {

    @JsonProperty("matching_findings")
    public abstract int matchingFindings();

    @JsonProperty("findings")
    public abstract List<DnsTunnelExclusionMatchedFindingResponse> findings();

    public static DnsTunnelExclusionImpactResponse create(int matchingFindings, List<DnsTunnelExclusionMatchedFindingResponse> findings) {
        return builder()
                .matchingFindings(matchingFindings)
                .findings(findings)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsTunnelExclusionImpactResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder matchingFindings(int matchingFindings);
        public abstract Builder findings(List<DnsTunnelExclusionMatchedFindingResponse> findings);
        public abstract DnsTunnelExclusionImpactResponse build();
    }
}
