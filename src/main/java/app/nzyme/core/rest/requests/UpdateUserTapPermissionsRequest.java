package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Body for setting the tap permissions of a user.")
@AutoValue
public abstract class UpdateUserTapPermissionsRequest {

    public abstract boolean allowAccessAllTenantTaps();
    public abstract List<String> taps();

    @JsonCreator
    public static UpdateUserTapPermissionsRequest create(@Schema(description = "Set to true to give the user access to all taps of the tenant.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                         @JsonProperty("allow_access_all_tenant_taps") boolean allowAccessAllTenantTaps,
                                                         @Schema(description = "UUIDs of the taps the user may access. Ignored when access to all tenant taps is " + "allowed.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                         @JsonProperty("taps") List<String> taps) {
        return builder()
                .allowAccessAllTenantTaps(allowAccessAllTenantTaps)
                .taps(taps)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateUserTapPermissionsRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder allowAccessAllTenantTaps(boolean allowAccessAllTenantTaps);

        public abstract Builder taps(List<String> taps);

        public abstract UpdateUserTapPermissionsRequest build();
    }
}
