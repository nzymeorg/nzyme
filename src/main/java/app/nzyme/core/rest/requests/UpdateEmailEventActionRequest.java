package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;


import java.util.List;

@Schema(description = "Body for updating an email event action.")
@AutoValue
public abstract class UpdateEmailEventActionRequest {

    @NotEmpty
    public abstract String name();

    @NotEmpty
    public abstract String description();

    @NotEmpty
    public abstract String subjectPrefix();

    @NotNull
    public abstract List<String> receivers();

    @JsonCreator
    public static UpdateEmailEventActionRequest create(@Schema(description = "New name of the event action.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                       @JsonProperty("name") String name,
                                                       @Schema(description = "New description of the event action.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                       @JsonProperty("description") String description,
                                                       @Schema(description = "Prefix added to the subject of every email this action sends.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                       @JsonProperty("subject_prefix") String subjectPrefix,
                                                       @Schema(description = "Email addresses that receive the email.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                       @JsonProperty("receivers") List<String> receivers) {
        return builder()
                .name(name)
                .description(description)
                .subjectPrefix(subjectPrefix)
                .receivers(receivers)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateEmailEventActionRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);

        public abstract Builder description(String description);

        public abstract Builder subjectPrefix(String subjectPrefix);

        public abstract Builder receivers(List<String> receivers);

        public abstract UpdateEmailEventActionRequest build();
    }
}
