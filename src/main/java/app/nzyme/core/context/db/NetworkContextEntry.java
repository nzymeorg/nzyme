package app.nzyme.core.context.db;

import app.nzyme.core.ethernet.CIDR;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class NetworkContextEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract CIDR network();
    @Nullable
    public abstract String name();
    public abstract String description();
    @Nullable
    public abstract String notes();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract DateTime createdAt();
    public abstract DateTime updatedAt();

    public static NetworkContextEntry create(long id, UUID uuid, CIDR network, String name, String description, String notes, UUID organizationId, UUID tenantId, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .network(network)
                .name(name)
                .description(description)
                .notes(notes)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NetworkContextEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);

        public abstract Builder uuid(UUID uuid);

        public abstract Builder network(CIDR network);

        public abstract Builder name(String name);

        public abstract Builder description(String description);

        public abstract Builder notes(String notes);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract NetworkContextEntry build();
    }
}
