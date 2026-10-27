package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class NameResolutionSpoofingFindingDetailsResponse {

    @JsonProperty("finding")
    public abstract NameResolutionSpoofingFindingResponse finding();

    @JsonProperty("victims")
    public abstract List<NameResolutionSpoofingVictimResponse> victims();

    public static NameResolutionSpoofingFindingDetailsResponse create(NameResolutionSpoofingFindingResponse finding, List<NameResolutionSpoofingVictimResponse> victims) {
        return builder()
                .finding(finding)
                .victims(victims)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NameResolutionSpoofingFindingDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder finding(NameResolutionSpoofingFindingResponse finding);
        public abstract Builder victims(List<NameResolutionSpoofingVictimResponse> victims);
        public abstract NameResolutionSpoofingFindingDetailsResponse build();
    }
}
