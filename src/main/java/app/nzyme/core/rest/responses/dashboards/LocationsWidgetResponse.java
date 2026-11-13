package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class LocationsWidgetResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("locations")
    public abstract List<LocationsWidgetLocationResponse> locations();

    public static LocationsWidgetResponse create(long total, List<LocationsWidgetLocationResponse> locations) {
        return builder()
                .total(total)
                .locations(locations)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_LocationsWidgetResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder locations(List<LocationsWidgetLocationResponse> locations);

        public abstract LocationsWidgetResponse build();
    }

}
