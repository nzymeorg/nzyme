package app.nzyme.core.rest.resources.taps.reports.tables.dot11;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

@AutoValue
public abstract class Dot11ChannelSwitchReport {

    public abstract String bssid();
    public abstract String frameType();
    public abstract String announcementType();
    public abstract int switchMode();
    @Nullable
    public abstract Integer newOperatingClass();
    public abstract int newChannel();
    @Nullable
    public abstract Integer frequency();
    public abstract int countMin();
    public abstract int countMax();
    public abstract long frameCount();
    public abstract long peakFramesPerSecond();
    public abstract long timestampRepetitions();
    public abstract long timestampMismatchFrames();
    public abstract long interleavedBeacons();
    public abstract long trailingElementFrames();
    @Nullable
    public abstract Boolean dsssOnly();
    @Nullable
    public abstract String countryCode();
    @Nullable
    public abstract Dot11SignalStrengthReport signalStrength();
    public abstract Map<Integer, Map<Integer, Long>> signalHistogram();

    @JsonCreator
    public static Dot11ChannelSwitchReport create(@JsonProperty("bssid") String bssid,
                                                  @JsonProperty("frame_type") String frameType,
                                                  @JsonProperty("announcement_type") String announcementType,
                                                  @JsonProperty("switch_mode") int switchMode,
                                                  @JsonProperty("new_operating_class") @Nullable Integer newOperatingClass,
                                                  @JsonProperty("new_channel") int newChannel,
                                                  @JsonProperty("frequency") @Nullable Integer frequency,
                                                  @JsonProperty("count_min") int countMin,
                                                  @JsonProperty("count_max") int countMax,
                                                  @JsonProperty("frame_count") long frameCount,
                                                  @JsonProperty("peak_frames_per_second") long peakFramesPerSecond,
                                                  @JsonProperty("timestamp_repetitions") long timestampRepetitions,
                                                  @JsonProperty("timestamp_mismatch_frames") long timestampMismatchFrames,
                                                  @JsonProperty("interleaved_beacons") long interleavedBeacons,
                                                  @JsonProperty("trailing_element_frames") long trailingElementFrames,
                                                  @JsonProperty("dsss_only") @Nullable Boolean dsssOnly,
                                                  @JsonProperty("country_code") @Nullable String countryCode,
                                                  @JsonProperty("signal_strength") @Nullable Dot11SignalStrengthReport signalStrength,
                                                  @JsonProperty("signal_histogram") @Nullable Map<Integer, Map<Integer, Long>> signalHistogram) {
        return builder()
                .bssid(bssid)
                .frameType(frameType)
                .announcementType(announcementType)
                .switchMode(switchMode)
                .newOperatingClass(newOperatingClass)
                .newChannel(newChannel)
                .frequency(frequency)
                .countMin(countMin)
                .countMax(countMax)
                .frameCount(frameCount)
                .peakFramesPerSecond(peakFramesPerSecond)
                .timestampRepetitions(timestampRepetitions)
                .timestampMismatchFrames(timestampMismatchFrames)
                .interleavedBeacons(interleavedBeacons)
                .trailingElementFrames(trailingElementFrames)
                .dsssOnly(dsssOnly)
                .countryCode(countryCode)
                .signalStrength(signalStrength)
                .signalHistogram(signalHistogram == null ? Collections.emptyMap() : signalHistogram)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dot11ChannelSwitchReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bssid(String bssid);
        public abstract Builder frameType(String frameType);
        public abstract Builder announcementType(String announcementType);
        public abstract Builder switchMode(int switchMode);
        public abstract Builder newOperatingClass(Integer newOperatingClass);
        public abstract Builder newChannel(int newChannel);
        public abstract Builder frequency(Integer frequency);
        public abstract Builder countMin(int countMin);
        public abstract Builder countMax(int countMax);
        public abstract Builder frameCount(long frameCount);
        public abstract Builder peakFramesPerSecond(long peakFramesPerSecond);
        public abstract Builder timestampRepetitions(long timestampRepetitions);
        public abstract Builder timestampMismatchFrames(long timestampMismatchFrames);
        public abstract Builder interleavedBeacons(long interleavedBeacons);
        public abstract Builder trailingElementFrames(long trailingElementFrames);
        public abstract Builder dsssOnly(Boolean dsssOnly);
        public abstract Builder countryCode(String countryCode);
        public abstract Builder signalStrength(Dot11SignalStrengthReport signalStrength);

        public abstract Builder signalHistogram(Map<Integer, Map<Integer, Long>> signalHistogram);

        public abstract Dot11ChannelSwitchReport build();
    }

}
