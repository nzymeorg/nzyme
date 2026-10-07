package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.annotation.Nullable;
import java.util.UUID;

@Schema(description = "Body for subscribing an event action to a system event type.")
@AutoValue
public abstract class SystemEventSubscriptionRequest {

    public abstract UUID actionId();

    @Nullable
    public abstract UUID organizationId();

    @JsonCreator
    public static SystemEventSubscriptionRequest create(@Schema(description = "UUID of the event action to run.", requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("action_id") UUID actionId, @Nullable @Schema(description = "Organization UUID. Omit to subscribe on the cluster level.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                        @JsonProperty("organization_id") UUID organizationId) {
        return builder()
                .actionId(actionId)
                .organizationId(organizationId)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SystemEventSubscriptionRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder actionId(UUID actionId);

        public abstract Builder organizationId(UUID organizationId);

        public abstract SystemEventSubscriptionRequest build();
    }
}
