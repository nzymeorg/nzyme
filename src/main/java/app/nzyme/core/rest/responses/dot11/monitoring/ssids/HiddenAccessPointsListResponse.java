package app.nzyme.core.rest.responses.dot11.monitoring.ssids;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@AutoValue
public abstract class HiddenAccessPointsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @Schema(description = "Number of hidden access points a tenant can hold. New ones are not recorded while the "
            + "total is at this number.")
    @JsonProperty("maximum")
    public abstract long maximum();

    @JsonProperty("access_points")
    public abstract List<HiddenAccessPointDetailsResponse> accessPoints();

    public static HiddenAccessPointsListResponse create(long total, long maximum, List<HiddenAccessPointDetailsResponse> accessPoints) {
        return builder()
                .total(total)
                .maximum(maximum)
                .accessPoints(accessPoints)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_HiddenAccessPointsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder maximum(long maximum);

        public abstract Builder accessPoints(List<HiddenAccessPointDetailsResponse> accessPoints);

        public abstract HiddenAccessPointsListResponse build();
    }

}
