package app.nzyme.core.rest.responses.ethernet.dnstunnels;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class DnsTunnelExclusionsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("exclusions")
    public abstract List<DnsTunnelExclusionResponse> exclusions();

    public static DnsTunnelExclusionsListResponse create(long total, List<DnsTunnelExclusionResponse> exclusions) {
        return builder()
                .total(total)
                .exclusions(exclusions)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsTunnelExclusionsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder exclusions(List<DnsTunnelExclusionResponse> exclusions);
        public abstract DnsTunnelExclusionsListResponse build();
    }
}
