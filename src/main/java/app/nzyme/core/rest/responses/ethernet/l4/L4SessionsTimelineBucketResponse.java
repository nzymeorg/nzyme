package app.nzyme.core.rest.responses.ethernet.l4;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class L4SessionsTimelineBucketResponse {

    @JsonProperty("sessions")
    public abstract long sessions();

    @JsonProperty("bytes")
    public abstract long bytes();

    public static L4SessionsTimelineBucketResponse create(long sessions, long bytes) {
        return builder()
                .sessions(sessions)
                .bytes(bytes)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_L4SessionsTimelineBucketResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sessions(long sessions);

        public abstract Builder bytes(long bytes);

        public abstract L4SessionsTimelineBucketResponse build();
    }
}
