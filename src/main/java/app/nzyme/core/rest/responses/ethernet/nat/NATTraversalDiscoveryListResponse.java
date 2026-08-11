package app.nzyme.core.rest.responses.ethernet.nat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class NATTraversalDiscoveryListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("discoveries")
    public abstract List<NATTraversalDiscoveryDetailsResponse> discoveries();

    public static NATTraversalDiscoveryListResponse create(long total, List<NATTraversalDiscoveryDetailsResponse> discoveries) {
        return builder()
                .total(total)
                .discoveries(discoveries)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NATTraversalDiscoveryListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder discoveries(List<NATTraversalDiscoveryDetailsResponse> discoveries);

        public abstract NATTraversalDiscoveryListResponse build();
    }
}
