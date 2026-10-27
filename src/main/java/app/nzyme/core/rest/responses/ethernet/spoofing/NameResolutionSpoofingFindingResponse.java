package app.nzyme.core.rest.responses.ethernet.spoofing;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class NameResolutionSpoofingFindingResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("responder_mac")
    public abstract String responderMac();

    @Nullable
    @JsonProperty("responder_mac_data")
    public abstract EthernetMacAddressResponse responderMacData();

    @JsonProperty("responder_addresses")
    public abstract List<String> responderAddresses();

    @JsonProperty("protocols")
    public abstract List<String> protocols();

    @JsonProperty("names")
    public abstract List<String> names();

    @JsonProperty("name_count")
    public abstract int nameCount();

    @JsonProperty("response_count")
    public abstract long responseCount();

    @JsonProperty("unicast_count")
    public abstract long unicastCount();

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

    public static NameResolutionSpoofingFindingResponse create(UUID uuid, String responderMac, EthernetMacAddressResponse responderMacData, List<String> responderAddresses, List<String> protocols, List<String> names, int nameCount, long responseCount, long unicastCount, int victimCount, boolean recent, int windowMinutes, List<SpoofingTapResponse> taps, List<SpoofingExclusionReferenceResponse> exclusions, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .uuid(uuid)
                .responderMac(responderMac)
                .responderMacData(responderMacData)
                .responderAddresses(responderAddresses)
                .protocols(protocols)
                .names(names)
                .nameCount(nameCount)
                .responseCount(responseCount)
                .unicastCount(unicastCount)
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
        return new AutoValue_NameResolutionSpoofingFindingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder responderMac(String responderMac);
        public abstract Builder responderMacData(EthernetMacAddressResponse responderMacData);
        public abstract Builder responderAddresses(List<String> responderAddresses);
        public abstract Builder protocols(List<String> protocols);
        public abstract Builder names(List<String> names);
        public abstract Builder nameCount(int nameCount);
        public abstract Builder responseCount(long responseCount);
        public abstract Builder unicastCount(long unicastCount);
        public abstract Builder victimCount(int victimCount);
        public abstract Builder recent(boolean recent);
        public abstract Builder windowMinutes(int windowMinutes);
        public abstract Builder taps(List<SpoofingTapResponse> taps);
        public abstract Builder exclusions(List<SpoofingExclusionReferenceResponse> exclusions);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract NameResolutionSpoofingFindingResponse build();
    }
}
