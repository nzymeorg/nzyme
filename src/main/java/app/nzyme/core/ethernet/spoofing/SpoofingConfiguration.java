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

    public abstract boolean nameresDetectionEnabled();
    public abstract int nameresDetectionWindowMinutes();
    public abstract int nameresLlmnrNameThreshold();
    public abstract int nameresNbtnsNameThreshold();
    public abstract int nameresMdnsNameThreshold();
    public abstract int nameresRetentionTimeDays();
    public abstract int nameresResponsesRetentionTimeDays();
    public abstract boolean nameresAlertOnNew();
    public abstract boolean dnsDetectionEnabled();
    public abstract int dnsDetectionWindowMinutes();
    public abstract int dnsOwnerLookbackHours();
    public abstract int dnsMinimumForgedReplies();
    public abstract int dnsRetentionTimeDays();
    public abstract boolean dnsAlertOnNew();

    public static SpoofingConfiguration create(boolean arpDetectionEnabled, int arpDetectionWindowMinutes, int arpOwnerLookbackHours, int arpMinimumClaims, int arpRetentionTimeDays, boolean arpAlertOnNew, boolean dhcpDetectionEnabled, int dhcpLearningPeriodHours, int dhcpDetectionWindowMinutes, int dhcpRetentionTimeDays, boolean dhcpAlertOnRogueServer, boolean dhcpAlertOnParametersChanged, boolean nameresDetectionEnabled, int nameresDetectionWindowMinutes, int nameresLlmnrNameThreshold, int nameresNbtnsNameThreshold, int nameresMdnsNameThreshold, int nameresRetentionTimeDays, int nameresResponsesRetentionTimeDays, boolean nameresAlertOnNew, boolean dnsDetectionEnabled, int dnsDetectionWindowMinutes, int dnsOwnerLookbackHours, int dnsMinimumForgedReplies, int dnsRetentionTimeDays, boolean dnsAlertOnNew) {
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
                .nameresDetectionEnabled(nameresDetectionEnabled)
                .nameresDetectionWindowMinutes(nameresDetectionWindowMinutes)
                .nameresLlmnrNameThreshold(nameresLlmnrNameThreshold)
                .nameresNbtnsNameThreshold(nameresNbtnsNameThreshold)
                .nameresMdnsNameThreshold(nameresMdnsNameThreshold)
                .nameresRetentionTimeDays(nameresRetentionTimeDays)
                .nameresResponsesRetentionTimeDays(nameresResponsesRetentionTimeDays)
                .nameresAlertOnNew(nameresAlertOnNew)
                .dnsDetectionEnabled(dnsDetectionEnabled)
                .dnsDetectionWindowMinutes(dnsDetectionWindowMinutes)
                .dnsOwnerLookbackHours(dnsOwnerLookbackHours)
                .dnsMinimumForgedReplies(dnsMinimumForgedReplies)
                .dnsRetentionTimeDays(dnsRetentionTimeDays)
                .dnsAlertOnNew(dnsAlertOnNew)
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
        public abstract Builder nameresDetectionEnabled(boolean nameresDetectionEnabled);
        public abstract Builder nameresDetectionWindowMinutes(int nameresDetectionWindowMinutes);
        public abstract Builder nameresLlmnrNameThreshold(int nameresLlmnrNameThreshold);
        public abstract Builder nameresNbtnsNameThreshold(int nameresNbtnsNameThreshold);
        public abstract Builder nameresMdnsNameThreshold(int nameresMdnsNameThreshold);
        public abstract Builder nameresRetentionTimeDays(int nameresRetentionTimeDays);
        public abstract Builder nameresResponsesRetentionTimeDays(int nameresResponsesRetentionTimeDays);
        public abstract Builder nameresAlertOnNew(boolean nameresAlertOnNew);
        public abstract Builder dnsDetectionEnabled(boolean dnsDetectionEnabled);
        public abstract Builder dnsDetectionWindowMinutes(int dnsDetectionWindowMinutes);
        public abstract Builder dnsOwnerLookbackHours(int dnsOwnerLookbackHours);
        public abstract Builder dnsMinimumForgedReplies(int dnsMinimumForgedReplies);
        public abstract Builder dnsRetentionTimeDays(int dnsRetentionTimeDays);
        public abstract Builder dnsAlertOnNew(boolean dnsAlertOnNew);
        public abstract SpoofingConfiguration build();
    }
}
