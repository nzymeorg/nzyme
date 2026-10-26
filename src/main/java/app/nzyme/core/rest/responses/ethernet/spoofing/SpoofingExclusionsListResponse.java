package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class SpoofingExclusionsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("exclusions")
    public abstract List<SpoofingExclusionResponse> exclusions();

    public static SpoofingExclusionsListResponse create(long total, List<SpoofingExclusionResponse> exclusions) {
        return builder()
                .total(total)
                .exclusions(exclusions)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SpoofingExclusionsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder exclusions(List<SpoofingExclusionResponse> exclusions);
        public abstract SpoofingExclusionsListResponse build();
    }
}
