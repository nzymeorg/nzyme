package app.nzyme.core.rest.responses.ethernet.assets;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class AssetTagSuggestionResponse {

    @Schema(description = "Name of the suggested tag.")
    @JsonProperty("tag")
    public abstract String tag();

    @Schema(description = "UUID of the tag of that name, if it exists already. Applying the suggestion then "
            + "only puts the tag on the asset.")
    @Nullable
    @JsonProperty("tag_uuid")
    public abstract UUID tagUuid();

    @JsonProperty("asset")
    public abstract AssetTagAssetResponse asset();

    @Schema(description = "The ports the asset serves that led to the suggestion.")
    @JsonProperty("ports")
    public abstract List<String> ports();

    @JsonProperty("sessions")
    public abstract long sessions();

    public static AssetTagSuggestionResponse create(String tag,
                                                    @Nullable UUID tagUuid,
                                                    AssetTagAssetResponse asset,
                                                    List<String> ports,
                                                    long sessions) {
        return builder()
                .tag(tag)
                .tagUuid(tagUuid)
                .asset(asset)
                .ports(ports)
                .sessions(sessions)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetTagSuggestionResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder tag(String tag);
        public abstract Builder tagUuid(UUID tagUuid);
        public abstract Builder asset(AssetTagAssetResponse asset);
        public abstract Builder ports(List<String> ports);
        public abstract Builder sessions(long sessions);

        public abstract AssetTagSuggestionResponse build();
    }

}
