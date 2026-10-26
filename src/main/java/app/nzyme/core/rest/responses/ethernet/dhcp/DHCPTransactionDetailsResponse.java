package app.nzyme.core.rest.responses.ethernet.dhcp;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.InternalAddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;

@AutoValue
public abstract class DHCPTransactionDetailsResponse {

    @JsonProperty("transaction_id")
    public abstract long transactionId();

    @JsonProperty("transaction_type")
    public abstract String transactionType();

    @JsonProperty("client_mac")
    public abstract EthernetMacAddressResponse clientMac();

    @JsonProperty("additional_client_macs")
    public abstract List<String> additionalClientMacs();

    @JsonProperty("server_mac")
    @Nullable
    public abstract EthernetMacAddressResponse serverMac();

    @JsonProperty("additional_server_macs")
    public abstract List<String> additionalServerMacs();

    @JsonProperty("offered_ip_addresses")
    public abstract List<String> offeredIpAddresses();

    @JsonProperty("requested_ip_address")
    @Nullable
    public abstract InternalAddressResponse requestedIpAddress();

    @JsonProperty("options")
    public abstract List<Integer> options();

    @JsonProperty("additional_options")
    public abstract List<List<Integer>> additionalOptions();

    @JsonProperty("fingerprint")
    @Nullable
    public abstract String fingerprint();

    @JsonProperty("additional_fingerprints")
    public abstract List<String> additionalFingerprints();

    @JsonProperty("vendor_class")
    @Nullable
    public abstract String vendorClass();

    @JsonProperty("additional_vendor_classes")
    public abstract List<String> additionalVendorClasses();

    @JsonProperty("timeline")
    public abstract List<DHCPTimelineStepResponse> timeline();

    @JsonProperty("first_packet")
    @Nullable
    public abstract DateTime firstPacket();

    @JsonProperty("latest_packet")
    public abstract DateTime latestPacket();

    @JsonProperty("notes")
    public abstract List<String> notes();

    @JsonProperty("is_successful")
    @Nullable
    public abstract Boolean isSuccessful();

    @JsonProperty("is_complete")
    public abstract boolean isComplete();

    @JsonProperty("duration_ms")
    @Nullable
    public abstract Long durationMs();

    @JsonProperty("server_responses")
    public abstract List<DHCPServerResponseResponse> serverResponses();

    @JsonProperty("selected_server_address")
    @Nullable
    public abstract String selectedServerAddress();

    public static DHCPTransactionDetailsResponse create(long transactionId, String transactionType, EthernetMacAddressResponse clientMac, List<String> additionalClientMacs, EthernetMacAddressResponse serverMac, List<String> additionalServerMacs, List<String> offeredIpAddresses, InternalAddressResponse requestedIpAddress, List<Integer> options, List<List<Integer>> additionalOptions, String fingerprint, List<String> additionalFingerprints, String vendorClass, List<String> additionalVendorClasses, List<DHCPTimelineStepResponse> timeline, DateTime firstPacket, DateTime latestPacket, List<String> notes, Boolean isSuccessful, boolean isComplete, Long durationMs, List<DHCPServerResponseResponse> serverResponses, String selectedServerAddress) {
        return builder()
                .transactionId(transactionId)
                .transactionType(transactionType)
                .clientMac(clientMac)
                .additionalClientMacs(additionalClientMacs)
                .serverMac(serverMac)
                .additionalServerMacs(additionalServerMacs)
                .offeredIpAddresses(offeredIpAddresses)
                .requestedIpAddress(requestedIpAddress)
                .options(options)
                .additionalOptions(additionalOptions)
                .fingerprint(fingerprint)
                .additionalFingerprints(additionalFingerprints)
                .vendorClass(vendorClass)
                .additionalVendorClasses(additionalVendorClasses)
                .timeline(timeline)
                .firstPacket(firstPacket)
                .latestPacket(latestPacket)
                .notes(notes)
                .isSuccessful(isSuccessful)
                .isComplete(isComplete)
                .durationMs(durationMs)
                .serverResponses(serverResponses)
                .selectedServerAddress(selectedServerAddress)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DHCPTransactionDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder transactionId(long transactionId);

        public abstract Builder transactionType(String transactionType);

        public abstract Builder clientMac(EthernetMacAddressResponse clientMac);

        public abstract Builder additionalClientMacs(List<String> additionalClientMacs);

        public abstract Builder serverMac(EthernetMacAddressResponse serverMac);

        public abstract Builder additionalServerMacs(List<String> additionalServerMacs);

        public abstract Builder offeredIpAddresses(List<String> offeredIpAddresses);

        public abstract Builder requestedIpAddress(InternalAddressResponse requestedIpAddress);

        public abstract Builder options(List<Integer> options);

        public abstract Builder additionalOptions(List<List<Integer>> additionalOptions);

        public abstract Builder fingerprint(String fingerprint);

        public abstract Builder additionalFingerprints(List<String> additionalFingerprints);

        public abstract Builder vendorClass(String vendorClass);

        public abstract Builder additionalVendorClasses(List<String> additionalVendorClasses);

        public abstract Builder timeline(List<DHCPTimelineStepResponse> timeline);

        public abstract Builder firstPacket(DateTime firstPacket);

        public abstract Builder latestPacket(DateTime latestPacket);

        public abstract Builder notes(List<String> notes);

        public abstract Builder isSuccessful(Boolean isSuccessful);

        public abstract Builder isComplete(boolean isComplete);

        public abstract Builder durationMs(Long durationMs);

        public abstract Builder serverResponses(List<DHCPServerResponseResponse> serverResponses);

        public abstract Builder selectedServerAddress(String selectedServerAddress);

        public abstract DHCPTransactionDetailsResponse build();
    }
}
