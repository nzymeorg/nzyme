package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class SegmentationStatusResponse {

    @JsonProperty("enabled")
    public abstract boolean enabled();

    @Schema(description = "Sessions the node recorded before this time are evaluated.")
    @Nullable
    @JsonProperty("watermark")
    public abstract DateTime watermark();

    @Nullable
    @JsonProperty("lag_seconds")
    public abstract Long lagSeconds();

    @Nullable
    @JsonProperty("last_run_at")
    public abstract DateTime lastRunAt();

    @Nullable
    @JsonProperty("last_run_duration_ms")
    public abstract Long lastRunDurationMs();

    @Nullable
    @JsonProperty("last_run_sessions")
    public abstract Long lastRunSessions();

    @Nullable
    @JsonProperty("last_run_flows")
    public abstract Long lastRunFlows();

    @Nullable
    @JsonProperty("last_run_findings")
    public abstract Long lastRunFindings();

    @JsonProperty("restamp_pending")
    public abstract boolean restampPending();

    @Schema(description = "Sessions that were recorded before a policy was created or last forbade more are still "
            + "being judged against it.")
    @JsonProperty("backfill_pending")
    public abstract boolean backfillPending();

    @JsonProperty("settle_seconds")
    public abstract int settleSeconds();

    @JsonProperty("segment_count")
    public abstract long segmentCount();

    @JsonProperty("policy_count")
    public abstract long policyCount();

    @Schema(description = "When an evaluation last hit its limits and left out the rest: flows_per_evaluation_limit "
            + "distinct flows, or sources_per_policy_per_evaluation_limit sources without a finding per policy.")
    @Nullable
    @JsonProperty("limit_reached_at")
    public abstract DateTime limitReachedAt();

    @JsonProperty("flows_per_evaluation_limit")
    public abstract int flowsPerEvaluationLimit();

    @JsonProperty("sources_per_policy_per_evaluation_limit")
    public abstract int sourcesPerPolicyPerEvaluationLimit();

    public static SegmentationStatusResponse create(boolean enabled,
                                                    DateTime watermark,
                                                    Long lagSeconds,
                                                    DateTime lastRunAt,
                                                    Long lastRunDurationMs,
                                                    Long lastRunSessions,
                                                    Long lastRunFlows,
                                                    Long lastRunFindings,
                                                    boolean restampPending,
                                                    boolean backfillPending,
                                                    int settleSeconds,
                                                    long segmentCount,
                                                    long policyCount,
                                                    DateTime limitReachedAt,
                                                    int flowsPerEvaluationLimit,
                                                    int sourcesPerPolicyPerEvaluationLimit) {
        return builder()
                .enabled(enabled)
                .watermark(watermark)
                .lagSeconds(lagSeconds)
                .lastRunAt(lastRunAt)
                .lastRunDurationMs(lastRunDurationMs)
                .lastRunSessions(lastRunSessions)
                .lastRunFlows(lastRunFlows)
                .lastRunFindings(lastRunFindings)
                .restampPending(restampPending)
                .backfillPending(backfillPending)
                .settleSeconds(settleSeconds)
                .segmentCount(segmentCount)
                .policyCount(policyCount)
                .limitReachedAt(limitReachedAt)
                .flowsPerEvaluationLimit(flowsPerEvaluationLimit)
                .sourcesPerPolicyPerEvaluationLimit(sourcesPerPolicyPerEvaluationLimit)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentationStatusResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder enabled(boolean enabled);
        public abstract Builder watermark(DateTime watermark);
        public abstract Builder lagSeconds(Long lagSeconds);
        public abstract Builder lastRunAt(DateTime lastRunAt);
        public abstract Builder lastRunDurationMs(Long lastRunDurationMs);
        public abstract Builder lastRunSessions(Long lastRunSessions);
        public abstract Builder lastRunFlows(Long lastRunFlows);
        public abstract Builder lastRunFindings(Long lastRunFindings);
        public abstract Builder restampPending(boolean restampPending);
        public abstract Builder backfillPending(boolean backfillPending);
        public abstract Builder settleSeconds(int settleSeconds);
        public abstract Builder segmentCount(long segmentCount);
        public abstract Builder policyCount(long policyCount);
        public abstract Builder limitReachedAt(DateTime limitReachedAt);
        public abstract Builder flowsPerEvaluationLimit(int flowsPerEvaluationLimit);
        public abstract Builder sourcesPerPolicyPerEvaluationLimit(int sourcesPerPolicyPerEvaluationLimit);

        public abstract SegmentationStatusResponse build();
    }

}
