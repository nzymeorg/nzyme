package app.nzyme.core.dot11.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class DiscoFrequencyCount {

    @Nullable
    public abstract Integer frequency();
    public abstract long frameCount();

    public static DiscoFrequencyCount create(Integer frequency, long frameCount) {
        return builder()
                .frequency(frequency)
                .frameCount(frameCount)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DiscoFrequencyCount.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder frequency(Integer frequency);

        public abstract Builder frameCount(long frameCount);

        public abstract DiscoFrequencyCount build();
    }
}
