package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class NameResolutionSpoofingFindingsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("findings")
    public abstract List<NameResolutionSpoofingFindingResponse> findings();

    public static NameResolutionSpoofingFindingsListResponse create(long total, List<NameResolutionSpoofingFindingResponse> findings) {
        return builder()
                .total(total)
                .findings(findings)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NameResolutionSpoofingFindingsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder findings(List<NameResolutionSpoofingFindingResponse> findings);
        public abstract NameResolutionSpoofingFindingsListResponse build();
    }
}
