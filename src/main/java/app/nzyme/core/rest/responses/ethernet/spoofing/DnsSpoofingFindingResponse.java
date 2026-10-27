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
public abstract class DnsSpoofingFindingResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("server_address")
    public abstract String serverAddress();

    @Nullable
    @JsonProperty("server_address_data")
    public abstract L4AddressResponse serverAddressData();

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

    @JsonProperty("forged_count")
    public abstract long forgedCount();

    @JsonProperty("won_count")
    public abstract long wonCount();

    @JsonProperty("unanswered_count")
    public abstract long unansweredCount();

    @JsonProperty("names")
    public abstract List<String> names();

    @JsonProperty("name_count")
    public abstract int nameCount();

    @JsonProperty("answers")
    public abstract List<String> answers();

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

    public static DnsSpoofingFindingResponse create(UUID uuid, String serverAddress, L4AddressResponse serverAddressData, String spooferMac, EthernetMacAddressResponse spooferMacData, String ownerMac, EthernetMacAddressResponse ownerMacData, long forgedCount, long wonCount, long unansweredCount, List<String> names, int nameCount, List<String> answers, int victimCount, boolean recent, int windowMinutes, List<SpoofingTapResponse> taps, List<SpoofingExclusionReferenceResponse> exclusions, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .uuid(uuid)
                .serverAddress(serverAddress)
                .serverAddressData(serverAddressData)
                .spooferMac(spooferMac)
                .spooferMacData(spooferMacData)
                .ownerMac(ownerMac)
                .ownerMacData(ownerMacData)
                .forgedCount(forgedCount)
                .wonCount(wonCount)
                .unansweredCount(unansweredCount)
                .names(names)
                .nameCount(nameCount)
                .answers(answers)
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
        return new AutoValue_DnsSpoofingFindingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder serverAddress(String serverAddress);

        public abstract Builder serverAddressData(L4AddressResponse serverAddressData);

        public abstract Builder spooferMac(String spooferMac);

        public abstract Builder spooferMacData(EthernetMacAddressResponse spooferMacData);

        public abstract Builder ownerMac(String ownerMac);

        public abstract Builder ownerMacData(EthernetMacAddressResponse ownerMacData);

        public abstract Builder forgedCount(long forgedCount);

        public abstract Builder wonCount(long wonCount);

        public abstract Builder unansweredCount(long unansweredCount);

        public abstract Builder names(List<String> names);

        public abstract Builder nameCount(int nameCount);

        public abstract Builder answers(List<String> answers);

        public abstract Builder victimCount(int victimCount);

        public abstract Builder recent(boolean recent);

        public abstract Builder windowMinutes(int windowMinutes);

        public abstract Builder taps(List<SpoofingTapResponse> taps);

        public abstract Builder exclusions(List<SpoofingExclusionReferenceResponse> exclusions);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract DnsSpoofingFindingResponse build();
    }
}
