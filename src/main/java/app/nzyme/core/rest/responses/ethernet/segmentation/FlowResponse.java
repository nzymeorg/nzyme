package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class FlowResponse {

    @JsonProperty("source")
    public abstract FlowSideResponse source();

    @JsonProperty("destination")
    public abstract FlowSideResponse destination();

    @JsonProperty("l4_type")
    public abstract String l4Type();

    @JsonProperty("port")
    public abstract int port();

    @JsonProperty("protocol")
    public abstract String protocol();

    @Schema(description = "CONNECTED, REJECTED or NO_REPLY, or SENT for what went to a broadcast or multicast address.")
    @JsonProperty("outcome")
    public abstract String outcome();

    @JsonProperty("sessions")
    public abstract long sessions();

    @JsonProperty("continued")
    public abstract long continued();

    @JsonProperty("tap_uuid")
    public abstract UUID tapUuid();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static FlowResponse create(FlowSideResponse source,
                                      FlowSideResponse destination,
                                      String l4Type,
                                      int port,
                                      String protocol,
                                      String outcome,
                                      long sessions,
                                      long continued,
                                      UUID tapUuid,
                                      DateTime firstSeen,
                                      DateTime lastSeen) {
        return builder()
                .source(source)
                .destination(destination)
                .l4Type(l4Type)
                .port(port)
                .protocol(protocol)
                .outcome(outcome)
                .sessions(sessions)
                .continued(continued)
                .tapUuid(tapUuid)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FlowResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder source(FlowSideResponse source);
        public abstract Builder destination(FlowSideResponse destination);
        public abstract Builder l4Type(String l4Type);
        public abstract Builder port(int port);
        public abstract Builder protocol(String protocol);
        public abstract Builder outcome(String outcome);
        public abstract Builder sessions(long sessions);
        public abstract Builder continued(long continued);
        public abstract Builder tapUuid(UUID tapUuid);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract FlowResponse build();
    }

}
