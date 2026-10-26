package app.nzyme.core.ethernet.spoofing.db;

import app.nzyme.core.ethernet.spoofing.DhcpOfferedParameters;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class DhcpSpoofingServerEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract String mac();
    public abstract String address();
    @Nullable
    public abstract String serverIdentifier();
    public abstract boolean trusted();
    @Nullable
    public abstract String trustSource();
    @Nullable
    public abstract DhcpOfferedParameters baseline();
    @Nullable
    public abstract DateTime baselineUpdatedAt();
    @Nullable
    public abstract DhcpOfferedParameters latest();
    public abstract long responseCount();
    public abstract long offerCount();
    public abstract long ackCount();
    public abstract long transactionCount();
    public abstract List<UUID> tapUuids();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();
    public abstract DateTime createdAt();
    public abstract DateTime updatedAt();

    public static DhcpSpoofingServerEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, String mac, String address, String serverIdentifier, boolean trusted, String trustSource, DhcpOfferedParameters baseline, DateTime baselineUpdatedAt, DhcpOfferedParameters latest, long responseCount, long offerCount, long ackCount, long transactionCount, List<UUID> tapUuids, DateTime firstSeen, DateTime lastSeen, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .mac(mac)
                .address(address)
                .serverIdentifier(serverIdentifier)
                .trusted(trusted)
                .trustSource(trustSource)
                .baseline(baseline)
                .baselineUpdatedAt(baselineUpdatedAt)
                .latest(latest)
                .responseCount(responseCount)
                .offerCount(offerCount)
                .ackCount(ackCount)
                .transactionCount(transactionCount)
                .tapUuids(tapUuids)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DhcpSpoofingServerEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder uuid(UUID uuid);
        public abstract Builder organizationId(UUID organizationId);
        public abstract Builder tenantId(UUID tenantId);
        public abstract Builder mac(String mac);
        public abstract Builder address(String address);
        public abstract Builder serverIdentifier(String serverIdentifier);
        public abstract Builder trusted(boolean trusted);
        public abstract Builder trustSource(String trustSource);
        public abstract Builder baseline(DhcpOfferedParameters baseline);
        public abstract Builder baselineUpdatedAt(DateTime baselineUpdatedAt);
        public abstract Builder latest(DhcpOfferedParameters latest);
        public abstract Builder responseCount(long responseCount);
        public abstract Builder offerCount(long offerCount);
        public abstract Builder ackCount(long ackCount);
        public abstract Builder transactionCount(long transactionCount);
        public abstract Builder tapUuids(List<UUID> tapUuids);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract DhcpSpoofingServerEntry build();
    }
}
