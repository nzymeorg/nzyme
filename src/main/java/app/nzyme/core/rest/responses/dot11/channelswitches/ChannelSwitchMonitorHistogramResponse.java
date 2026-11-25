package app.nzyme.core.rest.responses.dot11.channelswitches;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.Map;
import org.joda.time.DateTime;

@AutoValue
public abstract class ChannelSwitchMonitorHistogramResponse {

    @JsonProperty("bucket_size_ms")
    public abstract long bucketSizeMs();

    @JsonProperty("buckets")
    public abstract Map<DateTime, ChannelSwitchMonitorHistogramValueResponse> buckets();

    public static ChannelSwitchMonitorHistogramResponse create(long bucketSizeMs, Map<DateTime, ChannelSwitchMonitorHistogramValueResponse> buckets) {
        return builder()
                .bucketSizeMs(bucketSizeMs)
                .buckets(buckets)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ChannelSwitchMonitorHistogramResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bucketSizeMs(long bucketSizeMs);
        public abstract Builder buckets(Map<DateTime, ChannelSwitchMonitorHistogramValueResponse> buckets);

        public abstract ChannelSwitchMonitorHistogramResponse build();
    }

}
