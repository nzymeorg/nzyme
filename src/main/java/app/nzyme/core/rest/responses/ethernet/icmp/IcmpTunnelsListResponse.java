package app.nzyme.core.rest.responses.ethernet.icmp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class IcmpTunnelsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("tunnels")
    public abstract List<IcmpTunnelResponse> tunnels();

    public static IcmpTunnelsListResponse create(long total, List<IcmpTunnelResponse> tunnels) {
        return builder().total(total).tunnels(tunnels).build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpTunnelsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder tunnels(List<IcmpTunnelResponse> tunnels);
        public abstract IcmpTunnelsListResponse build();
    }
}
