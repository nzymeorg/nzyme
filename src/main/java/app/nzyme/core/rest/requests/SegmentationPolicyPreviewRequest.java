package app.nzyme.core.rest.requests;

import app.nzyme.core.ethernet.segmentation.PolicyRule;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;

@AutoValue
public abstract class SegmentationPolicyPreviewRequest {

    @Nullable
    public abstract PolicyRule scope();

    @Nullable
    public abstract List<PolicyRule> allowed();

    @JsonCreator
    public static SegmentationPolicyPreviewRequest create(@JsonProperty("scope") PolicyRule scope,
                                                          @JsonProperty("allowed") List<PolicyRule> allowed) {
        return builder()
                .scope(scope)
                .allowed(allowed)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentationPolicyPreviewRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder scope(PolicyRule scope);
        public abstract Builder allowed(List<PolicyRule> allowed);

        public abstract SegmentationPolicyPreviewRequest build();
    }

}
