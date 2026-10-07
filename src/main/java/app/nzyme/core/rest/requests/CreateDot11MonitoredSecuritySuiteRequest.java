package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "Body for adding an expected security suite to a monitored WiFi network.")
@AutoValue
public abstract class CreateDot11MonitoredSecuritySuiteRequest {

    @NotEmpty
    public abstract String suite();

    @JsonCreator
    public static CreateDot11MonitoredSecuritySuiteRequest create(@Schema(description = "Security suite identifier like \"WPA2-PSK-CCMP/CCMP\", or \"NONE\" for open networks.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                                  @JsonProperty("suite") String suite) {
        return builder()
                .suite(suite)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateDot11MonitoredSecuritySuiteRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder suite(String suite);

        public abstract CreateDot11MonitoredSecuritySuiteRequest build();
    }

}
