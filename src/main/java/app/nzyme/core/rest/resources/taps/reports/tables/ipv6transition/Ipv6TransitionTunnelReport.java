package app.nzyme.core.rest.resources.taps.reports.tables.ipv6transition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.Map;

@AutoValue
public abstract class Ipv6TransitionTunnelReport {

    public abstract String tunnelType();

    public abstract String transport();

    @Nullable
    public abstract Map<String, String> details();

    public abstract String connectionStatus();

    public abstract long tunneledBytes();

    @Nullable
    public abstract String sourceMac();

    @Nullable
    public abstract String destinationMac();

    public abstract String sourceAddress();

    @Nullable
    public abstract Integer sourcePort();

    public abstract String destinationAddress();

    @Nullable
    public abstract Integer destinationPort();

    public abstract DateTime establishedAt();

    @Nullable
    public abstract DateTime terminatedAt();

    public abstract DateTime mostRecentSegmentTime();

    @JsonCreator
    public static Ipv6TransitionTunnelReport create(@JsonProperty("tunnel_type") String tunnelType,
                                                    @JsonProperty("transport") String transport,
                                                    @JsonProperty("details") Map<String, String> details,
                                                    @JsonProperty("connection_status") String connectionStatus,
                                                    @JsonProperty("tunneled_bytes") long tunneledBytes,
                                                    @JsonProperty("source_mac") String sourceMac,
                                                    @JsonProperty("destination_mac") String destinationMac,
                                                    @JsonProperty("source_address") String sourceAddress,
                                                    @JsonProperty("source_port") Integer sourcePort,
                                                    @JsonProperty("destination_address") String destinationAddress,
                                                    @JsonProperty("destination_port") Integer destinationPort,
                                                    @JsonProperty("established_at") DateTime establishedAt,
                                                    @JsonProperty("terminated_at") DateTime terminatedAt,
                                                    @JsonProperty("most_recent_segment_time") DateTime mostRecentSegmentTime) {
        return builder()
                .tunnelType(tunnelType)
                .transport(transport)
                .details(details)
                .connectionStatus(connectionStatus)
                .tunneledBytes(tunneledBytes)
                .sourceMac(sourceMac)
                .destinationMac(destinationMac)
                .sourceAddress(sourceAddress)
                .sourcePort(sourcePort)
                .destinationAddress(destinationAddress)
                .destinationPort(destinationPort)
                .establishedAt(establishedAt)
                .terminatedAt(terminatedAt)
                .mostRecentSegmentTime(mostRecentSegmentTime)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Ipv6TransitionTunnelReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder tunnelType(String tunnelType);

        public abstract Builder transport(String transport);

        public abstract Builder details(Map<String, String> details);

        public abstract Builder connectionStatus(String connectionStatus);

        public abstract Builder tunneledBytes(long tunneledBytes);

        public abstract Builder sourceMac(String sourceMac);

        public abstract Builder destinationMac(String destinationMac);

        public abstract Builder sourceAddress(String sourceAddress);

        public abstract Builder sourcePort(Integer sourcePort);

        public abstract Builder destinationAddress(String destinationAddress);

        public abstract Builder destinationPort(Integer destinationPort);

        public abstract Builder establishedAt(DateTime establishedAt);

        public abstract Builder terminatedAt(DateTime terminatedAt);

        public abstract Builder mostRecentSegmentTime(DateTime mostRecentSegmentTime);

        public abstract Ipv6TransitionTunnelReport build();
    }

}
