package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class StatusWidgetResponse {

    @JsonProperty("level")
    public abstract String level();

    @Nullable
    @JsonProperty("active_alerts")
    public abstract Long activeAlerts();

    @JsonProperty("taps_online")
    public abstract long tapsOnline();

    @JsonProperty("taps_total")
    public abstract long tapsTotal();

    public static StatusWidgetResponse create(String level, Long activeAlerts, long tapsOnline, long tapsTotal) {
        return builder()
                .level(level)
                .activeAlerts(activeAlerts)
                .tapsOnline(tapsOnline)
                .tapsTotal(tapsTotal)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_StatusWidgetResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder level(String level);

        public abstract Builder activeAlerts(Long activeAlerts);

        public abstract Builder tapsOnline(long tapsOnline);

        public abstract Builder tapsTotal(long tapsTotal);

        public abstract StatusWidgetResponse build();
    }

}
