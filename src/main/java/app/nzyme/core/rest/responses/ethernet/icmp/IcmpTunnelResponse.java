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
public abstract class IcmpTunnelResponse {

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
    @JsonProperty("destination_mac")
    public abstract EthernetMacAddressResponse destinationMac();

    @JsonProperty("max_payload_bytes")
    public abstract long maxPayloadBytes();

    @JsonProperty("packet_count")
    public abstract long packetCount();

    @JsonProperty("tap_uuids")
    public abstract List<UUID> tapUuids();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static IcmpTunnelResponse create(UUID uuid, String sourceAddress, L4AddressResponse sourceAddressData, EthernetMacAddressResponse sourceMac, String destinationAddress, L4AddressResponse destinationAddressData, EthernetMacAddressResponse destinationMac, long maxPayloadBytes, long packetCount, List<UUID> tapUuids, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .uuid(uuid)
                .sourceAddress(sourceAddress)
                .sourceAddressData(sourceAddressData)
                .sourceMac(sourceMac)
                .destinationAddress(destinationAddress)
                .destinationAddressData(destinationAddressData)
                .destinationMac(destinationMac)
                .maxPayloadBytes(maxPayloadBytes)
                .packetCount(packetCount)
                .tapUuids(tapUuids)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpTunnelResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder sourceAddress(String sourceAddress);
        public abstract Builder sourceAddressData(L4AddressResponse sourceAddressData);
        public abstract Builder sourceMac(EthernetMacAddressResponse sourceMac);
        public abstract Builder destinationAddress(String destinationAddress);
        public abstract Builder destinationAddressData(L4AddressResponse destinationAddressData);
        public abstract Builder destinationMac(EthernetMacAddressResponse destinationMac);
        public abstract Builder maxPayloadBytes(long maxPayloadBytes);
        public abstract Builder packetCount(long packetCount);
        public abstract Builder tapUuids(List<UUID> tapUuids);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract IcmpTunnelResponse build();
    }
}
