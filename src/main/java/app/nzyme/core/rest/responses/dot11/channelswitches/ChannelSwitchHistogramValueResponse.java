package app.nzyme.core.rest.responses.dot11.channelswitches;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

@AutoValue
public abstract class ChannelSwitchHistogramValueResponse {

    @JsonProperty("bucket")
    public abstract DateTime bucket();

    @JsonProperty("frame_count")
    public abstract long frameCount();

    @JsonProperty("anomalous_frame_count")
    public abstract long anomalousFrameCount();

    public static ChannelSwitchHistogramValueResponse create(DateTime bucket, long frameCount, long anomalousFrameCount) {
        return builder()
                .bucket(bucket)
                .frameCount(frameCount)
                .anomalousFrameCount(anomalousFrameCount)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ChannelSwitchHistogramValueResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bucket(DateTime bucket);
        public abstract Builder frameCount(long frameCount);
        public abstract Builder anomalousFrameCount(long anomalousFrameCount);

        public abstract ChannelSwitchHistogramValueResponse build();
    }

}
