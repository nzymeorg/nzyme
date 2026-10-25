package app.nzyme.core.ethernet.icmp.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class IcmpExclusionEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract String name();
    @Nullable
    public abstract String description();
    public abstract boolean enabled();
    @Nullable
    public abstract List<String> detectionTypes();
    @Nullable
    public abstract String sourceCidr();
    @Nullable
    public abstract String sourceMac();
    @Nullable
    public abstract String gatewayCidr();
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

    public static IcmpExclusionEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, String name, String description, boolean enabled, List<String> detectionTypes, String sourceCidr, String sourceMac, String gatewayCidr, long matchCount, DateTime lastMatchedAt, UUID createdByUserId, String createdByName, String createdByEmail, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .name(name)
                .description(description)
                .enabled(enabled)
                .detectionTypes(detectionTypes)
                .sourceCidr(sourceCidr)
                .sourceMac(sourceMac)
                .gatewayCidr(gatewayCidr)
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
        return new AutoValue_IcmpExclusionEntry.Builder();
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
        public abstract Builder detectionTypes(List<String> detectionTypes);
        public abstract Builder sourceCidr(String sourceCidr);
        public abstract Builder sourceMac(String sourceMac);
        public abstract Builder gatewayCidr(String gatewayCidr);
        public abstract Builder matchCount(long matchCount);
        public abstract Builder lastMatchedAt(DateTime lastMatchedAt);
        public abstract Builder createdByUserId(UUID createdByUserId);
        public abstract Builder createdByName(String createdByName);
        public abstract Builder createdByEmail(String createdByEmail);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract IcmpExclusionEntry build();
    }
}
