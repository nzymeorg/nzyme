package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

@Schema(description = "Body for updating the WiFi disconnection monitor configuration of a monitored network.")
@AutoValue
public abstract class UpdateDiscoDetectionConfigRequest {

    @NotNull
    public abstract UUID monitoredNetworkId();

    @NotEmpty
    public abstract String methodType();

    @NotNull
    public abstract Map<String, Object> configuration();

    @JsonCreator
    public static UpdateDiscoDetectionConfigRequest create(@Schema(description = "UUID of the monitored WiFi network.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                           @JsonProperty("monitored_network_id") UUID monitoredNetworkId,
                                                           @Schema(description = "Disconnection detection method to use.", allowableValues = {"STATIC_THRESHOLD"}, requiredMode = Schema.RequiredMode.REQUIRED)
                                                           @JsonProperty("method_type") String methodType,
                                                           @Schema(description = "Method configuration. The accepted keys depend on the detection method.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                           @JsonProperty("configuration") Map<String, Object> configuration) {
        return builder()
                .monitoredNetworkId(monitoredNetworkId)
                .methodType(methodType)
                .configuration(configuration)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateDiscoDetectionConfigRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder monitoredNetworkId(UUID monitoredNetworkId);

        public abstract Builder methodType(String methodType);

        public abstract Builder configuration(Map<String, Object> configuration);

        public abstract UpdateDiscoDetectionConfigRequest build();
    }
}
