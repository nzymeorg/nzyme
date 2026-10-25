package app.nzyme.core.rest.responses.ethernet.icmp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class IcmpExclusionsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("exclusions")
    public abstract List<IcmpExclusionResponse> exclusions();

    public static IcmpExclusionsListResponse create(long total, List<IcmpExclusionResponse> exclusions) {
        return builder()
                .total(total)
                .exclusions(exclusions)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpExclusionsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder exclusions(List<IcmpExclusionResponse> exclusions);
        public abstract IcmpExclusionsListResponse build();
    }
}
