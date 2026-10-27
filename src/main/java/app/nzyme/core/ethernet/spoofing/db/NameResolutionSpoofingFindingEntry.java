package app.nzyme.core.ethernet.spoofing.db;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class NameResolutionSpoofingFindingEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract String responderMac();
    public abstract List<String> responderAddresses();
    public abstract List<String> protocols();
    public abstract List<String> names();
    public abstract int nameCount();
    public abstract long responseCount();
    public abstract long unicastCount();
    public abstract int victimCount();
    public abstract List<UUID> tapUuids();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();
    public abstract DateTime createdAt();
    public abstract DateTime updatedAt();

    public static NameResolutionSpoofingFindingEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, String responderMac, List<String> responderAddresses, List<String> protocols, List<String> names, int nameCount, long responseCount, long unicastCount, int victimCount, List<UUID> tapUuids, DateTime firstSeen, DateTime lastSeen, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .responderMac(responderMac)
                .responderAddresses(responderAddresses)
                .protocols(protocols)
                .names(names)
                .nameCount(nameCount)
                .responseCount(responseCount)
                .unicastCount(unicastCount)
                .victimCount(victimCount)
                .tapUuids(tapUuids)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NameResolutionSpoofingFindingEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder uuid(UUID uuid);
        public abstract Builder organizationId(UUID organizationId);
        public abstract Builder tenantId(UUID tenantId);
        public abstract Builder responderMac(String responderMac);
        public abstract Builder responderAddresses(List<String> responderAddresses);
        public abstract Builder protocols(List<String> protocols);
        public abstract Builder names(List<String> names);
        public abstract Builder nameCount(int nameCount);
        public abstract Builder responseCount(long responseCount);
        public abstract Builder unicastCount(long unicastCount);
        public abstract Builder victimCount(int victimCount);
        public abstract Builder tapUuids(List<UUID> tapUuids);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract NameResolutionSpoofingFindingEntry build();
    }
}
