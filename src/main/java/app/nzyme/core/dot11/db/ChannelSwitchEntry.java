package app.nzyme.core.dot11.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class ChannelSwitchEntry {

    public abstract long id();
    public abstract UUID tapUuid();
    public abstract String bssid();
    public abstract String frameType();
    public abstract String announcementType();
    public abstract int switchMode();
    @Nullable
    public abstract Integer frequency();
    @Nullable
    public abstract Integer newOperatingClass();
    public abstract int newChannel();
    @Nullable
    public abstract Integer newFrequency();
    public abstract int countMin();
    public abstract int countMax();
    public abstract long frameCount();
    public abstract int peakFramesPerSecond();
    public abstract List<String> anomalies();
    @Nullable
    public abstract String outcome();
    @Nullable
    public abstract Integer signalStrengthMin();
    @Nullable
    public abstract Integer signalStrengthMax();
    @Nullable
    public abstract Float signalStrengthAverage();
    public abstract DateTime createdAt();

    public static ChannelSwitchEntry create(long id, UUID tapUuid, String bssid, String frameType, String announcementType, int switchMode, Integer frequency, Integer newOperatingClass, int newChannel, Integer newFrequency, int countMin, int countMax, long frameCount, int peakFramesPerSecond, List<String> anomalies, String outcome, Integer signalStrengthMin, Integer signalStrengthMax, Float signalStrengthAverage, DateTime createdAt) {
        return builder()
                .id(id)
                .tapUuid(tapUuid)
                .bssid(bssid)
                .frameType(frameType)
                .announcementType(announcementType)
                .switchMode(switchMode)
                .frequency(frequency)
                .newOperatingClass(newOperatingClass)
                .newChannel(newChannel)
                .newFrequency(newFrequency)
                .countMin(countMin)
                .countMax(countMax)
                .frameCount(frameCount)
                .peakFramesPerSecond(peakFramesPerSecond)
                .anomalies(anomalies)
                .outcome(outcome)
                .signalStrengthMin(signalStrengthMin)
                .signalStrengthMax(signalStrengthMax)
                .signalStrengthAverage(signalStrengthAverage)
                .createdAt(createdAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ChannelSwitchEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder tapUuid(UUID tapUuid);
        public abstract Builder bssid(String bssid);
        public abstract Builder frameType(String frameType);
        public abstract Builder announcementType(String announcementType);
        public abstract Builder switchMode(int switchMode);
        public abstract Builder frequency(Integer frequency);
        public abstract Builder newOperatingClass(Integer newOperatingClass);
        public abstract Builder newChannel(int newChannel);
        public abstract Builder newFrequency(Integer newFrequency);
        public abstract Builder countMin(int countMin);
        public abstract Builder countMax(int countMax);
        public abstract Builder frameCount(long frameCount);
        public abstract Builder peakFramesPerSecond(int peakFramesPerSecond);
        public abstract Builder anomalies(List<String> anomalies);
        public abstract Builder outcome(String outcome);
        public abstract Builder signalStrengthMin(Integer signalStrengthMin);
        public abstract Builder signalStrengthMax(Integer signalStrengthMax);
        public abstract Builder signalStrengthAverage(Float signalStrengthAverage);
        public abstract Builder createdAt(DateTime createdAt);

        public abstract ChannelSwitchEntry build();
    }

}
