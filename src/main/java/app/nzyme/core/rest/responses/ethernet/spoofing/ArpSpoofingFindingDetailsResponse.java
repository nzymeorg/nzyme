package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class ArpSpoofingFindingDetailsResponse {

    @JsonProperty("finding")
    public abstract ArpSpoofingFindingResponse finding();

    @JsonProperty("victims")
    public abstract List<ArpSpoofingVictimResponse> victims();

    @JsonProperty("claim_timeline")
    public abstract List<ArpSpoofingClaimBucketResponse> claimTimeline();

    public static ArpSpoofingFindingDetailsResponse create(ArpSpoofingFindingResponse finding, List<ArpSpoofingVictimResponse> victims, List<ArpSpoofingClaimBucketResponse> claimTimeline) {
        return builder()
                .finding(finding)
                .victims(victims)
                .claimTimeline(claimTimeline)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ArpSpoofingFindingDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder finding(ArpSpoofingFindingResponse finding);
        public abstract Builder victims(List<ArpSpoofingVictimResponse> victims);
        public abstract Builder claimTimeline(List<ArpSpoofingClaimBucketResponse> claimTimeline);
        public abstract ArpSpoofingFindingDetailsResponse build();
    }
}
