package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Body for setting the feature permissions of a user.")
@AutoValue
public abstract class UpdateUserPermissionsRequest {

    public abstract List<String> permissions();

    @JsonCreator
    public static UpdateUserPermissionsRequest create(@Schema(description = "Identifiers of the feature permissions the user is granted.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                      @JsonProperty("permissions") List<String> permissions) {
        return builder()
                .permissions(permissions)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateUserPermissionsRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder permissions(List<String> permissions);

        public abstract UpdateUserPermissionsRequest build();
    }
}
