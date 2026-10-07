package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@Schema(description = "Body for updating the name of MAC address context.")
@AutoValue
public abstract class UpdateMacAddressContextNameRequest {

    @Nullable
    public abstract String name();

    @JsonCreator
    public static UpdateMacAddressContextNameRequest create(@Schema(description = "New short name of the asset. Omit or set to null to remove the name.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                            @JsonProperty("name") String name) {
        return builder()
                .name(name)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_UpdateMacAddressContextNameRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);

        public abstract UpdateMacAddressContextNameRequest build();
    }
}
