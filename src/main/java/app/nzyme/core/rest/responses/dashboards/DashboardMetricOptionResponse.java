package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class DashboardMetricOptionResponse {

    @JsonProperty("metric")
    public abstract String metric();

    @JsonProperty("title")
    public abstract String title();

    @JsonProperty("available")
    public abstract boolean available();

    public static DashboardMetricOptionResponse create(String metric, String title, boolean available) {
        return builder()
                .metric(metric)
                .title(title)
                .available(available)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DashboardMetricOptionResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder metric(String metric);

        public abstract Builder title(String title);

        public abstract Builder available(boolean available);

        public abstract DashboardMetricOptionResponse build();
    }

}
