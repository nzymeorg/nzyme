package app.nzyme.core.rest.responses.ethernet.assets.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class AssetIdentityConfigurationResponse {

    @JsonProperty("sections")
    public abstract List<AssetIdentityConfigurationSectionResponse> sections();

    public static AssetIdentityConfigurationResponse create(List<AssetIdentityConfigurationSectionResponse> sections) {
        return builder()
                .sections(sections)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetIdentityConfigurationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sections(List<AssetIdentityConfigurationSectionResponse> sections);
        public abstract AssetIdentityConfigurationResponse build();
    }
}
