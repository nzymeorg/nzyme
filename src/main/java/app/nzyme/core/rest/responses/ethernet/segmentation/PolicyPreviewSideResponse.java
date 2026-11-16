package app.nzyme.core.rest.responses.ethernet.segmentation;

import app.nzyme.core.ethernet.segmentation.PolicyRule;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

@AutoValue
public abstract class PolicyPreviewSideResponse {

    @Schema(description = "The selector that names the group, ready to be used in an allowed crossing.")
    @JsonProperty("selector")
    public abstract PolicyRule.Selector selector();

    @JsonProperty("label")
    public abstract String label();

    public static PolicyPreviewSideResponse create(PolicyRule.Selector selector,
                                                   String label) {
        return builder()
                .selector(selector)
                .label(label)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PolicyPreviewSideResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder selector(PolicyRule.Selector selector);
        public abstract Builder label(String label);

        public abstract PolicyPreviewSideResponse build();
    }

}
