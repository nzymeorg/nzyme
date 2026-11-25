package app.nzyme.core.rest.responses.ethernet.ipv6transition;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.Map;

@AutoValue
public abstract class Ipv6TransitionTunnelDetailsResponse {

    @JsonProperty("client")
    public abstract L4AddressResponse client();

    @JsonProperty("server")
    public abstract L4AddressResponse server();

    @JsonProperty("session_key")
    public abstract String sessionKey();

    @JsonProperty("tunnel_type")
    public abstract String tunnelType();

    @JsonProperty("transport")
    public abstract String transport();

    @JsonProperty("details")
    public abstract Map<String, String> details();

    @JsonProperty("connection_status")
    public abstract String connectionStatus();

    @JsonProperty("tunneled_bytes")
    public abstract long tunneledBytes();

    @JsonProperty("established_at")
    public abstract DateTime establishedAt();

    @JsonProperty("terminated_at")
    @Nullable
    public abstract DateTime terminatedAt();

    @JsonProperty("most_recent_segment_time")
    public abstract DateTime mostRecentSegmentTime();

    @JsonProperty("duration_ms")
    public abstract long durationMs();

    public static Ipv6TransitionTunnelDetailsResponse create(L4AddressResponse client,
                                                             L4AddressResponse server,
                                                             String sessionKey,
                                                             String tunnelType,
                                                             String transport,
                                                             Map<String, String> details,
                                                             String connectionStatus,
                                                             long tunneledBytes,
                                                             DateTime establishedAt,
                                                             DateTime terminatedAt,
                                                             DateTime mostRecentSegmentTime,
                                                             long durationMs) {
        return builder()
                .client(client)
                .server(server)
                .sessionKey(sessionKey)
                .tunnelType(tunnelType)
                .transport(transport)
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
        return new AutoValue_Ipv6TransitionTunnelDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder client(L4AddressResponse client);

        public abstract Builder server(L4AddressResponse server);

        public abstract Builder sessionKey(String sessionKey);

        public abstract Builder tunnelType(String tunnelType);

        public abstract Builder transport(String transport);

        public abstract Builder details(Map<String, String> details);

        public abstract Builder connectionStatus(String connectionStatus);

        public abstract Builder tunneledBytes(long tunneledBytes);

        public abstract Builder establishedAt(DateTime establishedAt);

        public abstract Builder terminatedAt(DateTime terminatedAt);

        public abstract Builder mostRecentSegmentTime(DateTime mostRecentSegmentTime);

        public abstract Builder durationMs(long durationMs);

        public abstract Ipv6TransitionTunnelDetailsResponse build();
    }

}
