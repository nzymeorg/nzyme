package app.nzyme.core.rest.responses.ethernet.vpn;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class VpnTunnelsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("tunnels")
    public abstract List<VpnTunnelDetailsResponse> tunnels();

    public static VpnTunnelsListResponse create(long total, List<VpnTunnelDetailsResponse> tunnels) {
        return builder()
                .total(total)
                .tunnels(tunnels)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_VpnTunnelsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder tunnels(List<VpnTunnelDetailsResponse> tunnels);

        public abstract VpnTunnelsListResponse build();
    }
}
