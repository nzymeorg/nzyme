package app.nzyme.core.rest.resources.taps.reports.tables.dot11;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class Dot11ConnectionFrameReport {

    public abstract String frameType();
    public abstract String transmitter();
    public abstract String receiver();
    public abstract String bssid();
    @Nullable
    public abstract Integer algorithm();
    @Nullable
    public abstract Integer transaction();
    @Nullable
    public abstract Integer statusCode();
    @Nullable
    public abstract Integer frequency();
    public abstract long frameCount();
    public abstract long retryFrames();
    public abstract long oldestFrameAgeMs();
    public abstract long newestFrameAgeMs();
    @Nullable
    public abstract Dot11SignalStrengthReport signalStrength();

    @JsonCreator
    public static Dot11ConnectionFrameReport create(@JsonProperty("frame_type") String frameType,
                                                    @JsonProperty("transmitter") String transmitter,
                                                    @JsonProperty("receiver") String receiver,
                                                    @JsonProperty("bssid") String bssid,
                                                    @JsonProperty("algorithm") @Nullable Integer algorithm,
                                                    @JsonProperty("transaction") @Nullable Integer transaction,
                                                    @JsonProperty("status_code") @Nullable Integer statusCode,
                                                    @JsonProperty("frequency") @Nullable Integer frequency,
                                                    @JsonProperty("frame_count") long frameCount,
                                                    @JsonProperty("retry_frames") long retryFrames,
                                                    @JsonProperty("oldest_frame_age_ms") long oldestFrameAgeMs,
                                                    @JsonProperty("newest_frame_age_ms") long newestFrameAgeMs,
                                                    @JsonProperty("signal_strength") @Nullable Dot11SignalStrengthReport signalStrength) {
        return builder()
                .frameType(frameType)
                .transmitter(transmitter)
                .receiver(receiver)
                .bssid(bssid)
                .algorithm(algorithm)
                .transaction(transaction)
                .statusCode(statusCode)
                .frequency(frequency)
                .frameCount(frameCount)
                .retryFrames(retryFrames)
                .oldestFrameAgeMs(oldestFrameAgeMs)
                .newestFrameAgeMs(newestFrameAgeMs)
                .signalStrength(signalStrength)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dot11ConnectionFrameReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder frameType(String frameType);

        public abstract Builder transmitter(String transmitter);

        public abstract Builder receiver(String receiver);

        public abstract Builder bssid(String bssid);

        public abstract Builder algorithm(Integer algorithm);

        public abstract Builder transaction(Integer transaction);

        public abstract Builder statusCode(Integer statusCode);

        public abstract Builder frequency(Integer frequency);

        public abstract Builder frameCount(long frameCount);

        public abstract Builder retryFrames(long retryFrames);

        public abstract Builder oldestFrameAgeMs(long oldestFrameAgeMs);

        public abstract Builder newestFrameAgeMs(long newestFrameAgeMs);

        public abstract Builder signalStrength(Dot11SignalStrengthReport signalStrength);

        public abstract Dot11ConnectionFrameReport build();
    }

}
