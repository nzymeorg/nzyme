package app.nzyme.core.rest.responses.ethernet.assets.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class AssetLinksListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("links")
    public abstract List<AssetLinkResponse> links();

    public static AssetLinksListResponse create(long total, List<AssetLinkResponse> links) {
        return builder()
                .total(total)
                .links(links)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetLinksListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder links(List<AssetLinkResponse> links);
        public abstract AssetLinksListResponse build();
    }
}
