package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.util.List;

@Schema(description = "Body for creating or updating an ICMP exclusion. All criteria are optional, but at least "
        + "one must be set. A detection is suppressed when every set criterion matches.")
@AutoValue
public abstract class IcmpExclusionRequest {

    @Nullable
    public abstract String name();

    @Nullable
    public abstract String description();

    @Nullable
    public abstract Boolean enabled();

    @Nullable
    public abstract List<String> detectionTypes();

    @Nullable
    public abstract String sourceCidr();

    @Nullable
    public abstract String sourceMac();

    @Nullable
    public abstract String gatewayCidr();

    @JsonCreator
    public static IcmpExclusionRequest create(@Schema(description = "Name of the exclusion. Required when creating or updating.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("name") String name,
                                              @Schema(description = "Optional description of why the exclusion exists.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("description") String description,
                                              @Schema(description = "Whether the exclusion is active. Defaults to true.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("enabled") Boolean enabled,
                                              @Schema(description = "Detection types to match: REDIRECT, TRACEROUTE. Omit or pass an empty list to match both.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("detection_types") List<String> detectionTypes,
                                              @Schema(description = "IP address or CIDR network of the sending device: the redirect sender or the tracing host.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("source_cidr") String sourceCidr,
                                              @Schema(description = "MAC address of the sending device.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("source_mac") String sourceMac,
                                              @Schema(description = "Redirects only: IP address or CIDR network of the advertised gateway. A gateway criterion never matches a traceroute.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                              @JsonProperty("gateway_cidr") String gatewayCidr) {
        return builder()
                .name(name)
                .description(description)
                .enabled(enabled)
                .detectionTypes(detectionTypes)
                .sourceCidr(sourceCidr)
                .sourceMac(sourceMac)
                .gatewayCidr(gatewayCidr)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpExclusionRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder enabled(Boolean enabled);
        public abstract Builder detectionTypes(List<String> detectionTypes);
        public abstract Builder sourceCidr(String sourceCidr);
        public abstract Builder sourceMac(String sourceMac);
        public abstract Builder gatewayCidr(String gatewayCidr);
        public abstract IcmpExclusionRequest build();
    }
}
