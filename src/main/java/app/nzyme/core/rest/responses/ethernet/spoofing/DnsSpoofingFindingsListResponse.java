package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class DnsSpoofingFindingsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("findings")
    public abstract List<DnsSpoofingFindingResponse> findings();

    public static DnsSpoofingFindingsListResponse create(long total, List<DnsSpoofingFindingResponse> findings) {
        return builder()
                .total(total)
                .findings(findings)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsSpoofingFindingsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder findings(List<DnsSpoofingFindingResponse> findings);

        public abstract DnsSpoofingFindingsListResponse build();
    }
}
