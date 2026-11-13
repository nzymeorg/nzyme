package app.nzyme.core.rest.responses.dashboards;

import app.nzyme.core.rest.responses.briefing.BriefingLocationEnvironmentResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class LocationsWidgetLocationResponse {

    @JsonProperty("name")
    public abstract String name();

    @JsonProperty("taps_total")
    public abstract int tapsTotal();

    @JsonProperty("taps_online")
    public abstract int tapsOnline();

    @JsonProperty("active_alerts")
    public abstract long activeAlerts();

    @Nullable
    @JsonProperty("time_zone")
    public abstract String timeZone();

    @Nullable
    @JsonProperty("environment")
    public abstract BriefingLocationEnvironmentResponse environment();

    public static LocationsWidgetLocationResponse create(String name, int tapsTotal, int tapsOnline, long activeAlerts, String timeZone, BriefingLocationEnvironmentResponse environment) {
        return builder()
                .name(name)
                .tapsTotal(tapsTotal)
                .tapsOnline(tapsOnline)
                .activeAlerts(activeAlerts)
                .timeZone(timeZone)
                .environment(environment)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_LocationsWidgetLocationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);

        public abstract Builder tapsTotal(int tapsTotal);

        public abstract Builder tapsOnline(int tapsOnline);

        public abstract Builder activeAlerts(long activeAlerts);

        public abstract Builder timeZone(String timeZone);

        public abstract Builder environment(BriefingLocationEnvironmentResponse environment);

        public abstract LocationsWidgetLocationResponse build();
    }

}
