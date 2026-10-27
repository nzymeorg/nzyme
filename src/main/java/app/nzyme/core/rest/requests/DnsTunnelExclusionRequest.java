package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@Schema(description = "Body for creating, updating or previewing a DNS tunnel exclusion. Both criteria are "
        + "optional, but at least one must be set. A finding is excluded when every set criterion matches.")
@AutoValue
public abstract class DnsTunnelExclusionRequest {

    @Nullable
    public abstract String name();

    @Nullable
    public abstract String description();

    @Nullable
    public abstract Boolean enabled();

    @Nullable
    public abstract String domain();

    @Nullable
    public abstract String clientAddressCidr();

    @Nullable
    public abstract Boolean applyToExistingFindings();

    @JsonCreator
    public static DnsTunnelExclusionRequest create(@Schema(description = "Name of the exclusion. Required when creating or updating, ignored for previews.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                   @JsonProperty("name") String name,
                                                   @Schema(description = "Optional description of why the exclusion exists.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                   @JsonProperty("description") String description,
                                                   @Schema(description = "Whether the exclusion is active. Defaults to true.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                   @JsonProperty("enabled") Boolean enabled,
                                                   @Schema(description = "Domain to exclude. Matches a finding's registered domain exactly or as a parent domain, so example.com also matches sub.example.com.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                   @JsonProperty("domain") String domain,
                                                   @Schema(description = "IP address or CIDR network of the client, like 10.0.0.1 or 10.0.0.0/24.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                   @JsonProperty("client_address_cidr") String clientAddressCidr,
                                                   @Schema(description = "Only when creating: also delete the existing findings that match the exclusion. Defaults to false.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                   @JsonProperty("apply_to_existing_findings") Boolean applyToExistingFindings) {
        return builder()
                .name(name)
                .description(description)
                .enabled(enabled)
                .domain(domain)
                .clientAddressCidr(clientAddressCidr)
                .applyToExistingFindings(applyToExistingFindings)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsTunnelExclusionRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder enabled(Boolean enabled);
        public abstract Builder domain(String domain);
        public abstract Builder clientAddressCidr(String clientAddressCidr);
        public abstract Builder applyToExistingFindings(Boolean applyToExistingFindings);
        public abstract DnsTunnelExclusionRequest build();
    }
}
