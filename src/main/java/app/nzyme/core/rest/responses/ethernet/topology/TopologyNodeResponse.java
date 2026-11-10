package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.UUID;

@AutoValue
public abstract class TopologyNodeResponse {

    @Schema(description = "Node ID. Pass it as node or peer parameter to narrow a request to this node.")
    @JsonProperty("id")
    public abstract String id();

    @Schema(description = "INTERNAL for site-local addresses, EXTERNAL for all others.")
    @JsonProperty("type")
    public abstract String type();

    @Schema(description = "What the node groups: HOST, SUBNET, TAP, NETWORK (an autonomous system) or COUNTRY.")
    @JsonProperty("level")
    public abstract String level();

    @Schema(description = "IP address of a HOST node.")
    @Nullable
    @JsonProperty("address")
    public abstract String address();

    @Schema(description = "CIDR of a SUBNET node.")
    @Nullable
    @JsonProperty("network")
    public abstract String network();

    @Schema(description = "Tap of a TAP node.")
    @Nullable
    @JsonProperty("tap_uuid")
    public abstract UUID tapUuid();

    @Nullable
    @JsonProperty("tap_name")
    public abstract String tapName();

    @Schema(description = "Asset that most recently used the address of an internal HOST node.")
    @Nullable
    @JsonProperty("asset")
    public abstract TopologyNodeAssetResponse asset();

    @Schema(description = "Most recent hostname of that asset.")
    @Nullable
    @JsonProperty("hostname")
    public abstract String hostname();

    @Nullable
    @JsonProperty("asn_number")
    public abstract Integer asnNumber();

    @Nullable
    @JsonProperty("asn_name")
    public abstract String asnName();

    @Schema(description = "Country of an external node. Not set for an autonomous system with traffic in more than one country.")
    @Nullable
    @JsonProperty("country_code")
    public abstract String countryCode();

    public static TopologyNodeResponse create(String id, String type, String level, @Nullable String address,
                                              @Nullable String network, @Nullable UUID tapUuid,
                                              @Nullable String tapName, @Nullable TopologyNodeAssetResponse asset,
                                              @Nullable String hostname, @Nullable Integer asnNumber,
                                              @Nullable String asnName, @Nullable String countryCode) {
        return builder()
                .id(id)
                .type(type)
                .level(level)
                .address(address)
                .network(network)
                .tapUuid(tapUuid)
                .tapName(tapName)
                .asset(asset)
                .hostname(hostname)
                .asnNumber(asnNumber)
                .asnName(asnName)
                .countryCode(countryCode)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologyNodeResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(String id);
        public abstract Builder type(String type);
        public abstract Builder level(String level);
        public abstract Builder address(String address);
        public abstract Builder network(String network);
        public abstract Builder tapUuid(UUID tapUuid);
        public abstract Builder tapName(String tapName);
        public abstract Builder asset(TopologyNodeAssetResponse asset);
        public abstract Builder hostname(String hostname);
        public abstract Builder asnNumber(Integer asnNumber);
        public abstract Builder asnName(String asnName);
        public abstract Builder countryCode(String countryCode);

        public abstract TopologyNodeResponse build();
    }

}
