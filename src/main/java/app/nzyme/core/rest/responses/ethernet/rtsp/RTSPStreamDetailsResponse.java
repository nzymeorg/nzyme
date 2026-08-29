package app.nzyme.core.rest.responses.ethernet.rtsp;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@AutoValue
public abstract class RTSPStreamDetailsResponse {

    @JsonProperty("setup_tcp_session_key")
    public abstract String setupTcpSessionKey();
    @JsonProperty("is_active") @Nullable
    public abstract Boolean isActive();
    @JsonProperty("state")
    public abstract String state();
    @JsonProperty("media_locator") @Nullable
    public abstract Map<String, Object> mediaLocator();
    @JsonProperty("request_uri") @Nullable
    public abstract String requestUri();
    @JsonProperty("client_agent") @Nullable
    public abstract String clientAgent();
    @JsonProperty("server_info") @Nullable
    public abstract String serverInfo();
    @JsonProperty("authentication")
    public abstract String authentication();
    @JsonProperty("flags")
    public abstract Set<String> flags();
    @JsonProperty("last_activity") @Nullable
    public abstract DateTime lastActivity();
    @JsonProperty("duration_ms") @Nullable
    public abstract Long durationMs();

    @JsonProperty("setup_connection_status")
    public abstract String setupConnectionStatus();
    @JsonProperty("setup_established_at")
    public abstract DateTime setupEstablishedAt();
    @JsonProperty("setup_terminated_at") @Nullable
    public abstract DateTime setupTerminatedAt();
    @JsonProperty("setup_most_recent_segment_time")
    public abstract DateTime setupMostRecentSegmentTime();
    @JsonProperty("setup_source") @Nullable
    public abstract L4AddressResponse setupSource();
    @JsonProperty("setup_destination") @Nullable
    public abstract L4AddressResponse setupDestination();
    @JsonProperty("setup_bytes_exchanged")
    public abstract Long setupBytesExchanged();

    @Nullable @JsonProperty("stream_l4_type")
    public abstract String streamL4Type();
    @Nullable @JsonProperty("stream_source")
    public abstract L4AddressResponse streamSource();
    @Nullable @JsonProperty("stream_destination")
    public abstract L4AddressResponse streamDestination();
    @Nullable @JsonProperty("stream_bytes_rx")
    public abstract Long streamBytesRx();
    @Nullable @JsonProperty("stream_bytes_tx")
    public abstract Long streamBytesTx();

    public static RTSPStreamDetailsResponse create(String setupTcpSessionKey, Boolean isActive, String state, Map<String, Object> mediaLocator, String requestUri, String clientAgent, String serverInfo, String authentication, Set<String> flags, DateTime lastActivity, Long durationMs, String setupConnectionStatus, DateTime setupEstablishedAt, DateTime setupTerminatedAt, DateTime setupMostRecentSegmentTime, L4AddressResponse setupSource, L4AddressResponse setupDestination, Long setupBytesExchanged, String streamL4Type, L4AddressResponse streamSource, L4AddressResponse streamDestination, Long streamBytesRx, Long streamBytesTx) {
        return builder()
                .setupTcpSessionKey(setupTcpSessionKey)
                .isActive(isActive)
                .state(state)
                .mediaLocator(mediaLocator)
                .requestUri(requestUri)
                .clientAgent(clientAgent)
                .serverInfo(serverInfo)
                .authentication(authentication)
                .flags(flags)
                .lastActivity(lastActivity)
                .durationMs(durationMs)
                .setupConnectionStatus(setupConnectionStatus)
                .setupEstablishedAt(setupEstablishedAt)
                .setupTerminatedAt(setupTerminatedAt)
                .setupMostRecentSegmentTime(setupMostRecentSegmentTime)
                .setupSource(setupSource)
                .setupDestination(setupDestination)
                .setupBytesExchanged(setupBytesExchanged)
                .streamL4Type(streamL4Type)
                .streamSource(streamSource)
                .streamDestination(streamDestination)
                .streamBytesRx(streamBytesRx)
                .streamBytesTx(streamBytesTx)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_RTSPStreamDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setupTcpSessionKey(String setupTcpSessionKey);

        public abstract Builder isActive(Boolean isActive);

        public abstract Builder state(String state);

        public abstract Builder mediaLocator(Map<String, Object> mediaLocator);

        public abstract Builder requestUri(String requestUri);

        public abstract Builder clientAgent(String clientAgent);

        public abstract Builder serverInfo(String serverInfo);

        public abstract Builder authentication(String authentication);

        public abstract Builder flags(Set<String> flags);

        public abstract Builder lastActivity(DateTime lastActivity);

        public abstract Builder durationMs(Long durationMs);

        public abstract Builder setupConnectionStatus(String setupConnectionStatus);

        public abstract Builder setupEstablishedAt(DateTime setupEstablishedAt);

        public abstract Builder setupTerminatedAt(DateTime setupTerminatedAt);

        public abstract Builder setupMostRecentSegmentTime(DateTime setupMostRecentSegmentTime);

        public abstract Builder setupSource(L4AddressResponse setupSource);

        public abstract Builder setupDestination(L4AddressResponse setupDestination);

        public abstract Builder setupBytesExchanged(Long setupBytesExchanged);

        public abstract Builder streamL4Type(String streamL4Type);

        public abstract Builder streamSource(L4AddressResponse streamSource);

        public abstract Builder streamDestination(L4AddressResponse streamDestination);

        public abstract Builder streamBytesRx(Long streamBytesRx);

        public abstract Builder streamBytesTx(Long streamBytesTx);

        public abstract RTSPStreamDetailsResponse build();
    }
}
