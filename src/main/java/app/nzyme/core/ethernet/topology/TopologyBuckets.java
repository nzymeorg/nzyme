package app.nzyme.core.ethernet.topology;

import org.joda.time.DateTime;

// Buckets start on multiples of their size, so requests for nearly the same range share their boundaries.
public record TopologyBuckets(long fromEpochSeconds, long sizeSeconds, int count) {

    public static final int MAX_BUCKETS = 72;

    private static final long[] SIZES_SECONDS = {
            60, 120, 300, 600, 900, 1_800, 3_600, 7_200, 10_800, 21_600, 43_200, 86_400, 172_800, 604_800, 2_592_000
    };

    public static TopologyBuckets of(DateTime from, DateTime to) {
        long fromSeconds = Math.floorDiv(from.getMillis(), 1000L);
        long toSeconds = Math.max(fromSeconds + 1, (long) Math.ceil(to.getMillis() / 1000.0));
        long span = toSeconds - fromSeconds;

        long size = SIZES_SECONDS[SIZES_SECONDS.length - 1];
        for (long candidate : SIZES_SECONDS) {
            if (span <= candidate * MAX_BUCKETS) {
                size = candidate;
                break;
            }
        }

        // Ranges of many years.
        while (span > size * MAX_BUCKETS) {
            size *= 2;
        }

        long alignedFrom = Math.floorDiv(fromSeconds, size) * size;
        int count = (int) Math.max(1, Math.ceil((toSeconds - alignedFrom) / (double) size));

        return new TopologyBuckets(alignedFrom, size, count);
    }

    public DateTime from() {
        return new DateTime(fromEpochSeconds * 1000L);
    }

    public long sizeMs() {
        return sizeSeconds * 1000L;
    }

    public DateTime bucketStart(int index) {
        return new DateTime((fromEpochSeconds + index * sizeSeconds) * 1000L);
    }

}
