package app.nzyme.core.rest.responses.dot11.disco;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class DiscoAnomalyTypeCountResponse {

    @JsonProperty("type")
    public abstract String type();

    @JsonProperty("title")
    public abstract String title();

    @JsonProperty("anomalies")
    public abstract long anomalies();

    public static DiscoAnomalyTypeCountResponse create(String type, String title, long anomalies) {
        return builder()
                .type(type)
                .title(title)
                .anomalies(anomalies)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DiscoAnomalyTypeCountResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder type(String type);

        public abstract Builder title(String title);

        public abstract Builder anomalies(long anomalies);

        public abstract DiscoAnomalyTypeCountResponse build();
    }

}
