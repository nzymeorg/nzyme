package app.nzyme.core.rest.responses.ethernet.dnstunnels;

import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class DnsTunnelConfigurationSectionResponse {

    @JsonProperty("key")
    public abstract String key();

    @JsonProperty("title")
    public abstract String title();

    @JsonProperty("detection")
    public abstract List<ConfigurationEntryResponse> detection();

    @JsonProperty("alerting")
    public abstract List<ConfigurationEntryResponse> alerting();

    public static DnsTunnelConfigurationSectionResponse create(String key, String title, List<ConfigurationEntryResponse> detection, List<ConfigurationEntryResponse> alerting) {
        return builder()
                .key(key)
                .title(title)
                .detection(detection)
                .alerting(alerting)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsTunnelConfigurationSectionResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder key(String key);
        public abstract Builder title(String title);
        public abstract Builder detection(List<ConfigurationEntryResponse> detection);
        public abstract Builder alerting(List<ConfigurationEntryResponse> alerting);
        public abstract DnsTunnelConfigurationSectionResponse build();
    }
}
