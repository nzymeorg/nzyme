package app.nzyme.core.security.authentication.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class ApiKeyEntry {

    public abstract UUID uuid();
    public abstract UUID userId();
    public abstract String name();
    public abstract String key();
    @Nullable
    public abstract DateTime lastActivity();
    @Nullable
    public abstract DateTime expiresAt();
    public abstract DateTime createdAt();

    public static ApiKeyEntry create(UUID uuid, UUID userId, String name, String key, DateTime lastActivity, DateTime expiresAt, DateTime createdAt) {
        return builder()
                .uuid(uuid)
                .userId(userId)
                .name(name)
                .key(key)
                .lastActivity(lastActivity)
                .expiresAt(expiresAt)
                .createdAt(createdAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ApiKeyEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder userId(UUID userId);

        public abstract Builder name(String name);

        public abstract Builder key(String key);

        public abstract Builder lastActivity(DateTime lastActivity);

        public abstract Builder expiresAt(DateTime expiresAt);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract ApiKeyEntry build();
    }
}
