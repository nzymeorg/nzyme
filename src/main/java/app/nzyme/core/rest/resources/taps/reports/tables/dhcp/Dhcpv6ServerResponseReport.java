package app.nzyme.core.rest.resources.taps.reports.tables.dhcp;

import app.nzyme.core.ethernet.dhcp.Dhcpv6Address;
import app.nzyme.core.ethernet.dhcp.Dhcpv6Prefix;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class Dhcpv6ServerResponseReport {

    public abstract String messageType();

    @Nullable
    public abstract String serverMac();

    public abstract String serverAddress();

    @Nullable
    public abstract String serverDuid();

    public abstract List<Dhcpv6Address> addresses();

    public abstract List<Dhcpv6Prefix> prefixes();

    public abstract List<String> dnsServers();

    public abstract List<String> domainSearchList();

    @Nullable
    public abstract Integer preference();

    @Nullable
    public abstract Integer statusCode();

    public abstract int count();

    public abstract DateTime firstSeen();

    public abstract DateTime lastSeen();

    @JsonCreator
    public static Dhcpv6ServerResponseReport create(@JsonProperty("message_type") String messageType,
                                                    @JsonProperty("server_mac") String serverMac,
                                                    @JsonProperty("server_address") String serverAddress,
                                                    @JsonProperty("server_duid") String serverDuid,
                                                    @JsonProperty("addresses") List<Dhcpv6Address> addresses,
                                                    @JsonProperty("prefixes") List<Dhcpv6Prefix> prefixes,
                                                    @JsonProperty("dns_servers") List<String> dnsServers,
                                                    @JsonProperty("domain_search_list") List<String> domainSearchList,
                                                    @JsonProperty("preference") Integer preference,
                                                    @JsonProperty("status_code") Integer statusCode,
                                                    @JsonProperty("count") int count,
                                                    @JsonProperty("first_seen") DateTime firstSeen,
                                                    @JsonProperty("last_seen") DateTime lastSeen) {
        return builder()
                .messageType(messageType)
                .serverMac(serverMac)
                .serverAddress(serverAddress)
                .serverDuid(serverDuid)
                .addresses(addresses)
                .prefixes(prefixes)
                .dnsServers(dnsServers)
                .domainSearchList(domainSearchList)
                .preference(preference)
                .statusCode(statusCode)
                .count(count)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dhcpv6ServerResponseReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder messageType(String messageType);

        public abstract Builder serverMac(String serverMac);

        public abstract Builder serverAddress(String serverAddress);

        public abstract Builder serverDuid(String serverDuid);

        public abstract Builder addresses(List<Dhcpv6Address> addresses);

        public abstract Builder prefixes(List<Dhcpv6Prefix> prefixes);

        public abstract Builder dnsServers(List<String> dnsServers);

        public abstract Builder domainSearchList(List<String> domainSearchList);

        public abstract Builder preference(Integer preference);

        public abstract Builder statusCode(Integer statusCode);

        public abstract Builder count(int count);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract Dhcpv6ServerResponseReport build();
    }

}
