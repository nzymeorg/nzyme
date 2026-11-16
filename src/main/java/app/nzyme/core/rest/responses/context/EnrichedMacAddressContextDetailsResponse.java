package app.nzyme.core.rest.responses.context;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

import javax.annotation.Nullable;
import java.util.List;

@AutoValue
public abstract class EnrichedMacAddressContextDetailsResponse {

    @JsonProperty("context")
    @Nullable
    public abstract MacAddressContextDetailsResponse context();

    @JsonProperty("context_type")
    public abstract MacAddressContextTypeResponse contextType();

    @JsonProperty("serves_dot11_monitored_network")
    @Nullable
    public abstract Dot11MonitoredNetworkContextResponse servesDot11MonitoredNetwork();

    @Schema(description = "Tags of the asset that has this MAC address, ordered by name. Empty if there is no "
            + "such asset or it has no tags.")
    @JsonProperty("tags")
    public abstract List<MacAddressTagResponse> tags();

    public static EnrichedMacAddressContextDetailsResponse create(MacAddressContextDetailsResponse context, MacAddressContextTypeResponse contextType, Dot11MonitoredNetworkContextResponse servesDot11MonitoredNetwork, List<MacAddressTagResponse> tags) {
        return builder()
                .context(context)
                .contextType(contextType)
                .servesDot11MonitoredNetwork(servesDot11MonitoredNetwork)
                .tags(tags)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_EnrichedMacAddressContextDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder context(MacAddressContextDetailsResponse context);

        public abstract Builder contextType(MacAddressContextTypeResponse contextType);

        public abstract Builder servesDot11MonitoredNetwork(Dot11MonitoredNetworkContextResponse servesDot11MonitoredNetwork);

        public abstract Builder tags(List<MacAddressTagResponse> tags);

        public abstract EnrichedMacAddressContextDetailsResponse build();
    }
}
