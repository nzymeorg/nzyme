package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Body for updating a user.")
@AutoValue
public abstract class UpdateUserRequest {

    @NotEmpty
    public abstract String email();

    @NotEmpty
    public abstract String name();

    @NotNull
    public abstract Boolean disableMfa();

    @JsonCreator
    public static UpdateUserRequest create(@Schema(description = "Email address of the user. It is also the login name.", requiredMode = Schema.RequiredMode.REQUIRED)
                                           @JsonProperty("email") String email,
                                           @Schema(description = "Full name of the user.", requiredMode = Schema.RequiredMode.REQUIRED)
                                           @JsonProperty("name") String name,
                                           @Schema(description = "Set to true to disable multi-factor authentication for this user.", requiredMode = Schema.RequiredMode.REQUIRED)
                                           @JsonProperty("disable_mfa") Boolean disableMfa) {
        return builder()
                .email(email)
                .name(name)
                .disableMfa(disableMfa)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateUserRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder email(String email);

        public abstract Builder name(String name);

        public abstract Builder disableMfa(Boolean disableMfa);

        public abstract UpdateUserRequest build();
    }

}
