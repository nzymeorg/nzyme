package app.nzyme.core.ethernet.icmp.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class IcmpTunnelEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract String sourceAddress();
    @Nullable
    public abstract String sourceMac();
    public abstract String destinationAddress();
    @Nullable
    public abstract String destinationMac();
    public abstract long maxPayloadBytes();
    public abstract long packetCount();
    public abstract List<UUID> tapUuids();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();
    public abstract DateTime createdAt();
    public abstract DateTime updatedAt();

    public static IcmpTunnelEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, String sourceAddress, String sourceMac, String destinationAddress, String destinationMac, long maxPayloadBytes, long packetCount, List<UUID> tapUuids, DateTime firstSeen, DateTime lastSeen, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .sourceAddress(sourceAddress)
                .sourceMac(sourceMac)
                .destinationAddress(destinationAddress)
                .destinationMac(destinationMac)
                .maxPayloadBytes(maxPayloadBytes)
                .packetCount(packetCount)
                .tapUuids(tapUuids)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpTunnelEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder uuid(UUID uuid);
        public abstract Builder organizationId(UUID organizationId);
        public abstract Builder tenantId(UUID tenantId);
        public abstract Builder sourceAddress(String sourceAddress);
        public abstract Builder sourceMac(String sourceMac);
        public abstract Builder destinationAddress(String destinationAddress);
        public abstract Builder destinationMac(String destinationMac);
        public abstract Builder maxPayloadBytes(long maxPayloadBytes);
        public abstract Builder packetCount(long packetCount);
        public abstract Builder tapUuids(List<UUID> tapUuids);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract IcmpTunnelEntry build();
    }
}
