package app.nzyme.core.ethernet.spoofing.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class ArpSpoofingVictimEntry {

    public abstract long id();
    public abstract long findingId();
    public abstract String mac();
    @Nullable
    public abstract String address();
    public abstract long packetCount();
    public abstract long unsolicitedCount();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();

    public static ArpSpoofingVictimEntry create(long id, long findingId, String mac, String address, long packetCount, long unsolicitedCount, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .id(id)
                .findingId(findingId)
                .mac(mac)
                .address(address)
                .packetCount(packetCount)
                .unsolicitedCount(unsolicitedCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ArpSpoofingVictimEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder findingId(long findingId);
        public abstract Builder mac(String mac);
        public abstract Builder address(String address);
        public abstract Builder packetCount(long packetCount);
        public abstract Builder unsolicitedCount(long unsolicitedCount);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract ArpSpoofingVictimEntry build();
    }
}
