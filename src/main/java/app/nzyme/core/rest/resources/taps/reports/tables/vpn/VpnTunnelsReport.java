package app.nzyme.core.rest.resources.taps.reports.tables.vpn;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class VpnTunnelsReport {

    public abstract List<VpnTunnelReport> tunnels();

    @JsonCreator
    public static VpnTunnelsReport create(@JsonProperty("tunnels") List<VpnTunnelReport> tunnels) {
        return builder()
                .tunnels(tunnels)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_VpnTunnelsReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder tunnels(List<VpnTunnelReport> tunnels);

        public abstract VpnTunnelsReport build();
    }
}
