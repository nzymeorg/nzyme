package app.nzyme.core.rest.responses.ethernet.icmp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class IcmpExclusionResponse {

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
    @JsonProperty("detection_types")
    public abstract List<String> detectionTypes();

    @Nullable
    @JsonProperty("source_cidr")
    public abstract String sourceCidr();

    @Nullable
    @JsonProperty("source_mac")
    public abstract String sourceMac();

    @Nullable
    @JsonProperty("gateway_cidr")
    public abstract String gatewayCidr();

    @JsonProperty("match_count")
    public abstract long matchCount();

    @Nullable
    @JsonProperty("last_matched_at")
    public abstract DateTime lastMatchedAt();

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

    public static IcmpExclusionResponse create(UUID uuid, String name, String description, boolean enabled, List<String> detectionTypes, String sourceCidr, String sourceMac, String gatewayCidr, long matchCount, DateTime lastMatchedAt, String createdByName, String createdByEmail, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .uuid(uuid)
                .name(name)
                .description(description)
                .enabled(enabled)
                .detectionTypes(detectionTypes)
                .sourceCidr(sourceCidr)
                .sourceMac(sourceMac)
                .gatewayCidr(gatewayCidr)
                .matchCount(matchCount)
                .lastMatchedAt(lastMatchedAt)
                .createdByName(createdByName)
                .createdByEmail(createdByEmail)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpExclusionResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder enabled(boolean enabled);
        public abstract Builder detectionTypes(List<String> detectionTypes);
        public abstract Builder sourceCidr(String sourceCidr);
        public abstract Builder sourceMac(String sourceMac);
        public abstract Builder gatewayCidr(String gatewayCidr);
        public abstract Builder matchCount(long matchCount);
        public abstract Builder lastMatchedAt(DateTime lastMatchedAt);
        public abstract Builder createdByName(String createdByName);
        public abstract Builder createdByEmail(String createdByEmail);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract IcmpExclusionResponse build();
    }
}
