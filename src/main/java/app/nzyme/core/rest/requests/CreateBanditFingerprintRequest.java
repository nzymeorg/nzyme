package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "Body for adding a fingerprint to a custom bandit.")
@AutoValue
public abstract class CreateBanditFingerprintRequest {

    @NotEmpty
    public abstract String fingerprint();

    @JsonCreator
    public static CreateBanditFingerprintRequest create(@NotEmpty @Schema(description = "WiFi fingerprint of the bandit.", requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("fingerprint") String fingerprint) {
        return builder()
                .fingerprint(fingerprint)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateBanditFingerprintRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder fingerprint(String fingerprint);

        public abstract CreateBanditFingerprintRequest build();
    }
}
