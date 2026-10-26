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

    public abstract boolean dhcpDetectionEnabled();
    public abstract int dhcpLearningPeriodHours();
    public abstract int dhcpDetectionWindowMinutes();
    public abstract int dhcpRetentionTimeDays();
    public abstract boolean dhcpAlertOnRogueServer();
    public abstract boolean dhcpAlertOnParametersChanged();

    public static SpoofingConfiguration create(boolean arpDetectionEnabled, int arpDetectionWindowMinutes, int arpOwnerLookbackHours, int arpMinimumClaims, int arpRetentionTimeDays, boolean arpAlertOnNew, boolean dhcpDetectionEnabled, int dhcpLearningPeriodHours, int dhcpDetectionWindowMinutes, int dhcpRetentionTimeDays, boolean dhcpAlertOnRogueServer, boolean dhcpAlertOnParametersChanged) {
        return builder()
                .arpDetectionEnabled(arpDetectionEnabled)
                .arpDetectionWindowMinutes(arpDetectionWindowMinutes)
                .arpOwnerLookbackHours(arpOwnerLookbackHours)
                .arpMinimumClaims(arpMinimumClaims)
                .arpRetentionTimeDays(arpRetentionTimeDays)
                .arpAlertOnNew(arpAlertOnNew)
                .dhcpDetectionEnabled(dhcpDetectionEnabled)
                .dhcpLearningPeriodHours(dhcpLearningPeriodHours)
                .dhcpDetectionWindowMinutes(dhcpDetectionWindowMinutes)
                .dhcpRetentionTimeDays(dhcpRetentionTimeDays)
                .dhcpAlertOnRogueServer(dhcpAlertOnRogueServer)
                .dhcpAlertOnParametersChanged(dhcpAlertOnParametersChanged)
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
        public abstract Builder dhcpDetectionEnabled(boolean dhcpDetectionEnabled);
        public abstract Builder dhcpLearningPeriodHours(int dhcpLearningPeriodHours);
        public abstract Builder dhcpDetectionWindowMinutes(int dhcpDetectionWindowMinutes);
        public abstract Builder dhcpRetentionTimeDays(int dhcpRetentionTimeDays);
        public abstract Builder dhcpAlertOnRogueServer(boolean dhcpAlertOnRogueServer);
        public abstract Builder dhcpAlertOnParametersChanged(boolean dhcpAlertOnParametersChanged);
        public abstract SpoofingConfiguration build();
    }
}
