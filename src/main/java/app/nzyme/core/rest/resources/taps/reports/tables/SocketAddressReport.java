package app.nzyme.core.rest.resources.taps.reports.tables;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class SocketAddressReport {

    @JsonProperty("address")
    public abstract String address();

    @JsonProperty("port")
    public abstract int port();

    @JsonCreator
    public static SocketAddressReport create(@JsonProperty("address") String address,
                                             @JsonProperty("port") int port) {
        return builder()
                .address(address)
                .port(port)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SocketAddressReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder address(String address);

        public abstract Builder port(int port);

        public abstract SocketAddressReport build();
    }
}
