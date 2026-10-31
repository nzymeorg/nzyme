package app.nzyme.core.rest.responses.briefing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

@AutoValue
public abstract class BriefingHistogramBucketResponse {

    @JsonProperty("bucket")
    public abstract DateTime bucket();

    @JsonProperty("value")
    public abstract double value();

    public static BriefingHistogramBucketResponse create(DateTime bucket, double value) {
        return builder()
                .bucket(bucket)
                .value(value)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BriefingHistogramBucketResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bucket(DateTime bucket);

        public abstract Builder value(double value);

        public abstract BriefingHistogramBucketResponse build();
    }
}
