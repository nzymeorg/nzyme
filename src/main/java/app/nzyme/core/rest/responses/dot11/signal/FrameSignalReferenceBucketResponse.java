package app.nzyme.core.rest.responses.dot11.signal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

@AutoValue
public abstract class FrameSignalReferenceBucketResponse {

    @JsonProperty("bucket")
    public abstract DateTime bucket();

    @JsonProperty("average")
    public abstract double average();

    @JsonProperty("min")
    public abstract int min();

    @JsonProperty("max")
    public abstract int max();

    @JsonProperty("samples")
    public abstract long samples();

    public static FrameSignalReferenceBucketResponse create(DateTime bucket, double average, int min, int max, long samples) {
        return builder()
                .bucket(bucket)
                .average(average)
                .min(min)
                .max(max)
                .samples(samples)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FrameSignalReferenceBucketResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bucket(DateTime bucket);

        public abstract Builder average(double average);

        public abstract Builder min(int min);

        public abstract Builder max(int max);

        public abstract Builder samples(long samples);

        public abstract FrameSignalReferenceBucketResponse build();
    }

}
