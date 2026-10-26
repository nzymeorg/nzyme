package app.nzyme.core.ethernet.spoofing.db;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class ArpSpoofingFindingEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract String address();
    public abstract String spooferMac();
    public abstract String ownerMac();
    public abstract long claimCount();
    public abstract long replyCount();
    public abstract long unsolicitedCount();
    public abstract long broadcastCount();
    public abstract long ownerClaimCount();
    public abstract int victimCount();
    public abstract List<UUID> tapUuids();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();
    public abstract DateTime createdAt();
    public abstract DateTime updatedAt();

    public static ArpSpoofingFindingEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, String address, String spooferMac, String ownerMac, long claimCount, long replyCount, long unsolicitedCount, long broadcastCount, long ownerClaimCount, int victimCount, List<UUID> tapUuids, DateTime firstSeen, DateTime lastSeen, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .address(address)
                .spooferMac(spooferMac)
                .ownerMac(ownerMac)
                .claimCount(claimCount)
                .replyCount(replyCount)
                .unsolicitedCount(unsolicitedCount)
                .broadcastCount(broadcastCount)
                .ownerClaimCount(ownerClaimCount)
                .victimCount(victimCount)
                .tapUuids(tapUuids)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ArpSpoofingFindingEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder uuid(UUID uuid);
        public abstract Builder organizationId(UUID organizationId);
        public abstract Builder tenantId(UUID tenantId);
        public abstract Builder address(String address);
        public abstract Builder spooferMac(String spooferMac);
        public abstract Builder ownerMac(String ownerMac);
        public abstract Builder claimCount(long claimCount);
        public abstract Builder replyCount(long replyCount);
        public abstract Builder unsolicitedCount(long unsolicitedCount);
        public abstract Builder broadcastCount(long broadcastCount);
        public abstract Builder ownerClaimCount(long ownerClaimCount);
        public abstract Builder victimCount(int victimCount);
        public abstract Builder tapUuids(List<UUID> tapUuids);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract ArpSpoofingFindingEntry build();
    }
}
