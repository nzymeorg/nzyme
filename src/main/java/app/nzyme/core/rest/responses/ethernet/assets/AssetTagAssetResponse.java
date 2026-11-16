package app.nzyme.core.rest.responses.ethernet.assets;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class AssetTagAssetResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("mac")
    public abstract String mac();

    @Nullable
    @JsonProperty("name")
    public abstract String name();

    @JsonProperty("addresses")
    public abstract List<String> addresses();

    public static AssetTagAssetResponse create(UUID uuid,
                                               String mac,
                                               String name,
                                               List<String> addresses) {
        return builder()
                .uuid(uuid)
                .mac(mac)
                .name(name)
                .addresses(addresses)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetTagAssetResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder mac(String mac);
        public abstract Builder name(String name);
        public abstract Builder addresses(List<String> addresses);

        public abstract AssetTagAssetResponse build();
    }

}
