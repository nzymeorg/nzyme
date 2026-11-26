package app.nzyme.core.rest.responses.dot11.signal;

import app.nzyme.core.rest.responses.dot11.Dot11MacLinkMetadataResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

@AutoValue
public abstract class FrameSignalTransmitterResponse {

    @JsonProperty("mac")
    public abstract Dot11MacLinkMetadataResponse mac();

    @JsonProperty("type")
    public abstract String type();

    @JsonProperty("frame_count")
    public abstract long frameCount();

    @JsonProperty("receiver_count")
    public abstract long receiverCount();

    @JsonProperty("comparison_frame_count")
    public abstract long comparisonFrameCount();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static FrameSignalTransmitterResponse create(Dot11MacLinkMetadataResponse mac, String type, long frameCount, long receiverCount, long comparisonFrameCount, DateTime lastSeen) {
        return builder()
                .mac(mac)
                .type(type)
                .frameCount(frameCount)
                .receiverCount(receiverCount)
                .comparisonFrameCount(comparisonFrameCount)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FrameSignalTransmitterResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder mac(Dot11MacLinkMetadataResponse mac);

        public abstract Builder type(String type);

        public abstract Builder frameCount(long frameCount);

        public abstract Builder receiverCount(long receiverCount);

        public abstract Builder comparisonFrameCount(long comparisonFrameCount);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract FrameSignalTransmitterResponse build();
    }

}
