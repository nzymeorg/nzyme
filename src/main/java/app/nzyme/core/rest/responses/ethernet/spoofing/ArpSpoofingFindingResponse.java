package app.nzyme.core.rest.responses.ethernet.spoofing;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class ArpSpoofingFindingResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("address")
    public abstract String address();

    @Nullable
    @JsonProperty("address_data")
    public abstract L4AddressResponse addressData();

    @JsonProperty("spoofer_mac")
    public abstract String spooferMac();

    @Nullable
    @JsonProperty("spoofer_mac_data")
    public abstract EthernetMacAddressResponse spooferMacData();

    @JsonProperty("owner_mac")
    public abstract String ownerMac();

    @Nullable
    @JsonProperty("owner_mac_data")
    public abstract EthernetMacAddressResponse ownerMacData();

    @JsonProperty("claim_count")
    public abstract long claimCount();

    @JsonProperty("reply_count")
    public abstract long replyCount();

    @JsonProperty("unsolicited_count")
    public abstract long unsolicitedCount();

    @JsonProperty("broadcast_count")
    public abstract long broadcastCount();

    @JsonProperty("owner_claim_count")
    public abstract long ownerClaimCount();

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

    public static ArpSpoofingFindingResponse create(UUID uuid, String address, L4AddressResponse addressData, String spooferMac, EthernetMacAddressResponse spooferMacData, String ownerMac, EthernetMacAddressResponse ownerMacData, long claimCount, long replyCount, long unsolicitedCount, long broadcastCount, long ownerClaimCount, int victimCount, boolean recent, int windowMinutes, List<SpoofingTapResponse> taps, List<SpoofingExclusionReferenceResponse> exclusions, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .uuid(uuid)
                .address(address)
                .addressData(addressData)
                .spooferMac(spooferMac)
                .spooferMacData(spooferMacData)
                .ownerMac(ownerMac)
                .ownerMacData(ownerMacData)
                .claimCount(claimCount)
                .replyCount(replyCount)
                .unsolicitedCount(unsolicitedCount)
                .broadcastCount(broadcastCount)
                .ownerClaimCount(ownerClaimCount)
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
        return new AutoValue_ArpSpoofingFindingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder address(String address);
        public abstract Builder addressData(L4AddressResponse addressData);
        public abstract Builder spooferMac(String spooferMac);
        public abstract Builder spooferMacData(EthernetMacAddressResponse spooferMacData);
        public abstract Builder ownerMac(String ownerMac);
        public abstract Builder ownerMacData(EthernetMacAddressResponse ownerMacData);
        public abstract Builder claimCount(long claimCount);
        public abstract Builder replyCount(long replyCount);
        public abstract Builder unsolicitedCount(long unsolicitedCount);
        public abstract Builder broadcastCount(long broadcastCount);
        public abstract Builder ownerClaimCount(long ownerClaimCount);
        public abstract Builder victimCount(int victimCount);
        public abstract Builder recent(boolean recent);
        public abstract Builder windowMinutes(int windowMinutes);
        public abstract Builder taps(List<SpoofingTapResponse> taps);
        public abstract Builder exclusions(List<SpoofingExclusionReferenceResponse> exclusions);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract ArpSpoofingFindingResponse build();
    }
}
