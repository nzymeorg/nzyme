package app.nzyme.core.rest.responses.ethernet.vpn;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;

@AutoValue
public abstract class VpnTunnelDetailsResponse {

    @JsonProperty("client")
    public abstract L4AddressResponse client();

    @JsonProperty("server")
    public abstract L4AddressResponse server();

    @JsonProperty("session_key")
    public abstract String sessionKey();

    @JsonProperty("l4_type")
    public abstract String l4Type();

    @JsonProperty("vpn_type")
    public abstract String vpnType();

    @JsonProperty("handshake_status")
    public abstract String handshakeStatus();

    @JsonProperty("message_types")
    public abstract List<String> messageTypes();

    @Nullable
    @JsonProperty("provider")
    public abstract String provider();

    @Nullable
    @JsonProperty("server_name")
    public abstract String serverName();

    @JsonProperty("details")
    public abstract Map<String, String> details();

    @JsonProperty("connection_status")
    public abstract String connectionStatus();

    @JsonProperty("tunneled_bytes")
    public abstract long tunneledBytes();

    @JsonProperty("established_at")
    public abstract DateTime establishedAt();

    @Nullable
    @JsonProperty("terminated_at")
    public abstract DateTime terminatedAt();

    @JsonProperty("most_recent_segment_time")
    public abstract DateTime mostRecentSegmentTime();

    @JsonProperty("duration_ms")
    public abstract long durationMs();

    public static VpnTunnelDetailsResponse create(L4AddressResponse client, L4AddressResponse server, String sessionKey, String l4Type, String vpnType, String handshakeStatus, List<String> messageTypes, String provider, String serverName, Map<String, String> details, String connectionStatus, long tunneledBytes, DateTime establishedAt, DateTime terminatedAt, DateTime mostRecentSegmentTime, long durationMs) {
        return builder()
                .client(client)
                .server(server)
                .sessionKey(sessionKey)
                .l4Type(l4Type)
                .vpnType(vpnType)
                .handshakeStatus(handshakeStatus)
                .messageTypes(messageTypes)
                .provider(provider)
                .serverName(serverName)
                .details(details)
                .connectionStatus(connectionStatus)
                .tunneledBytes(tunneledBytes)
                .establishedAt(establishedAt)
                .terminatedAt(terminatedAt)
                .mostRecentSegmentTime(mostRecentSegmentTime)
                .durationMs(durationMs)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_VpnTunnelDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder client(L4AddressResponse client);

        public abstract Builder server(L4AddressResponse server);

        public abstract Builder sessionKey(String sessionKey);

        public abstract Builder l4Type(String l4Type);

        public abstract Builder vpnType(String vpnType);

        public abstract Builder handshakeStatus(String handshakeStatus);

        public abstract Builder messageTypes(List<String> messageTypes);

        public abstract Builder provider(String provider);

        public abstract Builder serverName(String serverName);


        public abstract Builder details(Map<String, String> details);

        public abstract Builder connectionStatus(String connectionStatus);

        public abstract Builder tunneledBytes(long tunneledBytes);

        public abstract Builder establishedAt(DateTime establishedAt);

        public abstract Builder terminatedAt(DateTime terminatedAt);

        public abstract Builder mostRecentSegmentTime(DateTime mostRecentSegmentTime);

        public abstract Builder durationMs(long durationMs);

        public abstract VpnTunnelDetailsResponse build();
    }
}
