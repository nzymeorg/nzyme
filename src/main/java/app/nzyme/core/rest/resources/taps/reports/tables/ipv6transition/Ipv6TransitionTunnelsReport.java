package app.nzyme.core.rest.resources.taps.reports.tables.ipv6transition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class Ipv6TransitionTunnelsReport {

    public abstract List<Ipv6TransitionTunnelReport> tunnels();

    @JsonCreator
    public static Ipv6TransitionTunnelsReport create(@JsonProperty("tunnels") List<Ipv6TransitionTunnelReport> tunnels) {
        return builder()
                .tunnels(tunnels)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Ipv6TransitionTunnelsReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder tunnels(List<Ipv6TransitionTunnelReport> tunnels);

        public abstract Ipv6TransitionTunnelsReport build();
    }

}
