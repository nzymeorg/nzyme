package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "Body for adding an expected fingerprint to a BSSID of a monitored WiFi network.")
@AutoValue
public abstract class CreateDot11MonitoredBSSIDFingerprintRequest {

    @NotEmpty
    public abstract String fingerprint();

    @JsonCreator
    public static CreateDot11MonitoredBSSIDFingerprintRequest create(@Schema(description = "Expected WiFi fingerprint of the access point.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                                     @JsonProperty("fingerprint") @NotEmpty String fingerprint) {
        return builder()
                .fingerprint(fingerprint)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateDot11MonitoredBSSIDFingerprintRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder fingerprint(@NotEmpty String fingerprint);

        public abstract CreateDot11MonitoredBSSIDFingerprintRequest build();
    }
}
