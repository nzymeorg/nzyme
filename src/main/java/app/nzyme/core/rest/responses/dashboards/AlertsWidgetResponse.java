package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class AlertsWidgetResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("alerts")
    public abstract List<AlertsWidgetAlertResponse> alerts();

    public static AlertsWidgetResponse create(long total, List<AlertsWidgetAlertResponse> alerts) {
        return builder()
                .total(total)
                .alerts(alerts)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AlertsWidgetResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder alerts(List<AlertsWidgetAlertResponse> alerts);

        public abstract AlertsWidgetResponse build();
    }

}
