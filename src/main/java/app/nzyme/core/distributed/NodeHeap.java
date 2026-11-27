package app.nzyme.core.distributed;

import jakarta.annotation.Nullable;

public record NodeHeap(Status status,
                       @Nullable Double pauseTimePercent,
                       @Nullable Double heapAfterCollectionPercent,
                       @Nullable Double memoryPressurePercent) {

    public enum Status {
        OK,
        FULL,
        GC_OVERHEAD,
        MEMORY_PRESSURE,
        EXHAUSTED,
        NO_MEASUREMENTS
    }

    public static final int WINDOW_MINUTES = 10;

    // A node that started slowly has not filled its first window yet.
    public static final int ESTABLISHED_AFTER_MINUTES = 2 * WINDOW_MINUTES;

    // G1 aims to spend less than 8% of the time on collections.
    public static final double PAUSE_TIME_PERCENT = 10;

    /*
     * G1 keeps 10% of the heap free as a reserve and lets garbage pile up to there, so a healthy heap can sit just
     * below 90% after every collection. Above that, live data is using up the reserve.
     */
    public static final double FULL_AFTER_COLLECTION_PERCENT = 95;

    /*
     * A heap that squeezes the file cache of its host stays at 2 to 3% on a fast disk. A heap that does not fit
     * into the memory of the host reaches 50% while garbage collection shows nothing.
     */
    public static final double MEMORY_PRESSURE_PERCENT = 10;

    private static final int MINIMUM_SAMPLES = 8;
    private static final int MINIMUM_COLLECTION_SAMPLES = 3;

    public static NodeHeap none() {
        return new NodeHeap(Status.NO_MEASUREMENTS, null, null, null);
    }

    public static NodeHeap judge(boolean established,
                                 @Nullable Double averagePauseTimePercent,
                                 long pauseTimeSamples,
                                 @Nullable Double lowestHeapAfterCollectionPercent,
                                 long collectionSamples,
                                 @Nullable Double averageMemoryPressurePercent,
                                 long memoryPressureSamples) {
        boolean overhead = averagePauseTimePercent != null
                && pauseTimeSamples >= MINIMUM_SAMPLES
                && averagePauseTimePercent >= PAUSE_TIME_PERCENT;

        boolean full = lowestHeapAfterCollectionPercent != null
                && collectionSamples >= MINIMUM_COLLECTION_SAMPLES
                && lowestHeapAfterCollectionPercent >= FULL_AFTER_COLLECTION_PERCENT;

        boolean memoryPressure = averageMemoryPressurePercent != null
                && memoryPressureSamples >= MINIMUM_SAMPLES
                && averageMemoryPressurePercent >= MEMORY_PRESSURE_PERCENT;

        Status status;
        if (overhead && full) {
            status = Status.EXHAUSTED;
        } else if (memoryPressure) {
            status = Status.MEMORY_PRESSURE;
        } else if (full) {
            status = Status.FULL;
        } else if (overhead) {
            status = Status.GC_OVERHEAD;
        } else if (established && pauseTimeSamples < MINIMUM_SAMPLES) {
            // A node in trouble can be too busy to write its measurements. Their absence is not good news.
            status = Status.NO_MEASUREMENTS;
        } else {
            status = Status.OK;
        }

        return new NodeHeap(status, averagePauseTimePercent, lowestHeapAfterCollectionPercent,
                averageMemoryPressurePercent);
    }

}
