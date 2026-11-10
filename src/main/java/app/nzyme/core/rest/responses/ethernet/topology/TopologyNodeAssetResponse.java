package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.UUID;

@AutoValue
public abstract class TopologyNodeAssetResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("mac")
    public abstract String mac();

    @Schema(description = "Name from the MAC address context, if any.")
    @Nullable
    @JsonProperty("name")
    public abstract String name();

    public static TopologyNodeAssetResponse create(UUID uuid, String mac, @Nullable String name) {
        return builder()
                .uuid(uuid)
                .mac(mac)
                .name(name)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologyNodeAssetResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder mac(String mac);
        public abstract Builder name(String name);

        public abstract TopologyNodeAssetResponse build();
    }

}
