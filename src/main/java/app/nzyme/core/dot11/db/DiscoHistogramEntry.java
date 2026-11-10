package app.nzyme.core.dot11.db;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

@AutoValue
public abstract class DiscoHistogramEntry {

    public abstract DateTime bucket();
    public abstract long frameCount();
    public abstract long deauthenticationCount();
    public abstract long disassociationCount();
    public abstract long broadcastCount();

    public static DiscoHistogramEntry create(DateTime bucket, long frameCount, long deauthenticationCount, long disassociationCount, long broadcastCount) {
        return builder()
                .bucket(bucket)
                .frameCount(frameCount)
                .deauthenticationCount(deauthenticationCount)
                .disassociationCount(disassociationCount)
                .broadcastCount(broadcastCount)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DiscoHistogramEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bucket(DateTime bucket);

        public abstract Builder frameCount(long frameCount);

        public abstract Builder deauthenticationCount(long deauthenticationCount);

        public abstract Builder disassociationCount(long disassociationCount);

        public abstract Builder broadcastCount(long broadcastCount);

        public abstract DiscoHistogramEntry build();
    }
}
