package app.nzyme.core.rest.resources.taps.reports.tables.dot11;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class Dot11DiscoFrameReport {

    public abstract String discoType();
    public abstract String transmitter();
    public abstract String receiver();
    public abstract String bssid();
    public abstract int reasonCode();
    @Nullable
    public abstract Integer frequency();
    public abstract long frameCount();
    @Nullable
    public abstract Dot11SignalStrengthReport signalStrength();

    @JsonCreator
    public static Dot11DiscoFrameReport create(@JsonProperty("disco_type") String discoType,
                                               @JsonProperty("transmitter") String transmitter,
                                               @JsonProperty("receiver") String receiver,
                                               @JsonProperty("bssid") String bssid,
                                               @JsonProperty("reason_code") int reasonCode,
                                               @JsonProperty("frequency") @Nullable Integer frequency,
                                               @JsonProperty("frame_count") long frameCount,
                                               @JsonProperty("signal_strength") @Nullable Dot11SignalStrengthReport signalStrength) {
        return builder()
                .discoType(discoType)
                .transmitter(transmitter)
                .receiver(receiver)
                .bssid(bssid)
                .reasonCode(reasonCode)
                .frequency(frequency)
                .frameCount(frameCount)
                .signalStrength(signalStrength)
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

        public abstract Builder reasonCode(int reasonCode);

        public abstract Builder frequency(Integer frequency);

        public abstract Builder frameCount(long frameCount);

        public abstract Builder signalStrength(Dot11SignalStrengthReport signalStrength);

        public abstract Dot11DiscoFrameReport build();
    }
}
