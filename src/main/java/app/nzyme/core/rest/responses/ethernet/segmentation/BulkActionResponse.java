package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@AutoValue
public abstract class BulkActionResponse {

    @Schema(description = "Number of entries the action was applied to.")
    @JsonProperty("affected")
    public abstract long affected();

    @Schema(description = "The entries of the selection the action was not applied to: segments and asset tags "
            + "that a policy refers to.")
    @JsonProperty("skipped")
    public abstract List<BulkActionSkippedResponse> skipped();

    public static BulkActionResponse create(long affected, List<BulkActionSkippedResponse> skipped) {
        return builder()
                .affected(affected)
                .skipped(skipped)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BulkActionResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder affected(long affected);
        public abstract Builder skipped(List<BulkActionSkippedResponse> skipped);

        public abstract BulkActionResponse build();
    }

}
