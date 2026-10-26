package app.nzyme.core.rest.responses.ethernet.recon;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class ReconProbedHostSourceResponse {

    @JsonProperty("address")
    public abstract String address();

    @Nullable
    @JsonProperty("address_data")
    public abstract L4AddressResponse addressData();

    @Nullable
    @JsonProperty("mac")
    public abstract String mac();

    @Nullable
    @JsonProperty("mac_data")
    public abstract EthernetMacAddressResponse macData();

    @JsonProperty("scanner")
    public abstract boolean scanner();

    @JsonProperty("l4_types")
    public abstract List<String> l4Types();

    @JsonProperty("port_count")
    public abstract long portCount();

    @JsonProperty("failed_port_count")
    public abstract long failedPortCount();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static ReconProbedHostSourceResponse create(String address, L4AddressResponse addressData, String mac, EthernetMacAddressResponse macData, boolean scanner, List<String> l4Types, long portCount, long failedPortCount, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .address(address)
                .addressData(addressData)
                .mac(mac)
                .macData(macData)
                .scanner(scanner)
                .l4Types(l4Types)
                .portCount(portCount)
                .failedPortCount(failedPortCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ReconProbedHostSourceResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder address(String address);
        public abstract Builder addressData(L4AddressResponse addressData);
        public abstract Builder mac(String mac);
        public abstract Builder macData(EthernetMacAddressResponse macData);
        public abstract Builder scanner(boolean scanner);
        public abstract Builder l4Types(List<String> l4Types);
        public abstract Builder portCount(long portCount);
        public abstract Builder failedPortCount(long failedPortCount);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract ReconProbedHostSourceResponse build();
    }
}
