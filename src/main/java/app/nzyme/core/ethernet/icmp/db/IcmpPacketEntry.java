package app.nzyme.core.ethernet.icmp.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class IcmpPacketEntry {

    public abstract long id();
    public abstract UUID tapUuid();
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
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();

    public static IcmpPacketEntry create(long id, UUID tapUuid, String sourceMac, String destinationMac, String sourceAddress, String destinationAddress, int icmpType, int icmpCode, long packetCount, int bytesMin, int bytesMax, int ipTtl, int ipTos, boolean ipDf, String redirectGateway, Integer quotedProtocol, String quotedSourceAddress, String quotedDestinationAddress, Integer quotedSourcePort, Integer quotedDestinationPort, Integer quotedTtl, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .id(id)
                .tapUuid(tapUuid)
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
                .redirectGateway(redirectGateway)
                .quotedProtocol(quotedProtocol)
                .quotedSourceAddress(quotedSourceAddress)
                .quotedDestinationAddress(quotedDestinationAddress)
                .quotedSourcePort(quotedSourcePort)
                .quotedDestinationPort(quotedDestinationPort)
                .quotedTtl(quotedTtl)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpPacketEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder tapUuid(UUID tapUuid);
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
        public abstract Builder redirectGateway(String redirectGateway);
        public abstract Builder quotedProtocol(Integer quotedProtocol);
        public abstract Builder quotedSourceAddress(String quotedSourceAddress);
        public abstract Builder quotedDestinationAddress(String quotedDestinationAddress);
        public abstract Builder quotedSourcePort(Integer quotedSourcePort);
        public abstract Builder quotedDestinationPort(Integer quotedDestinationPort);
        public abstract Builder quotedTtl(Integer quotedTtl);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract IcmpPacketEntry build();
    }
}
