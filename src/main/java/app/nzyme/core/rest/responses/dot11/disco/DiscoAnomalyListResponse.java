package app.nzyme.core.rest.responses.dot11.disco;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class DiscoAnomalyListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("types")
    public abstract List<DiscoAnomalyTypeCountResponse> types();

    @JsonProperty("anomalies")
    public abstract List<DiscoAnomalyDetailsResponse> anomalies();

    public static DiscoAnomalyListResponse create(long total, List<DiscoAnomalyTypeCountResponse> types, List<DiscoAnomalyDetailsResponse> anomalies) {
        return builder()
                .total(total)
                .types(types)
                .anomalies(anomalies)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DiscoAnomalyListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder types(List<DiscoAnomalyTypeCountResponse> types);

        public abstract Builder anomalies(List<DiscoAnomalyDetailsResponse> anomalies);

        public abstract DiscoAnomalyListResponse build();
    }

}
