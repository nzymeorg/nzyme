package app.nzyme.core.ethernet.nat.db;

import app.nzyme.core.ethernet.l4.db.L4AddressData;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class NATTraversalDiscoveryEntry {

    public abstract String sessionKey();
    public abstract String transport();
    public abstract String mappedAddresses();
    public abstract DateTime mostRecentSegmentTime();
    public abstract DateTime firstSeen();
    @Nullable
    public abstract DateTime terminatedAt();
    @Nullable
    public abstract L4AddressData source();
    @Nullable
    public abstract L4AddressData destination();

    public static NATTraversalDiscoveryEntry create(String sessionKey, String transport, String mappedAddresses, DateTime mostRecentSegmentTime, DateTime firstSeen, DateTime terminatedAt, L4AddressData source, L4AddressData destination) {
        return builder()
                .sessionKey(sessionKey)
                .transport(transport)
                .mappedAddresses(mappedAddresses)
                .mostRecentSegmentTime(mostRecentSegmentTime)
                .firstSeen(firstSeen)
                .terminatedAt(terminatedAt)
                .source(source)
                .destination(destination)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NATTraversalDiscoveryEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sessionKey(String sessionKey);

        public abstract Builder transport(String transport);

        public abstract Builder mappedAddresses(String mappedAddresses);

        public abstract Builder mostRecentSegmentTime(DateTime mostRecentSegmentTime);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder terminatedAt(DateTime terminatedAt);

        public abstract Builder source(L4AddressData source);

        public abstract Builder destination(L4AddressData destination);

        public abstract NATTraversalDiscoveryEntry build();
    }
}
