package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

import java.util.List;

@AutoValue
public abstract class TapsWidgetResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("online")
    public abstract long online();

    @JsonProperty("offline")
    public abstract long offline();

    @JsonProperty("degraded")
    public abstract long degraded();

    @JsonProperty("alerting")
    public abstract long alerting();

    @JsonProperty("locations")
    public abstract List<TapsWidgetLocationResponse> locations();

    @Nullable
    @JsonProperty("cells")
    public abstract List<TapsWidgetCellResponse> cells();

    @JsonProperty("problems")
    public abstract List<TapsWidgetTapResponse> problems();

    public static TapsWidgetResponse create(long total, long online, long offline, long degraded, long alerting, List<TapsWidgetLocationResponse> locations, List<TapsWidgetCellResponse> cells, List<TapsWidgetTapResponse> problems) {
        return builder()
                .total(total)
                .online(online)
                .offline(offline)
                .degraded(degraded)
                .alerting(alerting)
                .locations(locations)
                .cells(cells)
                .problems(problems)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TapsWidgetResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder online(long online);

        public abstract Builder offline(long offline);

        public abstract Builder degraded(long degraded);

        public abstract Builder alerting(long alerting);

        public abstract Builder locations(List<TapsWidgetLocationResponse> locations);

        public abstract Builder cells(List<TapsWidgetCellResponse> cells);

        public abstract Builder problems(List<TapsWidgetTapResponse> problems);

        public abstract TapsWidgetResponse build();
    }

}
