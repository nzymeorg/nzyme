package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class SegmentSuggestionResponse {

    @Schema(description = "Where the suggestion comes from: DHCP or OBSERVED.")
    @JsonProperty("source")
    public abstract String source();

    @Schema(description = "Network in CIDR notation.")
    @JsonProperty("network")
    public abstract String network();

    @Schema(description = "Number of addresses of the network that are in no segment and opened a session or answered one in the "
            + "days the suggestions look back, as recorded by the taps the caller has access to. Not set for a caller without access "
            + "to a tap.")
    @Nullable
    @JsonProperty("hosts")
    public abstract Long hosts();

    @Schema(description = "Address of the trusted DHCP server that hands out addresses for the network. Only set "
            + "for suggestions of the source DHCP.")
    @Nullable
    @JsonProperty("dhcp_server")
    public abstract String dhcpServer();

    public static SegmentSuggestionResponse create(String source,
                                                   String network,
                                                   Long hosts,
                                                   String dhcpServer) {
        return builder()
                .source(source)
                .network(network)
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
        public abstract Builder network(String network);
        public abstract Builder hosts(Long hosts);
        public abstract Builder dhcpServer(String dhcpServer);

        public abstract SegmentSuggestionResponse build();
    }

}
