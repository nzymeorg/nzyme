package app.nzyme.core.rest.resources.taps.reports.tables.vpn;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;

@AutoValue
public abstract class VpnTunnelReport {

    public abstract String vpnType();
    public abstract String transport();
    public abstract String handshakeStatus();
    public abstract List<String> messageTypes();
    @Nullable
    public abstract String provider();
    @Nullable
    public abstract String serverName();
    @Nullable
    public abstract Map<String, String> details();
    public abstract String connectionStatus();
    public abstract long tunneledBytes();
    @Nullable
    public abstract String sourceMac();
    @Nullable
    public abstract String destinationMac();
    public abstract String sourceAddress();
    public abstract int sourcePort();
    public abstract String destinationAddress();
    public abstract int destinationPort();
    public abstract DateTime establishedAt();
    @Nullable
    public abstract DateTime terminatedAt();
    public abstract DateTime mostRecentSegmentTime();

    @JsonCreator
    public static VpnTunnelReport create(@JsonProperty("vpn_type") String vpnType,
                                         @JsonProperty("transport") String transport,
                                         @JsonProperty("handshake_status") String handshakeStatus,
                                         @JsonProperty("message_types") List<String> messageTypes,
                                         @JsonProperty("provider") String provider,
                                         @JsonProperty("server_name") String serverName,
                                         @JsonProperty("details") Map<String, String> details,
                                         @JsonProperty("connection_status") String connectionStatus,
                                         @JsonProperty("tunneled_bytes") long tunneledBytes,
                                         @JsonProperty("source_mac") String sourceMac,
                                         @JsonProperty("destination_mac") String destinationMac,
                                         @JsonProperty("source_address") String sourceAddress,
                                         @JsonProperty("source_port") int sourcePort,
                                         @JsonProperty("destination_address") String destinationAddress,
                                         @JsonProperty("destination_port") int destinationPort,
                                         @JsonProperty("established_at") DateTime establishedAt,
                                         @JsonProperty("terminated_at") DateTime terminatedAt,
                                         @JsonProperty("most_recent_segment_time") DateTime mostRecentSegmentTime) {
        return builder()
                .vpnType(vpnType)
                .transport(transport)
                .handshakeStatus(handshakeStatus)
                .messageTypes(messageTypes)
                .provider(provider)
                .serverName(serverName)
                .details(details)
                .connectionStatus(connectionStatus)
                .tunneledBytes(tunneledBytes)
                .sourceMac(sourceMac)
                .destinationMac(destinationMac)
                .sourceAddress(sourceAddress)
                .sourcePort(sourcePort)
                .destinationAddress(destinationAddress)
                .destinationPort(destinationPort)
                .establishedAt(establishedAt)
                .terminatedAt(terminatedAt)
                .mostRecentSegmentTime(mostRecentSegmentTime)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_VpnTunnelReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder vpnType(String vpnType);

        public abstract Builder transport(String transport);

        public abstract Builder handshakeStatus(String handshakeStatus);

        public abstract Builder messageTypes(List<String> messageTypes);

        public abstract Builder provider(String provider);

        public abstract Builder serverName(String serverName);


        public abstract Builder details(Map<String, String> details);

        public abstract Builder connectionStatus(String connectionStatus);

        public abstract Builder tunneledBytes(long tunneledBytes);

        public abstract Builder sourceMac(String sourceMac);

        public abstract Builder destinationMac(String destinationMac);

        public abstract Builder sourceAddress(String sourceAddress);

        public abstract Builder sourcePort(int sourcePort);

        public abstract Builder destinationAddress(String destinationAddress);

        public abstract Builder destinationPort(int destinationPort);

        public abstract Builder establishedAt(DateTime establishedAt);

        public abstract Builder terminatedAt(DateTime terminatedAt);

        public abstract Builder mostRecentSegmentTime(DateTime mostRecentSegmentTime);

        public abstract VpnTunnelReport build();
    }
}
