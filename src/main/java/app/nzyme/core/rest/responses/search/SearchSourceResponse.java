package app.nzyme.core.rest.responses.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class SearchSourceResponse {

    @JsonProperty("source")
    public abstract String source();

    @JsonProperty("title")
    public abstract String title();

    @JsonProperty("position")
    public abstract int position();

    public static SearchSourceResponse create(String source,
                                              String title,
                                              int position) {
        return builder()
                .source(source)
                .title(title)
                .position(position)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SearchSourceResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder source(String source);

        public abstract Builder title(String title);

        public abstract Builder position(int position);

        public abstract SearchSourceResponse build();
    }

}
