package app.nzyme.core.rest.responses.ethernet.icmp;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class IcmpUnreachableResponse {

    @JsonProperty("host_address")
    public abstract String hostAddress();

    @Nullable
    @JsonProperty("host_address_data")
    public abstract L4AddressResponse hostAddressData();

    @Nullable
    @JsonProperty("host_mac")
    public abstract EthernetMacAddressResponse hostMac();

    @JsonProperty("source_address")
    public abstract String sourceAddress();

    @Nullable
    @JsonProperty("source_address_data")
    public abstract L4AddressResponse sourceAddressData();

    @Nullable
    @JsonProperty("source_mac")
    public abstract EthernetMacAddressResponse sourceMac();

    @Nullable
    @JsonProperty("protocol")
    public abstract Integer protocol();

    @Nullable
    @JsonProperty("protocol_name")
    public abstract String protocolName();

    @JsonProperty("port")
    public abstract int port();

    @Nullable
    @JsonProperty("service")
    public abstract String service();

    @JsonProperty("packet_count")
    public abstract long packetCount();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static IcmpUnreachableResponse create(String hostAddress, L4AddressResponse hostAddressData, EthernetMacAddressResponse hostMac, String sourceAddress, L4AddressResponse sourceAddressData, EthernetMacAddressResponse sourceMac, Integer protocol, String protocolName, int port, String service, long packetCount, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .hostAddress(hostAddress)
                .hostAddressData(hostAddressData)
                .hostMac(hostMac)
                .sourceAddress(sourceAddress)
                .sourceAddressData(sourceAddressData)
                .sourceMac(sourceMac)
                .protocol(protocol)
                .protocolName(protocolName)
                .port(port)
                .service(service)
                .packetCount(packetCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpUnreachableResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder hostAddress(String hostAddress);
        public abstract Builder hostAddressData(L4AddressResponse hostAddressData);
        public abstract Builder hostMac(EthernetMacAddressResponse hostMac);
        public abstract Builder sourceAddress(String sourceAddress);
        public abstract Builder sourceAddressData(L4AddressResponse sourceAddressData);
        public abstract Builder sourceMac(EthernetMacAddressResponse sourceMac);
        public abstract Builder protocol(Integer protocol);
        public abstract Builder protocolName(String protocolName);
        public abstract Builder port(int port);
        public abstract Builder service(String service);
        public abstract Builder packetCount(long packetCount);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract IcmpUnreachableResponse build();
    }
}
