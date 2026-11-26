package app.nzyme.core.rest.responses.dot11.signal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.UUID;

@AutoValue
public abstract class FrameSignalFindingResponse {

    @JsonProperty("tap_uuid")
    public abstract UUID tapUuid();

    @Nullable
    @JsonProperty("tap_name")
    public abstract String tapName();

    @JsonProperty("frequency")
    public abstract int frequency();

    @JsonProperty("transmitter")
    public abstract String transmitter();

    @JsonProperty("frame_count")
    public abstract long frameCount();

    @JsonProperty("signal")
    public abstract double signal();

    @JsonProperty("signal_min")
    public abstract double signalMin();

    @JsonProperty("signal_max")
    public abstract double signalMax();

    @JsonProperty("compared_frame_count")
    public abstract long comparedFrameCount();

    @Nullable
    @JsonProperty("compared_with")
    public abstract Double comparedWith();

    @Nullable
    @JsonProperty("difference")
    public abstract Double difference();

    @JsonProperty("mismatch_frame_count")
    public abstract long mismatchFrameCount();

    @JsonProperty("largest_difference")
    public abstract double largestDifference();

    public static FrameSignalFindingResponse create(UUID tapUuid, String tapName, int frequency, String transmitter, long frameCount, double signal, double signalMin, double signalMax, long comparedFrameCount, Double comparedWith, Double difference, long mismatchFrameCount, double largestDifference) {
        return builder()
                .tapUuid(tapUuid)
                .tapName(tapName)
                .frequency(frequency)
                .transmitter(transmitter)
                .frameCount(frameCount)
                .signal(signal)
                .signalMin(signalMin)
                .signalMax(signalMax)
                .comparedFrameCount(comparedFrameCount)
                .comparedWith(comparedWith)
                .difference(difference)
                .mismatchFrameCount(mismatchFrameCount)
                .largestDifference(largestDifference)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FrameSignalFindingResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder tapUuid(UUID tapUuid);

        public abstract Builder tapName(String tapName);

        public abstract Builder frequency(int frequency);

        public abstract Builder transmitter(String transmitter);

        public abstract Builder frameCount(long frameCount);

        public abstract Builder signal(double signal);

        public abstract Builder signalMin(double signalMin);

        public abstract Builder signalMax(double signalMax);

        public abstract Builder comparedFrameCount(long comparedFrameCount);

        public abstract Builder comparedWith(Double comparedWith);

        public abstract Builder difference(Double difference);

        public abstract Builder mismatchFrameCount(long mismatchFrameCount);

        public abstract Builder largestDifference(double largestDifference);

        public abstract FrameSignalFindingResponse build();
    }

}
