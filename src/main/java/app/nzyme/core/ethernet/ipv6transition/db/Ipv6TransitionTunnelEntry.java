package app.nzyme.core.ethernet.ipv6transition.db;

import app.nzyme.core.ethernet.l4.db.L4AddressData;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.Map;

@AutoValue
public abstract class Ipv6TransitionTunnelEntry {

    public abstract String sessionKey();

    public abstract String tunnelType();

    public abstract String transport();

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

    public static Ipv6TransitionTunnelEntry create(String sessionKey,
                                                   String tunnelType,
                                                   String transport,
                                                   Map<String, String> details,
                                                   String connectionStatus,
                                                   long tunneledBytes,
                                                   L4AddressData client,
                                                   L4AddressData server,
                                                   DateTime establishedAt,
                                                   DateTime terminatedAt,
                                                   DateTime mostRecentSegmentTime,
                                                   long durationMs) {
        return builder()
                .sessionKey(sessionKey)
                .tunnelType(tunnelType)
                .transport(transport)
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
        return new AutoValue_Ipv6TransitionTunnelEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sessionKey(String sessionKey);

        public abstract Builder tunnelType(String tunnelType);

        public abstract Builder transport(String transport);

        public abstract Builder details(Map<String, String> details);

        public abstract Builder connectionStatus(String connectionStatus);

        public abstract Builder tunneledBytes(long tunneledBytes);

        public abstract Builder client(L4AddressData client);

        public abstract Builder server(L4AddressData server);

        public abstract Builder establishedAt(DateTime establishedAt);

        public abstract Builder terminatedAt(DateTime terminatedAt);

        public abstract Builder mostRecentSegmentTime(DateTime mostRecentSegmentTime);

        public abstract Builder durationMs(long durationMs);

        public abstract Ipv6TransitionTunnelEntry build();
    }

}
