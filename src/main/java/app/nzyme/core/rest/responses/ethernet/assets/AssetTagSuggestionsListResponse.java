package app.nzyme.core.rest.responses.ethernet.assets;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class AssetTagSuggestionsListResponse {

    @JsonProperty("suggestions")
    public abstract List<AssetTagSuggestionResponse> suggestions();

    @Schema(description = "Suggestions are worked out from the ports that assets serve, the ones with the most "
            + "sessions first. This is how many served ports are read at most.")
    @JsonProperty("limit")
    public abstract int limit();

    @Schema(description = "True if there were more served ports than the limit. Suggestions for the ones with "
            + "fewer sessions are missing then.")
    @JsonProperty("truncated")
    public abstract boolean truncated();

    public static AssetTagSuggestionsListResponse create(List<AssetTagSuggestionResponse> suggestions,
                                                         int limit,
                                                         boolean truncated) {
        return builder()
                .suggestions(suggestions)
                .limit(limit)
                .truncated(truncated)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetTagSuggestionsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder suggestions(List<AssetTagSuggestionResponse> suggestions);
        public abstract Builder limit(int limit);
        public abstract Builder truncated(boolean truncated);

        public abstract AssetTagSuggestionsListResponse build();
    }

}
