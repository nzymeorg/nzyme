package app.nzyme.core.rest.responses.ethernet.assets.identity;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class AssetLinkResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("predecessor_mac")
    public abstract String predecessorMac();

    @Nullable
    @JsonProperty("predecessor_mac_data")
    public abstract EthernetMacAddressResponse predecessorMacData();

    @JsonProperty("successor_mac")
    public abstract String successorMac();

    @Nullable
    @JsonProperty("successor_mac_data")
    public abstract EthernetMacAddressResponse successorMacData();

    @JsonProperty("kind")
    public abstract String kind();

    @JsonProperty("confidence")
    public abstract int confidence();

    @JsonProperty("evidence")
    public abstract List<AssetLinkEvidenceResponse> evidence();

    @JsonProperty("status")
    public abstract String status();

    @JsonProperty("predecessor_last_seen")
    public abstract DateTime predecessorLastSeen();

    @JsonProperty("successor_first_seen")
    public abstract DateTime successorFirstSeen();

    @JsonProperty("detected_at")
    public abstract DateTime detectedAt();

    @JsonProperty("updated_at")
    public abstract DateTime updatedAt();

    public static AssetLinkResponse create(UUID uuid, String predecessorMac, EthernetMacAddressResponse predecessorMacData, String successorMac, EthernetMacAddressResponse successorMacData, String kind, int confidence, List<AssetLinkEvidenceResponse> evidence, String status, DateTime predecessorLastSeen, DateTime successorFirstSeen, DateTime detectedAt, DateTime updatedAt) {
        return builder()
                .uuid(uuid)
                .predecessorMac(predecessorMac)
                .predecessorMacData(predecessorMacData)
                .successorMac(successorMac)
                .successorMacData(successorMacData)
                .kind(kind)
                .confidence(confidence)
                .evidence(evidence)
                .status(status)
                .predecessorLastSeen(predecessorLastSeen)
                .successorFirstSeen(successorFirstSeen)
                .detectedAt(detectedAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetLinkResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);
        public abstract Builder predecessorMac(String predecessorMac);
        public abstract Builder predecessorMacData(EthernetMacAddressResponse predecessorMacData);
        public abstract Builder successorMac(String successorMac);
        public abstract Builder successorMacData(EthernetMacAddressResponse successorMacData);
        public abstract Builder kind(String kind);
        public abstract Builder confidence(int confidence);
        public abstract Builder evidence(List<AssetLinkEvidenceResponse> evidence);
        public abstract Builder status(String status);
        public abstract Builder predecessorLastSeen(DateTime predecessorLastSeen);
        public abstract Builder successorFirstSeen(DateTime successorFirstSeen);
        public abstract Builder detectedAt(DateTime detectedAt);
        public abstract Builder updatedAt(DateTime updatedAt);
        public abstract AssetLinkResponse build();
    }
}
