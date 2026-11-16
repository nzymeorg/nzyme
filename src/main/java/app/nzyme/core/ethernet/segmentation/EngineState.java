package app.nzyme.core.ethernet.segmentation;

import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

public record EngineState(@Nullable DateTime watermark,
                          @Nullable DateTime carryoverBucket,
                          @Nullable DateTime restampRequestedAt,
                          @Nullable DateTime restampCompletedAt,
                          @Nullable DateTime lastRunAt,
                          @Nullable Long lastRunDurationMs,
                          @Nullable Long lastRunSessions,
                          @Nullable Long lastRunFlows,
                          @Nullable Long lastRunFindings,
                          // When an evaluation last left out flows or sources because it hit its limits.
                          @Nullable DateTime limitReachedAt) {

    public boolean restampPending() {
        return restampRequestedAt != null
                && (restampCompletedAt == null || restampCompletedAt.isBefore(restampRequestedAt));
    }

}
