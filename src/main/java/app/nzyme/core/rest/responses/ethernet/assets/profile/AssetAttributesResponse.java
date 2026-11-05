package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;
import java.util.Map;

@AutoValue
public abstract class AssetAttributesResponse {

    @JsonProperty("attributes")
    public abstract Map<String, List<AssetAttributeResponse>> attributes();

    public static AssetAttributesResponse create(Map<String, List<AssetAttributeResponse>> attributes) {
        return builder()
                .attributes(attributes)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetAttributesResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder attributes(Map<String, List<AssetAttributeResponse>> attributes);

        public abstract AssetAttributesResponse build();
    }

}
