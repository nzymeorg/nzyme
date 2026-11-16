package app.nzyme.core.rest.responses.ethernet.assets;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class AssetTagResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("description")
    public abstract String description();

    @JsonProperty("asset_count")
    public abstract long assetCount();

    @Schema(description = "Names of the segmentation policies that refer to the tag.")
    @JsonProperty("policies")
    public abstract List<String> policies();

    @JsonProperty("created_at")
    public abstract DateTime createdAt();

    @JsonProperty("updated_at")
    public abstract DateTime updatedAt();

    public static AssetTagResponse create(UUID uuid,
                                          String name,
                                          String description,
                                          long assetCount,
                                          List<String> policies,
                                          DateTime createdAt,
                                          DateTime updatedAt) {
        return builder()
                .uuid(uuid)
                .name(name)
                .description(description)
                .assetCount(assetCount)
                .policies(policies)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetTagResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder assetCount(long assetCount);
        public abstract Builder policies(List<String> policies);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract AssetTagResponse build();
    }

}
