package app.nzyme.core.rest.responses.ethernet.spoofing;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class NameResolutionSpoofingVictimResponse {

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

    @JsonProperty("names")
    public abstract List<String> names();

    @JsonProperty("response_count")
    public abstract long responseCount();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static NameResolutionSpoofingVictimResponse create(String mac, EthernetMacAddressResponse macData, String address, L4AddressResponse addressData, List<String> names, long responseCount, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .mac(mac)
                .macData(macData)
                .address(address)
                .addressData(addressData)
                .names(names)
                .responseCount(responseCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NameResolutionSpoofingVictimResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder mac(String mac);
        public abstract Builder macData(EthernetMacAddressResponse macData);
        public abstract Builder address(String address);
        public abstract Builder addressData(L4AddressResponse addressData);
        public abstract Builder names(List<String> names);
        public abstract Builder responseCount(long responseCount);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract NameResolutionSpoofingVictimResponse build();
    }
}
