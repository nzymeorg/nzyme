package app.nzyme.core.rest.responses.dot11.disco;

import app.nzyme.core.rest.responses.dot11.Dot11MacLinkMetadataResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class ConnectionFrameDetailsResponse {

    @JsonProperty("id")
    public abstract long id();

    @JsonProperty("frame_type")
    public abstract String frameType();

    @JsonProperty("transmitter")
    public abstract Dot11MacLinkMetadataResponse transmitter();

    @JsonProperty("receiver")
    public abstract Dot11MacLinkMetadataResponse receiver();

    @JsonProperty("bssid")
    public abstract Dot11MacLinkMetadataResponse bssid();

    @Nullable
    @JsonProperty("algorithm")
    public abstract Integer algorithm();

    @Nullable
    @JsonProperty("transaction")
    public abstract Integer transaction();

    @Nullable
    @JsonProperty("status_code")
    public abstract Integer statusCode();

    @Nullable
    @JsonProperty("frequency")
    public abstract Integer frequency();

    @Nullable
    @JsonProperty("signal_strength_average")
    public abstract Float signalStrengthAverage();

    @JsonProperty("frame_count")
    public abstract long frameCount();

    @JsonProperty("retry_frames")
    public abstract long retryFrames();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static ConnectionFrameDetailsResponse create(long id, String frameType, Dot11MacLinkMetadataResponse transmitter, Dot11MacLinkMetadataResponse receiver, Dot11MacLinkMetadataResponse bssid, Integer algorithm, Integer transaction, Integer statusCode, Integer frequency, Float signalStrengthAverage, long frameCount, long retryFrames, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .id(id)
                .frameType(frameType)
                .transmitter(transmitter)
                .receiver(receiver)
                .bssid(bssid)
                .algorithm(algorithm)
                .transaction(transaction)
                .statusCode(statusCode)
                .frequency(frequency)
                .signalStrengthAverage(signalStrengthAverage)
                .frameCount(frameCount)
                .retryFrames(retryFrames)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ConnectionFrameDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);

        public abstract Builder frameType(String frameType);

        public abstract Builder transmitter(Dot11MacLinkMetadataResponse transmitter);

        public abstract Builder receiver(Dot11MacLinkMetadataResponse receiver);

        public abstract Builder bssid(Dot11MacLinkMetadataResponse bssid);

        public abstract Builder algorithm(Integer algorithm);

        public abstract Builder transaction(Integer transaction);

        public abstract Builder statusCode(Integer statusCode);

        public abstract Builder frequency(Integer frequency);

        public abstract Builder signalStrengthAverage(Float signalStrengthAverage);

        public abstract Builder frameCount(long frameCount);

        public abstract Builder retryFrames(long retryFrames);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract ConnectionFrameDetailsResponse build();
    }

}
