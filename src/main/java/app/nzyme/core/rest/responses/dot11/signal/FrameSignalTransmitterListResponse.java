package app.nzyme.core.rest.responses.dot11.signal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class FrameSignalTransmitterListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("transmitters")
    public abstract List<FrameSignalTransmitterResponse> transmitters();

    public static FrameSignalTransmitterListResponse create(long total, List<FrameSignalTransmitterResponse> transmitters) {
        return builder()
                .total(total)
                .transmitters(transmitters)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FrameSignalTransmitterListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder transmitters(List<FrameSignalTransmitterResponse> transmitters);

        public abstract FrameSignalTransmitterListResponse build();
    }

}
