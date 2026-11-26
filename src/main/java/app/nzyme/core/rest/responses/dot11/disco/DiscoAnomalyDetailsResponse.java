package app.nzyme.core.rest.responses.dot11.disco;

import app.nzyme.core.rest.responses.dot11.Dot11MacLinkMetadataResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class DiscoAnomalyDetailsResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("tap_uuid")
    public abstract UUID tapUuid();

    @Nullable
    @JsonProperty("tap_name")
    public abstract String tapName();

    @JsonProperty("anomaly_type")
    public abstract String anomalyType();

    @JsonProperty("title")
    public abstract String title();

    @JsonProperty("bssid")
    public abstract Dot11MacLinkMetadataResponse bssid();

    @Nullable
    @JsonProperty("client")
    public abstract Dot11MacLinkMetadataResponse client();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    @JsonProperty("is_ongoing")
    public abstract boolean isOngoing();

    @JsonProperty("frame_count")
    public abstract long frameCount();

    @JsonProperty("frames_from_bssid")
    public abstract long framesFromBssid();

    @Nullable
    @JsonProperty("peak")
    public abstract Integer peak();

    @Nullable
    @JsonProperty("threshold")
    public abstract Integer threshold();

    @Nullable
    @JsonProperty("signal_strength")
    public abstract Double signalStrength();

    @Nullable
    @JsonProperty("reference_signal_strength")
    public abstract Double referenceSignalStrength();

    @JsonProperty("reason_codes")
    public abstract List<DiscoAnomalyReasonCodeResponse> reasonCodes();

    @JsonProperty("frame_types")
    public abstract List<String> frameTypes();

    @JsonProperty("sample_clients")
    public abstract List<String> sampleClients();

    @Nullable
    @JsonProperty("monitored_network_uuid")
    public abstract UUID monitoredNetworkUuid();

    @Nullable
    @JsonProperty("monitored_network_ssid")
    public abstract String monitoredNetworkSsid();

    public static DiscoAnomalyDetailsResponse create(UUID uuid, UUID tapUuid, String tapName, String anomalyType, String title, Dot11MacLinkMetadataResponse bssid, Dot11MacLinkMetadataResponse client, DateTime firstSeen, DateTime lastSeen, boolean isOngoing, long frameCount, long framesFromBssid, Integer peak, Integer threshold, Double signalStrength, Double referenceSignalStrength, List<DiscoAnomalyReasonCodeResponse> reasonCodes, List<String> frameTypes, List<String> sampleClients, UUID monitoredNetworkUuid, String monitoredNetworkSsid) {
        return builder()
                .uuid(uuid)
                .tapUuid(tapUuid)
                .tapName(tapName)
                .anomalyType(anomalyType)
                .title(title)
                .bssid(bssid)
                .client(client)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .isOngoing(isOngoing)
                .frameCount(frameCount)
                .framesFromBssid(framesFromBssid)
                .peak(peak)
                .threshold(threshold)
                .signalStrength(signalStrength)
                .referenceSignalStrength(referenceSignalStrength)
                .reasonCodes(reasonCodes)
                .frameTypes(frameTypes)
                .sampleClients(sampleClients)
                .monitoredNetworkUuid(monitoredNetworkUuid)
                .monitoredNetworkSsid(monitoredNetworkSsid)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DiscoAnomalyDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder tapUuid(UUID tapUuid);

        public abstract Builder tapName(String tapName);

        public abstract Builder anomalyType(String anomalyType);

        public abstract Builder title(String title);

        public abstract Builder bssid(Dot11MacLinkMetadataResponse bssid);

        public abstract Builder client(Dot11MacLinkMetadataResponse client);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract Builder isOngoing(boolean isOngoing);

        public abstract Builder frameCount(long frameCount);

        public abstract Builder framesFromBssid(long framesFromBssid);

        public abstract Builder peak(Integer peak);

        public abstract Builder threshold(Integer threshold);

        public abstract Builder signalStrength(Double signalStrength);

        public abstract Builder referenceSignalStrength(Double referenceSignalStrength);

        public abstract Builder reasonCodes(List<DiscoAnomalyReasonCodeResponse> reasonCodes);

        public abstract Builder frameTypes(List<String> frameTypes);

        public abstract Builder sampleClients(List<String> sampleClients);

        public abstract Builder monitoredNetworkUuid(UUID monitoredNetworkUuid);

        public abstract Builder monitoredNetworkSsid(String monitoredNetworkSsid);

        public abstract DiscoAnomalyDetailsResponse build();
    }

}
