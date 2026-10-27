package app.nzyme.core.ethernet.spoofing.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class NameResolutionSpoofingVictimEntry {

    public abstract long id();
    public abstract long findingId();
    public abstract String mac();
    @Nullable
    public abstract String address();
    public abstract List<String> names();
    public abstract long responseCount();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();

    public static NameResolutionSpoofingVictimEntry create(long id, long findingId, String mac, String address, List<String> names, long responseCount, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .id(id)
                .findingId(findingId)
                .mac(mac)
                .address(address)
                .names(names)
                .responseCount(responseCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NameResolutionSpoofingVictimEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder findingId(long findingId);
        public abstract Builder mac(String mac);
        public abstract Builder address(String address);
        public abstract Builder names(List<String> names);
        public abstract Builder responseCount(long responseCount);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract NameResolutionSpoofingVictimEntry build();
    }
}
