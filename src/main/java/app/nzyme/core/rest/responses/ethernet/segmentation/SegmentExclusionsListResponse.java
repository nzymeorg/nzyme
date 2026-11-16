package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@AutoValue
public abstract class SegmentExclusionsListResponse {

    @JsonProperty("exclusions")
    public abstract List<SegmentExclusionResponse> exclusions();

    @Schema(description = "How many excluded destinations a segment can have.")
    @JsonProperty("limit")
    public abstract int limit();

    @Schema(description = "If the caller has access to every tap that records the segment. Without it, exclusions "
            + "cannot be created or deleted and their matches are not returned.")
    @JsonProperty("all_taps_accessible")
    public abstract boolean allTapsAccessible();

    public static SegmentExclusionsListResponse create(List<SegmentExclusionResponse> exclusions,
                                                       int limit,
                                                       boolean allTapsAccessible) {
        return builder()
                .exclusions(exclusions)
                .limit(limit)
                .allTapsAccessible(allTapsAccessible)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentExclusionsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder exclusions(List<SegmentExclusionResponse> exclusions);
        public abstract Builder limit(int limit);
        public abstract Builder allTapsAccessible(boolean allTapsAccessible);

        public abstract SegmentExclusionsListResponse build();
    }

}
