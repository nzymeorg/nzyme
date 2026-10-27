package app.nzyme.core.rest.responses.ethernet.dnstunnels;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.UUID;

@AutoValue
public abstract class DnsTunnelTapResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @Nullable
    @JsonProperty("name")
    public abstract String name();

    public static DnsTunnelTapResponse create(UUID uuid, String name) {
        return builder()
                .uuid(uuid)
                .name(name)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsTunnelTapResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder name(String name);
        public abstract DnsTunnelTapResponse build();
    }
}
