package app.nzyme.core.rest.resources.taps.reports.tables.icmp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class IcmpErrorReport {

    @Nullable
    public abstract String sourceMac();
    @Nullable
    public abstract String destinationMac();
    public abstract String sourceAddress();
    public abstract String destinationAddress();
    public abstract int icmpType();
    public abstract int icmpCode();
    public abstract int ipTtl();
    @Nullable
    public abstract String redirectGateway();
    @Nullable
    public abstract Integer quotedProtocol();
    @Nullable
    public abstract String quotedSourceAddress();
    @Nullable
    public abstract String quotedDestinationAddress();
    @Nullable
    public abstract Integer quotedSourcePort();
    @Nullable
    public abstract Integer quotedDestinationPort();
    @Nullable
    public abstract Integer quotedTtl();
    public abstract DateTime timestamp();

    @JsonCreator
    public static IcmpErrorReport create(@JsonProperty("source_mac") String sourceMac,
                                         @JsonProperty("destination_mac") String destinationMac,
                                         @JsonProperty("source_address") String sourceAddress,
                                         @JsonProperty("destination_address") String destinationAddress,
                                         @JsonProperty("icmp_type") int icmpType,
                                         @JsonProperty("icmp_code") int icmpCode,
                                         @JsonProperty("ip_ttl") int ipTtl,
                                         @JsonProperty("redirect_gateway") String redirectGateway,
                                         @JsonProperty("quoted_protocol") Integer quotedProtocol,
                                         @JsonProperty("quoted_source_address") String quotedSourceAddress,
                                         @JsonProperty("quoted_destination_address") String quotedDestinationAddress,
                                         @JsonProperty("quoted_source_port") Integer quotedSourcePort,
                                         @JsonProperty("quoted_destination_port") Integer quotedDestinationPort,
                                         @JsonProperty("quoted_ttl") Integer quotedTtl,
                                         @JsonProperty("timestamp") DateTime timestamp) {
        return builder()
                .sourceMac(sourceMac)
                .destinationMac(destinationMac)
                .sourceAddress(sourceAddress)
                .destinationAddress(destinationAddress)
                .icmpType(icmpType)
                .icmpCode(icmpCode)
                .ipTtl(ipTtl)
                .redirectGateway(redirectGateway)
                .quotedProtocol(quotedProtocol)
                .quotedSourceAddress(quotedSourceAddress)
                .quotedDestinationAddress(quotedDestinationAddress)
                .quotedSourcePort(quotedSourcePort)
                .quotedDestinationPort(quotedDestinationPort)
                .quotedTtl(quotedTtl)
                .timestamp(timestamp)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpErrorReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sourceMac(String sourceMac);
        public abstract Builder destinationMac(String destinationMac);
        public abstract Builder sourceAddress(String sourceAddress);
        public abstract Builder destinationAddress(String destinationAddress);
        public abstract Builder icmpType(int icmpType);
        public abstract Builder icmpCode(int icmpCode);
        public abstract Builder ipTtl(int ipTtl);
        public abstract Builder redirectGateway(String redirectGateway);
        public abstract Builder quotedProtocol(Integer quotedProtocol);
        public abstract Builder quotedSourceAddress(String quotedSourceAddress);
        public abstract Builder quotedDestinationAddress(String quotedDestinationAddress);
        public abstract Builder quotedSourcePort(Integer quotedSourcePort);
        public abstract Builder quotedDestinationPort(Integer quotedDestinationPort);
        public abstract Builder quotedTtl(Integer quotedTtl);
        public abstract Builder timestamp(DateTime timestamp);
        public abstract IcmpErrorReport build();
    }
}
