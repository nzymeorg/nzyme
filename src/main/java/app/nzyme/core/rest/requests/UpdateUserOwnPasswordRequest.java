package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Body for changing the password of the calling user.")
@AutoValue
public abstract class UpdateUserOwnPasswordRequest {

    public abstract String currentPassword();
    public abstract String newPassword();

    @JsonCreator
    public static UpdateUserOwnPasswordRequest create(@Schema(description = "Current password of the calling user.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                      @JsonProperty("current_password") String currentPassword,
                                                      @Schema(description = "New password. Between 12 and 128 characters.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                      @JsonProperty("new_password") String newPassword) {
        return builder()
                .currentPassword(currentPassword)
                .newPassword(newPassword)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateUserOwnPasswordRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder currentPassword(String currentPassword);

        public abstract Builder newPassword(String newPassword);

        public abstract UpdateUserOwnPasswordRequest build();
    }

}
