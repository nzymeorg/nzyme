package app.nzyme.core.rest.responses.dot11.signal;

import app.nzyme.core.rest.responses.dot11.Dot11MacLinkMetadataResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class FrameSignalSourceResponse {

    @JsonProperty("address")
    public abstract String address();

    @JsonProperty("type")
    public abstract String type();

    @JsonProperty("mac")
    public abstract Dot11MacLinkMetadataResponse mac();

    @JsonProperty("frame_count")
    public abstract long frameCount();

    @JsonProperty("compared_frame_count")
    public abstract long comparedFrameCount();

    @JsonProperty("mismatch_frame_count")
    public abstract long mismatchFrameCount();

    @Nullable
    @JsonProperty("difference")
    public abstract Double difference();

    @JsonProperty("largest_difference")
    public abstract double largestDifference();

    public static FrameSignalSourceResponse create(String address, String type, Dot11MacLinkMetadataResponse mac, long frameCount, long comparedFrameCount, long mismatchFrameCount, Double difference, double largestDifference) {
        return builder()
                .address(address)
                .type(type)
                .mac(mac)
                .frameCount(frameCount)
                .comparedFrameCount(comparedFrameCount)
                .mismatchFrameCount(mismatchFrameCount)
                .difference(difference)
                .largestDifference(largestDifference)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FrameSignalSourceResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder address(String address);

        public abstract Builder type(String type);

        public abstract Builder mac(Dot11MacLinkMetadataResponse mac);

        public abstract Builder frameCount(long frameCount);

        public abstract Builder comparedFrameCount(long comparedFrameCount);

        public abstract Builder mismatchFrameCount(long mismatchFrameCount);

        public abstract Builder difference(Double difference);

        public abstract Builder largestDifference(double largestDifference);

        public abstract FrameSignalSourceResponse build();
    }

}
