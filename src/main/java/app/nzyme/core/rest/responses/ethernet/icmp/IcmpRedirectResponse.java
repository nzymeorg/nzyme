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
public abstract class IcmpRedirectResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("sender_address")
    public abstract String senderAddress();

    @Nullable
    @JsonProperty("sender_address_data")
    public abstract L4AddressResponse senderAddressData();

    @Nullable
    @JsonProperty("sender_mac")
    public abstract EthernetMacAddressResponse senderMac();

    @JsonProperty("gateway_address")
    public abstract String gatewayAddress();

    @Nullable
    @JsonProperty("gateway_address_data")
    public abstract L4AddressResponse gatewayAddressData();

    @JsonProperty("target_address")
    public abstract String targetAddress();

    @Nullable
    @JsonProperty("target_address_data")
    public abstract L4AddressResponse targetAddressData();

    @Nullable
    @JsonProperty("target_mac")
    public abstract EthernetMacAddressResponse targetMac();

    @JsonProperty("redirected_destinations")
    public abstract List<L4AddressResponse> redirectedDestinations();

    @JsonProperty("packet_count")
    public abstract long packetCount();

    @JsonProperty("tap_uuids")
    public abstract List<UUID> tapUuids();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static IcmpRedirectResponse create(UUID uuid, String senderAddress, L4AddressResponse senderAddressData, EthernetMacAddressResponse senderMac, String gatewayAddress, L4AddressResponse gatewayAddressData, String targetAddress, L4AddressResponse targetAddressData, EthernetMacAddressResponse targetMac, List<L4AddressResponse> redirectedDestinations, long packetCount, List<UUID> tapUuids, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .uuid(uuid)
                .senderAddress(senderAddress)
                .senderAddressData(senderAddressData)
                .senderMac(senderMac)
                .gatewayAddress(gatewayAddress)
                .gatewayAddressData(gatewayAddressData)
                .targetAddress(targetAddress)
                .targetAddressData(targetAddressData)
                .targetMac(targetMac)
                .redirectedDestinations(redirectedDestinations)
                .packetCount(packetCount)
                .tapUuids(tapUuids)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpRedirectResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder senderAddress(String senderAddress);
        public abstract Builder senderAddressData(L4AddressResponse senderAddressData);
        public abstract Builder senderMac(EthernetMacAddressResponse senderMac);
        public abstract Builder gatewayAddress(String gatewayAddress);
        public abstract Builder gatewayAddressData(L4AddressResponse gatewayAddressData);
        public abstract Builder targetAddress(String targetAddress);
        public abstract Builder targetAddressData(L4AddressResponse targetAddressData);
        public abstract Builder targetMac(EthernetMacAddressResponse targetMac);
        public abstract Builder redirectedDestinations(List<L4AddressResponse> redirectedDestinations);
        public abstract Builder packetCount(long packetCount);
        public abstract Builder tapUuids(List<UUID> tapUuids);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract IcmpRedirectResponse build();
    }
}
