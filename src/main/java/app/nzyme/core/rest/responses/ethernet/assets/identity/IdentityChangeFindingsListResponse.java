package app.nzyme.core.rest.responses.ethernet.assets.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class IdentityChangeFindingsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("findings")
    public abstract List<IdentityChangeFindingResponse> findings();

    public static IdentityChangeFindingsListResponse create(long total, List<IdentityChangeFindingResponse> findings) {
        return builder()
                .total(total)
                .findings(findings)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IdentityChangeFindingsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder findings(List<IdentityChangeFindingResponse> findings);
        public abstract IdentityChangeFindingsListResponse build();
    }
}
