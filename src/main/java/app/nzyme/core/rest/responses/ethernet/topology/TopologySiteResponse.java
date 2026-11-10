package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class TopologySiteResponse {

    @JsonProperty("id")
    public abstract String id();

    @Schema(description = "Name of the location, or of the tap if the site is a tap with its own coordinates.")
    @JsonProperty("name")
    public abstract String name();

    @JsonProperty("latitude")
    public abstract double latitude();

    @JsonProperty("longitude")
    public abstract double longitude();

    @Nullable
    @JsonProperty("location_uuid")
    public abstract UUID locationUuid();

    @Schema(description = "Selected taps at this site.")
    @JsonProperty("taps")
    public abstract List<TopologyTapResponse> taps();

    public static TopologySiteResponse create(String id, String name, double latitude, double longitude,
                                              @Nullable UUID locationUuid, List<TopologyTapResponse> taps) {
        return builder()
                .id(id)
                .name(name)
                .latitude(latitude)
                .longitude(longitude)
                .locationUuid(locationUuid)
                .taps(taps)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologySiteResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(String id);
        public abstract Builder name(String name);
        public abstract Builder latitude(double latitude);
        public abstract Builder longitude(double longitude);
        public abstract Builder locationUuid(UUID locationUuid);
        public abstract Builder taps(List<TopologyTapResponse> taps);

        public abstract TopologySiteResponse build();
    }

}
