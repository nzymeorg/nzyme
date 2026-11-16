package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

@AutoValue
public abstract class MatrixServiceResponse {

    @JsonProperty("l4_type")
    public abstract String l4Type();

    @Schema(description = "Destination port. -1 if the entry stands for many ports of a sweep.")
    @JsonProperty("port")
    public abstract int port();

    @Schema(description = "The protocols the taps detected, joined by a slash. Empty if none.")
    @JsonProperty("protocol")
    public abstract String protocol();

    @JsonProperty("connected")
    public abstract long connected();

    @JsonProperty("rejected")
    public abstract long rejected();

    @JsonProperty("no_reply")
    public abstract long noReply();

    @Schema(description = "Sessions that were sent to a broadcast or multicast address, where nothing is expected back.")
    @JsonProperty("sent")
    public abstract long sent();

    public static MatrixServiceResponse create(String l4Type,
                                               int port,
                                               String protocol,
                                               long connected,
                                               long rejected,
                                               long noReply,
                                               long sent) {
        return builder()
                .l4Type(l4Type)
                .port(port)
                .protocol(protocol)
                .connected(connected)
                .rejected(rejected)
                .noReply(noReply)
                .sent(sent)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_MatrixServiceResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder l4Type(String l4Type);
        public abstract Builder port(int port);
        public abstract Builder protocol(String protocol);
        public abstract Builder connected(long connected);
        public abstract Builder rejected(long rejected);
        public abstract Builder noReply(long noReply);
        public abstract Builder sent(long sent);

        public abstract MatrixServiceResponse build();
    }

}
