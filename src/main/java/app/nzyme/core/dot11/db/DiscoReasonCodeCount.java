package app.nzyme.core.dot11.db;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class DiscoReasonCodeCount {

    public abstract int reasonCode();
    public abstract long frameCount();

    public static DiscoReasonCodeCount create(int reasonCode, long frameCount) {
        return builder()
                .reasonCode(reasonCode)
                .frameCount(frameCount)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DiscoReasonCodeCount.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder reasonCode(int reasonCode);

        public abstract Builder frameCount(long frameCount);

        public abstract DiscoReasonCodeCount build();
    }
}
