package app.nzyme.core.rest.responses.dot11;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class SSIDSimilarityResponse {

    @JsonProperty("ssid")
    public abstract String ssid();

    @JsonProperty("similarity")
    public abstract double similarity();

    @JsonProperty("is_monitored")
    public abstract boolean isMonitored();

    @JsonProperty("is_allowed")
    public abstract boolean isAllowed();

    @JsonProperty("alerted")
    public abstract boolean alerted();

    public static SSIDSimilarityResponse create(String ssid, double similarity, boolean isMonitored, boolean isAllowed, boolean alerted) {
        return builder()
                .ssid(ssid)
                .similarity(similarity)
                .isMonitored(isMonitored)
                .isAllowed(isAllowed)
                .alerted(alerted)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SSIDSimilarityResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder ssid(String ssid);

        public abstract Builder similarity(double similarity);

        public abstract Builder isMonitored(boolean isMonitored);

        public abstract Builder isAllowed(boolean isAllowed);

        public abstract Builder alerted(boolean alerted);

        public abstract SSIDSimilarityResponse build();
    }
}
