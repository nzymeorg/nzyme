package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class DhcpSpoofingFindingsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("findings")
    public abstract List<DhcpSpoofingFindingResponse> findings();

    public static DhcpSpoofingFindingsListResponse create(long total, List<DhcpSpoofingFindingResponse> findings) {
        return builder()
                .total(total)
                .findings(findings)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DhcpSpoofingFindingsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder findings(List<DhcpSpoofingFindingResponse> findings);
        public abstract DhcpSpoofingFindingsListResponse build();
    }
}
