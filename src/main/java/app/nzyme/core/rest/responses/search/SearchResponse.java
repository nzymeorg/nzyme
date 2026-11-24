package app.nzyme.core.rest.responses.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@AutoValue
public abstract class SearchResponse {

    @JsonProperty("query")
    public abstract String query();

    @JsonProperty("interpretations")
    public abstract List<SearchInterpretationResponse> interpretations();

    @Schema(description = "Sources that were searched with this request and found something or failed.")
    @JsonProperty("groups")
    public abstract List<SearchGroupResponse> groups();

    @Schema(description = "Sources of recorded network data that apply to the query and were not searched with this "
            + "request. Request each one on its own.")
    @JsonProperty("pending")
    public abstract List<SearchSourceResponse> pending();

    public static SearchResponse create(String query,
                                        List<SearchInterpretationResponse> interpretations,
                                        List<SearchGroupResponse> groups,
                                        List<SearchSourceResponse> pending) {
        return builder()
                .query(query)
                .interpretations(interpretations)
                .groups(groups)
                .pending(pending)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SearchResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder query(String query);

        public abstract Builder interpretations(List<SearchInterpretationResponse> interpretations);

        public abstract Builder groups(List<SearchGroupResponse> groups);

        public abstract Builder pending(List<SearchSourceResponse> pending);

        public abstract SearchResponse build();
    }

}
