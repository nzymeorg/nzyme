package app.nzyme.core.rest.resources.taps.reports.tables.icmp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class IcmpSummaryReport {

    @Nullable
    public abstract String sourceMac();
    @Nullable
    public abstract String destinationMac();
    public abstract String sourceAddress();
    public abstract String destinationAddress();
    public abstract int icmpType();
    public abstract int icmpCode();
    public abstract long packetCount();
    public abstract int bytesMin();
    public abstract int bytesMax();
    public abstract int ipTtl();
    public abstract int ipTos();
    public abstract boolean ipDf();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();

    @JsonCreator
    public static IcmpSummaryReport create(@JsonProperty("source_mac") String sourceMac,
                                           @JsonProperty("destination_mac") String destinationMac,
                                           @JsonProperty("source_address") String sourceAddress,
                                           @JsonProperty("destination_address") String destinationAddress,
                                           @JsonProperty("icmp_type") int icmpType,
                                           @JsonProperty("icmp_code") int icmpCode,
                                           @JsonProperty("packet_count") long packetCount,
                                           @JsonProperty("bytes_min") int bytesMin,
                                           @JsonProperty("bytes_max") int bytesMax,
                                           @JsonProperty("ip_ttl") int ipTtl,
                                           @JsonProperty("ip_tos") int ipTos,
                                           @JsonProperty("ip_df") boolean ipDf,
                                           @JsonProperty("first_seen") DateTime firstSeen,
                                           @JsonProperty("last_seen") DateTime lastSeen) {
        return builder()
                .sourceMac(sourceMac)
                .destinationMac(destinationMac)
                .sourceAddress(sourceAddress)
                .destinationAddress(destinationAddress)
                .icmpType(icmpType)
                .icmpCode(icmpCode)
                .packetCount(packetCount)
                .bytesMin(bytesMin)
                .bytesMax(bytesMax)
                .ipTtl(ipTtl)
                .ipTos(ipTos)
                .ipDf(ipDf)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpSummaryReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sourceMac(String sourceMac);
        public abstract Builder destinationMac(String destinationMac);
        public abstract Builder sourceAddress(String sourceAddress);
        public abstract Builder destinationAddress(String destinationAddress);
        public abstract Builder icmpType(int icmpType);
        public abstract Builder icmpCode(int icmpCode);
        public abstract Builder packetCount(long packetCount);
        public abstract Builder bytesMin(int bytesMin);
        public abstract Builder bytesMax(int bytesMax);
        public abstract Builder ipTtl(int ipTtl);
        public abstract Builder ipTos(int ipTos);
        public abstract Builder ipDf(boolean ipDf);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract IcmpSummaryReport build();
    }
}
