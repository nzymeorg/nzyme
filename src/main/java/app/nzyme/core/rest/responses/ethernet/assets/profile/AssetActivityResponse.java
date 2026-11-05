package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;
import javax.annotation.Nullable;

@AutoValue
public abstract class AssetActivityResponse {

    @JsonProperty("retention_days")
    public abstract int retentionDays();
    @JsonProperty("days")
    public abstract int days();
    @JsonProperty("heatmap")
    public abstract List<AssetActivityCellResponse> heatmap();
    @JsonProperty("calendar")
    public abstract List<AssetActivityDayResponse> calendar();
    @JsonProperty("total_events")
    public abstract long totalEvents();
    @JsonProperty("total_bytes")
    public abstract long totalBytes();
    @JsonProperty("active_days")
    public abstract long activeDays();
    @JsonProperty("longest_gap_days")
    public abstract long longestGapDays();
    @Nullable
    @JsonProperty("longest_gap_end")
    public abstract String longestGapEnd();

    public static AssetActivityResponse create(int retentionDays, int days, List<AssetActivityCellResponse> heatmap, List<AssetActivityDayResponse> calendar, long totalEvents, long totalBytes, long activeDays, long longestGapDays, String longestGapEnd) {
        return builder()
                .retentionDays(retentionDays)
                .days(days)
                .heatmap(heatmap)
                .calendar(calendar)
                .totalEvents(totalEvents)
                .totalBytes(totalBytes)
                .activeDays(activeDays)
                .longestGapDays(longestGapDays)
                .longestGapEnd(longestGapEnd)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetActivityResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder retentionDays(int retentionDays);

        public abstract Builder days(int days);
        public abstract Builder heatmap(List<AssetActivityCellResponse> heatmap);
        public abstract Builder calendar(List<AssetActivityDayResponse> calendar);
        public abstract Builder totalEvents(long totalEvents);
        public abstract Builder totalBytes(long totalBytes);
        public abstract Builder activeDays(long activeDays);
        public abstract Builder longestGapDays(long longestGapDays);
        public abstract Builder longestGapEnd(String longestGapEnd);

        public abstract AssetActivityResponse build();
    }

}
