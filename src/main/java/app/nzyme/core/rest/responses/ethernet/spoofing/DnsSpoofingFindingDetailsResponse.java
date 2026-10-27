package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class DnsSpoofingFindingDetailsResponse {

    @JsonProperty("finding")
    public abstract DnsSpoofingFindingResponse finding();

    @JsonProperty("victims")
    public abstract List<DnsSpoofingVictimResponse> victims();

    public static DnsSpoofingFindingDetailsResponse create(DnsSpoofingFindingResponse finding, List<DnsSpoofingVictimResponse> victims) {
        return builder()
                .finding(finding)
                .victims(victims)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsSpoofingFindingDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder finding(DnsSpoofingFindingResponse finding);

        public abstract Builder victims(List<DnsSpoofingVictimResponse> victims);

        public abstract DnsSpoofingFindingDetailsResponse build();
    }
}
