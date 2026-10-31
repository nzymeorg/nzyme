package app.nzyme.core.rest.responses.briefing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.Map;

@AutoValue
public abstract class BriefingLinkResponse {

    @JsonProperty("target")
    public abstract String target();

    @JsonProperty("params")
    public abstract Map<String, String> params();

    public static BriefingLinkResponse create(String target, Map<String, String> params) {
        return builder()
                .target(target)
                .params(params)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BriefingLinkResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder target(String target);

        public abstract Builder params(Map<String, String> params);

        public abstract BriefingLinkResponse build();
    }
}
