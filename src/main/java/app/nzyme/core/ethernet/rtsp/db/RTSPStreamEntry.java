package app.nzyme.core.ethernet.rtsp.db;

import app.nzyme.core.ethernet.l4.db.L4AddressData;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.Set;
import java.util.UUID;

@AutoValue
public abstract class RTSPStreamEntry {

    public abstract String setupTcpSessionKey();
    public abstract String state();
    @Nullable
    public abstract String mediaLocator();
    @Nullable
    public abstract String requestUri();
    @Nullable
    public abstract String clientAgent();
    @Nullable
    public abstract String serverInfo();
    public abstract String authentication();
    public abstract Set<String> flags();
    public abstract String setupConnectionStatus();
    public abstract DateTime setupEstablishedAt();
    @Nullable
    public abstract DateTime setupTerminatedAt();
    public abstract DateTime setupMostRecentSegmentTime();
    @Nullable
    public abstract DateTime lastActivity();
    @Nullable
    public abstract Boolean isActive();
    @Nullable
    public abstract Long durationMs();

    @Nullable
    public abstract L4AddressData setupSource();
    @Nullable
    public abstract L4AddressData setupDestination();
    public abstract Long setupBytesExchanged();

    @Nullable
    public abstract String streamL4Type();
    @Nullable
    public abstract DateTime streamMostRecentSegmentTime();
    @Nullable
    public abstract L4AddressData streamSource();
    @Nullable
    public abstract L4AddressData streamDestination();
    @Nullable
    public abstract Long streamBytesRx();
    @Nullable
    public abstract Long streamBytesTx();

    public static RTSPStreamEntry create(String setupTcpSessionKey, String state, String mediaLocator, String requestUri, String clientAgent, String serverInfo, String authentication, Set<String> flags, String setupConnectionStatus, DateTime setupEstablishedAt, DateTime setupTerminatedAt, DateTime setupMostRecentSegmentTime, DateTime lastActivity, Boolean isActive, Long durationMs, L4AddressData setupSource, L4AddressData setupDestination, Long setupBytesExchanged, String streamL4Type, DateTime streamMostRecentSegmentTime, L4AddressData streamSource, L4AddressData streamDestination, Long streamBytesRx, Long streamBytesTx) {
        return builder()
                .setupTcpSessionKey(setupTcpSessionKey)
                .state(state)
                .mediaLocator(mediaLocator)
                .requestUri(requestUri)
                .clientAgent(clientAgent)
                .serverInfo(serverInfo)
                .authentication(authentication)
                .flags(flags)
                .setupConnectionStatus(setupConnectionStatus)
                .setupEstablishedAt(setupEstablishedAt)
                .setupTerminatedAt(setupTerminatedAt)
                .setupMostRecentSegmentTime(setupMostRecentSegmentTime)
                .lastActivity(lastActivity)
                .isActive(isActive)
                .durationMs(durationMs)
                .setupSource(setupSource)
                .setupDestination(setupDestination)
                .setupBytesExchanged(setupBytesExchanged)
                .streamL4Type(streamL4Type)
                .streamMostRecentSegmentTime(streamMostRecentSegmentTime)
                .streamSource(streamSource)
                .streamDestination(streamDestination)
                .streamBytesRx(streamBytesRx)
                .streamBytesTx(streamBytesTx)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_RTSPStreamEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setupTcpSessionKey(String setupTcpSessionKey);

        public abstract Builder state(String state);

        public abstract Builder mediaLocator(String mediaLocator);

        public abstract Builder requestUri(String requestUri);

        public abstract Builder clientAgent(String clientAgent);

        public abstract Builder serverInfo(String serverInfo);

        public abstract Builder authentication(String authentication);

        public abstract Builder flags(Set<String> flags);

        public abstract Builder setupConnectionStatus(String setupConnectionStatus);

        public abstract Builder setupEstablishedAt(DateTime setupEstablishedAt);

        public abstract Builder setupTerminatedAt(DateTime setupTerminatedAt);

        public abstract Builder setupMostRecentSegmentTime(DateTime setupMostRecentSegmentTime);

        public abstract Builder lastActivity(DateTime lastActivity);

        public abstract Builder isActive(Boolean isActive);

        public abstract Builder durationMs(Long durationMs);

        public abstract Builder setupSource(L4AddressData setupSource);

        public abstract Builder setupDestination(L4AddressData setupDestination);

        public abstract Builder setupBytesExchanged(Long setupBytesExchanged);

        public abstract Builder streamL4Type(String streamL4Type);

        public abstract Builder streamMostRecentSegmentTime(DateTime streamMostRecentSegmentTime);

        public abstract Builder streamSource(L4AddressData streamSource);

        public abstract Builder streamDestination(L4AddressData streamDestination);

        public abstract Builder streamBytesRx(Long streamBytesRx);

        public abstract Builder streamBytesTx(Long streamBytesTx);

        public abstract RTSPStreamEntry build();
    }
}
