package app.nzyme.core.rest.responses.ethernet.icmp;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class IcmpTracerouteResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

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
    @JsonProperty("protocol")
    public abstract Integer protocol();

    @Nullable
    @JsonProperty("protocol_name")
    public abstract String protocolName();

    @JsonProperty("routers")
    public abstract List<String> routers();

    @JsonProperty("packet_count")
    public abstract long packetCount();

    @JsonProperty("destination_reached")
    public abstract boolean destinationReached();

    @JsonProperty("tap_uuids")
    public abstract List<UUID> tapUuids();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static IcmpTracerouteResponse create(UUID uuid, String sourceAddress, L4AddressResponse sourceAddressData, EthernetMacAddressResponse sourceMac, String destinationAddress, L4AddressResponse destinationAddressData, Integer protocol, String protocolName, List<String> routers, long packetCount, boolean destinationReached, List<UUID> tapUuids, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .uuid(uuid)
                .sourceAddress(sourceAddress)
                .sourceAddressData(sourceAddressData)
                .sourceMac(sourceMac)
                .destinationAddress(destinationAddress)
                .destinationAddressData(destinationAddressData)
                .protocol(protocol)
                .protocolName(protocolName)
                .routers(routers)
                .packetCount(packetCount)
                .destinationReached(destinationReached)
                .tapUuids(tapUuids)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpTracerouteResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder sourceAddress(String sourceAddress);
        public abstract Builder sourceAddressData(L4AddressResponse sourceAddressData);
        public abstract Builder sourceMac(EthernetMacAddressResponse sourceMac);
        public abstract Builder destinationAddress(String destinationAddress);
        public abstract Builder destinationAddressData(L4AddressResponse destinationAddressData);
        public abstract Builder protocol(Integer protocol);
        public abstract Builder protocolName(String protocolName);
        public abstract Builder routers(List<String> routers);
        public abstract Builder packetCount(long packetCount);
        public abstract Builder destinationReached(boolean destinationReached);
        public abstract Builder tapUuids(List<UUID> tapUuids);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract IcmpTracerouteResponse build();
    }
}
