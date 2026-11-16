package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class SegmentSuggestionResponse {

    @Schema(description = "Where the suggestion comes from: NETWORK_CONTEXT, DHCP or OBSERVED.")
    @JsonProperty("source")
    public abstract String source();

    @JsonProperty("kind")
    public abstract String kind();

    @Schema(description = "Network in CIDR notation.")
    @JsonProperty("network")
    public abstract String network();

    @Nullable
    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("description")
    public abstract String description();

    @Schema(description = "Number of addresses of the network that were seen. Only set for observed networks.")
    @Nullable
    @JsonProperty("hosts")
    public abstract Long hosts();

    @Schema(description = "Address of the trusted DHCP server that hands out addresses for the network. Only set "
            + "for suggestions of the source DHCP.")
    @Nullable
    @JsonProperty("dhcp_server")
    public abstract String dhcpServer();

    public static SegmentSuggestionResponse create(String source,
                                                   String kind,
                                                   String network,
                                                   String name,
                                                   String description,
                                                   Long hosts,
                                                   String dhcpServer) {
        return builder()
                .source(source)
                .kind(kind)
                .network(network)
                .name(name)
                .description(description)
                .hosts(hosts)
                .dhcpServer(dhcpServer)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SegmentSuggestionResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder source(String source);
        public abstract Builder kind(String kind);
        public abstract Builder network(String network);
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder hosts(Long hosts);
        public abstract Builder dhcpServer(String dhcpServer);

        public abstract SegmentSuggestionResponse build();
    }

}
