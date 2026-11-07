package app.nzyme.core.rest.responses.ethernet.assets.identity;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class DuplicateMacDestinationResponse {

    @JsonProperty("address")
    public abstract L4AddressResponse address();

    @JsonProperty("sessions")
    public abstract long sessions();

    @JsonProperty("bytes")
    public abstract long bytes();

    public static DuplicateMacDestinationResponse create(L4AddressResponse address, long sessions, long bytes) {
        return builder()
                .address(address)
                .sessions(sessions)
                .bytes(bytes)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DuplicateMacDestinationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder address(L4AddressResponse address);
        public abstract Builder sessions(long sessions);
        public abstract Builder bytes(long bytes);
        public abstract DuplicateMacDestinationResponse build();
    }
}
