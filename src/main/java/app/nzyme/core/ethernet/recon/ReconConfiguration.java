package app.nzyme.core.ethernet.recon;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ReconConfiguration {

    public abstract boolean detectionEnabled();
    public abstract boolean sourceTcpEnabled();
    public abstract boolean sourceUdpEnabled();
    public abstract boolean sourceArpEnabled();
    public abstract boolean sourceIcmpEnabled();
    public abstract boolean sourceDnsEnabled();
    public abstract boolean fingerprintingEnabled();
    public abstract boolean includeExternalSources();
    public abstract boolean includeExternalTargets();
    public abstract int detectionWindowMinutes();
    public abstract int portScanMinimumPorts();
    public abstract int hostSweepMinimumHosts();
    public abstract int arpSweepMinimumHosts();
    public abstract int minimumFailurePercent();
    public abstract int scanRetentionTimeDays();
    public abstract boolean slowScanEnabled();
    public abstract int slowScanMemoryDays();
    public abstract int slowScanMinimumTargets();
    public abstract boolean alertOnNewScan();
    public abstract boolean alertOnNewFingerprinting();

    public static ReconConfiguration create(boolean detectionEnabled, boolean sourceTcpEnabled, boolean sourceUdpEnabled, boolean sourceArpEnabled, boolean sourceIcmpEnabled, boolean sourceDnsEnabled, boolean fingerprintingEnabled, boolean includeExternalSources, boolean includeExternalTargets, int detectionWindowMinutes, int portScanMinimumPorts, int hostSweepMinimumHosts, int arpSweepMinimumHosts, int minimumFailurePercent, int scanRetentionTimeDays, boolean slowScanEnabled, int slowScanMemoryDays, int slowScanMinimumTargets, boolean alertOnNewScan, boolean alertOnNewFingerprinting) {
        return builder()
                .detectionEnabled(detectionEnabled)
                .sourceTcpEnabled(sourceTcpEnabled)
                .sourceUdpEnabled(sourceUdpEnabled)
                .sourceArpEnabled(sourceArpEnabled)
                .sourceIcmpEnabled(sourceIcmpEnabled)
                .sourceDnsEnabled(sourceDnsEnabled)
                .fingerprintingEnabled(fingerprintingEnabled)
                .includeExternalSources(includeExternalSources)
                .includeExternalTargets(includeExternalTargets)
                .detectionWindowMinutes(detectionWindowMinutes)
                .portScanMinimumPorts(portScanMinimumPorts)
                .hostSweepMinimumHosts(hostSweepMinimumHosts)
                .arpSweepMinimumHosts(arpSweepMinimumHosts)
                .minimumFailurePercent(minimumFailurePercent)
                .scanRetentionTimeDays(scanRetentionTimeDays)
                .slowScanEnabled(slowScanEnabled)
                .slowScanMemoryDays(slowScanMemoryDays)
                .slowScanMinimumTargets(slowScanMinimumTargets)
                .alertOnNewScan(alertOnNewScan)
                .alertOnNewFingerprinting(alertOnNewFingerprinting)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ReconConfiguration.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder detectionEnabled(boolean detectionEnabled);
        public abstract Builder sourceTcpEnabled(boolean sourceTcpEnabled);
        public abstract Builder sourceUdpEnabled(boolean sourceUdpEnabled);
        public abstract Builder sourceArpEnabled(boolean sourceArpEnabled);
        public abstract Builder sourceIcmpEnabled(boolean sourceIcmpEnabled);
        public abstract Builder sourceDnsEnabled(boolean sourceDnsEnabled);
        public abstract Builder fingerprintingEnabled(boolean fingerprintingEnabled);
        public abstract Builder includeExternalSources(boolean includeExternalSources);
        public abstract Builder includeExternalTargets(boolean includeExternalTargets);
        public abstract Builder detectionWindowMinutes(int detectionWindowMinutes);
        public abstract Builder portScanMinimumPorts(int portScanMinimumPorts);
        public abstract Builder hostSweepMinimumHosts(int hostSweepMinimumHosts);
        public abstract Builder arpSweepMinimumHosts(int arpSweepMinimumHosts);
        public abstract Builder minimumFailurePercent(int minimumFailurePercent);
        public abstract Builder scanRetentionTimeDays(int scanRetentionTimeDays);
        public abstract Builder slowScanEnabled(boolean slowScanEnabled);
        public abstract Builder slowScanMemoryDays(int slowScanMemoryDays);
        public abstract Builder slowScanMinimumTargets(int slowScanMinimumTargets);
        public abstract Builder alertOnNewScan(boolean alertOnNewScan);
        public abstract Builder alertOnNewFingerprinting(boolean alertOnNewFingerprinting);
        public abstract ReconConfiguration build();
    }
}
