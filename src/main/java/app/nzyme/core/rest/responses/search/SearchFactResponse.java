package app.nzyme.core.rest.responses.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class SearchFactResponse {

    @JsonProperty("label")
    public abstract String label();

    @JsonProperty("value")
    public abstract String value();

    public static SearchFactResponse create(String label,
                                            String value) {
        return builder()
                .label(label)
                .value(value)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SearchFactResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder label(String label);

        public abstract Builder value(String value);

        public abstract SearchFactResponse build();
    }

}
