package app.nzyme.core.rest.responses.ethernet.spoofing;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class DhcpSpoofingServerResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("mac")
    public abstract String mac();

    @Nullable
    @JsonProperty("mac_data")
    public abstract EthernetMacAddressResponse macData();

    @JsonProperty("address")
    public abstract String address();

    @Nullable
    @JsonProperty("address_data")
    public abstract L4AddressResponse addressData();

    @Nullable
    @JsonProperty("server_identifier")
    public abstract String serverIdentifier();

    @JsonProperty("trusted")
    public abstract boolean trusted();

    @Nullable
    @JsonProperty("trust_source")
    public abstract String trustSource();

    @Nullable
    @JsonProperty("baseline")
    public abstract DhcpOfferedParametersResponse baseline();

    @Nullable
    @JsonProperty("baseline_updated_at")
    public abstract DateTime baselineUpdatedAt();

    @Nullable
    @JsonProperty("latest")
    public abstract DhcpOfferedParametersResponse latest();

    @JsonProperty("latest_matches_baseline")
    public abstract boolean latestMatchesBaseline();

    @JsonProperty("response_count")
    public abstract long responseCount();

    @JsonProperty("offer_count")
    public abstract long offerCount();

    @JsonProperty("ack_count")
    public abstract long ackCount();

    @JsonProperty("transaction_count")
    public abstract long transactionCount();

    @JsonProperty("taps")
    public abstract List<SpoofingTapResponse> taps();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static DhcpSpoofingServerResponse create(UUID uuid, String mac, EthernetMacAddressResponse macData, String address, L4AddressResponse addressData, String serverIdentifier, boolean trusted, String trustSource, DhcpOfferedParametersResponse baseline, DateTime baselineUpdatedAt, DhcpOfferedParametersResponse latest, boolean latestMatchesBaseline, long responseCount, long offerCount, long ackCount, long transactionCount, List<SpoofingTapResponse> taps, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .uuid(uuid)
                .mac(mac)
                .macData(macData)
                .address(address)
                .addressData(addressData)
                .serverIdentifier(serverIdentifier)
                .trusted(trusted)
                .trustSource(trustSource)
                .baseline(baseline)
                .baselineUpdatedAt(baselineUpdatedAt)
                .latest(latest)
                .latestMatchesBaseline(latestMatchesBaseline)
                .responseCount(responseCount)
                .offerCount(offerCount)
                .ackCount(ackCount)
                .transactionCount(transactionCount)
                .taps(taps)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DhcpSpoofingServerResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder mac(String mac);
        public abstract Builder macData(EthernetMacAddressResponse macData);
        public abstract Builder address(String address);
        public abstract Builder addressData(L4AddressResponse addressData);
        public abstract Builder serverIdentifier(String serverIdentifier);
        public abstract Builder trusted(boolean trusted);
        public abstract Builder trustSource(String trustSource);
        public abstract Builder baseline(DhcpOfferedParametersResponse baseline);
        public abstract Builder baselineUpdatedAt(DateTime baselineUpdatedAt);
        public abstract Builder latest(DhcpOfferedParametersResponse latest);
        public abstract Builder latestMatchesBaseline(boolean latestMatchesBaseline);
        public abstract Builder responseCount(long responseCount);
        public abstract Builder offerCount(long offerCount);
        public abstract Builder ackCount(long ackCount);
        public abstract Builder transactionCount(long transactionCount);
        public abstract Builder taps(List<SpoofingTapResponse> taps);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract DhcpSpoofingServerResponse build();
    }
}
