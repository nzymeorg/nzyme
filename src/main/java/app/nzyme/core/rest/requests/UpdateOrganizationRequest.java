package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "Body for updating an organization.")
@AutoValue
public abstract class UpdateOrganizationRequest {

    @NotEmpty
    public abstract String name();

    @NotEmpty
    public abstract String description();

    @JsonCreator
    public static UpdateOrganizationRequest create(@Schema(description = "New name of the organization.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                   @JsonProperty("name") String name,
                                                   @Schema(description = "New description of the organization.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                   @JsonProperty("description") String description) {
        return builder()
                .name(name)
                .description(description)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateOrganizationRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);

        public abstract Builder description(String description);

        public abstract UpdateOrganizationRequest build();
    }
}
