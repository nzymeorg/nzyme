package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class DhcpSpoofingFindingDetailsResponse {

    @JsonProperty("finding")
    public abstract DhcpSpoofingFindingResponse finding();

    @JsonProperty("victims")
    public abstract List<DhcpSpoofingVictimResponse> victims();

    public static DhcpSpoofingFindingDetailsResponse create(DhcpSpoofingFindingResponse finding, List<DhcpSpoofingVictimResponse> victims) {
        return builder()
                .finding(finding)
                .victims(victims)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DhcpSpoofingFindingDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder finding(DhcpSpoofingFindingResponse finding);
        public abstract Builder victims(List<DhcpSpoofingVictimResponse> victims);
        public abstract DhcpSpoofingFindingDetailsResponse build();
    }
}
