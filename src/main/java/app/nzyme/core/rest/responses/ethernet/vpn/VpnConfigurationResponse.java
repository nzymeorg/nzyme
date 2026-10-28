package app.nzyme.core.rest.responses.ethernet.vpn;

import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class VpnConfigurationResponse {

    @JsonProperty("display")
    public abstract List<ConfigurationEntryResponse> display();

    public static VpnConfigurationResponse create(List<ConfigurationEntryResponse> display) {
        return builder()
                .display(display)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_VpnConfigurationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder display(List<ConfigurationEntryResponse> display);

        public abstract VpnConfigurationResponse build();
    }
}
