package app.nzyme.core.ethernet.spoofing;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class SpoofingConfiguration {

    public abstract boolean arpDetectionEnabled();
    public abstract int arpDetectionWindowMinutes();
    public abstract int arpOwnerLookbackHours();
    public abstract int arpMinimumClaims();
    public abstract int arpRetentionTimeDays();
    public abstract boolean arpAlertOnNew();

    public static SpoofingConfiguration create(boolean arpDetectionEnabled, int arpDetectionWindowMinutes, int arpOwnerLookbackHours, int arpMinimumClaims, int arpRetentionTimeDays, boolean arpAlertOnNew) {
        return builder()
                .arpDetectionEnabled(arpDetectionEnabled)
                .arpDetectionWindowMinutes(arpDetectionWindowMinutes)
                .arpOwnerLookbackHours(arpOwnerLookbackHours)
                .arpMinimumClaims(arpMinimumClaims)
                .arpRetentionTimeDays(arpRetentionTimeDays)
                .arpAlertOnNew(arpAlertOnNew)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SpoofingConfiguration.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder arpDetectionEnabled(boolean arpDetectionEnabled);
        public abstract Builder arpDetectionWindowMinutes(int arpDetectionWindowMinutes);
        public abstract Builder arpOwnerLookbackHours(int arpOwnerLookbackHours);
        public abstract Builder arpMinimumClaims(int arpMinimumClaims);
        public abstract Builder arpRetentionTimeDays(int arpRetentionTimeDays);
        public abstract Builder arpAlertOnNew(boolean arpAlertOnNew);
        public abstract SpoofingConfiguration build();
    }
}
