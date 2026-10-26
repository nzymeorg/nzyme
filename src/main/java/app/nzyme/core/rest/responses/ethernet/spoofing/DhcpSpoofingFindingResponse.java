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
public abstract class DhcpSpoofingFindingResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("type")
    public abstract String type();

    @JsonProperty("server_mac")
    public abstract String serverMac();

    @Nullable
    @JsonProperty("server_mac_data")
    public abstract EthernetMacAddressResponse serverMacData();

    @JsonProperty("server_address")
    public abstract String serverAddress();

    @Nullable
    @JsonProperty("server_address_data")
    public abstract L4AddressResponse serverAddressData();

    @Nullable
    @JsonProperty("server_identifier")
    public abstract String serverIdentifier();

    @Nullable
    @JsonProperty("server_uuid")
    public abstract UUID serverUuid();

    @Nullable
    @JsonProperty("server_trusted")
    public abstract Boolean serverTrusted();

    @Nullable
    @JsonProperty("offered_accepted")
    public abstract Boolean offeredAccepted();

    @Nullable
    @JsonProperty("server_latest_matches_baseline")
    public abstract Boolean serverLatestMatchesBaseline();

    @Nullable
    @JsonProperty("offered")
    public abstract DhcpOfferedParametersResponse offered();

    @Nullable
    @JsonProperty("expected")
    public abstract DhcpOfferedParametersResponse expected();

    @JsonProperty("response_count")
    public abstract long responseCount();

    @JsonProperty("offer_count")
    public abstract long offerCount();

    @JsonProperty("ack_count")
    public abstract long ackCount();

    @JsonProperty("transaction_count")
    public abstract long transactionCount();

    @JsonProperty("victim_count")
    public abstract int victimCount();

    @JsonProperty("recent")
    public abstract boolean recent();

    @JsonProperty("window_minutes")
    public abstract int windowMinutes();

    @JsonProperty("taps")
    public abstract List<SpoofingTapResponse> taps();

    @JsonProperty("exclusions")
    public abstract List<SpoofingExclusionReferenceResponse> exclusions();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static DhcpSpoofingFindingResponse create(UUID uuid, String type, String serverMac, EthernetMacAddressResponse serverMacData, String serverAddress, L4AddressResponse serverAddressData, String serverIdentifier, UUID serverUuid, Boolean serverTrusted, Boolean offeredAccepted, Boolean serverLatestMatchesBaseline, DhcpOfferedParametersResponse offered, DhcpOfferedParametersResponse expected, long responseCount, long offerCount, long ackCount, long transactionCount, int victimCount, boolean recent, int windowMinutes, List<SpoofingTapResponse> taps, List<SpoofingExclusionReferenceResponse> exclusions, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .uuid(uuid)
                .type(type)
                .serverMac(serverMac)
                .serverMacData(serverMacData)
                .serverAddress(serverAddress)
                .serverAddressData(serverAddressData)
                .serverIdentifier(serverIdentifier)
                .serverUuid(serverUuid)
                .serverTrusted(serverTrusted)
                .offeredAccepted(offeredAccepted)
                .serverLatestMatchesBaseline(serverLatestMatchesBaseline)
                .offered(offered)
                .expected(expected)
                .responseCount(responseCount)
                .offerCount(offerCount)
                .ackCount(ackCount)
                .transactionCount(transactionCount)
                .victimCount(victimCount)
                .recent(recent)
                .windowMinutes(windowMinutes)
                .taps(taps)
                .exclusions(exclusions)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DhcpSpoofingFindingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder type(String type);
        public abstract Builder serverMac(String serverMac);
        public abstract Builder serverMacData(EthernetMacAddressResponse serverMacData);
        public abstract Builder serverAddress(String serverAddress);
        public abstract Builder serverAddressData(L4AddressResponse serverAddressData);
        public abstract Builder serverIdentifier(String serverIdentifier);
        public abstract Builder serverUuid(UUID serverUuid);
        public abstract Builder serverTrusted(Boolean serverTrusted);

        public abstract Builder offeredAccepted(Boolean offeredAccepted);

        public abstract Builder serverLatestMatchesBaseline(Boolean serverLatestMatchesBaseline);
        public abstract Builder offered(DhcpOfferedParametersResponse offered);
        public abstract Builder expected(DhcpOfferedParametersResponse expected);
        public abstract Builder responseCount(long responseCount);
        public abstract Builder offerCount(long offerCount);
        public abstract Builder ackCount(long ackCount);
        public abstract Builder transactionCount(long transactionCount);
        public abstract Builder victimCount(int victimCount);
        public abstract Builder recent(boolean recent);
        public abstract Builder windowMinutes(int windowMinutes);
        public abstract Builder taps(List<SpoofingTapResponse> taps);
        public abstract Builder exclusions(List<SpoofingExclusionReferenceResponse> exclusions);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract DhcpSpoofingFindingResponse build();
    }
}
