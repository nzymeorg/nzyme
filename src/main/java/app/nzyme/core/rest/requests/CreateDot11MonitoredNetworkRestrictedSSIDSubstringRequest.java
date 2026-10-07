package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "Body for adding a restricted SSID substring to a monitored WiFi network.")
@AutoValue
public abstract class CreateDot11MonitoredNetworkRestrictedSSIDSubstringRequest {

    @NotEmpty
    public abstract String substring();

    @JsonCreator
    public static CreateDot11MonitoredNetworkRestrictedSSIDSubstringRequest create(@Schema(description = "Substring that no other SSID in range is allowed to contain.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                                                   @JsonProperty("substring") String substring) {
        return builder()
                .substring(substring)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateDot11MonitoredNetworkRestrictedSSIDSubstringRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder substring(@NotEmpty String substring);

        public abstract CreateDot11MonitoredNetworkRestrictedSSIDSubstringRequest build();
    }
}
