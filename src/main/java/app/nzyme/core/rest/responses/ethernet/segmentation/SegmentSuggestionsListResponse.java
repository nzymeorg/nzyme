package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class SegmentSuggestionsListResponse {

    @JsonProperty("suggestions")
    public abstract List<SegmentSuggestionResponse> suggestions();

    @Schema(description = "At most this many suggestions are returned per source: network context, DHCP and "
            + "observed traffic.")
    @JsonProperty("limit_per_source")
    public abstract int limitPerSource();

    @Schema(description = "At least one source had more to suggest than limit_per_source.")
    @JsonProperty("limited")
    public abstract boolean limited();

    public static SegmentSuggestionsListResponse create(List<SegmentSuggestionResponse> suggestions,
                                                        int limitPerSource,
                                                        boolean limited) {
        return builder()
                .suggestions(suggestions)
                .limitPerSource(limitPerSource)
                .limited(limited)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentSuggestionsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder suggestions(List<SegmentSuggestionResponse> suggestions);
        public abstract Builder limitPerSource(int limitPerSource);
        public abstract Builder limited(boolean limited);

        public abstract SegmentSuggestionsListResponse build();
    }

}
