package app.nzyme.core.rest.responses.ethernet.segmentation;

import app.nzyme.core.rest.responses.ethernet.assets.AssetTagResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class SelectorOptionsResponse {

    @JsonProperty("segments")
    public abstract List<SegmentResponse> segments();

    @JsonProperty("tags")
    public abstract List<AssetTagResponse> tags();

    @Schema(description = "The protocols the taps detect.")
    @JsonProperty("protocols")
    public abstract List<String> protocols();

    public static SelectorOptionsResponse create(List<SegmentResponse> segments,
                                                 List<AssetTagResponse> tags,
                                                 List<String> protocols) {
        return builder()
                .segments(segments)
                .tags(tags)
                .protocols(protocols)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SelectorOptionsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder segments(List<SegmentResponse> segments);
        public abstract Builder tags(List<AssetTagResponse> tags);
        public abstract Builder protocols(List<String> protocols);

        public abstract SelectorOptionsResponse build();
    }

}
