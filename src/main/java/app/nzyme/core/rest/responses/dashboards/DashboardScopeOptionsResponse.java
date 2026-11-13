package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class DashboardScopeOptionsResponse {

    @JsonProperty("all_taps")
    public abstract boolean allTaps();

    @JsonProperty("locations")
    public abstract List<DashboardScopeItemResponse> locations();

    @JsonProperty("taps")
    public abstract List<DashboardScopeItemResponse> taps();

    @JsonProperty("metrics")
    public abstract List<DashboardMetricOptionResponse> metrics();

    @JsonProperty("can_show_alerts")
    public abstract boolean canShowAlerts();

    @JsonProperty("subsystems")
    public abstract List<String> subsystems();

    public static DashboardScopeOptionsResponse create(boolean allTaps, List<DashboardScopeItemResponse> locations, List<DashboardScopeItemResponse> taps, List<DashboardMetricOptionResponse> metrics, boolean canShowAlerts, List<String> subsystems) {
        return builder()
                .allTaps(allTaps)
                .locations(locations)
                .taps(taps)
                .metrics(metrics)
                .canShowAlerts(canShowAlerts)
                .subsystems(subsystems)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DashboardScopeOptionsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder allTaps(boolean allTaps);

        public abstract Builder locations(List<DashboardScopeItemResponse> locations);

        public abstract Builder taps(List<DashboardScopeItemResponse> taps);

        public abstract Builder metrics(List<DashboardMetricOptionResponse> metrics);

        public abstract Builder canShowAlerts(boolean canShowAlerts);

        public abstract Builder subsystems(List<String> subsystems);

        public abstract DashboardScopeOptionsResponse build();
    }

}
