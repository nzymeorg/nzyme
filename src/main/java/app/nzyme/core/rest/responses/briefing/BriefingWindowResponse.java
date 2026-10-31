package app.nzyme.core.rest.responses.briefing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class BriefingWindowResponse {

    @JsonProperty("mode")
    public abstract String mode();

    @JsonProperty("from")
    public abstract DateTime from();

    @JsonProperty("to")
    public abstract DateTime to();

    @JsonProperty("previous_from")
    public abstract DateTime previousFrom();

    @JsonProperty("previous_to")
    public abstract DateTime previousTo();

    @JsonProperty("duration_ms")
    public abstract long durationMs();

    @JsonProperty("is_first_visit")
    public abstract boolean isFirstVisit();

    @JsonProperty("capped")
    public abstract boolean capped();

    @JsonProperty("last_visit_at")
    @Nullable
    public abstract DateTime lastVisitAt();

    @JsonProperty("last_login_at")
    @Nullable
    public abstract DateTime lastLoginAt();

    @JsonProperty("previous_session_ended_at")
    @Nullable
    public abstract DateTime previousSessionEndedAt();

    public static BriefingWindowResponse create(String mode, DateTime from, DateTime to, DateTime previousFrom, DateTime previousTo, long durationMs, boolean isFirstVisit, boolean capped, DateTime lastVisitAt, DateTime lastLoginAt, DateTime previousSessionEndedAt) {
        return builder()
                .mode(mode)
                .from(from)
                .to(to)
                .previousFrom(previousFrom)
                .previousTo(previousTo)
                .durationMs(durationMs)
                .isFirstVisit(isFirstVisit)
                .capped(capped)
                .lastVisitAt(lastVisitAt)
                .lastLoginAt(lastLoginAt)
                .previousSessionEndedAt(previousSessionEndedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BriefingWindowResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder mode(String mode);

        public abstract Builder from(DateTime from);

        public abstract Builder to(DateTime to);

        public abstract Builder previousFrom(DateTime previousFrom);

        public abstract Builder previousTo(DateTime previousTo);

        public abstract Builder durationMs(long durationMs);

        public abstract Builder isFirstVisit(boolean isFirstVisit);

        public abstract Builder capped(boolean capped);

        public abstract Builder lastVisitAt(DateTime lastVisitAt);

        public abstract Builder lastLoginAt(DateTime lastLoginAt);

        public abstract Builder previousSessionEndedAt(DateTime previousSessionEndedAt);

        public abstract BriefingWindowResponse build();
    }
}
