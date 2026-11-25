package app.nzyme.core.rest.responses.ethernet.ipv6transition;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class Ipv6TransitionTunnelsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("tunnels")
    public abstract List<Ipv6TransitionTunnelDetailsResponse> tunnels();

    public static Ipv6TransitionTunnelsListResponse create(long total,
                                                           List<Ipv6TransitionTunnelDetailsResponse> tunnels) {
        return builder()
                .total(total)
                .tunnels(tunnels)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Ipv6TransitionTunnelsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder tunnels(List<Ipv6TransitionTunnelDetailsResponse> tunnels);

        public abstract Ipv6TransitionTunnelsListResponse build();
    }

}
