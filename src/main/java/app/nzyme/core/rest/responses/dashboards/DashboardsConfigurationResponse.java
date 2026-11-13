package app.nzyme.core.rest.responses.dashboards;

import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class DashboardsConfigurationResponse {

    @JsonProperty("dashboards_enabled")
    public abstract ConfigurationEntryResponse dashboardsEnabled();

    public static DashboardsConfigurationResponse create(ConfigurationEntryResponse dashboardsEnabled) {
        return builder()
                .dashboardsEnabled(dashboardsEnabled)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DashboardsConfigurationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder dashboardsEnabled(ConfigurationEntryResponse dashboardsEnabled);

        public abstract DashboardsConfigurationResponse build();
    }

}
