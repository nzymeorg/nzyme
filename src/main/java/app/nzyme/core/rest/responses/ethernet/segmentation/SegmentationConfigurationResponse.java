package app.nzyme.core.rest.responses.ethernet.segmentation;

import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class SegmentationConfigurationResponse {

    @JsonProperty("enabled")
    public abstract ConfigurationEntryResponse enabled();

    @JsonProperty("settle_seconds")
    public abstract ConfigurationEntryResponse settleSeconds();

    @JsonProperty("initial_backfill_hours")
    public abstract ConfigurationEntryResponse initialBackfillHours();

    @JsonProperty("flow_retention_time_days")
    public abstract ConfigurationEntryResponse flowRetentionTimeDays();

    @JsonProperty("finding_retention_time_days")
    public abstract ConfigurationEntryResponse findingRetentionTimeDays();

    public static SegmentationConfigurationResponse create(ConfigurationEntryResponse enabled,
                                                           ConfigurationEntryResponse settleSeconds,
                                                           ConfigurationEntryResponse initialBackfillHours,
                                                           ConfigurationEntryResponse flowRetentionTimeDays,
                                                           ConfigurationEntryResponse findingRetentionTimeDays) {
        return builder()
                .enabled(enabled)
                .settleSeconds(settleSeconds)
                .initialBackfillHours(initialBackfillHours)
                .flowRetentionTimeDays(flowRetentionTimeDays)
                .findingRetentionTimeDays(findingRetentionTimeDays)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentationConfigurationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder enabled(ConfigurationEntryResponse enabled);
        public abstract Builder settleSeconds(ConfigurationEntryResponse settleSeconds);
        public abstract Builder initialBackfillHours(ConfigurationEntryResponse initialBackfillHours);
        public abstract Builder flowRetentionTimeDays(ConfigurationEntryResponse flowRetentionTimeDays);
        public abstract Builder findingRetentionTimeDays(ConfigurationEntryResponse findingRetentionTimeDays);

        public abstract SegmentationConfigurationResponse build();
    }

}
