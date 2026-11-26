package app.nzyme.core.rest.responses.dot11.signal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class FrameSignalChartResponse {

    @JsonProperty("tap_uuid")
    public abstract UUID tapUuid();

    @Nullable
    @JsonProperty("tap_name")
    public abstract String tapName();

    @JsonProperty("frequency")
    public abstract int frequency();

    @JsonProperty("frame_count")
    public abstract long frameCount();

    @JsonProperty("is_truncated")
    public abstract boolean isTruncated();

    @JsonProperty("references")
    public abstract List<FrameSignalReferenceResponse> references();

    @JsonProperty("frames")
    public abstract List<FrameSignalFrameResponse> frames();

    public static FrameSignalChartResponse create(UUID tapUuid, String tapName, int frequency, long frameCount, boolean isTruncated, List<FrameSignalReferenceResponse> references, List<FrameSignalFrameResponse> frames) {
        return builder()
                .tapUuid(tapUuid)
                .tapName(tapName)
                .frequency(frequency)
                .frameCount(frameCount)
                .isTruncated(isTruncated)
                .references(references)
                .frames(frames)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FrameSignalChartResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder tapUuid(UUID tapUuid);

        public abstract Builder tapName(String tapName);

        public abstract Builder frequency(int frequency);

        public abstract Builder frameCount(long frameCount);

        public abstract Builder isTruncated(boolean isTruncated);

        public abstract Builder references(List<FrameSignalReferenceResponse> references);

        public abstract Builder frames(List<FrameSignalFrameResponse> frames);

        public abstract FrameSignalChartResponse build();
    }

}
