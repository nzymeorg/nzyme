package app.nzyme.core.ethernet.dhcp.db;

import app.nzyme.core.rest.responses.ethernet.InternalAddressResponse;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;

@AutoValue
public abstract class DHCPTransactionEntry {

    public abstract long transactionId();
    public abstract String transactionType();
    public abstract String clientMac();
    public abstract List<String> additionalClientMacs();
    @Nullable
    public abstract String serverMac();
    public abstract List<String> additionalServerMacs();
    public abstract List<String> offeredIpAddresses();
    @Nullable
    public abstract String requestedIpAddress();
    public abstract List<Integer> options();
    public abstract List<List<Integer>> additionalOptions();
    @Nullable
    public abstract String fingerprint();
    public abstract List<String> additionalFingerprints();
    @Nullable
    public abstract String vendorClass();
    public abstract List<String> additionalVendorClasses();
    public abstract Map<String, List<String>> timestamps(); // String not DateTime, because we need microsecond resolution.
    @Nullable
    public abstract DateTime firstPacket();
    public abstract DateTime latestPacket();
    public abstract List<String> notes();
    public abstract List<DHCPServerResponseEntry> serverResponses();
    @Nullable
    public abstract String selectedServerAddress();
    public abstract boolean isSuccessful();
    public abstract boolean isComplete();

    public static DHCPTransactionEntry create(long transactionId, String transactionType, String clientMac, List<String> additionalClientMacs, String serverMac, List<String> additionalServerMacs, List<String> offeredIpAddresses, String requestedIpAddress, List<Integer> options, List<List<Integer>> additionalOptions, String fingerprint, List<String> additionalFingerprints, String vendorClass, List<String> additionalVendorClasses, Map<String, List<String>> timestamps, DateTime firstPacket, DateTime latestPacket, List<String> notes, List<DHCPServerResponseEntry> serverResponses, String selectedServerAddress, boolean isSuccessful, boolean isComplete) {
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
                .timestamps(timestamps)
                .firstPacket(firstPacket)
                .latestPacket(latestPacket)
                .notes(notes)
                .serverResponses(serverResponses)
                .selectedServerAddress(selectedServerAddress)
                .isSuccessful(isSuccessful)
                .isComplete(isComplete)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DHCPTransactionEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder transactionId(long transactionId);

        public abstract Builder transactionType(String transactionType);

        public abstract Builder clientMac(String clientMac);

        public abstract Builder additionalClientMacs(List<String> additionalClientMacs);

        public abstract Builder serverMac(String serverMac);

        public abstract Builder additionalServerMacs(List<String> additionalServerMacs);

        public abstract Builder offeredIpAddresses(List<String> offeredIpAddresses);

        public abstract Builder requestedIpAddress(String requestedIpAddress);

        public abstract Builder options(List<Integer> options);

        public abstract Builder additionalOptions(List<List<Integer>> additionalOptions);

        public abstract Builder fingerprint(String fingerprint);

        public abstract Builder additionalFingerprints(List<String> additionalFingerprints);

        public abstract Builder vendorClass(String vendorClass);

        public abstract Builder additionalVendorClasses(List<String> additionalVendorClasses);

        public abstract Builder timestamps(Map<String, List<String>> timestamps);

        public abstract Builder firstPacket(DateTime firstPacket);

        public abstract Builder latestPacket(DateTime latestPacket);

        public abstract Builder notes(List<String> notes);

        public abstract Builder serverResponses(List<DHCPServerResponseEntry> serverResponses);

        public abstract Builder selectedServerAddress(String selectedServerAddress);

        public abstract Builder isSuccessful(boolean isSuccessful);

        public abstract Builder isComplete(boolean isComplete);

        public abstract DHCPTransactionEntry build();
    }
}
