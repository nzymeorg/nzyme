package app.nzyme.core.assets.identity.db;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class AssetLinkEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract String predecessorMac();
    public abstract String successorMac();
    public abstract String kind();
    public abstract int confidence();
    public abstract List<AssetLinkEvidence> evidence();
    public abstract String status();
    public abstract DateTime predecessorLastSeen();
    public abstract DateTime successorFirstSeen();
    public abstract DateTime detectedAt();
    public abstract DateTime createdAt();
    public abstract DateTime updatedAt();

    public static AssetLinkEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, String predecessorMac, String successorMac, String kind, int confidence, List<AssetLinkEvidence> evidence, String status, DateTime predecessorLastSeen, DateTime successorFirstSeen, DateTime detectedAt, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .predecessorMac(predecessorMac)
                .successorMac(successorMac)
                .kind(kind)
                .confidence(confidence)
                .evidence(evidence)
                .status(status)
                .predecessorLastSeen(predecessorLastSeen)
                .successorFirstSeen(successorFirstSeen)
                .detectedAt(detectedAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetLinkEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder uuid(UUID uuid);
        public abstract Builder organizationId(UUID organizationId);
        public abstract Builder tenantId(UUID tenantId);
        public abstract Builder predecessorMac(String predecessorMac);
        public abstract Builder successorMac(String successorMac);
        public abstract Builder kind(String kind);
        public abstract Builder confidence(int confidence);
        public abstract Builder evidence(List<AssetLinkEvidence> evidence);
        public abstract Builder status(String status);
        public abstract Builder predecessorLastSeen(DateTime predecessorLastSeen);
        public abstract Builder successorFirstSeen(DateTime successorFirstSeen);
        public abstract Builder detectedAt(DateTime detectedAt);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract AssetLinkEntry build();
    }
}
