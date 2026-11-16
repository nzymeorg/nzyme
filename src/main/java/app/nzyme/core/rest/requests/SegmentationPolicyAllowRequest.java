package app.nzyme.core.rest.requests;

import app.nzyme.core.ethernet.segmentation.PolicyRule;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class SegmentationPolicyAllowRequest {

    @Nullable
    public abstract PolicyRule rule();

    @JsonCreator
    public static SegmentationPolicyAllowRequest create(@Schema(description = "The crossing to allow.") @JsonProperty("rule") PolicyRule rule) {
        return builder()
                .rule(rule)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentationPolicyAllowRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder rule(PolicyRule rule);

        public abstract SegmentationPolicyAllowRequest build();
    }

}
