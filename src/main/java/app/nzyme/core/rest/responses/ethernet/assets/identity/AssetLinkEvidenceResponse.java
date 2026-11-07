package app.nzyme.core.rest.responses.ethernet.assets.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class AssetLinkEvidenceResponse {

    @JsonProperty("signal")
    public abstract String signal();

    @JsonProperty("weight")
    public abstract int weight();

    @Nullable
    @JsonProperty("value")
    public abstract String value();

    public static AssetLinkEvidenceResponse create(String signal, int weight, String value) {
        return builder()
                .signal(signal)
                .weight(weight)
                .value(value)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetLinkEvidenceResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder signal(String signal);
        public abstract Builder weight(int weight);
        public abstract Builder value(String value);
        public abstract AssetLinkEvidenceResponse build();
    }
}
