package app.nzyme.core.rest.responses.dot11.disco;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class DiscoFrameListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("frames")
    public abstract List<DiscoFrameDetailsResponse> frames();

    public static DiscoFrameListResponse create(long total, List<DiscoFrameDetailsResponse> frames) {
        return builder()
                .total(total)
                .frames(frames)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DiscoFrameListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder frames(List<DiscoFrameDetailsResponse> frames);

        public abstract DiscoFrameListResponse build();
    }
}
