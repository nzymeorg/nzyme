package app.nzyme.core.ethernet.spoofing.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class DnsSpoofingVictimEntry {

    public abstract long id();
    public abstract long findingId();
    public abstract String mac();
    @Nullable
    public abstract String address();
    public abstract List<String> names();
    public abstract long forgedCount();
    public abstract long wonCount();
    public abstract long unansweredCount();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();

    public static DnsSpoofingVictimEntry create(long id, long findingId, String mac, String address, List<String> names, long forgedCount, long wonCount, long unansweredCount, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .id(id)
                .findingId(findingId)
                .mac(mac)
                .address(address)
                .names(names)
                .forgedCount(forgedCount)
                .wonCount(wonCount)
                .unansweredCount(unansweredCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsSpoofingVictimEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);

        public abstract Builder findingId(long findingId);

        public abstract Builder mac(String mac);

        public abstract Builder address(String address);

        public abstract Builder names(List<String> names);

        public abstract Builder forgedCount(long forgedCount);

        public abstract Builder wonCount(long wonCount);

        public abstract Builder unansweredCount(long unansweredCount);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract DnsSpoofingVictimEntry build();
    }
}
