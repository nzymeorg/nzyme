package app.nzyme.core.rest.responses.ethernet.assets.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class AssetIdentityExclusionImpactResponse {

    @JsonProperty("matching_assets")
    public abstract int matchingAssets();

    @JsonProperty("assets")
    public abstract List<AssetIdentityExclusionMatchedAssetResponse> assets();

    public static AssetIdentityExclusionImpactResponse create(int matchingAssets, List<AssetIdentityExclusionMatchedAssetResponse> assets) {
        return builder()
                .matchingAssets(matchingAssets)
                .assets(assets)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetIdentityExclusionImpactResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder matchingAssets(int matchingAssets);
        public abstract Builder assets(List<AssetIdentityExclusionMatchedAssetResponse> assets);
        public abstract AssetIdentityExclusionImpactResponse build();
    }
}
