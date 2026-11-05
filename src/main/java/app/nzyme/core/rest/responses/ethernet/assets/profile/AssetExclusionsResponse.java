package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class AssetExclusionsResponse {

    @JsonProperty("exclusions")
    public abstract List<AssetExclusionMatchResponse> exclusions();

    public static AssetExclusionsResponse create(List<AssetExclusionMatchResponse> exclusions) {
        return builder()
                .exclusions(exclusions)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetExclusionsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder exclusions(List<AssetExclusionMatchResponse> exclusions);

        public abstract AssetExclusionsResponse build();
    }

}
