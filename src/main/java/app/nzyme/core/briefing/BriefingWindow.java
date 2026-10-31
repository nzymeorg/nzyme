package app.nzyme.core.briefing;

import app.nzyme.core.util.TimeRange;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;
import org.joda.time.Duration;

public record BriefingWindow(BriefingSinceMode mode,
                             DateTime from,
                             DateTime to,
                             boolean firstVisit,
                             boolean capped,
                             @Nullable DateTime lastVisitAt,
                             @Nullable DateTime lastLoginAt,
                             @Nullable DateTime previousSessionEndedAt) {

    public Duration duration() {
        return new Duration(from, to);
    }

    public DateTime previousFrom() {
        return from.minus(duration());
    }

    public DateTime previousTo() {
        return from;
    }

    public TimeRange current() {
        return TimeRange.create(from, to, false);
    }

    public TimeRange previous() {
        return TimeRange.create(previousFrom(), previousTo(), false);
    }

    public TimeRange combined() {
        return TimeRange.create(previousFrom(), to, false);
    }

}
