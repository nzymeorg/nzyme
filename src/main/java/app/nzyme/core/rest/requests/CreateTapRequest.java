package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;

import java.util.UUID;

@Schema(description = "Body for creating a tap.")
@AutoValue
public abstract class CreateTapRequest {

    @NotEmpty
    public abstract String name();

    @NotEmpty
    public abstract String description();

    @Nullable
    public abstract UUID location();

    @Nullable
    public abstract UUID floor();

    @Nullable
    public abstract Double latitude();

    @Nullable
    public abstract Double longitude();

    @JsonCreator
    public static CreateTapRequest create(@Schema(description = "Name of the tap.", requiredMode = Schema.RequiredMode.REQUIRED)
                                          @JsonProperty("name") String name,
                                          @Schema(description = "Description of the tap.", requiredMode = Schema.RequiredMode.REQUIRED)
                                          @JsonProperty("description") String description,
                                          @Schema(description = "UUID of the tenant location the tap is placed in.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                          @JsonProperty("location") UUID location,
                                          @Schema(description = "UUID of the floor of the tenant location the tap is placed on.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                          @JsonProperty("floor") UUID floor,
                                          @Schema(description = "Latitude of the tap in decimal degrees.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                          @JsonProperty("latitude") Double latitude,
                                          @Schema(description = "Longitude of the tap in decimal degrees.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                          @JsonProperty("longitude") Double longitude) {
        return builder()
                .name(name)
                .description(description)
                .location(location)
                .floor(floor)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateTapRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);

        public abstract Builder description(String description);

        public abstract Builder location(UUID location);

        public abstract Builder floor(UUID floor);

        public abstract Builder latitude(Double latitude);

        public abstract Builder longitude(Double longitude);

        public abstract CreateTapRequest build();
    }

}
