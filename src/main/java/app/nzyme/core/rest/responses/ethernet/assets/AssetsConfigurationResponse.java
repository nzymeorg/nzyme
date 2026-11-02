package app.nzyme.core.rest.responses.ethernet.assets;

import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class AssetsConfigurationResponse {

    @JsonProperty("statistics_retention_time_days")
    public abstract ConfigurationEntryResponse statisticsRetentionTimeDays();

    @JsonProperty("dhcp_fingerprint_monitoring_enabled")
    public abstract ConfigurationEntryResponse dhcpFingerprintMonitoringEnabled();

    public static AssetsConfigurationResponse create(ConfigurationEntryResponse statisticsRetentionTimeDays, ConfigurationEntryResponse dhcpFingerprintMonitoringEnabled) {
        return builder()
                .statisticsRetentionTimeDays(statisticsRetentionTimeDays)
                .dhcpFingerprintMonitoringEnabled(dhcpFingerprintMonitoringEnabled)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetsConfigurationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder statisticsRetentionTimeDays(ConfigurationEntryResponse statisticsRetentionTimeDays);

        public abstract Builder dhcpFingerprintMonitoringEnabled(ConfigurationEntryResponse dhcpFingerprintMonitoringEnabled);

        public abstract AssetsConfigurationResponse build();
    }
}
