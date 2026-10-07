package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Body for creating a monitored WiFi network.")
@AutoValue
public abstract class CreateDot11MonitoredNetworkRequest {

    @NotEmpty
    public abstract String ssid();

    @NotNull
    public abstract UUID organizationId();

    @NotNull
    public abstract UUID tenantId();

    @JsonCreator
    public static CreateDot11MonitoredNetworkRequest create(@NotEmpty @Schema(description = "SSID of the network to monitor.", requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("ssid") String ssid, @NotNull @Schema(description = "Organization UUID.", requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("organization_id") UUID organizationId, @NotNull @Schema(description = "Tenant UUID.", requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("tenant_id") UUID tenantId) {
        return builder()
                .ssid(ssid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateDot11MonitoredNetworkRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder ssid(@NotEmpty String ssid);

        public abstract Builder organizationId(@NotNull UUID organizationId);

        public abstract Builder tenantId(@NotNull UUID tenantId);

        public abstract CreateDot11MonitoredNetworkRequest build();
    }
}
