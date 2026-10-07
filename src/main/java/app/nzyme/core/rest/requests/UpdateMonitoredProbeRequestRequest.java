package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Body for updating a monitored probe request SSID of a tenant.")
@AutoValue
public abstract class UpdateMonitoredProbeRequestRequest {

    @NotNull
    public abstract UUID organizationId();

    @NotNull
    public abstract UUID tenantId();

    @NotBlank
    public abstract String ssid();

    @Nullable
    public abstract String notes();

    @JsonCreator
    public static UpdateMonitoredProbeRequestRequest create(@Schema(description = "Organization UUID.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                            @JsonProperty("organization_id") UUID organizationId,
                                                            @Schema(description = "Tenant UUID.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                            @JsonProperty("tenant_id") UUID tenantId,
                                                            @Schema(description = "SSID to watch for in WiFi probe requests.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                            @JsonProperty("ssid") String ssid,
                                                            @Schema(description = "Free form notes about this monitored probe request.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                            @JsonProperty("notes") String notes) {
        return builder()
                .organizationId(organizationId)
                .tenantId(tenantId)
                .ssid(ssid)
                .notes(notes)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateMonitoredProbeRequestRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder organizationId(@NotNull UUID organizationId);

        public abstract Builder tenantId(@NotNull UUID tenantId);

        public abstract Builder ssid(@NotBlank String ssid);

        public abstract Builder notes(String notes);

        public abstract UpdateMonitoredProbeRequestRequest build();
    }
}
