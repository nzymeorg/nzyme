package app.nzyme.core.rest.responses.ethernet.segmentation;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class FlowSideResponse {

    @JsonProperty("key")
    public abstract String key();

    @JsonProperty("internal")
    public abstract boolean internal();

    @Schema(description = "If the side is a broadcast or multicast address. Such a side is neither internal nor external and in no segment.")
    @JsonProperty("broadcast")
    public abstract boolean broadcast();

    @Schema(description = "Not set for external sides, which are stored per network, and for the folded destinations of a sweep.")
    @Nullable
    @JsonProperty("address")
    public abstract L4AddressResponse address();

    @Nullable
    @JsonProperty("segment_uuid")
    public abstract UUID segmentUuid();

    @Nullable
    @JsonProperty("segment_name")
    public abstract String segmentName();

    @Nullable
    @JsonProperty("asn")
    public abstract Long asn();

    @Schema(description = "Name of the organization that runs the autonomous system. Not set for flows that were stored before it was recorded.")
    @Nullable
    @JsonProperty("asn_name")
    public abstract String asnName();

    @Schema(description = "Domain of the organization that runs the autonomous system.")
    @Nullable
    @JsonProperty("asn_domain")
    public abstract String asnDomain();

    @Nullable
    @JsonProperty("country_code")
    public abstract String countryCode();

    @Schema(description = "Names of the tags of the asset behind the address.")
    @JsonProperty("tags")
    public abstract List<String> tags();

    public static FlowSideResponse create(String key,
                                          boolean internal,
                                          boolean broadcast,
                                          L4AddressResponse address,
                                          UUID segmentUuid,
                                          String segmentName,
                                          Long asn,
                                          String asnName,
                                          String asnDomain,
                                          String countryCode,
                                          List<String> tags) {
        return builder()
                .key(key)
                .internal(internal)
                .broadcast(broadcast)
                .address(address)
                .segmentUuid(segmentUuid)
                .segmentName(segmentName)
                .asn(asn)
                .asnName(asnName)
                .asnDomain(asnDomain)
                .countryCode(countryCode)
                .tags(tags)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FlowSideResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder key(String key);
        public abstract Builder internal(boolean internal);
        public abstract Builder broadcast(boolean broadcast);
        public abstract Builder address(L4AddressResponse address);
        public abstract Builder segmentUuid(UUID segmentUuid);
        public abstract Builder segmentName(String segmentName);
        public abstract Builder asn(Long asn);
        public abstract Builder asnName(String asnName);
        public abstract Builder asnDomain(String asnDomain);
        public abstract Builder countryCode(String countryCode);
        public abstract Builder tags(List<String> tags);

        public abstract FlowSideResponse build();
    }

}
