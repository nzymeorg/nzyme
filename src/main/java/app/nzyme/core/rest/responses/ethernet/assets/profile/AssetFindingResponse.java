package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class AssetFindingResponse {

    @JsonProperty("type")
    public abstract String type();
    @JsonProperty("role")
    public abstract String role();
    @JsonProperty("count")
    public abstract long count();
    @Nullable
    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static AssetFindingResponse create(String type, String role, long count, DateTime lastSeen) {
        return builder()
                .type(type)
                .role(role)
                .count(count)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetFindingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder type(String type);
        public abstract Builder role(String role);
        public abstract Builder count(long count);
        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract AssetFindingResponse build();
    }

}
