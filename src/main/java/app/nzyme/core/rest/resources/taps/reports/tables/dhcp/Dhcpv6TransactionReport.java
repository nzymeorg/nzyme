package app.nzyme.core.rest.resources.taps.reports.tables.dhcp;

import app.nzyme.core.ethernet.dhcp.Dhcpv6Address;
import app.nzyme.core.ethernet.dhcp.Dhcpv6Prefix;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;

@AutoValue
public abstract class Dhcpv6TransactionReport {

    public abstract String transactionType();

    public abstract long transactionId();

    public abstract String clientMac();

    public abstract List<String> additionalClientMacs();

    @Nullable
    public abstract String serverMac();

    public abstract List<String> additionalServerMacs();

    @Nullable
    public abstract String clientDuid();

    @Nullable
    public abstract String serverDuid();

    public abstract List<String> offeredIpAddresses();

    @Nullable
    public abstract String requestedIpAddress();

    public abstract List<Dhcpv6Address> addresses();

    public abstract List<Dhcpv6Prefix> prefixes();

    public abstract Map<String, List<String>> timestamps();

    public abstract DateTime firstPacket();

    public abstract DateTime latestPacket();

    public abstract List<String> notes();

    public abstract List<Integer> options();

    public abstract List<Integer> optionRequestList();

    @Nullable
    public abstract String vendorClass();

    @Nullable
    public abstract String fqdn();

    public abstract List<Dhcpv6ServerResponseReport> serverResponses();

    @Nullable
    public abstract String selectedServerAddress();

    @Nullable
    public abstract Boolean successful();

    public abstract boolean complete();

    @JsonCreator
    public static Dhcpv6TransactionReport create(@JsonProperty("transaction_type") String transactionType,
                                                 @JsonProperty("transaction_id") long transactionId,
                                                 @JsonProperty("client_mac") String clientMac,
                                                 @JsonProperty("additional_client_macs") List<String> additionalClientMacs,
                                                 @JsonProperty("server_mac") String serverMac,
                                                 @JsonProperty("additional_server_macs") List<String> additionalServerMacs,
                                                 @JsonProperty("client_duid") String clientDuid,
                                                 @JsonProperty("server_duid") String serverDuid,
                                                 @JsonProperty("offered_ip_addresses") List<String> offeredIpAddresses,
                                                 @JsonProperty("requested_ip_address") String requestedIpAddress,
                                                 @JsonProperty("addresses") List<Dhcpv6Address> addresses,
                                                 @JsonProperty("prefixes") List<Dhcpv6Prefix> prefixes,
                                                 @JsonProperty("timestamps") Map<String, List<String>> timestamps,
                                                 @JsonProperty("first_packet") DateTime firstPacket,
                                                 @JsonProperty("latest_packet") DateTime latestPacket,
                                                 @JsonProperty("notes") List<String> notes,
                                                 @JsonProperty("options") List<Integer> options,
                                                 @JsonProperty("option_request_list") List<Integer> optionRequestList,
                                                 @JsonProperty("vendor_class") String vendorClass,
                                                 @JsonProperty("fqdn") String fqdn,
                                                 @JsonProperty("server_responses") List<Dhcpv6ServerResponseReport> serverResponses,
                                                 @JsonProperty("selected_server_address") String selectedServerAddress,
                                                 @JsonProperty("successful") Boolean successful,
                                                 @JsonProperty("complete") boolean complete) {
        return builder()
                .transactionType(transactionType)
                .transactionId(transactionId)
                .clientMac(clientMac)
                .additionalClientMacs(additionalClientMacs)
                .serverMac(serverMac)
                .additionalServerMacs(additionalServerMacs)
                .clientDuid(clientDuid)
                .serverDuid(serverDuid)
                .offeredIpAddresses(offeredIpAddresses)
                .requestedIpAddress(requestedIpAddress)
                .addresses(addresses)
                .prefixes(prefixes)
                .timestamps(timestamps)
                .firstPacket(firstPacket)
                .latestPacket(latestPacket)
                .notes(notes)
                .options(options)
                .optionRequestList(optionRequestList)
                .vendorClass(vendorClass)
                .fqdn(fqdn)
                .serverResponses(serverResponses)
                .selectedServerAddress(selectedServerAddress)
                .successful(successful)
                .complete(complete)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dhcpv6TransactionReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder transactionType(String transactionType);

        public abstract Builder transactionId(long transactionId);

        public abstract Builder clientMac(String clientMac);

        public abstract Builder additionalClientMacs(List<String> additionalClientMacs);

        public abstract Builder serverMac(String serverMac);

        public abstract Builder additionalServerMacs(List<String> additionalServerMacs);

        public abstract Builder clientDuid(String clientDuid);

        public abstract Builder serverDuid(String serverDuid);

        public abstract Builder offeredIpAddresses(List<String> offeredIpAddresses);

        public abstract Builder requestedIpAddress(String requestedIpAddress);

        public abstract Builder addresses(List<Dhcpv6Address> addresses);

        public abstract Builder prefixes(List<Dhcpv6Prefix> prefixes);

        public abstract Builder timestamps(Map<String, List<String>> timestamps);

        public abstract Builder firstPacket(DateTime firstPacket);

        public abstract Builder latestPacket(DateTime latestPacket);

        public abstract Builder notes(List<String> notes);

        public abstract Builder options(List<Integer> options);

        public abstract Builder optionRequestList(List<Integer> optionRequestList);

        public abstract Builder vendorClass(String vendorClass);

        public abstract Builder fqdn(String fqdn);

        public abstract Builder serverResponses(List<Dhcpv6ServerResponseReport> serverResponses);

        public abstract Builder selectedServerAddress(String selectedServerAddress);

        public abstract Builder successful(Boolean successful);

        public abstract Builder complete(boolean complete);

        public abstract Dhcpv6TransactionReport build();
    }

}
