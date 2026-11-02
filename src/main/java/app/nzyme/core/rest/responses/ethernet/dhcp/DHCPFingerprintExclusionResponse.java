package app.nzyme.core.rest.responses.ethernet.dhcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class DHCPFingerprintExclusionResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("description")
    public abstract String description();

    @JsonProperty("enabled")
    public abstract boolean enabled();

    @JsonProperty("mac")
    public abstract String mac();

    @JsonProperty("mac_is_prefix")
    public abstract boolean macIsPrefix();

    @JsonProperty("transaction_types")
    public abstract List<String> transactionTypes();

    @JsonProperty("match_count")
    public abstract long matchCount();

    @Nullable
    @JsonProperty("last_matched_at")
    public abstract DateTime lastMatchedAt();

    @Nullable
    @JsonProperty("impact")
    public abstract DHCPFingerprintExclusionImpactResponse impact();

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

    public static DHCPFingerprintExclusionResponse create(UUID uuid, String name, String description, boolean enabled, String mac, boolean macIsPrefix, List<String> transactionTypes, long matchCount, DateTime lastMatchedAt, DHCPFingerprintExclusionImpactResponse impact, String createdByName, String createdByEmail, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .uuid(uuid)
                .name(name)
                .description(description)
                .enabled(enabled)
                .mac(mac)
                .macIsPrefix(macIsPrefix)
                .transactionTypes(transactionTypes)
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
        return new AutoValue_DHCPFingerprintExclusionResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder enabled(boolean enabled);
        public abstract Builder mac(String mac);
        public abstract Builder macIsPrefix(boolean macIsPrefix);
        public abstract Builder transactionTypes(List<String> transactionTypes);
        public abstract Builder matchCount(long matchCount);
        public abstract Builder lastMatchedAt(DateTime lastMatchedAt);
        public abstract Builder impact(DHCPFingerprintExclusionImpactResponse impact);
        public abstract Builder createdByName(String createdByName);
        public abstract Builder createdByEmail(String createdByEmail);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract DHCPFingerprintExclusionResponse build();
    }
}
