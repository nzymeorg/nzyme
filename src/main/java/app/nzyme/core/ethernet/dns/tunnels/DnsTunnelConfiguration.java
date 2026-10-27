package app.nzyme.core.ethernet.dns.tunnels;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class DnsTunnelConfiguration {

    public abstract boolean detectionEnabled();
    public abstract int detectionWindowMinutes();
    public abstract int minimumDistinctNames();
    public abstract int entropyThresholdTenths();
    public abstract int nameLengthThreshold();
    public abstract int recordTypeShareThreshold();
    public abstract int retentionTimeDays();
    public abstract boolean alertOnNew();

    public double entropyThreshold() {
        return entropyThresholdTenths() / 10.0;
    }

    public static DnsTunnelConfiguration create(boolean detectionEnabled, int detectionWindowMinutes, int minimumDistinctNames, int entropyThresholdTenths, int nameLengthThreshold, int recordTypeShareThreshold, int retentionTimeDays, boolean alertOnNew) {
        return builder()
                .detectionEnabled(detectionEnabled)
                .detectionWindowMinutes(detectionWindowMinutes)
                .minimumDistinctNames(minimumDistinctNames)
                .entropyThresholdTenths(entropyThresholdTenths)
                .nameLengthThreshold(nameLengthThreshold)
                .recordTypeShareThreshold(recordTypeShareThreshold)
                .retentionTimeDays(retentionTimeDays)
                .alertOnNew(alertOnNew)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsTunnelConfiguration.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder detectionEnabled(boolean detectionEnabled);
        public abstract Builder detectionWindowMinutes(int detectionWindowMinutes);
        public abstract Builder minimumDistinctNames(int minimumDistinctNames);
        public abstract Builder entropyThresholdTenths(int entropyThresholdTenths);
        public abstract Builder nameLengthThreshold(int nameLengthThreshold);
        public abstract Builder recordTypeShareThreshold(int recordTypeShareThreshold);
        public abstract Builder retentionTimeDays(int retentionTimeDays);
        public abstract Builder alertOnNew(boolean alertOnNew);
        public abstract DnsTunnelConfiguration build();
    }
}
