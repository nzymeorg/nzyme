package app.nzyme.core.rest.resources.taps.reports.tables.dot11;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.Map;

@AutoValue
public abstract class Dot11DiscoTransmitterReport {

    public abstract String bssid();
    public abstract long sentFrames();
    public abstract Map<String, Long> receivers();
    public abstract double signalStrengthAverage();
    public abstract int signalStrengthMin();
    public abstract int signalStrengthMax();

    @JsonCreator
    public static Dot11DiscoTransmitterReport create(@JsonProperty("bssid") String bssid,
                                                     @JsonProperty("sent_frames") long sentFrames,
                                                     @JsonProperty("receivers") Map<String, Long> receivers,
                                                     @JsonProperty("signal_strength_average") double signalStrengthAverage,
                                                     @JsonProperty("signal_strength_min") int signalStrengthMin,
                                                     @JsonProperty("signal_strength_max") int signalStrengthMax) {
        return builder()
                .bssid(bssid)
                .sentFrames(sentFrames)
                .receivers(receivers)
                .signalStrengthAverage(signalStrengthAverage)
                .signalStrengthMin(signalStrengthMin)
                .signalStrengthMax(signalStrengthMax)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dot11DiscoTransmitterReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bssid(String bssid);

        public abstract Builder sentFrames(long sentFrames);

        public abstract Builder receivers(Map<String, Long> receivers);

        public abstract Builder signalStrengthAverage(double signalStrengthAverage);

        public abstract Builder signalStrengthMin(int signalStrengthMin);

        public abstract Builder signalStrengthMax(int signalStrengthMax);

        public abstract Dot11DiscoTransmitterReport build();
    }
}
