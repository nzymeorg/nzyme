package app.nzyme.core.rest.responses.ethernet.dhcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class DHCPFlakyFingerprintAssetsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("assets")
    public abstract List<DHCPFlakyFingerprintAssetResponse> assets();

    public static DHCPFlakyFingerprintAssetsListResponse create(long total, List<DHCPFlakyFingerprintAssetResponse> assets) {
        return builder()
                .total(total)
                .assets(assets)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DHCPFlakyFingerprintAssetsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder assets(List<DHCPFlakyFingerprintAssetResponse> assets);

        public abstract DHCPFlakyFingerprintAssetsListResponse build();
    }
}
