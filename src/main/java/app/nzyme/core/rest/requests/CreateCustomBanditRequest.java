package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Body for creating a custom bandit of a tenant.")
@AutoValue
public abstract class CreateCustomBanditRequest {

    @NotNull
    public abstract UUID organizationId();

    @NotNull
    public abstract UUID tenantId();

    @NotEmpty
    public abstract String name();

    @NotEmpty
    public abstract String description();

    @JsonCreator
    public static CreateCustomBanditRequest create(@Schema(description = "Organization UUID.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                   @JsonProperty("organization_id") @NotNull UUID organizationId,
                                                   @Schema(description = "Tenant UUID.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                   @JsonProperty("tenant_id") @NotNull UUID tenantId,
                                                   @Schema(description = "Name of the bandit.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                   @JsonProperty("name")  @NotEmpty String name,
                                                   @Schema(description = "Description of the bandit.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                   @JsonProperty("description") @NotEmpty String description) {
        return builder()
                .organizationId(organizationId)
                .tenantId(tenantId)
                .name(name)
                .description(description)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateCustomBanditRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder organizationId(@NotNull UUID organizationId);

        public abstract Builder tenantId(@NotNull UUID tenantId);

        public abstract Builder name(@NotEmpty String name);

        public abstract Builder description(@NotEmpty String description);

        public abstract CreateCustomBanditRequest build();
    }
}
