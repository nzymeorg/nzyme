package app.nzyme.core.rest.responses.ethernet.spoofing;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class DnsSpoofingVictimResponse {

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

    @JsonProperty("forged_count")
    public abstract long forgedCount();

    @JsonProperty("won_count")
    public abstract long wonCount();

    @JsonProperty("unanswered_count")
    public abstract long unansweredCount();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static DnsSpoofingVictimResponse create(String mac, EthernetMacAddressResponse macData, String address, L4AddressResponse addressData, List<String> names, long forgedCount, long wonCount, long unansweredCount, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .mac(mac)
                .macData(macData)
                .address(address)
                .addressData(addressData)
                .names(names)
                .forgedCount(forgedCount)
                .wonCount(wonCount)
                .unansweredCount(unansweredCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsSpoofingVictimResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder mac(String mac);

        public abstract Builder macData(EthernetMacAddressResponse macData);

        public abstract Builder address(String address);

        public abstract Builder addressData(L4AddressResponse addressData);

        public abstract Builder names(List<String> names);

        public abstract Builder forgedCount(long forgedCount);

        public abstract Builder wonCount(long wonCount);

        public abstract Builder unansweredCount(long unansweredCount);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract DnsSpoofingVictimResponse build();
    }
}
