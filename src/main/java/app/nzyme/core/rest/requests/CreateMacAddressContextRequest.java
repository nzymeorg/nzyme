package app.nzyme.core.rest.requests;

import app.nzyme.core.rest.constraints.MacAddress;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Body for creating MAC address context of a tenant.")
@AutoValue
public abstract class CreateMacAddressContextRequest {

    @NotEmpty @MacAddress
    public abstract String macAddress();

    @Nullable
    @Size(max = 12)
    public abstract String name();

    @Nullable @Size(max = 32)
    public abstract String description();

    @Nullable
    public abstract String notes();

    @NotNull @app.nzyme.core.rest.constraints.UUID
    public abstract UUID organizationId();

    @NotNull @app.nzyme.core.rest.constraints.UUID
    public abstract UUID tenantId();

    @JsonCreator
    public static CreateMacAddressContextRequest create(@Schema(description = "MAC address like \"00:11:22:33:44:55\".", requiredMode = Schema.RequiredMode.REQUIRED)
                                                        @JsonProperty("mac_address") String macAddress,
                                                        @Schema(description = "Short name of the asset. Up to 12 characters.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                        @JsonProperty("name") String name,
                                                        @Schema(description = "Description of the asset. Up to 32 characters.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                        @JsonProperty("description") String description,
                                                        @Schema(description = "Free form notes about the asset.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                        @JsonProperty("notes") String notes,
                                                        @Schema(description = "Organization UUID.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                        @JsonProperty("organization_id") UUID organizationId,
                                                        @Schema(description = "Tenant UUID.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                        @JsonProperty("tenant_id") UUID tenantId) {
        return builder()
                .macAddress(macAddress)
                .name(name)
                .description(description)
                .notes(notes)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateMacAddressContextRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder macAddress(@NotEmpty String macAddress);

        public abstract Builder name(@NotEmpty @Size(max = 12) String name);

        public abstract Builder description(@Size(max = 32) String description);

        public abstract Builder notes(String notes);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract CreateMacAddressContextRequest build();
    }
}
