package app.nzyme.core.rest.resources.taps.reports.tables.dhcp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class Dhcpv4ServerResponseReport {

    public abstract String messageType();
    @Nullable
    public abstract String serverMac();
    public abstract String serverAddress();
    @Nullable
    public abstract String serverIdentifier();
    @Nullable
    public abstract String offeredAddress();
    @Nullable
    public abstract String subnetMask();
    public abstract List<String> routers();
    public abstract List<String> dnsServers();
    @Nullable
    public abstract String domainName();
    @Nullable
    public abstract Long leaseTimeSeconds();
    public abstract long count();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();

    @JsonCreator
    public static Dhcpv4ServerResponseReport create(@JsonProperty("message_type") String messageType,
                                                    @JsonProperty("server_mac") String serverMac,
                                                    @JsonProperty("server_address") String serverAddress,
                                                    @JsonProperty("server_identifier") String serverIdentifier,
                                                    @JsonProperty("offered_address") String offeredAddress,
                                                    @JsonProperty("subnet_mask") String subnetMask,
                                                    @JsonProperty("routers") List<String> routers,
                                                    @JsonProperty("dns_servers") List<String> dnsServers,
                                                    @JsonProperty("domain_name") String domainName,
                                                    @JsonProperty("lease_time_seconds") Long leaseTimeSeconds,
                                                    @JsonProperty("count") long count,
                                                    @JsonProperty("first_seen") DateTime firstSeen,
                                                    @JsonProperty("last_seen") DateTime lastSeen) {
        return builder()
                .messageType(messageType)
                .serverMac(serverMac)
                .serverAddress(serverAddress)
                .serverIdentifier(serverIdentifier)
                .offeredAddress(offeredAddress)
                .subnetMask(subnetMask)
                .routers(routers == null ? List.of() : routers)
                .dnsServers(dnsServers == null ? List.of() : dnsServers)
                .domainName(domainName)
                .leaseTimeSeconds(leaseTimeSeconds)
                .count(count)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dhcpv4ServerResponseReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder messageType(String messageType);
        public abstract Builder serverMac(String serverMac);
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
        public abstract Dhcpv4ServerResponseReport build();
    }
}
