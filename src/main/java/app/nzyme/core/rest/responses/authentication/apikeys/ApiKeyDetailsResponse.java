package app.nzyme.core.rest.responses.authentication.apikeys;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class ApiKeyDetailsResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();
    @JsonProperty("user_id")
    public abstract UUID userId();
    @JsonProperty("name")
    public abstract String name();
    @JsonProperty("last_activity") @Nullable
    public abstract DateTime lastActivity();
    @JsonProperty("expires_at") @Nullable
    public abstract DateTime expiresAt();
    @JsonProperty("created_at") @Nullable
    public abstract DateTime createdAt();

    public static ApiKeyDetailsResponse create(UUID uuid, UUID userId, String name, DateTime lastActivity, DateTime expiresAt, DateTime createdAt) {
        return builder()
                .uuid(uuid)
                .userId(userId)
                .name(name)
                .lastActivity(lastActivity)
                .expiresAt(expiresAt)
                .createdAt(createdAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ApiKeyDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder userId(UUID userId);

        public abstract Builder name(String name);

        public abstract Builder lastActivity(DateTime lastActivity);

        public abstract Builder expiresAt(DateTime expiresAt);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract ApiKeyDetailsResponse build();
    }
}
