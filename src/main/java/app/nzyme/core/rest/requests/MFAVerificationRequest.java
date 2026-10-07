package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Body for verifying a multi-factor authentication code.")
@AutoValue
public abstract class MFAVerificationRequest {

    public abstract String code();

    @JsonCreator
    public static MFAVerificationRequest create(@Schema(description = "Current one time password from the authenticator app of the user.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                @JsonProperty("code") String code) {
        return builder()
                .code(code)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_MFAVerificationRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder code(String code);

        public abstract MFAVerificationRequest build();
    }
}
