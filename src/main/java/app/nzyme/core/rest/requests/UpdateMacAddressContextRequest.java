package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@Schema(description = "Body for updating MAC address context.")
@AutoValue
public abstract class UpdateMacAddressContextRequest {

    @Nullable @Size(max = 12)
    public abstract String name();

    @Nullable
    @Size(max = 32)
    public abstract String description();

    @Nullable
    public abstract String notes();

    @JsonCreator
    public static UpdateMacAddressContextRequest create(@Schema(description = "Short name of the asset. Up to 12 characters.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                        @JsonProperty("name") String name,
                                                        @Schema(description = "Description of the asset. Up to 32 characters.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                        @JsonProperty("description") String description,
                                                        @Schema(description = "Free form notes about the asset.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                        @JsonProperty("notes") String notes) {
        return builder()
                .name(name)
                .description(description)
                .notes(notes)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateMacAddressContextRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(@NotEmpty @Size(max = 12) String name);

        public abstract Builder description(@Size(max = 32) String description);

        public abstract Builder notes(String notes);

        public abstract UpdateMacAddressContextRequest build();
    }

}
