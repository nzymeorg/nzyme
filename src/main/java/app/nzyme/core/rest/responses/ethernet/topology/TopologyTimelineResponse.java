package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class TopologyTimelineResponse {

    @Schema(description = "All buckets of the time range in order, including empty ones.")
    @JsonProperty("buckets")
    public abstract List<TopologyTimelineBucketResponse> buckets();

    @JsonProperty("totals")
    public abstract TopologyTimelineTotalsResponse totals();

    @JsonProperty("bucket_size_ms")
    public abstract long bucketSizeMs();

    public static TopologyTimelineResponse create(List<TopologyTimelineBucketResponse> buckets,
                                                  TopologyTimelineTotalsResponse totals, long bucketSizeMs) {
        return builder()
                .buckets(buckets)
                .totals(totals)
                .bucketSizeMs(bucketSizeMs)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologyTimelineResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder buckets(List<TopologyTimelineBucketResponse> buckets);
        public abstract Builder totals(TopologyTimelineTotalsResponse totals);
        public abstract Builder bucketSizeMs(long bucketSizeMs);

        public abstract TopologyTimelineResponse build();
    }

}
