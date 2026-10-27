package app.nzyme.core.rest.responses.ethernet.dnstunnels;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class DnsTunnelExclusionResponse {

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
    @JsonProperty("domain")
    public abstract String domain();

    @Nullable
    @JsonProperty("client_address_cidr")
    public abstract String clientAddressCidr();

    @JsonProperty("match_count")
    public abstract long matchCount();

    @Nullable
    @JsonProperty("last_matched_at")
    public abstract DateTime lastMatchedAt();

    @Nullable
    @JsonProperty("impact")
    public abstract DnsTunnelExclusionImpactResponse impact();

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

    public static DnsTunnelExclusionResponse create(UUID uuid, String name, String description, boolean enabled, String domain, String clientAddressCidr, long matchCount, DateTime lastMatchedAt, DnsTunnelExclusionImpactResponse impact, String createdByName, String createdByEmail, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .uuid(uuid)
                .name(name)
                .description(description)
                .enabled(enabled)
                .domain(domain)
                .clientAddressCidr(clientAddressCidr)
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
        return new AutoValue_DnsTunnelExclusionResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder enabled(boolean enabled);
        public abstract Builder domain(String domain);
        public abstract Builder clientAddressCidr(String clientAddressCidr);
        public abstract Builder matchCount(long matchCount);
        public abstract Builder lastMatchedAt(DateTime lastMatchedAt);
        public abstract Builder impact(DnsTunnelExclusionImpactResponse impact);
        public abstract Builder createdByName(String createdByName);
        public abstract Builder createdByEmail(String createdByEmail);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract DnsTunnelExclusionResponse build();
    }
}
