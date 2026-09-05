package app.nzyme.core.rest.responses.shared;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.Map;

@AutoValue
public abstract class NumericHistogramResponse {

    @JsonProperty("buckets")
    public abstract Map<DateTime, Integer> buckets();

    @JsonProperty("bucket_size_ms")
    public abstract long bucketSizeMs();

    public static NumericHistogramResponse create(Map<DateTime, Integer> buckets, long bucketSizeMs) {
        return builder()
                .buckets(buckets)
                .bucketSizeMs(bucketSizeMs)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NumericHistogramResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder buckets(Map<DateTime, Integer> buckets);

        public abstract Builder bucketSizeMs(long bucketSizeMs);

        public abstract NumericHistogramResponse build();
    }
}
