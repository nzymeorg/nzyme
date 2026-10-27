package app.nzyme.core.rest.responses.ethernet.dnstunnels;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class DnsTunnelFindingsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("findings")
    public abstract List<DnsTunnelFindingResponse> findings();

    public static DnsTunnelFindingsListResponse create(long total, List<DnsTunnelFindingResponse> findings) {
        return builder()
                .total(total)
                .findings(findings)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsTunnelFindingsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder findings(List<DnsTunnelFindingResponse> findings);
        public abstract DnsTunnelFindingsListResponse build();
    }
}
