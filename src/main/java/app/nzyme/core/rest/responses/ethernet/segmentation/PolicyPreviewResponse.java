package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@AutoValue
public abstract class PolicyPreviewResponse {

    @JsonProperty("rows")
    public abstract List<PolicyPreviewRowResponse> rows();

    @JsonProperty("allowed_sessions")
    public abstract long allowedSessions();

    @JsonProperty("crossed_sessions")
    public abstract long crossedSessions();

    @JsonProperty("attempted_sessions")
    public abstract long attemptedSessions();

    @Schema(description = "Sessions of flows that cannot be judged from stored data, like an external address selector against flows that are stored per network.")
    @JsonProperty("indeterminate_sessions")
    public abstract long indeterminateSessions();

    @Schema(description = "Positions, starting at 0, of the allowed crossings that no flow of the time range made use of. Empty if the result is truncated.")
    @JsonProperty("unused_allowed")
    public abstract List<Integer> unusedAllowed();

    @JsonProperty("truncated")
    public abstract boolean truncated();

    @Schema(description = "At most this many flows are read, the busiest ones. Truncated if there were more.")
    @JsonProperty("flow_limit")
    public abstract int flowLimit();

    @Schema(description = "At most this many rows are returned, the busiest ones. Truncated if there were more.")
    @JsonProperty("row_limit")
    public abstract int rowLimit();

    public static PolicyPreviewResponse create(List<PolicyPreviewRowResponse> rows,
                                               long allowedSessions,
                                               long crossedSessions,
                                               long attemptedSessions,
                                               long indeterminateSessions,
                                               List<Integer> unusedAllowed,
                                               boolean truncated,
                                               int flowLimit,
                                               int rowLimit) {
        return builder()
                .rows(rows)
                .allowedSessions(allowedSessions)
                .crossedSessions(crossedSessions)
                .attemptedSessions(attemptedSessions)
                .indeterminateSessions(indeterminateSessions)
                .unusedAllowed(unusedAllowed)
                .truncated(truncated)
                .flowLimit(flowLimit)
                .rowLimit(rowLimit)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PolicyPreviewResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder rows(List<PolicyPreviewRowResponse> rows);
        public abstract Builder allowedSessions(long allowedSessions);
        public abstract Builder crossedSessions(long crossedSessions);
        public abstract Builder attemptedSessions(long attemptedSessions);
        public abstract Builder indeterminateSessions(long indeterminateSessions);
        public abstract Builder unusedAllowed(List<Integer> unusedAllowed);
        public abstract Builder truncated(boolean truncated);
        public abstract Builder flowLimit(int flowLimit);
        public abstract Builder rowLimit(int rowLimit);

        public abstract PolicyPreviewResponse build();
    }

}
