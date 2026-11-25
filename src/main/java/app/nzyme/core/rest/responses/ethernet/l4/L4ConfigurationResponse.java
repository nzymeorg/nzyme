package app.nzyme.core.rest.responses.ethernet.l4;

import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class L4ConfigurationResponse {

    @JsonProperty("cgnat_is_internal")
    public abstract ConfigurationEntryResponse cgnatIsInternal();

    @JsonProperty("internal_networks")
    public abstract List<String> internalNetworks();

    @JsonProperty("internal_networks_limit")
    public abstract int internalNetworksLimit();

    @JsonProperty("learn_internal_networks")
    public abstract ConfigurationEntryResponse learnInternalNetworks();

    @JsonProperty("learned_internal_networks")
    public abstract List<String> learnedInternalNetworks();

    @JsonProperty("learned_internal_networks_limit")
    public abstract int learnedInternalNetworksLimit();

    @JsonProperty("pending_rewrites")
    public abstract long pendingRewrites();

    public static L4ConfigurationResponse create(ConfigurationEntryResponse cgnatIsInternal,
                                                 List<String> internalNetworks,
                                                 int internalNetworksLimit,
                                                 ConfigurationEntryResponse learnInternalNetworks,
                                                 List<String> learnedInternalNetworks,
                                                 int learnedInternalNetworksLimit,
                                                 long pendingRewrites) {
        return builder()
                .cgnatIsInternal(cgnatIsInternal)
                .internalNetworks(internalNetworks)
                .internalNetworksLimit(internalNetworksLimit)
                .learnInternalNetworks(learnInternalNetworks)
                .learnedInternalNetworks(learnedInternalNetworks)
                .learnedInternalNetworksLimit(learnedInternalNetworksLimit)
                .pendingRewrites(pendingRewrites)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_L4ConfigurationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder cgnatIsInternal(ConfigurationEntryResponse cgnatIsInternal);

        public abstract Builder internalNetworks(List<String> internalNetworks);

        public abstract Builder internalNetworksLimit(int internalNetworksLimit);

        public abstract Builder learnInternalNetworks(ConfigurationEntryResponse learnInternalNetworks);

        public abstract Builder learnedInternalNetworks(List<String> learnedInternalNetworks);

        public abstract Builder learnedInternalNetworksLimit(int learnedInternalNetworksLimit);

        public abstract Builder pendingRewrites(long pendingRewrites);

        public abstract L4ConfigurationResponse build();
    }

}
