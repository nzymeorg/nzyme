package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Body for updating the node matcher of a TLS wildcard certificate.")
@AutoValue
public abstract class UpdateTLSWildcardNodeMatcherRequest {

    public abstract String nodeMatcher();

    @JsonCreator
    public static UpdateTLSWildcardNodeMatcherRequest create(@Schema(description = "Java regular expression matched against node names to decide which nodes use this " + "certificate.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                             @JsonProperty("node_matcher") String nodeMatcher) {
        return builder()
                .nodeMatcher(nodeMatcher)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateTLSWildcardNodeMatcherRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder nodeMatcher(String nodeMatcher);

        public abstract UpdateTLSWildcardNodeMatcherRequest build();
    }

}
