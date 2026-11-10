package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class TopologyRankingRowResponse {

    @Schema(description = "Set for node rankings.")
    @Nullable
    @JsonProperty("node")
    public abstract TopologyNodeResponse node();

    @Schema(description = "Set for service rankings.")
    @Nullable
    @JsonProperty("service")
    public abstract TopologyServiceResponse service();

    @JsonProperty("sessions")
    public abstract long sessions();

    @Schema(description = "Sessions the node initiated. Node rankings only.")
    @Nullable
    @JsonProperty("sessions_initiated")
    public abstract Long sessionsInitiated();

    @Schema(description = "Bytes the node sent, or bytes clients sent to the service.")
    @JsonProperty("bytes_sent")
    public abstract long bytesSent();

    @Schema(description = "Bytes the node received, or bytes clients received from the service.")
    @JsonProperty("bytes_received")
    public abstract long bytesReceived();

    @Schema(description = "Distinct addresses the node exchanged traffic with. Node rankings only.")
    @Nullable
    @JsonProperty("peers")
    public abstract Long peers();

    @Schema(description = "Distinct source addresses. Service rankings only.")
    @Nullable
    @JsonProperty("clients")
    public abstract Long clients();

    @Schema(description = "Distinct destination addresses. Service rankings only.")
    @Nullable
    @JsonProperty("servers")
    public abstract Long servers();

    @Schema(description = "Start of the earliest session in the time range.")
    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static TopologyRankingRowResponse create(@Nullable TopologyNodeResponse node,
                                                    @Nullable TopologyServiceResponse service, long sessions,
                                                    @Nullable Long sessionsInitiated, long bytesSent,
                                                    long bytesReceived, @Nullable Long peers, @Nullable Long clients,
                                                    @Nullable Long servers, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .node(node)
                .service(service)
                .sessions(sessions)
                .sessionsInitiated(sessionsInitiated)
                .bytesSent(bytesSent)
                .bytesReceived(bytesReceived)
                .peers(peers)
                .clients(clients)
                .servers(servers)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologyRankingRowResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder node(TopologyNodeResponse node);
        public abstract Builder service(TopologyServiceResponse service);
        public abstract Builder sessions(long sessions);
        public abstract Builder sessionsInitiated(Long sessionsInitiated);
        public abstract Builder bytesSent(long bytesSent);
        public abstract Builder bytesReceived(long bytesReceived);
        public abstract Builder peers(Long peers);
        public abstract Builder clients(Long clients);
        public abstract Builder servers(Long servers);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract TopologyRankingRowResponse build();
    }

}
