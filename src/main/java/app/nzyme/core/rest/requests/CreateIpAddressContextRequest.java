package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.net.InetAddress;
import java.util.UUID;

@AutoValue
public abstract class CreateIpAddressContextRequest {

    @NotNull
    public abstract InetAddress ipAddress();

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
    public static CreateIpAddressContextRequest create(@JsonProperty("ip_address") InetAddress ipAddress,
                                                       @JsonProperty("name") String name,
                                                       @JsonProperty("description") String description,
                                                       @JsonProperty("notes") String notes,
                                                       @JsonProperty("organization_id") UUID organizationId,
                                                       @JsonProperty("tenant_id") UUID tenantId) {
        return builder()
                .ipAddress(ipAddress)
                .name(name)
                .description(description)
                .notes(notes)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateIpAddressContextRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder ipAddress(@NotNull InetAddress ipAddress);

        public abstract Builder name(@Size(max = 12) String name);

        public abstract Builder description(@Size(max = 32) String description);

        public abstract Builder notes(String notes);

        public abstract Builder organizationId(@NotNull UUID organizationId);

        public abstract Builder tenantId(@NotNull UUID tenantId);

        public abstract CreateIpAddressContextRequest build();
    }
}
