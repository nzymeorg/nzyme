package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class TapsWidgetTapResponse {

    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("location_name")
    public abstract String locationName();

    @JsonProperty("state")
    public abstract String state();

    @Nullable
    @JsonProperty("reason")
    public abstract String reason();

    @Nullable
    @JsonProperty("last_report")
    public abstract DateTime lastReport();

    @Nullable
    @JsonProperty("clock_drift_ms")
    public abstract Long clockDriftMs();

    @JsonProperty("dropped")
    public abstract long dropped();

    @JsonProperty("captures_running")
    public abstract int capturesRunning();

    @JsonProperty("captures_total")
    public abstract int capturesTotal();

    @JsonProperty("active_alerts")
    public abstract long activeAlerts();

    public static TapsWidgetTapResponse create(String name, String locationName, String state, String reason, DateTime lastReport, Long clockDriftMs, long dropped, int capturesRunning, int capturesTotal, long activeAlerts) {
        return builder()
                .name(name)
                .locationName(locationName)
                .state(state)
                .reason(reason)
                .lastReport(lastReport)
                .clockDriftMs(clockDriftMs)
                .dropped(dropped)
                .capturesRunning(capturesRunning)
                .capturesTotal(capturesTotal)
                .activeAlerts(activeAlerts)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TapsWidgetTapResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);

        public abstract Builder locationName(String locationName);

        public abstract Builder state(String state);

        public abstract Builder reason(String reason);

        public abstract Builder lastReport(DateTime lastReport);

        public abstract Builder clockDriftMs(Long clockDriftMs);

        public abstract Builder dropped(long dropped);

        public abstract Builder capturesRunning(int capturesRunning);

        public abstract Builder capturesTotal(int capturesTotal);

        public abstract Builder activeAlerts(long activeAlerts);

        public abstract TapsWidgetTapResponse build();
    }

}
