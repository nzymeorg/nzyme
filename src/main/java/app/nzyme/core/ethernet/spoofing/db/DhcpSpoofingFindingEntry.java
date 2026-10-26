package app.nzyme.core.ethernet.spoofing.db;

import app.nzyme.core.ethernet.spoofing.DhcpOfferedParameters;
import app.nzyme.core.ethernet.spoofing.DhcpSpoofingFindingType;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class DhcpSpoofingFindingEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract DhcpSpoofingFindingType type();
    public abstract String serverMac();
    public abstract String serverAddress();
    @Nullable
    public abstract String serverIdentifier();
    @Nullable
    public abstract DhcpOfferedParameters offered();
    @Nullable
    public abstract DhcpOfferedParameters expected();
    public abstract long responseCount();
    public abstract long offerCount();
    public abstract long ackCount();
    public abstract long transactionCount();
    public abstract int victimCount();
    public abstract List<UUID> tapUuids();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();
    public abstract DateTime createdAt();
    public abstract DateTime updatedAt();

    public static DhcpSpoofingFindingEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, DhcpSpoofingFindingType type, String serverMac, String serverAddress, String serverIdentifier, DhcpOfferedParameters offered, DhcpOfferedParameters expected, long responseCount, long offerCount, long ackCount, long transactionCount, int victimCount, List<UUID> tapUuids, DateTime firstSeen, DateTime lastSeen, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .type(type)
                .serverMac(serverMac)
                .serverAddress(serverAddress)
                .serverIdentifier(serverIdentifier)
                .offered(offered)
                .expected(expected)
                .responseCount(responseCount)
                .offerCount(offerCount)
                .ackCount(ackCount)
                .transactionCount(transactionCount)
                .victimCount(victimCount)
                .tapUuids(tapUuids)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DhcpSpoofingFindingEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder uuid(UUID uuid);
        public abstract Builder organizationId(UUID organizationId);
        public abstract Builder tenantId(UUID tenantId);
        public abstract Builder type(DhcpSpoofingFindingType type);
        public abstract Builder serverMac(String serverMac);
        public abstract Builder serverAddress(String serverAddress);
        public abstract Builder serverIdentifier(String serverIdentifier);
        public abstract Builder offered(DhcpOfferedParameters offered);
        public abstract Builder expected(DhcpOfferedParameters expected);
        public abstract Builder responseCount(long responseCount);
        public abstract Builder offerCount(long offerCount);
        public abstract Builder ackCount(long ackCount);
        public abstract Builder transactionCount(long transactionCount);
        public abstract Builder victimCount(int victimCount);
        public abstract Builder tapUuids(List<UUID> tapUuids);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract DhcpSpoofingFindingEntry build();
    }
}
