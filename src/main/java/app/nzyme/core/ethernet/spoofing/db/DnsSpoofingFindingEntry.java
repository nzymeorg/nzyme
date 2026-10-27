package app.nzyme.core.ethernet.spoofing.db;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class DnsSpoofingFindingEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract String serverAddress();
    public abstract String spooferMac();
    public abstract String ownerMac();
    public abstract long forgedCount();
    public abstract long wonCount();
    public abstract long unansweredCount();
    public abstract List<String> names();
    public abstract int nameCount();
    public abstract List<String> answers();
    public abstract int victimCount();
    public abstract List<UUID> tapUuids();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();
    public abstract DateTime createdAt();
    public abstract DateTime updatedAt();

    public static DnsSpoofingFindingEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, String serverAddress, String spooferMac, String ownerMac, long forgedCount, long wonCount, long unansweredCount, List<String> names, int nameCount, List<String> answers, int victimCount, List<UUID> tapUuids, DateTime firstSeen, DateTime lastSeen, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .serverAddress(serverAddress)
                .spooferMac(spooferMac)
                .ownerMac(ownerMac)
                .forgedCount(forgedCount)
                .wonCount(wonCount)
                .unansweredCount(unansweredCount)
                .names(names)
                .nameCount(nameCount)
                .answers(answers)
                .victimCount(victimCount)
                .tapUuids(tapUuids)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsSpoofingFindingEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);

        public abstract Builder uuid(UUID uuid);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract Builder serverAddress(String serverAddress);

        public abstract Builder spooferMac(String spooferMac);

        public abstract Builder ownerMac(String ownerMac);

        public abstract Builder forgedCount(long forgedCount);

        public abstract Builder wonCount(long wonCount);

        public abstract Builder unansweredCount(long unansweredCount);

        public abstract Builder names(List<String> names);

        public abstract Builder nameCount(int nameCount);

        public abstract Builder answers(List<String> answers);

        public abstract Builder victimCount(int victimCount);

        public abstract Builder tapUuids(List<UUID> tapUuids);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract DnsSpoofingFindingEntry build();
    }
}
