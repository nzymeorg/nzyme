package app.nzyme.core.rest.responses.ethernet.l4;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.Map;

@AutoValue
public abstract class L4SessionsTimelineResponse {

    @JsonProperty("buckets")
    public abstract Map<DateTime, L4SessionsTimelineBucketResponse> buckets();

    @JsonProperty("bucket_size_ms")
    public abstract long bucketSizeMs();

    public static L4SessionsTimelineResponse create(Map<DateTime, L4SessionsTimelineBucketResponse> buckets, long bucketSizeMs) {
        return builder()
                .buckets(buckets)
                .bucketSizeMs(bucketSizeMs)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_L4SessionsTimelineResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder buckets(Map<DateTime, L4SessionsTimelineBucketResponse> buckets);

        public abstract Builder bucketSizeMs(long bucketSizeMs);

        public abstract L4SessionsTimelineResponse build();
    }
}
