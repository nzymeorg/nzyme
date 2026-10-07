package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "Body for updating a location of a tenant.")
@AutoValue
public abstract class UpdateTenantLocationRequest {

    @NotEmpty
    public abstract String name();

    @Nullable
    public abstract String description();

    @Nullable
    @Min(-90) @Max(90)
    public abstract Double latitude();

    @Nullable
    @Min(-180) @Max(180)
    public abstract Double longitude();

    @JsonCreator
    public static UpdateTenantLocationRequest create(@Schema(description = "New name of the location.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                     @JsonProperty("name") String name,
                                                     @Schema(description = "New description of the location.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                     @JsonProperty("description") String description,
                                                     @Schema(description = "Latitude of the location in decimal degrees. Between -90 and 90.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                     @JsonProperty("latitude") Double latitude,
                                                     @Schema(description = "Longitude of the location in decimal degrees. Between -180 and 180.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                     @JsonProperty("longitude") Double longitude) {
        return builder()
                .name(name)
                .description(description)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateTenantLocationRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(@NotEmpty String name);

        public abstract Builder description(String description);

        public abstract Builder latitude(Double latitude);

        public abstract Builder longitude(Double longitude);

        public abstract UpdateTenantLocationRequest build();
    }
}
