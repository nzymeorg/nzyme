package app.nzyme.core.ethernet.l4.db;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

@AutoValue
public abstract class L4SessionsTimelineBucket {

    public abstract DateTime bucket();
    public abstract long sessions();
    public abstract long bytes();

    public static L4SessionsTimelineBucket create(DateTime bucket, long sessions, long bytes) {
        return builder()
                .bucket(bucket)
                .sessions(sessions)
                .bytes(bytes)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_L4SessionsTimelineBucket.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bucket(DateTime bucket);

        public abstract Builder sessions(long sessions);

        public abstract Builder bytes(long bytes);

        public abstract L4SessionsTimelineBucket build();
    }
}
