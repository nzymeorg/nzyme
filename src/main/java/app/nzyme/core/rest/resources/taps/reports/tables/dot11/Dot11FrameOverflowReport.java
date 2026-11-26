package app.nzyme.core.rest.resources.taps.reports.tables.dot11;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Dot11FrameOverflowReport {

    public abstract String bssid();
    public abstract String frameType();
    public abstract long frameCount();
    public abstract long oldestFrameAgeMs();
    public abstract long newestFrameAgeMs();

    @JsonCreator
    public static Dot11FrameOverflowReport create(@JsonProperty("bssid") String bssid,
                                                  @JsonProperty("frame_type") String frameType,
                                                  @JsonProperty("frame_count") long frameCount,
                                                  @JsonProperty("oldest_frame_age_ms") long oldestFrameAgeMs,
                                                  @JsonProperty("newest_frame_age_ms") long newestFrameAgeMs) {
        return new AutoValue_Dot11FrameOverflowReport(bssid, frameType, frameCount, oldestFrameAgeMs, newestFrameAgeMs);
    }

}
