package app.nzyme.core.rest.responses.ethernet.assets.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class AssetIdentitySummaryResponse {

    @JsonProperty("duplicates")
    public abstract List<DuplicateMacFindingResponse> duplicates();

    @JsonProperty("changes")
    public abstract List<IdentityChangeFindingResponse> changes();

    @JsonProperty("links")
    public abstract List<AssetLinkResponse> links();

    public static AssetIdentitySummaryResponse create(List<DuplicateMacFindingResponse> duplicates, List<IdentityChangeFindingResponse> changes, List<AssetLinkResponse> links) {
        return builder()
                .duplicates(duplicates)
                .changes(changes)
                .links(links)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetIdentitySummaryResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder duplicates(List<DuplicateMacFindingResponse> duplicates);
        public abstract Builder changes(List<IdentityChangeFindingResponse> changes);
        public abstract Builder links(List<AssetLinkResponse> links);
        public abstract AssetIdentitySummaryResponse build();
    }
}
