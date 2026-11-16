package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.joda.time.DateTime;

@AutoValue
public abstract class PolicyPreviewRowResponse {

    @JsonProperty("source")
    public abstract PolicyPreviewSideResponse source();

    @JsonProperty("destination")
    public abstract PolicyPreviewSideResponse destination();

    @JsonProperty("l4_type")
    public abstract String l4Type();

    @JsonProperty("port")
    public abstract int port();

    @JsonProperty("protocol")
    public abstract String protocol();

    @Schema(description = "ALLOWED, VIOLATION or INDETERMINATE.")
    @JsonProperty("verdict")
    public abstract String verdict();

    @JsonProperty("connected")
    public abstract long connected();

    @JsonProperty("rejected")
    public abstract long rejected();

    @JsonProperty("no_reply")
    public abstract long noReply();

    @Schema(description = "Sessions that were sent to a broadcast or multicast address, where nothing is expected back.")
    @JsonProperty("sent")
    public abstract long sent();

    @JsonProperty("source_hosts")
    public abstract long sourceHosts();

    @JsonProperty("destination_hosts")
    public abstract long destinationHosts();

    @JsonProperty("source_sample")
    public abstract List<String> sourceSample();

    @JsonProperty("destination_sample")
    public abstract List<String> destinationSample();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static PolicyPreviewRowResponse create(PolicyPreviewSideResponse source,
                                                  PolicyPreviewSideResponse destination,
                                                  String l4Type,
                                                  int port,
                                                  String protocol,
                                                  String verdict,
                                                  long connected,
                                                  long rejected,
                                                  long noReply,
                                                  long sent,
                                                  long sourceHosts,
                                                  long destinationHosts,
                                                  List<String> sourceSample,
                                                  List<String> destinationSample,
                                                  DateTime firstSeen,
                                                  DateTime lastSeen) {
        return builder()
                .source(source)
                .destination(destination)
                .l4Type(l4Type)
                .port(port)
                .protocol(protocol)
                .verdict(verdict)
                .connected(connected)
                .rejected(rejected)
                .noReply(noReply)
                .sent(sent)
                .sourceHosts(sourceHosts)
                .destinationHosts(destinationHosts)
                .sourceSample(sourceSample)
                .destinationSample(destinationSample)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PolicyPreviewRowResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder source(PolicyPreviewSideResponse source);
        public abstract Builder destination(PolicyPreviewSideResponse destination);
        public abstract Builder l4Type(String l4Type);
        public abstract Builder port(int port);
        public abstract Builder protocol(String protocol);
        public abstract Builder verdict(String verdict);
        public abstract Builder connected(long connected);
        public abstract Builder rejected(long rejected);
        public abstract Builder noReply(long noReply);
        public abstract Builder sent(long sent);
        public abstract Builder sourceHosts(long sourceHosts);
        public abstract Builder destinationHosts(long destinationHosts);
        public abstract Builder sourceSample(List<String> sourceSample);
        public abstract Builder destinationSample(List<String> destinationSample);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract PolicyPreviewRowResponse build();
    }

}
