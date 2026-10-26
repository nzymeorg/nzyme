package app.nzyme.core.rest.responses.ethernet.spoofing;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class ArpSpoofingVictimResponse {

    @JsonProperty("mac")
    public abstract String mac();

    @Nullable
    @JsonProperty("mac_data")
    public abstract EthernetMacAddressResponse macData();

    @Nullable
    @JsonProperty("address")
    public abstract String address();

    @Nullable
    @JsonProperty("address_data")
    public abstract L4AddressResponse addressData();

    @JsonProperty("packet_count")
    public abstract long packetCount();

    @JsonProperty("unsolicited_count")
    public abstract long unsolicitedCount();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static ArpSpoofingVictimResponse create(String mac, EthernetMacAddressResponse macData, String address, L4AddressResponse addressData, long packetCount, long unsolicitedCount, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .mac(mac)
                .macData(macData)
                .address(address)
                .addressData(addressData)
                .packetCount(packetCount)
                .unsolicitedCount(unsolicitedCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ArpSpoofingVictimResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder mac(String mac);
        public abstract Builder macData(EthernetMacAddressResponse macData);
        public abstract Builder address(String address);
        public abstract Builder addressData(L4AddressResponse addressData);
        public abstract Builder packetCount(long packetCount);
        public abstract Builder unsolicitedCount(long unsolicitedCount);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract ArpSpoofingVictimResponse build();
    }
}
