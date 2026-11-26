package app.nzyme.core.rest.responses.dot11.disco;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class DiscoAnomalyReasonCodeResponse {

    @JsonProperty("code")
    public abstract int code();

    @JsonProperty("name")
    public abstract String name();

    public static DiscoAnomalyReasonCodeResponse create(int code, String name) {
        return builder()
                .code(code)
                .name(name)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DiscoAnomalyReasonCodeResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder code(int code);

        public abstract Builder name(String name);

        public abstract DiscoAnomalyReasonCodeResponse build();
    }

}
