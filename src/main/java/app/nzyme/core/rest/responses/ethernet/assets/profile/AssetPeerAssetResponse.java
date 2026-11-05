package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.UUID;
import javax.annotation.Nullable;

@AutoValue
public abstract class AssetPeerAssetResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();
    @JsonProperty("mac")
    public abstract String mac();
    @Nullable
    @JsonProperty("name")
    public abstract String name();

    public static AssetPeerAssetResponse create(UUID uuid, String mac, String name) {
        return builder()
                .uuid(uuid)
                .mac(mac)
                .name(name)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetPeerAssetResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder mac(String mac);
        public abstract Builder name(String name);

        public abstract AssetPeerAssetResponse build();
    }

}
