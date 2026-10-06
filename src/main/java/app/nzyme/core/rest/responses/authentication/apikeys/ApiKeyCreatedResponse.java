package app.nzyme.core.rest.responses.authentication.apikeys;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class ApiKeyCreatedResponse {

    @JsonProperty("key")
    public abstract String key();

    @JsonProperty("expires_at") @Nullable
    public abstract DateTime expiresAt();

    public static ApiKeyCreatedResponse create(String key, DateTime expiresAt) {
        return builder()
                .key(key)
                .expiresAt(expiresAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ApiKeyCreatedResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder key(String key);

        public abstract Builder expiresAt(DateTime expiresAt);

        public abstract ApiKeyCreatedResponse build();
    }

}
