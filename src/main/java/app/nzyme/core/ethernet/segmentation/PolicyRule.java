package app.nzyme.core.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;

// An empty list means anything. Broadcast and multicast addresses are the exception: a scope only takes them in if
// it names them. Inside of a scope that names them, an allowed crossing to any destination covers them as well.
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicyRule(@JsonProperty("sources") @Nullable List<Selector> sources,
                         @JsonProperty("destinations") @Nullable List<Selector> destinations,
                         @JsonProperty("services") @Nullable List<Service> services) {

    // Only what the taps actually tag, which is less than the L7Tag enum of the tap knows: a policy about a protocol
    // that nothing tags would look like it holds. A tagger that is added to the tap has to be added here.
    public static final List<String> PROTOCOLS = List.of("DERP", "DHCP4", "DHCP6", "DNS", "DTLS", "HTTP", "NTP", "OpenVPN",
            "RTP", "RTSP", "SOCKS", "SSH", "STUN", "Teredo", "TLS", "TURN", "WebRTC", "WireGuard");

    public static boolean isKnownProtocol(@Nullable String protocol) {
        if (protocol == null) {
            return false;
        }

        for (String known : PROTOCOLS) {
            if (known.equalsIgnoreCase(protocol.trim())) {
                return true;
            }
        }

        return false;
    }

    public PolicyRule {
        sources = sources == null ? Collections.emptyList() : List.copyOf(sources);
        destinations = destinations == null ? Collections.emptyList() : List.copyOf(destinations);
        services = services == null ? Collections.emptyList() : List.copyOf(services);
    }

    public enum SelectorType {
        ANY(false),
        INTERNAL(false),
        EXTERNAL(false),
        UNASSIGNED(false),
        BROADCAST(false),
        SEGMENT(true),
        TAG(true),
        ASSET(true),
        ADDRESS(true),
        ASN(true),
        COUNTRY(true);

        private final boolean hasValue;

        SelectorType(boolean hasValue) {
            this.hasValue = hasValue;
        }

        public boolean hasValue() {
            return hasValue;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Selector(@JsonProperty("type") SelectorType type,
                           @JsonProperty("value") @Nullable String value) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Service(@JsonProperty("l4_type") @Nullable String l4Type,
                          @JsonProperty("port_from") @Nullable Integer portFrom,
                          @JsonProperty("port_to") @Nullable Integer portTo,
                          @JsonProperty("protocol") @Nullable String protocol) { }

}
