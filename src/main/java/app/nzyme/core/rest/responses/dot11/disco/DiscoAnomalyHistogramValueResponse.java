package app.nzyme.core.rest.responses.dot11.disco;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.Map;
import org.joda.time.DateTime;

@AutoValue
public abstract class DiscoAnomalyHistogramValueResponse {

    @JsonProperty("bucket")
    public abstract DateTime bucket();

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("types")
    public abstract Map<String, Long> types();

    public static DiscoAnomalyHistogramValueResponse create(DateTime bucket, long total, Map<String, Long> types) {
        return builder()
                .bucket(bucket)
                .total(total)
                .types(types)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DiscoAnomalyHistogramValueResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bucket(DateTime bucket);

        public abstract Builder total(long total);

        public abstract Builder types(Map<String, Long> types);

        public abstract DiscoAnomalyHistogramValueResponse build();
    }

}
