package app.nzyme.core.rest.resources.taps.reports.tables.rtsp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.Map;
import java.util.Set;

@AutoValue
public abstract class RtspSessionReport {

    public abstract String setupSourceAddress();
    public abstract int setupSourcePort();
    public abstract String setupDestinationAddress();
    public abstract int setupDestinationPort();
    public abstract String setupConnectionStatus();
    public abstract DateTime setupEstablishedAt();
    @Nullable
    public abstract DateTime setupTerminatedAt();
    public abstract DateTime setupMostRecentSegmentTime();
    public abstract String state();
    @Nullable
    public abstract Map<String, Object> mediaLocator();
    @Nullable
    public abstract String requestUri();
    @Nullable
    public abstract String clientAgent();
    @Nullable
    public abstract String serverInfo();
    public abstract String authentication();
    @Nullable
    public abstract RtspMediaDescriptionReport mediaDescription();
    public abstract Set<String> flags();

    @JsonCreator
    public static RtspSessionReport create(@JsonProperty("setup_source_address") String setupSourceAddress,
                                           @JsonProperty("setup_source_port") int setupSourcePort,
                                           @JsonProperty("setup_destination_address") String setupDestinationAddress,
                                           @JsonProperty("setup_destination_port") int setupDestinationPort,
                                           @JsonProperty("setup_connection_status") String setupConnectionStatus,
                                           @JsonProperty("setup_established_at") DateTime setupEstablishedAt,
                                           @JsonProperty("setup_terminated_at") DateTime setupTerminatedAt,
                                           @JsonProperty("setup_most_recent_segment_time") DateTime setupMostRecentSegmentTime,
                                           @JsonProperty("state") String state,
                                           @JsonProperty("media_locator") Map<String, Object> mediaLocator,
                                           @JsonProperty("request_uri") String requestUri,
                                           @JsonProperty("client_agent") String clientAgent,
                                           @JsonProperty("server_info") String serverInfo,
                                           @JsonProperty("authentication") String authentication,
                                           @JsonProperty("media_description") RtspMediaDescriptionReport mediaDescription,
                                           @JsonProperty("flags") Set<String> flags) {
        return builder()
                .setupSourceAddress(setupSourceAddress)
                .setupSourcePort(setupSourcePort)
                .setupDestinationAddress(setupDestinationAddress)
                .setupDestinationPort(setupDestinationPort)
                .setupConnectionStatus(setupConnectionStatus)
                .setupEstablishedAt(setupEstablishedAt)
                .setupTerminatedAt(setupTerminatedAt)
                .setupMostRecentSegmentTime(setupMostRecentSegmentTime)
                .state(state)
                .mediaLocator(mediaLocator)
                .requestUri(requestUri)
                .clientAgent(clientAgent)
                .serverInfo(serverInfo)
                .authentication(authentication)
                .mediaDescription(mediaDescription)
                .flags(flags)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_RtspSessionReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setupSourceAddress(String setupSourceAddress);

        public abstract Builder setupSourcePort(int setupSourcePort);

        public abstract Builder setupDestinationAddress(String setupDestinationAddress);

        public abstract Builder setupDestinationPort(int setupDestinationPort);

        public abstract Builder setupConnectionStatus(String setupConnectionStatus);

        public abstract Builder setupEstablishedAt(DateTime setupEstablishedAt);

        public abstract Builder setupTerminatedAt(DateTime setupTerminatedAt);

        public abstract Builder setupMostRecentSegmentTime(DateTime setupMostRecentSegmentTime);

        public abstract Builder state(String state);

        public abstract Builder mediaLocator(Map<String, Object> mediaLocator);

        public abstract Builder requestUri(String requestUri);

        public abstract Builder clientAgent(String clientAgent);

        public abstract Builder serverInfo(String serverInfo);

        public abstract Builder authentication(String authentication);

        public abstract Builder mediaDescription(RtspMediaDescriptionReport mediaDescription);

        public abstract Builder flags(Set<String> flags);

        public abstract RtspSessionReport build();
    }
}
