package app.nzyme.core.ethernet.nat.db;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

@AutoValue
public abstract class NATTraversalDiscoveryHistogramBucket {

    public abstract long completeCount();
    public abstract long incompleteCount();
    public abstract long errorCount();
    public abstract DateTime bucket();

    public static NATTraversalDiscoveryHistogramBucket create(long completeCount, long incompleteCount, long errorCount, DateTime bucket) {
        return builder()
                .completeCount(completeCount)
                .incompleteCount(incompleteCount)
                .errorCount(errorCount)
                .bucket(bucket)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NATTraversalDiscoveryHistogramBucket.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder completeCount(long completeCount);

        public abstract Builder incompleteCount(long incompleteCount);

        public abstract Builder errorCount(long errorCount);

        public abstract Builder bucket(DateTime bucket);

        public abstract NATTraversalDiscoveryHistogramBucket build();
    }
}
