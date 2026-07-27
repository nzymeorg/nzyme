package app.nzyme.core.rest.resources.taps.reports.tables.rtsp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class RtspMediaDescriptionReport {

    public abstract boolean hasVideo();
    public abstract boolean hasAudio();
    @Nullable
    public abstract String videoCodec();
    @Nullable
    public abstract String audioCodec();
    @Nullable
    public abstract String resolution();

    @JsonCreator
    public static RtspMediaDescriptionReport create(@JsonProperty("has_video") boolean hasVideo,
                                                    @JsonProperty("has_audio") boolean hasAudio,
                                                    @JsonProperty("video_codec") String videoCodec,
                                                    @JsonProperty("audio_codec") String audioCodec,
                                                    @JsonProperty("resolution") String resolution) {
        return builder()
                .hasVideo(hasVideo)
                .hasAudio(hasAudio)
                .videoCodec(videoCodec)
                .audioCodec(audioCodec)
                .resolution(resolution)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_RtspMediaDescriptionReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder hasVideo(boolean hasVideo);

        public abstract Builder hasAudio(boolean hasAudio);

        public abstract Builder videoCodec(String videoCodec);

        public abstract Builder audioCodec(String audioCodec);

        public abstract Builder resolution(String resolution);

        public abstract RtspMediaDescriptionReport build();
    }
}
