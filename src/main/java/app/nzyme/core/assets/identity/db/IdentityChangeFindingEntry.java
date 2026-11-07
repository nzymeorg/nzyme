package app.nzyme.core.assets.identity.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class IdentityChangeFindingEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract String mac();
    public abstract int score();
    public abstract List<IdentityChange> changes();
    @Nullable
    public abstract String previousFingerprint();
    @Nullable
    public abstract String newFingerprint();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();
    public abstract DateTime createdAt();
    public abstract DateTime updatedAt();

    public static IdentityChangeFindingEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, String mac, int score, List<IdentityChange> changes, String previousFingerprint, String newFingerprint, DateTime firstSeen, DateTime lastSeen, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .mac(mac)
                .score(score)
                .changes(changes)
                .previousFingerprint(previousFingerprint)
                .newFingerprint(newFingerprint)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IdentityChangeFindingEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder uuid(UUID uuid);
        public abstract Builder organizationId(UUID organizationId);
        public abstract Builder tenantId(UUID tenantId);
        public abstract Builder mac(String mac);
        public abstract Builder score(int score);
        public abstract Builder changes(List<IdentityChange> changes);
        public abstract Builder previousFingerprint(String previousFingerprint);
        public abstract Builder newFingerprint(String newFingerprint);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract IdentityChangeFindingEntry build();
    }
}
