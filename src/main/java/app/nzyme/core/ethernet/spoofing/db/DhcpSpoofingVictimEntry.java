package app.nzyme.core.ethernet.spoofing.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class DhcpSpoofingVictimEntry {

    public abstract long id();
    public abstract long findingId();
    public abstract String mac();
    @Nullable
    public abstract String assignedAddress();
    public abstract long transactionCount();
    public abstract long leaseCount();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();

    public static DhcpSpoofingVictimEntry create(long id, long findingId, String mac, String assignedAddress, long transactionCount, long leaseCount, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .id(id)
                .findingId(findingId)
                .mac(mac)
                .assignedAddress(assignedAddress)
                .transactionCount(transactionCount)
                .leaseCount(leaseCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DhcpSpoofingVictimEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder findingId(long findingId);
        public abstract Builder mac(String mac);
        public abstract Builder assignedAddress(String assignedAddress);
        public abstract Builder transactionCount(long transactionCount);
        public abstract Builder leaseCount(long leaseCount);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract DhcpSpoofingVictimEntry build();
    }
}
