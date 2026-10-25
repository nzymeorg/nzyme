package app.nzyme.core.rest.resources.taps.reports.tables.tcp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

@AutoValue
public abstract class TcpProbeReplyReport {

    public abstract String sourceAddress();
    public abstract String destinationAddress();
    public abstract int destinationPort();
    public abstract long packetCount();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();

    @JsonCreator
    public static TcpProbeReplyReport create(@JsonProperty("source_address") String sourceAddress,
                                             @JsonProperty("destination_address") String destinationAddress,
                                             @JsonProperty("destination_port") int destinationPort,
                                             @JsonProperty("packet_count") long packetCount,
                                             @JsonProperty("first_seen") DateTime firstSeen,
                                             @JsonProperty("last_seen") DateTime lastSeen) {
        return builder()
                .sourceAddress(sourceAddress)
                .destinationAddress(destinationAddress)
                .destinationPort(destinationPort)
                .packetCount(packetCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TcpProbeReplyReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sourceAddress(String sourceAddress);
        public abstract Builder destinationAddress(String destinationAddress);
        public abstract Builder destinationPort(int destinationPort);
        public abstract Builder packetCount(long packetCount);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract TcpProbeReplyReport build();
    }
}
