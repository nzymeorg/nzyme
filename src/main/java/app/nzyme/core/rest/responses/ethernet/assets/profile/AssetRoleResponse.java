package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class AssetRoleResponse {

    @JsonProperty("role")
    public abstract String role();
    @JsonProperty("count")
    public abstract long count();
    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();
    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();
    @Nullable
    @JsonProperty("dependents")
    public abstract Long dependents();

    public static AssetRoleResponse create(String role, long count, DateTime firstSeen, DateTime lastSeen, Long dependents) {
        return builder()
                .role(role)
                .count(count)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .dependents(dependents)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetRoleResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder role(String role);
        public abstract Builder count(long count);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder dependents(Long dependents);

        public abstract AssetRoleResponse build();
    }

}
