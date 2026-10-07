package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;

@Schema(description = "Body for updating a floor of a tenant location.")
@AutoValue
public abstract class UpdateFloorOfTenantLocationRequest {

    public abstract long number();

    @Nullable
    public abstract String name();

    @Min(0)
    public abstract float pathLossExponent();

    @JsonCreator
    public static UpdateFloorOfTenantLocationRequest create(@Schema(description = "Number of the floor.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                            @JsonProperty("number") long number,
                                                            @Schema(description = "Name of the floor. Defaults to the floor number if omitted.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                            @JsonProperty("name") String name,
                                                            @Schema(description = "Path loss exponent used for signal based positioning on this floor. The value 3.0 fits " + "typical office or residential buildings.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                            @JsonProperty("path_loss_exponent") float pathLossExponent) {
        return builder()
                .number(number)
                .name(name)
                .pathLossExponent(pathLossExponent)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateFloorOfTenantLocationRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder number(@Min(0) long number);

        public abstract Builder name(String name);

        public abstract Builder pathLossExponent(@Min(0) float pathLossExponent);

        public abstract UpdateFloorOfTenantLocationRequest build();
    }
}
