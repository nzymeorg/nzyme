package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.util.List;

@Schema(description = "Body for creating, updating or previewing a spoofing exclusion. All criteria are optional, "
        + "but at least one must be set. A finding is excluded when every set criterion matches.")
@AutoValue
public abstract class SpoofingExclusionRequest {

    @Nullable
    public abstract String name();

    @Nullable
    public abstract String description();

    @Nullable
    public abstract Boolean enabled();

    @Nullable
    public abstract List<String> types();

    @Nullable
    public abstract String addressCidr();

    @Nullable
    public abstract String mac();

    @Nullable
    public abstract Boolean applyToExistingFindings();

    @JsonCreator
    public static SpoofingExclusionRequest create(@Schema(description = "Name of the exclusion. Required when creating or updating, ignored for previews.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                  @JsonProperty("name") String name,
                                                  @Schema(description = "Optional description of why the exclusion exists.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                  @JsonProperty("description") String description,
                                                  @Schema(description = "Whether the exclusion is active. Defaults to true.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                  @JsonProperty("enabled") Boolean enabled,
                                                  @Schema(description = "Spoofing types to match: ARP. Omit or pass an empty list to match all types.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                  @JsonProperty("types") List<String> types,
                                                  @Schema(description = "IP address or CIDR network of the address that is being claimed, like 10.0.0.1 or 10.0.0.0/24.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                  @JsonProperty("address_cidr") String addressCidr,
                                                  @Schema(description = "MAC address of the device doing the claiming.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                  @JsonProperty("mac") String mac,
                                                  @Schema(description = "Only when creating: also delete the existing findings that match the exclusion. Defaults to false.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                                  @JsonProperty("apply_to_existing_findings") Boolean applyToExistingFindings) {
        return builder()
                .name(name)
                .description(description)
                .enabled(enabled)
                .types(types)
                .addressCidr(addressCidr)
                .mac(mac)
                .applyToExistingFindings(applyToExistingFindings)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SpoofingExclusionRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder enabled(Boolean enabled);
        public abstract Builder types(List<String> types);
        public abstract Builder addressCidr(String addressCidr);
        public abstract Builder mac(String mac);
        public abstract Builder applyToExistingFindings(Boolean applyToExistingFindings);
        public abstract SpoofingExclusionRequest build();
    }
}
