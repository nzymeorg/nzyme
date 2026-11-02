package app.nzyme.core.rest.responses.ethernet.dhcp;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class DHCPFlakyFingerprintAssetResponse {

    @JsonProperty("mac")
    public abstract EthernetMacAddressResponse mac();

    @JsonProperty("transaction_types")
    public abstract List<String> transactionTypes();

    @JsonProperty("fingerprints")
    public abstract List<String> fingerprints();

    @JsonProperty("fingerprint_count")
    public abstract long fingerprintCount();

    @JsonProperty("change_count")
    public abstract long changeCount();

    @JsonProperty("transaction_count")
    public abstract long transactionCount();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    @JsonProperty("exclusions")
    public abstract List<DHCPFingerprintExclusionReferenceResponse> exclusions();

    public static DHCPFlakyFingerprintAssetResponse create(EthernetMacAddressResponse mac, List<String> transactionTypes, List<String> fingerprints, long fingerprintCount, long changeCount, long transactionCount, DateTime firstSeen, DateTime lastSeen, List<DHCPFingerprintExclusionReferenceResponse> exclusions) {
        return builder()
                .exclusions(exclusions)
                .mac(mac)
                .transactionTypes(transactionTypes)
                .fingerprints(fingerprints)
                .fingerprintCount(fingerprintCount)
                .changeCount(changeCount)
                .transactionCount(transactionCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DHCPFlakyFingerprintAssetResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder mac(EthernetMacAddressResponse mac);

        public abstract Builder transactionTypes(List<String> transactionTypes);

        public abstract Builder fingerprints(List<String> fingerprints);

        public abstract Builder fingerprintCount(long fingerprintCount);

        public abstract Builder changeCount(long changeCount);

        public abstract Builder transactionCount(long transactionCount);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract Builder exclusions(List<DHCPFingerprintExclusionReferenceResponse> exclusions);

        public abstract DHCPFlakyFingerprintAssetResponse build();
    }
}
