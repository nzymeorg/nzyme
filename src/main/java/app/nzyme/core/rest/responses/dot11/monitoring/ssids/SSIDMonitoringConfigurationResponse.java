package app.nzyme.core.rest.responses.dot11.monitoring.ssids;

import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class SSIDMonitoringConfigurationResponse {

    @JsonProperty("is_enabled")
    public abstract ConfigurationEntryResponse isEnabled();

    @JsonProperty("eventing_is_enabled")
    public abstract ConfigurationEntryResponse eventingIsEnabled();

    @JsonProperty("dwell_time_minutes")
    public abstract ConfigurationEntryResponse dwellTimeMinutes();

    @JsonProperty("hidden_access_points_enabled")
    public abstract ConfigurationEntryResponse hiddenAccessPointsEnabled();

    public static SSIDMonitoringConfigurationResponse create(ConfigurationEntryResponse isEnabled, ConfigurationEntryResponse eventingIsEnabled, ConfigurationEntryResponse dwellTimeMinutes, ConfigurationEntryResponse hiddenAccessPointsEnabled) {
        return builder()
                .isEnabled(isEnabled)
                .eventingIsEnabled(eventingIsEnabled)
                .dwellTimeMinutes(dwellTimeMinutes)
                .hiddenAccessPointsEnabled(hiddenAccessPointsEnabled)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SSIDMonitoringConfigurationResponse.Builder();
    }


    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder isEnabled(ConfigurationEntryResponse isEnabled);

        public abstract Builder eventingIsEnabled(ConfigurationEntryResponse eventingIsEnabled);

        public abstract Builder dwellTimeMinutes(ConfigurationEntryResponse dwellTimeMinutes);

        public abstract Builder hiddenAccessPointsEnabled(ConfigurationEntryResponse hiddenAccessPointsEnabled);

        public abstract SSIDMonitoringConfigurationResponse build();
    }
}
