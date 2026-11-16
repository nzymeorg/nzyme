package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class AssetTagRequest {

    @Nullable
    public abstract String name();

    @Nullable
    public abstract String description();

    @JsonCreator
    public static AssetTagRequest create(@Schema(description = "Name of up to 24 characters: letters, digits, dashes, underscores and dots.") @JsonProperty("name") String name,
                                         @Schema(description = "Description of up to 64 characters.") @JsonProperty("description") String description) {
        return builder()
                .name(name)
                .description(description)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetTagRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder description(String description);

        public abstract AssetTagRequest build();
    }

}
