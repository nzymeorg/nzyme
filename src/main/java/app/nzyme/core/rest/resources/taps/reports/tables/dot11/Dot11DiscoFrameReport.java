package app.nzyme.core.rest.resources.taps.reports.tables.dot11;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

import java.util.Map;

@AutoValue
public abstract class Dot11DiscoFrameReport {

    public abstract String discoType();
    public abstract String transmitter();
    public abstract String receiver();
    public abstract String bssid();
    @Nullable
    public abstract Integer reasonCode();
    public abstract boolean isProtected();
    @Nullable
    public abstract Integer frequency();
    public abstract long frameCount();
    public abstract long retryFrames();
    public abstract int pairSequenceNumbers();
    public abstract long integrityFrames();
    public abstract long orderFrames();
    public abstract long oldestFrameAgeMs();
    public abstract long newestFrameAgeMs();
    @Nullable
    public abstract Dot11SignalStrengthReport signalStrength();
    @Nullable
    public abstract Map<Integer, Long> signalHistogram();

    @JsonCreator
    public static Dot11DiscoFrameReport create(@JsonProperty("disco_type") String discoType,
                                               @JsonProperty("transmitter") String transmitter,
                                               @JsonProperty("receiver") String receiver,
                                               @JsonProperty("bssid") String bssid,
                                               @JsonProperty("reason_code") @Nullable Integer reasonCode,
                                               @JsonProperty("protected") boolean isProtected,
                                               @JsonProperty("frequency") @Nullable Integer frequency,
                                               @JsonProperty("frame_count") long frameCount,
                                               @JsonProperty("retry_frames") long retryFrames,
                                               @JsonProperty("pair_sequence_numbers") int pairSequenceNumbers,
                                               @JsonProperty("integrity_frames") long integrityFrames,
                                               @JsonProperty("order_frames") long orderFrames,
                                               @JsonProperty("oldest_frame_age_ms") long oldestFrameAgeMs,
                                               @JsonProperty("newest_frame_age_ms") long newestFrameAgeMs,
                                               @JsonProperty("signal_strength") @Nullable Dot11SignalStrengthReport signalStrength,
                                               @JsonProperty("signal_histogram") @Nullable Map<Integer, Long> signalHistogram) {
        return builder()
                .discoType(discoType)
                .transmitter(transmitter)
                .receiver(receiver)
                .bssid(bssid)
                .reasonCode(reasonCode)
                .isProtected(isProtected)
                .frequency(frequency)
                .frameCount(frameCount)
                .retryFrames(retryFrames)
                .pairSequenceNumbers(pairSequenceNumbers)
                .integrityFrames(integrityFrames)
                .orderFrames(orderFrames)
                .oldestFrameAgeMs(oldestFrameAgeMs)
                .newestFrameAgeMs(newestFrameAgeMs)
                .signalStrength(signalStrength)
                .signalHistogram(signalHistogram)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dot11DiscoFrameReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder discoType(String discoType);

        public abstract Builder transmitter(String transmitter);

        public abstract Builder receiver(String receiver);

        public abstract Builder bssid(String bssid);

        public abstract Builder reasonCode(Integer reasonCode);

        public abstract Builder isProtected(boolean isProtected);

        public abstract Builder frequency(Integer frequency);

        public abstract Builder frameCount(long frameCount);

        public abstract Builder retryFrames(long retryFrames);

        public abstract Builder pairSequenceNumbers(int pairSequenceNumbers);

        public abstract Builder integrityFrames(long integrityFrames);

        public abstract Builder orderFrames(long orderFrames);

        public abstract Builder oldestFrameAgeMs(long oldestFrameAgeMs);

        public abstract Builder newestFrameAgeMs(long newestFrameAgeMs);

        public abstract Builder signalStrength(Dot11SignalStrengthReport signalStrength);

        public abstract Builder signalHistogram(Map<Integer, Long> signalHistogram);

        public abstract Dot11DiscoFrameReport build();
    }

}
