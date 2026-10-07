package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "Body for updating a tenant.")
@AutoValue
public abstract class UpdateTenantRequest {

    @NotEmpty
    public abstract String name();

    @NotEmpty
    public abstract String description();

    @Min(1)
    public abstract int sessionTimeoutMinutes();

    @Min(1)
    public abstract int sessionInactivityTimeoutMinutes();

    @Min(1)
    public abstract int mfaTimeoutMinutes();

    @JsonCreator
    public static UpdateTenantRequest create(@Schema(description = "New name of the tenant.", requiredMode = Schema.RequiredMode.REQUIRED)
                                             @JsonProperty("name") String name,
                                             @Schema(description = "New description of the tenant.", requiredMode = Schema.RequiredMode.REQUIRED)
                                             @JsonProperty("description") String description,
                                             @Schema(description = "Maximum lifetime of a session, in minutes. Minimum is 1.", requiredMode = Schema.RequiredMode.REQUIRED)
                                             @JsonProperty("session_timeout_minutes") int sessionTimeoutMinutes,
                                             @Schema(description = "Minutes of inactivity after which a session is closed. Minimum is 1.", requiredMode = Schema.RequiredMode.REQUIRED)
                                             @JsonProperty("session_inactivity_timeout_minutes") int sessionInactivityTimeoutMinutes,
                                             @Schema(description = "Minutes a user has to complete multi-factor authentication. Minimum is 1.", requiredMode = Schema.RequiredMode.REQUIRED)
                                             @JsonProperty("mfa_timeout_minutes") int mfaTimeoutMinutes) {
        return builder()
                .name(name)
                .description(description)
                .sessionTimeoutMinutes(sessionTimeoutMinutes)
                .sessionInactivityTimeoutMinutes(sessionInactivityTimeoutMinutes)
                .mfaTimeoutMinutes(mfaTimeoutMinutes)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateTenantRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);

        public abstract Builder description(String description);

        public abstract Builder sessionTimeoutMinutes(int sessionTimeoutMinutes);

        public abstract Builder sessionInactivityTimeoutMinutes(int sessionInactivityTimeoutMinutes);

        public abstract Builder mfaTimeoutMinutes(int mfaTimeoutMinutes);

        public abstract UpdateTenantRequest build();
    }

}
