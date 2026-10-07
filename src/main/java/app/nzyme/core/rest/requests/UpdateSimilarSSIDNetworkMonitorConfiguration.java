package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Body for updating the similar SSID monitor configuration of a monitored WiFi network.")
@AutoValue
public abstract class UpdateSimilarSSIDNetworkMonitorConfiguration {

    @Max(100)
    @Min(0)
    public abstract long threshold();

    @JsonCreator
    public static UpdateSimilarSSIDNetworkMonitorConfiguration create(@Schema(description = "Similarity of an SSID in percent that triggers an alert. Between 0 and 100.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                                      @JsonProperty("threshold") long threshold) {
        return builder()
                .threshold(threshold)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateSimilarSSIDNetworkMonitorConfiguration.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder threshold(long threshold);

        public abstract UpdateSimilarSSIDNetworkMonitorConfiguration build();
    }
}
