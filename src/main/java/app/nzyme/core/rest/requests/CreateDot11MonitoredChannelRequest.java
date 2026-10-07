package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "Body for adding an expected channel to a monitored WiFi network.")
@AutoValue
public abstract class CreateDot11MonitoredChannelRequest {

    @Min(0)
    public abstract long frequency();

    @JsonCreator
    public static CreateDot11MonitoredChannelRequest create(@Schema(description = "Channel frequency in MHz.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                            @JsonProperty("frequency") long frequency) {
        return builder()
                .frequency(frequency)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateDot11MonitoredChannelRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder frequency(long frequency);

        public abstract CreateDot11MonitoredChannelRequest build();
    }
}
