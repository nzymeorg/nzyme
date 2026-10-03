package app.nzyme.core.rest.responses.context;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class NetworkContextListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("networks")
    public abstract List<NetworkContextDetailsResponse> networks();

    public static NetworkContextListResponse create(long total, List<NetworkContextDetailsResponse> networks) {
        return builder()
                .total(total)
                .networks(networks)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NetworkContextListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder networks(List<NetworkContextDetailsResponse> networks);

        public abstract NetworkContextListResponse build();
    }
}
