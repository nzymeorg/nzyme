package app.nzyme.core.rest.responses.ethernet.segmentation;

import app.nzyme.core.ethernet.segmentation.PolicyRule;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class PolicyResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("description")
    public abstract String description();

    @Schema(description = "ACTIVE, AUDIT or PAUSED. A policy in audit is evaluated and records findings, but never alerts.")
    @JsonProperty("status")
    public abstract String status();

    @JsonProperty("alert_on_connected")
    public abstract boolean alertOnConnected();

    @JsonProperty("alert_on_attempted")
    public abstract boolean alertOnAttempted();

    @JsonProperty("scope")
    public abstract PolicyRule scope();

    @JsonProperty("allowed")
    public abstract List<PolicyRule> allowed();

    @Schema(description = "HOLDING, CROSSED, ATTEMPTED, NOT_OBSERVABLE or PAUSED.")
    @JsonProperty("state")
    public abstract String state();

    @JsonProperty("crossed_findings")
    public abstract long crossedFindings();

    @JsonProperty("attempted_findings")
    public abstract long attemptedFindings();

    @Nullable
    @JsonProperty("last_crossed")
    public abstract DateTime lastCrossed();

    @Nullable
    @JsonProperty("last_attempted")
    public abstract DateTime lastAttempted();

    @Schema(description = "A policy keeps at most this many findings.")
    @JsonProperty("findings_limit")
    public abstract int findingsLimit();

    @Schema(description = "The policy has findings_limit findings. Sources that have no finding yet are not "
            + "recorded until findings are dismissed or age out.")
    @JsonProperty("findings_limit_reached")
    public abstract boolean findingsLimitReached();

    @Schema(description = "Sessions that were recorded before the policy was created or last forbade more are "
            + "still being judged against it. Findings are added and the state can change until this ends.")
    @JsonProperty("backfill_pending")
    public abstract boolean backfillPending();

    @JsonProperty("created_at")
    public abstract DateTime createdAt();

    @JsonProperty("updated_at")
    public abstract DateTime updatedAt();

    public static PolicyResponse create(UUID uuid,
                                        String name,
                                        String description,
                                        String status,
                                        boolean alertOnConnected,
                                        boolean alertOnAttempted,
                                        PolicyRule scope,
                                        List<PolicyRule> allowed,
                                        String state,
                                        long crossedFindings,
                                        long attemptedFindings,
                                        DateTime lastCrossed,
                                        DateTime lastAttempted,
                                        int findingsLimit,
                                        boolean findingsLimitReached,
                                        boolean backfillPending,
                                        DateTime createdAt,
                                        DateTime updatedAt) {
        return builder()
                .uuid(uuid)
                .name(name)
                .description(description)
                .status(status)
                .alertOnConnected(alertOnConnected)
                .alertOnAttempted(alertOnAttempted)
                .scope(scope)
                .allowed(allowed)
                .state(state)
                .crossedFindings(crossedFindings)
                .attemptedFindings(attemptedFindings)
                .lastCrossed(lastCrossed)
                .lastAttempted(lastAttempted)
                .findingsLimit(findingsLimit)
                .findingsLimitReached(findingsLimitReached)
                .backfillPending(backfillPending)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PolicyResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder status(String status);
        public abstract Builder alertOnConnected(boolean alertOnConnected);
        public abstract Builder alertOnAttempted(boolean alertOnAttempted);
        public abstract Builder scope(PolicyRule scope);
        public abstract Builder allowed(List<PolicyRule> allowed);
        public abstract Builder state(String state);
        public abstract Builder crossedFindings(long crossedFindings);
        public abstract Builder attemptedFindings(long attemptedFindings);
        public abstract Builder lastCrossed(DateTime lastCrossed);
        public abstract Builder lastAttempted(DateTime lastAttempted);
        public abstract Builder findingsLimit(int findingsLimit);
        public abstract Builder findingsLimitReached(boolean findingsLimitReached);
        public abstract Builder backfillPending(boolean backfillPending);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract PolicyResponse build();
    }

}
