package app.nzyme.core.search;

import app.nzyme.core.util.TimeRange;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

/*
 * The part of the recorded network data a source looks at. The key identifies the window in the result cache:
 * a relative window keeps its key while its bounds move with the clock.
 */
public record SearchWindow(String key, @Nullable DateTime from, DateTime to) {

    public static final int DEFAULT_SCAN_HOURS = 24;

    public static SearchWindow all() {
        return new SearchWindow("all", null, DateTime.now().plusDays(1));
    }

    public static SearchWindow lastHours(int hours) {
        DateTime now = DateTime.now();
        return new SearchWindow("h" + hours, now.minusHours(hours), now.plusMinutes(5));
    }

    public static SearchWindow of(String key, TimeRange range) {
        if (range.isAllTime()) {
            return all();
        }

        return new SearchWindow(key, range.from(), range.to());
    }

    public boolean isAll() {
        return from == null;
    }

    public DateTime boundFrom() {
        return from == null ? new DateTime(0) : from;
    }

}
