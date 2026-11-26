package app.nzyme.core.rest.responses.dot11.channelswitches;

import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class ChannelSwitchAlertConfigurationResponse {

    @JsonProperty("general")
    public abstract List<ConfigurationEntryResponse> general();

    @JsonProperty("other_networks")
    public abstract List<ConfigurationEntryResponse> otherNetworks();

    public static ChannelSwitchAlertConfigurationResponse create(List<ConfigurationEntryResponse> general,
                                                                 List<ConfigurationEntryResponse> otherNetworks) {
        return new AutoValue_ChannelSwitchAlertConfigurationResponse(general, otherNetworks);
    }

}
