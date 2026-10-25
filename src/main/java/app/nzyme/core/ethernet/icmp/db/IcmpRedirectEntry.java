package app.nzyme.core.ethernet.icmp.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class IcmpRedirectEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract String senderAddress();
    @Nullable
    public abstract String senderMac();
    public abstract String gatewayAddress();
    public abstract String targetAddress();
    @Nullable
    public abstract String targetMac();
    public abstract List<String> redirectedDestinations();
    public abstract long packetCount();
    public abstract List<UUID> tapUuids();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();
    public abstract DateTime createdAt();
    public abstract DateTime updatedAt();

    public static IcmpRedirectEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, String senderAddress, String senderMac, String gatewayAddress, String targetAddress, String targetMac, List<String> redirectedDestinations, long packetCount, List<UUID> tapUuids, DateTime firstSeen, DateTime lastSeen, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .senderAddress(senderAddress)
                .senderMac(senderMac)
                .gatewayAddress(gatewayAddress)
                .targetAddress(targetAddress)
                .targetMac(targetMac)
                .redirectedDestinations(redirectedDestinations)
                .packetCount(packetCount)
                .tapUuids(tapUuids)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpRedirectEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder uuid(UUID uuid);
        public abstract Builder organizationId(UUID organizationId);
        public abstract Builder tenantId(UUID tenantId);
        public abstract Builder senderAddress(String senderAddress);
        public abstract Builder senderMac(String senderMac);
        public abstract Builder gatewayAddress(String gatewayAddress);
        public abstract Builder targetAddress(String targetAddress);
        public abstract Builder targetMac(String targetMac);
        public abstract Builder redirectedDestinations(List<String> redirectedDestinations);
        public abstract Builder packetCount(long packetCount);
        public abstract Builder tapUuids(List<UUID> tapUuids);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract IcmpRedirectEntry build();
    }
}
