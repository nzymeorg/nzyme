package app.nzyme.core.ethernet.dhcp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Dhcpv6Address {

    @JsonProperty("address")
    public abstract String address();

    @JsonProperty("preferred_lifetime")
    public abstract long preferredLifetime();

    @JsonProperty("valid_lifetime")
    public abstract long validLifetime();

    @JsonCreator
    public static Dhcpv6Address create(@JsonProperty("address") String address,
                                       @JsonProperty("preferred_lifetime") long preferredLifetime,
                                       @JsonProperty("valid_lifetime") long validLifetime) {
        return builder()
                .address(address)
                .preferredLifetime(preferredLifetime)
                .validLifetime(validLifetime)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dhcpv6Address.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder address(String address);

        public abstract Builder preferredLifetime(long preferredLifetime);

        public abstract Builder validLifetime(long validLifetime);

        public abstract Dhcpv6Address build();
    }

}
