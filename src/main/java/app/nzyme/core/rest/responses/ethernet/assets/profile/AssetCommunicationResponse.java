package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

@AutoValue
public abstract class AssetCommunicationResponse {

    @JsonProperty("mac")
    public abstract String mac();
    @JsonProperty("asset_uuid")
    public abstract UUID assetUuid();
    @Nullable
    @JsonProperty("name")
    public abstract String name();
    @JsonProperty("edges")
    public abstract List<AssetCommunicationEdgeResponse> edges();
    @JsonProperty("retention_days")
    public abstract int retentionDays();
    @Nullable
    @JsonProperty("origin")
    public abstract AssetCommunicationOriginResponse origin();

    public static AssetCommunicationResponse create(String mac, UUID assetUuid, String name, List<AssetCommunicationEdgeResponse> edges, int retentionDays, AssetCommunicationOriginResponse origin) {
        return builder()
                .mac(mac)
                .assetUuid(assetUuid)
                .name(name)
                .edges(edges)
                .retentionDays(retentionDays)
                .origin(origin)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetCommunicationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder mac(String mac);
        public abstract Builder assetUuid(UUID assetUuid);
        public abstract Builder name(String name);
        public abstract Builder edges(List<AssetCommunicationEdgeResponse> edges);

        public abstract Builder retentionDays(int retentionDays);

        public abstract Builder origin(AssetCommunicationOriginResponse origin);

        public abstract AssetCommunicationResponse build();
    }

}
