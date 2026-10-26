package app.nzyme.core.rest.resources.taps.reports.tables.dhcp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.Set;

@AutoValue
public abstract class Dhcpv4TransactionReport {

    public abstract String transactionType();
    public abstract long transactionId();
    public abstract String clientMac();
    public abstract Set<String> additionalClientMacs();
    @Nullable
    public abstract String serverMac();
    public abstract Set<String> additionalServerMacs();
    public abstract Set<String> offeredIpAddresses();
    @Nullable
    public abstract String requestedIpAddress();
    @Nullable
    public abstract List<Integer> options();
    @Nullable
    public abstract Set<List<Integer>> additionalOptions();
    @Nullable
    public abstract String vendorClass();
    public abstract Set<String> additionalVendorClasses();
    @Nullable
    public abstract Map<String, List<String>> timestamps(); // String not DateTime, because we need microsecond resolution.
    public abstract DateTime firstPacket();
    public abstract DateTime latestPacket();
    public abstract Set<String> notes();
    public abstract List<Dhcpv4ServerResponseReport> serverResponses();
    @Nullable
    public abstract String selectedServerAddress();
    @Nullable
    public abstract Boolean successful();
    @Nullable
    public abstract Boolean complete();

    @JsonCreator
    public static Dhcpv4TransactionReport create(@JsonProperty("transaction_type") String transactionType,
                                                 @JsonProperty("transaction_id") long transactionId,
                                                 @JsonProperty("client_mac") String clientMac,
                                                 @JsonProperty("additional_client_macs") Set<String> additionalClientMacs,
                                                 @JsonProperty("server_mac") String serverMac,
                                                 @JsonProperty("additional_server_macs") Set<String> additionalServerMacs,
                                                 @JsonProperty("offered_ip_addresses") Set<String> offeredIpAddresses,
                                                 @JsonProperty("requested_ip_address") String requestedIpAddress,
                                                 @JsonProperty("options") List<Integer> options,
                                                 @JsonProperty("additional_options") Set<List<Integer>> additionalOptions,
                                                 @JsonProperty("vendor_class") String vendorClass,
                                                 @JsonProperty("additional_vendor_classes") List<String> additionalVendorClasses,
                                                 @JsonProperty("timestamps") Map<String, List<String>> timestamps,
                                                 @JsonProperty("first_packet") DateTime firstPacket,
                                                 @JsonProperty("latest_packet") DateTime latestPacket,
                                                 @JsonProperty("notes") Set<String> notes,
                                                 @JsonProperty("server_responses") List<Dhcpv4ServerResponseReport> serverResponses,
                                                 @JsonProperty("selected_server_address") String selectedServerAddress,
                                                 @JsonProperty("successful") Boolean successful,
                                                 @JsonProperty("complete") Boolean complete) {
        return builder()
                .transactionType(transactionType)
                .transactionId(transactionId)
                .clientMac(clientMac)
                .additionalClientMacs(additionalClientMacs)
                .serverMac(serverMac)
                .additionalServerMacs(additionalServerMacs)
                .offeredIpAddresses(offeredIpAddresses)
                .requestedIpAddress(requestedIpAddress)
                .options(options)
                .additionalOptions(additionalOptions)
                .vendorClass(vendorClass)
                .additionalVendorClasses(additionalVendorClasses)
                .timestamps(timestamps)
                .firstPacket(firstPacket)
                .latestPacket(latestPacket)
                .notes(notes)
                .serverResponses(serverResponses == null ? List.of() : serverResponses) // Older taps do not send it.
                .selectedServerAddress(selectedServerAddress)
                .successful(successful)
                .complete(complete)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dhcpv4TransactionReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder transactionType(String transactionType);

        public abstract Builder transactionId(long transactionId);

        public abstract Builder clientMac(String clientMac);

        public abstract Builder additionalClientMacs(Set<String> additionalClientMacs);

        public abstract Builder serverMac(String serverMac);

        public abstract Builder additionalServerMacs(Set<String> additionalServerMacs);

        public abstract Builder offeredIpAddresses(Set<String> offeredIpAddresses);

        public abstract Builder requestedIpAddress(String requestedIpAddress);

        public abstract Builder options(List<Integer> options);

        public abstract Builder additionalOptions(Set<List<Integer>> additionalOptions);

        public abstract Builder vendorClass(String vendorClass);

        public abstract Builder additionalVendorClasses(List<String> additionalVendorClasses);

        public abstract Builder timestamps(Map<String, List<String>> timestamps);

        public abstract Builder firstPacket(DateTime firstPacket);

        public abstract Builder latestPacket(DateTime latestPacket);

        public abstract Builder notes(Set<String> notes);

        public abstract Builder serverResponses(List<Dhcpv4ServerResponseReport> serverResponses);

        public abstract Builder selectedServerAddress(String selectedServerAddress);

        public abstract Builder successful(Boolean successful);

        public abstract Builder complete(Boolean complete);

        public abstract Dhcpv4TransactionReport build();
    }
}
