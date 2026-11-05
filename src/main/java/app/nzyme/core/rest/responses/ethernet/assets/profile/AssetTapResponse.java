package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.UUID;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class AssetTapResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();
    @Nullable
    @JsonProperty("name")
    public abstract String name();
    @JsonProperty("count")
    public abstract long count();
    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();
    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static AssetTapResponse create(UUID uuid, String name, long count, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .uuid(uuid)
                .name(name)
                .count(count)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetTapResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder name(String name);
        public abstract Builder count(long count);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract AssetTapResponse build();
    }

}
