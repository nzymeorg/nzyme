package app.nzyme.core.dot11.db;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

@AutoValue
public abstract class ChannelSwitchHistogramEntry {

    public abstract DateTime bucket();
    public abstract long frameCount();
    public abstract long anomalousFrameCount();

    public static ChannelSwitchHistogramEntry create(DateTime bucket, long frameCount, long anomalousFrameCount) {
        return builder()
                .bucket(bucket)
                .frameCount(frameCount)
                .anomalousFrameCount(anomalousFrameCount)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ChannelSwitchHistogramEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bucket(DateTime bucket);
        public abstract Builder frameCount(long frameCount);
        public abstract Builder anomalousFrameCount(long anomalousFrameCount);

        public abstract ChannelSwitchHistogramEntry build();
    }

}
