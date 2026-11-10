package app.nzyme.core.rest.responses.dot11.disco;

import app.nzyme.core.rest.responses.dot11.Dot11MacLinkMetadataResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class DiscoFrameDetailsResponse {

    @JsonProperty("id")
    public abstract long id();

    @JsonProperty("tap_uuid")
    public abstract UUID tapUuid();

    @Nullable
    @JsonProperty("tap_name")
    public abstract String tapName();

    @JsonProperty("timestamp")
    public abstract DateTime timestamp();

    @JsonProperty("disco_type")
    public abstract String discoType();

    @JsonProperty("transmitter")
    public abstract Dot11MacLinkMetadataResponse transmitter();

    @JsonProperty("receiver")
    public abstract Dot11MacLinkMetadataResponse receiver();

    @JsonProperty("bssid")
    public abstract Dot11MacLinkMetadataResponse bssid();

    @JsonProperty("is_broadcast")
    public abstract boolean isBroadcast();

    @JsonProperty("transmitter_is_bssid")
    public abstract boolean transmitterIsBssid();

    @JsonProperty("receiver_is_bssid")
    public abstract boolean receiverIsBssid();

    @JsonProperty("reason_code")
    public abstract int reasonCode();

    @JsonProperty("reason")
    public abstract String reason();

    @Nullable
    @JsonProperty("frequency")
    public abstract Integer frequency();

    @Nullable
    @JsonProperty("signal_strength_min")
    public abstract Integer signalStrengthMin();

    @Nullable
    @JsonProperty("signal_strength_max")
    public abstract Integer signalStrengthMax();

    @Nullable
    @JsonProperty("signal_strength_average")
    public abstract Float signalStrengthAverage();

    @JsonProperty("frame_count")
    public abstract long frameCount();

    public static DiscoFrameDetailsResponse create(long id, UUID tapUuid, String tapName, DateTime timestamp, String discoType, Dot11MacLinkMetadataResponse transmitter, Dot11MacLinkMetadataResponse receiver, Dot11MacLinkMetadataResponse bssid, boolean isBroadcast, boolean transmitterIsBssid, boolean receiverIsBssid, int reasonCode, String reason, Integer frequency, Integer signalStrengthMin, Integer signalStrengthMax, Float signalStrengthAverage, long frameCount) {
        return builder()
                .id(id)
                .tapUuid(tapUuid)
                .tapName(tapName)
                .timestamp(timestamp)
                .discoType(discoType)
                .transmitter(transmitter)
                .receiver(receiver)
                .bssid(bssid)
                .isBroadcast(isBroadcast)
                .transmitterIsBssid(transmitterIsBssid)
                .receiverIsBssid(receiverIsBssid)
                .reasonCode(reasonCode)
                .reason(reason)
                .frequency(frequency)
                .signalStrengthMin(signalStrengthMin)
                .signalStrengthMax(signalStrengthMax)
                .signalStrengthAverage(signalStrengthAverage)
                .frameCount(frameCount)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DiscoFrameDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);

        public abstract Builder tapUuid(UUID tapUuid);

        public abstract Builder tapName(String tapName);

        public abstract Builder timestamp(DateTime timestamp);

        public abstract Builder discoType(String discoType);

        public abstract Builder transmitter(Dot11MacLinkMetadataResponse transmitter);

        public abstract Builder receiver(Dot11MacLinkMetadataResponse receiver);

        public abstract Builder bssid(Dot11MacLinkMetadataResponse bssid);

        public abstract Builder isBroadcast(boolean isBroadcast);

        public abstract Builder transmitterIsBssid(boolean transmitterIsBssid);

        public abstract Builder receiverIsBssid(boolean receiverIsBssid);

        public abstract Builder reasonCode(int reasonCode);

        public abstract Builder reason(String reason);

        public abstract Builder frequency(Integer frequency);

        public abstract Builder signalStrengthMin(Integer signalStrengthMin);

        public abstract Builder signalStrengthMax(Integer signalStrengthMax);

        public abstract Builder signalStrengthAverage(Float signalStrengthAverage);

        public abstract Builder frameCount(long frameCount);

        public abstract DiscoFrameDetailsResponse build();
    }
}
