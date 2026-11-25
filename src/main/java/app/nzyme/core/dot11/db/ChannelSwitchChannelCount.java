package app.nzyme.core.dot11.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class ChannelSwitchChannelCount {

    public abstract int newChannel();
    @Nullable
    public abstract Integer newFrequency();
    public abstract long frameCount();

    public static ChannelSwitchChannelCount create(int newChannel, Integer newFrequency, long frameCount) {
        return builder()
                .newChannel(newChannel)
                .newFrequency(newFrequency)
                .frameCount(frameCount)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ChannelSwitchChannelCount.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder newChannel(int newChannel);
        public abstract Builder newFrequency(Integer newFrequency);
        public abstract Builder frameCount(long frameCount);

        public abstract ChannelSwitchChannelCount build();
    }

}
