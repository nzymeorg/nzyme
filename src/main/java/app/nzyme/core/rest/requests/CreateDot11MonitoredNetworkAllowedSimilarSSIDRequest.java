package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@Schema(description = "Body for allowing an SSID that looks similar to a monitored WiFi network.")
@AutoValue
public abstract class CreateDot11MonitoredNetworkAllowedSimilarSSIDRequest {

    @NotEmpty
    @Size(max = 255)
    public abstract String ssid();

    @JsonCreator
    public static CreateDot11MonitoredNetworkAllowedSimilarSSIDRequest create(@Schema(description = "SSID that no longer raises the similar SSID alert of this monitored network. Matched exactly.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                                              @JsonProperty("ssid") @NotEmpty @Size(max = 255) String ssid) {
        return builder()
                .ssid(ssid)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateDot11MonitoredNetworkAllowedSimilarSSIDRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder ssid(@NotEmpty @Size(max = 255) String ssid);

        public abstract CreateDot11MonitoredNetworkAllowedSimilarSSIDRequest build();
    }
}
