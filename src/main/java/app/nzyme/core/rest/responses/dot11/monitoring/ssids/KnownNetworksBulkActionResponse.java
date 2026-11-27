package app.nzyme.core.rest.responses.dot11.monitoring.ssids;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

@AutoValue
public abstract class KnownNetworksBulkActionResponse {

    @Schema(description = "Number of entries the action was applied to.")
    @JsonProperty("affected")
    public abstract int affected();

    public static KnownNetworksBulkActionResponse create(int affected) {
        return builder()
                .affected(affected)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_KnownNetworksBulkActionResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder affected(int affected);
        public abstract KnownNetworksBulkActionResponse build();
    }

}
