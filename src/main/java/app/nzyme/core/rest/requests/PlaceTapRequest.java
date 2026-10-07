package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "Body for placing a tap on the floor plan of a tenant location.")
@AutoValue
public abstract class PlaceTapRequest {

    @JsonProperty("x")
    @Min(0)
    public abstract int x();

    @JsonProperty("y")
    @Min(0)
    public abstract int y();

    @JsonCreator
    public static PlaceTapRequest create(@Schema(description = "Horizontal position of the tap on the floor plan, in pixels.", requiredMode = Schema.RequiredMode.REQUIRED)
                                         @JsonProperty("x") int x,
                                         @Schema(description = "Vertical position of the tap on the floor plan, in pixels.", requiredMode = Schema.RequiredMode.REQUIRED)
                                         @JsonProperty("y") int y) {
        return builder()
                .x(x)
                .y(y)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PlaceTapRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder x(int x);

        public abstract Builder y(int y);

        public abstract PlaceTapRequest build();
    }
}
