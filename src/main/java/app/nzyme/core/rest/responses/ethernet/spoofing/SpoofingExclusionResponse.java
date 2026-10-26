package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class SpoofingExclusionResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("description")
    public abstract String description();

    @JsonProperty("enabled")
    public abstract boolean enabled();

    @Nullable
    @JsonProperty("types")
    public abstract List<String> types();

    @Nullable
    @JsonProperty("address_cidr")
    public abstract String addressCidr();

    @Nullable
    @JsonProperty("mac")
    public abstract String mac();

    @JsonProperty("match_count")
    public abstract long matchCount();

    @Nullable
    @JsonProperty("last_matched_at")
    public abstract DateTime lastMatchedAt();

    @Nullable
    @JsonProperty("impact")
    public abstract SpoofingExclusionImpactResponse impact();

    @Nullable
    @JsonProperty("created_by_name")
    public abstract String createdByName();

    @Nullable
    @JsonProperty("created_by_email")
    public abstract String createdByEmail();

    @JsonProperty("created_at")
    public abstract DateTime createdAt();

    @JsonProperty("updated_at")
    public abstract DateTime updatedAt();

    public static SpoofingExclusionResponse create(UUID uuid, String name, String description, boolean enabled, List<String> types, String addressCidr, String mac, long matchCount, DateTime lastMatchedAt, SpoofingExclusionImpactResponse impact, String createdByName, String createdByEmail, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .uuid(uuid)
                .name(name)
                .description(description)
                .enabled(enabled)
                .types(types)
                .addressCidr(addressCidr)
                .mac(mac)
                .matchCount(matchCount)
                .lastMatchedAt(lastMatchedAt)
                .impact(impact)
                .createdByName(createdByName)
                .createdByEmail(createdByEmail)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SpoofingExclusionResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder enabled(boolean enabled);
        public abstract Builder types(List<String> types);
        public abstract Builder addressCidr(String addressCidr);
        public abstract Builder mac(String mac);
        public abstract Builder matchCount(long matchCount);
        public abstract Builder lastMatchedAt(DateTime lastMatchedAt);
        public abstract Builder impact(SpoofingExclusionImpactResponse impact);
        public abstract Builder createdByName(String createdByName);
        public abstract Builder createdByEmail(String createdByEmail);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract SpoofingExclusionResponse build();
    }
}
