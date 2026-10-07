package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Body for updating a custom UAV type of a tenant.")
@AutoValue
public abstract class UpdateUavCustomTypeRequest {

    @NotBlank
    public abstract String matchType();
    @NotBlank
    public abstract String matchValue();
    @Nullable
    public abstract String defaultClassification();
    @NotBlank
    public abstract String type();
    @NotBlank
    public abstract String name();
    @Nullable
    public abstract String model();

    @JsonCreator
    public static UpdateUavCustomTypeRequest create(@Schema(description = "How the match value is compared to the serial number of a UAV.", allowableValues = {"EXACT", "PREFIX"}, requiredMode = Schema.RequiredMode.REQUIRED)
                                                    @JsonProperty("match_type") String matchType,
                                                    @Schema(description = "UAV serial number or serial number prefix to match.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                    @JsonProperty("match_value") String matchValue,
                                                    @Schema(description = "Classification applied to matching UAVs. Omit to leave them unknown.", allowableValues = {"NEUTRAL", "FRIENDLY", "HOSTILE"}, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                    @JsonProperty("default_classification") String defaultClassification,
                                                    @Schema(description = "Type of the UAV.", allowableValues = {"GENERIC_UNKNOWN", "AGRICULTURE", "CARGO", "HOBBY_TOY", "INDUSTRIAL_INSPECTION", "MAPPING_SURVEYING", "PHOTO_VIDEO", "PUBLIC_SAFETY", "RID_MODULE"}, requiredMode = Schema.RequiredMode.REQUIRED)
                                                    @JsonProperty("type") String type,
                                                    @Schema(description = "Name or description of the matched UAV.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                    @JsonProperty("name") String name,
                                                    @Schema(description = "Model of the UAV, for example \"DJI Mavic 3 Pro\".", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                    @JsonProperty("model") String model) {
        return builder()
                .matchType(matchType)
                .matchValue(matchValue)
                .defaultClassification(defaultClassification)
                .type(type)
                .name(name)
                .model(model)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateUavCustomTypeRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder matchType(@NotBlank String matchType);

        public abstract Builder matchValue(@NotBlank String matchValue);

        public abstract Builder defaultClassification(String defaultClassification);

        public abstract Builder type(@NotBlank String type);

        public abstract Builder name(@NotBlank String name);

        public abstract Builder model(String model);

        public abstract UpdateUavCustomTypeRequest build();
    }
}
