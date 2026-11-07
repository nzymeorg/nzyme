package app.nzyme.core.rest.responses.ethernet.assets.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class DuplicateMacFindingsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("findings")
    public abstract List<DuplicateMacFindingResponse> findings();

    public static DuplicateMacFindingsListResponse create(long total, List<DuplicateMacFindingResponse> findings) {
        return builder()
                .total(total)
                .findings(findings)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DuplicateMacFindingsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder findings(List<DuplicateMacFindingResponse> findings);
        public abstract DuplicateMacFindingsListResponse build();
    }
}
