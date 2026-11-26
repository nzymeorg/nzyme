package app.nzyme.core.rest.responses.dot11.disco;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class ConnectionFrameListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("frames")
    public abstract List<ConnectionFrameDetailsResponse> frames();

    public static ConnectionFrameListResponse create(long total, List<ConnectionFrameDetailsResponse> frames) {
        return builder()
                .total(total)
                .frames(frames)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ConnectionFrameListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder frames(List<ConnectionFrameDetailsResponse> frames);

        public abstract ConnectionFrameListResponse build();
    }

}
