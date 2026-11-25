package app.nzyme.core.rest.requests;

import app.nzyme.core.dot11.channelswitches.ChannelSwitchRules;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Body for updating the channel switch monitor configuration of a monitored WiFi network.")
@AutoValue
public abstract class UpdateChannelSwitchNetworkMonitorConfiguration {

    @Max(ChannelSwitchRules.BURST_THRESHOLD_MAXIMUM)
    @Min(ChannelSwitchRules.BURST_THRESHOLD_MINIMUM)
    public abstract long burstThreshold();

    @JsonCreator
    public static UpdateChannelSwitchNetworkMonitorConfiguration create(@Schema(description = "Beacons per second with a channel switch announcement from one BSSID that trigger an alert. Between 10 and 1000.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                                        @JsonProperty("burst_threshold") long burstThreshold) {
        return builder()
                .burstThreshold(burstThreshold)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateChannelSwitchNetworkMonitorConfiguration.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder burstThreshold(long burstThreshold);

        public abstract UpdateChannelSwitchNetworkMonitorConfiguration build();
    }
}
