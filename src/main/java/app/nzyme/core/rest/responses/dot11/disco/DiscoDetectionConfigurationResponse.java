package app.nzyme.core.rest.responses.dot11.disco;

import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class DiscoDetectionConfigurationResponse {

    @JsonProperty("general")
    public abstract List<ConfigurationEntryResponse> general();

    @JsonProperty("frames")
    public abstract List<ConfigurationEntryResponse> frames();

    @JsonProperty("connections")
    public abstract List<ConfigurationEntryResponse> connections();

    public static DiscoDetectionConfigurationResponse create(List<ConfigurationEntryResponse> general, List<ConfigurationEntryResponse> frames, List<ConfigurationEntryResponse> connections) {
        return builder()
                .general(general)
                .frames(frames)
                .connections(connections)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DiscoDetectionConfigurationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder general(List<ConfigurationEntryResponse> general);

        public abstract Builder frames(List<ConfigurationEntryResponse> frames);

        public abstract Builder connections(List<ConfigurationEntryResponse> connections);

        public abstract DiscoDetectionConfigurationResponse build();
    }

}
