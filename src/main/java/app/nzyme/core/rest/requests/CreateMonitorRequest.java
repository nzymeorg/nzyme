package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(description = "Body for creating a monitor.")
@AutoValue
public abstract class CreateMonitorRequest {

    @NotEmpty
    public abstract String name();

    @Nullable
    public abstract String description();

    @Nullable
    public abstract List<String> taps();

    @Min(0)
    public abstract Integer triggerCondition();

    @Min(1)
    public abstract Integer interval();

    @Min(1)
    public abstract Integer lookback();

    @NotEmpty
    public abstract String filters();

    @NotNull
    public abstract UUID organizationId();

    @NotNull
    public abstract UUID tenantId();

    @JsonCreator
    public static CreateMonitorRequest create(@Schema(description = "Name of the monitor.", requiredMode = Schema.RequiredMode.REQUIRED)
                                              @JsonProperty("name") String name,
                                              @Schema(description = "Description of the monitor.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("description") String description,
                                              @Schema(description = "Tap UUIDs the monitor runs against. Omit for all taps the calling user can access.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("taps") List<String> taps,
                                              @Schema(description = "Number of matching results that triggers the monitor. Minimum is 0.", requiredMode = Schema.RequiredMode.REQUIRED)
                                              @JsonProperty("trigger_condition") Integer triggerCondition,
                                              @Schema(description = "How often the monitor runs, in minutes. Minimum is 1.", requiredMode = Schema.RequiredMode.REQUIRED)
                                              @JsonProperty("interval") Integer interval,
                                              @Schema(description = "How far back the monitor looks on each run, in minutes. Minimum is 1.", requiredMode = Schema.RequiredMode.REQUIRED)
                                              @JsonProperty("lookback") Integer lookback,
                                              @Schema(description = "JSON encoded filter definition as produced by the web interface filter builder.", requiredMode = Schema.RequiredMode.REQUIRED)
                                              @JsonProperty("filters") String filters,
                                              @Schema(description = "Organization UUID.", requiredMode = Schema.RequiredMode.REQUIRED)
                                              @JsonProperty("organization_id") UUID organizationId,
                                              @Schema(description = "Tenant UUID.", requiredMode = Schema.RequiredMode.REQUIRED)
                                              @JsonProperty("tenant_id") UUID tenantId) {
        return builder()
                .name(name)
                .description(description)
                .taps(taps)
                .triggerCondition(triggerCondition)
                .interval(interval)
                .lookback(lookback)
                .filters(filters)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateMonitorRequest.Builder();
    }


    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(@NotEmpty String name);

        public abstract Builder description(String description);

        public abstract Builder taps(@NotEmpty List<String> taps);

        public abstract Builder triggerCondition(@Min(0) Integer triggerCondition);

        public abstract Builder interval(@Min(1) Integer interval);

        public abstract Builder lookback(@Min(1) Integer lookback);

        public abstract Builder filters(@NotEmpty String filters);

        public abstract Builder organizationId(@NotNull UUID organizationId);

        public abstract Builder tenantId(@NotNull UUID tenantId);

        public abstract CreateMonitorRequest build();
    }
}
