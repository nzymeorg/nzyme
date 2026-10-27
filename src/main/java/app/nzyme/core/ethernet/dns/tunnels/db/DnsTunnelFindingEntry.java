package app.nzyme.core.ethernet.dns.tunnels.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@AutoValue
public abstract class DnsTunnelFindingEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract String clientAddress();
    @Nullable
    public abstract String clientMac();
    public abstract String domain();
    public abstract List<String> serverAddresses();
    public abstract List<String> indicators();
    public abstract long queryCount();
    public abstract long distinctNameCount();
    public abstract long negativeCount();
    public abstract double averageEntropy();
    public abstract double maximumEntropy();
    public abstract double averageNameLength();
    public abstract int maximumNameLength();
    public abstract Map<String, Long> recordTypes();
    public abstract List<String> names();
    public abstract int nameCount();
    public abstract List<UUID> tapUuids();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();
    public abstract DateTime createdAt();
    public abstract DateTime updatedAt();

    public static DnsTunnelFindingEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, String clientAddress, String clientMac, String domain, List<String> serverAddresses, List<String> indicators, long queryCount, long distinctNameCount, long negativeCount, double averageEntropy, double maximumEntropy, double averageNameLength, int maximumNameLength, Map<String, Long> recordTypes, List<String> names, int nameCount, List<UUID> tapUuids, DateTime firstSeen, DateTime lastSeen, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .clientAddress(clientAddress)
                .clientMac(clientMac)
                .domain(domain)
                .serverAddresses(serverAddresses)
                .indicators(indicators)
                .queryCount(queryCount)
                .distinctNameCount(distinctNameCount)
                .negativeCount(negativeCount)
                .averageEntropy(averageEntropy)
                .maximumEntropy(maximumEntropy)
                .averageNameLength(averageNameLength)
                .maximumNameLength(maximumNameLength)
                .recordTypes(recordTypes)
                .names(names)
                .nameCount(nameCount)
                .tapUuids(tapUuids)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsTunnelFindingEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder uuid(UUID uuid);
        public abstract Builder organizationId(UUID organizationId);
        public abstract Builder tenantId(UUID tenantId);
        public abstract Builder clientAddress(String clientAddress);
        public abstract Builder clientMac(String clientMac);
        public abstract Builder domain(String domain);
        public abstract Builder serverAddresses(List<String> serverAddresses);
        public abstract Builder indicators(List<String> indicators);
        public abstract Builder queryCount(long queryCount);
        public abstract Builder distinctNameCount(long distinctNameCount);
        public abstract Builder negativeCount(long negativeCount);
        public abstract Builder averageEntropy(double averageEntropy);
        public abstract Builder maximumEntropy(double maximumEntropy);
        public abstract Builder averageNameLength(double averageNameLength);
        public abstract Builder maximumNameLength(int maximumNameLength);
        public abstract Builder recordTypes(Map<String, Long> recordTypes);
        public abstract Builder names(List<String> names);
        public abstract Builder nameCount(int nameCount);
        public abstract Builder tapUuids(List<UUID> tapUuids);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract DnsTunnelFindingEntry build();
    }
}
