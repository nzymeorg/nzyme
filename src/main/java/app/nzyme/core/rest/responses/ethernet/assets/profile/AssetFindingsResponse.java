package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class AssetFindingsResponse {

    @JsonProperty("findings")
    public abstract List<AssetFindingResponse> findings();

    public static AssetFindingsResponse create(List<AssetFindingResponse> findings) {
        return builder()
                .findings(findings)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetFindingsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder findings(List<AssetFindingResponse> findings);

        public abstract AssetFindingsResponse build();
    }

}
