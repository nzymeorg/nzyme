package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@AutoValue
public abstract class CreateApiKeyRequest {

    @NotBlank
    public abstract String name();

    @Min(1) @Nullable
    public abstract Integer expiryDays();

    @JsonCreator
    public static CreateApiKeyRequest create(@JsonProperty("name") String name,
                                             @JsonProperty("expiry_days") Integer expiryDays) {
        return builder()
                .name(name)
                .expiryDays(expiryDays)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateApiKeyRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(@NotBlank String name);

        public abstract Builder expiryDays(@Min(1) Integer expiryDays);

        public abstract CreateApiKeyRequest build();
    }
}
