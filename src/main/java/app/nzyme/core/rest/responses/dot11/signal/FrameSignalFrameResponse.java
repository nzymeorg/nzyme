package app.nzyme.core.rest.responses.dot11.signal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.Map;
import org.joda.time.DateTime;

@AutoValue
public abstract class FrameSignalFrameResponse {

    @JsonProperty("timestamp")
    public abstract DateTime timestamp();

    @JsonProperty("transmitter")
    public abstract String transmitter();

    @Nullable
    @JsonProperty("receiver")
    public abstract String receiver();

    @JsonProperty("is_broadcast")
    public abstract boolean isBroadcast();

    @JsonProperty("frame_type")
    public abstract String frameType();

    @Nullable
    @JsonProperty("reason_code")
    public abstract Integer reasonCode();

    @Nullable
    @JsonProperty("new_channel")
    public abstract Integer newChannel();

    @JsonProperty("is_protected")
    public abstract boolean isProtected();

    @JsonProperty("frame_count")
    public abstract long frameCount();

    @JsonProperty("signal")
    public abstract double signal();

    @Nullable
    @JsonProperty("signal_min")
    public abstract Integer signalMin();

    @Nullable
    @JsonProperty("signal_max")
    public abstract Integer signalMax();

    @Nullable
    @JsonProperty("signal_histogram")
    public abstract Map<Integer, Long> signalHistogram();

    @Nullable
    @JsonProperty("reference")
    public abstract Double reference();

    @Nullable
    @JsonProperty("difference")
    public abstract Double difference();

    public static FrameSignalFrameResponse create(DateTime timestamp, String transmitter, String receiver, boolean isBroadcast, String frameType, Integer reasonCode, Integer newChannel, boolean isProtected, long frameCount, double signal, Integer signalMin, Integer signalMax, Map<Integer, Long> signalHistogram, Double reference, Double difference) {
        return builder()
                .timestamp(timestamp)
                .transmitter(transmitter)
                .receiver(receiver)
                .isBroadcast(isBroadcast)
                .frameType(frameType)
                .reasonCode(reasonCode)
                .newChannel(newChannel)
                .isProtected(isProtected)
                .frameCount(frameCount)
                .signal(signal)
                .signalMin(signalMin)
                .signalMax(signalMax)
                .signalHistogram(signalHistogram)
                .reference(reference)
                .difference(difference)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FrameSignalFrameResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder timestamp(DateTime timestamp);

        public abstract Builder transmitter(String transmitter);

        public abstract Builder receiver(String receiver);

        public abstract Builder isBroadcast(boolean isBroadcast);

        public abstract Builder frameType(String frameType);

        public abstract Builder reasonCode(Integer reasonCode);

        public abstract Builder newChannel(Integer newChannel);

        public abstract Builder isProtected(boolean isProtected);

        public abstract Builder frameCount(long frameCount);

        public abstract Builder signal(double signal);

        public abstract Builder signalMin(Integer signalMin);

        public abstract Builder signalMax(Integer signalMax);

        public abstract Builder signalHistogram(Map<Integer, Long> signalHistogram);

        public abstract Builder reference(Double reference);

        public abstract Builder difference(Double difference);

        public abstract FrameSignalFrameResponse build();
    }

}
