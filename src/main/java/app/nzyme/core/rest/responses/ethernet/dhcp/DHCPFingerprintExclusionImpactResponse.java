package app.nzyme.core.rest.responses.ethernet.dhcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class DHCPFingerprintExclusionImpactResponse {

    @JsonProperty("matching_assets")
    public abstract int matchingAssets();

    @JsonProperty("assets")
    public abstract List<DHCPFingerprintExclusionMatchedAssetResponse> assets();

    public static DHCPFingerprintExclusionImpactResponse create(int matchingAssets, List<DHCPFingerprintExclusionMatchedAssetResponse> assets) {
        return builder()
                .matchingAssets(matchingAssets)
                .assets(assets)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DHCPFingerprintExclusionImpactResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder matchingAssets(int matchingAssets);

        public abstract Builder assets(List<DHCPFingerprintExclusionMatchedAssetResponse> assets);

        public abstract DHCPFingerprintExclusionImpactResponse build();
    }
}
