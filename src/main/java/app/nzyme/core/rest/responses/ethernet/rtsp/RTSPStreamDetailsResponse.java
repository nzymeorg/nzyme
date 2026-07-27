package app.nzyme.core.rest.responses.ethernet.rtsp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class RTSPStreamDetailsResponse {

    @JsonProperty("setup_tcp_session_key")
    public abstract String setupTcpSessionKey();

    @JsonProperty("state")
    public abstract String state();

    public static RTSPStreamDetailsResponse create(String setupTcpSessionKey, String state) {
        return builder()
                .setupTcpSessionKey(setupTcpSessionKey)
                .state(state)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_RTSPStreamDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setupTcpSessionKey(String setupTcpSessionKey);

        public abstract Builder state(String state);

        public abstract RTSPStreamDetailsResponse build();
    }
}
