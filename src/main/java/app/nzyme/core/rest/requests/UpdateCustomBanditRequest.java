package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "Body for updating a custom bandit of a tenant.")
@AutoValue
public abstract class UpdateCustomBanditRequest {

    @NotEmpty
    public abstract String name();

    @NotEmpty
    public abstract String description();

    @JsonCreator
    public static UpdateCustomBanditRequest create(@Schema(description = "New name of the bandit.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                   @JsonProperty("name") @NotEmpty String name,
                                                   @Schema(description = "New description of the bandit.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                   @JsonProperty("description") @NotEmpty String description) {
        return builder()
                .name(name)
                .description(description)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateCustomBanditRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(@NotEmpty String name);

        public abstract Builder description(@NotEmpty String description);

        public abstract UpdateCustomBanditRequest build();
    }
}
