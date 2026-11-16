package app.nzyme.core.rest.responses.ethernet.assets;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class AssetTagsListResponse {

    @Schema(description = "How many tags there are in total, on all pages.")
    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("tags")
    public abstract List<AssetTagResponse> tags();

    public static AssetTagsListResponse create(long total, List<AssetTagResponse> tags) {
        return builder()
                .total(total)
                .tags(tags)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetTagsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder tags(List<AssetTagResponse> tags);

        public abstract AssetTagsListResponse build();
    }

}
