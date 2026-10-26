package app.nzyme.core.rest.responses.ethernet.dhcp;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class DHCPServerResponseResponse {

    @JsonProperty("message_type")
    public abstract String messageType();

    @Nullable
    @JsonProperty("server_mac")
    public abstract String serverMac();

    @Nullable
    @JsonProperty("server_mac_data")
    public abstract EthernetMacAddressResponse serverMacData();

    @JsonProperty("server_address")
    public abstract String serverAddress();

    @Nullable
    @JsonProperty("server_identifier")
    public abstract String serverIdentifier();

    @Nullable
    @JsonProperty("offered_address")
    public abstract String offeredAddress();

    @Nullable
    @JsonProperty("subnet_mask")
    public abstract String subnetMask();

    @JsonProperty("routers")
    public abstract List<String> routers();

    @JsonProperty("dns_servers")
    public abstract List<String> dnsServers();

    @Nullable
    @JsonProperty("domain_name")
    public abstract String domainName();

    @Nullable
    @JsonProperty("lease_time_seconds")
    public abstract Long leaseTimeSeconds();

    @JsonProperty("count")
    public abstract long count();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static DHCPServerResponseResponse create(String messageType, String serverMac, EthernetMacAddressResponse serverMacData, String serverAddress, String serverIdentifier, String offeredAddress, String subnetMask, List<String> routers, List<String> dnsServers, String domainName, Long leaseTimeSeconds, long count, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .messageType(messageType)
                .serverMac(serverMac)
                .serverMacData(serverMacData)
                .serverAddress(serverAddress)
                .serverIdentifier(serverIdentifier)
                .offeredAddress(offeredAddress)
                .subnetMask(subnetMask)
                .routers(routers)
                .dnsServers(dnsServers)
                .domainName(domainName)
                .leaseTimeSeconds(leaseTimeSeconds)
                .count(count)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DHCPServerResponseResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder messageType(String messageType);
        public abstract Builder serverMac(String serverMac);
        public abstract Builder serverMacData(EthernetMacAddressResponse serverMacData);
        public abstract Builder serverAddress(String serverAddress);
        public abstract Builder serverIdentifier(String serverIdentifier);
        public abstract Builder offeredAddress(String offeredAddress);
        public abstract Builder subnetMask(String subnetMask);
        public abstract Builder routers(List<String> routers);
        public abstract Builder dnsServers(List<String> dnsServers);
        public abstract Builder domainName(String domainName);
        public abstract Builder leaseTimeSeconds(Long leaseTimeSeconds);
        public abstract Builder count(long count);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract DHCPServerResponseResponse build();
    }
}
