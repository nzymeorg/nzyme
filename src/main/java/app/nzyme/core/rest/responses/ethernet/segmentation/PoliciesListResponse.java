package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class PoliciesListResponse {

    @Schema(description = "Number of all entries that pass the filters, of which this is one page.")
    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("policies")
    public abstract List<PolicyResponse> policies();

    public static PoliciesListResponse create(long total, List<PolicyResponse> policies) {
        return builder()
                .total(total)
                .policies(policies)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PoliciesListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder policies(List<PolicyResponse> policies);

        public abstract PoliciesListResponse build();
    }

}
