package app.nzyme.core.ethernet.icmp;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class IcmpConfiguration {

    public abstract boolean redirectDetectionEnabled();
    public abstract boolean tracerouteDetectionEnabled();
    public abstract int detectionWindowMinutes();
    public abstract int tracerouteMinimumRouters();
    public abstract int detectionRetentionTimeDays();
    public abstract boolean alertOnRedirect();
    public abstract boolean alertOnTraceroute();
    public abstract boolean tunnelDetectionEnabled();
    public abstract int tunnelMinimumPayloadBytes();
    public abstract int tunnelMinimumPackets();
    public abstract boolean alertOnTunnel();

    public static IcmpConfiguration create(boolean redirectDetectionEnabled, boolean tracerouteDetectionEnabled, int detectionWindowMinutes, int tracerouteMinimumRouters, int detectionRetentionTimeDays, boolean alertOnRedirect, boolean alertOnTraceroute, boolean tunnelDetectionEnabled, int tunnelMinimumPayloadBytes, int tunnelMinimumPackets, boolean alertOnTunnel) {
        return builder()
                .redirectDetectionEnabled(redirectDetectionEnabled)
                .tracerouteDetectionEnabled(tracerouteDetectionEnabled)
                .detectionWindowMinutes(detectionWindowMinutes)
                .tracerouteMinimumRouters(tracerouteMinimumRouters)
                .detectionRetentionTimeDays(detectionRetentionTimeDays)
                .alertOnRedirect(alertOnRedirect)
                .alertOnTraceroute(alertOnTraceroute)
                .tunnelDetectionEnabled(tunnelDetectionEnabled)
                .tunnelMinimumPayloadBytes(tunnelMinimumPayloadBytes)
                .tunnelMinimumPackets(tunnelMinimumPackets)
                .alertOnTunnel(alertOnTunnel)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpConfiguration.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder redirectDetectionEnabled(boolean redirectDetectionEnabled);
        public abstract Builder tracerouteDetectionEnabled(boolean tracerouteDetectionEnabled);
        public abstract Builder detectionWindowMinutes(int detectionWindowMinutes);
        public abstract Builder tracerouteMinimumRouters(int tracerouteMinimumRouters);
        public abstract Builder detectionRetentionTimeDays(int detectionRetentionTimeDays);
        public abstract Builder alertOnRedirect(boolean alertOnRedirect);
        public abstract Builder alertOnTraceroute(boolean alertOnTraceroute);
        public abstract Builder tunnelDetectionEnabled(boolean tunnelDetectionEnabled);
        public abstract Builder tunnelMinimumPayloadBytes(int tunnelMinimumPayloadBytes);
        public abstract Builder tunnelMinimumPackets(int tunnelMinimumPackets);
        public abstract Builder alertOnTunnel(boolean alertOnTunnel);
        public abstract IcmpConfiguration build();
    }
}
