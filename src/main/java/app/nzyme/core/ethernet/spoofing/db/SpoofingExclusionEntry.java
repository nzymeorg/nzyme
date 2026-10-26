package app.nzyme.core.ethernet.spoofing.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class SpoofingExclusionEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract String name();
    @Nullable
    public abstract String description();
    public abstract boolean enabled();
    @Nullable
    public abstract List<String> types();
    @Nullable
    public abstract String addressCidr();
    @Nullable
    public abstract String mac();
    public abstract long matchCount();
    @Nullable
    public abstract DateTime lastMatchedAt();
    @Nullable
    public abstract UUID createdByUserId();
    @Nullable
    public abstract String createdByName();
    @Nullable
    public abstract String createdByEmail();
    public abstract DateTime createdAt();
    public abstract DateTime updatedAt();

    public static SpoofingExclusionEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, String name, String description, boolean enabled, List<String> types, String addressCidr, String mac, long matchCount, DateTime lastMatchedAt, UUID createdByUserId, String createdByName, String createdByEmail, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .name(name)
                .description(description)
                .enabled(enabled)
                .types(types)
                .addressCidr(addressCidr)
                .mac(mac)
                .matchCount(matchCount)
                .lastMatchedAt(lastMatchedAt)
                .createdByUserId(createdByUserId)
                .createdByName(createdByName)
                .createdByEmail(createdByEmail)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SpoofingExclusionEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder uuid(UUID uuid);
        public abstract Builder organizationId(UUID organizationId);
        public abstract Builder tenantId(UUID tenantId);
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder enabled(boolean enabled);
        public abstract Builder types(List<String> types);
        public abstract Builder addressCidr(String addressCidr);
        public abstract Builder mac(String mac);
        public abstract Builder matchCount(long matchCount);
        public abstract Builder lastMatchedAt(DateTime lastMatchedAt);
        public abstract Builder createdByUserId(UUID createdByUserId);
        public abstract Builder createdByName(String createdByName);
        public abstract Builder createdByEmail(String createdByEmail);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract SpoofingExclusionEntry build();
    }
}
