package app.nzyme.core.rest.responses.ethernet.spoofing;

import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class SpoofingConfigurationResponse {

    @JsonProperty("detection")
    public abstract List<ConfigurationEntryResponse> detection();

    @JsonProperty("alerting")
    public abstract List<ConfigurationEntryResponse> alerting();

    public static SpoofingConfigurationResponse create(List<ConfigurationEntryResponse> detection, List<ConfigurationEntryResponse> alerting) {
        return builder()
                .detection(detection)
                .alerting(alerting)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SpoofingConfigurationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder detection(List<ConfigurationEntryResponse> detection);
        public abstract Builder alerting(List<ConfigurationEntryResponse> alerting);
        public abstract SpoofingConfigurationResponse build();
    }
}
