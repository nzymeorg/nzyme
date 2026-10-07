package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Body for creating a user.")
@AutoValue
public abstract class CreateUserRequest {

    @NotEmpty
    public abstract String email();

    @NotEmpty
    public abstract String password();

    @NotEmpty
    public abstract String name();

    @NotNull
    public abstract Boolean disableMfa();

    @JsonCreator
    public static CreateUserRequest create(@Schema(description = "Email address of the user. It is also the login name.", requiredMode = Schema.RequiredMode.REQUIRED)
                                           @JsonProperty("email") String email,
                                           @Schema(description = "Password of the user. Between 12 and 128 characters.", requiredMode = Schema.RequiredMode.REQUIRED)
                                           @JsonProperty("password") String password,
                                           @Schema(description = "Full name of the user.", requiredMode = Schema.RequiredMode.REQUIRED)
                                           @JsonProperty("name") String name,
                                           @Schema(description = "Set to true to disable multi-factor authentication for this user.", requiredMode = Schema.RequiredMode.REQUIRED)
                                           @JsonProperty("disable_mfa") Boolean disableMfa) {
        return builder()
                .email(email)
                .password(password)
                .name(name)
                .disableMfa(disableMfa)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateUserRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder email(String email);

        public abstract Builder password(String password);

        public abstract Builder name(String name);

        public abstract Builder disableMfa(Boolean disableMfa);

        public abstract CreateUserRequest build();
    }

}
