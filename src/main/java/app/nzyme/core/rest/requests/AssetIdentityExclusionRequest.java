package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.util.List;

@Schema(description = "Body for creating, updating or previewing an asset identity exclusion. The MAC address "
        + "or vendor prefix is required. The types are optional and limit the exclusion to those detections.")
@AutoValue
public abstract class AssetIdentityExclusionRequest {

    @Nullable
    public abstract String name();

    @Nullable
    public abstract String description();

    @Nullable
    public abstract Boolean enabled();

    @Nullable
    public abstract String mac();

    @Nullable
    public abstract List<String> types();

    @JsonCreator
    public static AssetIdentityExclusionRequest create(@Schema(description = "Name of the exclusion. Required when creating or updating, ignored for previews.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                       @JsonProperty("name") String name,
                                                       @Schema(description = "Optional description of why the exclusion exists.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                       @JsonProperty("description") String description,
                                                       @Schema(description = "Whether the exclusion is active. Defaults to true.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                       @JsonProperty("enabled") Boolean enabled,
                                                       @Schema(description = "Full MAC address like AA:BB:CC:DD:EE:FF, or a vendor prefix like AA:BB:CC that covers every device of that vendor.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                       @JsonProperty("mac") String mac,
                                                       @Schema(description = "Detection types the exclusion is limited to: DUPLICATE_MAC, IDENTITY_CHANGE or MAC_CHANGE. Empty or omitted covers all types.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                       @JsonProperty("types") List<String> types) {
        return builder()
                .name(name)
                .description(description)
                .enabled(enabled)
                .mac(mac)
                .types(types)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetIdentityExclusionRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder enabled(Boolean enabled);
        public abstract Builder mac(String mac);
        public abstract Builder types(List<String> types);
        public abstract AssetIdentityExclusionRequest build();
    }
}
