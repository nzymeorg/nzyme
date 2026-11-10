package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import org.joda.time.DateTime;

@AutoValue
public abstract class TopologySeriesConfigurationResponse {

    @Schema(description = "Start of bucket 0.")
    @JsonProperty("from")
    public abstract DateTime from();

    @JsonProperty("bucket_size_ms")
    public abstract long bucketSizeMs();

    @Schema(description = "Number of buckets that cover the time range.")
    @JsonProperty("count")
    public abstract int count();

    public static TopologySeriesConfigurationResponse create(DateTime from, long bucketSizeMs, int count) {
        return builder()
                .from(from)
                .bucketSizeMs(bucketSizeMs)
                .count(count)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologySeriesConfigurationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder from(DateTime from);
        public abstract Builder bucketSizeMs(long bucketSizeMs);
        public abstract Builder count(int count);

        public abstract TopologySeriesConfigurationResponse build();
    }

}
