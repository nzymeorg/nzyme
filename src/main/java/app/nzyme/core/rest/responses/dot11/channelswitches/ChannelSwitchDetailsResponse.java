package app.nzyme.core.rest.responses.dot11.channelswitches;

import app.nzyme.core.rest.responses.dot11.Dot11MacLinkMetadataResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class ChannelSwitchDetailsResponse {

    @JsonProperty("id")
    public abstract long id();

    @JsonProperty("tap_uuid")
    public abstract UUID tapUuid();

    @Nullable
    @JsonProperty("tap_name")
    public abstract String tapName();

    @JsonProperty("timestamp")
    public abstract DateTime timestamp();

    @JsonProperty("bssid")
    public abstract Dot11MacLinkMetadataResponse bssid();

    @JsonProperty("frame_type")
    public abstract String frameType();

    @JsonProperty("announcement_type")
    public abstract String announcementType();

    @JsonProperty("switch_mode")
    public abstract int switchMode();

    @Nullable
    @JsonProperty("frequency")
    public abstract Integer frequency();

    @Nullable
    @JsonProperty("new_operating_class")
    public abstract Integer newOperatingClass();

    @JsonProperty("new_channel")
    public abstract int newChannel();

    @Nullable
    @JsonProperty("new_frequency")
    public abstract Integer newFrequency();

    @JsonProperty("count_min")
    public abstract int countMin();

    @JsonProperty("count_max")
    public abstract int countMax();

    @JsonProperty("frame_count")
    public abstract long frameCount();

    @JsonProperty("peak_frames_per_second")
    public abstract int peakFramesPerSecond();

    @JsonProperty("anomalies")
    public abstract List<String> anomalies();

    @Nullable
    @JsonProperty("outcome")
    public abstract String outcome();

    @Nullable
    @JsonProperty("signal_strength_min")
    public abstract Integer signalStrengthMin();

    @Nullable
    @JsonProperty("signal_strength_max")
    public abstract Integer signalStrengthMax();

    @Nullable
    @JsonProperty("signal_strength_average")
    public abstract Float signalStrengthAverage();

    public static ChannelSwitchDetailsResponse create(long id, UUID tapUuid, String tapName, DateTime timestamp, Dot11MacLinkMetadataResponse bssid, String frameType, String announcementType, int switchMode, Integer frequency, Integer newOperatingClass, int newChannel, Integer newFrequency, int countMin, int countMax, long frameCount, int peakFramesPerSecond, List<String> anomalies, String outcome, Integer signalStrengthMin, Integer signalStrengthMax, Float signalStrengthAverage) {
        return builder()
                .id(id)
                .tapUuid(tapUuid)
                .tapName(tapName)
                .timestamp(timestamp)
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
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ChannelSwitchDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder tapUuid(UUID tapUuid);
        public abstract Builder tapName(String tapName);
        public abstract Builder timestamp(DateTime timestamp);
        public abstract Builder bssid(Dot11MacLinkMetadataResponse bssid);
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

        public abstract ChannelSwitchDetailsResponse build();
    }

}
