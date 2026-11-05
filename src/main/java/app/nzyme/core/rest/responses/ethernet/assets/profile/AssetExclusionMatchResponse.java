package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.UUID;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class AssetExclusionMatchResponse {

    @JsonProperty("type")
    public abstract String type();
    @JsonProperty("uuid")
    public abstract UUID uuid();
    @JsonProperty("name")
    public abstract String name();
    @JsonProperty("enabled")
    public abstract boolean enabled();
    @JsonProperty("matched_by")
    public abstract String matchedBy();
    @JsonProperty("match_count")
    public abstract long matchCount();
    @Nullable
    @JsonProperty("last_matched_at")
    public abstract DateTime lastMatchedAt();

    public static AssetExclusionMatchResponse create(String type, UUID uuid, String name, boolean enabled, String matchedBy, long matchCount, DateTime lastMatchedAt) {
        return builder()
                .type(type)
                .uuid(uuid)
                .name(name)
                .enabled(enabled)
                .matchedBy(matchedBy)
                .matchCount(matchCount)
                .lastMatchedAt(lastMatchedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetExclusionMatchResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder type(String type);
        public abstract Builder uuid(UUID uuid);
        public abstract Builder name(String name);
        public abstract Builder enabled(boolean enabled);
        public abstract Builder matchedBy(String matchedBy);
        public abstract Builder matchCount(long matchCount);
        public abstract Builder lastMatchedAt(DateTime lastMatchedAt);

        public abstract AssetExclusionMatchResponse build();
    }

}
