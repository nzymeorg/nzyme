package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@AutoValue
public abstract class BulkActionSkippedResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("name")
    public abstract String name();

    @Schema(description = "Why the action was not applied to the entry, as a sentence for the user.")
    @JsonProperty("reason")
    public abstract String reason();

    public static BulkActionSkippedResponse create(UUID uuid, String name, String reason) {
        return builder()
                .uuid(uuid)
                .name(name)
                .reason(reason)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BulkActionSkippedResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder name(String name);
        public abstract Builder reason(String reason);

        public abstract BulkActionSkippedResponse build();
    }

}
