package app.nzyme.core.ethernet.dhcp.db;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class DHCPFlakyFingerprintAssetEntry {

    public abstract String clientMac();
    public abstract List<String> transactionTypes();
    public abstract List<String> fingerprints();
    public abstract long fingerprintCount();
    public abstract long changeCount();
    public abstract long transactionCount();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();

    public static DHCPFlakyFingerprintAssetEntry create(String clientMac, List<String> transactionTypes, List<String> fingerprints, long fingerprintCount, long changeCount, long transactionCount, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .clientMac(clientMac)
                .transactionTypes(transactionTypes)
                .fingerprints(fingerprints)
                .fingerprintCount(fingerprintCount)
                .changeCount(changeCount)
                .transactionCount(transactionCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DHCPFlakyFingerprintAssetEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder clientMac(String clientMac);

        public abstract Builder transactionTypes(List<String> transactionTypes);

        public abstract Builder fingerprints(List<String> fingerprints);

        public abstract Builder fingerprintCount(long fingerprintCount);

        public abstract Builder changeCount(long changeCount);

        public abstract Builder transactionCount(long transactionCount);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract DHCPFlakyFingerprintAssetEntry build();
    }
}
