package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.UUID;

@AutoValue
public abstract class DisplayTokenResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("token")
    public abstract String token();

    public static DisplayTokenResponse create(UUID uuid, String token) {
        return builder()
                .uuid(uuid)
                .token(token)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DisplayTokenResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder token(String token);

        public abstract DisplayTokenResponse build();
    }

}
