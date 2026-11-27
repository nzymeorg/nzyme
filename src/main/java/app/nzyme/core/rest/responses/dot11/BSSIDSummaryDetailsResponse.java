package app.nzyme.core.rest.responses.dot11;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.util.List;

@AutoValue
public abstract class BSSIDSummaryDetailsResponse {

    @JsonProperty("bssid")
    public abstract Dot11MacAddressResponse bssid();

    @JsonProperty("security_protocols")
    @Nullable
    public abstract List<String> securityProtocols();

    @JsonProperty("signal_strength_average")
    public abstract float signalStrengthAverage();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    @JsonProperty("client_count")
    public abstract long clientCount();

    @JsonProperty("fingerprints")
    public abstract List<String> fingerprints();

    @JsonProperty("advertised_ssid_names")
    public abstract List<String> advertisedSSIDNames();

    @JsonProperty("has_hidden_ssid_advertisements")
    public abstract boolean hasHiddenSSIDAdvertisements();

    @JsonProperty("infrastructure_types")
    public abstract List<String> infrastructureTypes();

    @Schema(description = "Up to 5 SSIDs advertised by access points with a neighboring address, which are likely "
            + "the same device. Only filled in the list of BSSIDs, and there only for BSSIDs without an SSID of "
            + "their own. A hint only.")
    @JsonProperty("likely_same_device_ssids")
    public abstract List<String> likelySameDeviceSsids();

    public static BSSIDSummaryDetailsResponse create(Dot11MacAddressResponse bssid, List<String> securityProtocols, float signalStrengthAverage, DateTime firstSeen, DateTime lastSeen, long clientCount, List<String> fingerprints, List<String> advertisedSSIDNames, boolean hasHiddenSSIDAdvertisements, List<String> infrastructureTypes, List<String> likelySameDeviceSsids) {
        return builder()
                .bssid(bssid)
                .securityProtocols(securityProtocols)
                .signalStrengthAverage(signalStrengthAverage)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .clientCount(clientCount)
                .fingerprints(fingerprints)
                .advertisedSSIDNames(advertisedSSIDNames)
                .hasHiddenSSIDAdvertisements(hasHiddenSSIDAdvertisements)
                .infrastructureTypes(infrastructureTypes)
                .likelySameDeviceSsids(likelySameDeviceSsids)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BSSIDSummaryDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bssid(Dot11MacAddressResponse bssid);

        public abstract Builder securityProtocols(List<String> securityProtocols);

        public abstract Builder signalStrengthAverage(float signalStrengthAverage);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract Builder clientCount(long clientCount);

        public abstract Builder fingerprints(List<String> fingerprints);

        public abstract Builder advertisedSSIDNames(List<String> advertisedSSIDNames);

        public abstract Builder hasHiddenSSIDAdvertisements(boolean hasHiddenSSIDAdvertisements);

        public abstract Builder infrastructureTypes(List<String> infrastructureTypes);

        public abstract Builder likelySameDeviceSsids(List<String> likelySameDeviceSsids);

        public abstract BSSIDSummaryDetailsResponse build();
    }
}
