package app.nzyme.core.rest.responses.ethernet.assets;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class AssetTagAssetsListResponse {

    @Schema(description = "How many assets carry the tag in total, on all pages.")
    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("assets")
    public abstract List<AssetTagAssetResponse> assets();

    public static AssetTagAssetsListResponse create(long total, List<AssetTagAssetResponse> assets) {
        return builder()
                .total(total)
                .assets(assets)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetTagAssetsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder assets(List<AssetTagAssetResponse> assets);

        public abstract AssetTagAssetsListResponse build();
    }

}
