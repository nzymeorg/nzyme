package app.nzyme.core.ethernet.vpn.db;

import app.nzyme.core.ethernet.l4.db.L4AddressData;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;

@AutoValue
public abstract class VpnTunnelEntry {

    public abstract String sessionKey();
    public abstract String l4Type();
    public abstract String vpnType();
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
    public abstract L4AddressData client();
    public abstract L4AddressData server();
    public abstract DateTime establishedAt();
    @Nullable
    public abstract DateTime terminatedAt();
    public abstract DateTime mostRecentSegmentTime();
    public abstract long durationMs();

    public static VpnTunnelEntry create(String sessionKey, String l4Type, String vpnType, String handshakeStatus, List<String> messageTypes, String provider, String serverName, Map<String, String> details, String connectionStatus, long tunneledBytes, L4AddressData client, L4AddressData server, DateTime establishedAt, DateTime terminatedAt, DateTime mostRecentSegmentTime, long durationMs) {
        return builder()
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
                .client(client)
                .server(server)
                .establishedAt(establishedAt)
                .terminatedAt(terminatedAt)
                .mostRecentSegmentTime(mostRecentSegmentTime)
                .durationMs(durationMs)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_VpnTunnelEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
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

        public abstract Builder client(L4AddressData client);

        public abstract Builder server(L4AddressData server);

        public abstract Builder establishedAt(DateTime establishedAt);

        public abstract Builder terminatedAt(DateTime terminatedAt);

        public abstract Builder mostRecentSegmentTime(DateTime mostRecentSegmentTime);

        public abstract Builder durationMs(long durationMs);

        public abstract VpnTunnelEntry build();
    }
}
