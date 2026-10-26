package app.nzyme.core.rest.responses.ethernet.spoofing;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class DhcpSpoofingVictimResponse {

    @JsonProperty("mac")
    public abstract String mac();

    @Nullable
    @JsonProperty("mac_data")
    public abstract EthernetMacAddressResponse macData();

    @Nullable
    @JsonProperty("assigned_address")
    public abstract String assignedAddress();

    @Nullable
    @JsonProperty("assigned_address_data")
    public abstract L4AddressResponse assignedAddressData();

    @JsonProperty("transaction_count")
    public abstract long transactionCount();

    @JsonProperty("lease_count")
    public abstract long leaseCount();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static DhcpSpoofingVictimResponse create(String mac, EthernetMacAddressResponse macData, String assignedAddress, L4AddressResponse assignedAddressData, long transactionCount, long leaseCount, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .mac(mac)
                .macData(macData)
                .assignedAddress(assignedAddress)
                .assignedAddressData(assignedAddressData)
                .transactionCount(transactionCount)
                .leaseCount(leaseCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DhcpSpoofingVictimResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder mac(String mac);
        public abstract Builder macData(EthernetMacAddressResponse macData);
        public abstract Builder assignedAddress(String assignedAddress);
        public abstract Builder assignedAddressData(L4AddressResponse assignedAddressData);
        public abstract Builder transactionCount(long transactionCount);
        public abstract Builder leaseCount(long leaseCount);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract DhcpSpoofingVictimResponse build();
    }
}
