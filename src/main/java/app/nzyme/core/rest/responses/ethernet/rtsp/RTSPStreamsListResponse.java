package app.nzyme.core.rest.responses.ethernet.rtsp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class RTSPStreamsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("streams")
    public abstract List<RTSPStreamDetailsResponse> sessions();

    public static RTSPStreamsListResponse create(long total, List<RTSPStreamDetailsResponse> sessions) {
        return builder()
                .total(total)
                .sessions(sessions)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_RTSPStreamsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder sessions(List<RTSPStreamDetailsResponse> sessions);

        public abstract RTSPStreamsListResponse build();
    }
}
