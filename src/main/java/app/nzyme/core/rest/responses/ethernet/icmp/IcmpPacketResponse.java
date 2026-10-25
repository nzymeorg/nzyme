package app.nzyme.core.rest.responses.ethernet.icmp;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class IcmpPacketResponse {

    @JsonProperty("tap_uuid")
    public abstract UUID tapUuid();

    @JsonProperty("source_address")
    public abstract String sourceAddress();

    @Nullable
    @JsonProperty("source_address_data")
    public abstract L4AddressResponse sourceAddressData();

    @Nullable
    @JsonProperty("source_mac")
    public abstract EthernetMacAddressResponse sourceMac();

    @JsonProperty("destination_address")
    public abstract String destinationAddress();

    @Nullable
    @JsonProperty("destination_address_data")
    public abstract L4AddressResponse destinationAddressData();

    @Nullable
    @JsonProperty("destination_mac")
    public abstract EthernetMacAddressResponse destinationMac();

    @JsonProperty("icmp_type")
    public abstract int icmpType();

    @JsonProperty("icmp_type_name")
    public abstract String icmpTypeName();

    @JsonProperty("icmp_code")
    public abstract int icmpCode();

    @JsonProperty("is_error")
    public abstract boolean isError();

    @JsonProperty("packet_count")
    public abstract long packetCount();

    @JsonProperty("bytes_min")
    public abstract int bytesMin();

    @JsonProperty("bytes_max")
    public abstract int bytesMax();

    @JsonProperty("ip_ttl")
    public abstract int ipTtl();

    @JsonProperty("ip_tos")
    public abstract int ipTos();

    @JsonProperty("ip_df")
    public abstract boolean ipDf();

    @Nullable
    @JsonProperty("redirect_gateway")
    public abstract String redirectGateway();

    @Nullable
    @JsonProperty("quoted_protocol")
    public abstract Integer quotedProtocol();

    @Nullable
    @JsonProperty("quoted_source_address")
    public abstract String quotedSourceAddress();

    @Nullable
    @JsonProperty("quoted_destination_address")
    public abstract String quotedDestinationAddress();

    @Nullable
    @JsonProperty("quoted_source_port")
    public abstract Integer quotedSourcePort();

    @Nullable
    @JsonProperty("quoted_destination_port")
    public abstract Integer quotedDestinationPort();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static IcmpPacketResponse create(UUID tapUuid, String sourceAddress, L4AddressResponse sourceAddressData, EthernetMacAddressResponse sourceMac, String destinationAddress, L4AddressResponse destinationAddressData, EthernetMacAddressResponse destinationMac, int icmpType, String icmpTypeName, int icmpCode, boolean isError, long packetCount, int bytesMin, int bytesMax, int ipTtl, int ipTos, boolean ipDf, String redirectGateway, Integer quotedProtocol, String quotedSourceAddress, String quotedDestinationAddress, Integer quotedSourcePort, Integer quotedDestinationPort, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .tapUuid(tapUuid)
                .sourceAddress(sourceAddress)
                .sourceAddressData(sourceAddressData)
                .sourceMac(sourceMac)
                .destinationAddress(destinationAddress)
                .destinationAddressData(destinationAddressData)
                .destinationMac(destinationMac)
                .icmpType(icmpType)
                .icmpTypeName(icmpTypeName)
                .icmpCode(icmpCode)
                .isError(isError)
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
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpPacketResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder tapUuid(UUID tapUuid);
        public abstract Builder sourceAddress(String sourceAddress);
        public abstract Builder sourceAddressData(L4AddressResponse sourceAddressData);
        public abstract Builder sourceMac(EthernetMacAddressResponse sourceMac);
        public abstract Builder destinationAddress(String destinationAddress);
        public abstract Builder destinationAddressData(L4AddressResponse destinationAddressData);
        public abstract Builder destinationMac(EthernetMacAddressResponse destinationMac);
        public abstract Builder icmpType(int icmpType);
        public abstract Builder icmpTypeName(String icmpTypeName);
        public abstract Builder icmpCode(int icmpCode);
        public abstract Builder isError(boolean isError);
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
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract IcmpPacketResponse build();
    }
}
