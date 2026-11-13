package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class DashboardsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("dashboards")
    public abstract List<DashboardResponse> dashboards();

    public static DashboardsListResponse create(long total, List<DashboardResponse> dashboards) {
        return builder()
                .total(total)
                .dashboards(dashboards)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DashboardsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder dashboards(List<DashboardResponse> dashboards);

        public abstract DashboardsListResponse build();
    }

}
