package app.nzyme.core.rest.responses.dot11.channelswitches;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

@AutoValue
public abstract class ChannelSwitchMonitorHistogramValueResponse {

    @JsonProperty("bucket")
    public abstract DateTime bucket();

    @JsonProperty("frame_count")
    public abstract long frameCount();

    @JsonProperty("highlighted_frame_count")
    public abstract long highlightedFrameCount();

    public static ChannelSwitchMonitorHistogramValueResponse create(DateTime bucket, long frameCount, long highlightedFrameCount) {
        return builder()
                .bucket(bucket)
                .frameCount(frameCount)
                .highlightedFrameCount(highlightedFrameCount)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ChannelSwitchMonitorHistogramValueResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bucket(DateTime bucket);
        public abstract Builder frameCount(long frameCount);
        public abstract Builder highlightedFrameCount(long highlightedFrameCount);

        public abstract ChannelSwitchMonitorHistogramValueResponse build();
    }

}
