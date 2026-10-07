package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Body for subscribing an event action to a detection event type.")
@AutoValue
public abstract class DetectionEventSubscriptionRequest {

    public abstract UUID actionId();
    public abstract UUID organizationId();

    @JsonCreator
    public static DetectionEventSubscriptionRequest create(@Schema(description = "UUID of the event action to run.", requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("action_id") UUID actionId, @Schema(description = "Organization UUID.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                           @JsonProperty("organization_id") UUID organizationId) {
        return builder()
                .actionId(actionId)
                .organizationId(organizationId)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DetectionEventSubscriptionRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder actionId(UUID actionId);

        public abstract Builder organizationId(UUID organizationId);

        public abstract DetectionEventSubscriptionRequest build();
    }
}
