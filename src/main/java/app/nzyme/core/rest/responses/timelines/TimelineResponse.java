package app.nzyme.core.rest.responses.timelines;

import app.nzyme.core.rest.responses.taps.TapHighLevelInformationDetailsResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class TimelineResponse {

    @JsonProperty("retention_days")
    public abstract int retentionDays();

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("events")
    public abstract List<TimelineEventDetailsResponse> events();

    @JsonProperty("ssids")
    public abstract List<String> ssids();

    @Schema(description = "True if the BSSID advertised without a network name in the time range.")
    @JsonProperty("has_hidden_ssid")
    public abstract boolean hasHiddenSsid();

    @JsonProperty("fingerprints")
    public abstract List<String> fingerprints();

    @JsonProperty("taps")
    public abstract List<TapHighLevelInformationDetailsResponse> taps();

    @Schema(description = "Set if the time range starts before this moment. Before it, only taps that had the "
            + "strongest signal of the BSSID at some point are known.")
    @JsonProperty("taps_complete_since")
    @Nullable
    public abstract DateTime tapsCompleteSince();

    @Schema(description = "Set if taps_complete_since is the start of the retention time of recorded 802.11 data, "
            + "in days. Not set if it is the moment timelines started to record the taps of a BSSID.")
    @JsonProperty("taps_retention_days")
    @Nullable
    public abstract Integer tapsRetentionDays();

    @Schema(description = "The lists of \"ssids\", \"fingerprints\" and \"taps\" for which timeline events hold more "
            + "values than list_limit. Only list_limit values of the events are part of such a list.")
    @JsonProperty("limited_lists")
    public abstract List<String> limitedLists();

    @Schema(description = "The largest number of values that is read from timeline events for each list.")
    @JsonProperty("list_limit")
    public abstract int listLimit();

    @JsonProperty("activity_histogram")
    public abstract TimelineActivityHistogramResponse histogram();

    public static TimelineResponse create(int retentionDays, long total, List<TimelineEventDetailsResponse> events, List<String> ssids, boolean hasHiddenSsid, List<String> fingerprints, List<TapHighLevelInformationDetailsResponse> taps, @Nullable DateTime tapsCompleteSince, @Nullable Integer tapsRetentionDays, List<String> limitedLists, int listLimit, TimelineActivityHistogramResponse histogram) {
        return builder()
                .retentionDays(retentionDays)
                .total(total)
                .events(events)
                .ssids(ssids)
                .hasHiddenSsid(hasHiddenSsid)
                .fingerprints(fingerprints)
                .taps(taps)
                .tapsCompleteSince(tapsCompleteSince)
                .tapsRetentionDays(tapsRetentionDays)
                .limitedLists(limitedLists)
                .listLimit(listLimit)
                .histogram(histogram)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TimelineResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder retentionDays(int retentionDays);

        public abstract Builder total(long total);

        public abstract Builder events(List<TimelineEventDetailsResponse> events);

        public abstract Builder ssids(List<String> ssids);

        public abstract Builder hasHiddenSsid(boolean hasHiddenSsid);

        public abstract Builder limitedLists(List<String> limitedLists);

        public abstract Builder listLimit(int listLimit);

        public abstract Builder fingerprints(List<String> fingerprints);

        public abstract Builder taps(List<TapHighLevelInformationDetailsResponse> taps);

        public abstract Builder tapsCompleteSince(@Nullable DateTime tapsCompleteSince);

        public abstract Builder tapsRetentionDays(@Nullable Integer tapsRetentionDays);

        public abstract Builder histogram(TimelineActivityHistogramResponse histogram);

        public abstract TimelineResponse build();
    }
}
