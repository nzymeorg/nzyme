package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "Body for adding an expected BSSID to a monitored WiFi network.")
@AutoValue
public abstract class CreateDot11MonitoredBSSIDRequest {

    @NotEmpty
    public abstract String bssid();

    @JsonCreator
    public static CreateDot11MonitoredBSSIDRequest create(@Schema(description = "BSSID MAC address like \"00:11:22:33:44:55\".", requiredMode = Schema.RequiredMode.REQUIRED)
                                                          @JsonProperty("bssid") @NotEmpty String bssid) {
        return builder()
                .bssid(bssid)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateDot11MonitoredBSSIDRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bssid(@NotEmpty String bssid);

        public abstract CreateDot11MonitoredBSSIDRequest build();
    }
}
