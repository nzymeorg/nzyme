package app.nzyme.core.rest.requests;

import app.nzyme.core.ethernet.segmentation.PolicyRule;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;

@AutoValue
public abstract class SegmentationPolicyRequest {

    @Nullable
    public abstract String name();

    @Nullable
    public abstract String description();

    @Nullable
    public abstract String status();

    @Nullable
    public abstract Boolean alertOnConnected();

    @Nullable
    public abstract Boolean alertOnAttempted();

    @Nullable
    public abstract PolicyRule scope();

    @Nullable
    public abstract List<PolicyRule> allowed();

    @JsonCreator
    public static SegmentationPolicyRequest create(@JsonProperty("name") String name,
                                                   @JsonProperty("description") String description,
                                                   @Schema(description = "ACTIVE, AUDIT or PAUSED.") @JsonProperty("status") String status,
                                                   @Schema(description = "Alert when a connection the policy does not allow was answered, or was sent to a broadcast or multicast address.") @JsonProperty("alert_on_connected") Boolean alertOnConnected,
                                                   @Schema(description = "Alert when a connection the policy does not allow was rejected or got no reply.") @JsonProperty("alert_on_attempted") Boolean alertOnAttempted,
                                                   @JsonProperty("scope") PolicyRule scope,
                                                   @JsonProperty("allowed") List<PolicyRule> allowed) {
        return builder()
                .name(name)
                .description(description)
                .status(status)
                .alertOnConnected(alertOnConnected)
                .alertOnAttempted(alertOnAttempted)
                .scope(scope)
                .allowed(allowed)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentationPolicyRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder status(String status);
        public abstract Builder alertOnConnected(Boolean alertOnConnected);
        public abstract Builder alertOnAttempted(Boolean alertOnAttempted);
        public abstract Builder scope(PolicyRule scope);
        public abstract Builder allowed(List<PolicyRule> allowed);

        public abstract SegmentationPolicyRequest build();
    }

}
