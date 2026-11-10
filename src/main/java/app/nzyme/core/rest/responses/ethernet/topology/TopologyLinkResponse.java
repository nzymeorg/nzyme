package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;
import org.joda.time.DateTime;

@AutoValue
public abstract class TopologyLinkResponse {

    @Schema(description = "ID of the node that initiated the sessions.")
    @JsonProperty("source")
    public abstract String source();

    @Schema(description = "ID of the node that accepted the sessions.")
    @JsonProperty("target")
    public abstract String target();

    @Schema(description = "ID of the service, if links were requested by service.")
    @Nullable
    @JsonProperty("service")
    public abstract String service();

    @JsonProperty("sessions")
    public abstract long sessions();

    @JsonProperty("sessions_tcp")
    public abstract long sessionsTcp();

    @JsonProperty("sessions_udp")
    public abstract long sessionsUdp();

    @Schema(description = "Bytes the source sent.")
    @JsonProperty("bytes_tx")
    public abstract long bytesTx();

    @Schema(description = "Bytes the source received.")
    @JsonProperty("bytes_rx")
    public abstract long bytesRx();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    @Schema(description = "The link over time, one entry per bucket with traffic: bucket index, sessions, bytes sent, bytes received. A session is placed in the bucket of its most recent activity.")
    @JsonProperty("series")
    public abstract List<long[]> series();

    public static TopologyLinkResponse create(String source, String target, @Nullable String service, long sessions,
                                              long sessionsTcp, long sessionsUdp, long bytesTx, long bytesRx,
                                              DateTime firstSeen, DateTime lastSeen, List<long[]> series) {
        return builder()
                .source(source)
                .target(target)
                .service(service)
                .sessions(sessions)
                .sessionsTcp(sessionsTcp)
                .sessionsUdp(sessionsUdp)
                .bytesTx(bytesTx)
                .bytesRx(bytesRx)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .series(series)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologyLinkResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder source(String source);
        public abstract Builder target(String target);
        public abstract Builder service(String service);
        public abstract Builder sessions(long sessions);
        public abstract Builder sessionsTcp(long sessionsTcp);
        public abstract Builder sessionsUdp(long sessionsUdp);
        public abstract Builder bytesTx(long bytesTx);
        public abstract Builder bytesRx(long bytesRx);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder series(List<long[]> series);

        public abstract TopologyLinkResponse build();
    }

}
