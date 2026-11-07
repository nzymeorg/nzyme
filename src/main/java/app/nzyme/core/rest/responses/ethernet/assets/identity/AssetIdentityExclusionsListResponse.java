package app.nzyme.core.rest.responses.ethernet.assets.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class AssetIdentityExclusionsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("exclusions")
    public abstract List<AssetIdentityExclusionResponse> exclusions();

    public static AssetIdentityExclusionsListResponse create(long total, List<AssetIdentityExclusionResponse> exclusions) {
        return builder()
                .total(total)
                .exclusions(exclusions)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetIdentityExclusionsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder exclusions(List<AssetIdentityExclusionResponse> exclusions);
        public abstract AssetIdentityExclusionsListResponse build();
    }
}
