package app.nzyme.core.rest.responses.ethernet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class L4AddressContextResponse {

    @JsonProperty("networks")
    public abstract List<L4AddressNetworkContextResponse> networks();

    public static L4AddressContextResponse create(List<L4AddressNetworkContextResponse> networks) {
        return builder()
                .networks(networks)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_L4AddressContextResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder networks(List<L4AddressNetworkContextResponse> networks);

        public abstract L4AddressContextResponse build();
    }
}
