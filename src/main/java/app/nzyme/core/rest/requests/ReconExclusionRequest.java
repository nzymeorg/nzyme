package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.util.List;

@Schema(description = "Body for creating, updating or previewing a recon exclusion. All criteria are optional, but "
        + "at least one must be set. A scan is excluded when every set criterion matches.")
@AutoValue
public abstract class ReconExclusionRequest {

    @Nullable
    public abstract String name();

    @Nullable
    public abstract String description();

    @Nullable
    public abstract Boolean enabled();

    @Nullable
    public abstract List<String> scanTypes();

    @Nullable
    public abstract String sourceCidr();

    @Nullable
    public abstract String sourceMac();

    @Nullable
    public abstract String targetCidr();

    @Nullable
    public abstract Boolean applyToExistingScans();

    @JsonCreator
    public static ReconExclusionRequest create(@Schema(description = "Name of the exclusion. Required when creating or updating, ignored for previews.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                               @JsonProperty("name") String name,
                                               @Schema(description = "Optional description of why the exclusion exists.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                               @JsonProperty("description") String description,
                                               @Schema(description = "Whether the exclusion is active. Defaults to true.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                               @JsonProperty("enabled") Boolean enabled,
                                               @Schema(description = "Scan types to match: TCP_PORT_SCAN, TCP_HOST_SWEEP, UDP_PORT_SCAN, UDP_HOST_SWEEP, ARP_SWEEP, NDP_SWEEP. Omit or pass an empty list to match all types.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                               @JsonProperty("scan_types") List<String> scanTypes,
                                               @Schema(description = "IP address or CIDR network of the scanning device, like 10.0.0.5 or 10.0.0.0/24.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                               @JsonProperty("source_cidr") String sourceCidr,
                                               @Schema(description = "MAC address of the scanning device.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                               @JsonProperty("source_mac") String sourceMac,
                                               @Schema(description = "IP address or CIDR network of the scanned targets. Matches a port scan by its target host and a sweep when every observed target is inside the network.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                               @JsonProperty("target_cidr") String targetCidr,
                                               @Schema(description = "Only when creating: also delete the existing scans that match the exclusion. Defaults to false.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                               @JsonProperty("apply_to_existing_scans") Boolean applyToExistingScans) {
        return builder()
                .name(name)
                .description(description)
                .enabled(enabled)
                .scanTypes(scanTypes)
                .sourceCidr(sourceCidr)
                .sourceMac(sourceMac)
                .targetCidr(targetCidr)
                .applyToExistingScans(applyToExistingScans)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ReconExclusionRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder enabled(Boolean enabled);
        public abstract Builder scanTypes(List<String> scanTypes);
        public abstract Builder sourceCidr(String sourceCidr);
        public abstract Builder sourceMac(String sourceMac);
        public abstract Builder targetCidr(String targetCidr);
        public abstract Builder applyToExistingScans(Boolean applyToExistingScans);
        public abstract ReconExclusionRequest build();
    }
}
