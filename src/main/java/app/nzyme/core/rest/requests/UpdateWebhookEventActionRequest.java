package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Body for updating a webhook event action.")
@AutoValue
public abstract class UpdateWebhookEventActionRequest {

    @NotEmpty
    public abstract String name();

    @NotEmpty
    public abstract String description();

    @NotEmpty
    public abstract String url();

    @NotNull
    public abstract boolean allowInsecure();

    @Nullable
    public abstract String bearerToken();

    @JsonCreator
    public static UpdateWebhookEventActionRequest create(@Schema(description = "New name of the event action.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                         @JsonProperty("name") String name,
                                                         @Schema(description = "New description of the event action.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                         @JsonProperty("description") String description,
                                                         @Schema(description = "HTTP or HTTPS URL that Nzyme posts the event to.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                         @JsonProperty("url") String url,
                                                         @Schema(description = "Set to true to accept invalid or untrusted TLS certificates.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                         @JsonProperty("allow_insecure") boolean allowInsecure,
                                                         @Schema(description = "Bearer token sent in the Authorization header. Omit for no authentication.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                         @JsonProperty("bearer_token") String bearerToken) {
        return builder()
                .name(name)
                .description(description)
                .url(url)
                .allowInsecure(allowInsecure)
                .bearerToken(bearerToken)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateWebhookEventActionRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(@NotEmpty String name);

        public abstract Builder description(@NotEmpty String description);

        public abstract Builder url(@NotEmpty String url);

        public abstract Builder allowInsecure(@NotNull boolean allowInsecure);

        public abstract Builder bearerToken(String bearerToken);

        public abstract UpdateWebhookEventActionRequest build();
    }
}
