package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Body for updating configuration values.")
@AutoValue
public abstract class GenericConfigurationUpdateRequest {

    public abstract Map<String, Object> change();

    @JsonCreator
    public static GenericConfigurationUpdateRequest create(@Schema(description = "Map of configuration keys to their new values.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                           @JsonProperty("change") Map<String, Object> change) {
        return builder()
                .change(change)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_GenericConfigurationUpdateRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder change(Map<String, Object> change);

        public abstract GenericConfigurationUpdateRequest build();
    }
}
