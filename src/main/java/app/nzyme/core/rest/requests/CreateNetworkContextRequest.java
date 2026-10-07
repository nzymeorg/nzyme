package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Body for creating network context of a tenant.")
@AutoValue
public abstract class CreateNetworkContextRequest {

    @NotNull
    public abstract String cidr();

    @Nullable
    @Size(max = 12)
    public abstract String name();

    @Nullable @Size(max = 32)
    public abstract String description();

    @Nullable
    public abstract String notes();

    @NotNull
    @app.nzyme.core.rest.constraints.UUID
    public abstract UUID organizationId();

    @NotNull @app.nzyme.core.rest.constraints.UUID
    public abstract UUID tenantId();

    @JsonCreator
    public static CreateNetworkContextRequest create(@Schema(description = "Network in CIDR notation like \"10.0.0.0/8\".", requiredMode = Schema.RequiredMode.REQUIRED)
                                                     @JsonProperty("cidr") String cidr,
                                                     @Schema(description = "Short name of the network. Up to 12 characters.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                     @JsonProperty("name") String name,
                                                     @Schema(description = "Description of the network. Up to 32 characters.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                     @JsonProperty("description") String description,
                                                     @Schema(description = "Free form notes about the network.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                     @JsonProperty("notes") String notes,
                                                     @Schema(description = "Organization UUID.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                     @JsonProperty("organization_id") UUID organizationId,
                                                     @Schema(description = "Tenant UUID.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                     @JsonProperty("tenant_id") UUID tenantId) {
        return builder()
                .cidr(cidr)
                .name(name)
                .description(description)
                .notes(notes)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateNetworkContextRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder cidr(@NotNull String cidr);

        public abstract Builder name(@Size(max = 12) String name);

        public abstract Builder description(@Size(max = 32) String description);

        public abstract Builder notes(String notes);

        public abstract Builder organizationId(@NotNull UUID organizationId);

        public abstract Builder tenantId(@NotNull UUID tenantId);

        public abstract CreateNetworkContextRequest build();
    }
}
