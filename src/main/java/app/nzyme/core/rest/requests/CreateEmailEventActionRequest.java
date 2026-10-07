package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@Schema(description = "Body for creating an email event action.")
@AutoValue
public abstract class CreateEmailEventActionRequest {

    @NotEmpty
    public abstract String name();

    @NotEmpty
    public abstract String description();

    @NotEmpty
    public abstract String subjectPrefix();

    @NotNull
    public abstract List<String> receivers();

    @Nullable
    public abstract UUID organizationId();

    @JsonCreator
    public static CreateEmailEventActionRequest create(@Schema(description = "Name of the event action.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                       @JsonProperty("name") String name,
                                                       @Schema(description = "Description of the event action.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                       @JsonProperty("description") String description,
                                                       @Schema(description = "Prefix added to the subject of every email this action sends.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                       @JsonProperty("subject_prefix") String subjectPrefix,
                                                       @Schema(description = "Email addresses that receive the email.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                       @JsonProperty("receivers") List<String> receivers,
                                                       @Schema(description = "Organization UUID. Omit for an action that is available to all organizations.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                       @JsonProperty("organization_id") UUID organizationId) {
        return builder()
                .name(name)
                .description(description)
                .subjectPrefix(subjectPrefix)
                .receivers(receivers)
                .organizationId(organizationId)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateEmailEventActionRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);

        public abstract Builder description(String description);

        public abstract Builder subjectPrefix(String subjectPrefix);

        public abstract Builder receivers(List<String> receivers);

        public abstract Builder organizationId(UUID organizationId);

        public abstract CreateEmailEventActionRequest build();
    }
}
