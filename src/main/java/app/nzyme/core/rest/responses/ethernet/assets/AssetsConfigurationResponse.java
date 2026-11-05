package app.nzyme.core.rest.responses.ethernet.assets;

import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class AssetsConfigurationResponse {

    @JsonProperty("statistics_retention_time_days")
    public abstract ConfigurationEntryResponse statisticsRetentionTimeDays();

    @JsonProperty("activity_retention_time_days")
    public abstract ConfigurationEntryResponse activityRetentionTimeDays();

    @JsonProperty("attributes_retention_time_days")
    public abstract ConfigurationEntryResponse attributesRetentionTimeDays();

    @JsonProperty("communication_retention_time_days")
    public abstract ConfigurationEntryResponse communicationRetentionTimeDays();

    @JsonProperty("dhcp_fingerprint_monitoring_enabled")
    public abstract ConfigurationEntryResponse dhcpFingerprintMonitoringEnabled();

    public static AssetsConfigurationResponse create(ConfigurationEntryResponse statisticsRetentionTimeDays,
                                                     ConfigurationEntryResponse activityRetentionTimeDays,
                                                     ConfigurationEntryResponse attributesRetentionTimeDays,
                                                     ConfigurationEntryResponse communicationRetentionTimeDays,
                                                     ConfigurationEntryResponse dhcpFingerprintMonitoringEnabled) {
        return builder()
                .statisticsRetentionTimeDays(statisticsRetentionTimeDays)
                .activityRetentionTimeDays(activityRetentionTimeDays)
                .attributesRetentionTimeDays(attributesRetentionTimeDays)
                .communicationRetentionTimeDays(communicationRetentionTimeDays)
                .dhcpFingerprintMonitoringEnabled(dhcpFingerprintMonitoringEnabled)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetsConfigurationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder statisticsRetentionTimeDays(ConfigurationEntryResponse statisticsRetentionTimeDays);

        public abstract Builder activityRetentionTimeDays(ConfigurationEntryResponse activityRetentionTimeDays);
        public abstract Builder attributesRetentionTimeDays(ConfigurationEntryResponse attributesRetentionTimeDays);
        public abstract Builder communicationRetentionTimeDays(ConfigurationEntryResponse communicationRetentionTimeDays);
        public abstract Builder dhcpFingerprintMonitoringEnabled(ConfigurationEntryResponse dhcpFingerprintMonitoringEnabled);

        public abstract AssetsConfigurationResponse build();
    }
}
